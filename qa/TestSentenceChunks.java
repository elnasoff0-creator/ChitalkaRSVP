package ru.reader.rsvp;
/** Standalone Java smoke test: javac -d /tmp/tests SentenceChunks.java EdgeTextSegments.java qa/TestSentenceChunks.java */
public final class TestSentenceChunks {
    private static void verify(String text){
        java.util.ArrayList<SentenceChunks.Chunk> parts=SentenceChunks.split(text);
        int total=0,previous=0;
        for(SentenceChunks.Chunk chunk:parts){
            if(chunk.text.length()>SentenceChunks.LIMIT)throw new AssertionError("exceeds limit");
            if(chunk.fromChar<previous||chunk.toChar>text.length())throw new AssertionError("offset out of order");
            if(!chunk.text.equals(text.substring(chunk.fromChar,chunk.toChar)))throw new AssertionError("source mismatch");
            if(chunk.fromWord>=chunk.toWord||SentenceChunks.forWord(parts,chunk.fromWord)!=chunk)throw new AssertionError("word index");
            previous=chunk.toChar;total+=chunk.toWord-chunk.fromWord;
        }
        if(total!=SentenceChunks.wordPositions(text).size())throw new AssertionError("lost words");
    }
    public static void main(String[] args){
        verify("Привет. Это новая глава! Последняя фраза.");
        verify("В начале.   В середине? \n\nИ в конце…");
        verify("Длинное предложение "+"тридцать три слова ".repeat(100)+"конец!");
        verify("одно".repeat(160)+" готово.");
        verify("\n\n     ");
        verify("смешанные-слова, пунктуация! 123. Ещё абзац.");
        System.out.println("PASS SentenceChunks max=800; word indexes, source offsets and long sentences");
    }
}
