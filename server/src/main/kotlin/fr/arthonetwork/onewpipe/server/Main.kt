package fr.arthonetwork.onewpipe.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.http.content.staticResources
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.io.File
import kotlinx.serialization.json.Json

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"
    val dataDir = File(System.getenv("DATA_DIR") ?: "./data")
    val jwtSecret = System.getenv("JWT_SECRET")
        ?: "dev-secret-change-me-${System.getProperty("user.name")}-${System.getenv("COMPUTERNAME") ?: "local"}"

    // Geo-localize trending and search (YouTube gl/hl params). Defaults to the
    // server's system locale; override with CONTENT_COUNTRY / CONTENT_LANGUAGE
    // (e.g. "FR" / "fr") to serve a specific region regardless of the host.
    val contentCountry = (System.getenv("CONTENT_COUNTRY") ?: java.util.Locale.getDefault().country)
        .takeIf { it.isNotBlank() } ?: "US"
    val contentLanguage = (System.getenv("CONTENT_LANGUAGE") ?: java.util.Locale.getDefault().language)
        .takeIf { it.isNotBlank() } ?: "en"
    NewPipe.init(
        OkHttpDownloader(okhttp3.OkHttpClient.Builder().build()),
        Localization(contentLanguage, contentCountry),
        ContentCountry(contentCountry)
    )
    // The web player uses one progressive stream for the fastest first frame.
    // Native clients enable the slower iOS response only from their quality menu.
    org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor.setFetchIosClient(false)

    val store = Store(dataDir)
    println("ONewPipe server listening on http://$host:$port (data: ${dataDir.absolutePath})")

    embeddedServer(Netty, port = port, host = host) {
        module(store, jwtSecret)
    }.start(wait = true)
}

