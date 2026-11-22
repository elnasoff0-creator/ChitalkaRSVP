package ru.reader.rsvp;

import android.os.SystemClock;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** One cancelable frame-loop to follow the ACTUAL spoken word in a RecyclerView.
 * This class does not seek TTS, change highlighted spans or own text selection.
 */
final class ReaderTtsScrollController {
    interface Host {
        WordLocation locateWord(int word);
        long navigationEpoch();
        boolean mayFollow(boolean explicitReturn);
        void jumpToAdapterPosition(int position,int offsetPx);
    }
    static final class WordLocation {
        final int adapterPosition;
        final boolean hasPosition;
        final int centerY;
        WordLocation(int adapterPosition,int centerY,boolean laidOut){
            this.adapterPosition=adapterPosition;this.centerY=centerY;this.hasPosition=laidOut;
        }
    }
    private final RecyclerView list;
    private final LinearLayoutManager layout;
    private final Host host;
    private final int insetTop,insetBottom,tolerancePx,minStep,maxStep;
    private int targetWord=-1,frameCount=0,stallFrames=0;
    private long targetEpoch=-1L,lastSkipLogAt=0L,animationStartedAt=0L;
    private boolean frameQueued=false,animating=false,explicit=false,disposed=false;
    private final Runnable frame=new Runnable(){@Override public void run(){
        frameQueued=false;
        if(disposed||targetWord<0)return;
        if(targetEpoch!=host.navigationEpoch()){
            cancel("STALE_NAVIGATION");return;
        }
        if(!host.mayFollow(explicit)){
            cancel("FOLLOW_SUSPENDED");return;
        }
        int height=list.getHeight();
        if(height<=insetTop+insetBottom+24){logSkip("NO_VIEWPORT");return;}
        WordLocation loc=host.locateWord(targetWord);
        if(loc==null){logSkip("NO_WORD");return;}
        int top=insetTop,bottom=height-insetBottom;
        int remaining;
        if(loc.hasPosition){
            remaining=ReaderTtsScrollPolicy.shift(loc.centerY,top,bottom,explicit,tolerancePx);
        }else{
            int first=layout.findFirstVisibleItemPosition();
            int last=layout.findLastVisibleItemPosition();
            if(first<0||last<0){
                if(++stallFrames<45)queueFrame();else logSkip("LAYOUT_TIMEOUT");
                return;
            }
            int direction=ReaderTtsScrollPolicy.missingDirection(loc.adapterPosition,first,last);
            if(direction==0){
                // RecyclerView is temporarily rebinding the visible row.
                if(++stallFrames<18)queueFrame();else logSkip("VISIBLE_ROW_NOT_LAID_OUT");
                return;
            }
            if(explicit&&Math.abs(loc.adapterPosition-(direction>0?last:first))>2){
                ReaderDiagnosticLog.event("TTS_SCROLL","FOLLOW_JUMP_EXPLICIT word="+
                    targetWord+" adapter="+loc.adapterPosition+" epoch="+targetEpoch);
                host.jumpToAdapterPosition(loc.adapterPosition,top);
                explicit=false;stallFrames=0;queueFrame();return;
            }
            // Ordinary TTS moves through neighbouring rows incrementally,
            // never using an abrupt scrollToPositionWithOffset jump.
            remaining=direction*Math.max(minStep*3,Math.min(maxStep*4,height/4));
        }
        stallFrames=0;
        if(remaining==0){finish();return;}
        if(!animating){
            animating=true;animationStartedAt=SystemClock.elapsedRealtime();
            ReaderDiagnosticLog.event("TTS_SCROLL","FOLLOW_START word="+targetWord+
                " epoch="+targetEpoch+" delta_px="+remaining);
        }
        int step=ReaderTtsScrollPolicy.frameStep(remaining,minStep,maxStep);
        int before=list.computeVerticalScrollOffset();
        list.scrollBy(0,step);
        int after=list.computeVerticalScrollOffset();
        if(before==after){
            if(++stallFrames>=3){logSkip("EDGE_REACHED");finish();return;}
        }else stallFrames=0;
        frameCount++;
        queueFrame();
    }};

    ReaderTtsScrollController(RecyclerView list,LinearLayoutManager layout,Host host,
                              int topInset,int bottomInset,int tolerance,int minimum,int maximum){
        this.list=list;this.layout=layout;this.host=host;
        insetTop=topInset;insetBottom=bottomInset;tolerancePx=tolerance;
        minStep=minimum;maxStep=maximum;
    }
    void follow(int word,boolean explicitReturn){
        if(disposed||word<0)return;
        long epoch=host.navigationEpoch();
        if(!host.mayFollow(explicitReturn)){
            logSkip("MANUAL_OR_SELECTION");return;
        }
        boolean newTarget=targetWord!=word||targetEpoch!=epoch||explicitReturn;
        if(newTarget){
            targetWord=word;targetEpoch=epoch;explicit=explicitReturn;
            if(explicitReturn)ReaderDiagnosticLog.event("TTS_SCROLL",
                "RETURN_TO_SPEECH word="+word+" epoch="+epoch);
            WordLocation loc=host.locateWord(word);
            if(loc!=null){
                int shift=loc.hasPosition?ReaderTtsScrollPolicy.shift(loc.centerY,insetTop,
                    list.getHeight()-insetBottom,explicitReturn,tolerancePx):Integer.MAX_VALUE;
                if(shift!=0)ReaderDiagnosticLog.event("TTS_SCROLL","FOLLOW_TARGET word="+word+
                    " epoch="+epoch+" delta_px="+(shift==Integer.MAX_VALUE?"unlaid":shift));
            }
        }
        queueFrame();
    }
    private void queueFrame(){
        if(frameQueued||disposed||targetWord<0)return;
        frameQueued=true;list.postOnAnimation(frame);
    }
    private void logSkip(String reason){
        long now=SystemClock.elapsedRealtime();
        if(now-lastSkipLogAt>1000){
            lastSkipLogAt=now;
            ReaderDiagnosticLog.event("TTS_SCROLL","FOLLOW_SKIPPED reason="+reason+
                " word="+targetWord+" epoch="+targetEpoch);
        }
    }
    private void finish(){
        if(animating)ReaderDiagnosticLog.event("TTS_SCROLL","FOLLOW_FINISHED word="+
            targetWord+" epoch="+targetEpoch+" frames="+frameCount+
            " elapsed_ms="+(SystemClock.elapsedRealtime()-animationStartedAt));
        animating=false;frameCount=0;targetWord=-1;explicit=false;
    }
    void cancel(String reason){
        if(frameQueued){list.removeCallbacks(frame);frameQueued=false;}
        if(targetWord>=0||animating){
            ReaderDiagnosticLog.event("TTS_SCROLL","FOLLOW_CANCELLED reason="+reason+
                " word="+targetWord+" epoch="+targetEpoch+
                " elapsed_ms="+(animating?SystemClock.elapsedRealtime()-animationStartedAt:0));
        }
        animating=false;targetWord=-1;frameCount=0;explicit=false;stallFrames=0;
    }
    void dispose(){cancel("DISPOSE");disposed=true;}
}
