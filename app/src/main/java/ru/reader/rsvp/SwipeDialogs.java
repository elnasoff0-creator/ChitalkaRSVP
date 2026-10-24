package ru.reader.rsvp;
import android.app.AlertDialog;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
/** Swipe-down dismissal without adding persistent buttons or modifying dialog choices. */
public final class SwipeDialogs {
    private SwipeDialogs(){}
    public static final class Builder extends AlertDialog.Builder {
        public Builder(Context c){super(c);}
        public Builder(Context c,int theme){super(c,theme);}
        @Override public AlertDialog create(){
            AlertDialog dialog=super.create();
            dialog.setOnShowListener(ignored->enable(dialog));
            return dialog;
        }
    }
    private static void enable(AlertDialog dialog){
        if(dialog.getWindow()==null)return;
        View decor=dialog.getWindow().getDecorView();
        int id=dialog.getContext().getResources().getIdentifier("alertTitle","id","android");
        View title=id==0?null:decor.findViewById(id);
        if(title!=null)title.setOnTouchListener(new SwipeDown(dialog,false));
        View content=decor.findViewById(android.R.id.content);
        if(content!=null)content.setOnTouchListener(new SwipeDown(dialog,false));
        ListView list=dialog.getListView();
        if(list!=null)list.setOnTouchListener(new SwipeDown(dialog,true));
    }
    private static final class SwipeDown implements View.OnTouchListener {
        private final AlertDialog dialog;private final boolean list;
        private float downX,downY;private boolean armed;
        SwipeDown(AlertDialog d,boolean list){dialog=d;this.list=list;}
        @Override public boolean onTouch(View view,MotionEvent event){
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downX=event.getX();downY=event.getY();armed=!list||!view.canScrollVertically(-1);
                    return !list;
                case MotionEvent.ACTION_MOVE:
                    if(armed&&event.getY()-downY>view.getResources().getDisplayMetrics().density*90
                         &&Math.abs(event.getY()-downY)>Math.abs(event.getX()-downX)*1.4f){
                        dialog.dismiss();armed=false;return true;
                    }
                    return !list;
                default:return !list;
            }
        }
    }
}
