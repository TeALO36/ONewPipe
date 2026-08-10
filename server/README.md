# ONewPipe self-hosted server

The server provides the account API, watch-position synchronization and the web interface from the same origin.

## Docker

From the repository root:

```bash
cd server
docker compose up -d --build
```

The persistent data is stored in the `onewpipe-data` volume. Set a strong `JWT_SECRET` in `server/.env` before exposing the service outside the local network.

## Java jar

Build and run it on Linux, macOS or Windows with Java 21:

```bash
./gradlew :server:fatJar
java -jar server/build/libs/onewpipe-server-all.jar
```

On Windows, copy `server/run-server.bat` beside `onewpipe-server-all.jar` and double-click it, or run it from PowerShell. The jar is cross-platform; the Windows release also contains a native `ONewPipeServer.exe` launcher when the release workflow completes.

## First connection

1. Start the server. It listens on `0.0.0.0:8080` by default.
2. On the same machine, open `http://localhost:8080`.
3. On another device, open `http://SERVER_IP:8080` (for example `http://192.168.1.10:8080`).
4. In Android or desktop, open **Settings → Server connection** and enter either `192.168.1.10:8080` or the complete URL. The client adds `http://` and the default `:8080` when omitted.
5. Choose **Create account** the first time. Later devices use **Sign in** with the same server URL and credentials.

The web UI, `/api/register`, `/api/login` and `/api/watchstate` all use the same server address. If devices cannot connect, allow TCP port 8080 through the host firewall and use HTTPS through a reverse proxy when the server is internet-facing.

## Useful checks

```text
GET http://SERVER_IP:8080/health  -> {"status":"ok"}
GET http://SERVER_IP:8080/          -> ONewPipe web interface
```

The server does not need a separate frontend process. The root route serves the bundled site, while API routes remain under `/api/`.

## Local-only administration (optional)

The administration page is **disabled by default**, is not linked from the normal site, and answers `404` to non-loopback clients. Enable it only for local maintenance on the server computer:

```text
ADMIN_PANEL_ENABLED=true
HOST=127.0.0.1
```

Then open `http://127.0.0.1:8080/__local_admin`. The first account created on a new data directory is the administrator. Existing installations migrate their oldest account as administrator. The local panel can inspect accounts, revoke sessions, delete non-administrator accounts, change the administrator password, and download a JSON backup. It is intentionally not enabled in the Docker compose defaults and adds nothing to the normal Android, desktop, or public web navigation.
