package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Verified SAF transfers for books imported directly into the libraries. */
final class LibraryMoveManager {
    private LibraryMoveManager() { }
    static final class Report {
        String newUri="",note="";
        int copied,existing,deleted;
    }
    static Report text(Context ctx,String original,String title,Uri destination)throws Exception {
        if(original==null||original.isEmpty())throw new IOException("Исходная книга не выбрана");
        String slug=RanobeLibStorage.slugForBookUri(ctx,original);
        if(!slug.isEmpty()){
            DownloadsRepository.Item found=null;
            for(DownloadsRepository.Item item:DownloadsRepository.load(ctx,null))
                if(DownloadsRepository.RANOBE.equals(item.type)&&slug.equals(item.source)){found=item;break;}
            if(found!=null){
                SafeDownloadTransfer.Result r=SafeDownloadTransfer.copy(ctx,found,destination);
                Report out=new Report();out.copied=r.copied;out.existing=r.existing;
                out.deleted=r.deleted;out.newUri=RanobeLibStorage.bookUri(ctx,slug);out.note=r.note;return out;
            }
        }
        Uri source=Uri.parse(original);
        DocumentFile folder=SafeDownloadTransfer.checkRoot(ctx,destination);
        Report out=new Report();
        String filename;
        File file=null;
        if("file".equalsIgnoreCase(source.getScheme())){
            file=new File(source.getPath()).getCanonicalFile();
            if(!file.isFile())throw new IOException("Книга отсутствует: "+file.getName());
            filename=file.getName();
        }else if("content".equalsIgnoreCase(source.getScheme())){
            DocumentFile doc=DocumentFile.fromSingleUri(ctx,source);
            if(doc==null||!doc.isFile())throw new IOException("Файл книги недоступен");
            filename=doc.getName()==null?"book.html":doc.getName();
        }else throw new IOException("Перенос такого адреса не поддерживается");
        DocumentFile bookDir=SafeDownloadTransfer.directory(folder,SafeDownloadTransfer.safe("Книга - "+title));
        SafeDownloadTransfer.Result progress=new SafeDownloadTransfer.Result();
        DocumentFile target=file!=null?SafeDownloadTransfer.copyFile(ctx,file,bookDir,filename,progress):
            SafeDownloadTransfer.copyUri(ctx,original,bookDir,filename,progress);
        // For managed HTML documents, preserve their relative chapter illustrations.
        // Never copy an arbitrary third-party folder containing unrelated files.
        if(file!=null&&"book.html".equalsIgnoreCase(filename)){
            File parent=file.getParentFile();
            if(SafeDownloadTransfer.appOwned(ctx,file)){
                for(String name:new String[]{"images","assets"}){
                    File assets=new File(parent,name);
                    if(assets.isDirectory())SafeDownloadTransfer.copyFolder(ctx,assets,
                        SafeDownloadTransfer.directory(bookDir,name),progress);
                }
                File image=new File(parent,"cover.jpg");
                if(image.isFile())SafeDownloadTransfer.copyFile(ctx,image,bookDir,"cover.jpg",progress);
            }
        }
        out.newUri=target.getUri().toString();
        // First update the persistent library and reading progress, then remove owned originals.
        BookUriMigration.text(ctx,original,out.newUri);
        out.copied=progress.copied;out.existing=progress.existing;
        if(file!=null&&SafeDownloadTransfer.appOwned(ctx,file)){
            if(!file.delete())out.note="Исходный файл оставлен: удаление не разрешено";
            else out.deleted++;
            if("book.html".equalsIgnoreCase(filename)){
                File parent=file.getParentFile();
                for(String name:new String[]{"images","assets"}){
                    File assets=new File(parent,name);
                    if(assets.isDirectory())SafeDownloadTransfer.removeCopiedFiles(assets);
                }
                File cover=new File(parent,"cover.jpg");
                if(cover.isFile()&&cover.delete())out.deleted++;
            }
        }else out.note="Импортированный исходник оставлен в прежней папке (не принадлежит приложению)";
        return out;
    }

