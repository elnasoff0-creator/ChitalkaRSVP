# ПРОЧиталка v1.11.183-beta CLEAN — полный проект

- `versionName`: `1.11.183-beta`
- `versionCode`: `18300`
- Основа: `v1.11.182-beta CLEAN`
- Java 17, Android Gradle Plugin 8.x, `compileSdk 35`.

## Что нового

1. Удалены декоративные подложки с TTS-бейджей ReaderActivity.
2. В меню `⋮` удалена устаревшая ссылка «Путь к файлам Edge TTS». «Загрузка озвучки» по-прежнему открывает единый менеджер текущей книги.
3. Edge TTS заблаговременно присоединяет следующий live/кешированный фрагмент в Media3, восстанавливает `STATE_ENDED`, ограничивает зависания WebSocket-буфера и SAF и безопасно обрабатывает ошибки воспроизведения.
4. Все значки `?` в настройках расположены в строке соответствующего заголовка (область касания 48dp) вместо отдельных строк.

## Проверка

На машине с Java 17:

```bash
bash qa/check_java_syntax.sh
bash qa/test_edge_sentence_blocks_182.sh
python3 qa/check_v182_contracts.py
bash qa/test_edge_live_buffer.sh
bash qa/test_edge_transitions_183.sh
bash qa/test_edge_prefetch_planner.sh
python3 qa/test_edge_idle_expiry_compilation.py
python3 qa/check_v183_contracts.py
```

APK собирается через GitHub Actions: `.github/workflows/release.yml`. Для подписи нужны стандартные `RELEASE_*` secrets. Полная компиляция и проверка Media3 на устройстве ещё не подтверждены.

Подробная расшифровка: `CHANGELOG-v1.11.183-beta.md`. Реальные и обязательные проверки: `QA-REPORT-v1.11.183-beta.md`.
