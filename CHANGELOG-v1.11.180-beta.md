# CHANGELOG 1.11.180-beta

- Отображение значков установленного TTS-движка в выборе голосов, резервного движка и overlay фактически активной озвучки.
- Короткое уведомление при переходе Edge на резервный TTS и обратно.
- Словарь переименован в «Словарь TTS» во всех исходниках пользовательских экранов.
- Общая подготовка произносимого текста для локального MP3, локального fallback-произношения и Edge TTS; игнорируемые фрагменты пропускаются.
- Автоматический резервный запуск разрешён только при включённом переключателе; неудача первого фрагмента не вызывает немедленного резервирования.
- Убран жёстко заданный запуск локального TTS из AudioPlayerActivity.
- Диагностика скрыта из общих настроек, доступна через боковое меню только в режиме разработчика; дополнительная проверка при открытии Activity.
- Версия 1.11.180-beta, versionCode 18000.

Ограничение: сквозная проверка работы Edge TTS на реальном устройстве и полная Android Gradle сборка не подтверждены.


## Build Hotfix — ReaderActivity

- Added missing refreshActiveTtsIcon() method required by ReaderActivity compilation.
- The overlay icon reflects active Edge, manually selected local, or actual fallback TTS engine and avoids repeated drawable loads.
- All previous versioned MD release notes removed from this CLEAN archive.
- Full Android Gradle build must be verified in GitHub Actions.
