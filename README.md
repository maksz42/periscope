# Periscope for Frigate NVR

![Matrix view screenshot](screenshots/matrix.png)

Upcycle your old Android tablet into a live viewer for Frigate NVR and hang it on a wall.\
**Note**: This is not a full Frigate client, it's only a live view.

## Compatibility
- **Android:** 2.2 and up (also Android TV)

## FAQ

### How to setup a connection?
Fill in the host field (IP or domain), port, and protocol. Android <5.0 doesn't support HTTPS.
If you're using the protected port, fill in the user and password fields, otherwise leave them empty.

### What's the difference between ImageView and SurfaceView display implementation?
- **ImageView:** Single-threaded.
- **SurfaceView:** Multithreaded and may use less memory. Minor visual glitches.

Choose the one that works best for you.

## TODO
- Adaptive resolution
- RTSP(S) support
