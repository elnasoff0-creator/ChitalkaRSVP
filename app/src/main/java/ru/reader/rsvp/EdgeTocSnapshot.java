package ru.reader.rsvp;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;

/** Persist the chapter titles once per source URI instead of parsing the entire book on every open.
 * All access happens on the manager's background executor. Files are private app cache data.
 */
final class EdgeTocSnapshot {
    private EdgeTocSnapshot(){}
    private static File file(Context context,String uri){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(uri.getBytes(StandardCharsets.UTF_8));
            StringBuilder name=new StringBuilder();for(byte b:digest)name.append(String.format(java.util.Locale.US,"%02x",b&255));
            File dir=new File(context.getFilesDir(),"edge_toc_snapshots");
            return new File(dir,name+".json");
        }catch(Exception error){return null;}
    }
    static ArrayList<String> load(Context context,String uri){
        File f=file(context,uri);if(f==null||!f.isFile()||f.length()>1024*1024)return null;
        try(FileInputStream in=new FileInputStream(f)){
            byte[] bytes=new byte[(int)f.length()];int offset=0,count;
            while(offset<bytes.length&&(count=in.read(bytes,offset,bytes.length-offset))>0)offset+=count;
            if(offset!=bytes.length)return null;
            JSONObject data=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(data.optInt("schema")!=1||!uri.equals(data.optString("uri")))return null;
            JSONArray list=data.getJSONArray("titles");ArrayList<String> result=new ArrayList<>();
            for(int i=0;i<list.length();i++)result.add(list.optString(i));
            return result;
        }catch(Exception ignored){return null;}
    }
    static void save(Context context,String uri,ArrayList<String> titles){
        File f=file(context,uri);if(f==null)return;
        try{
            JSONObject data=new JSONObject();data.put("schema",1);data.put("uri",uri);
            JSONArray list=new JSONArray();for(String title:titles)list.put(title);data.put("titles",list);
            byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);
            if(bytes.length>1024*1024)return;
            File dir=f.getParentFile();if(dir==null||(!dir.isDirectory()&&!dir.mkdirs()))return;
            File temp=new File(dir,f.getName()+".tmp");
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);out.getFD().sync();}
            if(!temp.renameTo(f)){if(f.exists())f.delete();temp.renameTo(f);}
        }catch(Exception ignored){}
    }
    static void remove(Context context,String uri){File f=file(context,uri);if(f!=null)f.delete();}
}
