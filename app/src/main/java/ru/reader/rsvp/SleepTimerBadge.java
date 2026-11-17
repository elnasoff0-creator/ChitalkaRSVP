package ru.reader.rsvp;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Keeps the sleep-timer icon truly centered when no minute/chapter counter is shown. */
final class SleepTimerBadge {
    private SleepTimerBadge() {}
    static void layout(FrameLayout badge, ImageView icon, TextView counter, boolean hasCounter, int iconDp){
        if(badge==null||icon==null||counter==null)return;
        int size=Math.round(badge.getResources().getDisplayMetrics().density*iconDp);
        FrameLayout.LayoutParams iconLp=new FrameLayout.LayoutParams(size,size,
            hasCounter?Gravity.TOP|Gravity.CENTER_HORIZONTAL:Gravity.CENTER);
        if(hasCounter)iconLp.topMargin=Math.round(badge.getResources().getDisplayMetrics().density*3);
        FrameLayout.LayoutParams old=(FrameLayout.LayoutParams)icon.getLayoutParams();
        if(old==null||old.width!=size||old.height!=size||old.gravity!=iconLp.gravity
                ||old.topMargin!=iconLp.topMargin)icon.setLayoutParams(iconLp);
        int visibility=hasCounter?View.VISIBLE:View.GONE;
        if(counter.getVisibility()!=visibility)counter.setVisibility(visibility);
    }
}
