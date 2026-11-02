package ru.reader.rsvp;

import android.app.AlertDialog;
import android.content.ClipData;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

/** Rename, hide, add and reorder shared drawer routes. No route may be deleted. */
public final class DrawerEditorActivity extends NavigationActivity {
    private int bg,fg;
    int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    GradientDrawable panel(){GradientDrawable d=new GradientDrawable();d.setColor(ReadingThemeCycle.isDark(bg)?0xff34363d:0xfffff9f2);d.setCornerRadius(dp(14));return d;}
    TextView text(String title,int size){TextView t=new TextView(this);t.setText(title);t.setTextSize(size);t.setTextColor(fg);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    @Override public void onCreate(Bundle state){super.onCreate(state);bg=getSharedPreferences("reader",0).getInt("bg",0xfff3e7d5);fg=getSharedPreferences("reader",0).getInt("fg",0xff30221e);render();}
    private void render(){
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setBackgroundColor(bg);layout.setPadding(dp(15),dp(18),dp(15),dp(10));setContentView(layout);SafeSystemBars.matchBackground(this,bg);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);layout.addView(head);
        TextView back=text("‹",30);back.setGravity(Gravity.CENTER);head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));back.setOnClickListener(v->finish());
        TextView title=text("Редактировать меню",20);head.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView done=text("Готово",16);head.addView(done);done.setOnClickListener(v->finish());
        ScrollView scroll=new ScrollView(this);layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout entries=new LinearLayout(this);entries.setOrientation(1);scroll.addView(entries);
        for(String id:DrawerMenuStore.order(this)){
            if(!DrawerMenuStore.visible(this,id))continue;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setBackground(panel());row.setPadding(dp(7),dp(8),dp(7),dp(8));
            LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,dp(63));rowLp.setMargins(0,dp(4),0,dp(4));entries.addView(row,rowLp);
            TextView handle=text("⠿",24);handle.setGravity(Gravity.CENTER);row.addView(handle,new LinearLayout.LayoutParams(dp(38),-1));
            handle.setOnTouchListener((v,e)->{if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){
                ClipData clip=ClipData.newPlainText("drawer",id);v.startDragAndDrop(clip,new View.DragShadowBuilder(row),id,0);return true;}return true;});
            ImageView icon=new ImageView(this);icon.setImageResource(DrawerMenuStore.icon(id,this));icon.setColorFilter(fg);row.addView(icon,new LinearLayout.LayoutParams(dp(22),dp(22)));
            TextView name=text("  "+DrawerMenuStore.name(this,id),16);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name,new LinearLayout.LayoutParams(0,-1,1));
            TextView edit=text("✎",21);edit.setGravity(Gravity.CENTER);row.addView(edit,new LinearLayout.LayoutParams(dp(41),-1));edit.setOnClickListener(v->rename(id));
            TextView hide=text(DrawerMenuStore.isProtected(id)?"◆":"×",22);hide.setGravity(Gravity.CENTER);
            row.addView(hide,new LinearLayout.LayoutParams(dp(38),-1));
            if(!DrawerMenuStore.isProtected(id))hide.setOnClickListener(v->{DrawerMenuStore.visible(this,id,false);render();});
            row.setOnDragListener((v,e)->{
                if(e.getAction()==DragEvent.ACTION_DRAG_STARTED)return e.getLocalState() instanceof String;
                if(e.getAction()==DragEvent.ACTION_DROP){if(!(e.getLocalState() instanceof String))return false;
                    String from=(String)e.getLocalState();ArrayList<String> order=DrawerMenuStore.order(this);
                    if(!from.equals(id)){order.remove(from);order.add(order.indexOf(id),from);DrawerMenuStore.saveOrder(this,order);render();}return true;}
                return true;
            });
        }
        TextView add=text("＋  Добавить пункт",17);add.setPadding(dp(18),dp(14),0,dp(14));entries.addView(add);add.setOnClickListener(v->showHidden());
        TextView reset=text("Восстановить стандартное меню",15);reset.setPadding(dp(18),dp(14),0,dp(14));entries.addView(reset);
        reset.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Восстановить стандартное меню?")
            .setPositiveButton("Восстановить",(d,w)->{DrawerMenuStore.reset(this);render();}).setNegativeButton("Отмена",null).show());
    }
    private void rename(String id){EditText input=new EditText(this);input.setSingleLine(true);input.setText(DrawerMenuStore.name(this,id));input.setPadding(dp(18),dp(14),dp(18),dp(14));
        new AlertDialog.Builder(this).setTitle("Переименовать пункт").setView(input).setNegativeButton("Отмена",null)
            .setPositiveButton("Сохранить",(d,w)->{DrawerMenuStore.rename(this,id,input.getText().toString());render();}).show();}
    private void showHidden(){ArrayList<String> hidden=new ArrayList<>();for(String id:DrawerMenuStore.order(this))if(!DrawerMenuStore.visible(this,id))hidden.add(id);
        if(hidden.isEmpty()){new AlertDialog.Builder(this).setMessage("Все доступные пункты уже добавлены.").setPositiveButton("Хорошо",null).show();return;}
        String[] names=new String[hidden.size()];for(int i=0;i<names.length;i++)names[i]=DrawerMenuStore.name(this,hidden.get(i));
        new AlertDialog.Builder(this).setTitle("Добавить пункт").setItems(names,(d,idx)->{DrawerMenuStore.visible(this,hidden.get(idx),true);render();}).setNegativeButton("Отмена",null).show();}
}
