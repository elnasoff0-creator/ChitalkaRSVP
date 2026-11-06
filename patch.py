from pathlib import Path
p=Path('app/src/main/java/ru/reader/rsvp')
def edit(name, fn):
 f=p/name;s=f.read_text();t=fn(s);assert t!=s,name;f.write_text(t)

def book(s):
 s=s.replace('public long bytes=0,total=0;', 'public ArrayList<String> relocatedUris=new ArrayList<>();public long bytes=0,total=0;')
 s=s.replace('j.put("chapters",a);j.put("states",s);', 'j.put("chapters",a);j.put("states",s);j.put("relocatedUris",new JSONArray(relocatedUris));')
 s=s.replace('if(s!=null)for(int i=0;i<s.length();i++)b.states.add(s.optInt(i));return b;', 'if(s!=null)for(int i=0;i<s.length();i++)b.states.add(s.optInt(i));JSONArray r=j.optJSONArray("relocatedUris");if(r!=null)for(int i=0;i<r.length();i++)b.relocatedUris.add(r.optString(i));return b;')
 s=s.replace('public static java.io.File trackFile(', 'public static String relocated(Job j,int i){return i>=0&&i<j.relocatedUris.size()?j.relocatedUris.get(i):"";}\n    public static java.io.File trackFile(')
 s=s.replace('for(int i=0;i<job.chapters.size();i++)trackFile(context,job,i).delete();', 'for(int i=0;i<job.chapters.size();i++){trackFile(context,job,i).delete();String uri=relocated(job,i);if(!uri.isEmpty())try{androidx.documentfile.provider.DocumentFile.fromSingleUri(context,android.net.Uri.parse(uri)).delete();}catch(Exception ignored){}}')
 s=s.replace('if(f.exists()&&f.length()>1024){book.uris.add(android.net.Uri.fromFile(f).toString());book.chapters.add(j.chapters.get(i).name);}', 'String relocated=relocated(j,i);if(!relocated.isEmpty()){book.uris.add(relocated);book.chapters.add(j.chapters.get(i).name);}else if(f.exists()&&f.length()>1024){book.uris.add(android.net.Uri.fromFile(f).toString());book.chapters.add(j.chapters.get(i).name);}')
 return s
edit('BookImportManager.java',book)

def repo(s):
 s=s.replace('if(state==100&&file.isFile()&&file.length()>1024){item.done++;item.downloadedBytes+=file.length();}', 'if(state==100&&!BookImportManager.relocated(job,i).isEmpty()){item.done++;}\n            else if(state==100&&file.isFile()&&file.length()>1024){item.done++;item.downloadedBytes+=file.length();}')
 return s
edit('DownloadsRepository.java',repo)

def worker(s):
 s=s.replace('if(job.states.get(i)==100&&BookImportManager.trackFile(this,job,i).exists()', 'if(job.states.get(i)==100&&!BookImportManager.relocated(job,i).isEmpty())continue;\n            if(job.states.get(i)==100&&BookImportManager.trackFile(this,job,i).exists()')
 return s
edit('DownloadWorker.java',worker)

