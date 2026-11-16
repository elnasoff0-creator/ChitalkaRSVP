package ru.reader.rsvp;
import java.io.*;
public class TestEdgeMp3ChapterExport173 {
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("segments and output");
        EdgeMp3ChapterExport.Format f=new EdgeMp3ChapterExport.Format();
        try(OutputStream out=new FileOutputStream(args[2])){
            EdgeMp3ChapterExport.writeMetadata(out,"Глава 7. Возвращение","Книга о море",7);
            try(InputStream a=new FileInputStream(args[0])){EdgeMp3ChapterExport.append(a,out,f);}
            try(InputStream b=new FileInputStream(args[1])){EdgeMp3ChapterExport.append(b,out,f);}
        }
        if(f.frames<20||f.bytes<2000||f.sampleRate!=24000)throw new AssertionError("Wrong MPEG frame count or format");
        System.out.println("PASS: joined "+f.frames+" frames, "+f.bytes+" bytes, "+f.sampleRate+"Hz");
    }
}
