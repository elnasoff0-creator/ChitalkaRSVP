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
    public static final int EGG=0, CHICK=1, READER=2, ADULT=3, DEAD=4;
    public static final int UNDECIDED=0, CLASSIC=1, SPEED=2, AUDIO=3;
    private static final String PREFS="chteico_v1";
    private ChteicoStore(){}
    public static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);}    
    public static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}    
    public static int stage(Context c){return prefs(c).getBoolean("pet_dead",false)?DEAD:prefs(c).getInt("stage",EGG);}
    public static boolean dead(Context c){return stage(c)==DEAD;}
    private static boolean acceptsFood(Context c){checkLife(c,false);return enabled(c)&&!dead(c);}
    public static synchronized void checkLife(Context c,boolean visit){
        SharedPreferences p=prefs(c); long now=System.currentTimeMillis();
        if(!p.getBoolean("enabled",false))return;
        if(!p.contains("pet_created_at")){
            SharedPreferences.Editor init=p.edit().putLong("pet_created_at",now).putLong("last_app_visit_at",now);
            if(stage(c)!=EGG)init.putLong("pet_hatched_at",now).putLong("last_fed_at",now);
            init.apply();
        }
        if(!dead(c)&&stage(c)!=EGG){
            long fed=p.getLong("last_fed_at",0),last=p.getLong("last_app_visit_at",0);
            if(fed<=0||last<=0){p.edit().putLong("last_fed_at",now).putLong("last_app_visit_at",now).apply();}
            else if(ChteicoLifeCycle.dies(now,fed,last)){
                p.edit().putBoolean("pet_dead",true).putLong("pet_dead_at",now).putBoolean("fishing_death_choice_pending",true).apply();
                ChteicoNotifications.cancelHunger(c);
            }
        }
        if(visit)p.edit().putLong("last_app_visit_at",now).apply();
    }
    public static ChteicoLifeCycle.Mood mood(Context c){SharedPreferences p=prefs(c);long now=p.getLong("life_paused_at",0);if(now<=0)now=System.currentTimeMillis();return ChteicoLifeCycle.mood(now,p.getLong("last_fed_at",now));}
    public static String moodName(Context c){switch(mood(c)){case EXCELLENT:return "отличное";case GOOD:return "хорошее";case SAD:return "грустное";default:return "нормальное";}}
    public static boolean fishingChoicePending(Context c){return prefs(c).getBoolean("fishing_death_choice_pending",false);}
    public static synchronized boolean resolveFishingChoice(Context c,boolean restart){
        SharedPreferences p=prefs(c);if(!p.getBoolean("fishing_death_choice_pending",false))return false;SharedPreferences.Editor e=p.edit();
        if(restart){
            // Migrate old shared page history before deleting game-only keys.
            e.putInt("reading_comic_unique_pages",comicPages(c));
            for(String key:p.getAll().keySet())if(key.startsWith("fishing_")){
                if(key.startsWith("fishing_page_")&&p.getBoolean(key,false))e.putBoolean("reading_page_"+key.substring("fishing_page_".length()),true);
                e.remove(key);
            }
            e.remove("food_fishing");
        }
        e.remove("fishing_death_choice_pending");return e.commit();
    }
    private static void resetLife(SharedPreferences.Editor e){long now=System.currentTimeMillis();e.remove("pet_dead").remove("pet_dead_at").remove("pet_hatched_at").remove("last_fed_at").remove("feeding_count").remove("last_hunger_notice_at").remove("last_pet_notice_at").remove("life_paused_at").putLong("pet_created_at",now).putLong("last_app_visit_at",now);}
    
    public static int branch(Context c){return prefs(c).getInt("branch",UNDECIDED);}    
    public static String stageName(int stage){return stage==DEAD?"Погиб":stage==ADULT?"Чтун":stage==READER?"Чтенец":stage==CHICK?"Чтенчик":"Чтейцо";}
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
        SharedPreferences p=prefs(c);SharedPreferences.Editor e=p.edit().putLong("words_advanced",p.getLong("words_advanced",0)+Math.min(400,increment));
        if(acceptsFood(c))e.putLong("food_words",p.getLong("food_words",0)+Math.min(400,increment));
        e.apply();
    }
    public static synchronized void tickAudio(Context c,String id,int seconds){
        tick(c,AUDIO,seconds);
    }
    private static void tick(Context c,int kind,int seconds){
        if(seconds<=0)return;
        SharedPreferences p=prefs(c);SharedPreferences.Editor e=p.edit();
        String key=kind==AUDIO?"seconds_audio":kind==SPEED?"seconds_speed":"seconds_classic";
        e.putLong(key,p.getLong(key,0)+Math.min(60,seconds));
        String daily="daily_"+(kind==AUDIO?"audio_":"read_")+date(System.currentTimeMillis());
        e.putLong(daily,p.getLong(daily,0)+Math.min(60,seconds));
        if(!p.contains("daily_stats_since"))e.putLong("daily_stats_since",System.currentTimeMillis());
        if(kind==AUDIO&&acceptsFood(c))e.putLong("food_seconds",p.getLong("food_seconds",0)+Math.min(60,seconds));
        active(p,e);e.apply();
    }
    public static synchronized boolean completeBook(Context c,String id){return complete(c,"finished_books",id);}    
    public static synchronized boolean completeAudio(Context c,String id){return complete(c,"finished_audio",id);}    
    private static boolean complete(Context c,String key,String id){
        if(id==null||id.isEmpty())return false;
        SharedPreferences p=prefs(c);if(ids(p,key).contains(id))return false;
        SharedPreferences.Editor e=p.edit();addUnique(p,e,key,id);active(p,e);
        int st=p.getInt("stage",EGG);
        // Completion remains a historical achievement; feeding drives new evolution.
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
            e.putLong("pet_hatched_at",System.currentTimeMillis()).putLong("last_fed_at",System.currentTimeMillis()).putInt("stage",CHICK).putInt("branch",chosenBranch(p))
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
        if(!enabled(c)||dead(c))return false;
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
        if(dead(c)||event!=CHICK&&event!=READER&&event!=ADULT)return null;
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
        return custom.isEmpty()?stageName(prefs(c).getInt("stage",EGG)):custom;
    }
    public static boolean canName(Context c){return !dead(c)&&!prefs(c).getBoolean("name_locked",false);}    
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
        int readingProgress=(int)(seconds*95/Math.max(1L,secondsTarget));
        int gameBonus=Math.min(10,p.getInt("fishing_xp",0)/200);
        return Math.min(95,readingProgress+gameBonus);
    }
    public static int progress(Context c){SharedPreferences p=prefs(c);int st=stage(c);
        if(st==DEAD)return 0;
        if(st==ADULT)return 100;
        if(st==EGG)return eggUnits(c)*100/8;
        long xp=p.getLong("pet_food_xp",st>=READER?300:0);
        return (int)Math.min(100,(st==CHICK?xp:Math.max(0,xp-300))*100/(st==CHICK?300:750));
    }
    public static long foodPages(Context c){SharedPreferences p=prefs(c);return p.getLong("food_words",0)/250+p.getLong("food_fishing",0)/10;}
    public static long foodMinutes(Context c){return prefs(c).getLong("food_seconds",0)/60;}
    public static long foodComics(Context c){return prefs(c).getLong("food_comics",0);}
    public static synchronized long feed(Context c){
        checkLife(c,false);
        if(!acceptsFood(c)||stage(c)==EGG)return 0;
        SharedPreferences p=prefs(c);long gain=foodPages(c)+foodMinutes(c)+foodComics(c);
        if(gain<=0)return 0;
        long xp=p.getLong("pet_food_xp",stage(c)==ADULT?1050:stage(c)>=READER?300:0)+gain;
        int old=stage(c),next=xp>=1050?ADULT:xp>=300?READER:CHICK;
        SharedPreferences.Editor e=p.edit().putLong("last_fed_at",System.currentTimeMillis()).putInt("feeding_count",p.getInt("feeding_count",0)+1).remove("last_hunger_notice_at").putLong("pet_food_xp",xp)
            .putLong("food_words",p.getLong("food_words",0)%250)
            .putLong("food_fishing",p.getLong("food_fishing",0)%10)
            .putLong("food_seconds",p.getLong("food_seconds",0)%60).putLong("food_comics",0);
        if(next>old){e.putInt("stage",next).putInt("pending_intro_stage",next).putInt("pending_stage_up",0);}
        if(!e.commit())return 0;
        ChteicoNotifications.cancelHunger(c);ChteicoNotifications.schedule(c);return gain;
    }
    /** Fishing economy is isolated from reading positions and existing books. */
    public static int fishingBait(Context c){return prefs(c).getInt("fishing_bait",10);}
    public static int fishingGold(Context c){return prefs(c).getInt("fishing_gold",0);}
    public static synchronized boolean useFishingBait(Context c){
        SharedPreferences p=prefs(c);int left=fishingBait(c);if(left<=0)return false;
        p.edit().putInt("fishing_bait",left-1).putInt("fishing_casts",p.getInt("fishing_casts",0)+1).apply();return true;
    }
    public static synchronized boolean buyFishingBait(Context c,int amount){
        final int cost=amount==1?30:amount==10?270:amount==50?1200:-1;
        if(cost<0)return false;
        SharedPreferences p=prefs(c);int gold=p.getInt("fishing_gold",0);
        if(gold<cost)return false;
        p.edit().putInt("fishing_gold",gold-cost)
            .putInt("fishing_bait",Math.max(0,p.getInt("fishing_bait",10))+amount).apply();return true;
    }
    public static synchronized void recordFishingCatch(Context c,int massGrams,int xp,int gold,int rarity){
        recordFishingCatch(c,massGrams,xp,gold,rarity,false);
    }
    /** Rod ownership and equipment are persisted independently of the pet and library. */
    private static final int[] FISHING_ROD_PRICES={0,180,800,3000,8000};
    public static int fishingRodEquipped(Context c){
        return Math.max(0,Math.min(4,prefs(c).getInt("fishing_rod_equipped",0)));
    }
    public static boolean hasFishingRod(Context c,int rod){
        return rod==0||(rod>0&&rod<FISHING_ROD_PRICES.length&&prefs(c).getBoolean("fishing_rod_owned_"+rod,false));
    }
    public static synchronized boolean equipFishingRod(Context c,int rod){
        if(!hasFishingRod(c,rod))return false;
        prefs(c).edit().putInt("fishing_rod_equipped",rod).apply();return true;
    }
    public static synchronized boolean purchaseFishingRod(Context c,int rod){
        if(rod<=0||rod>=FISHING_ROD_PRICES.length)return false;
        SharedPreferences p=prefs(c);
        if(p.getBoolean("fishing_rod_owned_"+rod,false))return false;
        int price=FISHING_ROD_PRICES[rod],gold=p.getInt("fishing_gold",0);
        if(gold<price)return false;
        // One atomic preference update prevents a paid rod from not being registered.
        return p.edit().putInt("fishing_gold",gold-price)
            .putBoolean("fishing_rod_owned_"+rod,true)
            .putInt("fishing_rod_equipped",rod).commit();
    }
    /** Saves fish totals and the optional shimmer counter in one consistent transaction. */
    public static synchronized void recordFishingCatch(Context c,int massGrams,int xp,int gold,int rarity,boolean shimmering){
        if(massGrams<=0||xp<0||gold<=0||rarity<0||rarity>4)return;
        SharedPreferences p=prefs(c);
        SharedPreferences.Editor e=p.edit().putInt("fishing_xp",p.getInt("fishing_xp",0)+xp)
            .putInt("fishing_gold",p.getInt("fishing_gold",0)+gold)
            .putInt("fishing_caught",p.getInt("fishing_caught",0)+1)
            .putLong("fishing_total_grams",p.getLong("fishing_total_grams",0L)+massGrams)
            .putLong("fishing_rarity_total_grams_"+rarity,
                p.getLong("fishing_rarity_total_grams_"+rarity,0L)+massGrams)
            .putInt("fishing_best_grams",Math.max(p.getInt("fishing_best_grams",0),massGrams))
            .putInt("fishing_rarity_"+rarity,p.getInt("fishing_rarity_"+rarity,0)+1)
            .putInt("fishing_rarity_best_grams_"+rarity,
                Math.max(p.getInt("fishing_rarity_best_grams_"+rarity,0),massGrams));
        if(acceptsFood(c))e.putLong("food_fishing",p.getLong("food_fishing",0)+xp);
        if(shimmering){
            e.putInt("fishing_shimmer_count",p.getInt("fishing_shimmer_count",0)+1)
                .putInt("fishing_shimmer_rarity_"+rarity,p.getInt("fishing_shimmer_rarity_"+rarity,0)+1);
        }
        e.apply();
    }
    /** The old-item count retains its legacy preference key so previous catches are preserved. */
    public static synchronized void recordFishingOldCover(Context c){
        SharedPreferences p=prefs(c);
        p.edit().putInt("fishing_old_boots",p.getInt("fishing_old_boots",0)+1).apply();
    }
    /** Compatibility for older callers; stored finds are now called "Старая обложка". */
    @Deprecated public static void recordFishingBoot(Context c){recordFishingOldCover(c);}
    private static String stableRewardId(String kind,String source,int ordinal){
        // Hashing keeps SharedPreferences keys short even for long content:// URIs.
        String text=kind+"|"+source+"|"+ordinal;
        try{java.security.MessageDigest sha=java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest=sha.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return hex.toString();
        }catch(Exception e){return Integer.toHexString(text.hashCode());}
    }
    private static boolean creditOnce(SharedPreferences p,String kind,String source,int number){
        String id=stableRewardId(kind,source,number);
        java.util.Set<String> registered=new java.util.HashSet<>(p.getStringSet("fishing_reward_ids",java.util.Collections.emptySet()));
        if(!registered.add(id))return false;
        p.edit().putStringSet("fishing_reward_ids",registered)
            .putInt("fishing_bait",p.getInt("fishing_bait",10)+10).apply();return true;
    }
    /** Only completed chapters are awarded; navigation alone does not award bait. */
    public static synchronized boolean awardReadChapter(Context c,String book,int chapter){
        if(book==null||book.isEmpty()||chapter<0)return false;
        return creditOnce(prefs(c),"read",book,chapter);
    }
    public static synchronized boolean awardAudioChapter(Context c,String book,int chapter){
        if(book==null||book.isEmpty()||chapter<0)return false;
        return creditOnce(prefs(c),"audio",book,chapter);
    }
    /** Only unique visited pages count; progress accumulates across every manga/volume. */
    public static synchronized boolean recordComicPage(Context c,String comic,int volume,int page){
        if(comic==null||comic.isEmpty()||volume<0||page<0)return false;
        SharedPreferences p=prefs(c);
        String id=stableRewardId("comic",comic+"|"+volume,page);
        // Per-page keys avoid copying an ever-growing StringSet on every swipe.
        String pageKey="fishing_page_"+id,readingKey="reading_page_"+id;
        boolean oldGame=p.getBoolean(pageKey,false),seen=p.getBoolean(readingKey,false)||oldGame;
        if(seen&&oldGame)return false;
        SharedPreferences.Editor edit=p.edit();boolean bait=false;
        if(!seen){edit.putBoolean(readingKey,true).putInt("reading_comic_unique_pages",comicPages(c)+1);
            if(acceptsFood(c))edit.putLong("food_comics",p.getLong("food_comics",0)+1);
        }
        if(!oldGame){int total=p.getInt("fishing_comic_unique_pages",0)+1;
            edit.putBoolean(pageKey,true).putInt("fishing_comic_unique_pages",total);
            bait=total%10==0;if(bait)edit.putInt("fishing_bait",p.getInt("fishing_bait",10)+10);
        }
        edit.apply();return bait;
    }
    public static int comicPages(Context c){SharedPreferences p=prefs(c);return p.getInt("reading_comic_unique_pages",p.getInt("fishing_comic_unique_pages",0));}
    public static boolean developerUnlocked(Context c){return prefs(c).getBoolean("developer_unlocked",false);}    
    public static void unlockDeveloper(Context c){prefs(c).edit().putBoolean("developer_unlocked",true).apply();}
    public static boolean toggleDeveloper(Context c){
        boolean next=!developerUnlocked(c);
        prefs(c).edit().putBoolean("developer_unlocked",next).apply();
        return next;
    }
    /** Developer-only preview override for QA of every evolution and reading branch. */
    public static synchronized void setDeveloperState(Context c,int stage,int branch){
        if(dead(c))return;
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
        else {putStageMarkers(p,e);e.putLong("last_fed_at",System.currentTimeMillis()).putLong("pet_hatched_at",System.currentTimeMillis());}
        e.apply();
    }
    public static synchronized void setEnabled(Context c,boolean enabled){
        SharedPreferences p=prefs(c);
        if(!enabled)checkLife(c,false);
        SharedPreferences.Editor e=p.edit().putBoolean("enabled",enabled);
        long now=System.currentTimeMillis(),paused=p.getLong("life_paused_at",0);
        if(!enabled&&p.getBoolean("enabled",false))e.putLong("life_paused_at",now);
        if(enabled&&paused>0){long delta=Math.max(0,now-paused);for(String key:new String[]{"last_fed_at","pet_hatched_at","pet_created_at","last_app_visit_at"}){long old=p.getLong(key,0);if(old>0)e.putLong(key,old+delta);}e.remove("life_paused_at");}
        // Existing lifetime statistics must not predetermine a newly activated egg's branch.
        // Keep the markers intact when resuming an egg already in progress.
        if(enabled && !p.getBoolean("enabled",false) && p.getInt("stage",EGG)==EGG
                && p.getInt("egg_units",0)==0 && p.getLong("branch_marker_classic",-1)<0){
            e.putLong("branch_marker_classic",p.getLong("seconds_classic",0))
             .putLong("branch_marker_speed",p.getLong("seconds_speed",0))
             .putLong("branch_marker_audio",p.getLong("seconds_audio",0));
        }
        e.apply();
        if(enabled)markAppLaunch(c);
        ChteicoNotifications.schedule(c);
    }    
    /** Reset game state, not imported books nor reader/audioplayer positions. */
    /** Reset the pet without touching independent reading statistics, earned achievements or books. */
    public static synchronized void resetPet(Context c){
        SharedPreferences p=prefs(c);
        SharedPreferences.Editor e=p.edit().putInt("stage",EGG).putInt("branch",UNDECIDED)
            .putInt("egg_units",0).remove("pet_food_xp").remove("food_words").remove("food_seconds").remove("food_fishing").remove("food_comics").remove("pending_intro_stage").putBoolean("pending_hatch",false)
            .putInt("pending_stage_up",0).remove("custom_name").remove("name_locked")
            .remove("bubble_seen").remove("bubble_last_id").remove("sleep_day").remove("launch_day").remove("daily_sleep_pending")
            .remove("evo_marker_classic").remove("evo_marker_speed").remove("evo_marker_audio")
            .remove("evo_marker_finished_books_count").remove("evo_marker_finished_audio_count")
            .putLong("branch_marker_classic",p.getLong("seconds_classic",0))
            .putLong("branch_marker_speed",p.getLong("seconds_speed",0))
            .putLong("branch_marker_audio",p.getLong("seconds_audio",0))
            .putInt("branch_marker_finished_books_count",p.getInt("finished_books_count",0))
            .putInt("branch_marker_finished_audio_count",p.getInt("finished_audio_count",0));
        resetLife(e);e.remove("post_hatch_mature").remove("bubble_last_quote_id").remove("hatch_last_kind");e.apply();ChteicoNotifications.cancelHunger(c);ChteicoNotifications.schedule(c);
    }
    /** Called when MainActivity is first created; the sleep/wake sequence runs at most once per day. */
    public static void markAppLaunch(Context c){
        checkLife(c,true);
        if(!enabled(c)||dead(c))return;
        SharedPreferences p=prefs(c);
        String today=date(System.currentTimeMillis());
        if(!today.equals(p.getString("launch_day","")))
            p.edit().putString("launch_day",today).putBoolean("daily_sleep_pending",true).apply();
    }
    /** Reselect on the next book/audio opening; lifetime statistics remain available. */
    public static void resetBranch(Context c){if(dead(c))return;prefs(c).edit().putInt("stage",EGG).putInt("branch",UNDECIDED).putInt("egg_units",0).remove("pet_food_xp").remove("food_words").remove("food_seconds").remove("food_fishing").remove("food_comics").remove("pending_intro_stage").putBoolean("pending_hatch",false).putInt("pending_stage_up",0)
        .remove("bubble_seen").remove("bubble_last_id").remove("sleep_day").remove("launch_day").remove("daily_sleep_pending")
        .putLong("branch_marker_classic",prefs(c).getLong("seconds_classic",0))
        .putLong("branch_marker_speed",prefs(c).getLong("seconds_speed",0))
        .putLong("branch_marker_audio",prefs(c).getLong("seconds_audio",0))
        .putInt("branch_marker_finished_books_count",prefs(c).getInt("finished_books_count",0))
        .putInt("branch_marker_finished_audio_count",prefs(c).getInt("finished_audio_count",0))
        .remove("evo_marker_classic").remove("evo_marker_speed").remove("evo_marker_audio")
        .remove("evo_marker_finished_books_count").remove("evo_marker_finished_audio_count").apply();}
}
