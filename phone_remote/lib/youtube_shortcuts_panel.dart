import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'tv_connection.dart';
import 'youtube_history_panel.dart';

/// YouTube favourites are stored locally for the selected television.
/// Sending a deep link is best-effort; Android TV Remote v2 has no app-open ACK.
class YoutubeShortcutsPanel extends StatefulWidget {
  const YoutubeShortcutsPanel({super.key});

  @override
  State<YoutubeShortcutsPanel> createState() => _YoutubeShortcutsPanelState();
}

class _YoutubeShortcutsPanelState extends State<YoutubeShortcutsPanel> {
  static const blue = Color(0xff2563eb);
  List<Map<String, String>> favourites = [];
  String? loadedTv;
  bool loading = true;
  bool sending = false;

  String get tvId => TvLink.instance.selectedId ?? '_unselected';
  String keyFor(String id) => 'youtube_favourites_v1_${Uri.encodeComponent(id)}';

  @override
  void initState() {
    super.initState();
    reload();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (loadedTv != tvId) reload();
  }

  Future<void> reload() async {
    final id = tvId;
    loadedTv = id;
    final prefs = await SharedPreferences.getInstance();
    List<Map<String, String>> parsed = [];
    try {
      final raw = prefs.getString(keyFor(id));
      if (raw != null) {
        final list = jsonDecode(raw) as List<dynamic>;
        parsed = list.map((item) => Map<String, String>.from(
          (item as Map).map((k, v) => MapEntry(k.toString(), v.toString())),
        )).where((item) => item['name']?.isNotEmpty == true &&
            item['url']?.isNotEmpty == true).toList();
      }
    } catch (_) {
      // Retain an empty, usable screen if old stored data is invalid.
    }
    if (mounted && id == tvId) {
      setState(() {
        favourites = parsed;
        loading = false;
      });
    }
  }

