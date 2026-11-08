package android.content;
public interface SharedPreferences {
    long getLong(String key,long def);
    Editor edit();
    interface Editor { Editor putLong(String key,long value); void apply(); }
}
