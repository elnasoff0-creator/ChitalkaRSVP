package ru.reader.rsvp;
/** Pure clock rules shared by UI, storage and reminders. */
public final class ChteicoLifeCycle {
    public static final long HOUR=3600000L, DAY=24*HOUR, MONTH=30*DAY;
    public enum Mood { EXCELLENT, GOOD, NORMAL, SAD }
    public static long elapsed(long now,long then){return then<=0?0:Math.max(0,now-then);}
    public static Mood mood(long now,long fed){long age=elapsed(now,fed);return age<3*HOUR?Mood.EXCELLENT:age<8*HOUR?Mood.GOOD:age<DAY?Mood.NORMAL:Mood.SAD;}
    public static boolean dies(long now,long fed,long visit){return elapsed(now,fed)>=MONTH&&elapsed(now,visit)>=MONTH;}
    public static long reminderInterval(long now,long fed){long age=elapsed(now,fed);return age<DAY?0:age<3*DAY?DAY:12*HOUR;}
    private ChteicoLifeCycle(){}
}
