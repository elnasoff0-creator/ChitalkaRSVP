package ru.reader.rsvp;
import android.app.Activity;
import android.os.Build;
import android.view.View;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
/** Keep every Activity below the system bars, including Android 15 edge-to-edge. */
public final class SafeSystemBars {
    private SafeSystemBars() {}
    public static void install(Activity activity) {
        // Before Android 15, non-immersive windows already fit content below bars.
        // Extra padding there would count system insets twice.
        if(Build.VERSION.SDK_INT<35)return;
        final View content=activity.findViewById(android.R.id.content);
        if(content==null)return;
        final int left=content.getPaddingLeft(),top=content.getPaddingTop();
        final int right=content.getPaddingRight(),bottom=content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content,(view,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            view.setPadding(left+bars.left,top+bars.top,right+bars.right,bottom+bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }
}
