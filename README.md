# MirageDArena `v0.1 Pre-Release`

Плагин **арены смерти** для **Paper 1.21.4** (Java 21). Игроки заходят на арену через NPC или команду, сражаются в постоянном PvP-режиме и выходят с задержкой: **30 сек** по `/leave`, **15 сек** через NPC. Всё настраивается в `config.yml` и `lang.yml`.

![Paper](https://img.shields.io/badge/Paper-1.21.4-00ACFF?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-21-FF7B00?style=for-the-badge)
![Version](https://img.shields.io/badge/version-0.1-00B0F2?style=for-the-badge)
![Studio](https://img.shields.io/badge/MirageStudio-sawervatoryy-8A2BE2?style=for-the-badge)

> Статус: **Pre-Release**. Код написан под Paper API 1.21.4, но на момент публикации ещё не проходил проверку на живом сервере — пожалуйста, сообщайте о багах в Discord.

## Возможности

- Несколько миров-арен, у каждого своё имя, иконка и точка входа.
- **NPC** входа и выхода без зависимости от Citizens (любая живая сущность, по умолчанию житель).
- **Выход через NPC** работает даже в PvP-режиме; при выходе открывается меню выбора мира арены смерти (мир применяется при следующем входе).
- `/leave` — задержка 30 сек, `/leave cancel` — отмена. Через NPC — 15 сек. Если игрок уже ждёт по `/leave` и нажал на NPC, оставшееся время сокращается до 15 сек.
- Отмена выхода при движении (и, опционально, при уроне).
- **Постоянное обновление PvP-режима** каждые N тиков без спама в консоль: команды выполняются от «тихой» консоли, ошибки логируются один раз.
- Белый список команд на арене; `/leave` разрешается, даже если PvP-плагин его отменил.
- Состояние игрока (на арене, место возврата, выбранный мир) хранится в PDC и переживает рестарт.
- Кастомные события для интеграций: `ArenaJoinEvent` (отменяемое), `ArenaLeaveEvent`.
- Все сообщения — в `lang.yml`; пустая строка отключает сообщение. Цвета `&f`, HEX `&#00ACFF`.

## Требования

- Paper 1.21.4, Java 21.
- Внешние библиотеки **не нужны**, ничего не шейдится (секция `libraries` пуста).
- Необязательно: GreatCombat, CombatLogX, PvPManager и любой другой PvP-плагин (через команды).

## Установка

1. Скачайте `MirageDArena-v0.1-PreRelease.jar` и положите в `plugins/`.
2. Запустите сервер — появятся `config.yml` и `lang.yml`.
3. Добавьте мир арены: `/darena world add <мир>` и точку входа `/darena world setspawn`.
4. Поставьте NPC: `/darena npc create vhod join` и `/darena npc create vyhod leave`.

## Команды

| Команда | Описание | Право |
|---|---|---|
| `/leave` | Выйти с арены (задержка) | `miragedarena.leave` (всем) |
| `/leave cancel` | Отменить выход | `miragedarena.leave` |
| `/darena join [мир]` | Войти на арену | `miragedarena.join` (всем) |
| `/darena reload` | Перезагрузить конфиги (асинхронно) | `miragedarena.admin` |
| `/darena status` | Состояние плагина | `miragedarena.admin` |
| `/darena kick <игрок>` | Убрать игрока с арены | `miragedarena.admin` |
| `/darena world add\|remove\|setspawn\|list` | Миры арены | `miragedarena.admin` |
| `/darena setexit` | Запасная точка выхода | `miragedarena.admin` |
| `/darena npc create <id> <join\|leave>` | Создать NPC на своей позиции | `miragedarena.admin` |
| `/darena npc remove <id>` / `list` | Удалить / список NPC | `miragedarena.admin` |

Дополнительные права: `miragedarena.npc.use` (клик по NPC, всем), `miragedarena.bypass.commands` (обход ограничения команд, op).

## Настройка

### Миры арены и «арена смерти»

```yaml
arena:
  default-world: "darena"
  worlds:
    darena:
      display-name: "&#00ACFF«&f Арена смерти &#00ACFF»"
      icon: DIAMOND_SWORD
      spawn: {x: 0.5, y: 100.0, z: 0.5}   # необязательно
```

Если миров больше одного и включён `leave.npc-world-select`, при выходе через NPC открывается меню: игрок выбирает мир, после чего стартует 15-секундный отсчёт. Выбранный мир используется при следующем входе (через NPC или команду).

### NPC

```yaml
npc:
  default-type: VILLAGER
  list:
    spawn_join:
      role: JOIN
      location: {world: world, x: 0.5, y: 80.0, z: 0.5, yaw: 0.0, pitch: 0.0}
```

NPC не сохраняются в мире, а пересоздаются при старте и `/darena reload`; чанки NPC удерживаются plugin chunk ticket, раз в `health-check-seconds` проверяется, что NPC на месте.

### Задержки выхода

```yaml
leave:
  command-delay-seconds: 30
  npc-delay-seconds: 15
  cancel-on-move: true
```

## PvP-плагины

PvP-режим обновляется каждые `pvp.refresh-interval-ticks` тиков: принудительно включается PvP в мире арены и выполняются команды провайдеров. Плейсхолдеры: `{player}`, `{uuid}`, `{world}`.

```yaml
pvp:
  providers:
    greatcombat:
      enabled: true
      plugin: "GreatCombat"
      start-commands: []     # при входе
      refresh-commands: []   # каждые N тиков
      end-commands: []       # при выходе
```

Провайдер пропускается, если плагин не установлен или все три списка пусты. Команды идут через «тихого» отправителя — консоль не засоряется.

**Важно про GreatCombat.** У плагина нет публичной документации API/команд, которую можно было бы подключить «из коробки», поэтому по умолчанию списки пусты — впишите команды для тега/продления боевого режима из вашей версии GreatCombat. Чтобы `/leave` работал в боевом режиме, плагин сам снимает отмену команды (`commands.force-allow: true`), но на всякий случай добавьте `/leave` в разрешённые команды GreatCombat.

Другие плагины могут слушать `ArenaJoinEvent` / `ArenaLeaveEvent`.

## Сборка

```bash
mvn clean package
```

Jar появится в `target/MirageDArena-v0.1-PreRelease.jar`. GitHub Actions (`.github/workflows/build.yml`) собирает проект на каждый push.

## Структура

```
org.github.sawervatoryy.mirageDArena
├── MirageDArena            // точка входа, сборка сервисов
├── arena/                  // ArenaService, LeaveService, LeaveCause
├── pvp/                    // PvpService, PvpProvider, CommandPvpProvider
├── npc/                    // NpcService, NpcRole
├── gui/                    // WorldSelectMenu
├── config/                 // ConfigService, Settings (records), Point
├── lang/                   // Messages
├── listener/ command/ event/ util/
```

## Потоки и производительность

- Чтение при `/darena reload` и запись `config.yml` — на отдельном виртуальном потоке; применение — на главном.
- Чанки NPC грузятся асинхронно (`getChunkAtAsync`), телепорты — `teleportAsync`.
- Единственное синхронное чтение — загрузка файлов при старте сервера.

## Лицензия

MIT — см. `LICENSE`.
