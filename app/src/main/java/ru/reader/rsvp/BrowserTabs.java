package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Connected, top-rounded browser tabs. Reuse for every existing and future tabbed screen. */
final class BrowserTabs {
    private BrowserTabs(){}
    private static int dp(Context c,int v){return Math.round(c.getResources().getDisplayMetrics().density*v);}
    private static GradientDrawable top(Context c,int color,int radius){
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);
        float r=dp(c,radius);shape.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});return shape;
    }
    static HorizontalScrollView strip(Context context,String[] names,int active,IntConsumer clicked){
        int bg=UiColorScheme.background(context),panel=UiColorScheme.neutral(context),accent=UiColorScheme.accent(context);
        boolean dark=UiColorScheme.dark(context);
        int ink=UiColorScheme.prefs(context).getInt("fg",dark?Color.WHITE:0xff30221e);
        HorizontalScrollView scroll=new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);scroll.setFillViewport(true);
        LinearLayout row=new LinearLayout(context);row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);scroll.addView(row,new HorizontalScrollView.LayoutParams(-2,dp(context,53)));
        for(int i=0;i<names.length;i++){
            final int index=i;boolean chosen=index==active;
            LinearLayout tab=new LinearLayout(context);tab.setGravity(Gravity.CENTER);tab.setOrientation(LinearLayout.VERTICAL);
            tab.setPadding(dp(context,13),dp(context,8),dp(context,13),0);
            int passive=UiColorScheme.blend(bg,panel,.52f);
            tab.setBackground(top(context,chosen?panel:passive,14));
            TextView label=new TextView(context);label.setText(names[i]);label.setSingleLine(true);
            label.setTextSize(14);label.setTextColor(ink);
            if(chosen)label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            label.setGravity(Gravity.CENTER);tab.addView(label,new LinearLayout.LayoutParams(-1,0,1));
            View indicator=new View(context);indicator.setBackgroundColor(chosen?accent:Color.TRANSPARENT);
            LinearLayout.LayoutParams underline=new LinearLayout.LayoutParams(-1,dp(context,3));
            underline.leftMargin=dp(context,3);underline.rightMargin=dp(context,3);tab.addView(indicator,underline);
            tab.setMinimumWidth(dp(context,83));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(context,49));
            if(i>0)lp.leftMargin=dp(context,3);row.addView(tab,lp);
            tab.setContentDescription(names[i]);tab.setSelected(chosen);tab.setOnClickListener(v->clicked.accept(index));
        }
        return scroll;
    }
    static LinearLayout panel(Context context){
        LinearLayout pane=new LinearLayout(context);pane.setOrientation(LinearLayout.VERTICAL);
        pane.setPadding(dp(context,9),dp(context,10),dp(context,9),dp(context,10));
        GradientDrawable shape=new GradientDrawable();shape.setColor(UiColorScheme.neutral(context));
        float r=dp(context,16);shape.setCornerRadii(new float[]{0,0,r,r,r,r,r,r});pane.setBackground(shape);
        return pane;
    }
}
