package ru.reader.rsvp;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

/** Per-book bookmarks; legacy word indexes are migrated once and retained. */
final class ReaderBookmarks {
    private ReaderBookmarks(){}
    static final class Item {
        int word;String title="",chapter="",excerpt="";long created;
        JSONObject json(){JSONObject o=new JSONObject();try{o.put("word",word).put("title",title).put("chapter",chapter).put("excerpt",excerpt).put("created",created);}catch(Exception ignored){}return o;}
        static Item from(JSONObject o){Item b=new Item();b.word=o.optInt("word",0);b.title=o.optString("title","");b.chapter=o.optString("chapter","");b.excerpt=o.optString("excerpt","");b.created=o.optLong("created",0);return b;}
    }
    static ArrayList<Item> list(Context ctx,String uri){SharedPreferences p=ctx.getSharedPreferences("reader",0);ArrayList<Item> out=new ArrayList<>();HashSet<Integer> seen=new HashSet<>();
        try{JSONArray a=new JSONArray(p.getString("bookmarks_v2:"+uri,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;Item b=Item.from(o);if(seen.add(b.word))out.add(b);}}catch(Exception ignored){}
        for(String legacy:p.getStringSet("bookmark:"+uri,Collections.emptySet()))try{int at=Integer.parseInt(legacy);if(seen.add(at)){Item item=new Item();item.word=at;item.title="Закладка";out.add(item);}}catch(Exception ignored){}
        out.sort((a,b)->Integer.compare(a.word,b.word));return out;
    }
    static void store(Context ctx,String uri,ArrayList<Item> list){JSONArray a=new JSONArray();for(Item b:list)a.put(b.json());
        ctx.getSharedPreferences("reader",0).edit().putString("bookmarks_v2:"+uri,a.toString()).remove("bookmark:"+uri).apply();
    }
    static void add(Context ctx,String uri,Item item){ArrayList<Item> all=list(ctx,uri);for(Item b:all)if(b.word==item.word)return;all.add(item);store(ctx,uri,all);}
    /** Fill legacy bookmarks using the already-open immutable reader text. No book reload. */
    static void fillMissingExcerpts(Context ctx,String uri,String body,ReadingWordMap words){
        if(body==null||body.isEmpty()||words==null||words.size()==0)return;
        ArrayList<Item> all=list(ctx,uri);boolean updated=false;
        for(Item item:all){
            if(item.excerpt!=null&&!item.excerpt.trim().isEmpty())continue;
            if(item.word<0||item.word>=words.size())continue;
            int start=Math.min(body.length(),words.starts[item.word]);
            int end=Math.min(body.length(),start+235);
            int nextLine=body.indexOf('\n',start);
            if(nextLine>start&&nextLine<end)end=nextLine;
            String extracted=body.substring(start,end).replaceAll("\\s+"," ").trim();
            if(!extracted.isEmpty()){
                item.excerpt=extracted+(end<body.length()&&end==start+235?"…":"");
                updated=true;
            }
        }
        if(updated)store(ctx,uri,all);
    }
}
