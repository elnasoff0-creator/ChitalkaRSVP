package android.app;
import android.content.DialogInterface;
public class AlertDialog implements DialogInterface {
    @Override public void dismiss() {}
    public static class Builder {
        public static String lastTitle;
        public static String[] lastOptions;
        public static int lastSelected;
        public static DialogInterface.OnClickListener lastListener;
        public Builder(Activity activity) {}
        public Builder setTitle(String title) { lastTitle=title; return this; }
        public Builder setSingleChoiceItems(String[] options, int selected, DialogInterface.OnClickListener listener) {
            lastOptions=options; lastSelected=selected; lastListener=listener; return this;
        }
        public Builder setNegativeButton(String title, DialogInterface.OnClickListener listener) { return this; }
        public AlertDialog show() { return new AlertDialog(); }
    }
}
