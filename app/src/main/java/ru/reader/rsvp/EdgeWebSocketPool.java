package ru.reader.rsvp;

import android.util.Log;
import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/** Four leaseable Edge transports, modeled after Quickify's bounded resource pool.
 * One lease owns at most one SSML turn; turn.end returns an OPEN socket to idle.
 * Failed/cancelled sockets are never reused. No authentication bypass or 429 retry.
 */
final class EdgeWebSocketPool {
    static final int CAPACITY=4;
    private static final OkHttpClient CLIENT=new OkHttpClient.Builder()
        .connectionPool(new ConnectionPool(4,5,TimeUnit.MINUTES))
        .connectTimeout(10,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS)
        .pingInterval(20,TimeUnit.SECONDS).build();
    private static final ArrayDeque<Lease> IDLE=new ArrayDeque<>();
    private static int allocated,sequence;
    static final class Lease {
        final int id;
        volatile WebSocket socket;
        volatile EdgeSpeechEngine owner;
        volatile boolean open,configured,dead;
        volatile String requestId="";
        Lease(int id){this.id=id;}
        boolean send(String text){WebSocket ws=socket;return ws!=null&&!dead&&ws.send(text);}
    }
    private EdgeWebSocketPool(){}
    static Lease acquire(EdgeSpeechEngine owner){
        Lease lease;
        synchronized(EdgeWebSocketPool.class){
            lease=null;
            while(!IDLE.isEmpty()){
                Lease candidate=IDLE.removeFirst();
                if(candidate.open&&!candidate.dead){lease=candidate;break;}
                retireLocked(candidate);
            }
            if(lease==null){
                if(allocated>=CAPACITY)throw new IllegalStateException("No Edge WebSocket lease available");
                lease=new Lease(++sequence);allocated++;
            }
            lease.owner=owner;
        }
        Log.d("EdgeSocketPool","acquired socketId="+lease.id+" reused="+lease.open);
        return lease;
    }
    static void connect(Lease lease,Request request){
        if(lease.open){EdgeSpeechEngine owner=lease.owner;if(owner!=null)owner.socketReady(lease,true);return;}
        try{
            WebSocket ws=CLIENT.newWebSocket(request,new WebSocketListener(){
                @Override public void onOpen(WebSocket w,Response response){
                    EdgeSpeechEngine owner;
                    synchronized(EdgeWebSocketPool.class){
                        if(lease.dead){w.cancel();return;}
                        lease.socket=w;lease.open=true;owner=lease.owner;
                    }
                    if(owner!=null)owner.socketReady(lease,false);
                }
                @Override public void onMessage(WebSocket w,String message){
                    EdgeSpeechEngine owner=lease.owner;
                    if(owner!=null)owner.socketText(lease,message);
                }
                @Override public void onMessage(WebSocket w,ByteString packet){
                    EdgeSpeechEngine owner=lease.owner;
                    if(owner!=null)owner.socketAudio(lease,packet);
                }
                @Override public void onFailure(WebSocket w,Throwable error,Response response){
                    EdgeSpeechEngine owner=lease.owner;
                    discard(lease,false);
                    if(owner!=null)owner.socketFailure(lease,error,response);
                }
                @Override public void onClosed(WebSocket w,int code,String reason){
                    EdgeSpeechEngine owner=lease.owner;
                    discard(lease,false);
                    if(owner!=null)owner.socketClosed(lease,code,reason);
                }
            });
            lease.socket=ws;
            if(lease.dead)ws.cancel();
        }catch(RuntimeException e){discard(lease,true);throw e;}
    }
    /** Called at turn.end, after the final MP3 metadata and bytes were received. */
    static void recycle(Lease lease){
        if(lease==null)return;
        synchronized(EdgeWebSocketPool.class){
            lease.owner=null;
            if(lease.open&&!lease.dead)IDLE.addLast(lease);
            else retireLocked(lease);
        }
    }
    /** Discard on failure/cancel; never return a stale socket to IDLE. */
    static void discard(Lease lease,boolean cancel){
        if(lease==null)return;
        WebSocket ws;
        synchronized(EdgeWebSocketPool.class){
            ws=lease.socket;
            retireLocked(lease);
        }
        if(cancel&&ws!=null)try{ws.cancel();}catch(Exception e){Log.w("EdgeSocketPool","Cancel failed: "+e);}
    }
    private static void retireLocked(Lease lease){
        if(lease.dead)return;
        lease.dead=true;lease.open=false;IDLE.remove(lease);
        if(allocated>0)allocated--;
    }
    static synchronized int allocatedForTest(){return allocated;}
    static synchronized int idleForTest(){return IDLE.size();}
}
