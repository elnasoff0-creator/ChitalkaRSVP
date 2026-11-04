#!/bin/sh
# Compile the newly added Android-facing classes against a small API-type stand-in.
# This supplements syntax QA; it is not an Android APK build.
set -eu
cd "$(dirname "$0")/.."
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/src/android/content/res" "$T/src/android/content" "$T/src/android/graphics/drawable" "$T/src/android/graphics" "$T/src/android/os" "$T/src/android/view" "$T/src/org/json" "$T/src/ru/reader/rsvp" "$T/bin"
cat > "$T/src/android/content/Context.java" <<'EOF'
package android.content;
public class Context {public static final int MODE_PRIVATE=0;public android.content.res.Resources getResources(){return new android.content.res.Resources();}public Object getTheme(){return null;}public SharedPreferences getSharedPreferences(String n,int m){return null;}}
EOF
cat > "$T/src/android/content/SharedPreferences.java" <<'EOF'
package android.content;
public interface SharedPreferences{int getInt(String key,int def);String getString(String key,String def);Editor edit();interface Editor{Editor putInt(String key,int value);Editor putString(String key,String value);void apply();}}
EOF
cat > "$T/src/android/content/res/Resources.java" <<'EOF'
package android.content.res;
public class Resources{public static class DisplayMetrics{public float density=1;}public DisplayMetrics getDisplayMetrics(){return new DisplayMetrics();}public android.graphics.drawable.Drawable getDrawable(int id,Object theme){return new android.graphics.drawable.Drawable();}}
EOF
cat > "$T/src/android/graphics/Paint.java" <<'EOF'
package android.graphics;
public class Paint{public static final int ANTI_ALIAS_FLAG=1;public enum Style{FILL,STROKE}public Paint(int flags){}public void setColor(int color){}public void setTypeface(Typeface f){}public void setTextSize(float size){}public void setStyle(Style style){}public void setStrokeWidth(float width){}}
EOF
cat > "$T/src/android/graphics/Typeface.java" <<'EOF'
package android.graphics;public class Typeface{public static final Typeface DEFAULT_BOLD=new Typeface();}
EOF
cat > "$T/src/android/graphics/Color.java" <<'EOF'
package android.graphics;public class Color{}
EOF
cat > "$T/src/android/graphics/RectF.java" <<'EOF'
package android.graphics;
public class RectF{public float left,top,right,bottom;public RectF(float a,float b,float c,float d){left=a;top=b;right=c;bottom=d;}public void inset(float a,float b){}public float width(){return right-left;}public float height(){return bottom-top;}}
EOF
cat > "$T/src/android/graphics/Canvas.java" <<'EOF'
package android.graphics;
public class Canvas{public void drawColor(int color){}public void drawRoundRect(RectF rect,float x,float y,Paint p){}public void drawLine(float a,float b,float c,float d,Paint p){}public void drawText(String s,float x,float y,Paint p){}}
EOF
cat > "$T/src/android/graphics/drawable/Drawable.java" <<'EOF'
package android.graphics.drawable;
public class Drawable{public Drawable mutate(){return this;}public void setBounds(int a,int b,int c,int d){}public void draw(android.graphics.Canvas c){}}
EOF
cat > "$T/src/android/os/SystemClock.java" <<'EOF'
package android.os;public class SystemClock{public static long elapsedRealtime(){return 0;}}
EOF
cat > "$T/src/android/view/View.java" <<'EOF'
package android.view;
public class View{public static final int LAYER_TYPE_SOFTWARE=1;private android.content.Context c;public View(android.content.Context ctx){c=ctx;}public android.content.Context getContext(){return c;}public android.content.res.Resources getResources(){return c.getResources();}public int getWidth(){return 300;}public int getHeight(){return 300;}public void setLayerType(int n,Object p){}public void setContentDescription(String s){}public void setClickable(boolean b){}public void postInvalidateDelayed(long n){}public void invalidate(){}protected void onDraw(android.graphics.Canvas c){}public boolean onTouchEvent(MotionEvent e){return true;}}
EOF
cat > "$T/src/android/view/MotionEvent.java" <<'EOF'
package android.view;public class MotionEvent{public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_CANCEL=3;public int getActionMasked(){return 0;}public float getX(){return 0;}public float getY(){return 0;}}
EOF
cat > "$T/src/org/json/JSONObject.java" <<'EOF'
package org.json;public class JSONObject{public String optString(String s){return "";}public JSONObject put(String s,Object o){return this;}}
EOF
cat > "$T/src/org/json/JSONArray.java" <<'EOF'
package org.json;public class JSONArray{public JSONArray(String s){}public int length(){return 0;}public JSONObject optJSONObject(int i){return null;}}
EOF
cat > "$T/src/ru/reader/rsvp/R.java" <<'EOF'
package ru.reader.rsvp;public final class R{public static final class drawable{public static final int ic_game_book_open=1,ic_game_book_upright=2;}}
EOF
cat > "$T/src/ru/reader/rsvp/ChteicoStore.java" <<'EOF'
package ru.reader.rsvp;
public class ChteicoStore{public static int stage(android.content.Context c){return 1;}public static int branch(android.content.Context c){return 1;}public static void addFlyerGameExperience(android.content.Context c,int x,float d){}public static void addGameExperience(android.content.Context c,int x,float d){}}
EOF
cat > "$T/src/ru/reader/rsvp/ChteicoPetView.java" <<'EOF'
package ru.reader.rsvp;public class ChteicoPetView{public static int gameFrameResource(int a,int b,boolean u){return 1;}}
EOF
javac -encoding UTF-8 -d "$T/bin" $(find "$T/src" -name '*.java') app/src/main/java/ru/reader/rsvp/ChteicoMiniGameView.java app/src/main/java/ru/reader/rsvp/UnifiedReadingProgress.java
printf '%s\n' 'PASS: ChteicoMiniGameView and UnifiedReadingProgress compile against Android/API type stand-ins'
