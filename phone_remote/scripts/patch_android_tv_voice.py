#!/usr/bin/env python3
"""Patch the pinned android_tv_remote source used by Flutter's pub cache.

Voice support uses the TV's existing mutually authenticated TLS connection.
The upstream package offers keys/text but no public voice APIs. This patch
is intentionally build-local; no upstream files or private keys are published.
Fail on unexpected upstream changes instead of silently shipping dead controls.
"""
import json
from pathlib import Path
from urllib.parse import urlparse, unquote


def package_path(name):
    config_file = Path(".dart_tool/package_config.json").resolve()
    obj = json.loads(config_file.read_text())
    entry = next(item for item in obj["packages"] if item["name"] == name)
    uri = entry["rootUri"]
    if uri.startswith("file:"):
        return Path(unquote(urlparse(uri).path)).resolve()
    return (config_file.parent / unquote(uri)).resolve()


def replace_once(source, before, after, label):
    count = source.count(before)
    if count != 1:
        raise RuntimeError(f"{label}: expected one anchor, got {count}")
    return source.replace(before, after, 1)


root = package_path("android_tv_remote")
public_path = root / "lib/android_tv_remote.dart"
ctrl_path = root / "lib/src/protocol/control.dart"

public = public_path.read_text()
ctrl = ctrl_path.read_text()

