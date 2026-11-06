#!/usr/bin/env python3
"""API-shape compile of real shared palette and ReaderButtonColors with lightweight stand-ins."""
from pathlib import Path
import tempfile,subprocess,textwrap
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
stubs={
'android/content/SharedPreferences.java': '''package android.content;public interface SharedPreferences{int getInt(String k,int def);String getString(String k,String def);}''',
'android/content/Context.java': '''package android.content;public class Context{public static final int MODE_PRIVATE=0;public SharedPreferences getSharedPreferences(String s,int n){return null;}}''',
'android/content/res/ColorStateList.java': '''package android.content.res;public final class ColorStateList{public static ColorStateList valueOf(int c){return null;}}''',
'android/graphics/Color.java': '''package android.graphics;public final class Color{public static final int WHITE=0xffffffff,BLACK=0xff000000;public static int red(int c){return (c>>>16)&255;}public static int green(int c){return (c>>>8)&255;}public static int blue(int c){return c&255;}public static int rgb(int r,int g,int b){return (255<<24)|(r<<16)|(g<<8)|b;}}''',
'android/view/View.java':'''package android.view;public class View{}''',
'android/view/ViewGroup.java':'''package android.view;public class ViewGroup extends View{public int getChildCount(){return 0;}public View getChildAt(int i){return null;}}''',
'android/view/Window.java':'''package android.view;public class Window{public View getDecorView(){return null;}}''',
'android/widget/TextView.java':'''package android.widget;public class TextView extends android.view.View{}''',
'android/widget/Button.java':'''package android.widget;public class Button extends TextView{public void setTextColor(int c){}}''',
'android/widget/CompoundButton.java':'''package android.widget;public class CompoundButton extends Button{public void setButtonTintList(android.content.res.ColorStateList c){}}''',
'android/widget/CheckedTextView.java':'''package android.widget;public class CheckedTextView extends TextView{public void setCheckMarkTintList(android.content.res.ColorStateList c){}}''',
'android/widget/Switch.java':'''package android.widget;public class Switch extends CompoundButton{public void setThumbTintList(android.content.res.ColorStateList c){}public void setTrackTintList(android.content.res.ColorStateList c){}}''',
'android/widget/SeekBar.java':'''package android.widget;public class SeekBar extends android.view.View{public void setProgressTintList(android.content.res.ColorStateList c){}public void setThumbTintList(android.content.res.ColorStateList c){}}''',
'android/widget/EditText.java':'''package android.widget;public class EditText extends TextView{public void setBackgroundTintList(android.content.res.ColorStateList c){}}''',
'android/widget/AbsListView.java':'''package android.widget;public class AbsListView extends android.view.ViewGroup{public interface OnScrollListener{void onScrollStateChanged(AbsListView v,int state);void onScroll(AbsListView v,int first,int visible,int total);}public void post(Runnable r){}}''',
'android/widget/ListView.java':'''package android.widget;public class ListView extends AbsListView{public void setOnScrollListener(AbsListView.OnScrollListener l){}}''',
'android/app/AlertDialog.java':'''package android.app;public class AlertDialog{public static final int BUTTON_POSITIVE=-1,BUTTON_NEGATIVE=-2,BUTTON_NEUTRAL=-3;public android.widget.Button getButton(int n){return null;}public android.widget.ListView getListView(){return null;}public android.view.Window getWindow(){return null;}}''',
}
with tempfile.TemporaryDirectory(prefix='ui134_') as dirname:
    base=Path(dirname);files=[]
    for name,contents in stubs.items():
        p=base/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(contents);files.append(p)
    call=['javac','-encoding','UTF-8','-d',str(base/'classes'),*map(str,files),str(src/'ReaderButtonColors.java'),str(src/'UiColorScheme.java')]
    r=subprocess.run(call,capture_output=True,text=True)
    if r.returncode:raise RuntimeError(r.stdout+'\n'+r.stderr)
print('PASS real shared UI palette & button colors: Java API signature typecheck')
