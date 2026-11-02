package live.learnwithchampak.champaks_tv_installer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.FileProvider
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File

class MainActivity: FlutterActivity() {
  private val channel = "champaks.tv/installer"

  override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
    super.configureFlutterEngine(flutterEngine)
    MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channel).setMethodCallHandler { call, result ->
      try {
        when (call.method) {
          "canInstall" -> result.success(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()
          )
          "installSettings" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName"))
              startActivity(intent)
            }
            result.success(true)
          }
          "install" -> {
            val path = call.argument<String>("path") ?: ""
            val file = File(path)
            val safeRoot = applicationContext.filesDir.canonicalFile
            if (!file.canonicalFile.toPath().startsWith(safeRoot.toPath()) ||
                file.name != "incoming.apk" || !file.isFile) {
              result.error("INVALID_FILE", "Unexpected APK path", null)
            } else {
              val uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file)
              val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
              }
              startActivity(intent)
              result.success(true)
            }
          }
          else -> result.notImplemented()
        }
      } catch (e: Exception) {
        result.error("INSTALL_ERROR", e.message, null)
      }
    }
  }
}
