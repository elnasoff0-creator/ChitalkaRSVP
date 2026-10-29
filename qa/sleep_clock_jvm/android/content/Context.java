package android.content;
import java.util.*;
public class Context {
    private final FakePrefs shared=new FakePrefs();
    public SharedPreferences getSharedPreferences(String name,int mode){return shared;}
    static final class FakePrefs implements SharedPreferences {
        private final Map<String,Long> values=new HashMap<>();
        public long getLong(String k,long d){return values.getOrDefault(k,d);}
        public Editor edit(){return new Editor(){public Editor putLong(String k,long v){values.put(k,v);return this;}public void apply(){}};}
    }
}
