# Полная замена GitHub-проекта без MRT

**Почему сборка снова падает:** GitHub сохраняет старые отслеживаемые `MrtDownloadService.java` и `MrtPlaybackService.java`, если файлы из нового ZIP загружаются поверх старых. В этом архиве они отсутствуют.

## Вариант без командной строки: GitHub Desktop (Windows)

1. Откройте GitHub Desktop и клонируйте свой `elnasoff0-creator/ChitalkaRSVP` (если ещё не клонирован). Переключитесь на `main`.
2. Проверьте отсутствие незакоммиченных локальных изменений (при необходимости сначала сохраните их).
3. Откройте локальную папку репозитория. Удалите **все файлы и папки внутри, кроме скрытой `.git`**. Саму папку репозитория не удаляйте. `.git` содержит историю и настройку связи с GitHub.
4. Скопируйте **содержимое** распакованного архива с исходниками, включая `.github/workflows` и папку `app`, в пустую локальную папку репозитория. Не копируйте файлы внутрь нового вложенного каталога.
5. Вернитесь в GitHub Desktop: там должны появиться в том числе **Deleted: MrtDownloadService.java, MrtPlaybackService.java** и изменения `.github/workflows/release.yml`.
6. Сделайте Commit to main и Push origin.
7. На GitHub запустите Actions → Publish signed APK release → Run workflow с веткой `main`. Запускайте именно из `main`, а не из старого тега `v*`.

GitHub Actions secrets (ключ подписи), Releases, Issues и история коммитов при таком способе сохраняются.

## Вариант с автоматической заменой

**Windows PowerShell** (нужен Git, локальный чистый клон и распакованный исходный ZIP; команда запускается внутри папки извлечённого архива):

```powershell
powershell -ExecutionPolicy Bypass -File .\RESET-GITHUB-REPO.ps1 -RepoPath "C:\path\to\ChitalkaRSVP" -Push
```

**Linux/macOS / Git Bash**:

```bash
bash RESET-GITHUB-REPO.sh /path/to/ChitalkaRSVP --push
```

Скрипты отказываются работать при незакоммиченных изменениях, проверяют наличие `.git`, удаляют старые отслеживаемые файлы, копируют проект без MRT, делают Git commit и отправляют его в GitHub только при `-Push`/`--push`.

**Не удаляйте сам репозиторий в Settings → Delete repository.** Не запускайте массовое удаление в произвольной папке; скрипты работают только с локальным клоном.

## Проверка

В Actions перед установкой SDK должны появиться шаги:

- `v1.11.30 - remove obsolete MRT Java before compilation`
- `Check that no obsolete MRT source can enter javac`

Если в логе сразу `Run gradle assembleRelease --stacktrace` и нет указанных шагов, GitHub использует старый workflow/ветку/тег, а не файлы из этого архива.

Версия приложения: **1.11.30-beta**, изменений в Android-коде относительно Edge-only исходников v1.11.30 нет. Новые файлы только инструкции и скрипты для полной замены репозитория.
