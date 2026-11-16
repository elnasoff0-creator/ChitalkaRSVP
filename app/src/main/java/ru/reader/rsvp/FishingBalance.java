package ru.reader.rsvp;

/** Deterministic fishing rules. No Android dependency: probabilities can be regression-tested. */
final class FishingBalance {
    private FishingBalance() {}
    static final double[][] RARITY_PERCENT = {
        {87.80, 12.20, 0, 0, 0},
        {60, 16, 10, 8, 6},
        {59.76, 24.39, 15.85, 0, 0},
        {36, 24, 18, 12, 10},
        {26, 25, 21, 16, 12}
    };
    static final double[] QUALITY = {1.80, 1.40, 1.00, 0.70, 0.40};
    static final float[] FILL_BONUS = {0f, .025f, .05f, .10f, .15f};
    static final float[] FALL_MULTIPLIER = {1f, 1f, 1f, 1.05f, 1.10f};
    static int clampRod(int rod) {return Math.max(0, Math.min(4, rod));}

    /** All distributions sum to exactly 1. The explicit bans override rod class. */
    static double[] chances(int rodIndex, int grade) {
        int rod=clampRod(rodIndex);
        double q=QUALITY[Math.max(0, Math.min(4, grade))];
        double[] base=RARITY_PERCENT[rod];
        double[] result=new double[5];
        double reserved=0d;
        double eligibleWeight=0d;
        double[] eligible=new double[5];
        for (int i=0;i<5;i++) {
            // Training is limited to common/uncommon, Good to common/uncommon/rare.
            if ((rod==0 && i>=2) || (rod==2 && i>=3) || base[i]==0d) continue;
            if (i>rod) {
                // Previously inaccessible rarity stays capped by its Good-cast chance.
                result[i]=base[i]*Math.min(1d,q)/100d;
                reserved+=result[i];
            } else {
                eligible[i]=base[i]*(i>=2?q:1d);
                eligibleWeight+=eligible[i];
            }
        }
        double remaining=Math.max(0d,1d-reserved);
        if (eligibleWeight==0d) throw new IllegalStateException("No allowed rarity for rod "+rod);
        for (int i=0;i<5;i++) if (eligible[i]>0d)
            result[i]=remaining*eligible[i]/eligibleWeight;
        return result;
    }

    static float fatigueThreshold(int rarity, boolean shimmering) {
        float[] base={.90f,.80f,.70f,.65f,.60f};
        return base[Math.max(0,Math.min(4,rarity))]-(shimmering?.05f:0f);
    }
    static float recoverySeconds(int rod) {return 2f+2f*clampRod(rod);}
    static float rodFillMultiplier(int rod) {return 1f+FILL_BONUS[clampRod(rod)];}
    static float zoneFallSpeed(int rod) {return .34f*FALL_MULTIPLIER[clampRod(rod)];}
    static float zoneRiseSpeed() {return .53f;}
    static float movementMultiplier(boolean tired) {return tired?.70f:1f;}
    static float turnMultiplier(boolean tired, float timeLeft) {
        return tired?1f:1f+.22f*Math.max(0f,Math.min(1f,timeLeft/.30f));
    }
}
