from pathlib import Path
manifest = Path("android/app/src/main/AndroidManifest.xml")
s = manifest.read_text()
permissions = """    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.INTERNET" />
"""
if "android.permission.POST_NOTIFICATIONS" not in s:
    s = s.replace("<application", permissions + "    <application", 1)
manifest.write_text(s)
print("Android manifest ready")
