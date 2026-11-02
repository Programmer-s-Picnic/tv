import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Phone-side sender for the optional Champak TV Installer companion.
/// A LAN transfer is not encrypted: use only on a trusted private Wi-Fi.
class TvInstallPanel extends StatefulWidget {
  final String initialHost;
  const TvInstallPanel({super.key, required this.initialHost});
  @override
  State<TvInstallPanel> createState() => _TvInstallPanelState();
}

class _TvInstallPanelState extends State<TvInstallPanel> {
  final host = TextEditingController();
  final pin = TextEditingController();
  File? apk;
  String status = 'Install Champak TV Installer on your television first.';
  bool sending = false;
  double progress = 0;
  HttpClient? activeClient;

  @override
  void initState() {
    super.initState();
    host.text = widget.initialHost;
    restore();
  }

  Future<void> restore() async {
    final prefs = await SharedPreferences.getInstance();
    if (!mounted) return;
    if (host.text.trim().isEmpty) host.text = prefs.getString('installer_tv_host') ?? '';
  }

  @override
  void dispose() {
    activeClient?.close(force:true);
    host.dispose();
    pin.dispose();
    super.dispose();
  }

  Future<void> chooseApk() async {
    try {
      final choice = await FilePicker.platform.pickFiles(
        type:FileType.custom, allowedExtensions:['apk'], withData:false);
      if (choice == null || choice.files.single.path == null) return;
      final selected = File(choice.files.single.path!);
      final bytes = await selected.length();
      if (bytes < 4 || bytes > 300 * 1024 * 1024) {
        update('APK must be less than 300 MB.');
        return;
      }
      if (!mounted) return;
      setState(() { apk = selected; progress=0; status='Selected: ${choice.files.single.name}'; });
    } catch(e) { update('Cannot select APK: $e'); }
  }

  void update(String message) {
    if (mounted) setState(() => status=message);
  }

  Future<void> send() async {
    if (sending || apk == null) return;
    final address = host.text.trim();
    final code = pin.text.trim();
    if (InternetAddress.tryParse(address)?.type != InternetAddressType.IPv4) {
      update('Enter the IPv4 address shown on the TV Installer screen.');
      return;
    }
    if (!RegExp(r'^\d{6}$').hasMatch(code)) {
      update('Enter the six-digit transfer PIN shown on TV.');
      return;
    }
    setState(() { sending=true;progress=0; status='Connecting to TV Installer…'; });
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('installer_tv_host',address);
      final file = apk!;
      final length = await file.length();
      final client = HttpClient()..connectionTimeout=const Duration(seconds:12);
      activeClient = client;
      final request = await client.post(address,8765,'/upload');
      request.headers.set('x-champak-pin',code);
      request.headers.set(HttpHeaders.contentTypeHeader,'application/vnd.android.package-archive');
      request.contentLength=length;
      var written=0;
      await for(final chunk in file.openRead()) {
        request.add(chunk);
        written+=chunk.length;
        if(mounted) setState(() {
          progress=written/length;
          status='Transferring APK… ${(progress*100).toStringAsFixed(0)}%';
        });
      }
      final response = await request.close();
      final message = await response.transform(SystemEncoding().decoder).join();
      if(response.statusCode!=200) throw StateError('TV rejected transfer (${response.statusCode}): $message');
      update('Transfer completed. On the TV select Install received APK and approve Android’s installer.');
    } catch(e) {
      update('Transfer failed: $e. Check Wi-Fi, the TV receiver and PIN.');
    } finally {
      activeClient?.close(force:true);
      activeClient=null;
      if(mounted) setState(()=>sending=false);
    }
  }

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment:CrossAxisAlignment.stretch,children:[
      Card(child:Padding(padding:const EdgeInsets.all(16),child:Column(
        crossAxisAlignment:CrossAxisAlignment.start,children:[
          const Text('Install APK to TV',style:TextStyle(fontSize:22,fontWeight:FontWeight.bold)),
          const SizedBox(height:8),
          const Text('One-time setup: install Champak TV Installer on the television. Keep it open during transfers.'),
          const SizedBox(height:15),
          TextField(controller:host,keyboardType:TextInputType.number,
            decoration:const InputDecoration(labelText:'TV IP address',hintText:'192.168.1.10')),
          const SizedBox(height:10),
          TextField(controller:pin,keyboardType:TextInputType.number,maxLength:6,
            decoration:const InputDecoration(labelText:'Six-digit TV transfer PIN')),
          const SizedBox(height:10),
          OutlinedButton.icon(onPressed:sending?null:chooseApk,
            icon:const Icon(Icons.folder_open),label:const Text('Choose APK from phone')),
          if(apk!=null)Padding(padding:const EdgeInsets.only(top:8),child:
            Text(apk!.uri.pathSegments.last,overflow:TextOverflow.ellipsis)),
          const SizedBox(height:12),
          if(sending) LinearProgressIndicator(value:progress),
          const SizedBox(height:12),
          FilledButton.icon(onPressed:sending||apk==null?null:send,
            icon:const Icon(Icons.send_to_mobile),label:Text(sending?'Sending…':'Send APK to TV')),
          const SizedBox(height:10),
          SelectableText(status),
        ]))),
      const SizedBox(height:10),
      const Text('Security: Transfer only to a TV you control, on trusted Wi-Fi. PIN-protected local HTTP is not encrypted. Android requires TV-side approval to install APKs.',
        style:TextStyle(fontSize:12,color:Colors.black54)),
    ]);
}
