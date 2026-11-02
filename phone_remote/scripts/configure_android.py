from pathlib import Path

manifest = Path("android/app/src/main/AndroidManifest.xml")
s = manifest.read_text()
permissions = """    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
"""
if 'android.permission.INTERNET' not in s:
    s = s.replace("<application", permissions + "    <application", 1)
s = s.replace('android:label="champaks_tv_remote"', 'android:label="Champak\'s TV Remote"')
# Android speech services must be discoverable on Android 11+.
if '<queries>' not in s:
    s=s.replace('<application', '''<queries>
        <intent><action android:name="android.speech.RecognitionService" /></intent>
    </queries>
    <application''',1)
# Bluetooth HID experiment only: paired devices and connect permission.
if 'android.permission.BLUETOOTH_CONNECT' not in s:
    s=s.replace('<application', '''<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-feature android:name="android.hardware.bluetooth" android:required="false" />
    <application''',1)
s = s.replace('<application', '<application android:usesCleartextTraffic="true"', 1)
manifest.write_text(s)
# Replace Flutter's generated activity with our Android HID bridge.
activity = Path('android/app/src/main/kotlin/live/learnwithchampak/champaks_tv_remote/MainActivity.kt')
activity.parent.mkdir(parents=True, exist_ok=True)
activity.write_text(Path('native/MainActivity.kt').read_text())
print('Bluetooth HID bridge installed')

print("Android network permissions and app display name configured")
