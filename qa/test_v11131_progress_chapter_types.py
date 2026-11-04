#!/usr/bin/env python3
"""Compile ReaderActivity's real chapter helpers and two save calls with javac.

A Java syntax parser does not check method argument types, which allowed the
v1.11.131 ReaderActivity -> UnifiedReadingProgress(int/String) mismatch.
"""
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
reader = (root / 'app/src/main/java/ru/reader/rsvp/ReaderActivity.java').read_text(encoding='utf-8')
progress = (root / 'app/src/main/java/ru/reader/rsvp/UnifiedReadingProgress.java').read_text(encoding='utf-8')


def method(name):
    match = re.search(r'    private (?:int|String) ' + name + r'\(int word\)\s*\{', reader)
    assert match, f'Missing ReaderActivity.{name}'
    start, depth = match.start(), 0
    for j in range(match.end() - 1, len(reader)):
        if reader[j] == '{':
            depth += 1
        elif reader[j] == '}':
            depth -= 1
            if depth == 0:
                return reader[start:j+1]
    raise AssertionError(f'Unclosed ReaderActivity.{name}')

calls = re.findall(r'UnifiedReadingProgress\.save\(this,docUri,.*?\);', reader, flags=re.DOTALL)
assert len(calls) == 2, f'Expected exactly two ReaderActivity progress calls, got {len(calls)}'
assert 'String chapter' in progress and 'void save(Context context' in progress, 'save() String chapter contract changed'
java = '''package ru.reader.rsvp;
import java.util.*;
public class ReaderChapterProgressContract {
  static class WordMap {
    int[] starts={0,5,20,55,90};
    int clamp(int word){return Math.max(0,Math.min(word,starts.length-1));}
  }
  final WordMap map=new WordMap();
  BookChapterIndex actualChapters=BookChapterIndex.empty();
  List<BookChapterIndex.Chapter> chapters=new ArrayList<>();
  String docUri="test-book";
  int index=3;
  String[] words={"one","two","three","four","five"};
  static class UnifiedReadingProgress {
    static String lastChapter;
    static void save(Object context,String uri,int word,int wordCount,String chapter,boolean syncLibrary){
      lastChapter=chapter;
    }
  }
''' + method('chapterForWord') + '\n' + method('chapterTitleForWord') + '''
  void saveLight(){ ''' + calls[0] + ''' }
  void saveFull(){ int libraryIndex=3; ''' + calls[1] + ''' }
  public static void main(String[] unused){
    ReaderChapterProgressContract test=new ReaderChapterProgressContract();
    test.chapters=Arrays.asList(
      new BookChapterIndex.Chapter("Chapter one",0,40,1),
      new BookChapterIndex.Chapter("Chapter two",40,80,1),
      new BookChapterIndex.Chapter("Chapter three",80,120,1));
    test.actualChapters=BookChapterIndex.build(test.chapters,120);
    test.saveLight();
    if(!"Chapter two".equals(UnifiedReadingProgress.lastChapter)) throw new AssertionError("saveLight chapter mismatch");
    test.saveFull();
    if(!"Chapter two".equals(UnifiedReadingProgress.lastChapter)) throw new AssertionError("saveFull chapter mismatch");
    test.actualChapters=BookChapterIndex.empty();
    if(!"Глава 2".equals(test.chapterTitleForWord(3))) throw new AssertionError("chapter label fallback mismatch");
    test.actualChapters=BookChapterIndex.build(test.chapters,120);
    if(!"Chapter three".equals(test.chapterTitleForWord(4))) throw new AssertionError("wrong chapter by word");
    System.out.println("PASS: ReaderActivity chapter title calls typecheck and return correct labels");
  }
}
'''
with tempfile.TemporaryDirectory(prefix='reader_chapter_contract_') as tmp:
    tmp_path=Path(tmp)
    fixture=tmp_path/'ReaderChapterProgressContract.java'
    fixture.write_text(java,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',tmp,str(fixture),str(root/'app/src/main/java/ru/reader/rsvp/BookChapterIndex.java')],check=True)
    subprocess.run(['java','-cp',tmp,'ru.reader.rsvp.ReaderChapterProgressContract'],check=True)