public = replace_once(
    public,
    "import 'dart:async';",
    "import 'dart:async';\nimport 'dart:typed_data';",
    "public imports",
)
public = replace_once(
    public,
    "  void sendKey(RemoteKeyCode key, {KeyAction action = KeyAction.short}) {",
    """  /// Open an Android TV Remote v2 voice session on the *paired* TLS socket.
  Future<int> beginVoice() {
    _assertConnected();
    return _control.beginVoice();
  }

  void sendVoiceAudio(Uint8List samples) {
    _assertConnected();
    _control.sendVoiceAudio(samples);
  }

  void endVoice() {
    _control.endVoice();
  }

  void sendKey(RemoteKeyCode key, {KeyAction action = KeyAction.short}) {""",
    "public voice API",
)
ctrl = replace_once(
    ctrl,
    "import 'dart:async';",
    "import 'dart:async';\nimport 'dart:typed_data';",
    "control imports",
)
ctrl = replace_once(
    ctrl,
    "  void Function()? onDisconnected;",
    """  void Function()? onDisconnected;
  StreamSubscription<List<int>>? _voiceSubscription;
  Completer<int>? _pendingVoice;
  int? _voiceSessionId;
  final List<int> _voiceBytes = [];""",
    "control voice fields",
)
ctrl = replace_once(
    ctrl,
    "  void respondPing() {",
    """  // Protobuf wire-format helpers: varint, nested length-delimited message.
  List<int> _voiceVarint(int value) => _encodeVarint(value);

  List<int> _voiceFrame(int field, List<int> inner) => [
    ..._voiceVarint((field << 3) | 2),
    ..._voiceVarint(inner.length),
    ...inner,
  ];

  int? _incomingVoiceSession(List<int> msg) {
    // Field 30 (RemoteVoiceBegin) is a length-delimited nested message.
    var offset = 0;
    while (offset < msg.length) {
      final key = _readVoiceVarint(msg, offset);
      if (key == null) return null;
      offset = key.$2;
      final field = key.$1 >> 3;
      final wire = key.$1 & 7;
      if (wire == 2) {
        final length = _readVoiceVarint(msg, offset);
        if (length == null) return null;
        offset = length.$2;
        final end = offset + length.$1;
        if (end > msg.length) return null;
        if (field == 30) {
          final inner = msg.sublist(offset, end);
          final innerKey = _readVoiceVarint(inner, 0);
          if (innerKey?.$1 != 8) return null; // field 1 int32 session_id
          return _readVoiceVarint(inner, innerKey!.$2)?.$1;
        }
        offset = end;
      } else if (wire == 0) {
        final value = _readVoiceVarint(msg, offset);
        if (value == null) return null;
        offset = value.$2;
      } else {
        return null;
      }
    }
    return null;
  }

  (int, int)? _readVoiceVarint(List<int> bytes, int start) {
    var value = 0;
    for (var i = start; i < bytes.length && i < start + 10; i++) {
      value |= (bytes[i] & 127) << (7 * (i - start));
      if ((bytes[i] & 128) == 0) return (value, i + 1);
    }
    return null;
  }

  /// Ask the TV to start voice search. The TV must reply with a session ID.
  Future<int> beginVoice() async {
    if (!isConnected) throw StateError('Connect the TV before voice search');
    if (_pendingVoice != null || _voiceSessionId != null) {
      throw StateError('Another voice session is active');
    }
    final pending = Completer<int>();
    _pendingVoice = pending;
    await _voiceSubscription?.cancel();
    _voiceSubscription = incoming.listen((msg) {
      if (pending.isCompleted) return;
      final id = _incomingVoiceSession(msg);
      if (id != null && id > 0) pending.complete(id);
    });
    try {
      // KEYCODE_SEARCH signals the TV voice-search service to offer a session.
      sendKey(RemoteKeyCode.search, action: KeyAction.short);
      final id = await pending.future.timeout(const Duration(seconds: 3));
      if (!isConnected) throw StateError('TV disconnected during voice start');
      _voiceSessionId = id;
      _voiceBytes.clear();
      sendMessage(_voiceFrame(30, [8, ..._voiceVarint(id)]));
      return id;
    } finally {
      _pendingVoice = null;
      await _voiceSubscription?.cancel();
      _voiceSubscription = null;
    }
  }

  /// 16-bit little-endian PCM at 8 kHz, mono; send in >=8KiB chunks.
  void sendVoiceAudio(Uint8List samples) {
    if (_voiceSessionId == null || !isConnected) return;
    _voiceBytes.addAll(samples);
    while (_voiceBytes.length >= 8192) {
      final chunk = _voiceBytes.sublist(0, 8192);
      _voiceBytes.removeRange(0, 8192);
      _sendVoiceChunk(chunk);
    }
  }

  void _sendVoiceChunk(List<int> bytes) {
    final id = _voiceSessionId;
    if (id == null || !isConnected) return;
    // Sample payload must be between 8KiB and 20KiB on compatible TVs.
    final padded = bytes.length >= 8192
      ? bytes
      : [...bytes, ...List<int>.filled(8192 - bytes.length, 0)];
    final inner = <int>[
      8, ..._voiceVarint(id),
      18, ..._voiceVarint(padded.length), ...padded,
    ];
    sendMessage(_voiceFrame(31, inner));
  }

  void endVoice() {
    final id = _voiceSessionId;
    _voiceSessionId = null;
    if (id == null) return;
    try {
      if (isConnected) {
        if (_voiceBytes.isNotEmpty) {
          // Final partial audio frame, padded to TV's minimum.
          final bytes = List<int>.from(_voiceBytes);
          final padded = bytes.length >= 8192
            ? bytes
            : [...bytes, ...List<int>.filled(8192 - bytes.length, 0)];
          sendMessage(_voiceFrame(31, [
            8, ..._voiceVarint(id),
            18, ..._voiceVarint(padded.length), ...padded,
          ]));
        }
        sendMessage(_voiceFrame(32, [8, ..._voiceVarint(id)]));
      }
    } finally {
      _voiceBytes.clear();
    }
  }

  void respondPing() {""",
    "control voice protocol",
)
ctrl = replace_once(
    ctrl,
    """  void disconnect() {
    _keepaliveTimer?.cancel();""",
    """  void disconnect() {
    endVoice();
    _voiceSubscription?.cancel();
    _voiceSubscription = null;
    _keepaliveTimer?.cancel();""",
    "control voice disconnect",
)

public_path.write_text(public)
ctrl_path.write_text(ctrl)
print("Patched Android TV Remote v2 voice sessions using existing TLS socket:", root)
