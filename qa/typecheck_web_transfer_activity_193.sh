#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)";trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/src/android/app" "$tmp/src/android/content" "$tmp/src/android/content/res" "$tmp/src/android/graphics/drawable" "$tmp/src/android/graphics" "$tmp/src/android/os" "$tmp/src/android/text" "$tmp/src/android/view" "$tmp/src/android/widget" "$tmp/src/com/google/zxing/common" "$tmp/src/com/google/zxing/qrcode" "$tmp/src/com/google/zxing" "$tmp/src/ru/reader/rsvp"
cat > "$tmp/src/android/content/Context.java" <<'JAVA'
package android.content;
public class Context {
 public static final String CLIPBOARD_SERVICE="clipboard";
 public Object getSystemService(String key){return null;}
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}
}
JAVA
cat > "$tmp/src/android/content/res/Resources.java" <<'JAVA'
package android.content.res;
public class Resources {public DisplayMetrics getDisplayMetrics(){return new DisplayMetrics();}
 public static final class DisplayMetrics{public float density=1f;}}
JAVA
cat > "$tmp/src/android/content/Intent.java" <<'JAVA'
package android.content;
public class Intent {public Intent(Context context,Class<?> target){}public Intent setAction(String action){return this;}
 public Intent putExtra(String key,String value){return this;}}
JAVA
cat > "$tmp/src/android/content/ClipData.java" <<'JAVA'
package android.content;public class ClipData {
 public static ClipData newPlainText(String label,String value){return new ClipData();}}
