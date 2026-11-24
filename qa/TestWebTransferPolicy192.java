package ru.reader.rsvp;

import java.io.IOException;

/** Pure Java policy contracts, no Android runtime required. */
public final class TestWebTransferPolicy192 {
    interface Task {void run()throws Exception;}
    private static void rejects(Task task){try{task.run();throw new AssertionError("Expected a rejected input");}catch(IOException expected){}catch(Exception err){throw new AssertionError(err);}}
    private static void expect(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        expect(WebTransferPolicy.filename("Книга ё.epub").equals("Книга ё.epub"),"Unicode filename");
        expect(WebTransferPolicy.kind("Novel.EPUB").equals("book"),"EPUB");
        expect(WebTransferPolicy.kind("Манга.CBZ").equals("manga"),"CBZ");
        expect(WebTransferPolicy.kind("audio.m4b").equals("audio"),"M4B");
        for(String invalid:new String[]{"", ".bashrc", "..", "../book.epub", "../book.cbz", "a\\b.epub", "/etc/passwd", "x\nContent-Length: 0.epub"})
            rejects(()->WebTransferPolicy.filename(invalid));
        rejects(()->WebTransferPolicy.kind("evil.apk"));
        rejects(()->WebTransferPolicy.kind("virus.exe.epub.zip"));
        expect(WebTransferPolicy.length("123",null,1024)==123,"Upload length");
        rejects(()->WebTransferPolicy.length("0",null,1024));
        rejects(()->WebTransferPolicy.length("-1",null,1024));
        rejects(()->WebTransferPolicy.length("1025",null,1024));
        rejects(()->WebTransferPolicy.length("1","chunked",1024));
        expect(WebTransferPolicy.sameOrigin("192.168.0.5:1234","http://192.168.0.5:1234","http://192.168.0.5:1234"),"valid local origin");
        expect(!WebTransferPolicy.sameOrigin("192.168.0.5:1234","https://attacker.example","http://192.168.0.5:1234"),"reject cross origin");
        expect(!WebTransferPolicy.sameOrigin("attacker.example","http://192.168.0.5:1234","http://192.168.0.5:1234"),"reject Host attack");
        System.out.println("PASS: Web Transfer formats, Unicode filenames, path rejection, bounds, origin and chunked restrictions");
    }
}
