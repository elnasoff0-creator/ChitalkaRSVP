ПРОЧиталка v1.11.107-beta CLEAN — исходный Android-проект.

Изменения: см. CHANGELOG_v1.11.107.txt.

Серверный Retry-After имеет приоритет над локальной паузой 1 секунда. Не следует ожидать, что это избавит от 429: число запросов регулируется на стороне Microsoft.

Logcat в Android Studio: View -> Tool Windows -> Logcat, фильтр EdgeTtsLatency или EdgeSpeechQueue.
Через ADB: adb logcat -s EdgeTtsLatency:I EdgeSpeechQueue:W EdgeSpeech:W EdgeFullBook:W '*:S'
В приложении отдельный экран просмотра Logcat не реализован.

Размеры блоков 800/500, последовательный сетевой доступ и прежний потоковый Media3 сохранены.
