package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.util.ArrayList;

/** Explicitly requested deletion of app-managed downloads, never imported source books. */
final class DownloadArchiveDelete {
    private DownloadArchiveDelete(){}
    static void delete(Context c,DownloadArchiveStore.Entry archived)throws Exception{
        DownloadsRepository.Item item=null;
        for(DownloadsRepository.Item e:DownloadsRepository.load(c,null))if(archived.id.equals(e.id)){item=e;break;}
        if(item!=null&&item.running)throw new Exception("Загрузка активна: сначала остановите её");
        switch(archived.type){
            case DownloadsRepository.AUDIO:
                if(item==null||item.job==null)throw new Exception("Задание аудиокниги не найдено; удаление файлов нельзя подтвердить");
                BookImportManager.Job job=item.job;
                // Delete only tracks owned by this download job. Originals are never included.
                for(int k=0;k<job.chapters.size();k++){
                    File file=BookImportManager.trackFile(c,job,k);
                    if(file.isFile()&&!file.delete())throw new Exception("Не удалось удалить "+file.getName());
                    String moved=BookImportManager.relocated(job,k);
                    if(!moved.isEmpty())deleteDocument(c,moved);
                }
                BookImportManager.remove(c,job,false);
                AudiobookCoverStore.coverFile(c,job).delete();
                ArrayList<AudioLibrary.Book> books=AudioLibrary.load(c);
                books.removeIf(b->b.id.equals("url:"+job.source));AudioLibrary.save(c,books);
                break;
            case DownloadsRepository.LINK:
                LinkDownloadStore.Batch batch=item!=null?item.link:LinkDownloadStore.get(c,archived.id.replaceFirst("^link:",""));
                if(batch==null)throw new Exception("Задание загрузки по ссылке не найдено");
                for(LinkDownloadStore.Entry e:batch.files)if(!e.savedUri.isEmpty())deleteDocument(c,e.savedUri);
                LinkDownloadStore.remove(c,batch.id);
                ArrayList<AudioLibrary.Book> linked=AudioLibrary.load(c);
                linked.removeIf(b->b.id.equals("direct:"+batch.id));AudioLibrary.save(c,linked);
                break;
            case DownloadsRepository.RANOBE:
                String novelSlug=archived.source;
                if(novelSlug.isEmpty())novelSlug=archived.id.replaceFirst("^ranobe:","");
                if(RanobeLibStorage.series(c,novelSlug).isEmpty())
                    throw new Exception("Задание ранобэ отсутствует; удаление отменено");
                RanobeLibStorage.deleteSeries(c,novelSlug);
                break;
            case DownloadsRepository.MANGA:
                String slug=archived.source;
                if(slug.isEmpty())slug=archived.id.replaceFirst("^manga:","");
                File safeRoot=MangaLibStorage.root(c).getCanonicalFile();
                File prefix=safeRoot;
                int matchingJobs=0;
                for(MangaLibStorage.Job j:MangaLibStorage.jobs(c))if(j.slug.equalsIgnoreCase(slug)){
                    matchingJobs++;
                    if(!j.file.isEmpty()){
                        File file=new File(j.file).getCanonicalFile();
                        // Never follow a path outside the application's MangaLib storage.
                        if(!file.getCanonicalPath().startsWith(prefix.getCanonicalPath()+File.separator))
                            throw new Exception("Файл находится вне папки MangaLib");
                        if(file.isFile()&&!file.delete())throw new Exception("Не удалось удалить CBZ: "+file.getName());
                    }
                    j.file="";j.done=0;j.total=0;j.bytes=0;j.status="cancelled";
                    MangaLibStorage.put(c,j);
                }
                if(matchingJobs==0)throw new Exception("Задание манги не найдено; файлы не проверены");
                MangaDownloadCover.invalidate(c,slug);
                break;
            case DownloadsRepository.EDGE:
                String uri=archived.bookUri;
                if(uri.isEmpty())uri=archived.id.replaceFirst("^edge:","");
                if(EdgeFullBookService.isServiceAlive()){
                    EdgeFullBookStore.Job work=EdgeFullBookStore.find(c,uri,archived.sourceTitle);
                    if(work!=null&&(EdgeFullBookStore.RUNNING.equals(work.status)
                        ||EdgeFullBookStore.QUEUED.equals(work.status)
                        ||EdgeFullBookStore.WAITING.equals(work.status)
                        ||EdgeFullBookStore.SAVING.equals(work.status)))
                        throw new Exception("Сначала приостановите загрузку Edge TTS");
                }
                if(!EdgeFolderPicker.available(c))throw new Exception("Нет доступа к папке Edge TTS. Выберите её повторно");
                if(!EdgeChapterArchive.deleteSavedBook(c,uri))
                    throw new Exception("Не удалось удалить сохранённые главы в выбранной папке Edge TTS");
                // Remove only this book's task metadata and temporary synthesis cache.
                EdgeFullBookStore.Job edgeJob=EdgeFullBookStore.find(c,uri,archived.sourceTitle);
                if(edgeJob!=null&&edgeJob.directory!=null){
                    String root=new File(c.getFilesDir(),"EdgeJobs").getCanonicalPath()+File.separator;
                    if(!edgeJob.directory.getCanonicalPath().startsWith(root))
                        throw new Exception("Недопустимый путь задания Edge TTS");
                    deleteFiles(edgeJob.directory);
                }
                File cached=EdgeBookStorage.bookFolder(c,uri,archived.sourceTitle);
                if(cached.getCanonicalPath().startsWith(EdgeBookStorage.booksRoot(c).getCanonicalPath()+File.separator))deleteFiles(cached);
                DownloadsRepository.hideEdge(c,uri);
                EdgeChapterArchive.invalidate();
                break;
            default:throw new Exception("Неизвестный тип загрузки");
        }
    }
    private static void deleteDocument(Context c,String value)throws Exception{
        Uri u=Uri.parse(value);
        DocumentFile doc=DocumentFile.fromSingleUri(c,u);
        if(doc==null)throw new Exception("Не удалось открыть загруженный файл");
        if(doc.exists()&&!doc.delete())throw new Exception("Нет доступа к удалению загруженного файла");
    }
    private static void deleteFiles(File f)throws Exception{
        if(f==null||!f.exists())return;
        File[] nested=f.listFiles();if(nested!=null)for(File child:nested)deleteFiles(child);
        if(!f.delete())throw new Exception("Не удалось удалить "+f.getName());
    }
}
