package ru.reader.rsvp;

/** Cache-first SAF policy: bounded waiting on user Play, full background restore when idle. */
final class EdgeFirstAudioPolicy {
    static final long PLAY_RESTORE_GRACE_MS=160L;
    static final long IDLE_RESTORE_WAIT_MS=9000L;
    private EdgeFirstAudioPolicy(){}
    static long restoreWaitMs(boolean foreground){
        return foreground?PLAY_RESTORE_GRACE_MS:IDLE_RESTORE_WAIT_MS;
    }
    static boolean bypassPendingRestore(boolean foreground,boolean existingForeground){
        return foreground&&!existingForeground;
    }
}
