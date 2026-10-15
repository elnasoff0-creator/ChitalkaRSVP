package ru.reader.rsvp;

import android.app.Activity;
import android.content.Intent;

/** Back from a notification entry should still return to the library. */
final class BackNavigation {
    private BackNavigation() {}
    static void back(Activity activity) {
        if (activity.isTaskRoot()) {
            Intent home=new Intent(activity,MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(home);
        }
        activity.finish();
    }
}
