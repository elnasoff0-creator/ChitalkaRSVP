package ru.reader.rsvp;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Clock correction for unofficial Edge Read Aloud's time-bound GEC token.
 * Only the client's token timestamp is adjusted; Android's system clock is untouched.
 */
final class EdgeServerClock {
    private static volatile long skewMillis;
    private EdgeServerClock() {}

    static long adjustedEpochSeconds() {
        return (System.currentTimeMillis()+skewMillis)/1000L;
    }

    /** Returns true only when a valid Date header changes the token for a retry.
     * Caller must limit the HTTP 403 retry to a single additional handshake.
     */
    static synchronized boolean correctFor403(String httpDate,String failedGec,long receivedAtMillis) {
        if(httpDate==null||failedGec==null)return false;
        SimpleDateFormat formatter=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        formatter.setLenient(false);
        ParsePosition position=new ParsePosition(0);
        Date parsed=formatter.parse(httpDate.trim(),position);
        if(parsed==null||position.getIndex()!=httpDate.trim().length())return false;
        long serverEpoch=parsed.getTime();
        long correctedSeconds=serverEpoch/1000L;
        if(EdgeToken.generate(correctedSeconds).equals(failedGec))return false;
        skewMillis=serverEpoch-receivedAtMillis;
        return true;
    }
}
