package ru.reader.rsvp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Decodes the Edge Read Aloud binary WebSocket envelope.
 * Wire format: 2-byte unsigned big-endian header length, ASCII headers, payload.
 * A final Path:audio frame WITHOUT Content-Type and WITHOUT bytes is normal.
 */
final class EdgeWsFrames {
    static final class Frame {
        final int dataOffset;
        final int dataLength;
        final boolean terminal;
        Frame(int offset, int length, boolean terminal){
            this.dataOffset=offset;this.dataLength=length;this.terminal=terminal;
        }
    }
    private EdgeWsFrames(){}
    /** Header detection for text frames (response, metadata, turn.end).
     * Do not match a raw substring: whitespace and header-name casing may vary.
     */
    static String textPath(String message){
        if(message==null)return "";
        String head=message;
        int end=message.indexOf("\r\n\r\n");
        if(end<0)end=message.indexOf("\n\n");
        if(end>=0)head=message.substring(0,end);
        for(String line:head.split("\r?\n")){
            int sep=line.indexOf(':');
            if(sep>0&&"path".equalsIgnoreCase(line.substring(0,sep).trim()))
                return line.substring(sep+1).trim().toLowerCase(Locale.ROOT);
        }
        return "";
    }
    static String textBody(String message){
        if(message==null)return "";
        int end=message.indexOf("\r\n\r\n");
        if(end>=0)return message.substring(end+4);
        end=message.indexOf("\n\n");
        return end<0?"":message.substring(end+2);
    }
    static Frame audio(byte[] packet) throws IOException {
        if(packet==null||packet.length<2)throw new IOException("Короткий бинарный пакет Edge");
        int headerLength=((packet[0]&255)<<8)|(packet[1]&255);
        if(headerLength<1||headerLength>8192||headerLength>packet.length-2)
            throw new IOException("Поврежденная длина заголовка Edge");
        String head=new String(packet,2,headerLength,StandardCharsets.US_ASCII);
        String path=null,contentType=null;
        for(String line:head.split("\\r?\\n")) {
            int sep=line.indexOf(':');
            if(sep<1)continue;
            String field=line.substring(0,sep).trim().toLowerCase(Locale.ROOT);
            String value=line.substring(sep+1).trim();
            if("path".equals(field))path=value;
            else if("content-type".equals(field))contentType=value;
        }
        if(!"audio".equalsIgnoreCase(path))
            throw new IOException("Неожиданный тип бинарного пакета Edge: "+(path==null?"отсутствует":path));
        int offset=2+headerLength;
        int length=packet.length-offset;
        // The service sends Path:audio with no Content-Type and no bytes at the end.
        if(length==0&&(contentType==null||contentType.isEmpty()))return new Frame(offset,0,true);
        if(contentType==null||contentType.isEmpty())
            throw new IOException("Аудиоданные Edge без Content-Type");
        String mime=contentType.split(";",2)[0].trim().toLowerCase(Locale.ROOT);
        if(!"audio/mpeg".equals(mime))
            throw new IOException("Edge вернул неподдерживаемый аудиоформат: "+mime);
        if(length==0)throw new IOException("Edge прислал пустой MP3-пакет");
        return new Frame(offset,length,false);
    }
}
