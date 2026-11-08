#!/usr/bin/env python3
"""Regression for GH Actions v1.11.127 Javac failure on anonymous ServiceConnection.this.

This is a source-level check plus a JDK-17 compilation/invocation fixture.
It is *not* an Android compile and does not require the Android SDK.
"""
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = (root / 'app/src/main/java/ru/reader/rsvp/EdgeStreamingService.java').read_text(encoding='utf-8')
play = (root / 'app/src/main/java/ru/reader/rsvp/EdgePlaybackService.java').read_text(encoding='utf-8')

assert 'player.setResumeSelection(EdgeStreamingService.this::rewindOnResume);' in src
assert 'player.setResumeSelection(this::rewindOnResume);' not in src
assert re.search(r'private\s+void\s+rewindOnResume\s*\(\s*\)', src)
assert 'interface ResumeSelection { void rewindToPreviousSentence(); }' in play
assert re.search(r'void\s+setResumeSelection\s*\(\s*ResumeSelection\s+callback\s*\)', play)
assert 'resumeSelection.rewindToPreviousSentence();' in play

fixture = '''
public class EdgeResumeBindingFixture {
    interface ResumeSelection { void rewindToPreviousSentence(); }
    static final class Player {
        ResumeSelection selected;
        void setResumeSelection(ResumeSelection r){selected=r;}
    }
    interface ServiceConnection { void onServiceConnected(); }
    private final Player player = new Player();
    private int invoked;
    private void rewindOnResume(){invoked++;}
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(){
            player.setResumeSelection(EdgeResumeBindingFixture.this::rewindOnResume);
        }
    };
    public static void main(String[] args){
        EdgeResumeBindingFixture service = new EdgeResumeBindingFixture();
        service.connection.onServiceConnected();
        service.player.selected.rewindToPreviousSentence();
        if(service.invoked!=1)throw new AssertionError("wrong callback receiver");
        System.out.println("PASS: callback references owning service, invoked once");
    }
}
'''
with tempfile.TemporaryDirectory() as dirpath:
    target = Path(dirpath)
    (target/'EdgeResumeBindingFixture.java').write_text(fixture, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(target), str(target/'EdgeResumeBindingFixture.java')], check=True)
    subprocess.run(['java', '-cp', str(target), 'EdgeResumeBindingFixture'], check=True)
print('PASS: EdgeStreamingService callback binding source contract and Java fixture')
