#!/bin/sh
# Compiles only the new fishing Java class against minimal Android API signatures.
# Complements source syntax checks. It does NOT replace Android Gradle assembleRelease.
set -eu
cd "$(dirname "$0")/.."
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
export T
python3 - <<'PY'
from pathlib import Path
import os
p=Path(os.environ['T'])/'src'
stubs={
'android/content/Context.java':'''package android.content; public class Context { public android.content.res.Resources getResources(){return null;} public SharedPreferences getSharedPreferences(String a,int b){return null;} }''',
'android/content/SharedPreferences.java':'''package android.content; public interface SharedPreferences{int getInt(String k,int d);}''',
'android/content/res/Resources.java':'''package android.content.res; public class Resources{public android.util.DisplayMetrics getDisplayMetrics(){return null;}}''',
'android/content/res/ColorStateList.java':'''package android.content.res; public class ColorStateList{public static ColorStateList valueOf(int c){return null;}}''',
'android/util/DisplayMetrics.java':'''package android.util; public class DisplayMetrics{public float density;}''',
'android/os/Looper.java':'''package android.os; public class Looper{public static Looper getMainLooper(){return null;}}''',
'android/os/Handler.java':'''package android.os; public class Handler{public Handler(Looper l){}public void post(Runnable r){}public void postDelayed(Runnable r,long d){}public void removeCallbacks(Runnable r){}}''',
'android/os/SystemClock.java':'''package android.os; public class SystemClock{public static long uptimeMillis(){return 0L;}}''',
'android/view/View.java':'''package android.view; public class View{public static final int VISIBLE=0,GONE=8;public View(android.content.Context c){}public android.content.Context getContext(){return null;}public android.content.res.Resources getResources(){return null;}public void setVisibility(int x){}public int getVisibility(){return 0;}public void setX(float x){}public void setY(float y){}public int getHeight(){return 100;}public int getWidth(){return 100;}public void setClickable(boolean a){}public void setFocusable(boolean a){}public void setContentDescription(String s){}public boolean isAttachedToWindow(){return true;}public boolean onTouchEvent(MotionEvent e){return false;}protected void onAttachedToWindow(){}protected void onDetachedFromWindow(){}}''',
'android/view/ViewGroup.java':'''package android.view; public class ViewGroup extends View{public ViewGroup(android.content.Context c){super(c);}public boolean onInterceptTouchEvent(MotionEvent e){return false;}}''',
'android/view/MotionEvent.java':'''package android.view; public class MotionEvent{public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_CANCEL=3;public int getActionMasked(){return 0;}public float getX(){return 0;}public float getY(){return 0;}}''',
'android/view/LayoutInflater.java':'''package android.view; public class LayoutInflater{public static LayoutInflater from(android.content.Context c){return null;}public View inflate(int x,ViewGroup v,boolean attach){return null;}}''',
'android/widget/FrameLayout.java':'''package android.widget; public class FrameLayout extends android.view.ViewGroup{public FrameLayout(android.content.Context c){super(c);}public <T extends android.view.View> T findViewById(int id){return null;}}''',
'android/widget/TextView.java':'''package android.widget; public class TextView extends android.view.View{public TextView(android.content.Context c){super(c);}public void setText(String s){}public CharSequence getText(){return "";}public void setTextColor(int i){}}''',
'android/widget/ImageView.java':'''package android.widget; public class ImageView extends android.view.View{public ImageView(android.content.Context c){super(c);}public void setImageResource(int i){}public void setColorFilter(int i){}}''',
'android/widget/ProgressBar.java':'''package android.widget; public class ProgressBar extends android.view.View{public ProgressBar(android.content.Context c){super(c);}public void setProgress(int i){}public void setProgressTintList(android.content.res.ColorStateList c){}}''',
'android/widget/Toast.java':'''package android.widget; public class Toast{public static final int LENGTH_SHORT=0;public static Toast makeText(android.content.Context c,String s,int duration){return null;}public void show(){}}''',
'ru/reader/rsvp/R.java':'''package ru.reader.rsvp;public class R{public static class layout{public static final int view_chteico_fishing=1;}public static class id{public static final int fishing_hud=1,fishing_status=2,fishing_shop=3,fishing_close=4,fishing_bobber=5,fishing_fish=6,fishing_zone=7,fishing_cast_meter=8,fishing_cast_optimum=9,fishing_cast_indicator=10,fishing_catch_panel=11,fishing_track=12,fishing_catch_progress=13;}public static class drawable{public static final int ic_fishing_fish_common=1,ic_fishing_fish_uncommon=2,ic_fishing_fish_rare=3,ic_fishing_fish_legendary=4,ic_fishing_fish_mythical=5;}}''',
'ru/reader/rsvp/ReaderButtonColors.java':'''package ru.reader.rsvp;public class ReaderButtonColors{public static int accent(android.content.Context c,int def){return def;}}''',
'ru/reader/rsvp/ChteicoStore.java':'''package ru.reader.rsvp;public class ChteicoStore{public static int fishingBait(android.content.Context c){return 0;}public static int fishingGold(android.content.Context c){return 0;}public static boolean useFishingBait(android.content.Context c){return false;}public static void recordFishingCatch(android.content.Context c,int a,int b,int d,int r){}public static boolean buyFishingBait(android.content.Context c,int n){return false;}}''',
'ru/reader/rsvp/SwipeDialogs.java':'''package ru.reader.rsvp;public class SwipeDialogs{public static class Builder{public Builder(android.content.Context c){}public Builder setTitle(String s){return this;}public Builder setMessage(String s){return this;}public Builder setPositiveButton(String s,Listener x){return this;}public Builder setNegativeButton(String s,Listener x){return this;}public Builder setItems(String[] labels,Listener x){return this;}public void show(){} }public interface Listener{void selected(Object dialog,int which);}}''',
}
for name,src in stubs.items():
 dest=p/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(src)
PY
mkdir -p "$T/bin"
javac -encoding UTF-8 -source 17 -target 17 -d "$T/bin" $(find "$T/src" -name '*.java') app/src/main/java/ru/reader/rsvp/ChteicoFishingView.java
printf '%s\n' 'PASS: Fishing Java typechecks against Android signature stand-ins'
