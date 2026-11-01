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
  String? selectedId;
  bool reconnecting = false;
  String? connectedName;

  bool get connected => remote.state == ConnState.connected;
  ConnState get state => remote.state;
  List<TvDevice> get knownDevices => _known.values.toList();

  Future<void> initialize() async {
    final dir = await getApplicationDocumentsDirectory();
    await AndroidTVRemote.init(basePath: dir.path);
    _subscription = remote.onStateChanged.listen((state) {
      if (state == ConnState.disconnected || state == ConnState.error) {
        connectedName = null;
      }
      _states.add(state);
    });
    final prefs = await SharedPreferences.getInstance();
    selectedId = prefs.getString('preferred_tv_id');
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
  }

  Future<void> _persist() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('known_tvs_v2', jsonEncode(_known.values.map((d)=>d.toJson()).toList()));
    if(selectedId != null) await prefs.setString('preferred_tv_id', selectedId!);
  }

  Future<List<TvDevice>> discover() async {
    final found = await remote.discoverDevices(timeout: const Duration(seconds: 6));
    for (final d in found) { _known[d.id] = d; }
    await _persist();
    return found;
  }

  Future<void> connect(TvDevice device, Future<String> Function() askPin) async {
    _known[device.id] = device;
    await _persist();
    await remote.connect(device, onPairingCode: askPin);
    if (!connected) throw StateError('The TV did not confirm the connection');
    connectedName = device.name;
    selectedId = device.id;
    await _persist();
    _states.add(remote.state);
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

  Future<bool> reconnectPreferred() async {
    if (connected || selectedId == null || reconnecting) return connected;
    final device=_known[selectedId];
    if(device == null) return false;
    reconnecting = true;
    try {
      // With an existing certificate, the library does not ask for a PIN.
      // If credentials are lost, do not open an unexpected pairing dialog.
      await remote.connect(device, onPairingCode: () async {
        throw StateError('Pairing code required. Select the TV to pair again.');
      }).timeout(const Duration(seconds: 12));
      if(connected){connectedName=device.name;_states.add(remote.state);}
      return connected;
    } catch (_) {return false;}
    finally {reconnecting = false;}
  }

  void disconnect() {
    remote.disconnect();
    connectedName=null;
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
      'Vol +':RemoteKeyCode.volumeUp,'Media':RemoteKeyCode.mediaPlayPause,
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

  void text(String value) {
    if(!connected) throw StateError('No TV connected');
    remote.sendText(value);
  }
}
