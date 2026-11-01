from pathlib import Path

manifest = Path("android/app/src/main/AndroidManifest.xml")
s = manifest.read_text()
permissions = """    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
"""
if 'android.permission.INTERNET' not in s:
    s = s.replace("<application", permissions + "    <application", 1)
s = s.replace('android:label="champaks_tv_remote"', 'android:label="Champak\'s TV Remote"')
manifest.write_text(s)
print("Android network permissions and app display name configured")
