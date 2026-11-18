package ru.reader.rsvp;
import java.util.*;
public class TestDownload179{
  static void must(boolean b,String e){if(!b)throw new AssertionError(e);}
  public static void main(String[] args){
    String id="edge:179";
    ArrayList<DownloadProgressTracker.Seed> a=new ArrayList<>();
    a.add(new DownloadProgressTracker.Seed("edge:0",100,true,90));
    a.add(new DownloadProgressTracker.Seed("edge:1",100,true,10));
    DownloadProgressTracker.Summary s=DownloadProgressTracker.plan(id,a);
    must(s.percent==100&&s.finished==2,"complete");
    a.set(0,new DownloadProgressTracker.Seed("edge:0",0,false,90));
    s=DownloadProgressTracker.plan(id,a);
    must(s.percent==10&&s.finished==1,"dictionary invalidation");
    s=DownloadProgressTracker.update(id,"edge:0",500,false,500);
    must(s.percent==55,"weighted progress");
    a.set(0,new DownloadProgressTracker.Seed("edge:0",0,false,90));
    s=DownloadProgressTracker.plan(id,a);
    must(s.percent==10,"authoritative reset from verification");
    a.set(0,new DownloadProgressTracker.Seed("edge:0",100,true,90));
    s=DownloadProgressTracker.plan(id,a);
    must(s.percent==100,"verified completion restored");
    System.out.println("PASS v179: weighted progress, dictionary invalidation, recovery");
  }
}
