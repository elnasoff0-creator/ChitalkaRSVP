package ru.reader.rsvp;

import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.ProgressBar;

/** Only shows a spinner while an actual pending operation is in progress. */
final class LoadingPlayIcon {
    private LoadingPlayIcon() {}
    static void show(ImageView target, boolean waiting, int normalIcon) {
        if (target == null) return;
        Boolean old=(Boolean)target.getTag(R.id.loading_icon_state);
        if(old!=null&&old.booleanValue()==waiting){
            if(!waiting){
                Integer oldIcon=(Integer)target.getTag(R.id.loading_icon_resource);
                if(oldIcon!=null&&oldIcon==normalIcon)return;
            }else return;
        }
        Drawable oldDrawable=target.getDrawable();
        if(oldDrawable instanceof Animatable)((Animatable)oldDrawable).stop();
        target.setTag(R.id.loading_icon_state,waiting);
        target.setTag(R.id.loading_icon_resource,normalIcon);
        if(waiting){
            ProgressBar standard=new ProgressBar(target.getContext());
            Drawable spinner=standard.getIndeterminateDrawable();
            if(spinner!=null){
                spinner=spinner.getConstantState()!=null?spinner.getConstantState().newDrawable().mutate():spinner.mutate();
                target.setImageDrawable(spinner);
                if(spinner instanceof Animatable)((Animatable)spinner).start();
            }else target.setImageResource(normalIcon);
            target.setContentDescription("Ожидание загрузки");
        }else target.setImageResource(normalIcon);
    }
}
