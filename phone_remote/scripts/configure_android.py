from pathlib import Path
manifest = Path("android/app/src/main/AndroidManifest.xml")
s = manifest.read_text()
permissions = """    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.INTERNET" />\n    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />\n    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
"""
if "android.permission.POST_NOTIFICATIONS" not in s:
    s = s.replace("<application", permissions + "    <application", 1)
manifest.write_text(s)
print("Android manifest ready")
