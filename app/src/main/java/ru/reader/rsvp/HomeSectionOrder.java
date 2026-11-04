package ru.reader.rsvp;

import android.app.Activity;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.DragEvent;
import android.animation.LayoutTransition;
import android.view.Gravity;
import android.widget.ScrollView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/** Independent home cards and editing-only drag handles; migrates old three section order. */
final class HomeSectionOrder {
    static final String[] IDS={"continue","library","comicContinue","comic","audioContinue","audioLibrary","pet"};
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
        // Insert the new resume card immediately before its library without resetting prior order.
        if(!result.contains("comicContinue")){
            int location=result.indexOf("comic");
            result.add(location>=0?location:result.size(),"comicContinue");
        }
        for(String id:IDS)if(!result.contains(id))result.add(id);
        return result;
    }
    private static void addUnique(ArrayList<String> out,String... ids){for(String id:ids)if(!out.contains(id))out.add(id);}
    static void setOrder(SharedPreferences p,ArrayList<String> ids){p.edit().putString(KEY,android.text.TextUtils.join(",",ids)).apply();}
    static HashSet<String> hidden(SharedPreferences p){return new HashSet<>(p.getStringSet(HIDDEN,new HashSet<>()));}
    static boolean visible(SharedPreferences p,String id){return !hidden(p).contains(id);}
    static void visible(SharedPreferences p,String id,boolean value){HashSet<String> items=hidden(p);if(value)items.remove(id);else items.add(id);p.edit().putStringSet(HIDDEN,items).apply();}
    static void reset(SharedPreferences p){SharedPreferences.Editor e=p.edit().remove(KEY).remove(HIDDEN).remove("home_section_order");
        for(String id:IDS)e.remove("home_section_title:"+id);
        e.apply();}
    static String title(String id){switch(id){case "continue":return "Продолжить чтение";case "library":return "Библиотека";
        case "comicContinue":return "Продолжить комикс";case "comic":return "Комиксотека";case "audioContinue":return "Продолжить аудиокнигу";
        case "audioLibrary":return "Аудиотека";case "pet":return "Мини-Чтейцо";default:return id;}}

    static String title(SharedPreferences p,String id){
        String name=p.getString("home_section_title:"+id,"").trim();return name.isEmpty()?title(id):name;
    }
    static void rename(MainActivity a,SharedPreferences prefs,String id){
        android.widget.EditText input=new android.widget.EditText(a);
        input.setSingleLine(true);input.setText(title(prefs,id));input.selectAll();
        input.setPadding(dp(a,18),dp(a,12),dp(a,18),dp(a,12));
        new SwipeDialogs.Builder(a).setTitle("Название блока").setView(input)
            .setNegativeButton("Отмена",null)
            .setNeutralButton("По умолчанию",(d,w)->{prefs.edit().remove("home_section_title:"+id).apply();a.draw();})
            .setPositiveButton("Сохранить",(d,w)->{
                String next=input.getText().toString().trim();
                if(next.isEmpty()){android.widget.Toast.makeText(a,"Название не может быть пустым",android.widget.Toast.LENGTH_SHORT).show();return;}
                prefs.edit().putString("home_section_title:"+id,next).apply();a.draw();
            }).show();
    }
    static void apply(MainActivity activity, LinearLayout parent, int[] bounds, SharedPreferences prefs, boolean editing){
        if(bounds.length!=8)return;
        ArrayList<View> all=new ArrayList<>();for(int i=0;i<parent.getChildCount();i++)all.add(parent.getChildAt(i));
        if(bounds[0]<0||bounds[7]>all.size())return;
        parent.removeAllViews();
        if(editing){LayoutTransition transition=new LayoutTransition();transition.setDuration(135);
            parent.setLayoutTransition(transition);
        }else parent.setLayoutTransition(null);
        for(int i=0;i<bounds[0];i++)parent.addView(all.get(i));
        HashMap<String,LinearLayout> groups=new HashMap<>();
        for(int i=0;i<IDS.length;i++){
            if(bounds[i]>bounds[i+1])return;
            final String id=IDS[i];
            LinearLayout group=new LinearLayout(activity);group.setOrientation(LinearLayout.VERTICAL);group.setTag("home:"+id);
            for(int j=bounds[i];j<bounds[i+1];j++)group.addView(all.get(j));
            if(group.getChildCount()==0&&!editing)continue;
            if(editing){
                // Hide real cards, lists, covers and pet; show compact reorder placeholders only.
                for(int child=0;child<group.getChildCount();child++)group.getChildAt(child).setVisibility(View.GONE);
                LinearLayout actions=new LinearLayout(activity);actions.setGravity(Gravity.CENTER_VERTICAL);
                actions.setPadding(dp(activity,8),dp(activity,3),dp(activity,8),dp(activity,3));
                GradientDrawable bg=new GradientDrawable();bg.setColor(0xffe7daf3);bg.setCornerRadius(dp(activity,12));actions.setBackground(bg);
                TextView grip=new TextView(activity);grip.setText("⠿");grip.setTextSize(25);grip.setGravity(Gravity.CENTER);grip.setTextColor(0xff30221e);
                actions.addView(grip,new LinearLayout.LayoutParams(dp(activity,44),dp(activity,50)));
                TextView label=new TextView(activity);label.setText(title(prefs,id));label.setTextColor(0xff30221e);label.setTextSize(15);label.setTypeface(null,1);
                label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);actions.addView(label,new LinearLayout.LayoutParams(0,-2,1));
                TextView rename=new TextView(activity);rename.setText("✎");rename.setTextColor(0xff30221e);
                rename.setTextSize(22);rename.setGravity(Gravity.CENTER);
                rename.setContentDescription("Переименовать "+title(prefs,id));
                actions.addView(rename,new LinearLayout.LayoutParams(dp(activity,44),dp(activity,50)));
                rename.setOnClickListener(v->rename(activity,prefs,id));
                TextView hide=new TextView(activity);hide.setText("✕");hide.setTextColor(0xff30221e);hide.setTextSize(20);hide.setGravity(Gravity.CENTER);
                actions.addView(hide,new LinearLayout.LayoutParams(dp(activity,44),dp(activity,50)));
                hide.setOnClickListener(v->{visible(prefs,id,false);activity.draw();});
                grip.setOnTouchListener((v,e)->{
                    if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){
                        v.startDragAndDrop(ClipData.newPlainText("home",id),new View.DragShadowBuilder(group),id,0);return true;
                    }return true;
                });
                group.addView(actions,0);
                group.setOnDragListener((v,event)->{
                    if(event.getAction()==DragEvent.ACTION_DRAG_STARTED)
                        return event.getLocalState() instanceof String;
                    if(event.getAction()==DragEvent.ACTION_DRAG_LOCATION){
                        if(parent.getParent() instanceof ScrollView){
                            ScrollView scroll=(ScrollView)parent.getParent();
                            float y=event.getY();
                            if(y<dp(activity,14))scroll.smoothScrollBy(0,-dp(activity,20));
                            else if(y>group.getHeight()-dp(activity,14))scroll.smoothScrollBy(0,dp(activity,20));
                        }
                        return true;
                    }
                    if(event.getAction()==DragEvent.ACTION_DRAG_ENTERED){
                        if(!(event.getLocalState() instanceof String))return false;
                        String from=(String)event.getLocalState();
                        View source=null;
                        for(int j=0;j<parent.getChildCount();j++){
                            View candidate=parent.getChildAt(j);
                            if(("home:"+from).equals(candidate.getTag())){source=candidate;break;}
                        }
                        if(source!=null&&source!=group&&group.getParent()==parent){
                            int target=parent.indexOfChild(group);
                            parent.removeView(source);parent.addView(source,Math.min(target,parent.getChildCount()));
                        }
                        return true;
                    }
                    if(event.getAction()==DragEvent.ACTION_DROP){
                        ArrayList<String> ordered=new ArrayList<>();
                        for(int j=0;j<parent.getChildCount();j++){
                            Object tag=parent.getChildAt(j).getTag();
                            if(tag instanceof String&&((String)tag).startsWith("home:"))ordered.add(((String)tag).substring(5));
                        }
                        for(String name:order(prefs))if(!ordered.contains(name))ordered.add(name);
                        setOrder(prefs,ordered);return true;
                    }
                    if(event.getAction()==DragEvent.ACTION_DRAG_ENDED&&!event.getResult()){
                        parent.post(activity::draw);return true;
                    }
                    return true;
                });
            }else{
                group.setOnLongClickListener(v->{if(!MenuEditPrefs.longPress(activity))return false;activity.enterHomeEditing();return true;});
                if(group.getChildCount()>0){View first=group.getChildAt(0);
                    first.setOnLongClickListener(v->{if(!MenuEditPrefs.longPress(activity))return false;activity.enterHomeEditing();return true;});
                }
            }
            groups.put(id,group);
        }
        for(String id:order(prefs)){
            if(!visible(prefs,id))continue;
            LinearLayout group=groups.get(id);if(group==null)continue;
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(activity,editing?8:12);
            parent.addView(group,lp);
        }
    }
    /** Legacy entrypoint remains harmless; long press on the home pet now opens editing. */
    static void startPetDrag(View pet){
        android.content.Context context=pet.getContext();
        if(context instanceof MainActivity)((MainActivity)context).enterHomeEditing();
    }
}
