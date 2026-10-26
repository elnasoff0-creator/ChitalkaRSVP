package ru.reader.rsvp;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ListView;

/** Unified animated, theme-aware top/bottom sheets for standard app choices. */
public final class SwipeDialogs {
    private SwipeDialogs(){}
    public static final class Builder extends AlertDialog.Builder {
        private final Context context;
        private boolean top;
        private View swipeHandle;
        private SheetDragLayout swipePanel;
        @Override public Builder setView(View view){
            if(view==null){super.setView((View)null);return this;}
            float density=context.getResources().getDisplayMetrics().density;
            SheetDragLayout panel=new SheetDragLayout(context);
            swipePanel=panel;
            panel.setPadding(0,(int)(9*density),0,0);
            View handle=new View(context);
            GradientDrawable pill=new GradientDrawable();pill.setColor(0xffa8a29c);
            pill.setCornerRadius(5*density);handle.setBackground(pill);
            LinearLayout.LayoutParams handleParams=new LinearLayout.LayoutParams((int)(46*density),(int)(5*density));
            handleParams.gravity=Gravity.CENTER_HORIZONTAL;handleParams.bottomMargin=(int)(10*density);
            panel.addView(handle,handleParams);
            panel.addView(view,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            swipeHandle=handle;super.setView(panel);return this;
        }
        public Builder(Context context){super(context);this.context=context;}
        public Builder(Context context,int theme){super(context,theme);this.context=context;}
        /** Use for controls attached to the upper toolbar. Other sheets slide up from below. */
        public Builder fromTop(){top=true;return this;}
        @Override public AlertDialog create(){
            AlertDialog dialog=super.create();
            final boolean openFromTop=top;
            // Configure the gravity and transition before the first rendered frame.
            // Changing them in OnShow caused the window to jump between center and edge.
            configure(dialog,context,openFromTop);
            if(swipePanel!=null)swipePanel.attach(dialog,openFromTop);
            if(swipeHandle!=null)swipeHandle.setOnTouchListener(new SwipeDismiss(dialog,openFromTop,false,true));
            dialog.setOnShowListener(ignored->{
                Window w=dialog.getWindow();
                if(w!=null)w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
                installGestures(dialog,openFromTop);
            });
            return dialog;
        }
        @Override public AlertDialog show(){
            AlertDialog dialog=create();
            dialog.show();
            return dialog;
        }
    }
    private static void configure(AlertDialog dialog,Context context,boolean top){
        Window window=dialog.getWindow();if(window==null)return;
        float density=context.getResources().getDisplayMetrics().density;
        int bg=context.getSharedPreferences("reader",0).getInt("bg",0xfff3e7d5);
        GradientDrawable shape=new GradientDrawable();shape.setColor(bg);shape.setCornerRadius(22f*density);
        window.setBackgroundDrawable(shape);
        window.setGravity(top?Gravity.TOP:Gravity.BOTTOM);
        window.setWindowAnimations(top?R.style.ChitalkaSheetTop:R.style.ChitalkaSheetBottom);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params=window.getAttributes();params.dimAmount=.35f;window.setAttributes(params);
        // Size is applied after show without changing gravity or animations.
    }
    private static void installGestures(AlertDialog dialog,boolean top){
        Window window=dialog.getWindow();if(window==null)return;
        int id=window.getContext().getResources().getIdentifier("alertTitle","id","android");
        View title=id==0?null:window.getDecorView().findViewById(id);
        if(title!=null)title.setOnTouchListener(new SwipeDismiss(dialog,top,false,true));
        View message=window.getDecorView().findViewById(android.R.id.message);
        if(message!=null)message.setOnTouchListener(new SwipeDismiss(dialog,top,false,true));
        ListView list=dialog.getListView();
        if(list!=null)list.setOnTouchListener(new SwipeDismiss(dialog,top,true,false));
    }
    private static final class SwipeDismiss implements View.OnTouchListener {
        private final AlertDialog dialog;private final boolean top,scrollable,handle;
        private float downX,downY;private boolean dismissed;
        SwipeDismiss(AlertDialog dialog,boolean top,boolean scrollable,boolean handle){
            this.dialog=dialog;this.top=top;this.scrollable=scrollable;this.handle=handle;
        }
        @Override public boolean onTouch(View view,MotionEvent event){
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downX=event.getRawX();downY=event.getRawY();dismissed=false;
                    // A non-clickable handle must consume DOWN to receive MOVE/UP.
                    return handle;
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    float dy=event.getRawY()-downY;
                    boolean atBoundary=!scrollable||(top?!view.canScrollVertically(1):!view.canScrollVertically(-1));
                    if(!dismissed&&atBoundary&&(top?-dy:dy)>view.getResources().getDisplayMetrics().density*52
                        && Math.abs(dy)>Math.abs(event.getRawX()-downX)*1.2f){
                        dismissed=true;dialog.dismiss();return true;
                    }
                    return handle;
                case MotionEvent.ACTION_CANCEL:return handle;
                default:return false;
            }
        }
    }
}
