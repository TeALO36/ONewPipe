# ONewPipe

ONewPipe is a privacy-focused, ad-free media frontend based on the NewPipe core. It is available as an Android application, as a desktop application for Windows, Linux, and macOS, and as a self-hosted web interface.

**Official public repository:** <https://github.com/TeALO36/ONewPipe>

## Features

- **Home** — themed rows (Gaming, Music, Movies & Series, Podcasts), each with a "See all" shortcut to its full grid.
- **Trending** — All / Gaming / Music / Movies & Series / Podcasts categories.
- **Search** — filter by videos or channels, with a recent-searches drop-down.
- **Subscriptions** — follow channels without an account, open a channel,
  unsubscribe, and load a feed of the newest videos from every followed channel.
- **Library** — watch history with resume positions, local playlists,
  a watch-later list, and the list of downloaded files.
- **Player** — quality selection, playback speed, play queue, repeat modes,
  audio-only (background) mode, subtitles, audio tracks, picture-in-picture,
  fullscreen, keyboard shortcuts, video description and comments.
- **Downloads** — every video quality saved as one file with its audio track,
  or audio only. On desktop and in the web interface the server downloads the
  two streams and combines them with NewPipe's muxers (MP4 or WebM); progress
  and cancellation live in the notification centre, and the page stays usable.
- **Settings** — theme, playback preferences, history controls, backup and
  restore, self-hosted account, updates. On Android the classic NewPipe
  interface remains reachable from here.

## Install from GitHub Releases

Use the **latest non-draft release** on the [Releases page](https://github.com/TeALO36/ONewPipe/releases). The file names are deliberately explicit:

| Device | File to download | What it is |
| --- | --- | --- |
| Android | `ONewPipe-vX.Y.Z-android.apk` | Android application package |
| Windows | `ONewPipe-vX.Y.Z-windows-setup.exe` | Recommended Windows installer |
| Windows | `ONewPipe-vX.Y.Z-windows-portable.exe` | Portable version; runs without installation |
| Linux (Debian/Ubuntu) | `ONewPipe-vX.Y.Z-linux.deb` | Debian package |
| Linux (other distributions) | `ONewPipe-vX.Y.Z-linux.AppImage` | Make it executable, then run it |
| macOS | `ONewPipe-vX.Y.Z-macos.dmg` | macOS disk image (not notarized: open it with right-click → Open the first time) |

The desktop application bundles its own Java runtime and the ONewPipe server, which it starts on the local machine; nothing else needs to be installed.

`X.Y.Z` is the release version shown in the release title. Do not download `ONewPipe-vX.Y.Z-server.jar`: that is the self-hosted server component, not an application for watching videos. Do not use files from **Actions artifacts** for a normal installation; those are CI builds and may not be signed for upgrades.

### Other installation methods

- **F-Droid:** when the ONewPipe repository is published there, install and update it from F-Droid. F-Droid signs its own APKs, so an F-Droid installation must not be replaced by the GitHub APK updater.
- **Build from source:** developers can build the Android or desktop targets with Gradle. Debug builds are for testing and are not compatible with signed release updates.
- **Windows portable:** a single executable that runs without installation; it can be moved or deleted without an uninstall step.

## Updates

### Android

Open **Settings → Updates**:

- **Automatically check for updates** periodically checks the public ONewPipe GitHub Releases API and displays a notification when a newer signed release is available.
- **Check for updates** is the manual update button.

The notification opens the matching GitHub release APK. Android verifies the signing key before installing it. If ONewPipe was installed through F-Droid, use F-Droid for updates instead.

### Desktop

The desktop application checks GitHub for a newer release when it starts. When one is found, **Open download page** takes you to the public release page so you can select the correct installer for Windows, Linux, or macOS. The application does not silently replace itself while it is running.

Versions up to 1.3.0 were a Compose/VLC application (`.msi` and `.zip` files); later releases publish the Electron application listed above.

## Release and compatibility rules

- Releases are tagged `vX.Y.Z` and are built for every supported desktop platform plus Android.
- GitHub release artifacts use the names in the table above; the operating system is always part of the name.
- GitHub APK releases must be signed with the persistent ONewPipe release key. The release workflow refuses tagged releases when that key is not configured.
- Switching between GitHub, F-Droid, debug, and pull-request builds can require exporting application data, uninstalling the old package, then restoring the data. This is an Android signing restriction, not an ONewPipe limitation.

## Self-hosted server

The server bundles the account API, watch-position synchronization and the web interface. It is available on Linux, macOS and Windows through Docker, a Java 21 fat jar, or the Windows launcher/native server distribution.

```bash
cd server
docker compose up -d --build
# then open http://SERVER_IP:8080
```

For a jar deployment, run `./gradlew :server:fatJar` and start `server/build/libs/onewpipe-server-all.jar`. On Windows, use `server/run-server.bat` beside the jar. The first visitor selects **Create account**; Android and desktop use **Settings → Server connection** with `SERVER_IP:8080` and the same credentials. The client accepts a bare IP and adds `http://` plus the default port 8080 automatically. The web UI is served at `/`, so `http://SERVER_IP:8080` is the complete site address.

Once connected, **Settings → Account → Sync subscriptions and playlists** merges the
subscriptions, playlists and watch-later list of every device through `/api/library`;
watch positions keep syncing on their own during playback.

In the web interface and the desktop application, **Settings → Account** signs in to this server or to another ONewPipe server (for the desktop application, enter the address of your server, as in the Android app). The first sign-in on a device merges its library with the account's; later changes, history and playback positions synchronize automatically.

Downloads use the server's disk and bandwidth: the desktop application may always download from its own local server, while a server reached over the network requires signing in with an account on that server.

Set a strong `JWT_SECRET`, keep `DATA_DIR` persistent, allow TCP port 8080 on the local firewall and use HTTPS behind a reverse proxy for internet access. See [server/README.md](server/README.md) for the complete setup.

## Development

```bash
./gradlew :desktopApp:run
./gradlew :app:assembleDebug
```

The desktop application is moving to a web interface shown by Electron:

- `webapp/` is the interface (Vite + React + TypeScript). `./gradlew :server:fatJar` builds it with npm and ships it in the server jar at `/`; pass `-PskipWebapp` to build the server without Node. The previous web UI stays available at `/classic`.
- For interface work, start the server on port 18080 and run `npm run dev` in `webapp/`; Vite forwards `/api` to that server (override with `ONEWPIPE_SERVER`).
- `desktopWeb/` is the Electron shell. After building the jar, run `npm install` then `npx electron .` in `desktopWeb/`. It starts the server on a free loopback port and opens the interface.

The upstream base currently merged into this fork is NewPipe 0.29.1 (`dev`).

ONewPipe is free software released under the [GNU GPL v3 or later](LICENSE). It is not affiliated with the official NewPipe project; it is a fork built on the NewPipe code and extractor. See [PRIVACY.md](PRIVACY.md) for what the application sends and stores.
