#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
T=$(mktemp -d);trap 'rm -rf "$T"' EXIT
mkdir -p "$T/src"
python3 - "$T/src" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1]);src={
'android/content/Context.java':'''package android.content;public class Context{
 public java.io.File getFilesDir(){return new java.io.File(".");}
 public Resources getResources(){return new Resources();}
 public SharedPreferences getSharedPreferences(String a,int b){return null;}
 public void startForegroundService(Intent intent){}
 public void startService(Intent intent){}
}''',
'android/content/Resources.java':'''package android.content;public class Resources{
 public static class Metrics{public float density=1f;}
 public Metrics getDisplayMetrics(){return new Metrics();}
}''',
'android/content/SharedPreferences.java':'''package android.content;public interface SharedPreferences{
 String getString(String key,String def);}
''',
'android/content/Intent.java':'''package android.content;public class Intent{
 public Intent(Context ctx,Class<?> cls){}
 public String getStringExtra(String key){return null;}
 public Intent setAction(String action){return this;}
 public Intent putExtra(String key,String v){return this;}
 public Intent putExtra(String key,int v){return this;}
 public Intent putExtra(String key,boolean v){return this;}
}''',
'android/net/Uri.java':'''package android.net;public class Uri{public static Uri parse(String uri){return null;}}''',
'android/os/Build.java':'''package android.os;public class Build{public static class VERSION{public static int SDK_INT=35;}}''',
'android/os/Bundle.java':'''package android.os;public class Bundle{}''',
'android/os/Looper.java':'''package android.os;public class Looper{public static Looper getMainLooper(){return null;}}''',
'android/os/Handler.java':'''package android.os;public class Handler{
 public Handler(Looper l){}
 public boolean post(Runnable r){return true;}
 public boolean postDelayed(Runnable r,long d){return true;}
 public void removeCallbacks(Runnable r){}
 public void removeCallbacksAndMessages(Object token){}
}''',
'android/os/SystemClock.java':'''package android.os;public class SystemClock{public static long elapsedRealtime(){return 0;}}''',
'android/graphics/Typeface.java':'''package android.graphics;public class Typeface{
 public static final Typeface DEFAULT=null;public static final int BOLD=1;}
''',
'android/graphics/drawable/GradientDrawable.java':'''package android.graphics.drawable;public class GradientDrawable{
 public void setColor(int c){}public void setCornerRadius(float r){}
}''',
'android/view/Gravity.java':'''package android.view;public class Gravity{
 public static final int CENTER=1,CENTER_VERTICAL=2,END=4;}
''',
'android/view/View.java':'''package android.view;public class View{
 public static final int VISIBLE=0,GONE=8;
 public View(android.content.Context c){}
 public void setOnClickListener(OnClickListener c){}
 public void setPadding(int a,int b,int c,int d){}
 public void setBackground(android.graphics.drawable.GradientDrawable d){}
 public void setVisibility(int v){}
 public void setEnabled(boolean b){}
 public void setContentDescription(CharSequence text){}
 public interface OnClickListener{void onClick(View v);}
}''',
'android/widget/LinearLayout.java':'''package android.widget;public class LinearLayout extends android.view.View{
 public static final int VERTICAL=1;
 public LinearLayout(android.content.Context c){super(c);}
 public void setOrientation(int o){}
 public void setGravity(int g){}
 public void setBackgroundColor(int c){}
 public void addView(android.view.View v){}
 public void addView(android.view.View v,LayoutParams p){}
 public void removeAllViews(){}
 public static class LayoutParams{public LayoutParams(int w,int h){}
 public LayoutParams(int w,int h,int weight){}
 public int topMargin,bottomMargin,leftMargin,rightMargin;}
}''',
'android/widget/TextView.java':'''package android.widget;public class TextView extends android.view.View{
 public TextView(android.content.Context c){super(c);}
 public void setText(String text){}
 public void setTextSize(int size){}
 public void setTextColor(int color){}
 public void setTypeface(android.graphics.Typeface type,int style){}
 public void setGravity(int gravity){}
 public void setSingleLine(boolean v){}
}''',
'android/widget/ImageView.java':'''package android.widget;public class ImageView extends android.view.View{
 public ImageView(android.content.Context c){super(c);}
 public void setImageResource(int r){}
 public void setColorFilter(int color){}
}''',
'android/widget/ProgressBar.java':'''package android.widget;public class ProgressBar extends android.view.View{
 public ProgressBar(android.content.Context c,Object a,int style){super(c);}
 public void setMax(int value){}
 public void setProgress(int value){}
}''',
'android/widget/ScrollView.java':'''package android.widget;public class ScrollView extends android.view.View{
 public ScrollView(android.content.Context c){super(c);}
 public void addView(android.view.View v){}
}''',
'android/widget/Toast.java':'''package android.widget;public class Toast{
 public static final int LENGTH_SHORT=0,LENGTH_LONG=1;
 public static Toast makeText(android.content.Context c,String s,int d){return null;}
 public void show(){}
}''',
'android/app/Activity.java':'''package android.app;public class Activity extends android.content.Context{
 protected void onCreate(android.os.Bundle b){}
 protected void onResume(){}
 protected void onPause(){}
 protected void onDestroy(){}
 protected void onActivityResult(int req,int result,android.content.Intent data){}
 public void onBackPressed(){}
 public void setContentView(android.view.View v){}
 public android.content.Intent getIntent(){return null;}
 public void finish(){}
}''',
'ru/reader/rsvp/NavigationActivity.java':'''package ru.reader.rsvp;public class NavigationActivity extends android.app.Activity{}''',
'ru/reader/rsvp/R.java':'''package ru.reader.rsvp;public class R{
 public static class drawable{public static final int ic_edge_merge_chapter=1,ic_retry_fragment=2,ic_edge_delete_chapter=3;}}
''',
'ru/reader/rsvp/SafeSystemBars.java':'''package ru.reader.rsvp;public class SafeSystemBars{
 public static void matchBackground(android.content.Context c,int color){}
}''',
'ru/reader/rsvp/UniversalDrawer.java':'''package ru.reader.rsvp;public class UniversalDrawer{
 public static void show(android.app.Activity a){}
}''',
'ru/reader/rsvp/BackNavigation.java':'''package ru.reader.rsvp;public class BackNavigation{
 public static void back(android.app.Activity a){}
}''',
'ru/reader/rsvp/SwipeDialogs.java':'''package ru.reader.rsvp;public class SwipeDialogs{
 public static class Builder{
 public Builder(android.app.Activity a){}
 public Builder setTitle(String t){return this;}
 public Builder setMessage(String t){return this;}
 public Builder setNegativeButton(String name,Click c){return this;}
 public Builder setPositiveButton(String name,Click c){return this;}
 public void show(){}
 }
 public interface Click{void onClick(Object dialog,int which);}
}''',
'ru/reader/rsvp/EdgeFullBookService.java':'''package ru.reader.rsvp;public class EdgeFullBookService{
 public static final String ADD="",PAUSE="",RESUME="",STOP_CHAPTER="";
 public static final String EXTRA_URI="",EXTRA_TITLE="",EXTRA_VOICE="",EXTRA_RATE="",EXTRA_CHARS="",EXTRA_CHAPTER="",EXTRA_FORCE="";
 public static boolean isServiceAlive(){return true;}
}''',
'ru/reader/rsvp/EdgeSpeechSegments.java':'''package ru.reader.rsvp;public class EdgeSpeechSegments{public static final int MAX=900;}''',
'ru/reader/rsvp/EdgeBookStorage.java':'''package ru.reader.rsvp;public class EdgeBookStorage{
 public static java.io.File bookFolder(android.content.Context c,String uri,String title){return new java.io.File(".");}
}''',
'ru/reader/rsvp/DownloadsRepository.java':'''package ru.reader.rsvp;public class DownloadsRepository{
 public static void showEdge(android.content.Context c,String u){}
 public static void storeEdgeMeta(java.io.File f,String uri,String title,int words){}
}''',
'ru/reader/rsvp/BookChapterIndex.java':'''package ru.reader.rsvp;public class BookChapterIndex{
 public static class Chapter{public int startChar,endChar;public String title="";}
 public static BookChapterIndex empty(){return new BookChapterIndex();}
 public int size(){return 0;}
 public Chapter get(int i){return null;}
}''',
'ru/reader/rsvp/BookLoader.java':'''package ru.reader.rsvp;public class BookLoader{
 public static class Book{public String text="";public BookChapterIndex chapters=new BookChapterIndex();}
 public static Book load(android.content.Context c,android.net.Uri uri){return null;}
}''',
'ru/reader/rsvp/SentenceChunks.java':'''package ru.reader.rsvp;public class SentenceChunks{
 public static java.util.ArrayList<Integer> wordPositions(String text){return new java.util.ArrayList<>();}
 public static int lowerBound(java.util.ArrayList<Integer> a,int key){return 0;}
}''',
'ru/reader/rsvp/EdgeSegmentSettings.java':'''package ru.reader.rsvp;public class EdgeSegmentSettings{
 public static int get(android.content.SharedPreferences p){return 900;}
}''',
'ru/reader/rsvp/EdgeFolderPicker.java':'''package ru.reader.rsvp;public class EdgeFolderPicker{
 public static boolean available(android.content.Context c){return true;}
 public static boolean accept(android.app.Activity a,int req,int result,android.content.Intent intent){return false;}
 public static void choose(android.app.Activity a){}
}''',
'ru/reader/rsvp/EdgeOfflineIndex.java':'''package ru.reader.rsvp;public class EdgeOfflineIndex{
 public static class Segment{}
 public static class Stats{public int covered,files;public long bytes;
 public boolean complete(){return false;}
 public int percent(){return 0;}}
 public static Stats stats(java.util.ArrayList<Segment> parts,int start,int end){return new Stats();}
}''',
'ru/reader/rsvp/EdgeAudioManager.java':'''package ru.reader.rsvp;public class EdgeAudioManager{
 public static java.util.ArrayList<EdgeOfflineIndex.Segment> segments(java.io.File f){return new java.util.ArrayList<>();}
}''',
'ru/reader/rsvp/EdgeChapterArchive.java':'''package ru.reader.rsvp;public class EdgeChapterArchive{
 public static class ChapterSnapshot{public int state(int i){return 0;}}
 public static ChapterSnapshot snapshot(android.content.Context c,String uri,String title){return null;}
 public static boolean deleteChapter(android.content.Context c,String uri,String title,int chapter){return true;}
 public static org.json.JSONArray queueFromSAF(android.content.Context c,String uri,String title,int chapter){return null;}
 public static boolean mergeChapter(android.content.Context c,String uri,String title,int chapter,
 java.io.File cache,org.json.JSONArray queue,BookChapterIndex chapters,java.util.ArrayList<Integer> starts){return true;}
}''',
'org/json/JSONObject.java':'''package org.json;public class JSONObject{
 public String optString(String key,String def){return def;}
 public String optString(String key){return "";}
 public int optInt(String key,int def){return def;}
 public int optInt(String key){return 0;}
 public JSONArray optJSONArray(String key){return null;}
 public JSONObject put(String s,Object v){return this;}
 public JSONObject(){}
 public JSONObject(String s){}
 public long optLong(String key,long def){return 0;}
 public boolean optBoolean(String key,boolean def){return false;}
 public String toString(){return "{}";}
}''',
'org/json/JSONArray.java':'''package org.json;public class JSONArray{
 public JSONArray(){}
 public JSONArray(String s){}
 public int length(){return 0;}
 public JSONObject optJSONObject(int i){return null;}
 public JSONArray put(Object v){return this;}
}'''
}
src["android/R.java"]="package android;public class R{public static class attr{public static final int progressBarStyleHorizontal=1;}}"
for path,body in src.items():
    f=root/path;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(body)
PY
mkdir -p "$T/bin"
javac -encoding UTF-8 -source 17 -target 17 -d "$T/bin" $(find "$T/src" -name '*.java') \
 app/src/main/java/ru/reader/rsvp/EdgeFullBookStore.java \
 app/src/main/java/ru/reader/rsvp/EdgeChapterDownloadsActivity.java
printf '%s\n' 'PASS: actual EdgeChapterDownloadsActivity + EdgeFullBookStore typecheck against Android API signatures'
