# Champak's TV Remote

Flutter Android phone remote starter in Programmer-s-Picnic/tv.

## Included
- Polished light-blue remote screen
- Touchpad and keyboard layouts
- Browser shortcut controls
- Program planner with day, time, channel and reminder lead-time settings
- Local persistence of program entries

## Current limitations
TV pairing, actual TV commands and Android notification delivery are not yet implemented. Buttons are clearly marked as prototypes; program reminders are stored locally but no device notifications are scheduled.

## Build APK
On GitHub choose Actions > Build Champak Phone Remote APK > Run workflow. Download the build artifact after success.

Local commands from phone_remote:
1. flutter create --platforms android --org live.learnwithchampak .
2. flutter pub get
3. flutter build apk --release

The workflow has not yet been verified by a successful build.
