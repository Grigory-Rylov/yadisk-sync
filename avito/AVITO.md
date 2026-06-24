# AVITO — Инструкция по управлению UI

## Механизм тапа

```bash
docker exec android-emulator adb shell input tap X Y
```

- `X Y` — целые координаты в пикселях (центр кликаемой области)
- Разрешение эмулятора: `1080x1920`
- После каждого тапа — `sleep 1` (или больше для загрузки экранов)
- Координаты берутся из `uiautomator dump` → `bounds="[x1,y1][x2,y2]"` → центр `(x1+x2)/2, (y1+y2)/2`

## Основные элементы

| Элемент | Resource ID | Bounds |
|---------|------------|--------|
| Поиск (EditText) | `com.avito.android:id/input_view` | `[168,102][918,258]` |
| Кнопка очистки поля | `com.avito.android:id/design_input_clear` | `[684,102][744,258]` |
| Назад (кнопка) | `com.avito.android:id/back_button_container` | `[48,144][120,216]` |
| Отмена (в поиске) | `com.avito.android:id/dismiss_text_view` | `[828,147][1044,213]` |
| Город/локация | `com.avito.android:id/geo_click_zone` | `[39,303][622,354]` |
| Фильтры | `com.avito.android:id/filters_click_zone` | `[819,305][1044,353]` |
| Сортировка | `com.avito.android:id/left_block` | `[30,390][352,474]` |
| Уведомления | `com.avito.android:id/right_block` | `[572,390][1050,474]` |
| Повторить (ошибка) | `com.avito.android:id/panel_error_retry` | `[406,1086][674,1194]` |
| Onboarding контейнер | `com.avito.android:id/replace_main_onboarding_container` | `[0,72][1080,1626]` |

## Нижняя навигация

| Вкладка | Bounds |
|---------|--------|
| Поиск | `[0,1626][216,1776]` |
| Избранное | `[216,1626][432,1776]` |
| Объявления | `[432,1626][648,1776]` |
| Сообщения | `[648,1626][864,1776]` |
| Профиль | `[864,1626][1080,1776]` |

## Как сделать поиск

1. Открыть поисковую строку:
   ```bash
   docker exec android-emulator adb shell input tap 543 180
   sleep 1
   ```

2. Очистить поле (если нужно):
   ```bash
   docker exec android-emulator adb shell input tap 714 180
   sleep 0.5
   ```

3. Ввести текст (для строк с пробелами — по словам через keyevent 62):
   ```bash
   docker exec android-emulator adb shell input text слово1
   sleep 0.3
   docker exec android-emulator adb shell input keyevent 62
   sleep 0.3
   docker exec android-emulator adb shell input text слово2
   sleep 1
   ```

4. Выбрать из подсказок или нажать Enter:
   ```bash
   # Вариант 1: тапнуть на подсказку (например, 1-я подсказка)
   docker exec android-emulator adb shell input tap 540 480
   sleep 5

   # Вариант 2: нажать Enter для поиска
   docker exec android-emulator adb shell input keyevent 66
   sleep 10
   ```

5. Выйти из поисковой строки:
   ```bash
   # Тапнуть на кнопку "Назад"
   docker exec android-emulator adb shell input tap 84 180
   sleep 1
   ```

6. Если запрос уже введён, выйти через "Отменить":
   ```bash
   docker exec android-emulator adb shell input tap 936 180
   sleep 1
   ```

## Как скрывать всплывающие баннеры и модальные окна

### Экран авторизации
```bash
# Кнопка закрытия в правом верхнем углу
docker exec android-emulator adb shell input tap 1017 108
sleep 1
```

### Диалог локализации и другие баннеры
1. Получить UI dump и проверить видимые элементы:
   ```bash
   docker exec android-emulator adb shell uiautomator dump /sdcard/window_dump.xml
   docker exec android-emulator adb shell cat /sdcard/window_dump.xml | grep -o 'text="[^"]*"'
   ```

2. Кнопки закрытия обычно находятся:
   - Правый верхний угол: `tap 1017 108`
   - Центрированный крестик: `tap 540 250`

### Cookie-диалог
- Найти кнопку принятия в UI dump и тапнуть по координатам.

### Onboarding и другие модальные окна
```bash
# Найти в UI dump элемент с text="Принять" или text="OK" или text="Далее"
# Тапнуть по центру его bounds
docker exec android-emulator adb shell input tap <X> <Y>
sleep 1
```

### Уведомления и всплывающие окна
- Использовать back button (`keyevent 4`) для закрытия:
  ```bash
  docker exec android-emulator adb shell input keyevent 4
  sleep 1
  ```

## Как считать координаты для тапа

Из UI dump извлекаю `bounds="[x1,y1][x2,y2]"`, затем вычисляю центр:

```
X = (x1 + x2) / 2
Y = (y1 + y2) / 2
```

**Пример:** bounds `"[406,1086][674,1194]"`
- X = (406 + 674) / 2 = 540
- Y = (1086 + 1194) / 2 = 1140
- Команда: `adb shell input tap 540 1140`

### Быстрый расчёт из dump

```bash
# Найти bounds нужного элемента по text или resource-id
docker exec android-emulator adb shell cat /sdcard/window_dump.xml | grep 'text="Повторить"'

# Вывод: bounds="[406,1086][674,1194]"
# Центр: X=540, Y=1140
```

## Полезные команды

```bash
# Получить UI dump
docker exec android-emulator adb shell uiautomator dump /sdcard/window_dump.xml

# Прочитать dump
docker exec android-emulator adb shell cat /sdcard/window_dump.xml

# Извлечь все текстовые элементы
docker exec android-emulator adb shell cat /sdcard/window_dump.xml | grep -o 'text="[^"]*"'

# Извлечь все bounds
docker exec android-emulator adb shell cat /sdcard/window_dump.xml | grep -o 'bounds="\[[^\]]*\]\[[^\]]*\]"'

# Скриншот
docker exec android-emulator adb exec-out screencap -p > /tmp/screenshot.png

# Запустить приложение
docker exec android-emulator adb shell am start -n com.avito.android/.ui.MainActivity

# Очистить данные (для сброса кэша)
docker exec android-emulator adb shell pm clear com.avito.android
```
