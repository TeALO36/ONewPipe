# ONewPipe

ONewPipe is a privacy-focused, ad-free media frontend based on the NewPipe core. It is available as an Android application and as a native desktop application for Windows, Linux, and macOS.

**Official public repository:** <https://github.com/TeALO36/ONewPipe>

## Install from GitHub Releases

Use the **latest non-draft release** on the [Releases page](https://github.com/TeALO36/ONewPipe/releases). The file names are deliberately explicit:

| Device | File to download | What it is |
| --- | --- | --- |
| Android | `ONewPipe-vX.Y.Z-android.apk` | Android application package |
| Windows | `ONewPipe-vX.Y.Z-windows-setup.msi` | Recommended Windows installer |
| Windows | `ONewPipe-vX.Y.Z-windows-portable.zip` | Portable version; extract it and run the application |
| Linux (Debian/Ubuntu) | `ONewPipe-vX.Y.Z-linux.deb` | Native Debian package |
| macOS | `ONewPipe-vX.Y.Z-macos.dmg` | macOS disk image |

`X.Y.Z` is the release version shown in the release title. Do not download `ONewPipe-vX.Y.Z-server.jar`: that is the self-hosted server component, not an application for watching videos. Do not use files from **Actions artifacts** for a normal installation; those are CI builds and may not be signed for upgrades.

### Other installation methods

- **F-Droid:** when the ONewPipe repository is published there, install and update it from F-Droid. F-Droid signs its own APKs, so an F-Droid installation must not be replaced by the GitHub APK updater.
- **Build from source:** developers can build the Android or desktop targets with Gradle. Debug builds are for testing and are not compatible with signed release updates.
- **Windows portable:** this is intentionally an archive rather than a self-installing executable; it can be moved or deleted without an uninstall step.

## Updates

### Android

Open **Settings → Updates**:

- **Automatically check for updates** periodically checks the public ONewPipe GitHub Releases API and displays a notification when a newer signed release is available.
- **Check for updates** is the manual update button.

The notification opens the matching GitHub release APK. Android verifies the signing key before installing it. If ONewPipe was installed through F-Droid, use F-Droid for updates instead.

### Desktop

The update icon in the left sidebar checks GitHub automatically when the application starts. It also provides a manual check button. When an update is found, **Open download page** takes you to the public release page so you can select the correct installer for Windows, Linux, or macOS. The application does not silently replace an installer while it is running.

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

Set a strong `JWT_SECRET`, keep `DATA_DIR` persistent, allow TCP port 8080 on the local firewall and use HTTPS behind a reverse proxy for internet access. See [server/README.md](server/README.md) for the complete setup.

## Development

```bash
./gradlew :desktopApp:run
./gradlew :app:assembleDebug
```

ONewPipe is free software released under the [GNU GPL v3 or later](LICENSE). It is not affiliated with the official NewPipe project; it is a fork built on the NewPipe code and extractor.
