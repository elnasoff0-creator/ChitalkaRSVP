#!/usr/bin/env python3
"""Release v152 source regression contracts (not a substitute for device QA)."""
from pathlib import Path
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
j=r/'app/src/main/java/ru/reader/rsvp'
read=lambda f:(j/f).read_text(encoding='utf-8')
gradle=(r/'app/build.gradle').read_text()
assert "versionCode 15300" in gradle and "versionName '1.11.153-beta'" in gradle
store=read('EdgeFullBookStore.java')
service=read('EdgeFullBookService.java')
ui=read('EdgeChapterDownloadsActivity.java')
repository=read('DownloadsRepository.java')
scheduler=read('EdgeSpeechQueue.java')
# One durable book JSON, multiple independent ordered tasks.
for key in ('"tasks"','"autoContinue"','class Task','int chapter,done,total','static synchronized void planMissing(',
            'static synchronized void resumeAll(', 'static synchronized void pauseAll(',
            'static synchronized void stopChapter(', 'static synchronized Job next(',
            'static synchronized void recoverInterrupted(', 'ready.status=DONE'):
    assert key in store,key
assert 'current.tasks.add(task)' in store
assert 'status=QUEUED' in store and 'chapter=PLAN' in store
assert 'if(chapter==-1){current.autoContinue=true;chapter=PLAN;}' in store
# Queue ADD does not cancel the previous selected chapter.
assert 'EdgeFullBookStore.chooseChapter(created' in service
assert 'Multiple chapter taps append independent durable tasks' in service
assert 'if(uri.equals(synthesizingUri))' not in service.split('}else if(STOP_CHAPTER.equals(action)')[0]
assert 'EdgeFullBookStore.Job next=EdgeFullBookStore.next(this)' in service
assert 'EdgeFullBookStore.planMissing(job,book.chapters,snapshot)' in service
assert 'EdgeChapterArchive.snapshot(this,job.uri,job.title).state(job.chapter)' in service
assert 'readyInRange(job,index,chapterStart,chapterEnd,only!=null)' in service
assert 'EdgeFullBookStore.status(job,EdgeFullBookStore.SAVING)' in service
assert 'EdgeFullBookStore.WAITING' in service and 'EdgeSpeechQueue.interactivePlaybackActive()' in service
assert 'if(!waiting)activeSeconds++' in service
assert 'EdgeFullBookStore.pauseAll(job); // avoid hammering 429' in service
assert 'STOP_CHAPTER' in service and 'synthesizingChapter' in service
assert 'interactivePlaybackActive' in scheduler
# Separate overall progress, chapter stats and user statuses.
assert 'totalProgress=new ProgressBar' in ui and 'totalProgress.setProgress(bookPercent)' in ui
assert 'totalPercent.setText(bookPercent+"%")' in ui
assert 'weighted+=w*pct' in ui and 'item.saved==0)pct=Math.min(pct,99)' in ui
assert 'finalPending' in ui and 'currentStage' in ui
assert 'EdgeFullBookStore.task(job,item.chapter)' in ui
assert 'jobDone==b.jobDone&&a.jobTotal==b.jobTotal' in ui
for label in ('В очереди','Ожидание Edge TTS','Идёт загрузка','Сохранение / сборка MP3',
              'Приостановлено','Ошибка','Готов единый MP3','Фрагменты готовы'):
    assert label in ui,label
assert 'STOP_CHAPTER' in ui and 'Перезагрузить главу' in ui
assert 'EdgeFullBookStore.WAITING.equals(full.status)' in repository
assert 'Ожидание Edge TTS' in repository
for resource in (r/'app/src/main/res').rglob('*.xml'):
    ET.parse(resource)
print('PASS: v152 durable multi-chapter FIFO, continue auto-plan, ReaderActivity wait, SAF verification, UI progress, version/resources')
