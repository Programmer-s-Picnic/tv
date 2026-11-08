import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'tv_connection.dart';

/// This is a local history of requests made by Champak's TV Remote.
/// Android TV Remote v2 does not expose YouTube's account viewing history.
class YoutubeHistoryStore {
  static const int maximumEntries = 150;
  static final ValueNotifier<int> revision = ValueNotifier<int>(0);

  static String keyFor(String tvId) =>
      'youtube_history_v1_${Uri.encodeComponent(tvId)}';

  static Future<List<Map<String, String>>> read(String tvId) async {
    final prefs = await SharedPreferences.getInstance();
    try {
      final raw = jsonDecode(prefs.getString(keyFor(tvId)) ?? '[]') as List;
      return raw.map((value) => Map<String, String>.from(
        (value as Map).map((key, item) =>
            MapEntry(key.toString(), item.toString())),
      )).where((entry) => (entry['url'] ?? '').isNotEmpty).toList();
    } catch (_) {
      return <Map<String, String>>[];
    }
  }

  static Future<void> record({
    required String title,
    required String url,
    required String type,
    String source = 'Remote',
    String? televisionId,
  }) async {
    final id = televisionId ?? TvLink.instance.selectedId;
    if (id == null) return;
    final entries = await read(id);
    entries.removeWhere((entry) => entry['url'] == url);
    entries.insert(0, {
      'title': title.trim().isEmpty ? 'YouTube link' : title.trim(),
      'url': url,
      'type': type,
      'source': source,
      'date': DateTime.now().toIso8601String(),
    });
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(keyFor(id), jsonEncode(entries.take(maximumEntries).toList()));
    revision.value++;
  }

  static Future<void> remove(String tvId, String url) async {
    final entries = await read(tvId);
    entries.removeWhere((entry) => entry['url'] == url);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(keyFor(tvId), jsonEncode(entries));
    revision.value++;
  }

  static Future<void> clear(String tvId) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(keyFor(tvId));
    revision.value++;
  }

  /// Allow known YouTube video, playlist and channel links only.
  /// Returns a normalized link and an inferred type.
  static ({String url, String type})? parseLink(String input) {
    var value = input.trim();
    if (value.isEmpty) return null;
    if (!value.contains('://')) value = 'https://$value';
    final uri = Uri.tryParse(value);
    if (uri == null || uri.scheme != 'https' ||
        uri.userInfo.isNotEmpty || uri.hasPort) return null;
    final host = uri.host.toLowerCase();
    final isShort = host == 'youtu.be' || host == 'www.youtu.be';
    final isYoutube = {'youtube.com', 'www.youtube.com',
      'm.youtube.com', 'music.youtube.com'}.contains(host);
    if (!isShort && !isYoutube) return null;
    final segments = uri.pathSegments;
    String videoId = '';
    if (isShort && segments.isNotEmpty) videoId = segments.first;
    if (!isShort && uri.path == '/watch') {
      videoId = uri.queryParameters['v'] ?? '';
    }
    if (!isShort && segments.length == 2 &&
        {'shorts', 'live', 'embed'}.contains(segments.first)) {
      videoId = segments[1];
    }
    if (RegExp(r'^[A-Za-z0-9_-]{11}$').hasMatch(videoId)) {
      return (url: 'https://www.youtube.com/watch?v=$videoId', type: 'Video');
    }
    final listId = uri.queryParameters['list'] ?? '';
    if (isYoutube && (uri.path == '/playlist' || uri.path == '/watch') &&
        RegExp(r'^[A-Za-z0-9_-]{10,}$').hasMatch(listId)) {
      return (url: 'https://www.youtube.com/playlist?list=$listId',
        type: 'Playlist');
    }
    if (isYoutube && segments.isNotEmpty &&
        (segments.first.startsWith('@') ||
         {'channel', 'c', 'user'}.contains(segments.first))) {
      return (url: uri.replace(host: 'www.youtube.com',
        query: '', fragment: '').toString(), type: 'Channel');
    }
    return null;
  }
}

class YoutubeHistoryPanel extends StatefulWidget {
  const YoutubeHistoryPanel({super.key});

  @override
  State<YoutubeHistoryPanel> createState() => _YoutubeHistoryPanelState();
}

class _YoutubeHistoryPanelState extends State<YoutubeHistoryPanel> {
  final search = TextEditingController();
  List<Map<String, String>> entries = [];
  String? loadedTv;
  bool loading = true;
  bool sending = false;

  String get tvId => TvLink.instance.selectedId ?? '_unselected';

  @override
  void initState() {
    super.initState();
    YoutubeHistoryStore.revision.addListener(refresh);
    search.addListener(refresh);
    reload();
  }

