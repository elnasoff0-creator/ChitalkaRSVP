package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/** Direct MP3, FB2, EPUB and public cloud-share downloads; no audiobook-site parsing. */
public final class LinkDownloadActivity extends Activity {
    private static final int PICK_FOLDER=3401,BG=0xfff3e7d5,INK=0xff30221e,ACCENT=0xff925536,CARD=0xfffff9f0;
    private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout root,previewArea;
    private EditText links,subfolder;
    private TextView folderLabel,status;
    private Button analyze,download;
    private Uri chosenTree;
    private String treeLabel="";
    private final ArrayList<LinkDownloadStore.Entry> discovered=new ArrayList<>();
    private final ArrayList<CheckBox> checkboxes=new ArrayList<>();
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius){GradientDrawable s=new GradientDrawable();s.setColor(color);s.setCornerRadius(dp(radius));return s;}
    private TextView text(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(INK);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Button button(String label,boolean primary){Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?0xffffffff:INK);b.setGravity(Gravity.CENTER);b.setBackground(shape(primary?ACCENT:CARD,16));b.setMinHeight(dp(49));return b;}
    private void append(View child,int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);root.addView(child,lp);}
    private LinearLayout panel(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(12),dp(13),dp(12),dp(13));p.setBackground(shape(CARD,19));return p;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        SharedPreferences p=getSharedPreferences("reader",0);String savedTree=p.getString("direct_last_tree","");if(!savedTree.isEmpty()){
            Uri uri=Uri.parse(savedTree);try{if(androidx.documentfile.provider.DocumentFile.fromTreeUri(this,uri)!=null&&androidx.documentfile.provider.DocumentFile.fromTreeUri(this,uri).canWrite())chosenTree=uri;}catch(Exception ignored){}
        }
        treeLabel=p.getString("direct_last_tree_label","");build();
    }
    private void build(){ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setPadding(dp(14),dp(25),dp(14),dp(22));root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);scroll.addView(root);setContentView(scroll);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);append(header,0);
        ImageView back=new ImageView(this);back.setImageResource(R.drawable.ic_back);back.setColorFilter(INK);back.setPadding(dp(11),dp(11),dp(11),dp(11));back.setBackground(shape(CARD,15));header.addView(back,new LinearLayout.LayoutParams(dp(49),dp(49)));back.setOnClickListener(v->BackNavigation.back(this));back.setContentDescription("Назад");
        TextView title=text("Скачать по ссылке",22,true);title.setTypeface(Typeface.SERIF,Typeface.BOLD);title.setGravity(Gravity.CENTER);header.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));
        header.addView(new View(this),new LinearLayout.LayoutParams(dp(49),dp(49)));
        LinearLayout linkPanel=panel();append(linkPanel,17);linkPanel.addView(text("Ссылка на MP3, FB2, EPUB или облачный файл",16,true));
        links=new EditText(this);links.setTextSize(14);links.setHint("https://example.com/book.epub\nhttps://example.com/book.fb2\nhttps://example.com/audio.mp3");links.setMinLines(3);links.setMaxLines(7);links.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        linkPanel.addView(links,new LinearLayout.LayoutParams(-1,-2));
        Button paste=button("Вставить ссылку из буфера",false);linkPanel.addView(paste);paste.setOnClickListener(v->{ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);ClipData data=clipboard.getPrimaryClip();if(data!=null&&data.getItemCount()>0){String value=data.getItemAt(0).coerceToText(this).toString();String previous=links.getText().toString().trim();links.setText(previous.isEmpty()?value:previous+"\n"+value);}});
        TextView explanation=text("Одна ссылка в строке. Поддерживаются MP3, FB2 и EPUB: прямые HTTPS-файлы, публичные ссылки Яндекс.Диска, Dropbox и Google Drive, если доступна прямая загрузка. Для Яндекс.Диска — первые 100 файлов публичной папки. Другие облака могут потребовать отдельный адаптер.",12,false);explanation.setTextColor(0xff716259);linkPanel.addView(explanation);
        LinearLayout folderPanel=panel();append(folderPanel,12);folderPanel.addView(text("Путь / Папка для сохранения",17,true));
        folderLabel=text(chosenTree==null?"Папка ещё не выбрана":treeLabel,14,false);folderLabel.setTextColor(ACCENT);folderPanel.addView(folderLabel);
        Button select=button("▣ Выбрать папку",false);folderPanel.addView(select);select.setOnClickListener(v->selectFolder());
        folderPanel.addView(text("Название новой подпапки (необязательно):",13,false));subfolder=new EditText(this);subfolder.setSingleLine(true);subfolder.setHint("Загрузка по ссылке");subfolder.setText(getSharedPreferences("reader",0).getString("direct_last_subfolder",""));folderPanel.addView(subfolder);
        TextView hint=text("Оставьте поле пустым, чтобы сохранить файлы прямо в выбранную папку. Новая подпапка будет создана автоматически при скачивании.",12,false);hint.setTextColor(0xff716259);folderPanel.addView(hint);
        analyze=button("⌕  Анализировать ссылки",true);append(analyze,14);analyze.setOnClickListener(v->analyze());
        status=text("",13,false);append(status,8);
        previewArea=new LinearLayout(this);previewArea.setOrientation(LinearLayout.VERTICAL);append(previewArea,4);
        download=button("↓  Скачать выбранное",true);append(download,12);download.setVisibility(View.GONE);download.setOnClickListener(v->confirm());
        Button openQueue=button("Открыть все загрузки",false);append(openQueue,8);openQueue.setOnClickListener(v->startActivity(new Intent(this,DownloadActivity.class)));
        String source=getIntent().getStringExtra("source");if(source!=null&&!source.isEmpty())links.setText(source);
    }
    private void selectFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);if(Build.VERSION.SDK_INT>=26&&chosenTree!=null)i.putExtra(DocumentsContract.EXTRA_INITIAL_URI,chosenTree);startActivityForResult(i,PICK_FOLDER);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PICK_FOLDER&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri selected=data.getData();try{
            getContentResolver().takePersistableUriPermission(selected,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            chosenTree=selected;androidx.documentfile.provider.DocumentFile folder=androidx.documentfile.provider.DocumentFile.fromTreeUri(this,selected);treeLabel=folder==null?selected.toString():folder.getName();if(treeLabel==null)treeLabel=selected.toString();folderLabel.setText(treeLabel);
            getSharedPreferences("reader",0).edit().putString("direct_last_tree",selected.toString()).putString("direct_last_tree_label",treeLabel).apply();
        }catch(Exception ex){chosenTree=null;folderLabel.setText("Невозможно получить доступ к папке: "+ex.getMessage());}}}
    private void analyze(){String input=links.getText().toString().trim();if(input.isEmpty()){status.setText("Введите HTTPS-ссылки на MP3, FB2 или EPUB");return;}
        String[] lines=input.split("\\r?\\n");if(lines.length>100){status.setText("Не более 100 ссылок за один раз");return;}
        analyze.setEnabled(false);discovered.clear();checkboxes.clear();previewArea.removeAllViews();download.setVisibility(View.GONE);status.setText("Проверяем ссылки…");
        new Thread(()->{ArrayList<LinkDownloadStore.Entry> found=new ArrayList<>();ArrayList<String> errors=new ArrayList<>();HashSet<String> seen=new HashSet<>();
            for(String value:lines){String url=value.trim();if(url.isEmpty())continue;try{ArrayList<LinkDownloadStore.Entry> files=DirectLinkResolver.resolve(url);for(LinkDownloadStore.Entry file:files)if(found.size()<100&&seen.add(file.source+"#"+file.cloudPath))found.add(file);}catch(Exception ex){errors.add(url+" — "+ex.getMessage());}}
            runOnUiThread(()->{analyze.setEnabled(true);discovered.addAll(found);renderFound(errors);});
        },"direct-link-analyzer").start();
    }
    private void renderFound(ArrayList<String> errors){previewArea.removeAllViews();checkboxes.clear();
        status.setText("Найдено файлов: "+discovered.size()+(errors.isEmpty()?"":" · ошибок: "+errors.size()));
        if(!discovered.isEmpty()){
            LinearLayout header=new LinearLayout(this);previewArea.addView(header);Button yes=button("Выбрать все",false),no=button("Снять выделение",false);header.addView(yes,new LinearLayout.LayoutParams(0,dp(48),1));header.addView(no,new LinearLayout.LayoutParams(0,dp(48),1));yes.setOnClickListener(v->{for(CheckBox c:checkboxes)c.setChecked(true);});no.setOnClickListener(v->{for(CheckBox c:checkboxes)c.setChecked(false);});
            for(int i=0;i<discovered.size();i++){final LinkDownloadStore.Entry entry=discovered.get(i);LinearLayout card=panel();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(7);previewArea.addView(card,lp);
                CheckBox cb=new CheckBox(this);cb.setText(entry.name);cb.setChecked(true);cb.setTextColor(INK);cb.setTextSize(15);card.addView(cb);checkboxes.add(cb);
                TextView meta=text((entry.size<0?"Размер неизвестен":String.format(Locale.ROOT,"%.1f МБ",entry.size/1048576.0))+" · "+LinkFileTypes.formatName(entry.format)+" найден",12,false);card.addView(meta);
                Button rename=button("Переименовать",false);card.addView(rename);rename.setOnClickListener(v->{EditText name=new EditText(this);name.setSingleLine(true);name.setText(entry.name);new AlertDialog.Builder(this).setTitle("Имя файла").setView(name).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{entry.name=LinkFileTypes.safeFilename(name.getText().toString(),entry.format);cb.setText(entry.name);}).show();});
            }
            download.setVisibility(View.VISIBLE);
        }
        if(!errors.isEmpty()){TextView title=text("Недоступные или неподдерживаемые ссылки:",14,true);previewArea.addView(title);for(String error:errors){TextView line=text(error,12,false);line.setTextColor(0xffac4032);previewArea.addView(line);}}
    }
    private void confirm(){if(chosenTree==null){status.setText("Сначала выберите папку сохранения");selectFolder();return;}
        ArrayList<LinkDownloadStore.Entry> selected=new ArrayList<>();HashSet<String> names=new HashSet<>();for(int i=0;i<checkboxes.size();i++)if(checkboxes.get(i).isChecked()){
            LinkDownloadStore.Entry item=discovered.get(i);item.name=LinkFileTypes.safeFilename(item.name,item.format);String key=item.name.toLowerCase(Locale.ROOT);
            if(!names.add(key)){status.setText("Повторяется имя файла: "+item.name+". Переименуйте один из файлов.");return;}selected.add(item);
        }
        if(selected.isEmpty()){status.setText("Выберите хотя бы один файл");return;}
        String folderName=subfolder.getText().toString().trim();if(!folderName.isEmpty()){
            String safe=LinkDownloadStore.safeName(folderName);if(!safe.equals(folderName)){status.setText("Недопустимые символы в названии папки");return;}folderName=safe;
        }
        final String finalFolder=folderName;
        new AlertDialog.Builder(this).setTitle("Подтвердите загрузку").setMessage("Файлов: "+selected.size()+"\nПапка: "+treeLabel+(finalFolder.isEmpty()?"":" / "+finalFolder)+"\n\nСкачивайте только файлы, на которые у вас есть право доступа.")
            .setNegativeButton("Отмена",null).setPositiveButton("Скачать",(d,w)->{
                LinkDownloadStore.Batch batch=new LinkDownloadStore.Batch();batch.treeUri=chosenTree.toString();batch.treeLabel=treeLabel;batch.subfolder=finalFolder;batch.title=finalFolder.isEmpty()?"Загрузка по ссылке":finalFolder;batch.files.addAll(selected);LinkDownloadStore.put(this,batch);
                getSharedPreferences("reader",0).edit().putString("direct_last_subfolder",finalFolder).apply();
                Intent intent=new Intent(this,DirectLinkDownloadService.class).setAction(DirectLinkDownloadService.START).putExtra(DirectLinkDownloadService.EXTRA_ID,batch.id);
                try{if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);
                    startActivity(new Intent(this,DownloadActivity.class));finish();
                }catch(Exception ex){status.setText("Не удалось запустить загрузку: "+ex.getMessage());}
            }).show();
    }
    @Override public void onBackPressed(){BackNavigation.back(this);}
}
