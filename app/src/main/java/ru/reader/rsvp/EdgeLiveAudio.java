package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/** One producer, one or more independent readers. Reading never waits for file writes.
 * Byte positions are in the raw MP3 elementary stream; a read waits for WebSocket frames.
 */
@UnstableApi
final class EdgeLiveAudio {
    static final class Buffer {
        private byte[] bytes=new byte[64*1024];
        private int length;
        private boolean finished;
        private String failure;
        synchronized void append(byte[] packet,int offset,int size){
            if(finished||size<=0)return;
            if(length+size>12*1024*1024){finish("MP3 exceeds 12 MB");return;}
            if(length+size>bytes.length)bytes=Arrays.copyOf(bytes,Math.max(length+size,bytes.length*2));
            System.arraycopy(packet,offset,bytes,length,size);length+=size;notifyAll();
        }
        synchronized void finish(String problem){finished=true;failure=problem;notifyAll();}
        synchronized int read(long from,byte[] destination,int off,int requested,Reader reader) throws IOException {
            while(from>=length&&!finished&&!reader.closed){
                try{wait(600);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Stream interrupted",e);}
            }
            if(reader.closed)return C.RESULT_END_OF_INPUT;
            if(from>=length){if(failure!=null)throw new IOException(failure);return C.RESULT_END_OF_INPUT;}
            int count=(int)Math.min(requested,length-from);
            System.arraycopy(bytes,(int)from,destination,off,count);return count;
        }
        synchronized void wake(){notifyAll();}
    }
    private static final class Reader extends BaseDataSource {
        private final ConcurrentHashMap<String,Buffer> active;
        private Buffer buffer;
        private Uri uri;
        private long offset;
        volatile boolean closed;
        Reader(ConcurrentHashMap<String,Buffer> active){super(false);this.active=active;}
        @Override public long open(DataSpec spec) throws IOException {
            closed=false;uri=spec.uri;offset=spec.position;
            buffer=active.get(uri.getLastPathSegment());
            if(buffer==null)throw new IOException("Stream no longer exists: "+uri);
            transferInitializing(spec);transferStarted(spec);
            return C.LENGTH_UNSET;
        }
        @Override public int read(byte[] out,int start,int length) throws IOException {
            if(length==0)return 0;
            if(buffer==null)throw new IOException("Stream is not open");
            int read=buffer.read(offset,out,start,length,this);
            if(read>0){offset+=read;bytesTransferred(read);}
            return read;
        }
        @Override public Uri getUri(){return uri;}
        @Override public void close(){closed=true;if(buffer!=null)buffer.wake();
            buffer=null;uri=null;transferEnded();}
    }
    static final class Factory implements DataSource.Factory {
        private final DataSource.Factory files;
        private final ConcurrentHashMap<String,Buffer> active=new ConcurrentHashMap<>();
        Factory(Context c){files=new DefaultDataSource.Factory(c);}
        void register(String id,Buffer data){active.put(id,data);}
        void unregister(String id){active.remove(id);}
        void clear(){active.clear();}
        @Override public DataSource createDataSource(){
            // A single DataSource instance can be opened only once at a time.
            return new DataSource(){
                DataSource delegate;
                @Override public long open(DataSpec spec) throws IOException {
                    delegate="edge-live".equals(spec.uri.getScheme())?new Reader(active):files.createDataSource();
                    return delegate.open(spec);
                }
                @Override public int read(byte[] b,int off,int len) throws IOException {return delegate.read(b,off,len);}
                @Override public Uri getUri(){return delegate==null?null:delegate.getUri();}
                @Override public void close() throws IOException {if(delegate!=null){delegate.close();delegate=null;}}
                @Override public void addTransferListener(androidx.media3.datasource.TransferListener listener){
                    // The delegate is constructed lazily on open; no traffic accounting is needed here.
                }
            };
        }
    }
}
