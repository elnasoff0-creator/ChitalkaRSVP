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
    static int speechBytes(String text){
        if(text==null)return 0;
        long count=0;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(c=='&')count+=5;
            else if(c=='<'||c=='>')count+=4;
            else if(c=='"'||c=='\'')count+=6;
            else if(c<=0x7f)count++;
            else if(c<=0x7ff)count+=2;
            else if(Character.isHighSurrogate(c)&&i+1<text.length()&&Character.isLowSurrogate(text.charAt(i+1))){count+=4;i++;}
            else if(Character.isSurrogate(c))count++; // UTF-8 encoder replaces malformed UTF-16 with '?'
            else count+=3;
            if(count>=Integer.MAX_VALUE)return Integer.MAX_VALUE;
        }
        return (int)count;
    }
    static boolean fits(String text){
        return speechBytes(text)<=MAX_SSML_UTF8-ENVELOPE_ALLOWANCE;
    }
}
