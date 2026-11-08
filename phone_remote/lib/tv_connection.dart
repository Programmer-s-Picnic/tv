import 'dart:async';
import 'dart:convert';
import 'package:android_tv_remote/android_tv_remote.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Local TV selection and encrypted Android TV Remote v2 connection.
class TvLink {
  TvLink._();
  static final TvLink instance = TvLink._();
  AndroidTVRemote get remote => AndroidTVRemote.instance;
  final StreamController<ConnState> _states = StreamController<ConnState>.broadcast();
  Stream<ConnState> get states => _states.stream;
  StreamSubscription<ConnState>? _subscription;
  final Map<String, TvDevice> _known = {};
  final Map<String, String> _aliases = {};
  String displayName(TvDevice device) => _aliases[device.id] ?? device.name;
  String get preferredName => selectedId == null ? 'Android TV' : (_known[selectedId] == null ? 'Android TV' : displayName(_known[selectedId]!));
  String? selectedId;
  bool reconnecting = false;
  String connectionMessage = 'Not connected';
  DateTime? lastConnected;
  Future<bool>? _ongoingReconnect;
  int _retryIndex = 0;
  Timer? _retryTimer;
  bool _active = true;
  bool _userSwitching = false;
  String? connectedName;

  bool get connected => remote.state == ConnState.connected;
  ConnState get state => remote.state;
  List<TvDevice> get knownDevices => _known.values.toList();
  String get preferredHost => _known[selectedId]?.host ?? '';

  Future<void> initialize() async {
    final dir = await getApplicationDocumentsDirectory();
    await AndroidTVRemote.init(basePath: dir.path);
    _subscription = remote.onStateChanged.listen((state) {
      if (state == ConnState.disconnected || state == ConnState.error) {
        connectedName = null;
        if (_active && !_userSwitching && selectedId != null) _scheduleRetry();
      } else if(state == ConnState.connected) {
        _retryIndex = 0;
        _retryTimer?.cancel();
        connectionMessage = 'Connected';
        lastConnected = DateTime.now();
      }
      _states.add(state);
    });
    final prefs = await SharedPreferences.getInstance();
    selectedId = prefs.getString('preferred_tv_id');
    try {
      final names = jsonDecode(prefs.getString('tv_aliases_v1') ?? '{}') as Map;
      for (final e in names.entries) {
        if (e.key is String && e.value is String && (e.value as String).trim().isNotEmpty) {
          _aliases[e.key as String] = (e.value as String).trim();
        }
      }
    } catch (_) {}
    try {
      final devices = jsonDecode(prefs.getString('known_tvs_v2') ?? '[]') as List<dynamic>;
      for (final item in devices) {
        final d = TvDevice.fromJson(Map<String, dynamic>.from(item as Map));
        _known[d.id] = d;
      }
    } catch (_) {
      // Keep the app usable even when old saved settings are malformed.
    }
    // Also merge TV devices which were stored by the remote protocol library.
    try {
      for (final item in await remote.getSavedDevices()) {
        if (item.host == null || item.host!.isEmpty) continue;
        final d = TvDevice(id:item.id,name:item.renamed,host:item.host!,
          controlPort:item.controlPort,pairingPort:item.pairingPort,
          model:item.model,vendor:item.vendor);
        _known[d.id] = d;
      }
    } catch (_) {}
    await _persist();
    if (selectedId == null && _known.length == 1) {
      selectedId = _known.keys.first;
      await _persist();
    }
  }

  /// Reattempt only while the app is active. Background OS restrictions apply.
  void setActive(bool active) {
    _active = active;
    if (!active) {
      _retryTimer?.cancel();
    } else if (!connected && selectedId != null) {
      _retryIndex = 0;
      _scheduleRetry(immediate:true);
    }
  }

  void _scheduleRetry({bool immediate=false}) {
    if (!_active || selectedId == null || connected || _userSwitching) return;
    if (_retryTimer?.isActive == true || reconnecting) return;
    const seconds = [2, 4, 8, 15, 30];
    final delay = immediate ? 0 : seconds[_retryIndex.clamp(0, seconds.length-1).toInt()];
    if (!immediate && _retryIndex < seconds.length-1) _retryIndex++;
    _retryTimer = Timer(Duration(seconds:delay), () async {
      _retryTimer = null;
      if (!_active || connected || _userSwitching) return;
      await reconnectPreferred();
      if (!connected) _scheduleRetry();
    });
  }

