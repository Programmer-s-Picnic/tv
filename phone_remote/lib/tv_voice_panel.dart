import 'dart:async';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:record/record.dart';
import 'package:android_tv_remote/android_tv_remote.dart';
import 'tv_connection.dart';

/// Streams live microphone PCM over the *existing* paired Android TV v2 socket.
/// TV voice search must explicitly acknowledge a voice session first.
class TvVoicePanel extends StatefulWidget {
  final Future<void> Function()? beforeStart;
  const TvVoicePanel({super.key, this.beforeStart});
  @override
  State<TvVoicePanel> createState() => _TvVoicePanelState();
}

class _TvVoicePanelState extends State<TvVoicePanel>
    with WidgetsBindingObserver {
  final AudioRecorder recorder = AudioRecorder();
  StreamSubscription<Uint8List>? microphone;
  StreamSubscription<ConnState>? tvStates;
  Timer? maxDuration;
  bool desired = false;
  bool starting = false;
  bool streaming = false;
  bool disposed = false;
  int? finger;
  String status = 'Hold the microphone to search on the TV';
  static const blue = Color(0xff2563eb);

  void showStatus(String value) {
    if (mounted && !disposed) setState(() => status = value);
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    tvStates = TvLink.instance.states.listen((state) {
      if (state == ConnState.disconnected || state == ConnState.error) {
        release();
      }
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) release();
  }

  void press() {
    if (desired || starting || streaming) return;
    desired = true;
    begin();
  }

  void release() {
    desired = false;
    if (!starting) stop();
  }

  Future<void> begin() async {
    if (starting || streaming || !desired) return;
    starting = true;
    showStatus('Connecting to TV voice search…');
    try {
      if (!TvLink.instance.connected) {
        if (!await TvLink.instance.reconnectPreferred()) {
          throw StateError('TV unavailable. Reconnect to your saved TV.');
        }
      }
      if (!desired) return;
      if (widget.beforeStart != null) await widget.beforeStart!();
      if (!desired) return;

      // A microphone permission prompt can temporarily pause the app.
      if (!await recorder.hasPermission()) {
        throw StateError('Microphone permission denied. Enable it in Android settings.');
      }
      if (!desired) return;

      // The remote protocol waits for the TV to return a voice session ID.
      // Unsupported televisions fail here, before opening the microphone.
      await TvLink.instance.remote.beginVoice();
      if (!desired) return;

      final pcmStream = await recorder.startStream(const RecordConfig(
        encoder: AudioEncoder.pcm16bits,
        sampleRate: 8000,
        numChannels: 1,
        autoGain: true,
      ));
      streaming = true;
      microphone = pcmStream.listen((chunk) {
        if (!desired || !TvLink.instance.connected) {
          release();
          return;
        }
        try {
          TvLink.instance.remote.sendVoiceAudio(chunk);
        } catch (e) {
          showStatus('Voice stream stopped: $e');
          release();
        }
      }, onError: (Object error) {
        showStatus('Microphone error: $error');
        release();
      });

      maxDuration?.cancel();
      maxDuration = Timer(const Duration(seconds: 30), () {
        showStatus('Voice input stopped after 30 seconds');
        release();
      });
      showStatus('LIVE · Speak into your phone. Release to finish.');
    } catch (e) {
      desired = false;
      final message = e.toString();
      if (message.contains('TimeoutException')) {
        showStatus('TV did not accept a voice session. Use Speak to Type below.');
      } else {
        showStatus('TV microphone unavailable: $e');
      }
    } finally {
      starting = false;
      if (!desired) await stop();
    }
  }

  Future<void> stop() async {
    maxDuration?.cancel();
    maxDuration = null;
    final sub = microphone;
    microphone = null;
    await sub?.cancel();
    try {
      if (streaming) await recorder.stop();
    } catch (_) {}
    streaming = false;
    try {
      TvLink.instance.remote.endVoice();
    } catch (_) {}
    if (mounted && !disposed && !starting) {
      setState(() {});
    }
  }

  @override
  void dispose() {
    disposed = true;
    desired = false;
    WidgetsBinding.instance.removeObserver(this);
    tvStates?.cancel();
    stop().whenComplete(() => recorder.dispose());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final active = streaming && desired;
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Row(children: [
        Icon(active ? Icons.mic : Icons.mic_none_rounded,
          color: active ? Colors.red : blue, size: 28),
        const SizedBox(width: 10),
        const Expanded(child: Text('Direct TV microphone',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 17))),
        if (active) const Text('LIVE',
          style: TextStyle(fontWeight: FontWeight.bold, color: Colors.red)),
      ]),
      const SizedBox(height: 12),
      Listener(
        onPointerDown: (e) {
          if (finger == null) {
            finger = e.pointer;
            press();
          }
        },
        onPointerUp: (e) {
          if (finger == e.pointer) {
            finger = null;
            release();
          }
        },
        onPointerCancel: (e) {
          if (finger == e.pointer) {
            finger = null;
            release();
          }
        },
        child: Container(
          height: 82,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: active ? const Color(0xffb91c1c) : const Color(0xff245fd2),
            borderRadius: BorderRadius.circular(20)),
          child: Row(mainAxisAlignment: MainAxisAlignment.center,children: [
            Icon(active ? Icons.graphic_eq : Icons.mic,
              color: Colors.white, size: 34),
            const SizedBox(width: 12),
            Text(active ? 'RELEASE TO FINISH' : 'HOLD TO TALK',
              style: const TextStyle(color: Colors.white,
                fontWeight: FontWeight.bold, fontSize: 17)),
          ]),
        ),
      ),
      const SizedBox(height: 8),
      Text(status, style: TextStyle(fontSize: 12,
        color: active ? Colors.red.shade800 : Colors.black54)),
      const SizedBox(height: 5),
      const Text('TV voice search only. Uses 8 kHz mono PCM over your paired Wi-Fi remote connection. '
        'If your TV does not support voice sessions, use Speak to Type below.',
        style: TextStyle(fontSize: 11, color: Colors.black54)),
    ]);
  }
}
