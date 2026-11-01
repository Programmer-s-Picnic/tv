import 'package:flutter/services.dart';

/// Experimental Bluetooth HID Device support. Not a generic Bluetooth TV remote.
class BluetoothTvPower {
  static const _channel = MethodChannel('champaks.tv/bluetooth');
  static Future<Map<String,dynamic>> status() async =>
    Map<String,dynamic>.from(await _channel.invokeMethod('status') ?? {});
  static Future<Map<String,dynamic>> requestPermission() async =>
    Map<String,dynamic>.from(await _channel.invokeMethod('requestPermission') ?? {});
  static Future<Map<String,dynamic>> initialize() async =>
    Map<String,dynamic>.from(await _channel.invokeMethod('initialize') ?? {});
  static Future<List<Map<String,dynamic>>> bondedDevices() async {
    final raw = await _channel.invokeMethod<List<dynamic>>('bonded') ?? [];
    return raw.map((e)=>Map<String,dynamic>.from(e as Map)).toList();
  }
  static Future<void> openSettings() async => _channel.invokeMethod('openBluetoothSettings');
  static Future<Map<String,dynamic>> connect(String mac) async =>
    Map<String,dynamic>.from(await _channel.invokeMethod('connect', {'address':mac}) ?? {});
  static Future<bool> power() async => await _channel.invokeMethod<bool>('power') ?? false;
}
