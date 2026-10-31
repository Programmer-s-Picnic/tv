import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
const blue=Color(0xff2563eb),ink=Color(0xff192b4e);
void main()=>runApp(const RemoteApp());
class RemoteApp extends StatelessWidget{
 const RemoteApp({super.key});
 @override Widget build(BuildContext context)=>MaterialApp(
  debugShowCheckedModeBanner:false,title:"Champak's TV Remote",
  theme:ThemeData(useMaterial3:true,scaffoldBackgroundColor:const Color(0xfff3f7ff),
   colorScheme:ColorScheme.fromSeed(seedColor:blue),
   inputDecorationTheme:InputDecorationTheme(filled:true,fillColor:const Color(0xffedf3ff),
    border:OutlineInputBorder(borderSide:BorderSide.none,borderRadius:BorderRadius.circular(15)))),
  home:const RemoteHome());
}
class RemoteHome extends StatefulWidget{
 const RemoteHome({super.key});
 @override State<RemoteHome> createState()=>_RemoteHomeState();
}
class _RemoteHomeState extends State<RemoteHome>{
 int page=0;List<Map<String,dynamic>> programs=[];
 @override void initState(){super.initState();load();}
 Future<void> load()async{final p=await SharedPreferences.getInstance();
 try{setState(()=>programs=List<Map<String,dynamic>>.from(jsonDecode(p.getString('tv_programs')??'[]')));}catch(_){}}
 Future<void> save()async{final p=await SharedPreferences.getInstance();
 await p.setString('tv_programs',jsonEncode(programs));}
 void unavailable(String action)=>ScaffoldMessenger.of(context).showSnackBar(SnackBar(
  content:Text(action+' will work after secure TV pairing is implemented.')));
 Widget panel(Widget child)=>Container(padding:const EdgeInsets.all(18),
  decoration:BoxDecoration(color:Colors.white,borderRadius:BorderRadius.circular(24),
   boxShadow:const [BoxShadow(color:Color(0x102469cf),blurRadius:24,offset:Offset(0,7))]),child:child);
 Widget keyButton(IconData icon,String title,{VoidCallback? click})=>InkWell(
  onTap:click??()=>unavailable(title),borderRadius:BorderRadius.circular(17),
  child:Container(height:70,decoration:BoxDecoration(color:const Color(0xffecf3ff),
   borderRadius:BorderRadius.circular(17)),child:Column(mainAxisAlignment:MainAxisAlignment.center,children:[
    Icon(icon,color:blue),const SizedBox(height:4),
    Text(title,style:const TextStyle(fontSize:11,fontWeight:FontWeight.w600,color:ink))])));
 Widget remote()=>Column(children:[
  panel(const Row(children:[Icon(Icons.tv_rounded,color:blue,size:34),SizedBox(width:12),
   Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
    Text('Android TV',style:TextStyle(fontWeight:FontWeight.bold,fontSize:18)),
    Text('Not connected • Pairing is next',style:TextStyle(color:Colors.black54))]))])),
  const SizedBox(height:15),panel(Column(children:[
   Row(mainAxisAlignment:MainAxisAlignment.spaceBetween,children:[
    IconButton(onPressed:()=>unavailable('Power'),icon:const Icon(Icons.power_settings_new,color:blue)),
    const Text('REMOTE',style:TextStyle(letterSpacing:2,fontWeight:FontWeight.bold,color:ink)),
    IconButton(onPressed:()=>unavailable('Settings'),icon:const Icon(Icons.settings_outlined,color:blue))]),
   SizedBox(height:245,width:245,child:Stack(alignment:Alignment.center,children:[
    Container(decoration:const BoxDecoration(color:Color(0xffeaf1ff),shape:BoxShape.circle)),
    Positioned(top:0,child:IconButton(iconSize:40,onPressed:()=>unavailable('Up'),icon:const Icon(Icons.keyboard_arrow_up))),
    Positioned(bottom:0,child:IconButton(iconSize:40,onPressed:()=>unavailable('Down'),icon:const Icon(Icons.keyboard_arrow_down))),
    Positioned(left:0,child:IconButton(iconSize:40,onPressed:()=>unavailable('Left'),icon:const Icon(Icons.keyboard_arrow_left))),
    Positioned(right:0,child:IconButton(iconSize:40,onPressed:()=>unavailable('Right'),icon:const Icon(Icons.keyboard_arrow_right))),
    FilledButton(onPressed:()=>unavailable('OK'),style:FilledButton.styleFrom(shape:const CircleBorder(),
     minimumSize:const Size(95,95)),child:const Text('OK',style:TextStyle(fontSize:23)))])),
   const SizedBox(height:15),
   Row(children:[Expanded(child:keyButton(Icons.arrow_back,'Back')),const SizedBox(width:7),
    Expanded(child:keyButton(Icons.home,'Home')),const SizedBox(width:7),
    Expanded(child:keyButton(Icons.apps,'Apps'))]),
   const SizedBox(height:9),
   Row(children:[Expanded(child:keyButton(Icons.volume_down,'Vol -')),const SizedBox(width:7),
    Expanded(child:keyButton(Icons.play_arrow_rounded,'Media')),const SizedBox(width:7),
    Expanded(child:keyButton(Icons.volume_up,'Vol +'))])]))]);
 Widget touchpad()=>panel(Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
  const Text('Precision touchpad',style:TextStyle(fontSize:21,fontWeight:FontWeight.bold,color:ink)),
  const Text('Swipe to move • Tap to click',style:TextStyle(color:Colors.black54)),
  const SizedBox(height:15),GestureDetector(onTap:()=>unavailable('Click'),
   child:Container(height:340,alignment:Alignment.center,
    decoration:BoxDecoration(gradient:const LinearGradient(colors:[Color(0xffd7e7ff),Color(0xffedf5ff)]),
      borderRadius:BorderRadius.circular(23)),
    child:const Icon(Icons.touch_app_rounded,color:blue,size:55))),
  const SizedBox(height:12),Row(children:[Expanded(child:keyButton(Icons.mouse,'Left click')),
    const SizedBox(width:10),Expanded(child:keyButton(Icons.menu_open,'Right click'))])]));
 Widget keyboard()=>panel(Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[
  const Text('Phone keyboard',style:TextStyle(fontSize:22,fontWeight:FontWeight.bold,color:ink)),
  const SizedBox(height:10),const TextField(minLines:3,maxLines:5,
   decoration:InputDecoration(hintText:'Enter URL, search or message')),
  const SizedBox(height:12),FilledButton.icon(onPressed:()=>unavailable('Send text'),
   icon:const Icon(Icons.send),label:const Text('Send to TV'))]));
 Widget browser()=>panel(Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
  const Text("Champak's TV Browser",style:TextStyle(fontSize:21,fontWeight:FontWeight.bold,color:ink)),
  const SizedBox(height:13),GridView.count(shrinkWrap:true,
   physics:const NeverScrollableScrollPhysics(),crossAxisCount:3,mainAxisSpacing:8,
   crossAxisSpacing:8,childAspectRatio:1.05,children:[
    keyButton(Icons.add_box_outlined,'New tab'),keyButton(Icons.refresh,'Reload'),
    keyButton(Icons.fullscreen,'Fullscreen'),keyButton(Icons.bookmark_outline,'Bookmarks'),
    keyButton(Icons.history,'History'),keyButton(Icons.download,'Downloads'),
    keyButton(Icons.language,'Address'),keyButton(Icons.tab,'Switch tab'),
    keyButton(Icons.center_focus_strong,'Focus')])])); 
 Future<void> addProgram()async{
  final title=TextEditingController(),channel=TextEditingController();
  TimeOfDay time=const TimeOfDay(hour:20,minute:0);int before=15;
  final days=<int>{1,2,3,4,5,6,7};
  final entry=await showModalBottomSheet<Map<String,dynamic>>(context:context,isScrollControlled:true,
   builder:(ctx)=>StatefulBuilder(builder:(ctx,change)=>Padding(
    padding:EdgeInsets.fromLTRB(18,20,18,MediaQuery.of(ctx).viewInsets.bottom+25),
    child:SingleChildScrollView(child:Column(mainAxisSize:MainAxisSize.min,
     crossAxisAlignment:CrossAxisAlignment.stretch,children:[
      const Text('New program reminder',style:TextStyle(fontSize:22,fontWeight:FontWeight.bold)),
      const SizedBox(height:12),TextField(controller:title,decoration:const InputDecoration(labelText:'Program')),
      const SizedBox(height:8),TextField(controller:channel,decoration:const InputDecoration(labelText:'Channel')),
      const SizedBox(height:10),Wrap(spacing:5,children:List.generate(7,(i)=>FilterChip(
       label:Text(['Mon','Tue','Wed','Thu','Fri','Sat','Sun'][i]),
       selected:days.contains(i+1),onSelected:(v)=>change(()=>v?days.add(i+1):days.remove(i+1))))),
      TextButton(onPressed:()async{final t=await showTimePicker(context:ctx,initialTime:time);
       if(t!=null)change(()=>time=t);},child:Text('Program time: '+time.format(ctx))),
      DropdownButtonFormField<int>(initialValue:15,decoration:const InputDecoration(labelText:'Minutes before'),
       items:[0,5,10,15,30,60].map((m)=>DropdownMenuItem(value:m,child:Text(m.toString()))).toList(),
       onChanged:(m)=>before=m??15),
      const SizedBox(height:15),FilledButton(onPressed:(){
       if(title.text.trim().isEmpty||days.isEmpty)return;
       Navigator.pop(ctx,{'title':title.text.trim(),'channel':channel.text.trim(),
        'time':time.hour.toString().padLeft(2,'0')+':'+time.minute.toString().padLeft(2,'0'),
        'days':days.toList()..sort(),'before':before});
      },child:const Text('Save program')),
      const Text('Scheduling phone notifications requires a later integration.',
       style:TextStyle(fontSize:11,color:Colors.black54))])))));
  title.dispose();channel.dispose();
  if(entry!=null){setState(()=>programs.add(entry));await save();}
 }
 Widget planner()=>Column(children:[
  panel(Row(children:[const Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
   Text('Program planner',style:TextStyle(fontSize:22,fontWeight:FontWeight.bold,color:ink)),
   Text('Your favourite television programs')])) ,
   IconButton.filled(onPressed:addProgram,icon:const Icon(Icons.add))])),
  const SizedBox(height:12),
  if(programs.isEmpty)panel(const Center(child:Column(children:[
   Icon(Icons.notifications_active_outlined,color:blue,size:50),SizedBox(height:12),
   Text('No programs yet'),Text('Use + to add your first program.')]))),
  ...programs.asMap().entries.map((item)=>Padding(padding:const EdgeInsets.only(bottom:8),
   child:panel(Row(children:[const Icon(Icons.event,color:blue),const SizedBox(width:12),
    Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
     Text(item.value['title'].toString(),style:const TextStyle(fontWeight:FontWeight.bold)),
     Text(item.value['channel'].toString()+' • '+item.value['time'].toString()),
     Text('Notify '+item.value['before'].toString()+' minutes early (planned)',
      style:const TextStyle(fontSize:11,color:Colors.black54))])),
    IconButton(onPressed:(){setState(()=>programs.removeAt(item.key));save();},
     icon:const Icon(Icons.delete_outline))]))))]);
 @override Widget build(BuildContext context){
  const names=['Remote','Touchpad','Keyboard','Programs','Browser'];
  const icons=[Icons.gamepad,Icons.mouse,Icons.keyboard,Icons.calendar_month,Icons.language];
  final pages=[remote(),touchpad(),keyboard(),planner(),browser()];
  return Scaffold(appBar:AppBar(title:const Text("Champak's TV Remote",
   style:TextStyle(fontWeight:FontWeight.bold,color:ink)),actions:[
    IconButton(onPressed:()=>unavailable('Pair TV'),icon:const Icon(Icons.cast_connected,color:blue))]),
   body:SafeArea(child:SingleChildScrollView(padding:const EdgeInsets.all(16),
    child:Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[
     Row(children:[Text(names[page],style:const TextStyle(fontSize:28,color:ink,fontWeight:FontWeight.bold)),
      const Spacer(),const Chip(label:Text('V0.1'))]),const SizedBox(height:14),
     pages[page],const SizedBox(height:28),
     const Center(child:Text('Developed and maintained by Champak Roy',
      style:TextStyle(fontSize:11,color:Colors.black38)))]))),
   bottomNavigationBar:NavigationBar(selectedIndex:page,onDestinationSelected:(i)=>setState(()=>page=i),
    destinations:List.generate(5,(i)=>NavigationDestination(icon:Icon(icons[i]),label:names[i]))));
 }
}
