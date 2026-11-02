# Champak's TV Remote — Google Play upload signing

App ID: `live.learnwithchampak.champaks_tv_remote`  
Release: `1.2.0` (version code `13`)  
Target SDK in the signed bundle: `36`  
Upload certificate SHA-256:
`F8:90:F0:3B:72:17:AD:FC:59:A2:AB:A5:D4:13:1D:90:4A:C1:B4:03:A6:D7:D1:F0:75:2C:A9:F9:A9:FC:22:A9`

## Private key

A new 4096-bit RSA PKCS#12 upload keystore was generated separately from
GitHub. Its alias is `champak_remote_upload`. The private keystore and
password are **not** in this repository and must **never** be committed.

The separately supplied signed V1.2 AAB has been JAR-signed with this key,
verified with `jarsigner -verify`, and is for a new Google Play listing using
Play App Signing. Google generates and protects the app signing key; this
keystore is an **upload key**, not the Google app signing key.

For an **existing** Play listing, first compare Play Console's upload
certificate against this certificate. If different, use the existing upload key
or request an upload-key reset before uploading. A mismatched key will be
rejected.

## Safe future release signing

1. Keep two offline backups of the private `.p12` file and its password
   in separate protected locations.
2. In a private CI environment, use the same keystore/alias/password for
   subsequent upload builds. Do not embed them in Gradle files or source code.
3. In GitHub Actions, store the base64 keystore bytes, store password,
   and alias in **Actions secrets** rather than in repository files.
4. For Gradle release builds, configure `signingConfigs.release` from
   environment variables and run `flutter build appbundle --release`.
5. Confirm the output signature fingerprint matches the SHA-256 above:
   `jarsigner -verify -verbose app-release.aab`.
6. Increment the version code for each new Play Console upload.

The GitHub APK published before this setup used the Flutter-generated
signing configuration and is intended for device testing; it does not
have the new upload-key signature. Do not assume it can be installed as an
update over a Play-distributed app.

## Publication checklist

- New Play listing package ID must match the bundle exactly.
- Android API 36 target requirement applies to new phone applications
  from 31 August 2026.
- Complete store listing, privacy policy, Data Safety, permissions,
  screenshots and other policy declarations.
- Test on real phones and Android TV hardware. URL shortcuts are
  best-effort and the program planner currently does not schedule Android
  notifications; do not claim unimplemented capabilities.
- Upload the correctly signed AAB to Internal testing, address Play
  pre-launch findings, and complete required testing tracks before
  Production access.

Documentation:
https://support.google.com/googleplay/android-developer/answer/9842756
https://developer.android.com/google/play/requirements/target-sdk
