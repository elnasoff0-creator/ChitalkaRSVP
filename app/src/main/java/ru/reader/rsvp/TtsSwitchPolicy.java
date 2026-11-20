package ru.reader.rsvp;

/** A repeated explicit Edge choice is a real switch if fallback is audible/paused. */
final class TtsSwitchPolicy {
    private TtsSwitchPolicy(){}
    static boolean shouldSwitch(boolean preferredLocal,boolean clickedLocal,boolean fallbackActive){
        return clickedLocal!=preferredLocal || (!clickedLocal && fallbackActive);
    }
}
