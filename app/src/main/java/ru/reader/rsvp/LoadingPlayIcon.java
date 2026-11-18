package ru.reader.rsvp;

import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

/** A non-blocking ring around (never instead of) the original Play/Pause/download icon. */
final class LoadingPlayIcon {
    private LoadingPlayIcon() {}

    static void show(ImageView view, boolean waiting, int iconResource) {
        if (view == null) return;
        Object previous = view.getTag(R.id.loading_icon_state);
        Object previousResource = view.getTag(R.id.loading_icon_resource);
        if (previous instanceof Boolean && (Boolean) previous == waiting
                && previousResource instanceof Integer && (Integer) previousResource == iconResource) return;
        Drawable old = view.getDrawable();
        if (old instanceof Animatable) ((Animatable) old).stop();
        view.setTag(R.id.loading_icon_state, waiting);
        view.setTag(R.id.loading_icon_resource, iconResource);
        if (!waiting) {
            view.setImageResource(iconResource);
            view.setContentDescription(null);
            return;
        }
        Drawable center = view.getContext().getDrawable(iconResource);
        if (center == null) { view.setImageResource(iconResource); return; }
        center = center.mutate();
        float density = view.getResources().getDisplayMetrics().density;
        int color = Color.WHITE;
        ColorStateList tint = view.getImageTintList();
        if (tint != null) color = tint.getColorForState(view.getDrawableState(), tint.getDefaultColor());
        else {
            android.util.TypedValue resolved = new android.util.TypedValue();
            if (view.getContext().getTheme().resolveAttribute(android.R.attr.textColorPrimary, resolved, true)) {
                if (resolved.resourceId != 0) {
                    ColorStateList colors = view.getContext().getColorStateList(resolved.resourceId);
                    if (colors != null) color = colors.getDefaultColor();
                } else if (resolved.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                        && resolved.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) color = resolved.data;
            }
        }
        Ring ring = new Ring(center, density, color);
        view.setImageDrawable(ring);
        ring.start();
        view.setContentDescription("Ожидание: кнопка управления остаётся доступной");
    }

    private static final class Ring extends Drawable implements Animatable {
        private final Drawable center;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;
        private ValueAnimator rotation;
        private float degrees;
        Ring(Drawable center, float density, int color) {
            this.center = center;
            this.density = density;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(2.5f * density);
            paint.setColor(color);
            center.setTint(color);
        }
        @Override protected void onBoundsChange(Rect bounds) { super.onBoundsChange(bounds); }
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            float cx = b.exactCenterX(), cy = b.exactCenterY();
            float side = Math.min(b.width(), b.height());
            float radius = Math.max(0, side * 0.46f - paint.getStrokeWidth() / 2f);
            canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius,
                degrees - 90f, 265f, false, paint);
            // Keep a clear gap between the ring and the original control symbol.
            int half = Math.max(1, Math.round(side * 0.29f));
            center.setBounds(Math.round(cx) - half, Math.round(cy) - half,
                Math.round(cx) + half, Math.round(cy) + half);
            center.draw(canvas);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); center.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); center.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public void start() {
            if (rotation != null && rotation.isRunning()) return;
            rotation = ValueAnimator.ofFloat(0f, 360f);
            rotation.setDuration(950L);
            rotation.setInterpolator(new LinearInterpolator());
            rotation.setRepeatCount(ValueAnimator.INFINITE);
            rotation.addUpdateListener(a -> { degrees = (Float) a.getAnimatedValue(); invalidateSelf(); });
            rotation.start();
        }
        @Override public void stop() { if (rotation != null) { rotation.cancel(); rotation = null; } }
        @Override public boolean isRunning() { return rotation != null && rotation.isRunning(); }
    }
}
