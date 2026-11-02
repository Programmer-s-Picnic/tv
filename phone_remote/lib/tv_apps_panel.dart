import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'tv_connection.dart';

/// Per-TV configurable shortcuts. Launching a link is best-effort: Android TV
/// Remote v2 does not acknowledge which application actually opened.
class TvAppsPanel extends StatefulWidget {
  const TvAppsPanel({super.key});
  @override
  State<TvAppsPanel> createState() => _TvAppsPanelState();
}

class _TvAppsPanelState extends State<TvAppsPanel> {
  static const blue = Color(0xff2563eb);
  static const defaults = <Map<String, String>>[
    {'name':'YouTube', 'link':'https://www.youtube.com/tv', 'icon':'youtube'},
    {'name':'Netflix', 'link':'https://www.netflix.com/', 'icon':'movie'},
    {'name':'Prime Video', 'link':'https://www.primevideo.com/', 'icon':'movie'},
    {'name':'JioHotstar', 'link':'https://www.hotstar.com/in', 'icon':'tv'},
    {'name':"Champak's TV Browser", 'link':'https://learnwithchampak.live/', 'icon':'web'},
  ];
  List<Map<String, String>> apps = [];
  String? loadedFor;
  bool loading = true;
  bool sending = false;

  String get tvId => TvLink.instance.selectedId ?? '_unselected';
  String get storageKey => 'tv_app_shortcuts_v1_${Uri.encodeComponent(tvId)}';

  @override
  void initState() {
    super.initState();
    refresh();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (loadedFor != tvId) refresh();
  }

  Future<void> refresh() async {
    final id = tvId;
    loadedFor = id;
    final prefs = await SharedPreferences.getInstance();
    final stored = prefs.getString('tv_app_shortcuts_v1_${Uri.encodeComponent(id)}');
    List<Map<String,String>> parsed;
    if (stored == null) {
      parsed = defaults.map((e) => Map<String,String>.from(e)).toList();
    } else {
      try {
        final items = jsonDecode(stored) as List<dynamic>;
        parsed = items.map((e) => Map<String,String>.from(
          (e as Map).map((k,v) => MapEntry(k.toString(),v.toString()))
        )).where((e)=>e['name']?.isNotEmpty == true && e['link']?.isNotEmpty == true).toList();
      } catch (_) {
        parsed = defaults.map((e) => Map<String,String>.from(e)).toList();
      }
    }
    if(mounted && tvId == id) setState(() {apps = parsed; loading = false;});
  }

