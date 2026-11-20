package ru.reader.rsvp;

import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;

/** Pure draw-state word background: opacity changes never reflow TextView Layout. */
final class ReaderWordHighlightSpan extends CharacterStyle implements UpdateAppearance {
    private final int rgb;
    private int alpha;
    ReaderWordHighlightSpan(int color){rgb=color&0x00ffffff;alpha=255;}
    void setOpacity(float opacity){alpha=Math.max(0,Math.min(255,Math.round(255f*opacity)));}
    @Override public void updateDrawState(TextPaint paint){paint.bgColor=(alpha<<24)|rgb;}
}
