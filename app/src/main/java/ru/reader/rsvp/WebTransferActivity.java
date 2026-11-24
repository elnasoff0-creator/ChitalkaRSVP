package ru.reader.rsvp;

import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/** Explicit session control. This Activity never owns upload sockets. */
public final class WebTransferActivity extends NavigationActivity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private TextView status,address,pin,active,last;
    private ImageView qr;
    private android.widget.Button start;
    private final Runnable update=new Runnable(){@Override public void run(){refresh();handler.postDelayed(this,1000);}};
    int dp(int v){return Math.round(getResources().getDisplayMetrics().density*v);}
    TextView label(String value,int sp){TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(UiColorScheme.foreground(this));t.setPadding(dp(4),dp(9),dp(4),dp(9));return t;}
    @Override protected void onCreate(Bundle state){super.onCreate(state);draw();}
    private void draw(){
        int bg=UiColorScheme.background(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setBackgroundColor(bg);
        content.setPadding(dp(16),dp(5),dp(16),dp(12));setContentView(content);SafeSystemBars.matchBackground(this,bg);
        AppToolbar.add(this,content,"Передача по Wi-Fi");
        ScrollView scroll=new ScrollView(this);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);scroll.addView(box);
        status=label("Сервер остановлен",18);status.setTypeface(null,1);box.addView(status);
        address=label("",19);address.setTextIsSelectable(true);box.addView(address);
        pin=label("",22);pin.setTextIsSelectable(true);box.addView(pin);
        qr=new ImageView(this);qr.setContentDescription("QR-код локального адреса передачи");qr.setAdjustViewBounds(true);
        LinearLayout.LayoutParams qrLp=new LinearLayout.LayoutParams(dp(220),dp(220));qrLp.gravity=Gravity.CENTER_HORIZONTAL;box.addView(qr,qrLp);
        android.widget.Button copy=button("Копировать адрес");copy.setOnClickListener(v->{
            WebTransferServer server=WebTransferService.current();if(server==null)return;
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Адрес Web Transfer",server.address()));
            android.widget.Toast.makeText(this,"Адрес скопирован",android.widget.Toast.LENGTH_SHORT).show();
        });box.addView(copy);
        active=label("",15);box.addView(active);last=label("",14);box.addView(last);
        box.addView(label("Компьютер и телефон должны быть в одной сети Wi-Fi. Откройте адрес в браузере, введите PIN и перетащите файлы.",14));
        box.addView(label("Перед первой передачей выберите папки книг, комиксов и аудиокниг в Настройки → Общие → Папки загрузок.",13));
        android.widget.Button folders=button("Выбрать папки книг, комиксов и аудиокниг");
        folders.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class).putExtra("download_folders",true)));
        box.addView(folders);
        android.widget.Button settings=button("Настройки Web Transfer · Онлайн");settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class).putExtra("online_tab",true)));box.addView(settings);
        start=button("Запустить сервер");start.setOnClickListener(v->{
            WebTransferServer s=WebTransferService.current();String action=s==null?WebTransferService.ACTION_START:WebTransferService.ACTION_STOP;
            Intent intent=new Intent(this,WebTransferService.class).setAction(action);
            if(s==null&&Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);
            handler.postDelayed(this::refresh,450);
        });box.addView(start);refresh();
    }
    private android.widget.Button button(String text){android.widget.Button b=new android.widget.Button(this);b.setText(text);b.setAllCaps(false);return b;}
    private String rendered="";
    private void refresh(){
        if(status==null)return;
        WebTransferServer s=WebTransferService.current();
        if(s==null){status.setText("Сервер остановлен");address.setText(WebTransferService.lastError());pin.setText("");active.setText("");last.setText("");qr.setImageDrawable(null);rendered="";start.setText("Запустить сервер");return;}
        status.setText("Сервер работает · только локальная Wi-Fi сеть");address.setText(s.address());pin.setText("PIN подключения: "+s.pin());
        active.setText("Подключений: "+s.connections()+" · Активных передач: "+s.activeUploads());last.setText(s.latest());start.setText("Остановить сервер");
        if(!rendered.equals(s.address())){rendered=s.address();drawQr(rendered);}
    }
    private void drawQr(String url){
        try{
            BitMatrix matrix=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,224,224);
            Bitmap image=Bitmap.createBitmap(224,224,Bitmap.Config.ARGB_8888);
            int ink=Color.BLACK,white=Color.WHITE;
            for(int y=0;y<224;y++)for(int x=0;x<224;x++)image.setPixel(x,y,matrix.get(x,y)?ink:white);
            qr.setImageBitmap(image);
        }catch(Exception ignored){qr.setImageDrawable(null);}
    }
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(update);handler.post(update);}
    @Override protected void onPause(){handler.removeCallbacks(update);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacks(update);super.onDestroy();}
}
