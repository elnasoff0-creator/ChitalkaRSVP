package ru.reader.rsvp;

import java.nio.charset.StandardCharsets;

/** Byte budget for complete WebSocket SSML messages, not a UTF-16 character limit.
 * Keep a conservative allowance for <speak>, voice name and prosody attributes.
 * Never truncate an oversized sentence to meet this budget.
 */
final class EdgeSpeechSize {
    static final int MAX_SSML_UTF8=4096;
    private static final int ENVELOPE_ALLOWANCE=256;
    private EdgeSpeechSize(){}
    static String escaped(String text){
        if(text==null)return "";
        return text.replace("&","&amp;").replace("<","&lt;")
            .replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"," ");
    }
    static int speechBytes(String text){return escaped(text).getBytes(StandardCharsets.UTF_8).length;}
    static boolean fits(String text){
        return speechBytes(text)<=MAX_SSML_UTF8-ENVELOPE_ALLOWANCE;
    }
}
