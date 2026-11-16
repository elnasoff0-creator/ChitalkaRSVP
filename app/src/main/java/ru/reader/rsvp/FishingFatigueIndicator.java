package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.util.AttributeSet;
import androidx.core.content.ContextCompat;

/** Non-interactive circular countdown above the fish; never inherits fish sprite rotation. */
public final class FishingFatigueIndicator extends View {
    private final Paint trackPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds=new RectF();
    private final Drawable icon;
    private float fraction=1f;

    public FishingFatigueIndicator(Context context) {
        this(context,null);
    }
    /** Required two-argument constructor for Android LayoutInflater. */
    public FishingFatigueIndicator(Context context, AttributeSet attributes) {
        super(context,attributes);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon=ContextCompat.getDrawable(context,R.drawable.ic_fishing_fatigue_debuff);
        trackPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        setVisibility(GONE);
    }
    void setPalette(int foreground, int accent) {
        // The ring uses the live accent, not a fixed blue or green; icon follows text color.
        trackPaint.setColor(Color.argb(70,Color.red(foreground),Color.green(foreground),Color.blue(foreground)));
        progressPaint.setColor(accent);
        if(icon!=null)icon.mutate().setTint(foreground);
        invalidate();
    }
    void setRingFraction(float value) {
        float f=Math.max(0f,Math.min(1f,value));
        if(fraction!=f){fraction=f;invalidate();}
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density=getResources().getDisplayMetrics().density;
        float stroke=2.25f*density;
        float inset=stroke*.75f;
        float side=Math.min(getWidth(),getHeight());
        float left=(getWidth()-side)/2f;
        float top=(getHeight()-side)/2f;
        arcBounds.set(left+inset,top+inset,left+side-inset,top+side-inset);
        trackPaint.setStrokeWidth(stroke);
        progressPaint.setStrokeWidth(stroke);
        canvas.drawArc(arcBounds,-90f,360f,false,trackPaint);
        if(fraction>0f)canvas.drawArc(arcBounds,-90f,360f*fraction,false,progressPaint);
        if(icon!=null) {
            int glyph=Math.round(side*.66f);
            int cx=getWidth()/2,cy=getHeight()/2;
            icon.setBounds(cx-glyph/2,cy-glyph/2,cx+(glyph+1)/2,cy+(glyph+1)/2);
            icon.draw(canvas);
        }
    }
}