  Future<void> save() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(storageKey, jsonEncode(apps));
  }

  IconData appIcon(Map<String,String> item) {
    switch(item['icon']) {
      case 'youtube': return Icons.smart_display_rounded;
      case 'movie': return Icons.movie_outlined;
      case 'tv': return Icons.tv_rounded;
      case 'web': return Icons.language_rounded;
      default: return Icons.apps_rounded;
    }
  }

  bool validLink(String link) {
    final uri = Uri.tryParse(link);
    return uri != null && (uri.scheme == 'https' || uri.scheme == 'http')
        && uri.host.isNotEmpty && !uri.userInfo.contains('@');
  }

  Future<void> editApp({int? index}) async {
    final current = index == null ? <String,String>{} : apps[index];
    final name = TextEditingController(text:current['name'] ?? '');
    final link = TextEditingController(text:current['link'] ?? '');
    String error = '';
    final result = await showDialog<Map<String,String>>(context:context,
      builder:(dialogContext)=>StatefulBuilder(builder:(dialogContext, update) =>
        AlertDialog(
          title:Text(index == null?'Add app shortcut':'Edit shortcut'),
          content:SingleChildScrollView(child:Column(mainAxisSize:MainAxisSize.min,children:[
            TextField(controller:name,maxLength:36,textCapitalization:TextCapitalization.words,
              decoration:const InputDecoration(labelText:'App name',hintText:'My TV app')),
            const SizedBox(height:8),
            TextField(controller:link,keyboardType:TextInputType.url,
              decoration:const InputDecoration(labelText:'App link (https://...)',
                hintText:'https://example.com')),
            if(error.isNotEmpty)Padding(padding:const EdgeInsets.only(top:8),
              child:Text(error,style:const TextStyle(color:Colors.red,fontSize:12))),
            const SizedBox(height:8),
            const Text('Links may open the TV browser instead of the native application. Not all TV apps support remote launch links.',
              style:TextStyle(fontSize:12,color:Colors.black54))
          ])),
          actions:[
            TextButton(onPressed:()=>Navigator.pop(dialogContext),child:const Text('Cancel')),
            FilledButton(onPressed:(){
              final title=name.text.trim(), url=link.text.trim();
              if(title.isEmpty || !validLink(url)) {
                update(()=>error='Enter a name and a valid http(s) link.');
                return;
              }
              Navigator.pop(dialogContext,{'name':title,'link':url,'icon':current['icon']??'app'});
            },child:const Text('Save'))
          ],
        )));
    name.dispose(); link.dispose();
    if(result == null || !mounted) return;
    setState(() {
      if(index == null) apps.add(result);
      else apps[index] = result;
    });
    await save();
  }

  Future<void> removeApp(int index) async {
    final name=apps[index]['name'] ?? 'shortcut';
    final accepted=await showDialog<bool>(context:context,builder:(ctx)=>AlertDialog(
      title:Text('Remove $name?'),
      content:const Text('This removes the shortcut only. It does not uninstall the TV application.'),
      actions:[
        TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('Cancel')),
        FilledButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('Remove'))
      ]));
    if(accepted != true || !mounted)return;
    setState(()=>apps.removeAt(index));
    await save();
  }

  Future<void> launch(Map<String,String> shortcut) async {
    if(sending)return;
    setState(()=>sending=true);
    try {
      final ok = TvLink.instance.connected || await TvLink.instance.reconnectPreferred();
      if(!ok)throw StateError('TV is offline. Check the connection first.');
      final url=shortcut['link']!;
      TvLink.instance.remote.launchApp(url);
      if(mounted) {
        ScaffoldMessenger.of(context).clearSnackBars();
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          behavior:SnackBarBehavior.floating,
          duration:const Duration(seconds:2),
          content:Text('Sent ${shortcut['name']} link to TV. Check its screen.')));
      }
    }catch(e) {
      if(mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        behavior:SnackBarBehavior.floating,
        duration:const Duration(seconds:4),
        content:Text('Could not launch: $e')));
    }finally{if(mounted)setState(()=>sending=false);}
  }

  @override
  Widget build(BuildContext context) {
    final id = tvId;
    if(loadedFor != id) {
      WidgetsBinding.instance.addPostFrameCallback((_) {if(mounted)refresh();});
    }
    final selected = TvLink.instance.selectedId != null;
    return Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[
      Row(children:[
        const Icon(Icons.apps_rounded,color:blue,size:28),
        const SizedBox(width:8),
        const Expanded(child:Text('My TV Apps',style:TextStyle(fontSize:22,fontWeight:FontWeight.bold))),
        IconButton(tooltip:'Add shortcut',onPressed:()=>editApp(),icon:const Icon(Icons.add_circle,color:blue))
      ]),
      Text(selected?'Shortcuts for ${TvLink.instance.preferredName}'
        :'Connect a TV to create its own set of shortcuts.',
        style:const TextStyle(color:Colors.black54)),
      const SizedBox(height:12),
      if(loading)const LinearProgressIndicator(),
      if(!loading && apps.isEmpty)
        const Padding(padding:EdgeInsets.all(20),child:Text('No shortcuts. Use + to add your first TV app.')),
      ...apps.asMap().entries.map((entry) {
        final i=entry.key, app=entry.value;
        return Card(
          color:Colors.white,margin:const EdgeInsets.only(bottom:8),
          child:Padding(padding:const EdgeInsets.symmetric(vertical:5),child:Row(children:[
            IconButton(
              tooltip:'Launch ${app['name']}',
              onPressed:sending?null:()=>launch(app),
              icon:Icon(appIcon(app),color:app['icon']=='youtube'?Colors.red:blue,size:29)),
            Expanded(child:InkWell(onTap:sending?null:()=>launch(app),
              child:Padding(padding:const EdgeInsets.symmetric(vertical:8),child:Column(
                crossAxisAlignment:CrossAxisAlignment.start,children:[
                Text(app['name']??'App',style:const TextStyle(fontWeight:FontWeight.w600)),
                Text(app['link']??'',overflow:TextOverflow.ellipsis,
                  style:const TextStyle(fontSize:11,color:Colors.black54))
              ])))),
            PopupMenuButton<String>(
              tooltip:'Shortcut options',onSelected:(action) async {
                if(action=='edit')await editApp(index:i);
                if(action=='delete')await removeApp(i);
                if(action=='up' && i>0) {
                  setState(() {final value=apps.removeAt(i);apps.insert(i-1,value);});await save();
                }
                if(action=='down' && i<apps.length-1) {
                  setState(() {final value=apps.removeAt(i);apps.insert(i+1,value);});await save();
                }
              },
              itemBuilder:(ctx)=>[
                const PopupMenuItem(value:'edit',child:Text('Edit')),
                if(i>0)const PopupMenuItem(value:'up',child:Text('Move up')),
                if(i<apps.length-1)const PopupMenuItem(value:'down',child:Text('Move down')),
                const PopupMenuItem(value:'delete',child:Text('Remove'))
              ])
          ])));
      }),
      const SizedBox(height:8),
      OutlinedButton.icon(onPressed:()=>editApp(),icon:const Icon(Icons.add),
        label:const Text('Add custom app shortcut')),
      const SizedBox(height:8),
      const Text('Shortcuts request a URL launch using Android TV Remote v2. Some installed apps may not accept it; a TV-side launcher is needed for reliable package-based launches.',
        style:TextStyle(fontSize:11,color:Colors.black54))
    ]);
  }
}
