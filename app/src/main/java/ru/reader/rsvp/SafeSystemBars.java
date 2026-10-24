package ru.reader.rsvp;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Edge-to-edge background that continues behind BOTH Android system bars.
 * The content receives safe insets; the background never receives them.
 */
public final class SafeSystemBars {
    private SafeSystemBars() {}

    public static void matchBackground(Activity activity, int color) {
        Window window = activity.getWindow();
        View decor = window.getDecorView();
        View content = activity.findViewById(android.R.id.content);
        // Paint the entire window, including the inset strips outside the activity root.
        decor.setBackgroundColor(color);
        if (content != null) content.setBackgroundColor(color);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        final boolean darkIcons = (Color.red(color) * 299L + Color.green(color) * 587L
                + Color.blue(color) * 114L) >= 150000;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decor);
        controller.setAppearanceLightStatusBars(darkIcons);
        controller.setAppearanceLightNavigationBars(darkIcons);
    }

    /** Called once, after an Activity has attached its content view. */
    public static void install(Activity activity) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        final int left = content.getPaddingLeft(), top = content.getPaddingTop();
        final int right = content.getPaddingRight(), bottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(left + safe.left, top + safe.top,
                right + safe.right, bottom + safe.bottom);
            return insets;
        });
        matchBackground(activity, activity.getSharedPreferences("reader", 0)
                .getInt("bg", 0xfff3e7d5));
        ViewCompat.requestApplyInsets(content);
    }
}
