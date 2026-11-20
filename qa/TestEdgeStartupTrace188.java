package ru.reader.rsvp;
public final class TestEdgeStartupTrace188 {
    static void yes(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        EdgeStartupTrace.begin("play_button");
        EdgeStartupTrace.stage("BOOK_READY","words=5");
        EdgeStartupTrace.stage("BOOK_READY","words=5");
        EdgeStartupTrace.stage("FIRST_MP3_PACKET","bytes=123");
        EdgeStartupTrace.firstAudio("media3_is_playing");
        EdgeStartupTrace.stage("LATE","no");
        yes(ReaderDiagnosticLog.count("stage=BOOK_READY")==1,"one event per stage");
        yes(ReaderDiagnosticLog.count("stage=FIRST_AUDIO")==1,"one first audio");
        yes(ReaderDiagnosticLog.count("stage=LATE")==0,"no late stage");
        EdgeStartupTrace.begin("force_edge");
        EdgeStartupTrace.cancelled("missing_saf_permission");
        EdgeStartupTrace.firstAudio("should_not_report");
        yes(ReaderDiagnosticLog.count("stage=CANCEL")==1,"cancelled trace logged");
        yes(ReaderDiagnosticLog.count("stage=FIRST_AUDIO")==1,"cancelled trace has no first audio");
        yes(ReaderDiagnosticLog.count("reason=force_edge")==1,"correlated force trace");
        System.out.println("PASS: v188 monotonic trace correlation and privacy-safe stages");
    }
}
