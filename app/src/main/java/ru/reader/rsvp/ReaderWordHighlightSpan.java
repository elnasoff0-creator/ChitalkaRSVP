package ru.reader.rsvp;

import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;

/** Pure draw-state word background: opacity changes never reflow TextView Layout. */
final class ReaderWordHighlightSpan extends CharacterStyle implements UpdateAppearance {
    private final int rgb;
    private int alpha;
    private final int baseAlpha;
    ReaderWordHighlightSpan(int color){this(color,100);}
    ReaderWordHighlightSpan(int color,int opacityPercent){rgb=color&0x00ffffff;baseAlpha=Math.round(255f*Math.max(0,Math.min(100,opacityPercent))/100f);alpha=baseAlpha;}
    void setOpacity(float opacity){alpha=Math.max(0,Math.min(255,Math.round(baseAlpha*opacity)));}
    @Override public void updateDrawState(TextPaint paint){paint.bgColor=(alpha<<24)|rgb;}
}
