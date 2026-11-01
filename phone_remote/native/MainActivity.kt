package live.learnwithchampak.champaks_tv_remote

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
  private val channelName = "champaks.tv/bluetooth"
  private val adapter: BluetoothAdapter? get() = BluetoothAdapter.getDefaultAdapter()
  private var hid: BluetoothHidDevice? = null
  private var selectedDevice: BluetoothDevice? = null
  private var registered = false
  private var hidConnected = false
  private lateinit var channel: MethodChannel

  private fun permitted(): Boolean =
    Build.VERSION.SDK_INT < 31 ||
      checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

  // Consumer Control / Power, report id 1. Phones without HID_DEVICE cannot offer this.
  private val reportDescriptor = byteArrayOf(
    0x05, 0x0C, 0x09, 0x01, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x01,
    0x09, 0x30, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x01,
    0x81.toByte(), 0x02, 0x75, 0x07, 0x95.toByte(), 0x01, 0x81.toByte(), 0x03,
    0xC0.toByte()
  )

  private val hidCallback = object : BluetoothHidDevice.Callback() {
    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registeredNow: Boolean) {
      registered = registeredNow
      runOnUiThread { channel.invokeMethod("status", status()) }
    }
    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
      hidConnected = state == BluetoothProfile.STATE_CONNECTED
      if (hidConnected) selectedDevice = device
      runOnUiThread { channel.invokeMethod("status", status()) }
    }
  }

  private val profileListener = object : BluetoothProfile.ServiceListener {
    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
      if (profile != BluetoothProfile.HID_DEVICE) return
      hid = proxy as BluetoothHidDevice
      try {
        val sdp = BluetoothHidDeviceAppSdpSettings(
          "Champak TV Remote", "TV standby power experiment",
          "Champak Roy", BluetoothHidDevice.SUBCLASS1_COMBO, reportDescriptor)
        hid?.registerApp(sdp, null, null, java.util.concurrent.Executor { task ->
          runOnUiThread { task.run() }
        }, hidCallback)
      } catch (_: Exception) { registered = false }
      runOnUiThread { channel.invokeMethod("status", status()) }
    }
    override fun onServiceDisconnected(profile: Int) {
      hid = null
      registered = false
      hidConnected = false
      runOnUiThread { channel.invokeMethod("status", status()) }
    }
  }

  private fun status(): Map<String, Any> = mapOf(
    "androidSupported" to (Build.VERSION.SDK_INT >= 28),
    "bluetoothEnabled" to (adapter?.isEnabled == true),
    "permissionGranted" to permitted(),
    "hidProfileAvailable" to (hid != null),
    "hidRegistered" to registered,
    "hidConnected" to hidConnected,
    "selectedName" to (if (permitted()) try { selectedDevice?.name ?: "" } catch (_: Exception) { "" } else "")
  )

  override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
    super.configureFlutterEngine(flutterEngine)
    channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
    channel.setMethodCallHandler { call, result ->
      try {
        when(call.method) {
          "status" -> result.success(status())
          "requestPermission" -> {
            if (Build.VERSION.SDK_INT >= 31 && !permitted()) {
              requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 3077)
            }
            result.success(status())
          }
          "openBluetoothSettings" -> {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            result.success(true)
          }
          "initialize" -> {
            if (Build.VERSION.SDK_INT < 28) { result.error("UNSUPPORTED", "Android 9 or newer is required for HID Device", null); return@setMethodCallHandler }
            if (!permitted()) { result.error("PERMISSION", "Bluetooth permission is required", null); return@setMethodCallHandler }
            val a = adapter
            if (a == null || !a.isEnabled) { result.error("DISABLED", "Enable Bluetooth first", null); return@setMethodCallHandler }
            if (hid == null) {
              val accepted = a.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE)
              if (!accepted) { result.error("UNAVAILABLE", "This phone does not expose Bluetooth HID Device", null); return@setMethodCallHandler }
            }
            result.success(status())
          }
          "bonded" -> {
            if (!permitted()) { result.error("PERMISSION", "Grant Nearby devices permission", null); return@setMethodCallHandler }
            val devices = adapter?.bondedDevices?.map {
              mapOf("name" to (it.name ?: "Bluetooth device"), "address" to it.address)
            } ?: emptyList()
            result.success(devices)
          }
          "connect" -> {
            if (!permitted()) { result.error("PERMISSION", "Grant Nearby devices permission", null); return@setMethodCallHandler }
            val address = call.argument<String>("address") ?: ""
            val target = adapter?.bondedDevices?.firstOrNull { it.address == address }
            if (target == null) { result.error("NOT_PAIRED", "Pair the television in Android Bluetooth settings first", null); return@setMethodCallHandler }
            if (hid == null || !registered) { result.error("NOT_READY", "HID profile not ready; initialize and retry", null); return@setMethodCallHandler }
            selectedDevice = target
            val started = hid!!.connect(target)
            if (!started) result.error("CONNECT_FAILED", "TV rejected Bluetooth HID connection", null)
            else result.success(status())
          }
          "power" -> {
            if (!permitted() || !hidConnected || selectedDevice == null || hid == null) {
              result.error("NOT_CONNECTED", "Bluetooth HID is not connected to the TV", null)
            } else {
              val sent = hid!!.sendReport(selectedDevice, 1, byteArrayOf(1))
              if (sent) {
                android.os.Handler(mainLooper).postDelayed({
                  try { hid?.sendReport(selectedDevice, 1, byteArrayOf(0)) } catch (_: Exception) {}
                }, 110)
              }
              result.success(sent) // Sent means HID accepted packet; TV may ignore it.
            }
          }
          else -> result.notImplemented()
        }
      } catch(e: SecurityException) {
        result.error("PERMISSION", e.message ?: "Bluetooth permission denied", null)
      } catch(e: Exception) {
        result.error("BT_ERROR", e.message ?: "Bluetooth operation failed", null)
      }
    }
  }

  override fun onDestroy() {
    try { hid?.unregisterApp() } catch (_: Exception) {}
    try { hid?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) } } catch (_: Exception) {}
    super.onDestroy()
  }
}
