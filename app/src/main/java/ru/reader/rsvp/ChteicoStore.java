package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** Local, opt-in reading companion. Independent of the book library and playback positions. */
public final class ChteicoStore {
    public static final int EGG=0, CHICK=1, READER=2, ADULT=3;
    public static final int UNDECIDED=0, CLASSIC=1, SPEED=2, AUDIO=3;
    private static final String PREFS="chteico_v1";
    private ChteicoStore(){}
    public static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);}    
    public static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}    
    public static int stage(Context c){return prefs(c).getInt("stage",EGG);}    
    public static int branch(Context c){return prefs(c).getInt("branch",UNDECIDED);}    
    public static String stageName(int stage){return stage==ADULT?"Чтун":stage==READER?"Чтенец":stage==CHICK?"Чтенчик":"Чтейцо";}
    public static String branchName(int branch){return branch==AUDIO?"Аудиокниги":branch==SPEED?"Скорочтение":branch==CLASSIC?"Чтение":"Ещё не выбрана";}
    private static String date(long millis){return new SimpleDateFormat("yyyyMMdd",Locale.ROOT).format(new Date(millis));}
    private static int daysBetween(String first,String second){try{
        SimpleDateFormat df=new SimpleDateFormat("yyyyMMdd",Locale.ROOT);df.setTimeZone(TimeZone.getDefault());
        long a=df.parse(first).getTime(),b=df.parse(second).getTime();
        return (int)Math.round((b-a)/86400000.0);
    }catch(Exception ignored){return 100;}}
    private static void active(SharedPreferences p, SharedPreferences.Editor e){
        long now=System.currentTimeMillis();String today=date(now),prev=p.getString("last_day","");
        if(!today.equals(prev)){
            int streak=daysBetween(prev,today)==1?p.getInt("streak",0)+1:1;
            e.putString("last_day",today).putInt("streak",streak).putInt("best_streak",Math.max(streak,p.getInt("best_streak",0)));
        }
        e.putLong("last_active",now);
    }
    private static Set<String> ids(SharedPreferences p,String key){Set<String> result=new HashSet<>();
        try{JSONArray a=new JSONArray(p.getString(key,"[]"));for(int i=0;i<a.length();i++)result.add(a.optString(i));}catch(Exception ignored){}return result;
    }
    private static void addUnique(SharedPreferences p,SharedPreferences.Editor e,String key,String id){
        if(id==null||id.isEmpty())return;Set<String> all=ids(p,key);if(all.add(id)){
            JSONArray a=new JSONArray();for(String val:all)a.put(val);e.putString(key,a.toString()).putInt(key+"_count",all.size());
        }
    }
    public static void openBook(Context c,String id,int mode){open(c,id,mode==0?CLASSIC:SPEED,false);}    
    public static void openAudio(Context c,String id){open(c,id,AUDIO,true);}    
    private static synchronized void open(Context c,String id,int kind,boolean audio){
        if(id==null||id.isEmpty())return;
        SharedPreferences p=prefs(c);SharedPreferences.Editor e=p.edit();
        addUnique(p,e,audio?"opened_audio":"opened_books",id);
        e.putInt("sessions",p.getInt("sessions",0)+1);
        active(p,e);
        if(enabled(c)&&p.getInt("stage",EGG)==EGG)e.putBoolean("pending_hatch",true).putInt("hatch_last_kind",kind);
        e.apply();
    }
    /** Only call while actual reading / player playback occurs, never on idle background time. */
    public static synchronized void tickReading(Context c,int mode,int seconds){tick(c,mode==0?CLASSIC:SPEED,seconds);}    
    public static synchronized void recordWords(Context c,int increment){
        if(increment<=0)return;
        SharedPreferences p=prefs(c);p.edit().putLong("words_advanced",p.getLong("words_advanced",0)+Math.min(400,increment)).apply();
    }
    public static synchronized void tickAudio(Context c,String id,int seconds){
        tick(c,AUDIO,seconds);
    }
    private static void tick(Context c,int kind,int seconds){
        if(seconds<=0)return;
        SharedPreferences p=prefs(c);SharedPreferences.Editor e=p.edit();
        String key=kind==AUDIO?"seconds_audio":kind==SPEED?"seconds_speed":"seconds_classic";
        e.putLong(key,p.getLong(key,0)+Math.min(60,seconds));
        active(p,e);e.apply();
    }
    public static synchronized boolean completeBook(Context c,String id){return complete(c,"finished_books",id);}    
    public static synchronized boolean completeAudio(Context c,String id){return complete(c,"finished_audio",id);}    
    private static boolean complete(Context c,String key,String id){
        if(id==null||id.isEmpty())return false;
        SharedPreferences p=prefs(c);if(ids(p,key).contains(id))return false;
        SharedPreferences.Editor e=p.edit();addUnique(p,e,key,id);active(p,e);
        int st=p.getInt("stage",EGG);
        if(enabled(c)){
            if(st==CHICK)e.putInt("pending_stage_up",READER);
            else if(st==READER)e.putInt("pending_stage_up",ADULT);
        }
        e.apply();return true;
    }
    /** Eggs have eight progress units: one per tap, two per qualified home return. */
    public static int eggUnits(Context c){return Math.max(0,Math.min(8,prefs(c).getInt("egg_units",0)));}
    public static boolean eggCracked(Context c){return stage(c)==EGG&&eggUnits(c)>=4;}
    private static int chosenBranch(SharedPreferences p){
        long classic=Math.max(0,p.getLong("seconds_classic",0)-p.getLong("branch_marker_classic",0));
        long speed=Math.max(0,p.getLong("seconds_speed",0)-p.getLong("branch_marker_speed",0));
        long audio=Math.max(0,p.getLong("seconds_audio",0)-p.getLong("branch_marker_audio",0));
        int last=p.getInt("hatch_last_kind",CLASSIC);
        if(classic==0&&speed==0&&audio==0)return last;
        return audio>=classic&&audio>=speed?AUDIO:(speed>classic?SPEED:CLASSIC);
    }
    private static void putStageMarkers(SharedPreferences p,SharedPreferences.Editor e){
        e.putLong("evo_marker_classic",p.getLong("seconds_classic",0));
        e.putLong("evo_marker_speed",p.getLong("seconds_speed",0));
        e.putLong("evo_marker_audio",p.getLong("seconds_audio",0));
        e.putInt("evo_marker_finished_books_count",p.getInt("finished_books_count",0));
        e.putInt("evo_marker_finished_audio_count",p.getInt("finished_audio_count",0));
    }
    private static boolean addEggUnits(Context c,int units){
        SharedPreferences p=prefs(c);if(!p.getBoolean("enabled",false)||stage(c)!=EGG)return false;
        int next=Math.min(8,eggUnits(c)+units);
        SharedPreferences.Editor e=p.edit().putInt("egg_units",next);
        if(next>=8){
            e.putInt("stage",CHICK).putInt("branch",chosenBranch(p))
                .putBoolean("pending_hatch",false)
                .putInt("pending_stage_up",0)
                .putInt("pending_intro_stage",CHICK);
            putStageMarkers(p,e);
        }
        e.apply();return next>=8;
    }
    public static synchronized boolean petTapped(Context c){return addEggUnits(c,1);}    
    /** Consumes one visit armed by opening a book/audio screen; opening the home alone never counts. */
    public static synchronized boolean hatchOnHome(Context c){
        if(!enabled(c)||stage(c)!=EGG)return false;
        SharedPreferences p=prefs(c);
        if(!p.getBoolean("pending_hatch",false))return false;
        p.edit().putBoolean("pending_hatch",false).apply();
        return addEggUnits(c,2);
    }
    public static synchronized boolean matureOnHome(Context c){
        if(!enabled(c))return false;
        SharedPreferences p=prefs(c);int st=stage(c),pending=p.getInt("pending_stage_up",0);
        // Migrate a previously-earned evolution from the former three-stage release.
        if(st==CHICK && pending==0 && p.getBoolean("post_hatch_mature",false))pending=READER;
        if(st==CHICK&&pending==READER){
            SharedPreferences.Editor e=p.edit().putInt("stage",READER).putInt("pending_stage_up",0).putBoolean("post_hatch_mature",false).putInt("pending_intro_stage",READER);
            putStageMarkers(p,e);e.apply();return true;
        }
        if(st==READER&&pending==ADULT){
            SharedPreferences.Editor e=p.edit().putInt("stage",ADULT).putInt("pending_stage_up",0).putInt("pending_intro_stage",ADULT);
            putStageMarkers(p,e);e.apply();return true;
        }
        return false;
    }
    /** Introduction is displayed once per evolution, even when the pet has been renamed. */
    public static synchronized String consumeIntroduction(Context c){
        SharedPreferences p=prefs(c);int event=p.getInt("pending_intro_stage",-1);
        if(event!=CHICK&&event!=READER&&event!=ADULT)return null;
        p.edit().remove("pending_intro_stage").apply();
        return event==CHICK?ChteicoPhrases.INTRO_CHICK:event==READER?ChteicoPhrases.INTRO_READER:ChteicoPhrases.INTRO_ADULT;
    }
    public static synchronized boolean consumeDailySleep(Context c){
        SharedPreferences p=prefs(c);
        if(!p.getBoolean("daily_sleep_pending",false))return false;
        String today=date(System.currentTimeMillis());
        if(today.equals(p.getString("sleep_day","")))return false;
        p.edit().putString("sleep_day",today).putBoolean("daily_sleep_pending",false).apply();
        return true;
    }
    public static String petName(Context c){
        String custom=prefs(c).getString("custom_name","").trim();
        return custom.isEmpty()?stageName(stage(c)):custom;
    }
    public static boolean canName(Context c){return !prefs(c).getBoolean("name_locked",false);}    
    public static synchronized boolean setPetName(Context c,String name){
        if(!canName(c)||name==null)return false;
        name=name.trim();if(name.isEmpty()||name.length()>18||name.contains("\n"))return false;
        prefs(c).edit().putString("custom_name",name).putBoolean("name_locked",true).apply();return true;
    }
    private static int stageProgress(SharedPreferences p,long secondsTarget){
        int books=Math.max(0,p.getInt("finished_books_count",0)-p.getInt("evo_marker_finished_books_count",0));
        int audio=Math.max(0,p.getInt("finished_audio_count",0)-p.getInt("evo_marker_finished_audio_count",0));
        if(books+audio>0)return 100;
        long seconds=Math.max(0,p.getLong("seconds_classic",0)-p.getLong("evo_marker_classic",0))
            +Math.max(0,p.getLong("seconds_speed",0)-p.getLong("evo_marker_speed",0))
            +Math.max(0,p.getLong("seconds_audio",0)-p.getLong("evo_marker_audio",0));
        return Math.min(95,(int)(seconds*95/Math.max(1L,secondsTarget)));
    }
    public static int progress(Context c){SharedPreferences p=prefs(c);int st=stage(c);
        if(st==ADULT)return 100;
        if(st==EGG)return eggUnits(c)*100/8;
        if(st==CHICK)return stageProgress(p,3600L);
        return stageProgress(p,5400L);
    }
    public static boolean developerUnlocked(Context c){return prefs(c).getBoolean("developer_unlocked",false);}    
    public static void unlockDeveloper(Context c){prefs(c).edit().putBoolean("developer_unlocked",true).apply();}
    /** Developer-only preview override for QA of every evolution and reading branch. */
    public static synchronized void setDeveloperState(Context c,int stage,int branch){
        stage=Math.max(EGG,Math.min(ADULT,stage));
        branch=Math.max(UNDECIDED,Math.min(AUDIO,branch));
        if(stage!=EGG&&branch==UNDECIDED)branch=CLASSIC;
        SharedPreferences p=prefs(c);
        SharedPreferences.Editor e=p.edit()
            .putInt("stage",stage)
            .putInt("branch",stage==EGG?UNDECIDED:branch)
            .putInt("egg_units",stage==EGG?0:8)
            .putBoolean("pending_hatch",false)
            .putInt("pending_stage_up",0)
            .remove("pending_intro_stage")
            .remove("bubble_seen")
            .remove("bubble_last_id");
        if(stage==EGG)e.remove("sleep_day");
        else putStageMarkers(p,e);
        e.apply();
    }
    public static void setEnabled(Context c,boolean enabled){
        prefs(c).edit().putBoolean("enabled",enabled).apply();
        if(enabled)markAppLaunch(c);
        ChteicoNotifications.schedule(c);
    }    
    /** Reset game state, not imported books nor reader/audioplayer positions. */
    /** Reset the pet without touching independent reading statistics, earned achievements or books. */
    public static void resetPet(Context c){
        SharedPreferences p=prefs(c);
        SharedPreferences.Editor e=p.edit().putInt("stage",EGG).putInt("branch",UNDECIDED)
            .putInt("egg_units",0).remove("pending_intro_stage").putBoolean("pending_hatch",false)
            .putInt("pending_stage_up",0).remove("custom_name").remove("name_locked")
            .remove("bubble_seen").remove("bubble_last_id").remove("sleep_day").remove("launch_day").remove("daily_sleep_pending")
            .remove("evo_marker_classic").remove("evo_marker_speed").remove("evo_marker_audio")
            .remove("evo_marker_finished_books_count").remove("evo_marker_finished_audio_count")
            .putLong("branch_marker_classic",p.getLong("seconds_classic",0))
            .putLong("branch_marker_speed",p.getLong("seconds_speed",0))
            .putLong("branch_marker_audio",p.getLong("seconds_audio",0))
            .putInt("branch_marker_finished_books_count",p.getInt("finished_books_count",0))
            .putInt("branch_marker_finished_audio_count",p.getInt("finished_audio_count",0));
        e.apply();
    }
    /** Called when MainActivity is first created; the sleep/wake sequence runs at most once per day. */
    public static void markAppLaunch(Context c){
        if(!enabled(c))return;
        SharedPreferences p=prefs(c);
        String today=date(System.currentTimeMillis());
        if(!today.equals(p.getString("launch_day","")))
            p.edit().putString("launch_day",today).putBoolean("daily_sleep_pending",true).apply();
    }
    /** Reselect on the next book/audio opening; lifetime statistics remain available. */
    public static void resetBranch(Context c){prefs(c).edit().putInt("stage",EGG).putInt("branch",UNDECIDED).putInt("egg_units",0).remove("pending_intro_stage").putBoolean("pending_hatch",false).putInt("pending_stage_up",0)
        .remove("bubble_seen").remove("bubble_last_id").remove("sleep_day").remove("launch_day").remove("daily_sleep_pending")
        .putLong("branch_marker_classic",prefs(c).getLong("seconds_classic",0))
        .putLong("branch_marker_speed",prefs(c).getLong("seconds_speed",0))
        .putLong("branch_marker_audio",prefs(c).getLong("seconds_audio",0))
        .putInt("branch_marker_finished_books_count",prefs(c).getInt("finished_books_count",0))
        .putInt("branch_marker_finished_audio_count",prefs(c).getInt("finished_audio_count",0))
        .remove("evo_marker_classic").remove("evo_marker_speed").remove("evo_marker_audio")
        .remove("evo_marker_finished_books_count").remove("evo_marker_finished_audio_count").apply();}
}
