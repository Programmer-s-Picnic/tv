# Champak's TV Remote — v0.3 release candidate

Flutter phone remote for compatible Android TV / Google TV devices.

## Features implemented
- Wi-Fi TV discovery, manual IP entry, six-character PIN pairing
- Remote v2 key commands: navigation, Home, Back, volume and media buttons
- Phone keyboard text send
- Swipe-to-directional navigation
- Locally saved favorite TV program schedules
- Dedicated TV Browser shortcut layout (not all shortcuts supported yet)

## Limitations
Pairing and commands require Android TV Remote v2 support and have not been tested on the owner's physical television. Swipe navigation is directional focus navigation, not a full mouse pointer. TV Browser-specific shortcuts require further TV Browser integration. Programs are saved locally; notifications are not yet delivered.

## Branding and publishing
- Vector artwork: `branding/app_icon.svg`
- Generate launcher and Play graphics: `python3 scripts/generate_branding.py`
- Apply launcher icons: `dart run flutter_launcher_icons` (after `flutter pub get`)
- Distribution workflow in `json-images` generates the branded APK and Play graphics.
- Play submission checklist: [PLAY_STORE_RELEASE.md](PLAY_STORE_RELEASE.md)
- Privacy policy: https://programmer-s-picnic.github.io/json-images/tv/champaks-tv-remote-privacy.html

## Build locally
Run:
```bash
flutter create --platforms android --org live.learnwithchampak .
python3 scripts/configure_android.py
flutter pub get
python3 -m pip install pillow cairosvg
python3 scripts/generate_branding.py
dart run flutter_launcher_icons
flutter build apk --release
```

For Google Play, configure production signing and build an Android App Bundle after device testing and legal/dependency review. The development APK workflow is not a production-signing substitute.
