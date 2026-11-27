package ru.reader.rsvp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.SystemClock;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Isolated ephemeral same-Wi-Fi HTTP endpoint: no remote library read, listing or deletion. */
final class WebTransferServer {
    private final Context app;
    private final String ip,password;
    private final Set<String> sessions=Collections.newSetFromMap(new ConcurrentHashMap<String,Boolean>());
    private volatile WebTransferMdns mdns;
    private final int port;
    private final ServerSocket listener;
    private final ExecutorService requests=Executors.newFixedThreadPool(3,r->{Thread t=new Thread(r,"WebTransfer-HTTP");t.setDaemon(true);return t;});
    private final AtomicInteger inflight=new AtomicInteger();
    private final Set<Socket> sockets=Collections.newSetFromMap(new ConcurrentHashMap<Socket,Boolean>());
    private final java.util.concurrent.ConcurrentHashMap<String,Socket> transfers=new java.util.concurrent.ConcurrentHashMap<>();
    private final int idleMinutes;
    private volatile boolean running=true;
    private volatile long lastActivity=SystemClock.elapsedRealtime();
    private volatile String latest="Ожидание файлов";
    private int invalidPasswords=0;
    private long blockedUntil=0;
    private Thread accepting;
    private WebTransferServer(Context c,String ipv4,int listenPort)throws IOException{
        app=c.getApplicationContext();ip=ipv4;idleMinutes=WebTransferPrefs.idleMinutes(c);
        try{password=WebTransferAuth.password(c);}catch(Exception e){throw new IOException("Не удалось открыть защищённый пароль",e);}
        try{listener=new ServerSocket(listenPort,8,InetAddress.getByName(ip));}catch(java.net.BindException e){throw new IOException("Порт занят. Измените постоянный порт или включите динамический в Настройки → Онлайн.",e);}port=listener.getLocalPort();
    }
    static String wifiIpv4(Context c){
        ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null)return null;
        Network network=cm.getActiveNetwork();if(network==null)return null;
        NetworkCapabilities caps=cm.getNetworkCapabilities(network);
        if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return null;
        LinkProperties lp=cm.getLinkProperties(network);if(lp==null)return null;
        for(LinkAddress address:lp.getLinkAddresses()){
            InetAddress a=address.getAddress();
            if(a instanceof Inet4Address&&!a.isAnyLocalAddress()&&!a.isLoopbackAddress()&&!a.isLinkLocalAddress())
                return a.getHostAddress();
        }
        return null;
    }
    static WebTransferServer start(Context context)throws IOException{
        File old=new File(context.getCacheDir(),"webtransfer_staging");
        File[] candidates=old.listFiles();
        if(candidates!=null)for(File candidate:candidates){
            if(candidate.isFile()&&candidate.getName().startsWith("incoming_")
                &&candidate.getName().endsWith(".part")
                &&System.currentTimeMillis()-candidate.lastModified()>3600000L)
                candidate.delete();
        }
        String ip=wifiIpv4(context);
        if(ip==null)throw new IOException("Подключите устройство к Wi-Fi и повторите попытку");
        WebTransferServer s=new WebTransferServer(context,ip,WebTransferPrefs.port(context));
        s.accepting=new Thread(s::acceptLoop,"WebTransfer-Accept");s.accepting.setDaemon(true);s.accepting.start();s.mdns=WebTransferMdns.start(context,ip,s.port);return s;
    }
    String address(){return "http://"+ip+":"+port;}
    String password(){return password;}
    String localAddress(){return mdns!=null&&mdns.available()?"http://prochitalka.local:"+port:"";}
    boolean running(){return running;}
    int activeUploads(){return inflight.get();}
    int connections(){return sockets.size();}
    String latest(){return latest;}
    long idleMs(){return SystemClock.elapsedRealtime()-lastActivity;}
    int idleMinutes(){return idleMinutes;}
    boolean wifiSame(){return ip.equals(wifiIpv4(app));}
    boolean cancelTransfer(String id){
        Socket uploading=transfers.get(id);
        if(uploading==null||!WebTransferDownloadStore.cancel(app,id))return false;
        try{uploading.close();}catch(IOException ignored){}
        return true;
    }
    void close(){
        running=false;if(mdns!=null)mdns.close();sessions.clear();
        WebTransferDownloadStore.interruptActive(app,"Wi-Fi-сеанс завершён до окончания передачи");try{listener.close();}catch(IOException ignored){}
        for(Socket s:sockets)try{s.close();}catch(IOException ignored){}
        requests.shutdownNow();
    }
    private void acceptLoop(){
        while(running){
            try{
                Socket socket=listener.accept();socket.setSoTimeout(45000);
                if(sockets.size()>=8){socket.close();continue;}
                sockets.add(socket);
                try{requests.execute(()->handle(socket));}
                catch(RuntimeException rejected){sockets.remove(socket);socket.close();}
            }catch(SocketException closed){break;}
            catch(Exception error){if(!running)break;}
        }
    }
    private static String readLine(InputStream in,int max)throws IOException{
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();int n;
        while((n=in.read())!=-1){if(n==10)break;if(n!=13){if(bytes.size()>=max)throw new IOException("HTTP line too large");bytes.write(n);}}
        if(n<0&&bytes.size()==0)return null;
        return bytes.toString("ISO-8859-1");
    }
    private static String cleanValue(String s){return s==null?"":s.trim();}
    private boolean authenticated(Map<String,String> headers){
        String cookie=headers.get("cookie");if(cookie==null)return false;
        for(String part:cookie.split(";")){String v=part.trim();if(v.startsWith("wt_session=")){String token=v.substring(11);if(sessions.contains(token)||WebTransferAuth.trusted(app,token))return true;}}
        return false;
    }
    private void handle(Socket socket){
        try(Socket client=socket;InputStream in=new BufferedInputStream(client.getInputStream());OutputStream out=new BufferedOutputStream(client.getOutputStream())){
            if(!running||!wifiSame()){respond(out,503,"application/json",json("Wi-Fi недоступен"),null);return;}
            String first=readLine(in,4096);if(first==null)return;
            String[] req=first.split(" ");
            if(req.length!=3||!"HTTP/1.1".equals(req[2])||!("GET".equals(req[0])||"POST".equals(req[0]))){respond(out,400,"application/json",json("Недопустимый запрос"),null);return;}
            String path=req[1];if(path.length()>256||!path.startsWith("/")||path.indexOf('?')>=0){respond(out,404,"text/plain",new byte[0],null);return;}
            Map<String,String> headers=new HashMap<>();int size=first.length();String line;
            while((line=readLine(in,8192))!=null&&!line.isEmpty()){
                size+=line.length();if(size>24576||headers.size()>40)throw new IOException("Слишком много заголовков");
                int colon=line.indexOf(':');if(colon<1)throw new IOException("Некорректный заголовок");
                String key=line.substring(0,colon).trim().toLowerCase(Locale.ROOT);
                if(headers.putIfAbsent(key,cleanValue(line.substring(colon+1)))!=null)throw new IOException("Повторный заголовок");
            }
            String host=headers.get("host");
            if(host==null||!(host.equals(ip+":"+port)||(!localAddress().isEmpty()&&host.equals("prochitalka.local:"+port)))){respond(out,403,"text/plain",new byte[0],null);return;}
            if("GET".equals(req[0])&&("/".equals(path)||"/index.html".equals(path))){
                try(InputStream asset=app.getAssets().open("webtransfer/index.html")){
                    java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
                    while((n=asset.read(buf))!=-1)bytes.write(buf,0,n);
                    respond(out,200,"text/html; charset=utf-8",bytes.toByteArray(),null);
                }return;
            }
            if(!"POST".equals(req[0])){respond(out,404,"text/plain",new byte[0],null);return;}
            // Browser must issue same-origin requests; no wildcard CORS and no cross-site uploads.
            String origin=headers.get("origin");
            if(!WebTransferPolicy.sameOrigin(host,origin,"http://"+host)||!"1".equals(headers.get("x-wt-request"))){respond(out,403,"application/json",json("Запрос с другого сайта запрещён"),null);return;}
            if("/api/pair".equals(path)){
                if(!"0".equals(headers.getOrDefault("content-length","0"))){respond(out,400,"application/json",json("Неверный запрос"),null);return;}
                boolean allowed;
                synchronized(this){
                    long now=SystemClock.elapsedRealtime();
                    String supplied=headers.get("x-wt-password");
                    allowed=now>=blockedUntil&&supplied!=null&&WebTransferAuth.equal(password,URLDecoder.decode(supplied,"UTF-8"));
                    if(!allowed){invalidPasswords++;if(invalidPasswords>=5){blockedUntil=now+60000L;invalidPasswords=0;}}
                    else {invalidPasswords=0;blockedUntil=0;lastActivity=now;}
                }
                if(!allowed){respond(out,403,"application/json",json("Неверный пароль или временная блокировка"),null);return;}
                String token=WebTransferAuth.random(32);boolean remember="1".equals(headers.get("x-wt-remember"));
                if(remember)WebTransferAuth.remember(app,token);else {if(sessions.size()>=128)sessions.clear();sessions.add(token);}
                respond(out,200,"application/json",("{\"ok\":true}").getBytes(StandardCharsets.UTF_8),
                    "Set-Cookie: wt_session="+token+"; HttpOnly; SameSite=Strict; Path=/"+(remember?"; Max-Age=2592000":"")+"\r\n");return;
            }
            if(!authenticated(headers)){respond(out,401,"application/json",json("Введите пароль с устройства"),null);return;}
            if("/api/status".equals(path)){
                String response="{\"ok\":true,\"active\":"+inflight.get()+"}";
                respond(out,200,"application/json",response.getBytes(StandardCharsets.UTF_8),null);return;
            }
            if("/api/cancel".equals(path)){
                String cancelId=headers.get("x-wt-transfer-id");
                if(cancelId==null||!cancelId.startsWith("wifi:")||!"0".equals(headers.getOrDefault("content-length","0"))){
                    respond(out,400,"application/json",json("Неверный ID передачи"),null);return;
                }
                boolean stopped=cancelTransfer(cancelId);
                respond(out,stopped?200:404,"application/json",
                    stopped?"{\"ok\":true}".getBytes(StandardCharsets.UTF_8):json("Передача завершена или не найдена"),null);
                return;
            }
            if(!"/api/upload".equals(path)&&!"/api/folder".equals(path)){respond(out,404,"application/json",json("Адрес не найден"),null);return;}
            if(inflight.incrementAndGet()>2){inflight.decrementAndGet();respond(out,429,"application/json",json("Одновременно можно передавать не более двух файлов"),null);return;}
            File temp=null;
            String transferId=null;
            boolean registered=false;
            try{
                if("/api/folder".equals(path)){
                    String id=headers.get("x-wt-transfer-id");
                    if(id==null||!id.matches("wifi:[A-Za-z0-9_-]{10,90}"))throw new IOException("Неверный ID передачи");
                    WebTransferUploadManager.Outcome result=WebTransferFolder.receive(app,in,headers,id,client,transfers,()->running,()->lastActivity=SystemClock.elapsedRealtime());
                    latest="Добавлено: "+result.name;WebTransferService.notifyCompleted(app,result.name);
                    JSONObject response=new JSONObject();response.put("ok",true);response.put("name",result.name);response.put("bytes",result.bytes);
                    respond(out,200,"application/json",response.toString().getBytes(StandardCharsets.UTF_8),null);return;
                }
                long length=WebTransferPolicy.length(headers.getOrDefault("content-length","-1"),
                    headers.get("transfer-encoding"),WebTransferUploadManager.MAX_BYTES);
                String raw=headers.get("x-wt-filename");if(raw==null||raw.length()>700)throw new IOException("Не указано имя файла");
                String fileName=WebTransferUploadManager.sanitize(URLDecoder.decode(raw,"UTF-8"));
                transferId=headers.get("x-wt-transfer-id");
                if(transferId==null||transferId.isEmpty())transferId=WebTransferDownloadStore.newId();
                if(!transferId.matches("wifi:[A-Za-z0-9_-]{10,90}"))throw new IOException("Неверный ID передачи");
                if(!WebTransferDownloadStore.begin(app,transferId,fileName,"",length))
                    throw new IOException("Задание передачи уже существует или недоступно");
                registered=true;
                transfers.put(transferId,client);
                String type=WebTransferUploadManager.category(fileName);
                if(!DownloadFolderPicker.available(app,type))throw new IOException("Выберите папку "+DownloadFolderPicker.label(type)+" в настройках приложения");
                File dir=new File(app.getCacheDir(),"webtransfer_staging");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Нет места для временных файлов");
                if(dir.getUsableSpace()<length+16L*1024*1024)throw new IOException("Недостаточно свободного места");
                temp=File.createTempFile("incoming_",".part",dir);
                try(FileOutputStream file=new FileOutputStream(temp)){
                    byte[] buffer=new byte[65536];long left=length;
                    while(left>0){
                        if(!running)throw new IOException("Сервер остановлен");
                        if(WebTransferDownloadStore.cancelled(app,transferId))throw new IOException("Передача отменена");
                        int n=in.read(buffer,0,(int)Math.min(buffer.length,left));if(n<0)throw new IOException("Передача прервана");
                        file.write(buffer,0,n);left-=n;lastActivity=SystemClock.elapsedRealtime();
                        latest=fileName+" · получено "+((length-left)*100/length)+"%";
                        WebTransferDownloadStore.progress(app,transferId,length-left,length);
                    }
                    file.getFD().sync();
                }
                if(!running)throw new IOException("Сервер остановлен");
                if(WebTransferDownloadStore.cancelled(app,transferId))throw new IOException("Передача отменена");
                WebTransferDownloadStore.importing(app,transferId);
                latest=fileName+" · сохранение через SAF…";
                WebTransferUploadManager.Outcome result=WebTransferUploadManager.commit(app,temp,fileName,headers.get("x-wt-collision"));
                WebTransferDownloadStore.finish(app,transferId,WebTransferDownloadStore.DONE,"",result.uri,length);
                WebTransferService.notifyCompleted(app,result.name);
                latest="Добавлено: "+result.name;
                JSONObject response=new JSONObject();response.put("ok",true);response.put("name",result.name);response.put("bytes",result.bytes);
                respond(out,200,"application/json",response.toString().getBytes(StandardCharsets.UTF_8),null);
            }catch(Exception error){
                String message=error.getMessage()==null?"Ошибка импорта":error.getMessage();
                latest="Ошибка передачи: "+message;
                int code=message.startsWith("DUPLICATE:")||message.startsWith("DUPLICATE_SKIP:")?409:400;
                if(registered){
                    String state=WebTransferDownloadStore.cancelled(app,transferId)?WebTransferDownloadStore.CANCELLED:
                        code==409?WebTransferDownloadStore.DUPLICATE:WebTransferDownloadStore.ERROR;
                    WebTransferDownloadStore.finish(app,transferId,state,message.replace("DUPLICATE:","").replace("DUPLICATE_SKIP:",""),"",-1);
                }
                byte[] errorPayload=json(message.replace("DUPLICATE:","").replace("DUPLICATE_SKIP:",""));
                String marker=message.startsWith("DUPLICATE_SKIP:")?"X-WT-Skip: 1\r\n":null;
                respond(out,code,"application/json",errorPayload,marker);
            }finally{
                if(registered)transfers.remove(transferId,client);
                if(temp!=null)temp.delete();inflight.decrementAndGet();lastActivity=SystemClock.elapsedRealtime();
            }
        }catch(Exception ignored){}finally{sockets.remove(socket);}
    }
    private static byte[] json(String error){
        try{return new JSONObject().put("ok",false).put("error",error).toString().getBytes(StandardCharsets.UTF_8);}
        catch(Exception e){return "{\"ok\":false}".getBytes(StandardCharsets.UTF_8);}
    }
    private static void respond(OutputStream out,int status,String mime,byte[] data,String extra)throws IOException {
        String reason=status==200?"OK":status==400?"Bad Request":status==401?"Unauthorized":status==403?"Forbidden":status==404?"Not Found":status==409?"Conflict":status==429?"Too Many Requests":"Service Unavailable";
        String header="HTTP/1.1 "+status+" "+reason+"\r\nContent-Type: "+mime+"\r\nContent-Length: "+data.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; form-action 'none'; base-uri 'none'\r\n";
        if(extra!=null)header+=extra;
        out.write((header+"\r\n").getBytes(StandardCharsets.ISO_8859_1));out.write(data);out.flush();
    }
}
