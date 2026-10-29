# LiveTag + Webcam mod

The 27-slot `/live menu` has a LIVE toggle in slot 11 and a WEBCAM permission toggle in slot 15.

* `/live on` enables the tag, not the camera. `/live off` immediately revokes webcam broadcasting permission.
* Webcam is opt-in and stored as UUIDs in `plugins/LiveTag/data.yml` under `webcam-opt-in`.
* Webcam permission is **explicitly denied by default** for every online player (as Webcam's broadcast permission may default to true); eligible players get `webcam.broadcast=true`.
* A client must install the compatible Webcam client mod and voluntarily start the webcam; the GUI **never activates hardware**.
* This integration depends only on Bukkit permissions, not on Webcam's Java API. Install Webcam separately and open/configure its UDP port.

## Build

The project uses the upstream `pom.xml`: Java 25, Paper API 26.3 beta. Run `mvn clean package` using JDK 25 and working dependency access. The plugin JAR is then under `target/`. The supplied source ZIP is not a compiled JAR.

## Testing

1. Install Webcam and LiveTag on a *test* Paper server.
2. Grant streamer `livetag.use`.
3. Check that `webcam.broadcast` is denied before turning on LIVE.
4. `/live menu` > LIVE ON > WEBCAM ALLOWED: permission should become true; camera is still manually controlled by client.
5. LIVE OFF: permission should become false; restart/rejoin and repeat.
6. Verify the installed Webcam version reacts to dynamic permission changes within its documented polling interval. If not, a plugin-specific integration is needed.

### Cautions

* This release preserves webcam opt-in across LIVE OFF; turning LIVE back on restores the saved preference, but the client still controls capture.
* If permission managers or other plugins override the attachment, test the effective permission with your actual server stack.
* Tested by source inspection only; it has not been compiled or run on a server in this environment.