  @override
  void dispose() {
    YoutubeHistoryStore.revision.removeListener(refresh);
    search.removeListener(refresh);
    search.dispose();
    super.dispose();
  }

  void refresh() {
    if (mounted) reload();
  }

  Future<void> reload() async {
    final id = tvId;
    loadedTv = id;
    final saved = await YoutubeHistoryStore.read(id);
    if (mounted && id == tvId) {
      setState(() {
        entries = saved;
        loading = false;
      });
    }
  }

  void message(String value) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(value), duration: const Duration(seconds: 3)));
  }

  Future<bool> ensureConnected() async {
    if (TvLink.instance.connected) return true;
    return TvLink.instance.reconnectPreferred();
  }

  Future<void> replay(Map<String, String> entry) async {
    if (sending) return;
    setState(() => sending = true);
    try {
      if (!await ensureConnected()) {
        throw StateError('TV is offline. Reconnect it first.');
      }
      final link = entry['url']!;
      TvLink.instance.remote.launchApp(link);
      await YoutubeHistoryStore.record(
        title: entry['title'] ?? 'YouTube link',
        url: link,
        type: entry['type'] ?? 'Video',
        source: 'Reopened from History',
      );
      message('YouTube link requested on TV. Playback is not confirmed.');
    } catch (error) {
      message('Unable to reopen link: $error');
    } finally {
      if (mounted) setState(() => sending = false);
    }
  }

  Future<void> addLink() async {
    if (TvLink.instance.selectedId == null) {
      message('Select a saved TV before adding history.');
      return;
    }
    final title = TextEditingController();
    final link = TextEditingController();
    String? problem;
    final result = await showDialog<({String title, String url, String type})>(
      context: context,
      builder: (dialog) => StatefulBuilder(
        builder: (dialog, update) => AlertDialog(
          title: const Text('Add a YouTube link'),
          content: SingleChildScrollView(child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(controller: title, maxLength: 80,
                decoration: const InputDecoration(
                  labelText: 'Title', hintText: 'Video or channel name')),
              const SizedBox(height: 10),
              TextField(controller: link,
                keyboardType: TextInputType.url, autocorrect: false,
                decoration: const InputDecoration(labelText: 'YouTube URL',
                  hintText: 'youtu.be/VIDEO_ID')),
              if (problem != null) Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(problem!, style: const TextStyle(color: Colors.red))),
              const SizedBox(height: 10),
              const Text('Manually added links are labelled as saved links, '
                'not confirmed viewing activity.',
                style: TextStyle(fontSize: 12)),
            ],
          )),
          actions: [
            TextButton(onPressed: () => Navigator.pop(dialog),
              child: const Text('Cancel')),
            FilledButton(onPressed: () {
              final parsed = YoutubeHistoryStore.parseLink(link.text);
              if (parsed == null) {
                update(() => problem = 'Enter a valid YouTube video, '
                    'playlist or channel link.');
                return;
              }
              Navigator.pop(dialog, (
                title: title.text.trim().isEmpty
                  ? 'YouTube ${parsed.type}' : title.text.trim(),
                url: parsed.url, type: parsed.type,
              ));
            }, child: const Text('Save')),
          ],
        ),
      ),
    );
    title.dispose();
    link.dispose();
    if (result == null) return;
    await YoutubeHistoryStore.record(
      title: result.title, url: result.url, type: result.type,
      source: 'Added manually',
    );
  }

  Future<void> removeItem(Map<String, String> entry) async {
    await YoutubeHistoryStore.remove(tvId, entry['url'] ?? '');
  }

  Future<void> clearHistory() async {
    if (entries.isEmpty) return;
    final ok = await showDialog<bool>(context: context,
      builder: (dialog) => AlertDialog(
        title: const Text('Clear YouTube remote history?'),
        content: Text('Remove ${entries.length} locally saved entries for '
          '${TvLink.instance.preferredName}? This does not change your '
          'Google or TV YouTube watch history.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialog, false),
            child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(dialog, true),
            child: const Text('Clear')),
        ],
      ),
    );
    if (ok == true) await YoutubeHistoryStore.clear(tvId);
  }

  Future<void> openOfficialHistory() async {
    try {
      if (!await ensureConnected()) {
        throw StateError('Connect your saved TV first.');
      }
      TvLink.instance.remote.launchApp(
        'https://www.youtube.com/feed/history');
      message('Requested YouTube watch history on TV. '
        'If unsupported, open History inside the TV YouTube app.');
    } catch (error) {
      message('Unable to request YouTube history: $error');
    }
  }

  IconData iconFor(String? type) {
    switch (type) {
      case 'Video': return Icons.ondemand_video_rounded;
      case 'Playlist': return Icons.playlist_play_rounded;
      case 'Channel': return Icons.video_library_rounded;
      default: return Icons.smart_display_rounded;
    }
  }

  String when(String? raw) {
    final date = DateTime.tryParse(raw ?? '')?.toLocal();
    if (date == null) return 'Date unavailable';
    final day = MaterialLocalizations.of(context).formatMediumDate(date);
    final hour = TimeOfDay.fromDateTime(date).format(context);
    return '$day · $hour';
  }

  @override
  Widget build(BuildContext context) {
    if (loadedTv != tvId) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) reload();
      });
    }
    final query = search.text.trim().toLowerCase();
    final visible = entries.where((entry) => query.isEmpty ||
      (entry['title'] ?? '').toLowerCase().contains(query) ||
      (entry['type'] ?? '').toLowerCase().contains(query) ||
      (entry['url'] ?? '').toLowerCase().contains(query)).toList();
    return ListView(padding: const EdgeInsets.all(16), children: [
      Row(children: [
        const Icon(Icons.history_rounded, color: Color(0xff2563eb)),
        const SizedBox(width: 8),
        const Expanded(child: Text('YouTube History',
          style: TextStyle(fontSize: 21, fontWeight: FontWeight.bold))),
        IconButton(onPressed: addLink, tooltip: 'Add a YouTube link',
          icon: const Icon(Icons.add_circle_outline)),
        IconButton(onPressed: entries.isEmpty ? null : clearHistory,
          tooltip: 'Clear history for this TV',
          icon: const Icon(Icons.delete_sweep_outlined)),
      ]),
      const SizedBox(height: 4),
      Text(TvLink.instance.selectedId == null
        ? 'Select a TV to keep its activity history.'
        : 'History for ${TvLink.instance.preferredName}',
        style: const TextStyle(color: Colors.black54)),
      const SizedBox(height: 12),
      TextField(controller: search,
        decoration: const InputDecoration(
          prefixIcon: Icon(Icons.search),
          hintText: 'Search video, playlist or channel history')),
      const SizedBox(height: 12),
      OutlinedButton.icon(onPressed: openOfficialHistory,
        icon: const Icon(Icons.open_in_new),
        label: const Text('Open actual YouTube watch history on TV')),
      const SizedBox(height: 8),
      const Text('This phone saves YouTube links and app-launch requests '
        'sent through Champak\'s TV Remote. It cannot read what was actually '
        'watched on the TV or your Google account. Launch requests do not '
        'guarantee playback.',
        style: TextStyle(fontSize: 12, color: Colors.black54)),
      const SizedBox(height: 16),
      if (loading) const LinearProgressIndicator(),
      if (!loading && entries.isEmpty)
        const Card(child: Padding(padding: EdgeInsets.all(20),
          child: Column(children: [
            Icon(Icons.history_toggle_off, size: 46,
              color: Color(0xff2563eb)),
            SizedBox(height: 8),
            Text('No YouTube remote history yet.'),
            SizedBox(height: 6),
            Text('Launch a YouTube shortcut or add a link to start.',
              textAlign: TextAlign.center),
          ]))),
      if (!loading && entries.isNotEmpty && visible.isEmpty)
        const Padding(padding: EdgeInsets.all(12),
          child: Text('No entries match your search.')),
      ...visible.map((entry) => Card(
        child: ListTile(
          leading: Icon(iconFor(entry['type']), color: Colors.red),
          title: Text(entry['title'] ?? 'YouTube link',
            maxLines: 2, overflow: TextOverflow.ellipsis),
          subtitle: Text(
            '${entry['type'] ?? 'Link'} · ${when(entry['date'])}\n'
            '${entry['source'] ?? 'Remote'}',
            maxLines: 3, overflow: TextOverflow.ellipsis),
          onTap: sending ? null : () => replay(entry),
          trailing: PopupMenuButton<String>(
            tooltip: 'History options',
            onSelected: (action) {
              if (action == 'open') replay(entry);
              if (action == 'remove') removeItem(entry);
            },
            itemBuilder: (context) => const [
              PopupMenuItem(value: 'open', child: Text('Open on TV again')),
              PopupMenuItem(value: 'remove', child: Text('Remove from history')),
            ],
          ),
        ),
      )),
      const SizedBox(height: 14),
      const Text('History is saved locally, separately for each paired TV. '
        'The newest 150 launch requests are retained.',
        style: TextStyle(fontSize: 11, color: Colors.black54)),
    ]);
  }
}
