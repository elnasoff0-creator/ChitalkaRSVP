package ru.reader.rsvp;
import android.content.Context;
import android.view.View;
import android.widget.ImageView;
/** Shared right-aligned selection marker for TTS choice dialogs. */
final class TtsSelectionMark {
    private TtsSelectionMark(){}
    static ImageView view(Context context,boolean chosen){
        ImageView icon=new ImageView(context);
        if(chosen)icon.setImageResource(R.drawable.ic_tts_selected_circle);
        else icon.setVisibility(View.INVISIBLE);
        icon.setContentDescription(chosen?"Выбрано":"");
        return icon;
    }
}
