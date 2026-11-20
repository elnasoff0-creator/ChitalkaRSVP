package ru.reader.rsvp;

/** Unit regression for manual engine choice and foreground SAF restore policy. */
public final class TestEdgeFirstAudio188 {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    public static void main(String[] args){
        check(!TtsSwitchPolicy.shouldSwitch(false,false,false),"Edge reselect while healthy is noop");
        check(TtsSwitchPolicy.shouldSwitch(false,false,true),"Edge reselect with active backup MUST switch");
        check(TtsSwitchPolicy.shouldSwitch(true,false,false),"Local -> Edge switch");
        check(TtsSwitchPolicy.shouldSwitch(false,true,false),"Edge -> Local switch");
        check(!TtsSwitchPolicy.shouldSwitch(true,true,false),"Already local is noop");
        check(EdgeFirstAudioPolicy.restoreWaitMs(true)==160L,"foreground grace <= 160ms");
        check(EdgeFirstAudioPolicy.restoreWaitMs(false)==9000L,"idle restore may finish");
        check(EdgeFirstAudioPolicy.bypassPendingRestore(true,false),"Play bypasses stalled warm restore");
        check(!EdgeFirstAudioPolicy.bypassPendingRestore(true,true),"Play preserves its own short grace");
        check(!EdgeFirstAudioPolicy.bypassPendingRestore(false,false),"idle must not bypass");
        System.out.println("PASS: v188 explicit Edge reselect and bounded SAF restore policy");
    }
}
