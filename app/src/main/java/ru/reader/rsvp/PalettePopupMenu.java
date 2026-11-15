package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/** PopupMenu-compatible, runtime-palette surface. Native XML popup backgrounds cannot
 * follow a user-selected color; this window resolves background/foreground on every show.
 */
final class PalettePopupMenu extends PopupMenu {
    private final Context context;
    private final View anchor;
    private Menu delegated;
    private int nextAutoId=2000000;
    private PopupWindow surface;
    PalettePopupMenu(Context c,View anchor){super(c,anchor);this.context=c;this.anchor=anchor;}
    @Override public Menu getMenu(){
        if(delegated!=null)return delegated;
        final Menu original=super.getMenu();
        delegated=(Menu)Proxy.newProxyInstance(context.getClassLoader(),new Class<?>[]{Menu.class},
            (proxy,method,args)->{
                if("add".equals(method.getName())&&args!=null){
                    if(args.length==1){
                        CharSequence title=args[0] instanceof Integer?
                            context.getText((Integer)args[0]):(CharSequence)args[0];
                        return original.add(0,nextAutoId++,Menu.NONE,title);
                    }
                    if(args.length==4){
                        int group=(Integer)args[0],itemId=(Integer)args[1],order=(Integer)args[2];
                        CharSequence title=args[3] instanceof Integer?
                            context.getText((Integer)args[3]):(CharSequence)args[3];
                        return original.add(group,itemId==0?nextAutoId++:itemId,order,title);
                    }
                }
                try{return method.invoke(original,args);}
                catch(InvocationTargetException wrapped){throw wrapped.getTargetException();}
            });
        return delegated;
    }
    private int dp(float n){return (int)(context.getResources().getDisplayMetrics().density*n+.5f);}
    @Override public void show(){
        final Menu menu=super.getMenu();
        if(menu.size()==0||anchor.getWindowToken()==null)return;
        int bg=UiColorScheme.neutral(context),fg=UiColorScheme.foreground(context);
        int edge=UiColorScheme.blend(bg,fg,.16f);
        GradientDrawable rounded=new GradientDrawable();rounded.setColor(bg);
        rounded.setCornerRadius(dp(16));rounded.setStroke(dp(1),edge);
        LinearLayout actions=new LinearLayout(context);actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(dp(5),dp(6),dp(5),dp(6));
        int oldGroup=Integer.MIN_VALUE;
        for(int i=0;i<menu.size();i++){
            MenuItem item=menu.getItem(i);if(!item.isVisible())continue;
            if(oldGroup!=Integer.MIN_VALUE&&oldGroup!=item.getGroupId()){
                View divider=new View(context);divider.setBackgroundColor(edge);
                LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,dp(1));
                line.setMargins(dp(10),dp(5),dp(10),dp(5));actions.addView(divider,line);
            }
            oldGroup=item.getGroupId();
            TextView entry=new TextView(context);entry.setText(item.getTitle());entry.setTextColor(fg);
            entry.setTextSize(15);entry.setGravity(Gravity.CENTER_VERTICAL);
            entry.setPadding(dp(15),dp(9),dp(15),dp(9));
            entry.setMinHeight(dp(46));entry.setEnabled(item.isEnabled());
            if(item.isChecked())entry.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            if(!item.isEnabled())entry.setAlpha(.42f);
            GradientDrawable hit=new GradientDrawable();
            hit.setColor(UiColorScheme.blend(bg,UiColorScheme.accent(context),.08f));
            hit.setCornerRadius(dp(10));
            android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
            states.addState(new int[]{android.R.attr.state_pressed},hit);
            states.addState(new int[]{},new GradientDrawable());
            entry.setBackground(states);
            entry.setOnClickListener(v->{
                if(surface!=null)surface.dismiss();
                menu.performIdentifierAction(item.getItemId(),0);
            });
            actions.addView(entry,new LinearLayout.LayoutParams(-1,-2));
        }
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);scroll.addView(actions);
        scroll.setBackground(rounded);
        int screenW=context.getResources().getDisplayMetrics().widthPixels;
        int screenH=context.getResources().getDisplayMetrics().heightPixels;
        int width=Math.min(dp(320),screenW-dp(24));
        actions.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int height=Math.min(Math.min(actions.getMeasuredHeight(),dp(470)),screenH-dp(76));
        surface=new PopupWindow(scroll,width,height,true);
        surface.setBackgroundDrawable(rounded);surface.setElevation(dp(8));
        surface.setOutsideTouchable(true);surface.setClippingEnabled(true);
        int[] location=new int[2];anchor.getLocationOnScreen(location);
        int x=Math.max(dp(8),Math.min(location[0]+anchor.getWidth()-width,screenW-width-dp(8)));
        int y=location[1]+anchor.getHeight();
        if(y+height>screenH-dp(16))y=Math.max(dp(8),location[1]-height);
        surface.showAtLocation(anchor,Gravity.TOP|Gravity.LEFT,x,y);
    }
    @Override public void dismiss(){if(surface!=null){surface.dismiss();surface=null;}else super.dismiss();}
}
