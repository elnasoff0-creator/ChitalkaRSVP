ПРОЧиталка v1.11.92-beta CLEAN — исходный Android-проект

СБОРКА
Открыть проект в Android Studio с установленными Android SDK 35 и JDK 17.
Собрать app (Build > Make Project) или Gradle assembleDebug.
APK в данном архиве нет. Временная сборка/проверка на устройстве не проводилась.

КАК УСТРОЕН EDGE TTS
1. При тапе по предложению старое воспроизведение и предыдущий сетевой запрос отменяются.
2. Для кэшированного аудио выбирается таймкод WordBoundary. При отсутствии метки создаётся фрагмент, начинающийся с выбранного предложения.
3. Media3 ProgressiveMediaSource читает MP3 из EdgeLiveDataSource, который ожидает поступления байтов в EdgeLiveBuffer.
4. WebSocket EdgeSpeechEngine передаёт новые MP3-байты напрямую в буфер, а метки WordBoundary — в подсветку.
5. После turn.end проверенный MP3 сохраняется в постоянный кэш, исходный поток воспроизведения не прерывается.
6. Фоновая подготовка уступает запросу текущего предложения. Ограничения HTTP 429 обрабатываются по Retry-After.

ВАЖНО
Неофициальный Edge Read Aloud не даёт клиенту гарантий доступности и времени ответа. На нескачанное предложение необходим сетевой запрос; физически мгновенный переход без кэша и ответа сервера не гарантируется. HTTP 429 означает отказ сервера, и попытки его обхода не используются.
Если возникла ошибка: открыть Logcat с фильтрами EdgeSpeech / EdgeTtsLatency / EdgeSpeechQueue и приложить последние строки.

ОЧИСТКА
В корне оставлены только CHANGELOG_v1.11.92.txt, README_v1.11.92.txt и COMMIT_MESSAGE_v1.11.92.txt; исторические документы удалены.

ОСНОВНЫЕ ТЕСТЫ (из корня проекта, JDK 17)
qa/test_edge_live_buffer.sh
qa/test_edge_priority.sh
qa/test_edge_speech_engine.sh
qa/test_edge_websocket_frames.sh
qa/test_edge_sentence_seek.sh
qa/check_java_syntax.sh
