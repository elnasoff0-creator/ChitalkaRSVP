package ru.reader.rsvp;

/** Pure JVM regression tests. Compile with FishingBalance and FishingFatigue, no Android SDK. */
public final class TestFishing171 {
    private static int tests=0;
    private static void near(double actual,double expected,double epsilon,String why) {
        tests++;
        if (Math.abs(actual-expected)>epsilon) throw new AssertionError(why+": "+actual+" != "+expected);
    }
    private static void yes(boolean b,String why) { tests++;if(!b)throw new AssertionError(why); }
    public static void main(String[] args) {
        double[][] expected=FishingBalance.RARITY_PERCENT;
        for(int rod=0;rod<5;rod++)for(int grade=0;grade<5;grade++) {
            double[] probs=FishingBalance.chances(rod,grade);
            double sum=0;
            for(int r=0;r<5;r++) {
                yes(probs[r]>=0d&&probs[r]<=1d,"range rod="+rod+", grade="+grade+", r="+r);
                if(grade==2)near(probs[r]*100,expected[rod][r],1e-10,"Good cast baseline");
                if(rod==0&&r>=2 || rod==2&&r>=3)near(probs[r],0d,1e-12,"explicit rod ban");
                if(r>rod)yes(probs[r]<=expected[rod][r]/100d+1e-12,"above-rod rarity cap");
                sum+=probs[r];
            }
            near(sum,1d,1e-10,"normalization");
        }
        near(FishingBalance.chances(3,0)[4],.1,1e-12,"master's mythical cap on Super cast");
        float[] fatigue={.90f,.80f,.70f,.65f,.60f};
        for(int r=0;r<5;r++) {
            near(FishingBalance.fatigueThreshold(r,false),fatigue[r],1e-6,"normal fatigue threshold");
            near(FishingBalance.fatigueThreshold(r,true),fatigue[r]-.05f,1e-6,"shimmer fatigue threshold");
        }
        float[] bonus={1f,1.025f,1.05f,1.1f,1.15f};
        float[] fall={.34f,.34f,.34f,.357f,.374f};
        for(int rod=0;rod<5;rod++) {
            near(FishingBalance.rodFillMultiplier(rod),bonus[rod],1e-6,"fill bonus");
            near(FishingBalance.zoneFallSpeed(rod),fall[rod],1e-6,"release zone fall speed");
            near(FishingBalance.recoverySeconds(rod),2+2*rod,1e-6,"recovery duration");
        }
        near(FishingBalance.zoneRiseSpeed(),.53f,1e-6,"hold speed must not change");
        near(FishingBalance.movementMultiplier(true),.7f,1e-6,"slower tired fish");
        near(FishingBalance.turnMultiplier(true,.30f),1f,1e-6,"no turn boost for tired fish");
        near(FishingBalance.turnMultiplier(false,.30f),1.22f,1e-6,"normal turn boost");
        FishingFatigue f=new FishingFatigue();
        f.begin(3,true,3); // legendary shimmering: fatigue at 60%, 8s recovery
        near(f.threshold(),.60f,1e-6,"legendary shimmer threshold");
        f.update(.599f,.016f);yes(f.state()==FishingFatigue.FULL_STRENGTH,"no fatigue below threshold");
        f.update(.60f,.016f);yes(f.state()==FishingFatigue.TIRED,"equal-to-threshold fatigue");
        near(f.ringFraction(),1f,1e-6,"full tired ring");
        f.update(.599f,.016f);yes(f.state()==FishingFatigue.RECOVERING,"drop below starts recovery");
        near(f.remaining(),8f,1e-6,"countdown starts at full duration");
        f.update(.599f,3f);near(f.remaining(),5f,1e-6,"count down while below threshold");
        yes(f.isTired(),"fatigue during recovery");
        f.update(.60f,.016f);yes(f.state()==FishingFatigue.TIRED,"progress cancels countdown");
        near(f.remaining(),8f,1e-6,"full reset after crossing threshold");
        f.update(.50f,.016f);yes(f.state()==FishingFatigue.RECOVERING,"subsequent new countdown");
        f.update(.50f,7.5f);yes(f.state()==FishingFatigue.RECOVERING,"not recovered early");
        f.update(.50f,.5f);yes(f.state()==FishingFatigue.FULL_STRENGTH,"recovered at 8 sec");
        yes(!f.isTired(),"back to full strength");
        f.update(.59f,1f);yes(!f.isTired(),"cannot retrigger below threshold");
        f.update(.61f,.01f);yes(f.isTired(),"refatigue when threshold reached again");
        f.reset();yes(!f.isTired(),"reset on catch/end/new attempt");
        System.out.println("PASS "+tests+" deterministic fishing balance and fatigue assertions");
    }
}