  Future<void> persist() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(keyFor(tvId), jsonEncode(favourites));
  }

  /// Accept common Youtube links without admitting arbitrary URL schemes/hosts.
  /// Keep the original watch/list/channel URL usable as a web fallback.
  String? normalizedUrl(String input, String kind) {
    var candidate = input.trim();
    if (candidate.isEmpty) return null;
    if (!candidate.contains('://')) candidate = 'https://$candidate';
    final uri = Uri.tryParse(candidate);
    if (uri == null || uri.scheme != 'https' || uri.userInfo.isNotEmpty) return null;
    final hostname = uri.host.toLowerCase();
    final youtube = hostname == 'youtube.com' ||
        hostname == 'www.youtube.com' ||
        hostname == 'm.youtube.com' ||
        hostname == 'music.youtube.com';
    final short = hostname == 'youtu.be' || hostname == 'www.youtu.be';
    if (!youtube && !short) return null;
    if (kind == 'Channel') {
      if (!youtube) return null;
      if (!(uri.path.startsWith('/@') ||
          uri.path.startsWith('/channel/') ||
          uri.path.startsWith('/c/') ||
          uri.path.startsWith('/user/'))) return null;
      return uri.replace(scheme: 'https', host: 'www.youtube.com',
        fragment: '').toString();
    }
    if (kind == 'Video') {
      final videoId = short ? (uri.pathSegments.isEmpty ? '' : uri.pathSegments.first)
        : (uri.path == '/watch' ? uri.queryParameters['v'] ?? ''
          : uri.path.startsWith('/shorts/') ? uri.pathSegments.last : '');
      if (!RegExp(r'^[A-Za-z0-9_-]{11}$').hasMatch(videoId)) return null;
      return 'https://www.youtube.com/watch?v=$videoId';
    }
    if (kind == 'Playlist') {
      if (!youtube) return null;
      final id = uri.queryParameters['list'] ?? '';
      if (!(uri.path == '/playlist' || uri.path == '/watch') ||
          !RegExp(r'^[A-Za-z0-9_-]{10,}$').hasMatch(id)) return null;
      return 'https://www.youtube.com/playlist?list=$id';
    }
    return null;
  }

  Future<void> edit({int? index}) async {
    final existing = index == null ? <String, String>{} : favourites[index];
    final name = TextEditingController(text: existing['name'] ?? '');
    final url = TextEditingController(text: existing['url'] ?? '');
    var type = existing['type'] ?? 'Channel';
    var error = '';
    final data = await showDialog<Map<String, String>>(
      context: context, builder: (dialog) => StatefulBuilder(
        builder: (dialog, update) => AlertDialog(
          title: Text(index == null ? 'Add YouTube shortcut' : 'Edit YouTube shortcut'),
          content: SingleChildScrollView(child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              DropdownButtonFormField<String>(
                value: type,
                decoration: const InputDecoration(labelText: 'Shortcut type'),
                items: ['Channel', 'Video', 'Playlist'].map((value) =>
                  DropdownMenuItem(value: value, child: Text(value))).toList(),
                onChanged: (value) { if (value != null) update(() => type = value); },
              ),
              const SizedBox(height: 12),
              TextField(controller: name, maxLength: 45,
                textCapitalization: TextCapitalization.words,
                decoration: const InputDecoration(
                  labelText: 'Display name', hintText: 'My favourite channel')),
              const SizedBox(height: 8),
              TextField(controller: url, keyboardType: TextInputType.url,
                autocorrect: false,
                decoration: InputDecoration(
                  labelText: 'YouTube link',
                  hintText: type == 'Channel'
                      ? 'youtube.com/@ChannelName'
                      : type == 'Video'
                          ? 'youtu.be/VIDEO_ID'
                          : 'youtube.com/playlist?list=...',
                )),
              const SizedBox(height: 8),
              if (error.isNotEmpty) Text(error,
                style: const TextStyle(color: Colors.red, fontSize: 12)),
              const Text('Link launching is device-dependent. Open the link '
                'in your TV browser if the YouTube app does not respond.',
                style: TextStyle(fontSize: 12, color: Colors.black54)),
            ],
          )),
          actions: [
            TextButton(onPressed: () => Navigator.pop(dialog),
              child: const Text('Cancel')),
            FilledButton(onPressed: () {
              final title = name.text.trim();
              final validated = normalizedUrl(url.text, type);
              if (title.isEmpty || validated == null) {
                update(() => error = 'Enter a name and a valid YouTube $type link.');
                return;
              }
              Navigator.pop(dialog, {
                'name': title, 'url': validated, 'type': type,
              });
            }, child: const Text('Save')),
          ],
        ),
      ),
    );
    name.dispose();
    url.dispose();
    if (data == null || !mounted) return;
    setState(() {
      if (index == null) {
        favourites.add(data);
      } else {
        favourites[index] = data;
      }
    });
    await persist();
  }

  Future<void> remove(int index) async {
    final confirm = await showDialog<bool>(context: context, builder: (dialog) =>
      AlertDialog(
        title: Text('Remove ${favourites[index]['name']}?'),
        content: const Text('Only this saved shortcut will be removed.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialog, false),
            child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(dialog, true),
            child: const Text('Remove')),
        ],
      ));
    if (confirm != true || !mounted) return;
    setState(() => favourites.removeAt(index));
    await persist();
  }

  void showMessage(String message) {
    if (!mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    messenger.clearSnackBars();
    messenger.showSnackBar(SnackBar(
      behavior: SnackBarBehavior.floating,
      duration: const Duration(seconds: 3),
      content: Text(message, maxLines: 2, overflow: TextOverflow.ellipsis),
    ));
  }

  Future<void> launch(Map<String, String> item, {bool webFallback = false}) async {
    if (sending) return;
    setState(() => sending = true);
    try {
      if (!TvLink.instance.connected &&
          !await TvLink.instance.reconnectPreferred()) {
        throw StateError('TV is offline. Reconnect before opening links.');
      }
      final link = item['url']!;
      // Android TV Remote v2 sends launch requests, not guaranteed launches.
      // A web link is the fallback request; it cannot force a particular browser.
      TvLink.instance.remote.launchApp(link);
      // Record what the remote requested, not a claim of confirmed playback.
      try {
        await YoutubeHistoryStore.record(
          title: item['name'] ?? 'YouTube shortcut',
          url: link,
          type: item['type'] ?? 'Video',
          source: 'YouTube Favourites',
        );
      } catch (_) {
        // A local history write failure must not misreport TV launch status.
      }
      showMessage(webFallback
        ? 'Web link requested on TV. Choose the TV browser if prompted.'
        : '${item['type']} launch requested. Check the television.');
    } catch (e) {
      showMessage('Could not send YouTube link: $e');
    } finally {
      if (mounted) setState(() => sending = false);
    }
  }

  IconData iconFor(String? type) {
    if (type == 'Playlist') return Icons.playlist_play_rounded;
    if (type == 'Video') return Icons.ondemand_video_rounded;
    return Icons.video_library_rounded;
  }

  @override
  Widget build(BuildContext context) {
    if (loadedTv != tvId) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) reload();
      });
    }
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Row(children: [
        const Icon(Icons.smart_display_rounded, color: Colors.red, size: 28),
        const SizedBox(width: 10),
        const Expanded(child: Text('My YouTube Favourites',
          style: TextStyle(fontSize: 21, fontWeight: FontWeight.bold))),
        IconButton(onPressed: () => edit(), tooltip: 'Add YouTube shortcut',
          icon: const Icon(Icons.add_circle, color: blue)),
      ]),
      Text(TvLink.instance.selectedId == null
        ? 'Select a TV to save its YouTube favourites.'
        : 'Saved for ${TvLink.instance.preferredName}',
        style: const TextStyle(color: Colors.black54)),
      const SizedBox(height: 12),
      if (loading) const LinearProgressIndicator(),
      if (!loading && favourites.isEmpty)
        const Padding(padding: EdgeInsets.symmetric(vertical: 18),
          child: Text('No favourites yet. Add a channel, video or playlist.')),
      ...favourites.asMap().entries.map((entry) {
        final i = entry.key;
        final item = entry.value;
        return Card(
          margin: const EdgeInsets.only(bottom: 9),
          color: Colors.white,
          child: Padding(padding: const EdgeInsets.symmetric(vertical: 5),
            child: Row(children: [
              IconButton(
                onPressed: sending ? null : () => launch(item),
                icon: Icon(iconFor(item['type']), color: Colors.red, size: 29),
                tooltip: 'Open ${item['name']}'),
              Expanded(child: InkWell(
                onTap: sending ? null : () => launch(item),
                child: Padding(padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(item['name'] ?? 'YouTube shortcut',
                        style: const TextStyle(fontWeight: FontWeight.w600)),
                      Text('${item['type']} · ${item['url']}',
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontSize: 11, color: Colors.black54)),
                    ],
                  )),
              )),
              PopupMenuButton<String>(
                tooltip: 'YouTube shortcut options',
                onSelected: (action) async {
                  if (action == 'web') await launch(item, webFallback: true);
                  if (action == 'edit') await edit(index: i);
                  if (action == 'remove') await remove(i);
                  if (action == 'up' && i > 0) {
                    setState(() {
                      final value = favourites.removeAt(i);
                      favourites.insert(i - 1, value);
                    });
                    await persist();
                  }
                  if (action == 'down' && i < favourites.length - 1) {
                    setState(() {
                      final value = favourites.removeAt(i);
                      favourites.insert(i + 1, value);
                    });
                    await persist();
                  }
                },
                itemBuilder: (context) => [
                  const PopupMenuItem(value: 'web',
                    child: Text('Try web link')),
                  const PopupMenuItem(value: 'edit', child: Text('Edit')),
                  if (i > 0) const PopupMenuItem(value: 'up', child: Text('Move up')),
                  if (i < favourites.length - 1)
                    const PopupMenuItem(value: 'down', child: Text('Move down')),
                  const PopupMenuItem(value: 'remove', child: Text('Remove')),
                ],
              ),
            ]),
          ),
        );
      }),
      OutlinedButton.icon(
        onPressed: () => edit(),
        icon: const Icon(Icons.add),
        label: const Text('Add Channel, Video or Playlist')),
      const SizedBox(height: 8),
      const Text('Channel links, videos and playlists are saved per TV. '
        'The remote can request URLs but cannot confirm that YouTube or '
        'a TV browser opened them.',
        style: TextStyle(fontSize: 11, color: Colors.black54)),
    ]);
  }
}
