package fr.arthonetwork.onewpipe.server

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

    NewPipe.init(
        OkHttpDownloader(okhttp3.OkHttpClient.Builder().build()),
        Localization("en", "US"),
        ContentCountry("US")
    )
    // YouTube throttles the plain WEB client to ~360p; the iOS client returns
    // the full format range (720p/1080p/4K + audio) without a poToken.
    org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor.setFetchIosClient(true)

    val store = Store(dataDir)
    println("ONewPipe server listening on http://$host:$port (data: ${dataDir.absolutePath})")

    embeddedServer(Netty, port = port, host = host) {
        module(store, jwtSecret)
    }.start(wait = true)
}

fun Application.module(store: Store, jwtSecret: String) {
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
        // Web UI (same origin as the API: no CORS needed)
        staticResources("/", "web") {
            default("index.html")
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
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.authenticate(
    jwtSecret: String,
    store: Store
): String? {
    val header = request.headers["Authorization"] ?: ""
    val token = header.removePrefix("Bearer ").trim()
    val username = Jwt.verify(jwtSecret, token)
    if (username == null || store.findAccount(username) == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Missing or invalid token"))
        return null
    }
    return username
}
