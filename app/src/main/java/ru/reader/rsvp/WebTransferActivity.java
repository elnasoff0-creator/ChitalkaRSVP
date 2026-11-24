package ru.reader.rsvp;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/** Single compact, theme-aware controller. Transfers themselves belong to the foreground service. */
public final class WebTransferActivity extends NavigationActivity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private TextView status,address,pin,active,last,foldersSummary;
    private ImageView qr,start,copy;
    private String rendered="";
    private boolean commandPending;
    private final Runnable update=new Runnable(){@Override public void run(){refresh();handler.postDelayed(this,1000);}};
    private int dp(int v){return Math.round(getResources().getDisplayMetrics().density*v);}
    private GradientDrawable surface(){GradientDrawable bg=new GradientDrawable();
        bg.setColor(UiColorScheme.neutral(this));bg.setCornerRadius(dp(17));return bg;}
    private TextView text(String value,int size,boolean bold){TextView t=new TextView(this);t.setText(value);
        t.setTextSize(size);t.setTextColor(UiColorScheme.foreground(this));
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private ImageView action(int drawable,String hint){
        ImageView button=new ImageView(this);button.setImageResource(drawable);
        button.setColorFilter(UiColorScheme.foreground(this));button.setPadding(dp(10),dp(10),dp(10),dp(10));
        button.setBackground(surface());button.setContentDescription(hint);
        button.setFocusable(true);button.setClickable(true);
        return button;
    }
    private LinearLayout section(LinearLayout parent,int padding){LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(padding),dp(padding),dp(padding),dp(padding));
        card.setBackground(surface());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(12);parent.addView(card,lp);return card;}
    @Override protected void onCreate(Bundle state){super.onCreate(state);draw();}
    private void draw(){
        int bg=UiColorScheme.background(this);content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);content.setBackgroundColor(bg);
        content.setPadding(dp(16),dp(5),dp(16),dp(12));setContentView(content);
        SafeSystemBars.matchBackground(this,bg);
        AppToolbar.add(this,content,"Передача по Wi-Fi");
        LinearLayout toolbar=(LinearLayout)content.getChildAt(0);
        start=action(R.drawable.ic_web_server_start,"Запустить сервер Wi-Fi");
        toolbar.addView(start,new LinearLayout.LayoutParams(dp(44),dp(44)));
        start.setOnClickListener(v->toggleServer());
        ImageView help=action(R.drawable.ic_info_help,"Справка: Передача книг по Wi-Fi");
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(44),dp(44));hp.leftMargin=dp(6);
        toolbar.addView(help,hp);help.setOnClickListener(v->startActivity(
            new Intent(this,ActivityInfo.class).putExtra("help_section","Передача книг по Wi-Fi")));

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
        content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        LinearLayout connection=section(body,16);
        status=text("Сервер остановлен",18,true);connection.addView(status);
        TextView caption=text("Адрес подключения",12,false);caption.setTextColor(UiColorScheme.muted(this));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(13);
        connection.addView(caption,cp);
        LinearLayout addressRow=new LinearLayout(this);addressRow.setGravity(Gravity.CENTER_VERTICAL);
        connection.addView(addressRow);
        address=text("",16,true);address.setTextIsSelectable(true);
        address.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);address.setSingleLine(true);
        addressRow.addView(address,new LinearLayout.LayoutParams(0,dp(48),1));
        address.setGravity(Gravity.CENTER_VERTICAL);
        copy=action(R.drawable.ic_web_copy_address,"Копировать адрес сервера");
        addressRow.addView(copy,new LinearLayout.LayoutParams(dp(44),dp(44)));
        copy.setOnClickListener(v->copyAddress());
        pin=text("",18,true);connection.addView(pin);
        qr=new ImageView(this);qr.setContentDescription("QR-код адреса локального сервера");
        qr.setAdjustViewBounds(true);qr.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(dp(210),dp(210));
        q.gravity=Gravity.CENTER_HORIZONTAL;q.topMargin=dp(12);connection.addView(qr,q);

        LinearLayout destination=section(body,16);
        LinearLayout destinationRow=new LinearLayout(this);
        destinationRow.setGravity(Gravity.CENTER_VERTICAL);destination.addView(destinationRow);
        TextView heading=text("Папки загрузок",16,true);
        destinationRow.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        ImageView choose=action(R.drawable.ic_folder_audio,"Выбрать папку загрузки через Android");
        destinationRow.addView(choose,new LinearLayout.LayoutParams(dp(44),dp(44)));
        choose.setOnClickListener(this::showFolderPicker);
        foldersSummary=text("",13,false);foldersSummary.setTextColor(UiColorScheme.muted(this));
        LinearLayout.LayoutParams summaryParams=new LinearLayout.LayoutParams(-1,-2);
        summaryParams.topMargin=dp(8);destination.addView(foldersSummary,summaryParams);

        LinearLayout transfers=section(body,16);
        TextView transferTitle=text("Передача файлов",16,true);transfers.addView(transferTitle);
        active=text("",14,false);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);
        ap.topMargin=dp(7);transfers.addView(active,ap);
        last=text("",13,false);last.setTextColor(UiColorScheme.muted(this));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(5);
        transfers.addView(last,lp);
        refresh();
    }
    private void showFolderPicker(View anchor){
        PopupMenu menu=new PalettePopupMenu(this,anchor);
        menu.getMenu().add("Книги").setOnMenuItemClickListener(m->{
            DownloadFolderPicker.choose(this,DownloadFolderPicker.BOOK_REQUEST,DownloadFolderPicker.BOOK);return true;});
        menu.getMenu().add("Комиксы").setOnMenuItemClickListener(m->{
            DownloadFolderPicker.choose(this,DownloadFolderPicker.MANGA_REQUEST,DownloadFolderPicker.MANGA);return true;});
        menu.getMenu().add("Аудиокниги").setOnMenuItemClickListener(m->{
            DownloadFolderPicker.choose(this,DownloadFolderPicker.AUDIO_REQUEST,DownloadFolderPicker.AUDIO);return true;});
        menu.show();
    }
    private void copyAddress(){
        WebTransferServer server=WebTransferService.current();
        if(server==null){Toast.makeText(this,"Сначала запустите сервер",Toast.LENGTH_SHORT).show();return;}
        ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(
            ClipData.newPlainText("Адрес Web Transfer",server.address()));
        Toast.makeText(this,"Адрес скопирован",Toast.LENGTH_SHORT).show();
    }
    private void toggleServer(){
        if(commandPending)return;
        WebTransferServer current=WebTransferService.current();
        if(current!=null&&current.activeUploads()>0){
            new SwipeDialogs.Builder(this).setTitle("Остановить сервер?")
                .setMessage("Активные передачи будут прерваны, незавершённые файлы удалены.")
                .setNegativeButton("Отмена",null).setPositiveButton("Остановить",(d,w)->sendCommand(false)).show();
            return;
        }
        sendCommand(current==null);
    }
    private void sendCommand(boolean launch){
        commandPending=true;start.setEnabled(false);start.setAlpha(.45f);
        Intent intent=new Intent(this,WebTransferService.class).setAction(
            launch?WebTransferService.ACTION_START:WebTransferService.ACTION_STOP);
        try{if(launch&&Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);}
        catch(Exception ex){Toast.makeText(this,"Ошибка запуска: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
        handler.postDelayed(()->{commandPending=false;refresh();},750);
    }
    private void refresh(){
        if(status==null)return;
        WebTransferServer server=WebTransferService.current();
        boolean running=server!=null;
        start.setImageResource(running?R.drawable.ic_web_server_stop:R.drawable.ic_web_server_start);
        start.setContentDescription(running?"Остановить сервер Wi-Fi":"Запустить сервер Wi-Fi");
        start.setEnabled(!commandPending);start.setAlpha(commandPending?.45f:1f);
        copy.setEnabled(running);copy.setAlpha(running?1f:.35f);
        status.setText(running?"Сервер запущен · локальная сеть":"Сервер остановлен");
        address.setText(running?server.address():WebTransferService.lastError());
        pin.setText(running?"PIN: "+server.pin():"");
        qr.setVisibility(running?View.VISIBLE:View.GONE);
        active.setText(running?"Подключений: "+server.connections()+" · Активных передач: "+server.activeUploads():
            "Нет активных передач");
        last.setText(running?server.latest():"Принятые файлы и история отображаются в разделе «Загрузки».");
        foldersSummary.setText("Книги: "+DownloadFolderPicker.summary(this,DownloadFolderPicker.BOOK)+"\n"+
            "Комиксы: "+DownloadFolderPicker.summary(this,DownloadFolderPicker.MANGA)+"\n"+
            "Аудиокниги: "+DownloadFolderPicker.summary(this,DownloadFolderPicker.AUDIO));
        if(running&&!rendered.equals(server.address())){rendered=server.address();drawQr(rendered);}
        if(!running){rendered="";qr.setImageDrawable(null);}
    }
    private void drawQr(String uri){
        try{
            BitMatrix bits=new QRCodeWriter().encode(uri,BarcodeFormat.QR_CODE,224,224);
            Bitmap bitmap=Bitmap.createBitmap(224,224,Bitmap.Config.ARGB_8888);
            for(int y=0;y<224;y++)for(int x=0;x<224;x++)bitmap.setPixel(x,y,bits.get(x,y)?Color.BLACK:Color.WHITE);
            qr.setImageBitmap(bitmap);
        }catch(Exception ignored){qr.setImageDrawable(null);}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        String category=request==DownloadFolderPicker.BOOK_REQUEST?DownloadFolderPicker.BOOK:
            request==DownloadFolderPicker.MANGA_REQUEST?DownloadFolderPicker.MANGA:
            request==DownloadFolderPicker.AUDIO_REQUEST?DownloadFolderPicker.AUDIO:null;
        if(category!=null)DownloadFolderPicker.receive(this,request,result,data,request,category,ok->refresh());
    }
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(update);handler.post(update);}
    @Override protected void onPause(){handler.removeCallbacks(update);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacks(update);super.onDestroy();}
}
