package ru.reader.rsvp;

import java.util.ArrayList;

/** Pure Java 17 deterministic invariants for fast, revisioned progress aggregation. */
public final class TestDownloadProgress176 {
    private static void verify(boolean pass,String info){if(!pass)throw new AssertionError(info);}
    public static void main(String[] args){
        String id="audio:large";
        ArrayList<DownloadProgressTracker.Seed> rows=new ArrayList<>();
        for(int i=0;i<500;i++)rows.add(new DownloadProgressTracker.Seed("audio:large:"+i,0,false));
        DownloadProgressTracker.Summary baseline=DownloadProgressTracker.plan(id,rows);
        verify(baseline.chapters==500&&baseline.weightedTotal==500000,"500 chapters plan");
        long previous=baseline.weightedDone;
        for(int j=1;j<=10000;j++){
            DownloadProgressTracker.Summary x=DownloadProgressTracker.update(id,"audio:large:237",j%997,false,j);
            verify(x.weightedTotal==500000,"stable denominator");
        }
        DownloadProgressTracker.Summary latest=DownloadProgressTracker.summary(id);
        verify(latest.weightedDone>=0&&latest.weightedDone<=999,"fraction bounded");
        long one=latest.weightedDone;
        verify(DownloadProgressTracker.update(id,"audio:large:237",100,false,9999).weightedDone==one,"stale sequence dropped");
        long committedSequence=10001;
        DownloadProgressTracker.Summary done=DownloadProgressTracker.update(id,"audio:large:237",1000,true,committedSequence);
        verify(done.weightedDone==1000&&done.finished==1,"100% only durable commit");
        verify(DownloadProgressTracker.update(id,"audio:large:237",0,false,10002).finished==1,"no rollback from network");
        DownloadProgressTracker.plan("manga:another",java.util.Collections.singletonList(
            new DownloadProgressTracker.Seed("manga:another:1",100,true)));
        verify(DownloadProgressTracker.summary("manga:another").percent==100,"independent book");
        verify(DownloadProgressTracker.summary(id).finished==1,"no cross-book mutation");
        ArrayList<DownloadProgressTracker.Seed> changed=new ArrayList<>(rows);
        changed.remove(498);changed.remove(497);
        DownloadProgressTracker.Summary changedPlan=DownloadProgressTracker.plan(id,changed);
        verify(changedPlan.chapters==498&&changedPlan.weightedTotal==498000,"structural plan update");
        DownloadProgressTracker.Summary reset=DownloadProgressTracker.plan(id,changed);
        verify(reset.finished==0&&reset.weightedDone==0,"authoritative explicit delete reset");
        DownloadProgressTracker.forget(id);
        verify(DownloadProgressTracker.summary(id)==null,"memory evict");
        System.out.println("PASS: v176 500-chapter/10000-event tracker, revisions, isolated books, reset");
    }
}
