PROChitalka / ПРОЧиталка 1.11.88-beta CLEAN (source)

Новая Edge архитектура (File-First):
  EdgeSpeechSegments.java — группировка предложений до 600 символов, сохранение позиций текста.
  EdgeSpeechQueue.java — одна WebSocket сессия, приоритет Play, пауза между запросами и Retry-After.
  EdgeSpeechEngine.java — Microsoft Edge v2, получаем MP3 + WordBoundary до завершения turn.end.
  EdgeSpeechFiles.java — проверка формата и атомарное сохранение готового MP3.
  EdgePlaybackService.java — только Media3 ExoPlayer, без PCM-ветки и недописанных MP3.
  EdgeStreamingService.java — синтез выбранного фрагмента и одного следующего, кэш и навигация.
  EdgeTtsClient.java — общее API для чтения и предварительной загрузки всей книги.

Зависимости: Android SDK 35, Java 17; Gradle project без wrapper (используйте установленный Gradle/Android Studio).
Проверки без SDK: bash qa/check_java_syntax.sh; bash qa/test_edge_speech_engine.sh;
 bash qa/check_edge_speech_groups.sh; bash qa/check_sentence_chunks.sh; bash qa/test_shared_book_session.sh

HTTP 429 — ограничение сервера Microsoft; уменьшение числа запросов помогает,
но не гарантирует отсутствие ошибки. Вернуться к работе можно после завершения
временной блокировки и не более ограниченного количества повторов. Для гарантированной
доступности необходим лицензированный серверный API TTS или локальная озвучка.

Диагностика задержки: Android Studio Logcat, фильтр EdgeTtsLatency и EdgeSpeech.
Значение first sound after Play измеряет именно старт декодированного звука,
а EdgeSpeech firstAudio/total — скорость ответа сервера и завершения MP3.