JAVA
cat > "$tmp/src/android/content/ClipboardManager.java" <<'JAVA'
package android.content;public class ClipboardManager {public void setPrimaryClip(ClipData data){}}
JAVA
cat > "$tmp/src/android/app/Activity.java" <<'JAVA'
package android.app;
public class Activity extends android.content.Context {
 protected void onCreate(android.os.Bundle b){}protected void onResume(){}protected void onPause(){}
 protected void onDestroy(){}protected void onActivityResult(int req,int result,android.content.Intent data){}
 public void setContentView(android.view.View v){}public android.content.Intent startService(android.content.Intent i){return i;}
 public android.content.Intent startForegroundService(android.content.Intent i){return i;}
 public void startActivity(android.content.Intent i){}public void startActivityForResult(android.content.Intent i,int request){}
 public boolean isFinishing(){return false;}public boolean isDestroyed(){return false;}
}
JAVA
cat > "$tmp/src/android/graphics/Color.java" <<'JAVA'
package android.graphics;public class Color {public static final int BLACK=0xff000000,WHITE=0xffffffff;}
JAVA
cat > "$tmp/src/android/graphics/Bitmap.java" <<'JAVA'
package android.graphics;public class Bitmap {
 public static class Config {public static final Config ARGB_8888=new Config();}
 public static Bitmap createBitmap(int w,int h,Config config){return new Bitmap();}
 public void setPixel(int x,int y,int color){}
}
JAVA
cat > "$tmp/src/android/graphics/Typeface.java" <<'JAVA'
package android.graphics;public class Typeface {public static final Typeface DEFAULT=new Typeface();public static final int BOLD=1;}
JAVA
cat > "$tmp/src/android/graphics/drawable/GradientDrawable.java" <<'JAVA'
package android.graphics.drawable;public class GradientDrawable {
 public void setColor(int color){}public void setCornerRadius(float radius){}
}
JAVA
cat > "$tmp/src/android/os/Build.java" <<'JAVA'
package android.os;public class Build {public static class VERSION {public static int SDK_INT=35;}}
JAVA
cat > "$tmp/src/android/os/Bundle.java" <<'JAVA'
package android.os;public class Bundle {}
JAVA
cat > "$tmp/src/android/os/Looper.java" <<'JAVA'
package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}
JAVA
cat > "$tmp/src/android/os/Handler.java" <<'JAVA'
package android.os;public class Handler {public Handler(Looper looper){}public boolean post(Runnable runnable){return true;}
 public boolean postDelayed(Runnable runnable,long millis){return true;}
 public void removeCallbacks(Runnable runnable){}
}
JAVA
cat > "$tmp/src/android/text/TextUtils.java" <<'JAVA'
package android.text;public class TextUtils {public enum TruncateAt{MIDDLE,END}}
JAVA
cat > "$tmp/src/android/view/Gravity.java" <<'JAVA'
package android.view;public class Gravity {public static final int CENTER_HORIZONTAL=1,CENTER_VERTICAL=2;}
JAVA
cat > "$tmp/src/android/view/View.java" <<'JAVA'
package android.view;public class View {
 public static final int VISIBLE=0,GONE=8;
 public interface OnClickListener {void onClick(View v);}
 public View(){}public void setBackground(Object o){}public void setPadding(int a,int b,int c,int d){}
 public void setBackgroundColor(int bg){}public void setContentDescription(String d){}
 public void setFocusable(boolean v){}public void setClickable(boolean v){}
 public void setOnClickListener(OnClickListener l){}public void setEnabled(boolean value){}
 public void setAlpha(float alpha){}public Object getForeground(){return null;}public void setForeground(Object o){}public void setVisibility(int visibility){}
}
JAVA
cat > "$tmp/src/android/widget/LinearLayout.java" <<'JAVA'
package android.widget;
public class LinearLayout extends android.view.View {
 public static final int VERTICAL=1;
 public LinearLayout(android.content.Context c){}
 public void setOrientation(int orientation){}public void setGravity(int gravity){}
 public void addView(android.view.View v){}public void addView(android.view.View v,LayoutParams params){}
 public android.view.View getChildAt(int i){return new LinearLayout(null);}
 public static class LayoutParams {
  public int topMargin,leftMargin,gravity;
  public LayoutParams(int w,int h){}public LayoutParams(int w,int h,float weight){}
 }
}
JAVA
cat > "$tmp/src/android/widget/ScrollView.java" <<'JAVA'
package android.widget;public class ScrollView extends android.view.View {
 public ScrollView(android.content.Context c){}public void setFillViewport(boolean v){}
 public void addView(android.view.View child){}
}
JAVA
cat > "$tmp/src/android/widget/TextView.java" <<'JAVA'
package android.widget;public class TextView extends android.view.View {
 public TextView(android.content.Context c){}public void setText(String value){}
 public void setTextSize(int size){}public void setTextColor(int color){}
 public void setTypeface(android.graphics.Typeface typeface,int style){}
 public void setTextIsSelectable(boolean b){}public void setSingleLine(boolean b){}
 public void setEllipsize(android.text.TextUtils.TruncateAt mode){}
 public void setGravity(int gravity){}
}
JAVA
cat > "$tmp/src/android/widget/ImageView.java" <<'JAVA'
package android.widget;public class ImageView extends android.view.View {
 public ImageView(android.content.Context c){}public void setImageResource(int image){}
 public void setColorFilter(int color){}public void setAdjustViewBounds(boolean ok){}
 public void setImageBitmap(android.graphics.Bitmap image){}public void setImageDrawable(Object obj){}
}
JAVA
cat > "$tmp/src/android/widget/PopupMenu.java" <<'JAVA'
package android.widget;public class PopupMenu {
 public PopupMenu(android.content.Context c,android.view.View anchor){}
 public Menu getMenu(){return new Menu();}public void show(){}
 public interface OnMenuItemClickListener {boolean onClick(Object item);}
 public static class Menu {public MenuItem add(String label){return new MenuItem();}}
 public static class MenuItem {public MenuItem setOnMenuItemClickListener(OnMenuItemClickListener listener){return this;}}
}
JAVA
cat > "$tmp/src/android/widget/Toast.java" <<'JAVA'
package android.widget;public class Toast {
 public static final int LENGTH_SHORT=0,LENGTH_LONG=1;
 public static Toast makeText(android.content.Context ctx,String text,int duration){return new Toast();}
 public void show(){}
}
JAVA
cat > "$tmp/src/android/app/AlertDialog.java" <<'JAVA'
package android.app;public class AlertDialog {}
JAVA
cat > "$tmp/src/com/google/zxing/BarcodeFormat.java" <<'JAVA'
package com.google.zxing;public enum BarcodeFormat {QR_CODE}
JAVA
cat > "$tmp/src/com/google/zxing/common/BitMatrix.java" <<'JAVA'
package com.google.zxing.common;public class BitMatrix {public boolean get(int x,int y){return false;}}
JAVA
cat > "$tmp/src/com/google/zxing/qrcode/QRCodeWriter.java" <<'JAVA'
package com.google.zxing.qrcode;
public class QRCodeWriter {
 public com.google.zxing.common.BitMatrix encode(String s,com.google.zxing.BarcodeFormat format,int w,int h){return null;}
}
JAVA
cat > "$tmp/src/ru/reader/rsvp/Dependencies.java" <<'JAVA'
package ru.reader.rsvp;
import android.content.*;
import android.app.*;
import android.view.*;
import android.widget.*;
class NavigationActivity extends Activity {}
class ActivityInfo extends NavigationActivity {}
final class UiColorScheme {
 static int background(Context c){return 0;}static int neutral(Context c){return 0;}
 static int accent(Context c){return 0;}static int foreground(Context c){return 0;}static int muted(Context c){return 0;}
}
final class AppToolbar {
 static TextView add(Activity a,LinearLayout b,String name){return new TextView(a);}
}
final class SafeSystemBars {static void matchBackground(Activity a,int color){}}
final class R {static class drawable {
 static final int ic_web_server_start=1,ic_web_server_stop=2,ic_info_help=3,
 ic_web_copy_address=4,ic_folder_audio=5;
}}
final class ReaderButtonColors {static int contrastingText(int c){return c;}}
final class WebTransferIndicator {WebTransferIndicator(Context c){}void start(){}void stop(){}}
final class WebTransferAuth {static String password(Context c)throws Exception{return "password";}}
final class WebTransferServer {
 String address(){return "";}String localAddress(){return "";}boolean running(){return true;}int activeUploads(){return 0;}
 int connections(){return 0;}String latest(){return "";}
}
final class WebTransferService {
 static final String ACTION_START="start",ACTION_STOP="stop";
 static WebTransferServer current(){return null;}static String lastError(){return "";}
}
final class SwipeDialogs {
 static class Builder {Builder(Context c){}Builder setTitle(String v){return this;}
 Builder setMessage(String v){return this;}Builder setNegativeButton(String v,Object listener){return this;}
 interface Click{void clicked(Object dialog,int which);}
 Builder setPositiveButton(String text,Click click){return this;}
 void show(){}
 }
}
final class PalettePopupMenu extends PopupMenu {PalettePopupMenu(Context c,View anchor){super(c,anchor);}}
final class DownloadFolderPicker {
 static final int BOOK_REQUEST=1,MANGA_REQUEST=2,AUDIO_REQUEST=3;
 static final String BOOK="book",MANGA="manga",AUDIO="audio";
 interface Callback{void completed(boolean ok);}
 static String summary(Context c,String type){return "";}
 static void choose(Activity a,int request,String key){}
 static boolean receive(Activity a,int req,int result,Intent data,int expected,String key,Callback cb){return true;}
}
JAVA
javac -encoding UTF-8 -d "$tmp/bin" $(find "$tmp/src" -name '*.java') \
 "$root/app/src/main/java/ru/reader/rsvp/WebTransferActivity.java"
echo 'PASS: WebTransferActivity Android API typecheck using isolated javac stubs'
