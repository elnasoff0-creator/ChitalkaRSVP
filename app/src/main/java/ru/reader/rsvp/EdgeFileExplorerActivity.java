package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/** Browse persistent per-book Edge TTS audio files without relying on restricted Android/data access. */
public class EdgeFileExplorerActivity extends NavigationActivity {
    private static final int CHOOSE_EXPORT_FOLDER=509;
    private File folder;
    private ListView filesView;
    private TextView subtitle;
    private final ArrayList<File> files=new ArrayList<>();

    private TextView label(String text,int size) {
        TextView t=new TextView(this);
        t.setText(text); t.setTextSize(size); t.setTextColor(0xff30221e);
        t.setPadding(15,12,12,12);
        return t;
    }
    private Button button(String text) { Button b=new Button(this);b.setText(text);b.setAllCaps(false);return b; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String path=getIntent().getStringExtra("book_folder");
        try {
            File root=getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
            if(root==null)root=getFilesDir();
            File edgeAllowed=new File(root,"ChitalkaRSVP/Books").getCanonicalFile();
            File music=getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC);
            File audioAllowed=music==null?null:new File(music,"Audiobooks").getCanonicalFile();
            folder=new File(path==null?"":path).getCanonicalFile();
            boolean edgeFolder=folder.getName().equals("EdgeTTS")&&folder.getAbsolutePath().startsWith(edgeAllowed.getAbsolutePath()+File.separator);
            boolean audiobookFolder=audioAllowed!=null&&folder.getAbsolutePath().startsWith(audioAllowed.getAbsolutePath()+File.separator);
            if(!edgeFolder&&!audiobookFolder){finish();return;}
        } catch(Exception e) {finish();return;}
        LinearLayout rootView=new LinearLayout(this);
        rootView.setOrientation(LinearLayout.VERTICAL);
        rootView.setPadding(10,18,10,8);
        rootView.setBackgroundColor(0xfff3e7d5);
        setContentView(rootView);SafeSystemBars.matchBackground(this,getSharedPreferences("reader",0).getInt("bg",0xfff3e7d5));
        TextView menuTitle=label("☰  Главное меню",17);menuTitle.setPadding(8,10,8,12);
        menuTitle.setOnClickListener(v->UniversalDrawer.show(this));rootView.addView(menuTitle);
        rootView.addView(label("Файлы аудиокниги · "+(folder.getName().equals("EdgeTTS")?folder.getParentFile().getName():folder.getName()),18));
        subtitle=label(folder.getAbsolutePath(),11);
        rootView.addView(subtitle);
        LinearLayout actions=new LinearLayout(this);
        rootView.addView(actions);
        Button refresh=button("Обновить");refresh.setOnClickListener(v->refresh());
        actions.addView(refresh,new LinearLayout.LayoutParams(0,53,1));
        Button export=button("Экспорт в проводник");
        export.setOnClickListener(v->chooseFolder());
        actions.addView(export,new LinearLayout.LayoutParams(0,53,2));
        filesView=new ListView(this);
        rootView.addView(filesView,new LinearLayout.LayoutParams(-1,0,1));
        filesView.setOnItemClickListener((parent,view,position,id)->openFile(files.get(position)));
        rootView.addView(label("Файлы доступны здесь через встроенный проводник. Чтобы увидеть их в системном файловом менеджере, выберите папку экспорта.",12));
        refresh();
    }

    private void refresh() {
        files.clear();
        File[] list=folder.listFiles(f->f.isFile() && (EdgeAudioFormat.forFilename(f.getName())!=null||f.getName().equals("sentences.json")));
        if(list!=null) {
            Arrays.sort(list,Comparator.comparing(File::getName));
            files.addAll(Arrays.asList(list));
        }
        ArrayList<String> names=new ArrayList<>();
        long total=0;
        for(File f:files) {
            total+=f.length();
            names.add(f.getName()+"   ("+String.format(java.util.Locale.ROOT,"%.1f КБ",f.length()/1024.0)+")");
        }
        filesView.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,names));
        subtitle.setText(files.size()+" файлов · "+String.format(java.util.Locale.ROOT,"%.1f МБ",total/1024.0/1024.0)+"\n"+folder.getAbsolutePath());
    }

    private void openFile(File file) {
        try {
            Uri content=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",file);
            Intent intent=new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(content,EdgeAudioFormat.forFilename(file.getName())!=null?EdgeAudioFormat.forFilename(file.getName()).mime:"application/json");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent,"Открыть файл"));
        } catch(Exception ex) {
            Toast.makeText(this,"Не удалось открыть: "+ex.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private void chooseFolder() {
        try {
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(pick,CHOOSE_EXPORT_FOLDER);
        } catch(Exception e) {Toast.makeText(this,"Системный проводник недоступен",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=CHOOSE_EXPORT_FOLDER||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri tree=data.getData();
        new Thread(()-> {
            int exported=0;
            try {
                Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
                Uri outDir=DocumentsContract.createDocument(getContentResolver(),parent,
                        DocumentsContract.Document.MIME_TYPE_DIR,"ChitalkaRSVP - " + folder.getParentFile().getName());
                if(outDir==null)throw new Exception("Не удалось создать папку");
                File[] clips=folder.listFiles(f->f.isFile() && (EdgeAudioFormat.forFilename(f.getName())!=null||f.getName().equals("sentences.json")));
                if(clips!=null)for(File f:clips) {
                    Uri doc=DocumentsContract.createDocument(getContentResolver(),outDir,
                            EdgeAudioFormat.forFilename(f.getName())!=null?EdgeAudioFormat.forFilename(f.getName()).mime:"application/json",f.getName());
                    if(doc==null)continue;
                    try(InputStream in=new FileInputStream(f);OutputStream out=getContentResolver().openOutputStream(doc)) {
                        if(out==null)continue;
                        byte[] block=new byte[16384]; int n;
                        while((n=in.read(block))!=-1)out.write(block,0,n);
                        exported++;
                    }
                }
                int count=exported;
                runOnUiThread(()->new SwipeDialogs.Builder(this).setMessage("Экспортировано файлов: "+count+". Они теперь доступны в выбранной папке системного проводника.")
                        .setPositiveButton("OK",null).show());
            } catch(Exception e) {
                String reason=e.getMessage();
                runOnUiThread(()->Toast.makeText(this,"Ошибка экспорта: "+reason,Toast.LENGTH_LONG).show());
            }
        },"edge-export-audio").start();
    }
}
