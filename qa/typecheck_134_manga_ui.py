#!/usr/bin/env python3
"""Typecheck the actual new manga detail Activity against focused Android API stubs."""
from pathlib import Path
import tempfile, subprocess
root=Path(__file__).resolve().parents[1]
source=root/'app/src/main/java/ru/reader/rsvp'
stubs={
'android/content/Context.java':'''package android.content;public class Context{public android.content.res.Resources getResources(){return new android.content.res.Resources();}}''',
'android/content/res/Resources.java':'''package android.content.res;public class Resources{public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}}''',
'android/util/DisplayMetrics.java':'''package android.util;public class DisplayMetrics{public float density=1;}''',
'android/content/Intent.java':'''package android.content;public class Intent{public Intent(Context c,Class<?> cl){}public String getStringExtra(String k){return "";}public Intent putExtra(String k,String s){return this;}}''',
'android/graphics/Typeface.java':'''package android.graphics;public class Typeface{public static final Typeface DEFAULT=new Typeface();public static final int BOLD=1;}''',
'android/graphics/drawable/GradientDrawable.java':'''package android.graphics.drawable;public class GradientDrawable{public void setColor(int c){}public void setCornerRadius(float r){}}''',
'android/os/Bundle.java':'''package android.os;public class Bundle{}''',
'android/os/Looper.java':'''package android.os;public class Looper{public static Looper getMainLooper(){return new Looper();}}''',
'android/os/Handler.java':'''package android.os;public class Handler{public Handler(Looper l){}public void post(Runnable r){}public void postDelayed(Runnable r,long delay){}public void removeCallbacks(Runnable r){}public void removeCallbacksAndMessages(Object token){}}''',
'android/view/View.java':'''package android.view;public class View{public interface OnClickListener{void onClick(View v);}public void setOnClickListener(OnClickListener l){}public void setBackground(android.graphics.drawable.GradientDrawable d){}public void setPadding(int l,int t,int r,int b){}public void post(Runnable r){}public void setBackgroundColor(int color){}}''',
'android/view/Gravity.java':'''package android.view;public class Gravity{public static final int CENTER=1,CENTER_VERTICAL=2;}''',
'android/widget/LinearLayout.java':'''package android.widget;public class LinearLayout extends android.view.View{public static final int VERTICAL=1;public LinearLayout(android.content.Context c){}public void setOrientation(int mode){}public void setGravity(int mode){}public void addView(android.view.View child){}public void addView(android.view.View child,LayoutParams p){}public void removeAllViews(){}public static class LayoutParams{public int topMargin,leftMargin,bottomMargin;public LayoutParams(int w,int h){}public LayoutParams(int w,int h,float weight){}}}''',
'android/widget/TextView.java':'''package android.widget;public class TextView extends android.view.View{public TextView(android.content.Context c){}public void setText(CharSequence t){}public void setTextSize(int s){}public void setTextColor(int c){}public void setTypeface(android.graphics.Typeface f,int style){}public void setGravity(int g){}public void setSingleLine(boolean b){}}''',
'android/widget/ProgressBar.java':'''package android.widget;public class ProgressBar extends android.view.View{public ProgressBar(android.content.Context c,Object a,int style){}public void setMax(int n){}public void setProgress(int n){}}''',
'android/widget/ScrollView.java':'''package android.widget;public class ScrollView extends android.view.View{public ScrollView(android.content.Context c){}public void addView(android.view.View v){}public int getScrollY(){return 0;}public void scrollTo(int x,int y){}}''',
'android/widget/Toast.java':'''package android.widget;public class Toast{public static final int LENGTH_SHORT=0;public static Toast makeText(android.content.Context c,String s,int n){return new Toast();}public void show(){}}''',
'android/R.java':'''package android;public class R{public static class attr{public static int progressBarStyleHorizontal=0;}}''',
'ru/reader/rsvp/NavigationActivity.java':'''package ru.reader.rsvp;public class NavigationActivity extends android.content.Context{protected void onCreate(android.os.Bundle b){}protected void onResume(){}protected void onPause(){}protected void onDestroy(){}public void setContentView(android.view.View v){}public android.content.Intent getIntent(){return null;}public void finish(){}public void startActivity(android.content.Intent i){}}''',
'ru/reader/rsvp/SafeSystemBars.java':'''package ru.reader.rsvp;public class SafeSystemBars{public static void matchBackground(android.content.Context c,int color){}}''',
'ru/reader/rsvp/UniversalDrawer.java':'''package ru.reader.rsvp;public class UniversalDrawer{public static void show(android.content.Context c){}}''',
'ru/reader/rsvp/UiColorScheme.java':'''package ru.reader.rsvp;public class UiColorScheme{public static int accent(android.content.Context c){return 0;}}''',
'ru/reader/rsvp/ComicReaderActivity.java':'''package ru.reader.rsvp;public class ComicReaderActivity extends NavigationActivity{}''',
'ru/reader/rsvp/MangaLibStorage.java':'''package ru.reader.rsvp;import java.util.*;public class MangaLibStorage{public static class Job{public String slug,series,volume,number,chapter,status,error;public long bytes;public int done,total;}public static ArrayList<Job> jobs(android.content.Context c){return new ArrayList<>();}public static String comicUri(String slug){return slug;}}''',
'ru/reader/rsvp/MangaLibQueue.java':'''package ru.reader.rsvp;import java.util.*;public class MangaLibQueue{public static int resume(android.content.Context c,String slug){return 0;}public static int pause(android.content.Context c,String slug){return 0;}public static int enqueue(android.content.Context c,List<MangaLibStorage.Job> j){return 0;}}''',
}
with tempfile.TemporaryDirectory(prefix='mangaui134_') as folder:
    base=Path(folder);files=[]
    for name,data in stubs.items():
        f=base/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(data);files.append(f)
    proc=subprocess.run(['javac','-encoding','UTF-8','-d',str(base/'out'),*(str(f) for f in files),str(source/'MangaLibChapterDownloadsActivity.java')],capture_output=True,text=True)
    if proc.returncode:raise RuntimeError(proc.stderr)
print('PASS: new MangaLib chapter download Activity compiles against focused Android API stubs')
