# ONewPipe privacy policy

ONewPipe is a fork of [NewPipe](https://github.com/TeamNewPipe/NewPipe). Like NewPipe,
it has no account on the streaming services, no advertising and no analytics SDK.
This document describes exactly what leaves your device.

## What ONewPipe never does

- It does not create an account on YouTube, SoundCloud, Bandcamp, media.ccc.de or PeerTube.
- It does not send your watch history, searches, subscriptions or playlists to the
  ONewPipe developers.
- It contains no advertising, no tracking SDK and no crash-reporting service.

## Data stored on your device only

The following is written to the application's private storage and never leaves the
device unless you explicitly enable server synchronization (see below):

- watch history and the position where you stopped each video;
- search history;
- local playlists and the "watch later" list;
- subscriptions;
- the list of files you downloaded;
- your settings (theme, playback preferences, preferred quality).

Watch and search history can be disabled and cleared at any time in
**Settings → History and cache**.

## Network connections ONewPipe makes

1. **Streaming services.** Requests go directly from your device to the service you
   are browsing (for example YouTube). Those services see your IP address and the
   usual request metadata, exactly as a web browser would. ONewPipe sends no account
   identifier with them.
2. **Update check (optional).** When update checking is enabled, ONewPipe queries the
   public GitHub Releases API of this repository to compare version numbers. GitHub
   receives the request as any other download from GitHub would.
3. **Your own ONewPipe server (optional).** If you connect the app to a self-hosted
   ONewPipe server in **Settings → Server connection**, your username, password and the
   watch positions you choose to synchronize are sent to *your* server, at the address
   you entered. Nothing is sent to any server operated by the ONewPipe developers —
   there is none.

## Crash reports

Crash reports are shown to you inside the app. Nothing is uploaded automatically: a
report is only sent if you choose to send it yourself, to the destination you pick.

## Your rights over your data

All the data listed above is on your device. Uninstalling the application, clearing
its storage, or using the clear buttons in **Settings** removes it. If you run a
self-hosted server, the data on it is under your control and is removed by deleting
the server's data directory.

## Contact

Questions about this policy: open an issue on
<https://github.com/TeALO36/ONewPipe/issues>.
