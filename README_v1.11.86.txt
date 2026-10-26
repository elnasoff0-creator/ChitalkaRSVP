ПРОЧиталка v1.11.86-beta CLEAN

Содержит полный Android-проект (исходники), НЕ готовый APK.

Изменения в Edge:
- EdgeRealtimeTransport.java — новый сетевой WebSocket транспорт с потоковой передачей аудио и меток WordBoundary.
- EdgeTtsClient.java — тонкий адаптер для совместимости с сервисами EdgeStreamingService / EdgeFullBookService.
- EdgeRequestGate.java — одновременный единственный запрос, приоритет PLAY, без фиксированной задержки 2.2 c.
- При ответе HTTP 429 повторный запрос НЕ запускается до окончания Retry-After / cooldown.
- Existing EdgePcmStream and EdgeStreamCache / EdgeCacheCommitter remain the sinks. No changes to offline MP3 layout.

Проверка исходников (Linux/macOS):
  bash qa/check_java_syntax.sh
  bash qa/check_reader_methods.sh
  bash qa/check_sentence_chunks.sh
  bash qa/test_shared_book_session.sh
  bash qa/test_edge_rate_gate.sh
  bash qa/test_edge_realtime_stream.sh

Сборка APK (Android SDK + Java 17 + Gradle 8.7):
  gradle --no-daemon :app:assembleRelease --stacktrace

На телефоне проверить:
1. Воспроизведение НЕ сохранённого предложения (задержку до первого звука).
2. Несколько предложений подряд, ручной переход к далёкому предложению.
3. Точную подсветку слова и фоновые MP3 + words.json.
4. Поведение HTTP 429 (время ожидания и отсутствие циклических повторов).
5. Повторное воспроизведение уже сохранённого MP3 без подключения к сети.

Ограничения: HTTP 429/403 могут зависеть от политики Microsoft, региона и выбранного голоса. Реальное время до первого звука и Android MediaCodec/AudioTrack нельзя проверить JVM-стабами. Для полной валидации нужна GitHub Actions сборка и устройство.
