package ru.reader.rsvp;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Window;

/** Rounded theme-aware native dialogs; keeps Android selection, IME and accessibility. */
public final class RoundedDialogs {
    private RoundedDialogs(){}
    public static final class Builder extends AlertDialog.Builder {
        private final Context context;
        public Builder(Context c){super(c);context=c;}
        public Builder(Context c,int theme){super(c,theme);context=c;}
        @Override public AlertDialog create(){
            AlertDialog dialog=super.create();Window w=dialog.getWindow();
            if(w!=null){
                GradientDrawable background=new GradientDrawable();
                background.setColor(UiColorScheme.neutral(context));
                background.setCornerRadius(22f*context.getResources().getDisplayMetrics().density);
                w.setBackgroundDrawable(background);
            }
            dialog.setOnShowListener(d->UiColorScheme.tintDialog(dialog,context));
            return dialog;
        }
        @Override public AlertDialog show(){AlertDialog dialog=create();dialog.show();return dialog;}
    }
}
