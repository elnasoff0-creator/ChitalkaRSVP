package ru.reader.rsvp;

import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/** Shared four-mode toolbar layout and motion. Keeps left Back/Mode actions fixed,
 * the weighted spacer in the middle, and every other action right-aligned.
 * All four reading modes call this same implementation.
 */
final class ReaderToolbarController {
    private ReaderToolbarController(){}
    private static int dp(Resources r,int value){return Math.round(r.getDisplayMetrics().density*value);}
    static void measure(LinearLayout bar,View separator){
        if(bar==null)return;
        Resources r=bar.getResources();int count=0;
        for(int i=0;i<bar.getChildCount();i++){
            View child=bar.getChildAt(i);
            if(child!=separator&&child.getVisibility()!=View.GONE)count++;
        }
        int usable=r.getDisplayMetrics().widthPixels-dp(r,16);
        int width=Math.min(dp(r,44),Math.max(dp(r,32),
            (usable-dp(r,5)*Math.max(0,count-1))/Math.max(1,count)));
        for(int i=0;i<bar.getChildCount();i++){
            View child=bar.getChildAt(i);
            if(child==separator)continue;
            ViewGroup.LayoutParams general=child.getLayoutParams();
            if(general instanceof LinearLayout.LayoutParams){
                LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)general;
                params.height=width;params.width=width;params.leftMargin=i==0?0:dp(r,5);
                child.setLayoutParams(params);
            }
        }
    }
    static void reveal(View stacked,boolean shown,int offsetDp){
        if(stacked==null)return;
        stacked.animate().cancel();
        stacked.setTag(Boolean.valueOf(shown));
        stacked.setVisibility(View.VISIBLE);
        stacked.animate().translationY(shown?0:-dp(stacked.getResources(),offsetDp))
            .alpha(shown?1f:0f).setDuration(180)
            .withEndAction(()->{
                if(Boolean.FALSE.equals(stacked.getTag()))stacked.setVisibility(View.INVISIBLE);
            }).start();
        stacked.setClickable(shown);
    }
    static void reset(View stacked){
        if(stacked==null)return;
        stacked.animate().cancel();stacked.setTag(Boolean.TRUE);
        stacked.setVisibility(View.VISIBLE);stacked.setTranslationY(0f);stacked.setAlpha(1f);
        stacked.setClickable(true);
    }
}
