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

## Home Assistant Operating System (HAOS)

To use Periscope with the Frigate HAOS add-on, you need to expose a port.

1. Open the HAOS web interface.
2. Navigate to:  
   **Settings → Add-ons → Frigate → Configuration**
3. In the **Network** section, enable **Show disabled ports**.
4. Map the port of either:
   - **Authenticated Web interface**  
   - **Unauthenticated Web interface**  

Then use that port and your HAOS IP in Periscope settings.

## FAQ

### How to setup a connection?
Fill in the host field (IP or domain), port, and protocol. HTTPS is supported on Android 4.1 and later.
If you're using the authenticated port, fill in the user and password fields, otherwise leave them empty.

### How to set up Birdseye?
Make sure you have Birdseye and restream enabled in Frigate's config:
```yaml
birdseye:
  enabled: true
  restream: true
```
**birdseye** should then appear in the camera list in Periscope.

### What's the difference between single-threaded and multi-threaded display implementation?
Apart from threading, multi-threaded display uses less memory but suffers from minor visual glitches. There are more implementation nuances.

Choose the one that works best for you.

### JPG or WEBP?
It's a network vs CPU tradeoff. WEBP can work on slower networks but uses more CPU, especially on the server. WEBP is supported on Android 4.4 and later.

### What is the timeout setting?
Specifies how long to wait for a response from Frigate before displaying the loading indicator.

### What is autostart?
Periscope can start automatically after booting up the device. This is especially useful if you disable the lock screen in the developer settings. Android >= 10 requires additional permissions. Autostart probably won't work on Android >= 13, and it's better to disable it then.

### How do I watch a camera over a show on Android TV?
In Periscope's settings, open **Watch over TV** and choose **Status**: it opens the TV's accessibility settings, where you turn on **Periscope: watch a camera over TV** (Android 5.1 and later). Then, while anything is playing, an app or an HDMI input, hold **Info** on the remote: a camera list appears, and the camera you pick floats in a corner over the show. Hold **Info** again to change or close it.

- No Info button, or rather tap twice? Pick another under **Remote button**, and set **Open with** to a hold or a double press. Buttons that would also act on the show (arrows, OK, Back, Home, power, volume, play/pause) can't be chosen.
- While a camera is showing, the list opens on **Close camera**, so closing it takes the button and OK.
- The corner, size (up to most of the screen) and transparency are in the same settings. The list follows your camera selection and order, unless you pick its own cameras under **Cameras in the list**, for example to keep a private camera out of it.
- To look closer, open the list while a camera floats and choose **Full screen** (one up from **Close camera**); with nothing floating, **Open Periscope** opens the grid. Back returns to what was playing.
- The service only reacts to that button, and to Up, Down, OK and Back while the list is open; it doesn't read the screen.
- If the accessibility setting is greyed out ("restricted setting"), open Periscope's app info, choose **Allow restricted settings** from the menu, and try again.
- Some TVs (e.g. TCL) also require allowing autostart for Periscope before the service can run.

## TODO
- Adaptive resolution
- ~RTSP(S) support~ (WIP)

[yt-review]: https://www.youtube.com/watch?v=KiajZnkMgUQ "Click to watch the review"

## ❤️ Sponsor
If you like **Periscope**, please consider [becoming a sponsor](https://github.com/sponsors/maksz42)!
