ПРОЧиталка v1.11.100-beta CLEAN — исходный Android-проект.

Это исправление компиляции поверх v1.11.99-beta. Сохранены изменения архитектуры:
- ReaderActivity — единый экран четырех режимов чтения и TTS.
- EdgeAudioManager — общий индекс сохраненного Edge TTS MP3.
- DownloadActivity — фоновые проверки, DiffUtil и быстрые фильтры.
- ReaderToolbarController/ReaderSheets — общая панель и выдвижные окна.
- Мини-Чтейцо — реплика поверх питомца без сдвига изображения.

Исправлено: условие `if(saved)` для результата `EdgeSpeechFiles.save()`
заменено на `if(saved!=null)` (saved имеет тип java.io.File).

Проверка перед публикацией APK:
1. bash qa/test_edge_save_contract.sh
2. bash qa/test_edge_speech_files.sh
3. Gradle :app:assembleRelease на машине с Java 17 и Android SDK 35.

Файлы MP3/индексы на устройстве не затрагиваются. Сборка на настоящем
Android-устройстве и Release Gradle в данном окружении не выполнялись.
