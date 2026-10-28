ПРОЧиталка v1.11.112-beta CLEAN — VISUAL/THEME/PET FIX
========================================================
Проверенная база: v1.11.112-beta CLEAN TimeSync FIX.
Номер версии 1.11.112-beta и versionCode 11212 не изменены.
Архив содержит исходники Android Studio, но не готовый APK.

ЧТО ИЗМЕНЕНО

1. ПЯТЬ XML-ИКОНОК ТЕМЫ ЧТЕНИЯ
Путь: app/src/main/res/drawable/
* ic_reading_theme_day.xml       — солнце, золотые контуры #FFD56B
* ic_reading_theme_night.xml     — полумесяц, голубые контуры #A6D7FF
* ic_reading_theme_soft.xml      — лист, зелёные контуры #82D6A0
* ic_reading_theme_sepia.xml     — открытая книга, персиковые контуры #E5B58C
* ic_reading_theme_custom.xml    — палитра, фиолетовый контур #BC9EF9, цветные точки
Все пять — native Android VectorDrawable XML с viewport 24x24, прозрачной
заливкой, скруглёнными линиями, без PNG, фоновых прямоугольников и затемнения tint.

2. КНОПКА СМЕНЫ ТЕМ
MainActivity: кнопка добавлена НАВЕРХУ непосредственно после Поиска (перед Добавить).
ReaderActivity: кнопка также доступна в верхнем toolbar книги.
Одно нажатие — немедленное переключение:
Дневная → Ночная → Мягкая → Сепия → Пользовательская → Дневная.
Кнопка показывает цветную иконку активной темы.
ReadingThemeCycle.java — общие предустановленные цвета и сохранение параметров.
Выбранная тема сохраняется в существующих prefs reader (bg, fg).
Пользовательские значения запоминаются отдельно (theme_custom_bg, theme_custom_fg),
поэтому переключение на стандартную тему не стирает личную цветовую пару.
Пункт «Пользовательская тема» добавлен и в настройки оформления.
Примечание: если пользовательская пара идентична готовому пресету, индекс
определяется по цветам и отображается как соответствующая готовая тема.

3. НОЧНАЯ И КОНТРАСТНАЯ ТЕМЫ
ChteicoPetView.java: на тёмном (по яркости, а не только #1e2025) фоне
рисунок питомца выводится белым: цветовой фильтр применён к VectorDrawable.
В светлых темах остаётся исходная графика и фильтр снимается.
MainActivity, ReaderActivity, ChteicoActivity, ChteicoSpeechBubble,
SettingsActivity используют яркость фона и для подложек/облачков.
Исправлена читаемость выбранного значения и пунктов списка у Spinner:
- «Режим чтения»;
- этап и ветка питомца в настройках разработчика;
- «Замена точки» и режим V1/V2/V3 Edge TTS.
Созданы контрастные адаптеры с явным textColor/bg в выбранной строке и popup.

4. МИНИ-ПИТОМЕЦ В ГЛАВНОМ МЕНЮ
ChteicoEmotions.java + MainActivity.java:
- Короткое одиночное касание открывает полный экран Чтейца.
- Долгое удержание для перехода больше не нужно.
- При движении больше стандартного touch slop немедленно показывается эмоция.
- На карточке мини-питомца тем же движением сразу запускается Drag and Drop:
  никаких таймеров long-press. Drag-and-drop требует движения, но без удержания.
- Направление свайпа не ограничено (вверх, вниз, вправо, влево).
- Виброотклик через системный performHapticFeedback(VIRTUAL_KEY) на tap/move.
- Облачко эмоций уменьшено с 136x78dp до 112x56dp, шрифт с 17 до 14sp.
- Сон: шрифт 10sp, короткая подпись Zzz без длинной мордочки, чтобы помещалась.
- Питомец остаётся в карточке 140x140dp, порядок секций сохраняется.
На полном экране Чтейца обычное касание по-прежнему выполняет действие питомца,
а свайп показывает эмоцию; сама мини-карточка открывает полный экран по tap.

5. НЕ ИЗМЕНЯЛОСЬ
- Microsoft Edge TTS, пул WebSocket 4 ресурса и 2 фоновые задачи;
- воспроизведение MP3 через Media3, подсветка WordBoundary, кэш;
- TimeSync через дату действующего WebSocket-handshake, обработка 403;
- отсутствие авто-повтора 429 и локальный лимит ожидания 500 мс;
- диагностический журнал «Показать лог-файл»;
- версии исходных экранов, книг, аудиотеки, оглавления и сохранения позиции.

ПРОВЕРКИ
- Парсинг 107 Java-файлов компиляторным анализатором Java 17: PASS.
- XML всех ресурсов, в том числе пяти новых VectorDrawable: PASS.
- XML: 24x24dp, прозрачная заливка у каждого path: PASS.
- Проверка кнопки после «Поиск», переключения 5 тем и пользовательских цветов: PASS.
- Проверка жестов, хаптики, облачка и белого питомца: PASS.
- QA существующих Edge TimeSync, HTTP429, WebSocket pool, журнал: PASS.
- UI regression QA (включая обновлённый тест мини-питомца): PASS.

ПОЛНОЦЕННЫЙ APK НЕ СОБРАН (Android SDK/Gradle в данной среде отсутствуют).
Android-тесты на реальном телефоне остаются обязательными: проверить tint
сложных векторных спрайтов, Drag and Drop, Spinner и toolbar в четырёх режимах.
Сборка: gradle --no-daemon :app:assembleDebug --stacktrace

ФАЙЛЫ КЛЮЧЕВОЙ ПРАВКИ
app/src/main/java/ru/reader/rsvp/ReadingThemeCycle.java (новый)
app/src/main/java/ru/reader/rsvp/ReaderActivity.java
app/src/main/java/ru/reader/rsvp/MainActivity.java
app/src/main/java/ru/reader/rsvp/SettingsActivity.java
app/src/main/java/ru/reader/rsvp/ChteicoPetView.java
app/src/main/java/ru/reader/rsvp/ChteicoEmotions.java
app/src/main/java/ru/reader/rsvp/ChteicoActivity.java
app/src/main/java/ru/reader/rsvp/ChteicoSpeechBubble.java
app/src/main/res/drawable/ic_reading_theme_*.xml (5 новых)
qa/test_reading_theme_visual_fix.py (новый)
