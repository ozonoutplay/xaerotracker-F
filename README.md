# XaeroTracker — Fabric Port

Server-side Fabric мод для Minecraft **1.21.4**, порт плагина [XaeroTracker](https://github.com/inf-mc/XaeroTracker) с Paper на Fabric.

Показывает всех игроков на карте Xaero's Minimap / Xaero's World Map у клиентов, у которых установлен этот мод.

## Требования

- Minecraft 1.21.4
- Fabric Loader ≥ 0.16.0
- Fabric API

> Серверный мод — клиентам ничего ставить не нужно кроме самого Xaero's Minimap/WorldMap.

## Сборка

```bash
./gradlew build
```

JAR будет в `build/libs/xaerotracker-fabric-1.0.0.jar`

## Конфиг

При первом запуске создаётся `config/xaerotracker/xaerotracker.properties`:

```properties
should-send-level-id: true     # Отправлять ли level ID клиентам (нужно для работы)
level-id: 12345678             # Уникальный ID сервера, сгенерируется автоматически
sync-cooldown: 250             # Задержка между синками позиций (мс)
only-sync-same-world: false    # Показывать только игроков в том же измерении
```

## Команды

```
/xt toggleTracked                   — скрыть/показать себя на картах других
/xt toggleTracked <игрок>           — скрыть/показать другого игрока (op)
/xt toggleTrackEveryone             — видеть всех игроков включая скрытых (op)
/xt toggleTrackEveryone <игрок>     — дать право видеть всех другому игроку (op)
```

Также работает алиас `/xaerotracker`.

## Отличия от Paper-версии

| Paper | Fabric |
|-------|--------|
| `plugin.yml` permissions | Brigadier permissions (op level) |
| `vanished` metadata | — |
| YAML конфиг | `.properties` конфиг |
| `FilePlayerList` (YAML) | `FilePlayerList` (plain text) |

