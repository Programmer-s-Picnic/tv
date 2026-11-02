import 'dart:async';
import 'dart:io';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ReceiverApp());
}

class ReceiverApp extends StatelessWidget {
  const ReceiverApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    title: "Champak's TV Installer",
    theme: ThemeData(useMaterial3: true, colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff2563eb)),
      scaffoldBackgroundColor: const Color(0xffeff5ff)),
    home: const ReceiverScreen(),
  );
}

class ReceiverScreen extends StatefulWidget {
  const ReceiverScreen({super.key});
  @override
  State<ReceiverScreen> createState() => _ReceiverScreenState();
}

class _ReceiverScreenState extends State<ReceiverScreen> {
  static const channel = MethodChannel('champaks.tv/installer');
  static const maxBytes = 300 * 1024 * 1024;
  HttpServer? server;
  late String pin;
  String ip = 'Checking network…';
  String state = 'Starting receiver…';
  double progress = 0;
  bool receiving = false;
  File? received;

  @override
  void initState() {
    super.initState();
    newPin();
    start();
  }

  void newPin() {
    final random = Random.secure();
    pin = (100000 + random.nextInt(900000)).toString();
  }

  Future<void> start() async {
    try {
      final interfaces = await NetworkInterface.list(type: InternetAddressType.IPv4);
      final addresses = <String>[];
      for (final network in interfaces) {
        for (final address in network.addresses) {
          if (!address.isLoopback && !address.address.startsWith('169.254.')) {
            addresses.add(address.address);
          }
        }
      }
      server = await HttpServer.bind(InternetAddress.anyIPv4, 8765);
      if (!mounted) return;
      setState(() {
        ip = addresses.isEmpty ? 'Find TV IP in Settings → Network' : addresses.first;
        state = 'Ready for an APK transfer';
      });
      server!.listen(receive, onError: (Object error) {
        if (mounted) setState(() => state = 'Receiver error: $error');
      });
    } catch (error) {
      if (mounted) setState(() => state = 'Could not start receiver: $error');
    }
  }

  void update(String value, {double? valueProgress}) {
    if (!mounted) return;
    setState(() {
      state = value;
      if (valueProgress != null) progress = valueProgress;
    });
  }

  Future<void> reply(HttpResponse response, int code, String text) async {
    response.statusCode = code;
    response.headers.contentType = ContentType.text;
    response.write(text);
    await response.close();
  }

  Future<void> receive(HttpRequest request) async {
    final response = request.response;
    response.headers.set('Access-Control-Allow-Origin', 'null');
    if (request.method == 'GET' && request.uri.path == '/health') {
      await reply(response, HttpStatus.ok, 'Champak TV Installer available');
      return;
    }
    if (request.method != 'POST' || request.uri.path != '/upload') {
      await reply(response, HttpStatus.notFound, 'Not found');
      return;
    }
    if (request.headers.value('x-champak-pin') != pin) {
      await reply(response, HttpStatus.unauthorized, 'Incorrect TV pairing code');
      return;
    }
    if (receiving) {
      await reply(response, HttpStatus.conflict, 'Another transfer is active');
      return;
    }
    final declared = request.contentLength;
    if (declared <= 0 || declared > maxBytes) {
      await reply(response, HttpStatus.requestEntityTooLarge, 'APK must be between 1 byte and 300 MB');
      return;
    }
    receiving = true;
    File? destination;
    IOSink? sink;
    try {
      final directory = await getApplicationSupportDirectory();
      destination = File('${directory.path}/incoming.apk');
      if (await destination.exists()) await destination.delete();
      sink = destination.openWrite();
      var total = 0;
      final signature = <int>[];
      update('Receiving APK from phone…', valueProgress: 0);
      await for (final chunk in request) {
        total += chunk.length;
        if (total > maxBytes || total > declared) throw const FormatException('Transfer exceeds allowed length');
        if (signature.length < 4) signature.addAll(chunk.take(4 - signature.length));
        sink.add(chunk);
        if (mounted) setState(() => progress = total / declared);
      }
      await sink.flush();
      await sink.close();
      sink = null;
      if (total != declared || signature.length != 4 ||
          signature[0] != 0x50 || signature[1] != 0x4b ||
          signature[2] != 0x03 || signature[3] != 0x04) {
        throw const FormatException('Incomplete transfer or invalid APK header');
      }
      received = destination;
      update('APK received. Select Install on the TV.', valueProgress: 1);
      await reply(response, HttpStatus.ok, 'Received. Approve installation on the TV.');
    } catch (error) {
      try { await sink?.close(); } catch (_) {}
      try { await destination?.delete(); } catch (_) {}
      update('Transfer failed: $error', valueProgress: 0);
      try { await reply(response, HttpStatus.badRequest, 'Transfer failed: $error'); } catch (_) {}
    } finally {
      receiving = false;
    }
  }

  Future<void> install() async {
    final file = received;
    if (file == null || !await file.exists()) return;
    try {
      final permitted = await channel.invokeMethod<bool>('canInstall') ?? false;
      if (!permitted) {
        update('Allow APK installation for Champak TV Installer, then return and tap Install again.');
        await channel.invokeMethod('installSettings');
        return;
      }
      await channel.invokeMethod('install', {'path': file.path});
      update('Installation prompt opened. Approve it on this TV.');
    } catch (error) {
      update('Cannot open installer: $error');
    }
  }

  @override
  void dispose() {
    server?.close(force: true);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(body: Center(child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 820),
      child: Padding(padding: const EdgeInsets.all(32), child: Column(
        mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Row(children: [
            Icon(Icons.tv_rounded, color: Color(0xff2563eb), size: 56),
            SizedBox(width: 18),
            Expanded(child: Text("Champak's TV Installer", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 32))),
          ]),
          const SizedBox(height: 24),
          const Text('One-time TV companion • Use only on a trusted home Wi-Fi network.',
            style: TextStyle(fontSize: 18)),
          const SizedBox(height: 22),
          SelectableText('TV address: $ip:8765', style: const TextStyle(fontSize: 27)),
          const SizedBox(height: 10),
          Text('One-time transfer PIN: $pin', style: const TextStyle(fontSize: 34, fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          Text(state, style: const TextStyle(fontSize: 19)),
          const SizedBox(height: 10),
          LinearProgressIndicator(value: receiving || progress > 0 ? progress : null, minHeight: 8),
          const SizedBox(height: 24),
          Row(children: [
            FilledButton.icon(onPressed: received == null ? null : install,
              icon: const Icon(Icons.install_mobile), label: const Text('Install received APK')),
            const SizedBox(width: 16),
            OutlinedButton(onPressed: receiving ? null : () => setState(newPin),
              child: const Text('Change transfer PIN')),
          ]),
          const SizedBox(height: 18),
          const Text('On your phone open TV Remote → Install → enter the TV address and PIN → choose an APK. Then approve installation here.',
            style: TextStyle(fontSize: 17)),
          const SizedBox(height: 8),
          const Text('Only transfer APKs from sources you trust. This receiver never installs silently.',
            style: TextStyle(fontSize: 15, color: Colors.black54)),
        ],
      )),
    )));
  }
}
