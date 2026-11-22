# BeatForge — битмейкер в стиле MPC / SP-404 (Kotlin + Jetpack Compose)

## Сборка APK (Android Studio)
1. Установи Android Studio (Koala 2024.1+ или новее), при первом запуске поставь Android SDK 34.
2. File → Open → выбери папку `BeatForge`. Дождись Gradle Sync (нужен интернет, ~5–10 мин в первый раз).
   Если Studio ругается на отсутствие gradle-wrapper.jar — нажми «Use Gradle from distribution» / "OK";
   либо в терминале Studio выполни `gradle wrapper --gradle-version 8.7`.
3. Подключи телефон (Настройки → Для разработчиков → Отладка по USB) и нажми ▶ Run — приложение установится.
4. Готовый APK: Build → Build Bundle(s) / APK(s) → Build APK(s). Файл:
   `app/build/outputs/apk/debug/app-debug.apk` — скинь на телефон и установи (разреши установку из неизвестных источников).

## Если создаёшь проект вручную (New Project → Empty Activity, Compose, minSdk 29)
Скопируй файлы по тем же путям:
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` → корень
- `app/build.gradle.kts` → модуль app
- `app/src/main/AndroidManifest.xml`, `res/values/themes.xml`
- `app/src/main/java/com/beatforge/app/…` — все .kt (MainActivity, BeatViewModel, audio/*, ui/*)
Пакет и applicationId — `com.beatforge.app` (если меняешь, поправь package в файлах и namespace).

## Структура кода
- `audio/SynthDrums.kt` — синтез 16 звуков (808, бочки, снейр, клэп, хэты, том, конга, ковбелл, шейкер, крэш, FX).
- `audio/Mixer.kt` — голоса (Gate/Trigger/Mute Group), секвенсор sample-accurate со свингом, мастер-цепочка:
  Looper → Isolator → Filter LP/HP + Resonance → Bitcrusher → Vinyl → Reverb → Limiter. Один и тот же код играет и рендерит экспорт.
- `audio/Dsp.kt` — биквады, Freeverb, лимитер, винил, биткрашер, изолятор (LR4 200 Гц / 2.5 кГц).
- `audio/AudioEngine.kt` — AudioTrack PCM_FLOAT, PERFORMANCE_MODE_LOW_LATENCY, буфер = 2 burst, авто-рост при underrun.
- `audio/SampleIO.kt` — декод любых аудиофайлов (MediaCodec), WAV-экспорт в Downloads.
- `audio/Recorder.kt` — запись с микрофона и системного звука (MediaProjection + AudioPlaybackCapture).
- `ui/*` — вкладки Студия / Канал / FX / Нарезка / Справка.

## Сборка через GitHub (без Android Studio)
1. Создай пустой репозиторий на github.com, залей в него ВСЁ содержимое папки BeatForge
   (кнопка «uploading an existing file» → перетащи файлы и папки, включая скрытую `.github`; ветка main).
2. Открой вкладку Actions → «Build APK» → Run workflow (или запустится сам после пуша).
3. Через ~5–8 минут открой завершённый запуск → внизу Artifacts → `BeatForge-apk` → скачай zip, внутри `app-debug.apk`.
