package ru.reader.rsvp;
import android.content.Context;
import android.net.wifi.WifiManager;
import java.net.*;
import java.io.*;
import java.util.Arrays;

/** Minimal IPv4 mDNS A record responder; IP address remains the authoritative fallback. */
final class WebTransferMdns implements AutoCloseable {
    private static final byte[] NAME={11,'p','r','o','c','h','i','t','a','l','k','a',5,'l','o','c','a','l',0};
    private MulticastSocket socket;private WifiManager.MulticastLock lock;private volatile boolean ready;
    private InetAddress ip,group;private NetworkInterface iface;
    static WebTransferMdns start(Context c,String address,int port){WebTransferMdns m=new WebTransferMdns();try{
        m.ip=InetAddress.getByName(address);m.group=InetAddress.getByName("224.0.0.251");m.iface=NetworkInterface.getByInetAddress(m.ip);if(m.iface==null)return m;
        WifiManager wifi=(WifiManager)c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);if(wifi==null)return m;
        m.lock=wifi.createMulticastLock("webtransfer-mdns");m.lock.setReferenceCounted(false);m.lock.acquire();
        m.socket=new MulticastSocket(null);m.socket.setReuseAddress(true);m.socket.bind(new InetSocketAddress(5353));m.socket.setNetworkInterface(m.iface);m.socket.setTimeToLive(255);m.socket.joinGroup(new InetSocketAddress(m.group,5353),m.iface);
        Thread t=new Thread(m::loop,"WebTransfer-mDNS");t.setDaemon(true);m.ready=true;t.start();
    }catch(Exception e){m.close();}return m;}
    boolean available(){return ready;}
    private void loop(){byte[] data=new byte[4096];try{while(ready){DatagramPacket p=new DatagramPacket(data,data.length);socket.receive(p);int len=p.getLength();if(len<12)continue;
        // Only a matching uncompressed question; ignore malformed packets and arbitrary names.
        int flags=(data[2]&255)<<8|(data[3]&255);int questions=(data[4]&255)<<8|(data[5]&255);if((flags&0x8000)!=0||questions==0)continue;
        int offset=12;for(int q=0;q<questions&&offset<len;q++){
            int start=offset;while(offset<len&&(data[offset]&255)>0&&(data[offset]&255)<=63){offset+=1+(data[offset]&255);}if(offset>=len||data[offset]!=0||offset+5>len)break;
            int end=offset+1;int type=(data[end]&255)<<8|(data[end+1]&255),cls=(data[end+2]&255)<<8|(data[end+3]&255);offset=end+4;
            byte[] n=Arrays.copyOfRange(data,start,end);for(int i=0;i<n.length;i++)if(n[i]>='A'&&n[i]<='Z')n[i]=(byte)(n[i]+32);
            if(Arrays.equals(n,NAME)&&(type==1||type==255)&&(cls&0x7fff)==1){answer(p,(cls&0x8000)!=0);break;}
        }
    }}catch(Exception ignored){}finally{close();}}
    private void answer(DatagramPacket query,boolean unicast)throws IOException{
        boolean legacy=query.getPort()!=5353;ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(bytes);
        d.writeShort(legacy?((query.getData()[0]&255)<<8|(query.getData()[1]&255)):0);d.writeShort(0x8400);d.writeShort(legacy?1:0);d.writeShort(1);d.writeShort(0);d.writeShort(0);
        if(legacy){d.write(NAME);d.writeShort(1);d.writeShort(1);}d.write(NAME);d.writeShort(1);d.writeShort(legacy?1:0x8001);d.writeInt(legacy?10:120);d.writeShort(4);d.write(ip.getAddress());d.flush();byte[] answer=bytes.toByteArray();
        socket.send(new DatagramPacket(answer,answer.length,unicast||legacy?query.getAddress():group,unicast||legacy?query.getPort():5353));
    }
    public synchronized void close(){ready=false;if(socket!=null){socket.close();socket=null;}if(lock!=null){try{if(lock.isHeld())lock.release();}catch(Exception ignored){}lock=null;}}
}
