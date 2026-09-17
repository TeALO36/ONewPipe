# ONewPipe web interface

The interface of the ONewPipe server, also shown by the Electron desktop shell (`../desktopWeb`). Built with Vite, React and TypeScript.

- `npm run dev` starts the dev server on port 5173. It forwards `/api` and `/health` to `http://127.0.0.1:18080`; set `ONEWPIPE_SERVER` to use another server.
- `npm run build` writes `dist/`. `./gradlew :server:fatJar` runs this build and ships the result in the server jar at `/`.

Playback uses Shaka Player with the DASH manifest built by the server (`/api/manifest`). The library (history, playlists, subscriptions, settings) is stored in the browser.
