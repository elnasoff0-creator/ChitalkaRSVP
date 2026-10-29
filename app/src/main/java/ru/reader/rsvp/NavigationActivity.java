package ru.reader.rsvp;

import android.app.Activity;

/** Shared navigation host: drawer opens by explicit menu button, never edge swipe.
 * Leftward dismiss gesture remains inside UniversalDrawer.SwipeShade.
 */
public class NavigationActivity extends Activity {
    @Override protected void onDestroy(){UniversalDrawer.discard(this);super.onDestroy();}
    @Override public void onBackPressed(){
        if(UniversalDrawer.closeIfOpen(this))return;
        super.onBackPressed();
    }
}
