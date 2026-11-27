package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;

/** Configuration is read only at the beginning of a session. No automatic server startup. */
final class WebTransferPrefs {
    static final String PORT="web_transfer_port_192", TIMEOUT="web_transfer_idle_minutes_192";
    static final String DUPLICATE="web_transfer_duplicate_192", NOTIFY="web_transfer_notify_192";
    private WebTransferPrefs(){}
    static SharedPreferences get(Context c){return c.getSharedPreferences("reader",Context.MODE_PRIVATE);}
    static final String DYNAMIC="web_transfer_dynamic_195";
    static boolean dynamic(Context c){return get(c).getBoolean(DYNAMIC,false);}
    static int fixedPort(Context c){int p=get(c).getInt(PORT,8787);return p>=1024&&p<=65535?p:8787;}
    static int port(Context c){return dynamic(c)?0:fixedPort(c);}
    static int idleMinutes(Context c){int v=get(c).getInt(TIMEOUT,15);return v==0?0:Math.max(5,Math.min(30,v));}
    static String duplicate(Context c){String v=get(c).getString(DUPLICATE,"ask");return "skip".equals(v)||"rename".equals(v)?v:"ask";}
    static boolean notifications(Context c){return get(c).getBoolean(NOTIFY,true);}
}
