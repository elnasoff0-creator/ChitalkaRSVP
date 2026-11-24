package ru.reader.rsvp;

import java.io.IOException;
import java.util.Locale;

/** Pure request/file validation shared by Android and the standalone QA suite. */
final class WebTransferPolicy {
    private WebTransferPolicy(){}
    static String filename(String raw)throws IOException {
        if(raw==null||raw.length()>230)throw new IOException("Недопустимая длина имени");
        if(raw.indexOf('/')>=0||raw.indexOf('\\')>=0||raw.indexOf('\u0000')>=0||raw.equals(".")||raw.equals(".."))
            throw new IOException("Путь внутри имени запрещён");
        String name=raw.trim();if(name.isEmpty()||name.startsWith(".")||name.length()>160)
            throw new IOException("Недопустимое имя файла");
        for(int i=0;i<name.length();i++)if(Character.isISOControl(name.charAt(i)))throw new IOException("Недопустимые символы имени");
        return name;
    }
    static String kind(String filename)throws IOException {
        String s=filename.toLowerCase(Locale.ROOT);
        for(String e:new String[]{".epub",".fb2",".txt",".html",".htm"})if(s.endsWith(e))return "book";
        for(String e:new String[]{".cbz",".cbr"})if(s.endsWith(e))return "manga";
        for(String e:new String[]{".mp3",".m4a",".m4b",".ogg",".flac"})if(s.endsWith(e))return "audio";
        throw new IOException("Неподдерживаемый формат");
    }
    static long length(String value,String transferEncoding,long limit)throws IOException {
        if(transferEncoding!=null)throw new IOException("Chunked upload запрещён");
        try{long parsed=Long.parseLong(value);if(parsed>0&&parsed<=limit)return parsed;}catch(Exception ignored){}
        throw new IOException("Недопустимый размер файла");
    }
    static boolean sameOrigin(String host,String origin,String local){return local!=null&&local.equals(origin)&&local.startsWith("http://")&&local.substring(7).equals(host);}
}
