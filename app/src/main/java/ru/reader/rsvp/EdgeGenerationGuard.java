package ru.reader.rsvp;

import java.util.HashSet;
import java.util.Set;

/** Prevents both Edge download modes from synthesizing the same cached MP3 concurrently. */
final class EdgeGenerationGuard {
    private static final Set<String> BUSY=new HashSet<>();
    private EdgeGenerationGuard(){}
    static synchronized boolean claim(String path){return BUSY.add(path);}
    static synchronized void release(String path){if(path!=null)BUSY.remove(path);}
}
