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
        icon.setLayoutParams(iconLp);
        counter.setVisibility(hasCounter?View.VISIBLE:View.GONE);
    }
}
