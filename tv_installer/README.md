# Champak's TV Installer — one-time Android TV companion

The phone remote cannot install third-party APKs through the Android TV Remote v2 protocol alone. This companion is a separate TV app that receives an APK on the local network and invokes Android's normal package installer after the viewer approves it.

## First-time TV setup
1. Download `champaks-tv-installer-v1.0.apk` from the json-images distribution repo.
2. Install it ON THE TV one time using a USB drive or the TV's browser/downloader/file manager. Approve the Android "install unknown apps" setting for that initial installer if necessary.
3. Keep the phone and TV on the same **trusted private** Wi-Fi network.
4. Launch **Champak TV Installer** on the television. It displays a local IPv4 address and a newly generated six-digit transfer PIN.
5. Open Champak's TV Remote on the phone and select the **Install** tab. Enter the TV IP address and the PIN.
6. Select a valid Android TV-compatible APK (300 MB maximum) and transfer it.
7. Press **Install received APK** on the television. Allow unknown apps for Champak TV Installer if Android requests it; then approve installation using Android's normal installer UI.

## Important limitations and security
- The TV installer must be open and reachable for file transfers; no system-wide background receiver.
- PIN changes when the TV app restarts; only the TV address is retained on the phone.
- Local plain HTTP is **not encrypted**; PIN reduces accidental/unauthorized uploads but is not protection against active attackers or traffic interception on the same LAN. Do not use public Wi-Fi.
- Only one transfer at a time is accepted, max 300 MB, and APK header/declared content length are validated; Android is the final authority on signatures, package compatibility, updates, and installation permissions.
- Android's installation approval cannot be bypassed. This is not an unattended or silent application installer.
- The companion has no external server, account, ads or analytics.
- Do not use this for APKs from untrusted sources. Installing APKs from outside Play may be restricted by managed-device policies and Play distribution requirements.
- Both artifacts require physical-device acceptance testing before general publication.

## Implementation
- `lib/main.dart`: foreground `HttpServer` on TCP port 8765; per-session PIN, streaming APK upload, file check, install approval UI.
- `native/MainActivity.kt`: Android `FileProvider`, unknown-apps permission check and standard `ACTION_VIEW` installer.
- `scripts/configure_android.py`: modifies the generated Flutter Android manifest, sets TV launcher category and native bridge.
- Phone side: `phone_remote/lib/tv_install_panel.dart`.
