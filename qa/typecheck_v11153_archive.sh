#!/bin/sh
# Type-check actual v153 archive/store/deletion classes with Android/RecyclerView stand-ins.
set -eu
cd "$(dirname "$0")/.."
T=$(mktemp -d);trap 'rm -rf "$T"' EXIT
python3 - "$T/src" <<'PY'
from pathlib import Path
import sys
base=Path(sys.argv[1]);src={
'android/content/Context.java':'''package android.content;public class Context{
 public static final int MODE_PRIVATE=0;
 public SharedPreferences getSharedPreferences(String k,int m){return null;}
 public android.content.res.Resources getResources(){return null;}
 public Context getApplicationContext(){return this;}
 public java.io.File getFilesDir(){return new java.io.File(".");}
}''',
'android/content/res/Resources.java':'''package android.content.res;public class Resources{
 public android.util.DisplayMetrics getDisplayMetrics(){return null;}
}''',
'android/util/DisplayMetrics.java':'''package android.util;public class DisplayMetrics{public float density=1;}''',
'android/content/SharedPreferences.java':'''package android.content;import java.util.Set;
 public interface SharedPreferences{String getString(String key,String def);Set<String> getStringSet(String key,Set<String> def);
 int getInt(String key,int def);Editor edit();interface Editor{
 Editor putString(String key,String value);Editor putStringSet(String key,Set<String> value);
 Editor putInt(String key,int value);boolean commit();void apply();}}''',
'android/app/Activity.java':'''package android.app;public class Activity extends android.content.Context{
 protected void onCreate(android.os.Bundle state){} protected void onResume(){} protected void onDestroy(){}
 public void onBackPressed(){} public void finish(){} public boolean isFinishing(){return false;}
 public boolean isDestroyed(){return false;} public void runOnUiThread(Runnable r){}
 public void setContentView(android.view.View v){} }
''',
'android/os/Bundle.java':'''package android.os;public class Bundle{}''',
'android/content/Intent.java':'''package android.content;public class Intent{}''',
'android/net/Uri.java':'''package android.net;public class Uri{public static Uri parse(String s){return null;}}''',
'android/graphics/Typeface.java':'''package android.graphics;public class Typeface{
 public static final Typeface DEFAULT=null,SERIF=null;public static final int BOLD=1;}''',
'android/graphics/drawable/GradientDrawable.java':'''package android.graphics.drawable;public class GradientDrawable{
 public void setColor(int c){}public void setCornerRadius(float r){}}''',
'android/view/Gravity.java':'''package android.view;public class Gravity{
 public static final int CENTER_VERTICAL=1,CENTER=2,END=4;}''',
'android/view/View.java':'''package android.view;public class View{
 public static final int VISIBLE=0,GONE=8; public void setVisibility(int mode){}
 public View(android.content.Context c){}public void setOnClickListener(OnClickListener l){}
 public void setPadding(int l,int t,int r,int b){} public void setBackground(android.graphics.drawable.GradientDrawable d){}
 public void setContentDescription(CharSequence text){}
 public void setLayoutParams(Object params){}
 public interface OnClickListener{void onClick(View v);}}''',
'android/view/ViewGroup.java':'''package android.view;public class ViewGroup extends View{public ViewGroup(android.content.Context c){super(c);}}''',
'android/widget/LinearLayout.java':'''package android.widget;public class LinearLayout extends android.view.ViewGroup{
 public static final int VERTICAL=1;public LinearLayout(android.content.Context c){super(c);}
 public void setOrientation(int o){} public void setGravity(int o){}
 public void setBackgroundColor(int c){} public void addView(android.view.View v){}
 public void addView(android.view.View v,LayoutParams p){} public void removeAllViews(){}
 public static class LayoutParams{public int topMargin,bottomMargin,leftMargin,rightMargin;
 public LayoutParams(int w,int h){} public LayoutParams(int w,int h,int weight){}}
 }''',
'android/widget/TextView.java':'''package android.widget;public class TextView extends android.view.View{
 public TextView(android.content.Context c){super(c);}public void setText(CharSequence s){}
 public void setTextSize(int s){}public void setTextColor(int c){}
 public void setTypeface(android.graphics.Typeface t,int style){} public void setGravity(int v){}
 }''',
'android/widget/EditText.java':'''package android.widget;public class EditText extends TextView{
 public EditText(android.content.Context c){super(c);}public void setSingleLine(boolean yes){}
 public void setHint(CharSequence s){}public void setHintTextColor(int color){}
 public void addTextChangedListener(android.text.TextWatcher watcher){}
 }''',
'android/text/Editable.java':'''package android.text;public interface Editable extends CharSequence{}''',
'android/text/TextWatcher.java':'''package android.text;public interface TextWatcher{
 void beforeTextChanged(CharSequence s,int start,int count,int after);
 void onTextChanged(CharSequence s,int start,int before,int count);void afterTextChanged(Editable e); }''',
'android/widget/ImageView.java':'''package android.widget;public class ImageView extends android.view.View{
 public ImageView(android.content.Context c){super(c);}public void setImageResource(int id){}
 public void setColorFilter(int c){} }''',
'android/widget/PopupMenu.java':'''package android.widget;public class PopupMenu{
 public PopupMenu(android.content.Context c,android.view.View v){}public Menu getMenu(){return new Menu();}
 public void show(){}public static class Menu{public Item add(String title){return new Item();}}
 public static class Item{public Item setOnMenuItemClickListener(ClickListener l){return this;}}
 public interface ClickListener{boolean onClick(Item item);} }''',
'android/widget/Toast.java':'''package android.widget;public class Toast{
 public static final int LENGTH_LONG=1,LENGTH_SHORT=0;
 public static Toast makeText(android.content.Context c,String s,int duration){return null;}
 public void show(){} }''',
'androidx/recyclerview/widget/RecyclerView.java':'''package androidx.recyclerview.widget;
 public class RecyclerView extends android.view.ViewGroup{
 public RecyclerView(android.content.Context c){super(c);} public void setLayoutManager(Object manager){}
 public void setItemAnimator(Object a){}public void setAdapter(Adapter<?> a){}
 public static class LayoutParams{public LayoutParams(int w,int h){}}
 public static class ViewHolder{public final android.view.View itemView;
 public ViewHolder(android.view.View v){itemView=v;}}
 public abstract static class Adapter<V extends ViewHolder>{
 public void setHasStableIds(boolean yes){}public long getItemId(int p){return 0;}
 public abstract V onCreateViewHolder(android.view.ViewGroup parent,int type);
 public abstract void onBindViewHolder(V holder,int position);
 public abstract int getItemCount();}
 }''',
'androidx/recyclerview/widget/LinearLayoutManager.java':'''package androidx.recyclerview.widget;
 public class LinearLayoutManager{public LinearLayoutManager(android.content.Context c){}}''',
'androidx/recyclerview/widget/DiffUtil.java':'''package androidx.recyclerview.widget;
 public class DiffUtil{
 public abstract static class Callback{public abstract int getOldListSize();public abstract int getNewListSize();
 public abstract boolean areItemsTheSame(int a,int b);public abstract boolean areContentsTheSame(int a,int b);}
 public static DiffResult calculateDiff(Callback c,boolean b){return new DiffResult();}
 public static class DiffResult{public void dispatchUpdatesTo(RecyclerView.Adapter<?> adapter){}}
 }''',
'androidx/documentfile/provider/DocumentFile.java':'''package androidx.documentfile.provider;
 public class DocumentFile{
 public static DocumentFile fromSingleUri(android.content.Context c,android.net.Uri u){return null;}
 public boolean exists(){return false;}public boolean delete(){return false;}}
''',
'org/json/JSONObject.java':'''package org.json;public class JSONObject{
 public JSONObject(){}public JSONObject(String s){}
 public JSONObject put(String k,Object v){return this;}
 public String optString(String k,String d){return d;}
 public long optLong(String k,long d){return d;}}
''',
'org/json/JSONArray.java':'''package org.json;public class JSONArray{
 public JSONArray(){}public JSONArray(String s){}
 public JSONArray put(Object value){return this;}public int length(){return 0;}
 public JSONObject optJSONObject(int i){return null;}}
''',
'ru/reader/rsvp/NavigationActivity.java':'''package ru.reader.rsvp;
 public class NavigationActivity extends android.app.Activity{}''',
'ru/reader/rsvp/R.java':'''package ru.reader.rsvp;public class R{
 public static class drawable{public static final int ic_download_restore=1;
 public static final int ic_download_archive_delete=2;public static final int ic_download_archive_clear=3;}}
''',
'ru/reader/rsvp/SafeSystemBars.java':'''package ru.reader.rsvp;public class SafeSystemBars{
 public static void matchBackground(android.content.Context c,int color){}}''',
'ru/reader/rsvp/SwipeDialogs.java':'''package ru.reader.rsvp;public class SwipeDialogs{
 public static class Builder{public Builder(android.app.Activity a){}
 public Builder setTitle(String s){return this;}public Builder setMessage(String s){return this;}
 public Builder setNegativeButton(String s,Click l){return this;}
 public Builder setPositiveButton(String s,Click l){return this;}
 public void show(){}}
 public interface Click{void onClick(Object dialog,int which);}}
''',
'ru/reader/rsvp/DownloadsRepository.java':'''package ru.reader.rsvp;import java.util.*;
 public class DownloadsRepository{
 public static final String AUDIO="audiobook",EDGE="edge",MANGA="mangalib",LINK="direct_link";
 public static class Item{public String id="",title="",type="",cover="",description="",source="",bookUri="";
 public boolean ready,running;public BookImportManager.Job job;public LinkDownloadStore.Batch link;}
 public static ArrayList<Item> load(android.content.Context c,Object live){return new ArrayList<>();}
 public static void hideEdge(android.content.Context c,String uri){}
 }''',
'ru/reader/rsvp/BookImportManager.java':'''package ru.reader.rsvp;
 public class BookImportManager{public static class Job{
 public String source="";public java.util.ArrayList<Object> chapters=new java.util.ArrayList<>();}
 public static java.io.File trackFile(android.content.Context c,Job j,int k){return null;}
 public static String relocated(Job j,int n){return "";}
 public static void remove(android.content.Context c,Job j,boolean files){}
 }''',
'ru/reader/rsvp/AudioLibrary.java':'''package ru.reader.rsvp;import java.util.*;
 public class AudioLibrary{public static class Book{public String id="";}
 public static ArrayList<Book> load(android.content.Context c){return new ArrayList<>();}
 public static void save(android.content.Context c,ArrayList<Book> books){}}
''',
'ru/reader/rsvp/AudiobookCoverStore.java':'''package ru.reader.rsvp;
 public class AudiobookCoverStore{public static java.io.File coverFile(android.content.Context c,BookImportManager.Job j){return null;}}
''',
'ru/reader/rsvp/LinkDownloadStore.java':'''package ru.reader.rsvp;
 public class LinkDownloadStore{public static class Entry{public String savedUri="";}
 public static class Batch{public String id="";public java.util.ArrayList<Entry> files=new java.util.ArrayList<>();}
 public static Batch get(android.content.Context c,String id){return null;}
 public static void remove(android.content.Context c,String id){}}
''',
'ru/reader/rsvp/MangaLibStorage.java':'''package ru.reader.rsvp;
 public class MangaLibStorage{public static class Job{public String slug="",file="",status="";
 public int done,total;public long bytes;}
 public static java.io.File root(android.content.Context c){return null;}
 public static java.util.ArrayList<Job> jobs(android.content.Context c){return new java.util.ArrayList<>();}
 public static void put(android.content.Context c,Job j){}
 }''',
'ru/reader/rsvp/MangaDownloadCover.java':'''package ru.reader.rsvp;
 public class MangaDownloadCover{public static void invalidate(android.content.Context c,String slug){}}''',
'ru/reader/rsvp/EdgeFullBookService.java':'''package ru.reader.rsvp;
 public class EdgeFullBookService{public static boolean isServiceAlive(){return false;}}''',
'ru/reader/rsvp/EdgeFullBookStore.java':'''package ru.reader.rsvp;
 public class EdgeFullBookStore{
 public static final String RUNNING="running",QUEUED="queued",WAITING="waiting",SAVING="saving";
 public static class Job{public String status="";public java.io.File directory;}
 public static Job find(android.content.Context c,String uri,String title){return null;}}
''',
'ru/reader/rsvp/EdgeFolderPicker.java':'''package ru.reader.rsvp;
 public class EdgeFolderPicker{public static boolean available(android.content.Context c){return false;}}
''',
'ru/reader/rsvp/EdgeChapterArchive.java':'''package ru.reader.rsvp;
 public class EdgeChapterArchive{public static boolean deleteSavedBook(android.content.Context c,String uri){return false;}
 public static void invalidate(){}}
''',
'ru/reader/rsvp/EdgeBookStorage.java':'''package ru.reader.rsvp;
 public class EdgeBookStorage{public static java.io.File bookFolder(android.content.Context c,String uri,String title){return null;}
 public static java.io.File booksRoot(android.content.Context c){return null;}}
''',
}
for path,contents in src.items():
 p=base/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(contents)
PY
mkdir -p "$T/bin"
javac -encoding UTF-8 -source 17 -target 17 -d "$T/bin" $(find "$T/src" -name '*.java') \
 app/src/main/java/ru/reader/rsvp/DownloadArchiveStore.java \
 app/src/main/java/ru/reader/rsvp/DownloadArchiveDelete.java \
 app/src/main/java/ru/reader/rsvp/DownloadArchiveActivity.java
printf '%s\n' 'PASS: actual DownloadArchiveStore, DownloadArchiveDelete and DownloadArchiveActivity typechecked with Android stubs'
