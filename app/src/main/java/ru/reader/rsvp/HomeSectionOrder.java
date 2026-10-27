package ru.reader.rsvp;

import android.app.Activity;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/** Persistently reorder home sections, rather than fragile child positions. */
final class HomeSectionOrder {
    private static final String KEY="home_section_order";
    private static final String[] DEFAULT={"reading","audio","pet"};
    private HomeSectionOrder(){}
    private static int dp(Activity a,int n){return (int)(n*a.getResources().getDisplayMetrics().density+.5f);}
    private static ArrayList<String> order(SharedPreferences prefs){
        ArrayList<String> result=new ArrayList<>();
        String saved=prefs.getString(KEY,"reading,audio,pet");
        for(String id:saved.split(","))if(Arrays.asList(DEFAULT).contains(id)&&!result.contains(id))result.add(id);
        for(String id:DEFAULT)if(!result.contains(id))result.add(id);
        return result;
    }
    static void startPetDrag(View pet){
        View target=pet;
        while(target!=null){
            Object tag=target.getTag();
            if(tag instanceof String&&((String)tag).startsWith("home:")){
                String id=((String)tag).substring(5);
                pet.startDragAndDrop(ClipData.newPlainText("home-section",id),
                        new View.DragShadowBuilder(pet),id,0);
                return;
            }
            if(!(target.getParent() instanceof View))break;
            target=(View)target.getParent();
        }
    }
    static void apply(Activity activity,LinearLayout parent,int reading,int audio,int pet,SharedPreferences prefs){
        ArrayList<View> old=new ArrayList<>();
        for(int i=0;i<parent.getChildCount();i++)old.add(parent.getChildAt(i));
        if(reading<0||audio<reading||pet<audio||pet>old.size())return;
        parent.removeAllViews();
        for(int i=0;i<reading;i++)parent.addView(old.get(i));
        HashMap<String,LinearLayout> sections=new HashMap<>();
        String[] ids=DEFAULT;
        int[] boundaries={reading,audio,pet,old.size()};
        for(int i=0;i<3;i++){
            LinearLayout group=new LinearLayout(activity);group.setOrientation(LinearLayout.VERTICAL);group.setTag("home:"+ids[i]);
            for(int j=boundaries[i];j<boundaries[i+1];j++){
                View child=old.get(j);group.addView(child);
            }
            if(group.getChildCount()==0)continue;
            final String draggedId=ids[i];
            View.OnLongClickListener drag=v->{
                ClipData clip=ClipData.newPlainText("home-section",draggedId);
                v.startDragAndDrop(clip,new View.DragShadowBuilder(v),draggedId,0);
                return true;
            };
            group.setOnLongClickListener(drag);
            // A long press on a continuation card also starts a move. Child buttons remain clickable.
            for(int c=0;c<group.getChildCount();c++){
                View item=group.getChildAt(c);
                if(!"pet".equals(draggedId)&&item.hasOnClickListeners())item.setOnLongClickListener(drag);
            }
            group.setOnDragListener((v,event)->{
                if(event.getAction()==DragEvent.ACTION_DRAG_STARTED)
                    return event.getLocalState() instanceof String;
                if(event.getAction()==DragEvent.ACTION_DROP){
                    if(!(event.getLocalState() instanceof String))return false;
                    String source=(String)event.getLocalState();
                    ArrayList<String> current=order(prefs);
                    if(!source.equals(draggedId)){
                        current.remove(source);
                        current.add(current.indexOf(draggedId),source);
                        prefs.edit().putString(KEY,android.text.TextUtils.join(",",current)).apply();
                        parent.post(()->arrange(activity,parent,sections,prefs));
                    }
                    return true;
                }
                return true;
            });
            sections.put(draggedId,group);
        }
        arrange(activity,parent,sections,prefs);
    }
    private static void arrange(Activity activity,LinearLayout parent,HashMap<String,LinearLayout> groups,SharedPreferences prefs){
        // Only regroup the three managed views. Leave the leading screen spacing intact.
        for(LinearLayout group:groups.values()){
            if(group.getParent() instanceof LinearLayout)((LinearLayout)group.getParent()).removeView(group);
        }
        // Strip separator views added by previous arrangement.
        for(int i=parent.getChildCount()-1;i>=0;i--)
            if("home_separator".equals(parent.getChildAt(i).getTag()))parent.removeViewAt(i);
        boolean first=true;
        for(String id:order(prefs)){
            LinearLayout section=groups.get(id);
            if(section==null)continue;
            if(!first){
                View separator=new View(activity);
                int fg=prefs.getInt("fg",0xff30221e);
                separator.setBackgroundColor((fg&0x00ffffff)|0x33000000);
                separator.setTag("home_separator");
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(activity,1));
                lp.topMargin=dp(activity,14);lp.bottomMargin=dp(activity,13);
                parent.addView(separator,lp);
            }
            first=false;
            parent.addView(section,new LinearLayout.LayoutParams(-1,-2));
        }
    }
}
