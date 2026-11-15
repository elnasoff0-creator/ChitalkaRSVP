package ru.reader.rsvp;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Stable shared geometry for the drawer/title area used by primary app pages. */
final class AppToolbar {
    private AppToolbar(){}
    static final int HEIGHT_DP=52;
    private static int dp(Activity a,int n){return Math.round(a.getResources().getDisplayMetrics().density*n);}
    static TextView add(Activity a,LinearLayout parent,String title){
        final int fg=UiColorScheme.prefs(a).getInt("fg",UiColorScheme.dark(a)?Color.WHITE:0xff30221e);
        LinearLayout bar=new LinearLayout(a);bar.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(bar,new LinearLayout.LayoutParams(-1,dp(a,HEIGHT_DP)));
        ImageView drawer=new ImageView(a);drawer.setImageResource(R.drawable.ic_menu_lines);
        drawer.setColorFilter(fg);drawer.setPadding(dp(a,10),dp(a,10),dp(a,10),dp(a,10));
        GradientDrawable surface=new GradientDrawable();surface.setColor(UiColorScheme.neutral(a));surface.setCornerRadius(dp(a,13));
        drawer.setBackground(surface);drawer.setContentDescription("Боковое меню");
        bar.addView(drawer,new LinearLayout.LayoutParams(dp(a,44),dp(a,44)));
        drawer.setOnClickListener(v->UniversalDrawer.show(a));
        TextView heading=new TextView(a);heading.setText(title);heading.setTextSize(23);
        heading.setTextColor(fg);heading.setTypeface(Typeface.SERIF,Typeface.BOLD);
        heading.setSingleLine(true);heading.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(0,-2,1);labelParams.leftMargin=dp(a,12);
        bar.addView(heading,labelParams);return heading;
    }
}
