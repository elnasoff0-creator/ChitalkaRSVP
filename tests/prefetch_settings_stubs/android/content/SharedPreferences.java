package android.content;
public interface SharedPreferences {
    int getInt(String key, int defaultValue);
    Editor edit();
    interface Editor {
        Editor putInt(String key, int value);
        void apply();
    }
}
