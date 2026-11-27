package ru.reader.rsvp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Shared browser tabs: selectable top-rounded tabs attached to the same content surface.
 * For 2-5 tabs every tab has an equal share of the *actual* screen width, never a
 * wrap-content scroll row that can put the second tab outside the viewport.
 * Tab labels remain accessible even in icon-only mode.
 */
final class BrowserTabs {
    private BrowserTabs(){}
    private static final java.util.WeakHashMap<Context,java.lang.ref.WeakReference<HorizontalScrollView>> latest=new java.util.WeakHashMap<>();
    private static int dp(Context c,int v){return Math.round(c.getResources().getDisplayMetrics().density*v);}
    private static GradientDrawable top(Context c,int color,int radius){
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);
        float r=dp(c,radius);shape.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});return shape;
    }
    /** Top-and-side only stroke: bottom remains open to the selected panel. */
    private static Drawable activeTab(Context context,int fill,int stroke){
        return new Drawable(){
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas canvas){
                Rect b=getBounds();float r=dp(context,13),half=dp(context,2)/2f;
                float l=b.left+half,t=b.top+half,x=b.right-half,bottom=b.bottom;
                Path shape=new Path();shape.moveTo(l,bottom);shape.lineTo(l,t+r);
                shape.quadTo(l,t,l+r,t);shape.lineTo(x-r,t);shape.quadTo(x,t,x,t+r);
                shape.lineTo(x,bottom);shape.close();paint.setStyle(Paint.Style.FILL);paint.setColor(fill);canvas.drawPath(shape,paint);
                Path topLine=new Path();topLine.moveTo(l,bottom);topLine.lineTo(l,t+r);
                topLine.quadTo(l,t,l+r,t);topLine.lineTo(x-r,t);topLine.quadTo(x,t,x,t+r);
                topLine.lineTo(x,bottom);paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(context,2));paint.setColor(stroke);canvas.drawPath(topLine,paint);
            }
            @Override public void setAlpha(int alpha){}
            @Override public void setColorFilter(android.graphics.ColorFilter filter){}
            @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };
    }
    static HorizontalScrollView strip(Context context,String[] names,int active,IntConsumer clicked){
        return build(context,names,null,active,clicked);
    }
    static HorizontalScrollView iconStrip(Context context,String[] names,int[] icons,int active,IntConsumer clicked){
        if(icons==null||icons.length!=names.length)throw new IllegalArgumentException("One icon per tab is required");
        return build(context,names,icons,active,clicked);
    }
    private static HorizontalScrollView build(Context context,String[] names,int[] icons,int active,IntConsumer clicked){
        final int bg=UiColorScheme.background(context),panel=UiColorScheme.neutral(context),accent=UiColorScheme.accent(context);
        final boolean dark=UiColorScheme.dark(context),equal=names.length>=1&&names.length<=5;
        final int ink=UiColorScheme.prefs(context).getInt("fg",dark?Color.WHITE:0xff30221e);
        HorizontalScrollView scroll=new HorizontalScrollView(context);
        scroll.setTag(active);latest.put(context,new java.lang.ref.WeakReference<>(scroll));
        scroll.setHorizontalScrollBarEnabled(false);scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        final int height=icons==null?52:56;
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(context,height)));
        LinearLayout row=new LinearLayout(context);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.BOTTOM);
        // MATCH_PARENT is essential for weighted equal-width tabs inside HorizontalScrollView.
        scroll.addView(row,new HorizontalScrollView.LayoutParams(
            equal?ViewGroup.LayoutParams.MATCH_PARENT:ViewGroup.LayoutParams.WRAP_CONTENT,dp(context,height)));
        for(int i=0;i<names.length;i++){
            final int index=i;final boolean chosen=i==active;
            LinearLayout tab=new LinearLayout(context);tab.setGravity(Gravity.CENTER);tab.setOrientation(LinearLayout.VERTICAL);
            tab.setPadding(dp(context,6),dp(context,3),dp(context,6),0);
            int passive=UiColorScheme.blend(bg,panel,.48f);
            tab.setBackground(chosen?activeTab(context,panel,accent):top(context,passive,14));
            tab.setContentDescription(names[i]);tab.setSelected(chosen);tab.setClickable(true);
            if(icons==null){
                TextView label=new TextView(context);label.setText(names[i]);label.setSingleLine(true);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                label.setTextSize(15);label.setTextColor(ink);label.setGravity(Gravity.CENTER);
                if(chosen)label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                tab.addView(label,new LinearLayout.LayoutParams(-1,0,1));
            }else{
                ImageView image=new ImageView(context);image.setImageResource(icons[i]);
                image.setColorFilter(chosen?accent:ink);
                image.setContentDescription(names[i]);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(context,25),dp(context,25));
                iconParams.gravity=Gravity.CENTER;tab.addView(image,iconParams);
                // Center the icon within all available height; never use a weighted trailing spacer.
            }
            int tabWidth=icons!=null?dp(context,66):dp(context,92);
            LinearLayout.LayoutParams lp=equal?new LinearLayout.LayoutParams(0,dp(context,height),1f)
                :new LinearLayout.LayoutParams(tabWidth,dp(context,height));
            if(i>0)lp.leftMargin=dp(context,3);
            row.addView(tab,lp);
            tab.setOnClickListener(v->{if(index!=active)clicked.accept(index);});
        }
        return scroll;
    }
    static void fixedViewport(Context context,View viewport,View pane){
        pane.setForeground(null);viewport.setForeground(panelOutline(context,viewport));
    }
    private static Drawable panelOutline(Context context,View panel){return new Drawable(){
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas){
            Rect b=getBounds();float half=dp(context,2)/2f,l=b.left+half,r=b.right-half,t=b.top+half,bot=b.bottom-half;
            float start=l,end=l;java.lang.ref.WeakReference<HorizontalScrollView> ref=latest.get(context);
            HorizontalScrollView strip=ref==null?null:ref.get();
            if(strip!=null&&strip.getChildCount()>0&&strip.getTag() instanceof Integer){
                ViewGroup row=(ViewGroup)strip.getChildAt(0);int index=(Integer)strip.getTag();
                if(index>=0&&index<row.getChildCount()){
                    View tab=row.getChildAt(index);int[] a=new int[2],p=new int[2];tab.getLocationInWindow(a);panel.getLocationInWindow(p);
                    start=Math.max(l,Math.min(r,a[0]-p[0]+half));end=Math.max(l,Math.min(r,a[0]-p[0]+tab.getWidth()-half));
                }
            }
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(context,2));paint.setColor(UiColorScheme.accent(context));
            Path path=new Path();path.moveTo(start,t);path.lineTo(l,t);path.lineTo(l,bot);path.lineTo(r,bot);path.lineTo(r,t);path.lineTo(end,t);canvas.drawPath(path,paint);
        }
        @Override public void setAlpha(int a){} @Override public void setColorFilter(android.graphics.ColorFilter f){} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    };}
    static LinearLayout panel(Context context){
        LinearLayout pane=new LinearLayout(context);pane.setOrientation(LinearLayout.VERTICAL);
        pane.setPadding(dp(context,9),dp(context,10),dp(context,9),dp(context,10));
        GradientDrawable shape=new GradientDrawable();shape.setColor(UiColorScheme.neutral(context));
        pane.setBackgroundColor(UiColorScheme.neutral(context));
        pane.setForeground(panelOutline(context,pane));
        return pane;
    }
}
