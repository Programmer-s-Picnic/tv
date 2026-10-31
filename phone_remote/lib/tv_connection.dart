import 'package:android_tv_remote/android_tv_remote.dart';
import 'package:path_provider/path_provider.dart';

class TvLink {
  static final TvLink instance = TvLink._();
  TvLink._();
  AndroidTVRemote get remote => AndroidTVRemote.instance;
  String? connectedName;
  Future<void> initialize() async {
    final dir = await getApplicationDocumentsDirectory();
    await AndroidTVRemote.init(basePath: dir.path);
  }
  bool get connected => remote.state == ConnState.connected;
  Future<List<TvDevice>> discover() => remote.discoverDevices(timeout: const Duration(seconds: 6));
  Future<void> connect(TvDevice device, Future<String> Function() pin) async {
    await remote.connect(device, onPairingCode: pin);
    if (!connected) throw StateError('TV did not confirm connection');
    connectedName = device.name;
  }
  Future<void> connectIp(String ip, Future<String> Function() pin) async {
    final host = ip.trim();
    if (!RegExp(r'^(\d{1,3}\.){3}\d{1,3}$').hasMatch(host)) {
      throw const FormatException('Enter a valid IPv4 address');
    }
    final device = TvDevice(id: host, name:'Android TV ($host)',
      host:host, controlPort:6466, pairingPort:6467);
    await connect(device,pin);
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
      'Left click':RemoteKeyCode.dpadCenter,
      'Right click':RemoteKeyCode.menu,
      'Reload':RemoteKeyCode.refresh,
      'Fullscreen':RemoteKeyCode.f11,
      'Settings':RemoteKeyCode.settings,
      'New tab':RemoteKeyCode.t,
      'Switch tab':RemoteKeyCode.tab,
      'Focus':RemoteKeyCode.tab,
      'Address':RemoteKeyCode.search,
      'Bookmarks':RemoteKeyCode.bookmark,
      'Downloads':RemoteKeyCode.notification,
      'History':RemoteKeyCode.notification,
      'Click':RemoteKeyCode.dpadCenter,
    };
    final code = keys[name];
    if(code==null) throw UnsupportedError('Command not supported: $name');
    remote.sendKey(code);
  }
  void text(String value) => remote.sendText(value);
}