fun Application.module(
    store: Store,
    jwtSecret: String,
    adminPanelEnabled: Boolean = System.getenv("ADMIN_PANEL_ENABLED")?.equals("true", ignoreCase = true) == true
) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Request failed", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(cause.message ?: "Internal server error")
            )
        }
    }

    routing {
        // The admin surface is deliberately not part of the public web UI.
        // It is disabled by default and returns 404 to every non-loopback client.
        get("/__local_admin") {
            if (!call.requireLocalAdmin(adminPanelEnabled)) return@get
            val page = Thread.currentThread().contextClassLoader
                .getResourceAsStream("web/admin.html")
                ?: error("Missing local admin page")
            call.respondBytes(page.use { it.readBytes() }, ContentType.Text.Html)
        }

        get("/api/admin/overview") {
            if (!call.requireLocalAdmin(adminPanelEnabled)) return@get
            val username = call.authenticateAdmin(jwtSecret, store) ?: return@get
            call.respond(
                AdminOverviewDto(
                    currentUsername = username,
                    accounts = store.adminAccounts().map { account ->
                        AdminAccountDto(
                            username = account.username,
                            createdAt = account.createdAt,
                            isAdmin = store.isAdmin(account.username),
                            watchStateCount = store.watchStateCount(account.username)
                        )
                    },
                    storeFileBytes = store.storeFileBytes()
                )
            )
        }

        post("/api/admin/password") {
            if (!call.requireLocalAdmin(adminPanelEnabled)) return@post
            val username = call.authenticateAdmin(jwtSecret, store) ?: return@post
            val request = call.receive<AdminPasswordRequest>()
            if (request.password.length < 6) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Password must contain at least 6 characters"))
                return@post
            }
            store.updatePassword(username, request.password)
            call.respond(mapOf("status" to "password-updated", "message" to "Sign in again"))
        }

        post("/api/admin/revoke-sessions") {
            if (!call.requireLocalAdmin(adminPanelEnabled)) return@post
            call.authenticateAdmin(jwtSecret, store) ?: return@post
            val request = call.receive<AdminAccountRequest>()
            if (!store.revokeSessions(request.username.trim())) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Account not found"))
                return@post
            }
            call.respond(mapOf("status" to "sessions-revoked"))
        }

        post("/api/admin/account/delete") {
            if (!call.requireLocalAdmin(adminPanelEnabled)) return@post
            val currentUsername = call.authenticateAdmin(jwtSecret, store) ?: return@post
            val username = call.receive<AdminAccountRequest>().username.trim()
            if (username.isBlank() || username == currentUsername || store.isAdmin(username)) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("The administrator account cannot be deleted"))
                return@post
            }
            if (!store.deleteAccount(username)) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Account not found"))
                return@post
            }
            call.respond(mapOf("status" to "account-deleted"))
        }

        get("/api/admin/backup") {
            if (!call.requireLocalAdmin(adminPanelEnabled)) return@get
            call.authenticateAdmin(jwtSecret, store) ?: return@get
            call.respondBytes(store.backupJson(), ContentType.Application.Json)
        }

        // ---- Extraction API ----

        get("/api/trending") {
            val service = call.request.queryParameters["service"]?.toIntOrNull() ?: 0
            val category = call.request.queryParameters["category"] ?: "all"
            call.respond(fetchTrending(service, category))
        }

        get("/api/search") {
            val query = call.request.queryParameters["q"] ?: ""
            if (query.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing query"))
                return@get
            }
            val service = call.request.queryParameters["service"]?.toIntOrNull() ?: 0
            call.respond(searchMedia(service, query))
        }

        get("/api/video") {
            val url = call.request.queryParameters["url"] ?: ""
            if (url.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing url"))
                return@get
            }
            call.respond(fetchVideoInfo(url))
        }

        // ---- Account API ----

        post("/api/register") {
            val request = call.receive<AuthRequest>()
            val username = request.username.trim()
            if (username.length < 3 || request.password.length < 6) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Username (min 3 chars) and password (min 6 chars) required"))
                return@post
            }
            val salt = Passwords.newSalt()
            val created = store.createAccount(
                AccountRecord(username = username, salt = salt, hash = Passwords.hash(request.password, salt))
            )
            if (!created) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("Username already taken"))
                return@post
            }
            call.respond(HttpStatusCode.Created, AuthResponse(token = Jwt.sign(jwtSecret, username), username = username))
        }

        post("/api/login") {
            val request = call.receive<AuthRequest>()
            val account = store.findAccount(request.username.trim())
            if (account == null || account.hash != Passwords.hash(request.password, account.salt)) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid username or password"))
                return@post
            }
            call.respond(AuthResponse(token = Jwt.sign(jwtSecret, account.username), username = account.username))
        }

        // ---- Watch-state sync (bearer token) ----

        get("/api/watchstate") {
            val username = call.authenticate(jwtSecret, store) ?: return@get
            call.respond(store.getWatchState(username))
        }

        post("/api/watchstate") {
            val username = call.authenticate(jwtSecret, store) ?: return@post
            val request = call.receive<WatchStateRequest>()
            val synced = store.upsertWatchState(username, request.items)
            call.respond(WatchStateResponse(synced = synced, items = store.getWatchState(username)))
        }

        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        // Web UI (same origin as the API: no CORS needed). Keep this catch-all
        // after every API/admin route so missing static files do not swallow them.
        staticResources("/", "web") {
            default("index.html")
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.authenticate(
    jwtSecret: String,
    store: Store
): String? {
    val header = request.headers["Authorization"] ?: ""
    val token = header.removePrefix("Bearer ").trim()
    val claims = Jwt.verifyClaims(jwtSecret, token)
    val username = claims?.sub
    if (claims == null || username == null || store.findAccount(username) == null ||
        !store.isSessionValid(username, claims.iat)
    ) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing or invalid token"))
        return null
    }
    return username
}

private suspend fun io.ktor.server.application.ApplicationCall.authenticateAdmin(
    jwtSecret: String,
    store: Store
): String? {
    val username = authenticate(jwtSecret, store)
    if (username == null || !store.isAdmin(username)) {
        respond(HttpStatusCode.Forbidden, ErrorResponse("Administrator access required"))
        return null
    }
    return username
}

private suspend fun io.ktor.server.application.ApplicationCall.requireLocalAdmin(enabled: Boolean): Boolean {
    if (!enabled) {
        respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        return false
    }
    val remoteAddress = request.local.remoteAddress
    val loopback = remoteAddress == "127.0.0.1" ||
        remoteAddress == "::1" ||
        remoteAddress == "0:0:0:0:0:0:0:1"
    if (!loopback) {
        respond(HttpStatusCode.NotFound, ErrorResponse("Not found"))
        return false
    }
    return true
}
