package ru.reader.rsvp;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import java.io.IOException;

/** Media3 progressive MP3 source backed by Edge WebSocket byte delivery.
 * Bypasses the former complete-file-before-first-sound bottleneck.
 */
@UnstableApi
final class EdgeLiveDataSource extends BaseDataSource {
    private final EdgeLiveBuffer buffer;
    private Uri uri;
    private long position;
    private boolean opened;
    EdgeLiveDataSource(EdgeLiveBuffer buffer){super(false);this.buffer=buffer;}
    @Override public long open(DataSpec spec) throws IOException {
        uri=spec.uri;
        position=spec.position;
        opened=true;
        transferInitializing(spec);
        transferStarted(spec);
        return C.LENGTH_UNSET;
    }
    @Override public int read(byte[] dest,int offset,int length) throws IOException {
        if(!opened)throw new IOException("Edge audio source is closed");
        int n=buffer.read(position,dest,offset,length);
        if(n<0)return C.RESULT_END_OF_INPUT;
        position+=n;
        bytesTransferred(n);
        return n;
    }
    @Override public Uri getUri(){return uri;}
    @Override public void close(){
        uri=null;
        if(opened){opened=false;transferEnded();}
    }
    static final class Factory implements DataSource.Factory {
        private final EdgeLiveBuffer buffer;
        Factory(EdgeLiveBuffer buffer){this.buffer=buffer;}
        @Override public DataSource createDataSource(){return new EdgeLiveDataSource(buffer);}
    }
}
