package ru.reader.rsvp;

import android.content.Intent;
import android.net.Uri;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fast, searchable logical archive. Restores metadata; files are never moved on archive/restore. */
public final class DownloadArchiveActivity extends NavigationActivity {
    private static final int BG=0xfff3e7d5, PANEL=0xfffff9f0, INK=0xff30221e, MUTED=0xff716259;
    private static final int ARCHIVE_TRANSFER_REQUEST=12560;
    private String pendingArchiveTransferId="";
    private final ExecutorService disk=Executors.newSingleThreadExecutor();
    private final ArrayList<DownloadArchiveStore.Entry> records=new ArrayList<>(),shown=new ArrayList<>();
    private RecyclerView list;private ArchiveAdapter adapter;private TextView count,sortButton,empty;
    private String query="";private int order;
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int c,int radius){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextColor(INK);v.setTextSize(size);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private ImageView image(int id,String description){ImageView v=new ImageView(this);v.setImageResource(id);v.setColorFilter(INK);
        v.setPadding(dp(9),dp(9),dp(9),dp(9));v.setContentDescription(description);v.setBackground(background(PANEL,14));return v;}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        if(state!=null)pendingArchiveTransferId=state.getString("archive_move_id","");
        order=getSharedPreferences("reader",0).getInt("archive_download_sort_v153",0);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(24),dp(12),dp(10));
        root.setBackgroundColor(BG);setContentView(root);SafeSystemBars.matchBackground(this,BG);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);root.addView(heading,new LinearLayout.LayoutParams(-1,dp(54)));
        ImageView back=image(R.drawable.ic_download_restore,"Назад к загрузкам");heading.addView(back,new LinearLayout.LayoutParams(dp(46),dp(46)));
        back.setOnClickListener(v->finish());
        TextView label=text("Архив загрузок",23,true);label.setTypeface(Typeface.SERIF,Typeface.BOLD);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(9);heading.addView(label,lp);
        ImageView clear=image(R.drawable.ic_download_archive_clear,"Очистить архив");heading.addView(clear,new LinearLayout.LayoutParams(dp(46),dp(46)));
        clear.setOnClickListener(v->confirmClear());
        EditText find=new EditText(this);find.setSingleLine(true);find.setTextSize(16);find.setTextColor(INK);
        find.setHint("Поиск в архиве…");find.setHintTextColor(MUTED);find.setPadding(dp(16),dp(8),dp(16),dp(8));
        find.setBackground(background(PANEL,14));
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(48));fp.topMargin=dp(10);root.addView(find,fp);
        find.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();filter();}
            public void afterTextChanged(Editable s){} });
        LinearLayout tools=new LinearLayout(this);tools.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams toolsLp=new LinearLayout.LayoutParams(-1,dp(46));toolsLp.topMargin=dp(8);root.addView(tools,toolsLp);
        sortButton=text("",14,true);sortButton.setGravity(Gravity.CENTER_VERTICAL);sortButton.setPadding(dp(12),0,dp(9),0);
        sortButton.setBackground(background(PANEL,13));tools.addView(sortButton,new LinearLayout.LayoutParams(0,dp(42),1));
        sortLabel();sortButton.setOnClickListener(v->showSort());
        count=text("",12,false);count.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);count.setTextColor(MUTED);
        LinearLayout.LayoutParams countLp=new LinearLayout.LayoutParams(dp(108),-1);countLp.leftMargin=dp(5);tools.addView(count,countLp);
        empty=text("Архив пуст",14,false);empty.setGravity(Gravity.CENTER);empty.setTextColor(MUTED);
        root.addView(empty,new LinearLayout.LayoutParams(-1,dp(48)));
        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));list.setItemAnimator(null);
        adapter=new ArchiveAdapter();adapter.setHasStableIds(true);list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        reload();
    }
    @Override protected void onResume(){super.onResume();if(adapter!=null)reload();}
    @Override protected void onSaveInstanceState(Bundle out){
        out.putString("archive_move_id",pendingArchiveTransferId);super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy(){disk.shutdownNow();super.onDestroy();}
    private void reload(){records.clear();records.addAll(DownloadArchiveStore.entries(this));filter();}
    private void sortLabel(){if(sortButton!=null)sortButton.setText(new String[]{"Дата · новые сначала ▾","Дата · старые сначала ▾","Имя · А–Я ▾","Имя · Я–А ▾"}[order]);}
    private void showSort(){PopupMenu menu=new PopupMenu(this,sortButton);
        String[] opts={"Дата: сначала новые","Дата: сначала старые","Имя: А–Я","Имя: Я–А"};
        for(int n=0;n<opts.length;n++){final int value=n;
            menu.getMenu().add(opts[n]).setOnMenuItemClickListener(i->{order=value;getSharedPreferences("reader",0).edit()
                .putInt("archive_download_sort_v153",order).apply();sortLabel();filter();return true;});}
        menu.show();
    }
    private void filter(){if(adapter==null)return;
        final String needle=query.trim().toLowerCase(Locale.ROOT);
        ArrayList<DownloadArchiveStore.Entry> next=new ArrayList<>();
        for(DownloadArchiveStore.Entry e:records){
            String type=typeName(e.type);
            if(needle.isEmpty()||(e.displayTitle+" "+e.description+" "+type).toLowerCase(Locale.ROOT).contains(needle))next.add(e);
        }
        Comparator<DownloadArchiveStore.Entry> cmp;
        if(order<2)cmp=Comparator.comparingLong(e->e.archivedAt);
        else cmp=Comparator.comparing(e->e.displayTitle.toLowerCase(Locale.ROOT),String.CASE_INSENSITIVE_ORDER);
        if(order==0||order==3)cmp=cmp.reversed();next.sort(cmp.thenComparing(e->e.id));
        ArrayList<DownloadArchiveStore.Entry> old=new ArrayList<>(shown);
        DiffUtil.DiffResult diff=DiffUtil.calculateDiff(new DiffUtil.Callback(){
            public int getOldListSize(){return old.size();}public int getNewListSize(){return next.size();}
            public boolean areItemsTheSame(int a,int b){return old.get(a).id.equals(next.get(b).id);}
            public boolean areContentsTheSame(int a,int b){return old.get(a).archivedAt==next.get(b).archivedAt && old.get(a).displayTitle.equals(next.get(b).displayTitle);}
        },false);
        shown.clear();shown.addAll(next);diff.dispatchUpdatesTo(adapter);
        count.setText(shown.size()+" из "+records.size());
        if(empty!=null){empty.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE);
            empty.setText(records.isEmpty()?"Архив пуст":"Ничего не найдено");}
    }
    private static String typeName(String type){switch(type){
        case DownloadsRepository.AUDIO:return "Аудиокнига";case DownloadsRepository.EDGE:return "Edge TTS";
        case DownloadsRepository.MANGA:return "Манга";case DownloadsRepository.RANOBE:return "Ранобэ";case DownloadsRepository.LINK:return "По ссылке";
        default:return "Загрузка";
    }}
    private void restore(DownloadArchiveStore.Entry entry){
        // Check source availability away from the UI thread before restoring.
        disk.execute(()->{
            boolean available=false;
            try{for(DownloadsRepository.Item item:DownloadsRepository.load(getApplicationContext(),null))
                if(entry.id.equals(item.id)&&(entry.ready?item.ready:true)){available=true;break;}}
            catch(Exception ignored){}
            final boolean ready=available;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                if(!ready){Toast.makeText(this,"Файлы загрузки недоступны. Восстановление невозможно",Toast.LENGTH_LONG).show();return;}
                if(!DownloadArchiveStore.restore(this,entry.id)){
                    Toast.makeText(this,"Не удалось восстановить запись",Toast.LENGTH_LONG).show();return;
                }
                Toast.makeText(this,entry.ready?"Восстановлено в «Готовые»":"Восстановлено в «В процессе»",Toast.LENGTH_SHORT).show();reload();
            });
        });
    }
    private void rename(DownloadArchiveStore.Entry entry){
        BookRenameDialog.show(this,entry.displayTitle,newName->{
            if(!BookTitleStore.renameDownload(this,entry.id,entry.type,entry.bookUri,entry.source,newName))return false;
            reload();return true;
        });
    }
    private void move(DownloadArchiveStore.Entry entry){
        new SwipeDialogs.Builder(this).setTitle("Перенести файлы из архива")
            .setMessage("Будут перенесены доступные файлы этой загрузки с проверкой SHA-256. " +
                "Рабочие файлы Edge TTS останутся у движка; пользовательские оригиналы не удаляются.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Выбрать папку",(d,w)->{
                pendingArchiveTransferId=entry.id;
                Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(picker,ARCHIVE_TRANSFER_REQUEST);
            }).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=ARCHIVE_TRANSFER_REQUEST)return;
        String id=pendingArchiveTransferId;pendingArchiveTransferId="";
        if(result!=RESULT_OK||data==null||data.getData()==null||id.isEmpty())return;
        Uri destination=data.getData();
        try{getContentResolver().takePersistableUriPermission(destination,
            Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }catch(Exception e){Toast.makeText(this,"Папка недоступна",Toast.LENGTH_LONG).show();return;}
        disk.execute(()->{
            String message;
            try{
                DownloadsRepository.Item match=null;
                for(DownloadsRepository.Item item:DownloadsRepository.load(getApplicationContext(),null))
                    if(item.id.equals(id)){match=item;break;}
                if(match==null)throw new java.io.IOException("Исходная загрузка отсутствует");
                SafeDownloadTransfer.Result report=SafeDownloadTransfer.copy(getApplicationContext(),match,destination);
                message="Перенесено: "+(report.copied+report.existing)+" файлов, удалено старых: "+report.deleted+
                    ". "+report.note;
            }catch(Exception ex){message="Перенос остановлен: "+ex.getMessage()+". Исходные файлы сохранены.";}
            final String text=message;
            runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){
                new SwipeDialogs.Builder(this).setTitle("Результат переноса")
                    .setMessage(text).setPositiveButton("Закрыть",null).show();reload();}});
        });
    }

    private void delete(DownloadArchiveStore.Entry entry){
        new SwipeDialogs.Builder(this).setTitle("Удалить скачанные файлы?")
            .setMessage("Будут удалены аудиофайлы/CBZ этой загрузки и управляемые приложением данные. Исходные книги останутся без изменений. Это действие нельзя отменить.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Удалить",(d,w)->{
                Toast.makeText(this,"Удаляем файлы…",Toast.LENGTH_SHORT).show();
                disk.execute(()->{
                    String failure=null;
                    try{DownloadArchiveDelete.delete(getApplicationContext(),entry);
                        if(!DownloadArchiveStore.removedFiles(getApplicationContext(),entry.id))
                            throw new Exception("Не удалось сохранить изменение архива");}
                    catch(Exception ex){failure=ex.getMessage();}
                    final String problem=failure;
                    runOnUiThread(()->{if(isFinishing()||isDestroyed())return;
                        if(problem==null){reload();Toast.makeText(this,"Файлы удалены",Toast.LENGTH_SHORT).show();}
                        else Toast.makeText(this,"Удаление не завершено: "+problem,Toast.LENGTH_LONG).show();
                    });
                });
            }).show();
    }
    private void confirmClear(){
        if(records.isEmpty()){Toast.makeText(this,"Архив пуст",Toast.LENGTH_SHORT).show();return;}
        new SwipeDialogs.Builder(this).setTitle("Очистить архив?")
            .setMessage("Удалить все записи архива из списка? Скачанные файлы останутся на устройстве. Они не вернутся автоматически в «Готовые».")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Очистить архив",(d,w)->{
                if(DownloadArchiveStore.clear(this)){reload();Toast.makeText(this,"Архив очищен · файлы сохранены",Toast.LENGTH_SHORT).show();}
                else Toast.makeText(this,"Не удалось очистить архив",Toast.LENGTH_LONG).show();
            }).show();
    }
    private final class ArchiveAdapter extends RecyclerView.Adapter<ArchiveAdapter.Holder>{
        @Override public long getItemId(int p){return shown.get(p).id.hashCode();}
        @Override public int getItemCount(){return shown.size();}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout root=new LinearLayout(DownloadArchiveActivity.this);root.setOrientation(LinearLayout.VERTICAL);
            root.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new Holder(root);
        }
        @Override public void onBindViewHolder(Holder h,int position){
            LinearLayout root=(LinearLayout)h.itemView;root.removeAllViews();DownloadArchiveStore.Entry e=shown.get(position);
            LinearLayout card=new LinearLayout(DownloadArchiveActivity.this);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(13),dp(12),dp(13),dp(12));card.setBackground(background(PANEL,17));
            LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);cardLp.bottomMargin=dp(9);root.addView(card,cardLp);
            TextView title=text(e.displayTitle,17,true);title.setTypeface(Typeface.SERIF,Typeface.BOLD);card.addView(title);
            String date=new SimpleDateFormat("dd.MM.yyyy HH:mm",new Locale("ru")).format(new java.util.Date(e.archivedAt));
            TextView sub=text(typeName(e.type)+" · В архиве с "+date,12,false);sub.setTextColor(MUTED);
            LinearLayout.LayoutParams subLp=new LinearLayout.LayoutParams(-1,-2);subLp.topMargin=dp(5);card.addView(sub,subLp);
            LinearLayout actions=new LinearLayout(DownloadArchiveActivity.this);actions.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(46));ap.topMargin=dp(9);card.addView(actions,ap);
            ImageView restore=image(R.drawable.ic_download_restore,"Восстановить");
            actions.addView(restore,new LinearLayout.LayoutParams(dp(46),dp(46)));
            TextView restoreText=text("Восстановить",13,true);restoreText.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rsp=new LinearLayout.LayoutParams(0,-1,1);rsp.leftMargin=dp(7);actions.addView(restoreText,rsp);
            ImageView rename=image(R.drawable.ic_more_vertical,"Меню загрузки");
            actions.addView(rename,new LinearLayout.LayoutParams(dp(46),dp(46)));
            ImageView delete=image(R.drawable.ic_download_archive_delete,"Удалить файлы");
            actions.addView(delete,new LinearLayout.LayoutParams(dp(46),dp(46)));
            rename.setOnClickListener(v->{
                PopupMenu menu=new PopupMenu(DownloadArchiveActivity.this,rename);
                menu.getMenu().add("Переименовать").setOnMenuItemClickListener(action->{rename(e);return true;});
                menu.getMenu().add("Перенести файлы").setOnMenuItemClickListener(action->{move(e);return true;});
                menu.show();
            });
            restore.setOnClickListener(v->restore(e));restoreText.setOnClickListener(v->restore(e));delete.setOnClickListener(v->delete(e));
        }
        final class Holder extends RecyclerView.ViewHolder{Holder(View v){super(v);}}
    }
    @Override public void onBackPressed(){finish();}
}