  Future<void> _persist() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('known_tvs_v2', jsonEncode(_known.values.map((d)=>d.toJson()).toList()));
    await prefs.setString('tv_aliases_v1', jsonEncode(_aliases));
    if(selectedId != null) { await prefs.setString('preferred_tv_id', selectedId!); }
    else { await prefs.remove('preferred_tv_id'); }
  }

  /// A local display nickname; does not change the remote certificate/device ID.
  Future<void> renameTv(String id, String name) async {
    if (!_known.containsKey(id)) throw StateError('Saved TV not found');
    final nickname = name.trim();
    if (nickname.isEmpty || nickname.length > 40) {
      throw const FormatException('Use a name between 1 and 40 characters');
    }
    _aliases[id] = nickname;
    if (selectedId == id && connected) connectedName = nickname;
    await _persist();
    _states.add(remote.state);
  }

  /// Forget one TV, including stored remote credentials. Never affects other TVs.
  Future<void> forgetTv(String id) async {
    if (!_known.containsKey(id)) return;
    _retryTimer?.cancel();
    _userSwitching = true;
    try {
      if (_ongoingReconnect != null) {
        try { await _ongoingReconnect; } catch (_) {}
      }
      if (selectedId == id) {
        if (connected) remote.disconnect();
        selectedId = null;
        connectedName = null;
        connectionMessage = 'Choose your television';
      }
      _known.remove(id);
      _aliases.remove(id);
      await remote.removeSavedDevice(id);
      await _persist();
    } finally {
      _userSwitching = false;
      _states.add(remote.state);
    }
  }

  Future<List<TvDevice>> discover() async {
    final found = await remote.discoverDevices(timeout: const Duration(seconds: 6));
    for (final d in found) { _known[d.id] = d; }
    await _persist();
    return found;
  }

  Future<void> connect(TvDevice device, Future<String> Function() askPin) async {
    _retryTimer?.cancel();
    _userSwitching = true;
    // Let an existing reconnect finish before the user selects another TV.
    if (_ongoingReconnect != null) {
      try { await _ongoingReconnect; } catch (_) {}
    }
    try {
      _known[device.id] = device;
      await _persist();
      connectionMessage = 'Connecting to ${displayName(device)}';
      _states.add(remote.state);
      await remote.connect(device, onPairingCode: askPin);
      if (!connected) throw StateError('The TV did not confirm the connection');
      connectedName = displayName(device);
      selectedId = device.id;
      _retryIndex = 0;
      connectionMessage = 'Connected';
      lastConnected = DateTime.now();
      await _persist();
      _states.add(remote.state);
    } finally {
      _userSwitching = false;
      if (!connected) _scheduleRetry();
    }
  }

  Future<void> connectIp(String ip, Future<String> Function() askPin) async {
    final host=ip.trim();
    final parts=host.split('.');
    if(parts.length!=4 || parts.any((p)=>int.tryParse(p)==null || int.parse(p)<0 || int.parse(p)>255)) {
      throw const FormatException('Enter a valid IPv4 address');
    }
    final matched=_known.values.where((d)=>d.host==host);
    final device=matched.isNotEmpty?matched.first:TvDevice(
      id:host,name:'Android TV ($host)',host:host,controlPort:6466,pairingPort:6467);
    await connect(device,askPin);
  }

  /// Silent reconnection using the existing per-device pairing certificate.
  /// Never invokes a pairing dialog; retry only on a future attempt if offline.
  Future<bool> reconnectPreferred() {
    if (connected) return Future.value(true);
    if (selectedId == null || _userSwitching) return Future.value(false);
    if (_ongoingReconnect != null) return _ongoingReconnect!;
    final future = _reconnectAttempt();
    _ongoingReconnect = future;
    return future.whenComplete(() { _ongoingReconnect = null; });
  }

  Future<bool> _reconnectAttempt() async {
    final device = _known[selectedId];
    if (device == null) return false;
    reconnecting = true;
    connectionMessage = 'Reconnecting to ${displayName(device)}';
    _states.add(remote.state);
    try {
      // On a saved certificate, the remote library reconnects without a PIN.
      await remote.connect(device, onPairingCode: () async {
        throw StateError('Pairing credentials expired. Re-pair this TV once.');
      }).timeout(const Duration(seconds: 12));
      if (connected) {
        connectedName = device.name;
        lastConnected = DateTime.now();
        connectionMessage = 'Connected';
        _retryIndex = 0;
        _retryTimer?.cancel();
        _states.add(remote.state);
        return true;
      }
      connectionMessage = 'TV not responding. Retrying automatically';
    } catch(e) {
      final message = e.toString();
      connectionMessage = message.contains('Pairing credentials expired')
        ? 'TV pairing expired; tap to pair again'
        : 'TV offline or unreachable. Reconnecting automatically';
    } finally {
      reconnecting = false;
      _states.add(remote.state);
    }
    if (_active) _scheduleRetry();
    return false;
  }

  void disconnect() {
    _retryTimer?.cancel();
    remote.disconnect();
    connectedName=null;
    connectionMessage = 'Disconnected';
    _states.add(remote.state);
  }

  void key(String name) {
    const keys = <String,RemoteKeyCode>{
      'Up':RemoteKeyCode.dpadUp,'Down':RemoteKeyCode.dpadDown,
      'Left':RemoteKeyCode.dpadLeft,'Right':RemoteKeyCode.dpadRight,
      'OK':RemoteKeyCode.dpadCenter,'Back':RemoteKeyCode.back,
      'Home':RemoteKeyCode.home,'Apps':RemoteKeyCode.appSwitch,
      'Power':RemoteKeyCode.power,'Volume -':RemoteKeyCode.volumeDown,
      'Volume +':RemoteKeyCode.volumeUp,'Vol -':RemoteKeyCode.volumeDown,
      'Vol +':RemoteKeyCode.volumeUp,'Mute / Unmute':RemoteKeyCode.volumeMute,
      'Mute':RemoteKeyCode.volumeMute,'Media':RemoteKeyCode.mediaPlayPause,
      'Play/Pause':RemoteKeyCode.mediaPlayPause,
      'Left click':RemoteKeyCode.dpadCenter,'Right click':RemoteKeyCode.menu,
      'Reload':RemoteKeyCode.refresh,'Fullscreen':RemoteKeyCode.f11,
      'Settings':RemoteKeyCode.settings,'New tab':RemoteKeyCode.t,
      'Switch tab':RemoteKeyCode.tab,'Focus':RemoteKeyCode.tab,
      'Address':RemoteKeyCode.search,'Bookmarks':RemoteKeyCode.bookmark,
      'Downloads':RemoteKeyCode.notification,'History':RemoteKeyCode.notification,
      'Click':RemoteKeyCode.dpadCenter,
    };
    if(!connected) throw StateError('No TV connected');
    final code=keys[name];
    if(code==null) throw UnsupportedError('Command not supported: $name');
    remote.sendKey(code);
  }

  /// Request YouTube with an HTTPS app link. Using a bare package name makes
  /// this remote library rewrite it to market://launch?id=..., which is not
  /// consistently supported by Android TV and can silently do nothing.
  Future<void> openYouTube({bool alternate=false}) async {
    if (!connected) {
      final ok = await reconnectPreferred();
      if (!ok) throw StateError('TV is not connected. Reconnection is in progress.');
    }
    remote.launchApp(alternate
      ? 'https://www.youtube.com/'
      : 'https://www.youtube.com/tv');
  }

  /// Toggle TV standby via the paired remote v2 channel.
  /// Some sets drop Wi-Fi or stop their remote service in standby.
  Future<void> togglePower() async {
    if (!connected) {
      final recovered = await reconnectPreferred();
      if (!recovered) {
        throw StateError('TV is unreachable. Check its standby network settings or power it on with its original remote.');
      }
    }
    remote.sendKey(RemoteKeyCode.power);
  }

  void text(String value) {
    if(!connected) throw StateError('No TV connected');
    remote.sendText(value);
  }

  /// Replace the focused TV input's contents rather than appending.
  ///
  /// Remote v2 exposes key events and text injection, but cannot directly
  /// inspect/edit TV text fields. Ctrl+A followed by Delete is the standard
  /// select-all mechanism when the focused TV editor supports it.
  /// If a TV app/keyboard ignores Ctrl+A, replacement cannot be guaranteed
  /// without a TV-side accessibility/IME companion.
  bool _replacingText = false;
  Future<void> replaceText(String value) async {
    if (_replacingText) {
      throw StateError('Text replacement is already in progress.');
    }
    if (!connected) throw StateError('No TV connected');
    _replacingText = true;
    var controlHeld = false;
    try {
      remote.sendKeyPress(RemoteKeyCode.ctrlLeft);
      controlHeld = true;
      await Future<void>.delayed(const Duration(milliseconds: 80));
      remote.sendKey(RemoteKeyCode.a);
      await Future<void>.delayed(const Duration(milliseconds: 130));
      remote.sendKeyRelease(RemoteKeyCode.ctrlLeft);
      controlHeld = false;
      await Future<void>.delayed(const Duration(milliseconds: 130));
      remote.sendKey(RemoteKeyCode.del);
      await Future<void>.delayed(const Duration(milliseconds: 200));
      // An empty string intentionally clears the field.
      if (value.isNotEmpty) remote.sendText(value);
    } finally {
      if (controlHeld && connected) {
        try { remote.sendKeyRelease(RemoteKeyCode.ctrlLeft); }
        catch (_) {}
      }
      _replacingText = false;
    }
  }
}
