package ru.reader.rsvp;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One timer UI for audiobook, offline TTS and Edge TTS. */
final class SleepTimerSheet {
    interface Listener {
        void minutes(int minutes);
        void chapters(int count);
        /** -1 when the duration cannot yet be reliably calculated. */
        long remainingMillis(int chapters);
    }
    private SleepTimerSheet(){}
    private static int dp(Activity a,int n){return (int)(n*a.getResources().getDisplayMetrics().density+.5f);}
    private static TextView text(Activity a,String label,int size){
        TextView t=new TextView(a);t.setText(label);t.setTextSize(size);
        t.setTextColor(a.getSharedPreferences("reader",0).getInt("fg",0xff30221e));
        return t;
    }
    private static GradientDrawable surface(Activity a){
        GradientDrawable d=new GradientDrawable();
        d.setColor(ReaderButtonColors.neutral(a,0xfffff9f0));d.setCornerRadius(dp(a,12));return d;
    }
    private static String clock(long ms){
        long seconds=Math.max(0,ms)/1000;
        return String.format(java.util.Locale.ROOT,"%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);
    }
    static void open(Activity activity,Listener listener){
        UiNavigationSheet.showTopCustom(activity,"Таймер сна",(body,dialog)->{
            for(int value:new int[]{0,5,10,15,30,60}){
                TextView item=text(activity,value==0?"Выключить":value+" минут",16);
                item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(activity,14),0,dp(activity,12),0);
                item.setBackground(surface(activity));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(activity,46));lp.bottomMargin=dp(activity,5);
                body.addView(item,lp);
                item.setOnClickListener(v->{dialog.dismiss();listener.minutes(value);});
            }
            TextView chapter=text(activity,"Конец главы  ·  удерживайте для выбора числа глав",15);
            chapter.setGravity(Gravity.CENTER_VERTICAL);chapter.setPadding(dp(activity,14),0,dp(activity,10),0);
            chapter.setBackground(surface(activity));
            body.addView(chapter,new LinearLayout.LayoutParams(-1,dp(activity,54)));
            LinearLayout picker=new LinearLayout(activity);picker.setGravity(Gravity.CENTER_VERTICAL);
            picker.setVisibility(View.GONE);body.addView(picker,new LinearLayout.LayoutParams(-1,dp(activity,50)));
            android.content.SharedPreferences timerState=activity.getSharedPreferences("reader",0);
            AudioPlaybackService local=AudioPlaybackService.instance;
            boolean ttsDomain=activity instanceof ReaderActivity
                ||(local!=null&&local.isTtsMode());
            boolean chapterActive=ttsDomain
                ?timerState.getInt(PlaybackSleepCountdown.TTS_CHAPTER_KEY,-1)>0
                :timerState.getBoolean("audio_sleep_at_chapter_end",false);
            final int[] count={chapterActive&&!ttsDomain
                ?Math.max(1,timerState.getInt("audio_sleep_chapter_count",1)):1};
            TextView minus=text(activity,"−1",19);minus.setGravity(Gravity.CENTER);
            TextView countLabel=text(activity,"1 глава",17);countLabel.setGravity(Gravity.CENTER);
            TextView plus=text(activity,"+1",19);plus.setGravity(Gravity.CENTER);
            picker.addView(minus,new LinearLayout.LayoutParams(dp(activity,52),-1));
            picker.addView(countLabel,new LinearLayout.LayoutParams(0,-1,1));
            picker.addView(plus,new LinearLayout.LayoutParams(dp(activity,52),-1));
            TextView countdown=text(activity,"",13);countdown.setGravity(Gravity.CENTER);
            countdown.setVisibility(chapterActive?View.VISIBLE:View.GONE);body.addView(countdown);
            final Runnable[] tick=new Runnable[1];
            tick[0]=()->{
                if(!dialog.isShowing())return;
                long left=listener.remainingMillis(count[0]);
                countdown.setText(left<0?"Осталось: время уточняется":"Осталось: "+clock(left)
                        +(count[0]>1?" (по известным длительностям)":""));
                countdown.postDelayed(tick[0],1000);
            };
            dialog.setOnDismissListener(d->countdown.removeCallbacks(tick[0]));
            if(chapterActive)countdown.postDelayed(tick[0],300);
            Runnable update=()->{countLabel.setText(count[0]+(count[0]==1?" глава":" глав"));
                countdown.removeCallbacks(tick[0]);countdown.postDelayed(tick[0],100);};
            minus.setOnClickListener(v->{count[0]=Math.max(1,count[0]-1);update.run();});
            plus.setOnClickListener(v->{count[0]=Math.min(999,count[0]+1);update.run();});
            TextView apply=text(activity,"Запустить до конца "+"выбранных глав",16);
            apply.setGravity(Gravity.CENTER);apply.setBackground(surface(activity));
            apply.setVisibility(View.GONE);body.addView(apply,new LinearLayout.LayoutParams(-1,dp(activity,46)));
            chapter.setOnClickListener(v->{if(picker.getVisibility()!=View.VISIBLE){dialog.dismiss();listener.chapters(1);}
                else{dialog.dismiss();listener.chapters(count[0]);}});
            chapter.setOnLongClickListener(v->{picker.setVisibility(View.VISIBLE);apply.setVisibility(View.VISIBLE);
                countdown.setVisibility(View.VISIBLE);update.run();return true;});
            apply.setOnClickListener(v->{dialog.dismiss();listener.chapters(count[0]);});
            TextView custom=text(activity,"Пользовательский · минут",16);
            custom.setPadding(dp(activity,14),dp(activity,12),dp(activity,14),dp(activity,12));
            custom.setBackground(surface(activity));
            LinearLayout.LayoutParams customLp=new LinearLayout.LayoutParams(-1,dp(activity,49));customLp.topMargin=dp(activity,8);
            body.addView(custom,customLp);
            LinearLayout customRow=new LinearLayout(activity);customRow.setGravity(Gravity.CENTER_VERTICAL);
            customRow.setVisibility(View.GONE);body.addView(customRow);
            EditText minutes=new EditText(activity);minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
            minutes.setSingleLine(true);minutes.setHint("Число минут");minutes.setText("45");
            customRow.addView(minutes,new LinearLayout.LayoutParams(0,dp(activity,53),1));
            TextView accept=text(activity,"Готово",16);accept.setGravity(Gravity.CENTER);
            customRow.addView(accept,new LinearLayout.LayoutParams(dp(activity,89),dp(activity,53)));
            custom.setOnClickListener(v->customRow.setVisibility(customRow.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
            accept.setOnClickListener(v->{
                try{int value=Integer.parseInt(minutes.getText().toString().trim());
                    if(value<1||value>10080){minutes.setError("От 1 до 10080 минут");return;}
                    dialog.dismiss();listener.minutes(value);
                }catch(NumberFormatException ex){minutes.setError("Введите количество минут");}
            });
        });
    }
}