def safe(s):
 s=s.replace('/** Verified, non-destructive SAF transfer. The source library is never invalidated. */', '/** Verified SAF migration; deletes sources only after durable library references are changed. */')
 s=s.replace('static final class Result {int copied,existing;long bytes;}', 'static final class Result {int copied,existing,deleted;long bytes;String note="";}')
 s=s.replace('final File file;final String uri,name;', 'final File file;final String uri,name;final int chapter;')
 s=s.replace('Source(File f,String n){file=f;uri=null;name=n;}', 'Source(File f,String n){this(f,n,-1);}\n        Source(File f,String n,int ch){file=f;uri=null;name=n;chapter=ch;}')
 s=s.replace('Source(String u,String n){file=null;uri=u;name=n;}', 'Source(String u,String n){file=null;uri=u;name=n;chapter=-1;}')
 s=s.replace('if(file.isFile()&&file.length()>0)sources.add(new Source(file,file.getName()));', 'if(file.isFile()&&file.length()>0)sources.add(new Source(file,file.getName(),i));')
 s=s.replace('if(sources.isEmpty())throw new IOException("Нет завершённых файлов для переноса");', 'if(sources.isEmpty())throw new IOException("Нет завершённых файлов для переноса");')
 s=s.replace('Result result=new Result();\n        for(Source source:sources){', 'Result result=new Result();\n        java.util.ArrayList<Migration> ready=new java.util.ArrayList<>();\n        for(Source source:sources){')
 s=s.replace('copyOne(ctx,source,targetDir,safe(segments[segments.length-1]),result);', 'DocumentFile destination=copyOne(ctx,source,targetDir,safe(segments[segments.length-1]),result);\n            ready.add(new Migration(source,destination));')
 s=s.replace('        return result;\n    }\n    private static DocumentFile directory', '''        if(DownloadsRepository.AUDIO.equals(item.type))migrateAudio(ctx,item,ready,result);
        else if(DownloadsRepository.LINK.equals(item.type))migrateLinks(ctx,item,ready,result);
        else result.note="Манга и Edge TTS: копии проверены, исходники нужны движку чтения и пока оставлены.";
        return result;
    }
    private static final class Migration {
        final Source source;final DocumentFile destination;
        Migration(Source s,DocumentFile d){source=s;destination=d;}
    }
    private static void migrateAudio(Context ctx,DownloadsRepository.Item item,java.util.ArrayList<Migration> moves,Result result)throws Exception{
        BookImportManager.Job job=null;
        for(BookImportManager.Job candidate:BookImportManager.load(ctx))if(candidate.source.equals(item.source)){job=candidate;break;}
        if(job==null)throw new IOException("Задание аудиокниги изменилось; исходники сохранены");
        for(Migration m:moves){
            if(m.source.chapter<0||m.source.chapter>=job.chapters.size()||!m.source.file.isFile())
                throw new IOException("Состав глав изменился; исходники сохранены");
            while(job.relocatedUris.size()<=m.source.chapter)job.relocatedUris.add("");
            job.relocatedUris.set(m.source.chapter,m.destination.getUri().toString());
        }
        // Persist source-of-truth first, then refresh the audio library URI index.
        BookImportManager.put(ctx,job);
        BookImportManager.updateLibrary(ctx,job);
        for(Migration m:moves){if(m.source.file.delete())result.deleted++;}
    }
    private static void migrateLinks(Context ctx,DownloadsRepository.Item item,java.util.ArrayList<Migration> moves,Result result)throws Exception{
        LinkDownloadStore.Batch batch=LinkDownloadStore.get(ctx,item.link.id);
        if(batch==null)throw new IOException("Запись загрузки изменена; исходники сохранены");
        java.util.HashMap<String,String> changed=new java.util.HashMap<>();
        for(Migration m:moves)changed.put(m.source.uri,m.destination.getUri().toString());
        int matched=0;
        for(LinkDownloadStore.Entry entry:batch.files){
            String newer=changed.get(entry.savedUri);
            if(newer!=null){entry.savedUri=newer;matched++;}
        }
        if(matched!=moves.size())throw new IOException("Состав загрузки изменился; исходники сохранены");
        // Update bookmarks without resetting reading progress or replacing book identities.
        updateReaderUris(ctx,changed);
        updateAudioUris(ctx,changed);
        LinkDownloadStore.put(ctx,batch);
        for(Migration m:moves){
            if(m.source.uri.equals(m.destination.getUri().toString()))continue;
            try{DocumentFile old=DocumentFile.fromSingleUri(ctx,Uri.parse(m.source.uri));
                if(old!=null&&old.isFile()&&old.delete())result.deleted++;
            }catch(Exception ignored){}
        }
    }
    private static void updateReaderUris(Context ctx,java.util.Map<String,String> changed)throws Exception{
        android.content.SharedPreferences prefs=ctx.getSharedPreferences("reader",0);
        org.json.JSONArray books=new org.json.JSONArray(prefs.getString("library","[]"));
        for(int i=0;i<books.length();i++){
            org.json.JSONObject book=books.optJSONObject(i);if(book==null)continue;
            String newer=changed.get(book.optString("uri"));if(newer!=null)book.put("uri",newer);
        }
        if(!prefs.edit().putString("library",books.toString()).commit())throw new IOException("Не сохранена библиотека книг");
    }
    private static void updateAudioUris(Context ctx,java.util.Map<String,String> changed)throws IOException{
        java.util.ArrayList<AudioLibrary.Book> books=AudioLibrary.load(ctx);
        for(AudioLibrary.Book book:books)for(int i=0;i<book.uris.size();i++){
            String newer=changed.get(book.uris.get(i));if(newer!=null)book.uris.set(i,newer);
        }
        org.json.JSONArray data=new org.json.JSONArray();for(AudioLibrary.Book book:books)data.put(book.json());
        if(!ctx.getSharedPreferences("reader",0).edit().putString(AudioLibrary.STORE,data.toString()).commit())
            throw new IOException("Не сохранена аудиобиблиотека");
    }
    private static DocumentFile directory''')
 s=s.replace('private static void copyOne(Context ctx,Source source,DocumentFile dir,String name,Result result)throws Exception{', 'private static DocumentFile copyOne(Context ctx,Source source,DocumentFile dir,String name,Result result)throws Exception{')
 s=s.replace('result.existing++;return;', 'result.existing++;return existing;')
 s=s.replace('result.copied++;\n        }catch(Exception ex)', 'result.copied++;return dir.findFile(name);\n        }catch(Exception ex)')
 return s
edit('SafeDownloadTransfer.java',safe)

def activity(s):
 s=s.replace('Выберите папку назначения. Файлы будут скопированы с проверкой целостности. Исходные файлы останутся на месте, поэтому книги, манга, прогресс чтения и загрузки продолжат работать. Существующие файлы не перезаписываются.', 'Выберите папку назначения. Копии проверяются по SHA-256. Для аудиокниг и файлов по ссылке пути в библиотеках обновляются, после чего старые файлы удаляются. Манга и Edge TTS пока сохраняют локальные оригиналы для корректного чтения. Существующие файлы не перезаписываются.')
 s=s.replace('"Готово: скопировано "+report.copied+\n                        ", уже были в папке "+report.existing+". Библиотека сохранена."', '"Скопировано "+report.copied+", уже были: "+report.existing+", старых удалено: "+report.deleted+". "+report.note')
 return s
edit('DownloadActivity.java',activity)
f=Path('app/build.gradle');s=f.read_text().replace('versionCode 13700','versionCode 13800').replace('1.11.137-beta','1.11.138-beta');f.write_text(s)
