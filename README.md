# YaDisk Sync

Приложение для автоматической синхронизации фотографий с Яндекс.Диска на Android-устройство.

## Возможности

- **Фоновая синхронизация** — периодический запуск через WorkManager (интервал по умолчанию: 15 мин)
- **Фильтрация по дате** — скачиваются только фотографии новее заданной даты
- **Умное отслеживание** — уже загруженные файлы пропускаются, удалённые — скачиваются заново
- **Работа по Wi-Fi** — синхронизация запускается только при подключении к несчетчику сети (Wi-Fi)
- **Экономия батареи** — не запускается при низком заряде
- **Ручной запуск** — кнопка «Sync Now» на главном экране

## Архитектура и стек

| Компонент        | Технология                                       |
|------------------|--------------------------------------------------|
| Язык             | Kotlin 1.9                                       |
| Мин. SDK         | 26 (Android 8.0)                                 |
| Target SDK       | 35 (Android 15)                                  |
| UI               | Jetpack Compose + Material Design 3              |
| Архитектура      | Clean Architecture, MVVM                         |
| DI               | Hilt                                             |
| База данных      | Room                                             |
| Настройки        | DataStore (Preferences)                          |
| Сеть             | Retrofit2 + OkHttp + Kotlin Serialization        |
| Фоновые задачи   | WorkManager (PeriodicWorkRequest)                |
| Асинхронность    | Kotlin Coroutines + Flow                         |

## Структура проекта

```
app/src/main/java/com/yadisksync/
├── data/
│   ├── local/          — Room DB, DataStore, DAO
│   └── remote/         — Retrofit API, модели Yandex Disk
├── di/                 — Hilt модули
├── domain/
│   ├── repository/     — репозитории
│   └── usecase/        — бизнес-логика (SyncPhotosUseCase)
├── ui/
│   ├── navigation/     — навигация и bottom nav
│   ├── screens/        — Compose-экраны (Home, Settings)
│   └── theme/          — Material 3 тема
└── worker/             — SyncWorker (WorkManager)
```

## Настройка и сборка

### 1. Клонировать репозиторий

```bash
git clone git@github.com:Grigory-Rylov/yadisk-sync.git
cd yadisk-sync
```

### 2. Получить OAuth-токен Яндекс.Диска

1. Перейти на https://oauth.yandex.ru/
2. Войти в нужный Яндекс-аккаунт
3. Создать приложение: **Новый токен** → выбрать **Диск** → **Доступ к файлам**
4. Скопировать полученный токен (формат: `y0__...`)

### 3. Создать `local.properties`

В корне проекта создать файл `local.properties`:

```properties
sdk.dir=/путь/к/android-sdk
ya.disk.access.token=y0__ВАШ_ТОКЕН_ЗДЕСЬ
```

> **Важно:** `local.properties` находится в `.gitignore` и **никогда не коммитится**.
> Если вы клонируете проект — файл нужно создать вручную.

### 4. Собрать

```bash
./gradlew assembleDebug
```

APK будет в `app/build/outputs/apk/debug/app-debug.apk`.

### 5. Установить на устройство

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Конфигурация сборки

В `app/build.gradle.kts` из `local.properties` извлекаются:

| Ключ                       | BuildConfig поле         | По умолчанию | Описание                          |
|----------------------------|--------------------------|--------------|-----------------------------------|
| `ya.disk.access.token`     | `BuildConfig.YA_DISK_TOKEN` | `""`       | OAuth-токен для API Яндекс.Диска  |
| —                          | `BuildConfig.SAVE_FILES_TO_DISK` | `false` | Флаг: реально сохранять файлы на диск (если `false` — только логи) |

## Yandex Disk API

Приложение работает с REST API Яндекс.Диска:

- `GET /v1/disk/resources/files` — список файлов в указанной папке (пагинация по 100)
- `GET /v1/disk/resources/download` — получение прямой ссылки на скачивание файла

Авторизация через заголовок `Authorization: OAuth <token>`.

## Разрешения

- `INTERNET` — сетевые запросы к Yandex Disk API
- `POST_NOTIFICATIONS` (Android 13+) — прогресс-уведомления во время синхронизации
- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` — запись файлов на диск
- `MANAGE_EXTERNAL_STORAGE` — доступ к папкам на внешнем хранилище (Android 11+)
- `RECEIVE_BOOT_COMPLETED` — восстановление периодической синхронизации после перезагрузки

## Лицензия

MIT
