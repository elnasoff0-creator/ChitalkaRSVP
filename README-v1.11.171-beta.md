# ПРОЧиталка v1.11.171-beta CLEAN — Full Source

Это **полный исходный проект Android Java** на базе `v1.11.170-beta CLEAN` с обновлённой мини-игрой «Рыбалка» питомца «Чтейцо». Не установочный APK.

Версия: `versionCode 17100` / `versionName 1.11.171-beta`. Пакет: `ru.reader.rsvp`, Java 17, Gradle Android plugin и `compileSdk 35`, `minSdk 24`, `targetSdk 35` (как в базовом проекте).

## Что нового

- Временная усталость рыбы с точными порогами по редкости и мерцанию: −30% к скорости и без рывков на разворотах.
- Круговой таймер восстановления на 2/4/6/8/10 секунд по удочке; отменяется, когда прогресс снова достигает порога.
- Контурный значок **хвоста рыбы с двумя направленными вниз шевронами**, закреплённый над рыбой, не вращается при её разворотах и перекрашивается по теме приложения.
- Новые шансы редкости для всех удочек; явный запрет редких/легендарных/мифических для тренировочной и легендарных/мифических для хорошей. У новичка по согласованному балансу легендарная и мифическая доступны.
- Бонусы прироста шкалы по удочкам: 0 / 2,5 / 5 / 10 / 15%.
- После **отпускания пальца** скорость падения зелёной зоны: 0,34 / 0,34 / 0,34 / 0,357 / 0,374 ед./с; при удержании подъём неизменен — 0,53 ед./с.

Прочие функции ридера, OCR, Edge TTS, браузера и менеджера скачиваний сохранены в исходном проекте. Их поведение в этой версии не менялось.

## Важные файлы

- `app/src/main/java/ru/reader/rsvp/ChteicoFishingView.java` — интеграция механики в игру.
- `app/src/main/java/ru/reader/rsvp/FishingBalance.java` — точные формулы вероятностей и бонусов.
- `app/src/main/java/ru/reader/rsvp/FishingFatigue.java` — временная усталость (без Android-зависимостей).
- `app/src/main/java/ru/reader/rsvp/FishingFatigueIndicator.java` — Canvas-таймер с VectorDrawable внутри.
- `app/src/main/res/drawable/ic_fishing_fatigue_debuff.xml` — XML-иконка.
- `app/src/main/res/layout/view_chteico_fishing.xml` — графический слой индикатора.
- `TZ-PROChitalka-v1.11.171-beta.md` — полное утверждённое техническое задание.
- `CHANGELOG-v1.11.171-beta.md` — подробная расшифровка изменений.
- `QA-REPORT-v1.11.171-beta.md` — результаты и ограничения тестирования.

## Сборка и тесты

Обычная release-сборка через установленный **Android SDK** и Gradle 8.7:

```bash
gradle --no-daemon :app:assembleRelease --stacktrace
```

Офлайновые проверки без Android SDK:

```bash
python -m unittest qa.test_v168_contracts qa.test_v169_contracts qa.test_v170_contracts qa.test_v171_contracts -v
sh qa/typecheck_v171_fishing.sh
javac -d /tmp/fish-test app/src/main/java/ru/reader/rsvp/{FishingBalance,FishingFatigue}.java qa/TestFishing171.java
java -cp /tmp/fish-test ru.reader.rsvp.TestFishing171
```

Полная Gradle-сборка и испытания на реальном телефоне не выполнялись в рабочей среде: отсутствует Android SDK и установленный Gradle. Проверки Java с Android API-заменителями **не заменяют** настоящий Android Gradle build. Перед установкой требуется выполнить release-сборку в GitHub Actions и проверить визуальную анимацию и работу на устройстве.

В комплекте только актуальные документы версии v1.11.171-beta, без README и CHANGELOG предыдущих выпусков.
