package ru.reader.rsvp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Shared browser tabs: selectable top-rounded tabs attached to the same content surface.
 * For 2-5 tabs every tab has an equal share of the *actual* screen width, never a
 * wrap-content scroll row that can put the second tab outside the viewport.
 * Tab labels remain accessible even in icon-only mode.
 */
final class BrowserTabs {
    private BrowserTabs(){}
    private static int dp(Context c,int v){return Math.round(c.getResources().getDisplayMetrics().density*v);}
    private static GradientDrawable top(Context c,int color,int radius){
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);
        float r=dp(c,radius);shape.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});return shape;
    }
    static HorizontalScrollView strip(Context context,String[] names,int active,IntConsumer clicked){
        return build(context,names,null,active,clicked);
    }
    static HorizontalScrollView iconStrip(Context context,String[] names,int[] icons,int active,IntConsumer clicked){
        if(icons==null||icons.length!=names.length)throw new IllegalArgumentException("One icon per tab is required");
        return build(context,names,icons,active,clicked);
    }
    private static HorizontalScrollView build(Context context,String[] names,int[] icons,int active,IntConsumer clicked){
        final int bg=UiColorScheme.background(context),panel=UiColorScheme.neutral(context),accent=UiColorScheme.accent(context);
        final boolean dark=UiColorScheme.dark(context),equal=names.length>=1&&names.length<=5;
        final int ink=UiColorScheme.prefs(context).getInt("fg",dark?Color.WHITE:0xff30221e);
        HorizontalScrollView scroll=new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(context,52)));
        LinearLayout row=new LinearLayout(context);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.BOTTOM);
        // MATCH_PARENT is essential for weighted equal-width tabs inside HorizontalScrollView.
        scroll.addView(row,new HorizontalScrollView.LayoutParams(
            equal?ViewGroup.LayoutParams.MATCH_PARENT:ViewGroup.LayoutParams.WRAP_CONTENT,dp(context,52)));
        for(int i=0;i<names.length;i++){
            final int index=i;final boolean chosen=i==active;
            LinearLayout tab=new LinearLayout(context);tab.setGravity(Gravity.CENTER);tab.setOrientation(LinearLayout.VERTICAL);
            tab.setPadding(dp(context,6),dp(context,5),dp(context,6),0);
            int passive=UiColorScheme.blend(bg,panel,.48f);
            tab.setBackground(top(context,chosen?panel:passive,14));
            tab.setContentDescription(names[i]);tab.setSelected(chosen);tab.setClickable(true);
            if(icons==null){
                TextView label=new TextView(context);label.setText(names[i]);label.setSingleLine(true);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                label.setTextSize(15);label.setTextColor(ink);label.setGravity(Gravity.CENTER);
                if(chosen)label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                tab.addView(label,new LinearLayout.LayoutParams(-1,0,1));
            }else{
                ImageView image=new ImageView(context);image.setImageResource(icons[i]);
                image.setColorFilter(chosen?accent:ink);
                image.setContentDescription(names[i]);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(context,25),dp(context,25));
                iconParams.gravity=Gravity.CENTER;tab.addView(image,iconParams);
                View filler=new View(context);tab.addView(filler,new LinearLayout.LayoutParams(1,0,1));
            }
            View indicator=new View(context);indicator.setBackgroundColor(chosen?accent:Color.TRANSPARENT);
            LinearLayout.LayoutParams underline=new LinearLayout.LayoutParams(-1,dp(context,3));
            underline.leftMargin=dp(context,8);underline.rightMargin=dp(context,8);tab.addView(indicator,underline);
            int tabWidth=icons!=null?dp(context,66):dp(context,92);
            LinearLayout.LayoutParams lp=equal?new LinearLayout.LayoutParams(0,dp(context,49),1f)
                :new LinearLayout.LayoutParams(tabWidth,dp(context,49));
            if(i>0)lp.leftMargin=dp(context,3);
            row.addView(tab,lp);
            tab.setOnClickListener(v->{if(index!=active)clicked.accept(index);});
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
