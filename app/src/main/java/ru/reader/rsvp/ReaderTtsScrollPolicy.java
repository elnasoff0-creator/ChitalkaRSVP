package ru.reader.rsvp;

/** Pure viewport follow math; no audio or selection state is mutated. */
final class ReaderTtsScrollPolicy {
    private ReaderTtsScrollPolicy() {}

    /** Signed pixels needed to put the active WORD in a comfortable screen zone. */
    static int shift(int wordCenterY,int viewportTop,int viewportBottom,
                     boolean explicitReturn,int tolerancePx){
        int usable=Math.max(0,viewportBottom-viewportTop);
        if(usable<24)return 0;
        int anchor=viewportTop+Math.round(usable*(explicitReturn?.50f:.61f));
        if(!explicitReturn){
            int upper=viewportTop+Math.max(tolerancePx,Math.round(usable*.10f));
            int lower=viewportTop+Math.round(usable*.69f);
            if(wordCenterY>=upper&&wordCenterY<=lower)return 0;
            if(wordCenterY<upper)anchor=viewportTop+Math.round(usable*.24f);
        }
        int shift=wordCenterY-anchor;
        return Math.abs(shift)<=Math.max(1,tolerancePx)?0:shift;
    }

    /** Once a follow animation starts, the word must reach the anchor. The
     * activation dead-zone is NOT a completion criterion (v1.11.189 regression).
     * All targets use the same smooth frame loop, including explicit return.
     */
    static int movingShift(int wordCenterY,int viewportTop,int viewportBottom,
                           int tolerancePx,int anchorPercent){
        int usable=Math.max(0,viewportBottom-viewportTop);
        if(usable<24)return 0;
        int anchor=viewportTop+Math.round(usable*anchorPercent/100f);
        int remaining=wordCenterY-anchor;
        return Math.abs(remaining)<=Math.max(1,tolerancePx)?0:remaining;
    }

    /** Chosen once when an animation starts; remains stable until it completes. */
    static int anchorPercent(int wordCenterY,int viewportTop,int viewportBottom,boolean explicitReturn){
        if(explicitReturn)return 50;
        int usable=Math.max(0,viewportBottom-viewportTop);
        return wordCenterY<viewportTop+Math.round(usable*.10f)?24:61;
    }

    /** Bounded easing; recomputing on every frame retargets without a second animator. */
    static int frameStep(int remainingPx,int minStepPx,int maxStepPx){
        if(remainingPx==0)return 0;
        int step=Math.max(Math.max(1,minStepPx),
            Math.min(Math.max(1,maxStepPx),Math.round(Math.abs(remainingPx)*.20f)));
        return Integer.signum(remainingPx)*Math.min(Math.abs(remainingPx),step);
    }

    static int missingDirection(int targetAdapterPosition,int firstVisible,int lastVisible){
        if(targetAdapterPosition>lastVisible)return 1;
        if(targetAdapterPosition<firstVisible)return -1;
        return 0;
    }
}
