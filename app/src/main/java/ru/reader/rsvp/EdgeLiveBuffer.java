package ru.reader.rsvp;

import java.io.IOException;
import java.util.Arrays;

/** Single producer, one or more Media3 readers. Keeps encoded MP3 bytes for replay
 * and supports blocking reads while Edge streams, without decoding or temp files.
 * All waits happen on Media3 loader threads, never the Android main thread.
 */
final class EdgeLiveBuffer {
    private static final int MAX_SIZE = 12 * 1024 * 1024;
    private static final long DEFAULT_STALL_TIMEOUT_MS = 45000L;
    private final long stallTimeoutMs;
    EdgeLiveBuffer(){this(DEFAULT_STALL_TIMEOUT_MS);}
    /** Package-private shorter timeout supports deterministic regression tests. */
    EdgeLiveBuffer(long timeoutMs){stallTimeoutMs=Math.max(50L,timeoutMs);}
    private byte[] data = new byte[32 * 1024];
    private int size;
    private boolean ended;
    private IOException failure;

    synchronized void append(byte[] source,int from,int length) throws IOException {
        if (ended || failure != null) return;
        if (source == null || from < 0 || length < 0 || from > source.length - length)
            throw new IOException("Invalid Edge audio buffer range");
        if (length > MAX_SIZE - size) {
            fail(new IOException("Edge audio exceeds 12 MB"));
            throw failure;
        }
        if (size + length > data.length)
            data = Arrays.copyOf(data,Math.min(MAX_SIZE,Math.max(size+length,data.length*2)));
        System.arraycopy(source,from,data,size,length);
        size += length;
        notifyAll();
    }
    synchronized void complete() { ended=true;notifyAll(); }
    synchronized void fail(IOException e) {
        if(failure==null)failure=e==null?new IOException("Edge stream aborted"):e;
        notifyAll();
    }
    synchronized int available(){return size;}
    synchronized boolean finished(){return ended;}
    /** Returns -1 only after a normal end. Fails immediately if producer fails. */
    synchronized int read(long position,byte[] out,int offset,int length) throws IOException {
        if(length==0)return 0;
        if(position<0 || position>Integer.MAX_VALUE)throw new IOException("Bad MP3 position");
        int at=(int)position;
        final long waitStarted=System.nanoTime();
        while(at>=size && !ended && failure==null){
            long elapsedMs=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-waitStarted);
            long remainingMs=stallTimeoutMs-elapsedMs;
            if(remainingMs<=0){
                fail(new IOException("Edge audio stream stalled without new MP3 data"));
                break;
            }
            try{wait(Math.min(1000L,remainingMs));}catch(InterruptedException e){
                Thread.currentThread().interrupt();
                throw new IOException("Edge audio reader interrupted",e);
            }
        }
        if(failure!=null)throw failure;
        if(at>=size)return -1;
        int count=Math.min(length,size-at);
        System.arraycopy(data,at,out,offset,count);
        return count;
    }
}
