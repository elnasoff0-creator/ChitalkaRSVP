package ru.reader.rsvp;

import android.app.Activity;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/** Independent home cards and editing-only drag handles; migrates old three section order. */
final class HomeSectionOrder {
    static final String[] IDS={"continue","library","comic","audioContinue","audioLibrary","pet"};
    private static final String KEY="home_section_order_v123",HIDDEN="home_sections_hidden_v123";
    private HomeSectionOrder(){}
    private static int dp(Activity a,int n){return (int)(n*a.getResources().getDisplayMetrics().density+.5f);}
    private static boolean valid(String s){return Arrays.asList(IDS).contains(s);}
    static ArrayList<String> order(SharedPreferences prefs){
        ArrayList<String> result=new ArrayList<>();
        String saved=prefs.getString(KEY,null);
        if(saved==null){ // Previous three-group order migration, preserving its user's group sequence.
            String legacy=prefs.getString("home_section_order","reading,audio,pet");
            for(String group:legacy.split(",")){
                switch(group){case "reading":addUnique(result,"continue","library","comic");break;
                    case "audio":addUnique(result,"audioContinue","audioLibrary");break;
                    case "pet":addUnique(result,"pet");break;}
            }
        }else for(String id:saved.split(","))if(valid(id)&&!result.contains(id))result.add(id);
        for(String id:IDS)if(!result.contains(id))result.add(id);
        return result;
    }
    private static void addUnique(ArrayList<String> out,String... ids){for(String id:ids)if(!out.contains(id))out.add(id);}
    static void setOrder(SharedPreferences p,ArrayList<String> ids){p.edit().putString(KEY,android.text.TextUtils.join(",",ids)).apply();}
    static HashSet<String> hidden(SharedPreferences p){return new HashSet<>(p.getStringSet(HIDDEN,new HashSet<>()));}
    static boolean visible(SharedPreferences p,String id){return !hidden(p).contains(id);}
    static void visible(SharedPreferences p,String id,boolean value){HashSet<String> items=hidden(p);if(value)items.remove(id);else items.add(id);p.edit().putStringSet(HIDDEN,items).apply();}
    static void reset(SharedPreferences p){p.edit().remove(KEY).remove(HIDDEN).remove("home_section_order").apply();}
    static String title(String id){switch(id){case "continue":return "Продолжить чтение";case "library":return "Библиотека";
        case "comic":return "Комиксотека";case "audioContinue":return "Продолжить аудиокнигу";
        case "audioLibrary":return "Аудиотека";case "pet":return "Мини-Чтейцо";default:return id;}}

    static void apply(MainActivity activity, LinearLayout parent, int[] bounds, SharedPreferences prefs, boolean editing){
        if(bounds.length!=7)return;
        ArrayList<View> all=new ArrayList<>();for(int i=0;i<parent.getChildCount();i++)all.add(parent.getChildAt(i));
        if(bounds[0]<0||bounds[6]>all.size())return;
        parent.removeAllViews();
        for(int i=0;i<bounds[0];i++)parent.addView(all.get(i));
        HashMap<String,LinearLayout> groups=new HashMap<>();
        for(int i=0;i<IDS.length;i++){
            if(bounds[i]>bounds[i+1])return;
            final String id=IDS[i];
            LinearLayout group=new LinearLayout(activity);group.setOrientation(LinearLayout.VERTICAL);group.setTag("home:"+id);
            for(int j=bounds[i];j<bounds[i+1];j++)group.addView(all.get(j));
            if(group.getChildCount()==0)continue;
            if(editing){
                LinearLayout actions=new LinearLayout(activity);actions.setGravity(Gravity.CENTER_VERTICAL);
                actions.setPadding(dp(activity,8),dp(activity,4),dp(activity,8),dp(activity,4));
                GradientDrawable bg=new GradientDrawable();bg.setColor(0xffe7daf3);bg.setCornerRadius(dp(activity,12));actions.setBackground(bg);
                TextView grip=new TextView(activity);grip.setText("⠿");grip.setTextSize(25);grip.setGravity(Gravity.CENTER);grip.setTextColor(0xff30221e);
                actions.addView(grip,new LinearLayout.LayoutParams(dp(activity,42),dp(activity,43)));
                TextView label=new TextView(activity);label.setText(title(id));label.setTextColor(0xff30221e);label.setTextSize(15);label.setTypeface(null,1);
                actions.addView(label,new LinearLayout.LayoutParams(0,-2,1));
                TextView hide=new TextView(activity);hide.setText("✕");hide.setTextColor(0xff30221e);hide.setTextSize(20);hide.setGravity(Gravity.CENTER);
                actions.addView(hide,new LinearLayout.LayoutParams(dp(activity,42),dp(activity,43)));
                hide.setOnClickListener(v->{visible(prefs,id,false);activity.draw();});
                grip.setOnTouchListener((v,e)->{
                    if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){
                        v.startDragAndDrop(ClipData.newPlainText("home",id),new View.DragShadowBuilder(group),id,0);return true;
                    }return true;
                });
                group.addView(actions,0);
                group.setOnDragListener((v,event)->{
                    if(event.getAction()==DragEvent.ACTION_DRAG_STARTED)return event.getLocalState() instanceof String;
                    if(event.getAction()==DragEvent.ACTION_DROP){
                        if(!(event.getLocalState() instanceof String))return false;
                        String from=(String)event.getLocalState();ArrayList<String> order=order(prefs);
                        if(!from.equals(id)&&order.contains(from)){
                            order.remove(from);order.add(order.indexOf(id),from);setOrder(prefs,order);
                            parent.post(activity::draw);
                        }return true;
                    }return true;
                });
            }else{
                group.setOnLongClickListener(v->{activity.enterHomeEditing();return true;});
                if(group.getChildCount()>0){View first=group.getChildAt(0);
                    first.setOnLongClickListener(v->{activity.enterHomeEditing();return true;});
                }
            }
            groups.put(id,group);
        }
        for(String id:order(prefs)){
            if(!visible(prefs,id))continue;
            LinearLayout group=groups.get(id);if(group==null)continue;
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(activity,12);
            parent.addView(group,lp);
        }
    }
    /** Legacy entrypoint remains harmless; long press on the home pet now opens editing. */
    static void startPetDrag(View pet){
        android.content.Context context=pet.getContext();
        if(context instanceof MainActivity)((MainActivity)context).enterHomeEditing();
    }
}
