package ru.reader.rsvp;

import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;

/** Same accent outline for press and confirmed active download, no layout resizing. */
final class DownloadButtonFeedback {
    private DownloadButtonFeedback() {}
    private static GradientDrawable shape(int color,int accent,boolean outlined,float radius,float stroke){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);
        if(outlined)d.setStroke(Math.max(1,Math.round(stroke)),accent);
        return d;
    }
    static void apply(View button,int surface,int accent,boolean active,int radiusDp){
        if(button==null)return;
        float density=button.getResources().getDisplayMetrics().density;
        float radius=radiusDp*density,stroke=2*density;
        int pressed=UiColorScheme.blend(surface,accent,.14f);
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},shape(pressed,accent,true,radius,stroke));
        states.addState(new int[]{android.R.attr.state_focused},shape(pressed,accent,true,radius,stroke));
        states.addState(new int[]{},shape(surface,accent,active,radius,stroke));
        button.setBackground(states);
    }
}
