#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src/ru/reader/rsvp" "$TMP/classes"
cat > "$TMP/src/ru/reader/rsvp/TestEdgeWsFrames.java" <<'JAVA'
package ru.reader.rsvp;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
final class TestEdgeWsFrames {
  static byte[] packet(String header,byte[] payload){
    byte[] h=header.getBytes(StandardCharsets.US_ASCII);
    byte[] p=new byte[2+h.length+payload.length];
    p[0]=(byte)(h.length>>8);p[1]=(byte)h.length;
    System.arraycopy(h,0,p,2,h.length);System.arraycopy(payload,0,p,2+h.length,payload.length);
    return p;
  }
  static void check(boolean value,String detail){if(!value)throw new AssertionError(detail);}
  static void rejected(byte[] value,String message)throws Exception {
    try{EdgeWsFrames.audio(value);throw new AssertionError("Accepted invalid: "+message);}
    catch(IOException expected){check(expected.getMessage().contains(message),expected.getMessage());}
  }
  public static void main(String[] args)throws Exception{
    byte[] mp3=new byte[]{'I','D','3',4,0,0,0,0,0,0};
    EdgeWsFrames.Frame typical=EdgeWsFrames.audio(packet("Path:audio\r\nContent-Type:audio/mpeg\r\n",mp3));
    check(!typical.terminal&&typical.dataLength==mp3.length,"ordinary mp3");
    EdgeWsFrames.Frame spaced=EdgeWsFrames.audio(packet("Path: audio\r\nContent-Type: audio/mpeg\r\n",mp3));
    check(!spaced.terminal&&spaced.dataLength==mp3.length,"spaced headers");
    EdgeWsFrames.Frame variant=EdgeWsFrames.audio(packet("content-TYPE:\tAudio/MPEG; charset=binary\r\nPATH: audio\r\n",mp3));
    check(!variant.terminal&&variant.dataLength==mp3.length,"case and MIME params");
    EdgeWsFrames.Frame empty=EdgeWsFrames.audio(packet("Path:audio\r\n",new byte[0]));
    check(empty.terminal&&empty.dataLength==0,"valid empty terminal");
    rejected(packet("Path:audio\r\n",mp3),"без Content-Type");
    rejected(packet("Path:audio\r\nContent-Type: audio/webm\r\n",mp3),"audio/webm");
    rejected(packet("Path:audio\r\nContent-Type:audio/mpeg\r\n",new byte[0]),"пустой MP3");
    rejected(packet("Path:audio.metadata\r\nContent-Type:audio/mpeg\r\n",mp3),"тип бинарного");
    rejected(new byte[]{0,127,2},"длина заголовка");
    check("turn.end".equals(EdgeWsFrames.textPath("X-RequestId: 456\r\nPath: turn.end\r\n\r\n")),"spaced text finish");
    check("audio.metadata".equals(EdgeWsFrames.textPath("PATH:audio.metadata\n\n{ }")),"metadata path");
    check("{ }".equals(EdgeWsFrames.textBody("PATH:audio.metadata\n\n{ }")),"metadata body");
    System.out.println("PASS: 12 WebSocket frame scenarios, including the screenshot failure");
  }
}
JAVA
javac -encoding UTF-8 -d "$TMP/classes" \
  "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeWsFrames.java" \
  "$TMP/src/ru/reader/rsvp/TestEdgeWsFrames.java"
java -Dfile.encoding=UTF-8 -cp "$TMP/classes" ru.reader.rsvp.TestEdgeWsFrames
