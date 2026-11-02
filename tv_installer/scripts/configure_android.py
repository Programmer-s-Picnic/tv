from pathlib import Path
manifest = Path('android/app/src/main/AndroidManifest.xml')
s = manifest.read_text()
s = s.replace('<application', '''<uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    <uses-feature android:name="android.software.leanback" android:required="false" />
    <application android:usesCleartextTraffic="true" android:banner="@mipmap/ic_launcher"''', 1)
s = s.replace('android:label="champaks_tv_installer"', 'android:label="Champak TV Installer"')
s = s.replace('</application>', '''<provider
         android:name="androidx.core.content.FileProvider"
         android:authorities="${applicationId}.fileprovider"
         android:exported="false"
         android:grantUriPermissions="true">
         <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths"/>
       </provider>
    </application>''')
s = s.replace('<category android:name="android.intent.category.LAUNCHER"/>', '''<category android:name="android.intent.category.LAUNCHER"/>
                <category android:name="android.intent.category.LEANBACK_LAUNCHER"/>''')
manifest.write_text(s)
xml_dir = Path('android/app/src/main/res/xml')
xml_dir.mkdir(parents=True, exist_ok=True)
(xml_dir/'file_paths.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android"><files-path name="apks" path="."/></paths>
''')
activity = Path('android/app/src/main/kotlin/live/learnwithchampak/champaks_tv_installer/MainActivity.kt')
activity.parent.mkdir(parents=True, exist_ok=True)
activity.write_text(Path('native/MainActivity.kt').read_text())
print('TV installation permissions and activity installed')
