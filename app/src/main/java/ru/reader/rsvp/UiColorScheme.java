package ru.reader.rsvp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.AbsListView;

/** One configurable palette for selected rows, primary actions, tabs and dialog controls. */
public final class UiColorScheme {
    public static final String KEY_MODE="ui_color_scheme", KEY_CUSTOM="ui_custom_accent";
    private UiColorScheme(){}
    public static SharedPreferences prefs(Context c){return c.getSharedPreferences("reader",Context.MODE_PRIVATE);}
    public static int background(Context c){return prefs(c).getInt("bg",0xfff3e7d5);}
    public static boolean dark(Context c){int b=background(c);return (Color.red(b)*299+Color.green(b)*587+Color.blue(b)*114)<145000;}
    public static int accent(Context c){
        SharedPreferences p=prefs(c);String mode=p.getString(KEY_MODE,"theme");
        int value;
        if("warm".equals(mode))value=0xffa26949;
        else if("green".equals(mode))value=0xff39845b;
        else if("violet".equals(mode))value=0xff7551a6;
        else if("custom".equals(mode))value=p.getInt(KEY_CUSTOM,0xffa26949);
        else {value=p.getInt("button_accent_color",0);if(value==0)value=0xffa26949;}
        // Keep the chosen hue but ensure the check mark remains visible against the page.
        return Color.rgb(Color.red(value),Color.green(value),Color.blue(value));
    }
    public static int blend(int base,int ink,float alpha){
        alpha=Math.max(0,Math.min(1,alpha));
        return Color.rgb(Math.round(Color.red(base)*(1-alpha)+Color.red(ink)*alpha),
            Math.round(Color.green(base)*(1-alpha)+Color.green(ink)*alpha),
            Math.round(Color.blue(base)*(1-alpha)+Color.blue(ink)*alpha));
    }
    public static int selected(Context c){return blend(background(c),accent(c),dark(c)?.32f:.18f);}
    public static int neutral(Context c){
        SharedPreferences p=prefs(c);int b=background(c);int saved=p.getInt("button_color",0);
        if(saved!=0 && saved!=b)return saved;
        int panel=p.getInt("panel_color",0);if(panel!=0 && panel!=b)return panel;
        return dark(c)?blend(b,Color.WHITE,.085f):
            ((Color.red(b)+Color.green(b)+Color.blue(b))>735?blend(b,Color.BLACK,.055f):blend(b,Color.WHITE,.65f));
    }
    /** Outline-only selected state: keep the themed surface, no solid accent fill. */
    public static GradientDrawable selectionOutline(Context c,boolean isSelected,int normalColor,int radiusDp){
        float density=c.getResources().getDisplayMetrics().density;
        GradientDrawable g=new GradientDrawable();
        g.setColor(normalColor);
        g.setCornerRadius(radiusDp*density);
        if(isSelected)g.setStroke(Math.max(1,(int)(2*density+.5f)),accent(c));
        return g;
    }
    public static int onAccent(Context c){return ReaderButtonColors.contrastingText(accent(c));}
    public static String displayName(Context c){
        String m=prefs(c).getString(KEY_MODE,"theme");
        if("warm".equals(m))return "Тёплая";
        if("green".equals(m))return "Зелёная";
        if("violet".equals(m))return "Сиреневая";
        if("custom".equals(m))return "Свой цвет";
        return "Из текущей темы";
    }
    public static void tintControls(View v,Context c){
        if(v instanceof CompoundButton){
            ((CompoundButton)v).setButtonTintList(ColorStateList.valueOf(accent(c)));
        }
        if(v instanceof android.widget.Switch){
            ((android.widget.Switch)v).setThumbTintList(ColorStateList.valueOf(accent(c)));
            ((android.widget.Switch)v).setTrackTintList(ColorStateList.valueOf(selected(c)));
        }
        if(v instanceof android.widget.SeekBar){
            ((android.widget.SeekBar)v).setProgressTintList(ColorStateList.valueOf(accent(c)));
            ((android.widget.SeekBar)v).setThumbTintList(ColorStateList.valueOf(accent(c)));
        }
        if(v instanceof android.widget.EditText){
            ((android.widget.EditText)v).setBackgroundTintList(ColorStateList.valueOf(accent(c)));
        }
        if(v instanceof CheckedTextView){
            ((CheckedTextView)v).setCheckMarkTintList(ColorStateList.valueOf(accent(c)));
        }
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;
            for(int i=0;i<group.getChildCount();i++)tintControls(group.getChildAt(i),c);
        }
    }
    /** Applied to native AlertDialog multi-choice rows too, including recycled ListView items. */
    public static void tintDialog(AlertDialog d,Context c){
        if(d.getButton(AlertDialog.BUTTON_POSITIVE)!=null)
            d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent(c));
        if(d.getButton(AlertDialog.BUTTON_NEGATIVE)!=null)
            d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(accent(c));
        if(d.getButton(AlertDialog.BUTTON_NEUTRAL)!=null)
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(accent(c));
        View decor=d.getWindow()==null?null:d.getWindow().getDecorView();
        if(decor!=null)tintControls(decor,c);
        ListView list=d.getListView();
        if(list!=null){
            Runnable update=()->{for(int i=0;i<list.getChildCount();i++)tintControls(list.getChildAt(i),c);};
            list.setOnScrollListener(new AbsListView.OnScrollListener(){
                @Override public void onScrollStateChanged(AbsListView v,int state){}
                @Override public void onScroll(AbsListView v,int first,int visible,int total){v.post(update);}
            });
            list.post(update);
        }
    }
}
