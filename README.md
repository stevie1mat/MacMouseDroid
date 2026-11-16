# MacMouseDroid

Local prototype: use a Mac mouse to move a pointer on an Android phone’s own screen. Android 8 or later. No root or USB debugging required for normal use.

## Run

1. Install `dist/MouseForMobile.apk` on the phone.
2. Open Mouse for Mobile and enable its Accessibility service. For sideloaded apps, Android may require App info → menu → Allow restricted settings first.
3. Put both devices on the same trusted Wi-Fi, and tap **Start sharing** on the phone.
4. Open `dist/Mouse for Mobile.app` on the Mac. Enter the phone’s IPv4 address (including `:8765`) and pairing code.
5. Click **Control phone**. Look at the phone and move your Mac mouse. Escape releases it; switching away from the Mac window also releases it.
6. Tap **Stop sharing** on the phone when finished. Releasing the Mac mouse does not stop phone sharing.

Click taps, right click goes Back, scrolling swipes, H goes Home, Space long presses. Drag-and-drop, typing, automatic screen-edge switching, mirroring and control while the phone is locked are not implemented. Unlock the phone before use. Some apps and system screens may limit accessibility gestures. OEM battery management may stop the service.

## Connection and privacy

The Android Accessibility service draws a noninteractive pointer overlay and performs gestures; it does not request screen-content access. Sharing opens TCP port 8765 on the phone until stopped or the service is destroyed. A random 48-bit pairing code changes each session. Commands and the code are unencrypted, so this prototype is for trusted private networks only. Do not expose the port to the internet. Guest Wi-Fi client isolation and VPNs may prevent connections.

The Mac app captures mouse events only while its own window is active. No macOS Accessibility permission is needed. It releases the cursor on Escape, loss of window focus, a connection error or normal exit.

## Build

Run `bash scripts/build.sh`. Uses installed Android SDK platform 35, build tools 36.0.0, JDK 17, and the macOS Swift toolchain. Override `ANDROID_SDK_ROOT` and `MOUSE_JAVA_HOME` if needed. APK uses a local debug signing key stored in `build/`; the Mac app is ad-hoc signed, not notarized. No Gradle dependencies are downloaded.

Build/signature checks do not replace physical-device testing. Test cursor direction, tap placement, scrolling, orientation changes, disconnect recovery, stopping sharing and releasing the Mac cursor before relying on it.

## Interface

The Android app uses a warm ivory and forest green interface with separate setup and sharing states. Connection details can be copied, and the setup screen links to App info for restricted settings. Sharing remains disabled until the Accessibility service is enabled.