    static Report comic(Context ctx,ComicLibrary.Comic comic,Uri destination)throws Exception {
        if(comic==null)throw new IOException("Комикс не найден");
        if(comic.uri.startsWith("mangalib:")){
            DownloadsRepository.Item download=null;
            for(DownloadsRepository.Item i:DownloadsRepository.load(ctx,null))
                if(DownloadsRepository.MANGA.equals(i.type)&&comic.uri.equals(i.bookUri)){download=i;break;}
            if(download==null)throw new IOException("Загрузка MangaLib отсутствует");
            SafeDownloadTransfer.Result r=SafeDownloadTransfer.copy(ctx,download,destination);
            Report out=new Report();out.copied=r.copied;out.existing=r.existing;out.deleted=r.deleted;out.note=r.note;
            out.newUri=comic.uri;return out;
        }
        Uri source=Uri.parse(comic.uri);
        DocumentFile input=SafTreeDirectory.resolve(ctx,source);
        if(input==null||!input.isDirectory()){
            DocumentFile single=DocumentFile.fromSingleUri(ctx,source);
            if(single==null||!single.isFile())throw new IOException("Исходный комикс недоступен");
            DocumentFile target=SafeDownloadTransfer.directory(SafeDownloadTransfer.checkRoot(ctx,destination),
                SafeDownloadTransfer.safe("Комикс - "+comic.title));
            SafeDownloadTransfer.Result data=new SafeDownloadTransfer.Result();
            DocumentFile moved=SafeDownloadTransfer.copyUri(ctx,comic.uri,target,single.getName(),data);
            Report out=new Report();out.newUri=moved.getUri().toString();out.copied=data.copied;out.existing=data.existing;
            updateComic(ctx,comic,out.newUri);out.note="Импортированный оригинал оставлен";return out;
        }
        if(SafTreeDirectory.containsOrSame(source,destination))
            throw new IOException("Нельзя переместить папку комикса внутрь неё самой");
        DocumentFile root=SafeDownloadTransfer.directory(SafeDownloadTransfer.checkRoot(ctx,destination),
            SafeDownloadTransfer.safe("Комикс - "+comic.title));
        if(source.equals(root.getUri()))throw new IOException("Выбрана исходная папка");
        SafeDownloadTransfer.Result data=new SafeDownloadTransfer.Result();
        SafeDownloadTransfer.copyDocumentTree(ctx,input,root,data,0);
        Report out=new Report();out.newUri=root.getUri().toString();out.copied=data.copied;out.existing=data.existing;
        updateComic(ctx,comic,out.newUri);
        out.note="Импортированная пользователем папка сохранена без удаления оригинала";
        return out;
    }
    private static void updateComic(Context ctx,ComicLibrary.Comic comic,String newUri)throws IOException{
        String old=comic.uri;
        if(old.equals(newUri))return;
        // Copy state and change only the URI identity; the selected chapter, page and
        // scroll position are stored in the Comic object and therefore remain unchanged.
        ComicLibrary.Comic changed=ComicLibrary.Comic.read(comic.json());changed.uri=newUri;
        java.util.ArrayList<ComicLibrary.Comic> all=ComicLibrary.list(ctx);
        for(int n=0;n<all.size();n++)if(all.get(n).uri.equals(old)){all.set(n,changed);break;}
        ComicLibrary.write(ctx,all);
        BookTitleStore.moveComicId(ctx,old,newUri);
        android.content.SharedPreferences p=ctx.getSharedPreferences("reader",0);
        if(p.contains("comic_manual_cover:"+old))p.edit().putInt("comic_manual_cover:"+newUri,
            p.getInt("comic_manual_cover:"+old,0)).commit();
    }

    static Report audio(Context ctx,AudioLibrary.Book book,Uri destination)throws Exception {
        if(book==null||book.uris.isEmpty())throw new IOException("Аудиокнига пуста");
        if(book.id.startsWith("url:")){
            for(DownloadsRepository.Item item:DownloadsRepository.load(ctx,null))
                if(DownloadsRepository.AUDIO.equals(item.type)&&("url:"+item.source).equals(book.id)){
                    SafeDownloadTransfer.Result r=SafeDownloadTransfer.copy(ctx,item,destination);
                    Report out=new Report();out.copied=r.copied;out.existing=r.existing;
                    out.deleted=r.deleted;out.note=r.note;return out;
                }
        }
        DocumentFile dir=SafeDownloadTransfer.directory(SafeDownloadTransfer.checkRoot(ctx,destination),
            SafeDownloadTransfer.safe("Аудиокнига - "+book.name));
        SafeDownloadTransfer.Result totals=new SafeDownloadTransfer.Result();
        ArrayList<String> relocated=new ArrayList<>();
        for(int i=0;i<book.uris.size();i++){
            String source=book.uris.get(i),name;
            Uri uri=Uri.parse(source);File local=null;
            if("file".equalsIgnoreCase(uri.getScheme())){
                local=new File(uri.getPath());name=local.getName();
            }else{
                DocumentFile single=DocumentFile.fromSingleUri(ctx,uri);
                name=single!=null&&single.getName()!=null?single.getName():String.format(java.util.Locale.ROOT,"%03d.mp3",i+1);
            }
            DocumentFile dest=local!=null?SafeDownloadTransfer.copyFile(ctx,local,dir,name,totals):
                SafeDownloadTransfer.copyUri(ctx,source,dir,name,totals);
            relocated.add(dest.getUri().toString());
        }
        // Record original URIs, then commit the audio library as a single unit.
        AudioLibrary.Book target=AudioLibrary.Book.parse(book.json());
        target.uris.clear();target.uris.addAll(relocated);
        ArrayList<AudioLibrary.Book> all=AudioLibrary.load(ctx);boolean changed=false;
        for(int i=0;i<all.size();i++)if(all.get(i).id.equals(book.id)){all.set(i,target);changed=true;break;}
        if(!changed)throw new IOException("Запись аудиокниги исчезла; исходники сохранены");
        org.json.JSONArray output=new org.json.JSONArray();for(AudioLibrary.Book b:all)output.put(b.json());
        if(!ctx.getSharedPreferences("reader",0).edit().putString(AudioLibrary.STORE,output.toString()).commit())
            throw new IOException("Не удалось сохранить обновлённую Аудиотеку");
        Report report=new Report();report.copied=totals.copied;report.existing=totals.existing;
        for(int i=0;i<book.uris.size();i++){
            String old=book.uris.get(i);
            Uri uri=Uri.parse(old);
            if("file".equalsIgnoreCase(uri.getScheme())){
                File local=new File(uri.getPath());
                if(SafeDownloadTransfer.appOwned(ctx,local)&&local.isFile()&&local.delete())report.deleted++;
            }
        }
        report.note="Аудиотека обновлена; внешние пользовательские источники не удаляются";
        return report;
    }
}
