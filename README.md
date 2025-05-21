# Periscope for Frigate NVR

![Matrix view screenshot](screenshots/matrix.png)

Upcycle your old Android tablet into a live viewer for Frigate NVR and hang it on a wall.\
**Note**: This is not a full Frigate client, it's only a live view.

➡️ [Check out Periscope v0.2 review by @mostlychris][yt-review]

[![video review thumbnail](https://img.youtube.com/vi/KiajZnkMgUQ/mqdefault.jpg)][yt-review]

## Compatibility
- **Android:** 2.2 and up (also Android TV)

## Play Protect note
Some releases are falsely marked as harmful by Play Protect.

During the installation, you might need to tap **`More details` → `Install anyway`**, or temporarily disable Play Protect in the Play Store.

## FAQ

### How to setup a connection?
Fill in the host field (IP or domain), port, and protocol. Android <5.0 doesn't support HTTPS.
If you're using the protected port, fill in the user and password fields, otherwise leave them empty.

### What's the difference between single-threaded and multi-threaded display implementation?
Apart from threading, multi-threaded display uses less memory but suffers from minor visual glitches. There are more implementation nuances.

Choose the one that works best for you.

### JPG or WEBP?
It's a network vs CPU tradeoff. WEBP can work on slower networks but uses more CPU, especially on the server. WEBP is supported on Android 4.4 and later.

### What is the timeout setting?
Specifies how long to wait for a response from Frigate before displaying the loading indicator.


## TODO
- Adaptive resolution
- RTSP(S) support

[yt-review]: https://www.youtube.com/watch?v=KiajZnkMgUQ "Click to watch the review"

## ❤️ Sponsor
If you like **Periscope**, please consider [becoming a sponsor](https://github.com/sponsors/maksz42)!
