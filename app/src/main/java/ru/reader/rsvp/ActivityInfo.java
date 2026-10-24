package ru.reader.rsvp;
import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Offline, short-form collapsible in-app manual. */
public class ActivityInfo extends Activity {
    private int bg,fg;
    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    GradientDrawable background(int color){GradientDrawable gd=new GradientDrawable();gd.setColor(color);gd.setCornerRadius(dp(12));return gd;}
    TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(fg);return t;}
    @Override public void onCreate(Bundle state){super.onCreate(state);SharedPreferences prefs=getSharedPreferences("reader",0);
        bg=prefs.getInt("bg",0xfff3e7d5);fg=prefs.getInt("fg",0xff30221e);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(bg);root.setPadding(dp(16),dp(22),dp(16),dp(12));setContentView(root);SafeSystemBars.matchBackground(this,bg);
        TextView title=text("←   Справка",24);title.setPadding(dp(2),dp(8),0,dp(18));title.setOnClickListener(v->finish());root.addView(title);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout entries=new LinearLayout(this);entries.setOrientation(1);scroll.addView(entries);
        section(entries,"Библиотека","Добавляйте EPUB и FB2, открывайте последнюю книгу и управляйте обложками. Позиция чтения сохраняется автоматически.");
        section(entries,"Обычное чтение","Изменяйте оформление, шрифт и размер. Для перехода к чтению выберите книгу в библиотеке.");
        section(entries,"Скоростное чтение","Выбирайте режим показа слов, количество соседних слов и скорость. Воспроизведение ставится на паузу кнопкой Play.");
        section(entries,"Аудиокниги","Добавляйте папки MP3/M4A, выбирайте главы, скорость, обложку и таймер сна. Прогресс сохраняется раздельно для каждой книги.");
        section(entries,"Системные голоса TTS","Выбирайте установленные локальные голоса Samsung и Google, слушайте пример, настройте скорость в словах в минуту. Локальные звуковые файлы хранятся временно.");
        section(entries,"Microsoft Edge TTS","Для синтеза требуется интернет. Полученные фрагменты сохраняются постоянно и доступны повторно через «Файлы аудиокниги».");
        section(entries,"Офлайн-прослушивание","Скачанные онлайн-фрагменты доступны без сети. Если они кончились, приложение предложит продолжить через системный TTS.");
        section(entries,"Загрузки","Просматривайте загрузки и используйте «Подготовить всю книгу», чтобы получить недостающие части аудио заранее.");
        section(entries,"Чтейцо и награды","Читайте книги, развивайте питомца и получайте награды. В настройках доступен переключатель питомца.");
        section(entries,"Обновления","Пункт «Обновление» в главном меню проверяет новую версию приложения.");
    }
    void section(LinearLayout entries,String heading,String body){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setBackground(background((Color.red(bg)+Color.green(bg)+Color.blue(bg))<360?0xff353b3a:0xfffff8ee));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(9);entries.addView(panel,lp);
        TextView h=text(heading+"    ▾",17);h.setPadding(dp(14),dp(16),dp(14),dp(16));panel.addView(h);
        TextView content=text(body,15);content.setPadding(dp(14),0,dp(14),dp(17));content.setVisibility(View.GONE);panel.addView(content);
        h.setOnClickListener(v->{boolean show=content.getVisibility()!=View.VISIBLE;content.setVisibility(show?View.VISIBLE:View.GONE);h.setText(heading+(show?"    ▴":"    ▾"));});
    }
}
