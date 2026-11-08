#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
mkdir -p "$T/src/android/content" "$T/src/android/database/sqlite" "$T/src/android/database" "$T/src/android/graphics/drawable" "$T/src/android/graphics" "$T/src/android/app" "$T/src/android/media" "$T/src/android/os" "$T/src/android/text" "$T/src/android/view" "$T/src/android/widget" "$T/src/ru/reader/rsvp" "$T/bin"
cat > "$T/src/android/content/Context.java" <<'JAVA'
package android.content;
public class Context {public static final int MODE_PRIVATE=0;public Context getApplicationContext(){return this;}public SharedPreferences getSharedPreferences(String s,int m){return null;}public android.content.res.Resources getResources(){return null;}public void startActivity(Intent i){}public java.io.File getCacheDir(){return null;}}
JAVA
mkdir -p "$T/src/android/content/res"
cat > "$T/src/android/content/res/Resources.java" <<'JAVA'
package android.content.res;public class Resources{public Metrics getDisplayMetrics(){return new Metrics();}public static class Metrics{public float density=1;}}
JAVA
cat > "$T/src/android/content/res/ColorStateList.java" <<'JAVA'
package android.content.res;public class ColorStateList{public static ColorStateList valueOf(int i){return new ColorStateList();}}
JAVA
cat > "$T/src/android/content/SharedPreferences.java" <<'JAVA'
package android.content;
public interface SharedPreferences{String getString(String k,String d);int getInt(String k,int d);boolean getBoolean(String k,boolean d);Editor edit();interface Editor{Editor putBoolean(String k,boolean b);Editor putInt(String k,int n);boolean commit();}}
JAVA
cat > "$T/src/android/content/ContentValues.java" <<'JAVA'
package android.content;public class ContentValues{public void put(String k,String v){}public void put(String k,Integer v){}}
JAVA
cat > "$T/src/android/content/Intent.java" <<'JAVA'
package android.content;public class Intent{public Intent(){}public Intent(Context c,Class<?> x){}public String getStringExtra(String k){return null;}public boolean hasExtra(String k){return false;}public int getIntExtra(String k,int d){return d;}public Intent putExtra(String k,String v){return this;}public Intent putExtra(String k,int v){return this;}}
JAVA
cat > "$T/src/android/database/Cursor.java" <<'JAVA'
package android.database;public interface Cursor extends AutoCloseable{boolean moveToFirst();boolean moveToNext();int getInt(int c);String getString(int c);long getLong(int c);void close();}
JAVA
cat > "$T/src/android/database/sqlite/SQLiteDatabase.java" <<'JAVA'
package android.database.sqlite;public class SQLiteDatabase{public void execSQL(String s){}public void beginTransaction(){}public void setTransactionSuccessful(){}public void endTransaction(){}public int delete(String tab,String where,String[] args){return 0;}public long insertOrThrow(String t,String unused,android.content.ContentValues v){return 0;}public int update(String t,android.content.ContentValues v,String w,String[] args){return 0;}public android.database.Cursor rawQuery(String s,String[] a){return null;}}
JAVA
cat > "$T/src/android/database/sqlite/SQLiteOpenHelper.java" <<'JAVA'
package android.database.sqlite;public abstract class SQLiteOpenHelper {public SQLiteOpenHelper(android.content.Context c,String s,Object factory,int v){}public abstract void onCreate(SQLiteDatabase db);public abstract void onUpgrade(SQLiteDatabase db,int a,int b);public SQLiteDatabase getWritableDatabase(){return null;}public SQLiteDatabase getReadableDatabase(){return null;}public void close(){}}
JAVA
cat > "$T/src/android/app/Activity.java" <<'JAVA'
package android.app;public class Activity extends android.content.Context{public static final int RESULT_OK=-1;public void onCreate(android.os.Bundle b){}protected void onResume(){}protected void onDestroy(){}public void setContentView(android.view.View v){}public android.content.Intent getIntent(){return new android.content.Intent();}public void setResult(int c,android.content.Intent i){}public boolean isFinishing(){return false;}public boolean isDestroyed(){return false;}}
JAVA
cat > "$T/src/android/app/AlertDialog.java" <<'JAVA'
package android.app;public class AlertDialog{public static final int BUTTON_POSITIVE=-1;public void dismiss(){}public android.widget.Button getButton(int n){return null;}public void setOnShowListener(android.content.DialogInterface.OnShowListener l){}public void show(){}public static class Builder{public Builder(android.content.Context ctx){}public Builder setTitle(String s){return this;}public Builder setView(android.view.View v){return this;}public Builder setNegativeButton(String s,android.content.DialogInterface.OnClickListener l){return this;}public Builder setPositiveButton(String s,android.content.DialogInterface.OnClickListener l){return this;}public Builder setNeutralButton(String s,android.content.DialogInterface.OnClickListener l){return this;}public Builder setItems(String[] items,android.content.DialogInterface.OnClickListener l){return this;}public AlertDialog create(){return new AlertDialog();}public AlertDialog show(){return new AlertDialog();}}}
JAVA
cat > "$T/src/android/content/DialogInterface.java" <<'JAVA'
package android.content;public interface DialogInterface {interface OnClickListener {void onClick(DialogInterface dialog,int which);}interface OnShowListener{void onShow(DialogInterface dialog);}}
JAVA
cat > "$T/src/android/graphics/drawable/Drawable.java" <<'JAVA'
package android.graphics.drawable;public class Drawable{}
JAVA
cat > "$T/src/android/graphics/drawable/RippleDrawable.java" <<'JAVA'
package android.graphics.drawable;public class RippleDrawable extends Drawable{public RippleDrawable(android.content.res.ColorStateList c,Drawable d,Drawable mask){}}
JAVA
cat > "$T/src/android/graphics/drawable/GradientDrawable.java" <<'JAVA'
package android.graphics.drawable;public class GradientDrawable extends Drawable{public void setColor(int v){}public void setCornerRadius(float r){}}
JAVA
cat > "$T/src/android/graphics/Color.java" <<'JAVA'
package android.graphics;public class Color{public static int red(int c){return 0;}public static int green(int c){return 0;}public static int blue(int c){return 0;}}
JAVA
cat > "$T/src/android/os/Bundle.java" <<'JAVA'
package android.os;public class Bundle{}
JAVA
cat > "$T/src/android/os/Looper.java" <<'JAVA'
package android.os;public class Looper{public static Looper getMainLooper(){return null;}}
JAVA
cat > "$T/src/android/os/Handler.java" <<'JAVA'
package android.os;public class Handler{public Handler(Looper l){}public boolean post(Runnable r){return false;}}
JAVA
cat > "$T/src/android/media/MediaPlayer.java" <<'JAVA'
package android.media;public class MediaPlayer{public void setDataSource(String f){}public void setOnCompletionListener(OnCompletionListener l){}public interface OnCompletionListener{void onCompletion(MediaPlayer p);}public void prepare(){}public void start(){}public void release(){}public PlaybackParams getPlaybackParams(){return new PlaybackParams();}public void setPlaybackParams(PlaybackParams p){}public static class PlaybackParams{public PlaybackParams setSpeed(float f){return this;}}}
JAVA
cat > "$T/src/android/text/Editable.java" <<'JAVA'
package android.text;public interface Editable extends CharSequence{}
JAVA
cat > "$T/src/android/text/TextWatcher.java" <<'JAVA'
package android.text;public interface TextWatcher{void beforeTextChanged(CharSequence s,int a,int c,int x);void onTextChanged(CharSequence s,int a,int b,int c);void afterTextChanged(Editable e);}
JAVA
cat > "$T/src/android/text/InputType.java" <<'JAVA'
package android.text;public class InputType{public static final int TYPE_CLASS_TEXT=1,TYPE_TEXT_VARIATION_VISIBLE_PASSWORD=2,TYPE_TEXT_FLAG_NO_SUGGESTIONS=4;}
JAVA
cat > "$T/src/android/view/View.java" <<'JAVA'
package android.view;public class View{public static final int VISIBLE=0,GONE=8;public interface OnClickListener{void onClick(View v);}public void setOnClickListener(OnClickListener l){}public void setPadding(int a,int b,int c,int d){}public void setBackground(android.graphics.drawable.GradientDrawable g){}public void setBackgroundColor(int c){}public void setBackground(android.graphics.drawable.Drawable d){}public void setVisibility(int v){}public int getVisibility(){return 0;}public void post(Runnable r){}public int getTop(){return 0;}public void setContentDescription(String s){}public android.view.ViewGroup.LayoutParams getLayoutParams(){return null;}public void setLayoutParams(android.view.ViewGroup.LayoutParams p){}}
JAVA
cat > "$T/src/android/view/ViewGroup.java" <<'JAVA'
package android.view;public class ViewGroup extends View{public void removeAllViews(){}public void addView(View v){}public void addView(View v,LayoutParams p){}public static class LayoutParams{public int width;public LayoutParams(int w,int h){width=w;}}}
JAVA
cat > "$T/src/android/view/Gravity.java" <<'JAVA'
package android.view;public class Gravity{public static final int CENTER=1,CENTER_VERTICAL=2;}
JAVA
cat > "$T/src/android/widget/TextView.java" <<'JAVA'
package android.widget;public class TextView extends android.view.View{public TextView(android.content.Context c){}public void setText(String s){}public void setTextSize(float n){}public void setTextColor(int c){}public void setGravity(int g){}public void setMaxLines(int n){}public void setHint(String s){}public void setHintTextColor(int c){}public void setBackgroundTintList(android.content.res.ColorStateList color){}public void setEnabled(boolean b){}public void setSelection(int n){}public int length(){return 0;}public android.text.Editable getText(){return null;}public void setSingleLine(boolean b){}public void setMinLines(int l){}public void setInputType(int i){}public void addTextChangedListener(android.text.TextWatcher t){}}
JAVA
cat > "$T/src/android/widget/EditText.java" <<'JAVA'
package android.widget;public class EditText extends TextView{public EditText(android.content.Context c){super(c);}}
JAVA
cat > "$T/src/android/widget/Button.java" <<'JAVA'
package android.widget;public class Button extends TextView{public Button(android.content.Context c){super(c);}public void setAllCaps(boolean v){}}
JAVA
cat > "$T/src/android/widget/CompoundButton.java" <<'JAVA'
package android.widget;public class CompoundButton extends Button{public CompoundButton(android.content.Context c){super(c);}public void setButtonTintList(android.content.res.ColorStateList l){}public interface OnCheckedChangeListener{void onCheckedChanged(CompoundButton b,boolean c);}public void setOnCheckedChangeListener(OnCheckedChangeListener l){}public void setChecked(boolean b){}public boolean isChecked(){return false;}}
JAVA
cat > "$T/src/android/widget/CheckBox.java" <<'JAVA'
package android.widget;public class CheckBox extends CompoundButton{public CheckBox(android.content.Context c){super(c);}}
JAVA
cat > "$T/src/android/widget/ImageButton.java" <<'JAVA'
package android.widget;public class ImageButton extends Button{public ImageButton(android.content.Context c){super(c);}public void setImageResource(int r){}public void setColorFilter(int c){}}
JAVA
cat > "$T/src/android/widget/LinearLayout.java" <<'JAVA'
package android.widget;public class LinearLayout extends android.view.ViewGroup{public static final int VERTICAL=1;public LinearLayout(android.content.Context c){}public void setOrientation(int v){}public void setGravity(int v){}public static class LayoutParams extends android.view.ViewGroup.LayoutParams{public int bottomMargin,rightMargin,leftMargin;public float weight;public LayoutParams(int x,int y){super(x,y);}public LayoutParams(int x,int y,float weight){super(x,y);}}}
JAVA
cat > "$T/src/android/widget/ScrollView.java" <<'JAVA'
package android.widget;public class ScrollView extends android.view.ViewGroup{public ScrollView(android.content.Context c){}public void smoothScrollTo(int x,int y){}}
JAVA
cat > "$T/src/android/widget/Toast.java" <<'JAVA'
package android.widget;public class Toast{public static final int LENGTH_LONG=1,LENGTH_SHORT=0;public static Toast makeText(android.content.Context c,String s,int d){return new Toast();}public void show(){}}
JAVA
cat > "$T/src/ru/reader/rsvp/NavigationActivity.java" <<'JAVA'
package ru.reader.rsvp;public class NavigationActivity extends android.app.Activity{}
JAVA
cat > "$T/src/ru/reader/rsvp/SafeSystemBars.java" <<'JAVA'
package ru.reader.rsvp;public class SafeSystemBars{public static void matchBackground(android.app.Activity a,int bg){}}
JAVA
cat > "$T/src/ru/reader/rsvp/UniversalDrawer.java" <<'JAVA'
package ru.reader.rsvp;public class UniversalDrawer{public static void show(android.app.Activity a){}}
JAVA
cat > "$T/src/ru/reader/rsvp/R.java" <<'JAVA'
package ru.reader.rsvp;public final class R{public static final class drawable{public static final int ic_menu_lines=1,ic_plus=2,ic_edit_name=3,ic_tts_engine=4,ic_microphone=5;}}
JAVA
cat > "$T/src/ru/reader/rsvp/EdgeTtsClient.java" <<'JAVA'
package ru.reader.rsvp;public class EdgeTtsClient{public interface Callback{void progress(int count,long size);void finished(byte[] data,int n,String error);}public static EdgeTtsClient synthesize(String text,String voice,String rate,int priority,Callback cb){return null;}public void cancel(){}}
JAVA
cat > "$T/src/ru/reader/rsvp/EdgeSpeechQueue.java" <<'JAVA'
package ru.reader.rsvp;public class EdgeSpeechQueue{public static final int PREFETCH=1;}
JAVA
cat > "$T/src/ru/reader/rsvp/UiColorScheme.java" <<'JAVA'
package ru.reader.rsvp;public class UiColorScheme{public static int accent(android.content.Context c){return 0xffa26949;}public static int neutral(android.content.Context c){return 0xfff4e4d6;}public static int selected(android.content.Context c){return 0xffeed5cf;}public static int onAccent(android.content.Context c){return 0xffffffff;}public static int blend(int a,int b,float f){return a;}}
JAVA
cat > "$T/src/ru/reader/rsvp/SwipeDialogs.java" <<'JAVA'
package ru.reader.rsvp;public class SwipeDialogs{public static class Builder extends android.app.AlertDialog.Builder{public Builder(android.content.Context c){super(c);}}}
JAVA
cat > "$T/src/ru/reader/rsvp/TtsWpm.java" <<'JAVA'
package ru.reader.rsvp;public class TtsWpm{public static float rate(android.content.Context c){return 1.0f;}}
JAVA
cat > "$T/src/ru/reader/rsvp/EdgeWordTimings.java" <<'JAVA'
package ru.reader.rsvp;public class EdgeWordTimings{public static class Word{public int from,to;public long startMs,durationMs;public Word(long start,long duration,int from,int to){this.from=from;this.to=to;}}public static class Boundary{}public static java.util.List<Word> align(String s,java.util.List<Boundary>b){return java.util.List.of();}}
JAVA
cat > "$T/src/ru/reader/rsvp/EdgeAudioManager.java" <<'JAVA'
package ru.reader.rsvp;public class EdgeAudioManager{public static String key(String text,String voice,String rate){return text+voice+rate;}}
JAVA
javac -encoding UTF-8 -d "$T/bin" $(find "$T/src" -name '*.java') \
 app/src/main/java/ru/reader/rsvp/EdgeSpeechTextTransform.java \
 app/src/main/java/ru/reader/rsvp/EdgeDictionaryStore.java \
 app/src/main/java/ru/reader/rsvp/EdgeDictionaryActivity.java \
 app/src/main/java/ru/reader/rsvp/ActivityInfo.java
printf '%s\n' 'PASS: SQLite store, dictionary editor, HelpActivity and transform cross-compile against Android API type stand-ins'
