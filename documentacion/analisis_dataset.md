# Análisis del dataset

## 1. Origen

| | |
|---|---|
| Nombre | LoL Match Intervals: 2 Million In-Game Snapshots |
| Autor | Nathan Smallcalder |
| URL | https://www.kaggle.com/datasets/nathansmallcalder/league-of-legends-match-interval-snapshots-2026 |
| Fuente de los datos | API oficial de Riot Games, endpoints `match-v5` y `timeline-v5` |
| Alcance | 39.954 partidas Ranked Solo/Duo del servidor EUW (feb. 2024 – ene. 2026; el 96 % es de enero de 2026) |
| Licencia | Figura como "Other (specified in description)", pero la descripción no la especifica. Uso académico, citando la fuente |

El dataset completo pesa ~300 MB. El repo guarda un recorte reproducible de
100 partidas (ver `database/dataset_crudo/README.md`). Las cifras de este
documento se calcularon sobre el dataset completo salvo que se indique
"en el recorte".

## 2. Archivos

| Archivo | Filas (completo) | Filas (recorte) | Una fila por… | Clave |
|---|---|---|---|---|
| `matches.csv` | 39.954 | 100 | partida | `match_id` |
| `processed_summoner_data.csv` | 399.540 | 1.000 | participante en una partida | `id` (también `match_id` + `participant_id`) |
| `intervals.csv` | 2.108.090 | 5.460 | participante cada 5 minutos | `id` (también `player_id` + `minute`) |
| `ChampionTbl.csv` | 173 | 173 | campeón | `ChampionId` |
| `ItemTbl.csv` | 635 | — | ítem | `ItemID` (fuera de alcance) |

### Relaciones entre archivos (verificadas)

```
matches.match_id  1 ───< 10  processed_summoner_data.match_id
processed_summoner_data.id  1 ───< N  intervals.player_id
ChampionTbl.ChampionName  1 ───< N  processed_summoner_data.champion   (por nombre)
ChampionTbl.ChampionId    1 ───< N  matches.blue_bans / red_bans       (por id, dentro de una lista)
```

- `intervals.player_id` **no** es la posición 1–10 del jugador: es el `id`
  global de `processed_summoner_data`. Se verificó en las 2,1 millones de filas
  que la partida de ambos lados coincide.
- Todas las partidas tienen exactamente 10 participantes y todos los
  participantes de una misma partida tienen la misma cantidad de snapshots.
- No hay claves huérfanas en ningún sentido. 442 partidas (todas de menos de
  4 minutos) no tienen snapshots.

### Diccionario de columnas

**`matches.csv`**

| Columna | Descripción | Uso |
|---|---|---|
| `match_id` | ID de Riot (ej. `EUW1_7677509245`) | Clave de la partida |
| `game_duration` | Duración en segundos | Se usa |
| `game_date` | Fecha y hora de inicio | Se usa |
| `game_version` | Versión completa (ej. `16.2.740.1491`) | Se usa |
| `patch_version` | Parche (ej. `16`) | **Derivado**: primer número de `game_version` |
| `winning_team` | Lado ganador: 100 (azul) o 200 (rojo) | Se usa |
| `average_rank` | Rango promedio de los 10 jugadores | Se usa |
| `game_mode`, `queue_id`, `region` | `CLASSIC`, `420`, `euw1` | **Constantes** en todo el dataset |
| `blue_bans`, `red_bans` | Lista de 5 IDs de campeón separados por coma; `-1` = turno sin ban | Se usa (**no atómico**) |

**`processed_summoner_data.csv`**

| Columna | Descripción | Uso |
|---|---|---|
| `id` | ID global del participante | Clave |
| `match_id` | Partida | FK |
| `participant_id` | Número 1–10 dentro de la partida | **Determina el lado**: 1–5 → 100, 6–10 → 200 |
| `team_id` | Lado: 100 o 200 | Se usa |
| `champion` | **Nombre** del campeón | Se usa (se reemplaza por su ID) |
| `role` | TOP, JUNGLE, MIDDLE, BOTTOM, UTILITY | Se usa |
| `individual_position` | Posición detectada por Riot | Se descarta: tiene repetidos y valores inválidos (ver §6) |

**`intervals.csv`**

| Grupo | Columnas | Uso |
|---|---|---|
| Identificación | `id`, `match_id`, `player_id`, `minute` | Clave (`player_id` + `minute`) |
| Participante | `current_gold`, `total_gold`, `cs`, `jungle_cs`, `xp`, `level`, `kills`, `deaths`, `assists` | Se usa |
| Ítems | `item_0` … `item_6` | Fuera de alcance (grupo repetido) |
| Equipo | `team_inhibitors`, `team_towers`, `team_dragons_fire`, `_water`, `_earth`, `_air`, `_chemtech`, `_hextech`, `team_barons`, `team_void_grubs`, `team_heralds` | Se usa, pero **no depende del participante** (ver §4) |
| Derivadas | `team_kills`, `team_dragons`, `team_gold_diff`, `gold_diff`, `xp_diff` | **Se descartan** (ver §4) |

## 3. Entidades identificadas

| Entidad | Origen | Identificador |
|---|---|---|
| **Partida** | `matches.csv` | `match_id` |
| **Participante** (un jugador dentro de una partida) | `processed_summoner_data.csv` | `id`; también (partida, lado, rol) |
| **Campeón** | `ChampionTbl.csv` | `ChampionId` |
| **Rol** | valores de `role` | código (TOP, JUNGLE…) |
| **Lado** (equipo azul / rojo) | `team_id`, `winning_team` | 100 / 200 |
| **Ban** | `blue_bans`, `red_bans` | (partida, lado, turno) |
| **Snapshot de participante** | columnas de participante de `intervals.csv` | (participante, minuto) |
| **Snapshot de equipo** | columnas `team_*` de `intervals.csv` | (partida, lado, minuto) |

**No existe la entidad "jugador" entre partidas.** El dataset no trae nombre
de invocador ni PUUID: cada participación tiene un ID propio, así que no se
puede reconstruir el historial de una persona. Por el mismo motivo, el rango
no es de un jugador sino el promedio de la partida.

## 4. Dependencias funcionales y redundancias

La "tabla universal" que resulta de unir los tres archivos tiene una fila por
participante y minuto, con 55 columnas, y clave **(match_id, player_id, minute)**.

### Ejemplo real

Partida `EUW1_7677509245`, minuto 20 (columnas seleccionadas):

| player_id | lado | rol | campeón | total_gold | kills | team_kills | team_towers | team_dragons | team_gold_diff | gold_diff |
|---|---|---|---|---|---|---|---|---|---|---|
| 303141 | 100 | TOP | Gragas | 8538 | 4 | 8 | 3 | 3 | -2205 | 1740 |
| 303142 | 100 | JUNGLE | Belveth | 6797 | 0 | 8 | 3 | 3 | -2205 | -864 |
| 303143 | 100 | MIDDLE | Fizz | 6904 | 2 | 8 | 3 | 3 | -2205 | -2410 |
| 303144 | 100 | BOTTOM | Caitlyn | 7820 | 1 | 8 | 3 | 3 | -2205 | -1027 |
| 303145 | 100 | UTILITY | Lulu | 5467 | 1 | 8 | 3 | 3 | -2205 | 356 |
| 303146 | 200 | TOP | Zyra | 6798 | 0 | 12 | 3 | 0 | 2205 | -1740 |
| … | | | | | | | | | | |

Se ve que `team_kills`, `team_towers`, `team_dragons` y `team_gold_diff` se
repiten en las 5 filas de cada equipo, y que los datos de la partida
(duración, fecha, ganador, bans) se repetirían en sus 50 filas.

### Dependencias encontradas

| Dependencia | Tipo | Verificación |
|---|---|---|
| `match_id` → duración, fecha, versión, ganador, rango, bans | Parcial (depende de parte de la clave) | Por definición de `matches.csv` |
| `player_id` → `match_id`, lado, rol, campeón | Parcial | 100 % de las filas |
| `participant_id` → lado | Transitiva (1–5 → 100, 6–10 → 200) | 399.540 de 399.540 filas |
| (partida, lado, minuto) → las 11 columnas `team_*` no derivadas | Transitiva (vía `player_id` → lado) | 100 % constantes dentro de cada grupo |
| nombre del campeón → ID del campeón | Transitiva | Todos los nombres cruzan con el catálogo (salvo mayúsculas, §6) |
| `game_version` → `patch_version` | Transitiva | 100 % |

### Columnas derivadas (redundantes)

| Columna | Se calcula como | Coincidencia |
|---|---|---|
| `team_kills` | suma de `kills` de los 5 participantes del lado | 100 % |
| `team_gold_diff` | Σ `total_gold` del lado − Σ `total_gold` del rival | 100 % |
| `gold_diff` / `xp_diff` | `total_gold` / `xp` − el del rival con el mismo `role` | 100 % (comparando por `individual_position` baja a 99,7 %: las 641 partidas que no cuadran tienen posiciones repetidas o inválidas) |
| `team_dragons` | suma de los 6 tipos de dragón + Dragón Elder | 99,6 % sin Elder; la diferencia es exactamente la cantidad de Elders, que no tiene columna propia |
| `patch_version` | primer número de `game_version` | 100 % |

`team_gold_diff` es valioso como control: la curva de ventaja de oro que
calcula la aplicación (en SQL y en Flux) tiene que coincidir exactamente con
este valor.

## 5. Anomalías de la estructura sin normalizar

**Actualización**
- Corregir las torres de un equipo en un minuto obliga a modificar 5 filas;
  si se olvida una, el mismo equipo tiene dos cantidades de torres distintas
  en el mismo instante.
- Corregir la duración o el ganador de una partida obliga a modificar sus
  ~50 filas.
- Corregir el nombre de un campeón ("FiddleSticks") obliga a recorrer todas
  las partidas donde aparece.
- Si se corrige el oro de un participante, `team_gold_diff` y `gold_diff`
  quedan desactualizados en 10 filas (las del equipo y las del rival).

**Inserción**
- No se puede registrar una partida, sus bans o su ganador sin al menos un
  snapshot (la clave incluye `minute`).
- No se puede cargar un campeón nuevo hasta que alguien lo juegue.
- Un ban no puede registrarse por separado: vive dentro de un texto con 5 IDs.

**Eliminación**
- Si se borran los snapshots de una partida, se pierden su fecha, duración,
  ganador y bans, y qué campeón jugó cada participante.
- Borrar el último snapshot de un participante borra la única evidencia de
  sus estadísticas finales.

## 6. Calidad de datos y decisiones de limpieza

| Problema | Alcance (completo) | Decisión |
|---|---|---|
| Partidas de menos de 5 minutos (remakes) | 455 | Excluidas del recorte |
| Partidas sin snapshots | 442 | Excluidas |
| `current_gold` negativo | 8 snapshots | Excluidas esas partidas |
| `individual_position` repetida en un equipo | 660 equipos | Se usa `role`, que nunca se repite |
| `individual_position` = `Invalid` / `TOPJUNGLE` | 60 partidas | Excluidas |
| `match_id` de otros servidores (`EUN1_`, `TR1_`) con `region = euw1` | 58 partidas | Excluidas (solo `EUW1_`) |
| Campeón "FiddleSticks" en participantes vs "Fiddlesticks" en el catálogo | 1 campeón | Se cruza ignorando mayúsculas |
| Turnos sin ban (`-1`) | 11.108 de 399.540 turnos | No generan fila de ban |
| El mismo campeón baneado por ambos equipos | 16.744 partidas | Válido en Ranked; se conserva |
| Último snapshot etiquetado con un minuto mayor que la duración (es el final de la partida, no un minuto exacto) | 19 % de las partidas | Se respeta el valor del dataset y se documenta |
| El ganador termina con menos oro (remontadas) | 7,5 % de las partidas | Dato válido; útil para la demo |

## 7. Recorte de 100 partidas

Criterios: enero de 2026, parche 16, entre 20 y 40 minutos, solo `EUW1_`, con
snapshots, sin posiciones inválidas y sin oro negativo. Cumplen 32.670
partidas; se eligen 100 al azar con semilla fija (`2026`).

| Métrica (en el recorte) | Valor |
|---|---|
| Partidas | 100 (fechas: 9 al 30 de enero de 2026) |
| Ganador | Rojo 57, azul 43 |
| Duración | 20:44 a 39:35 (promedio 28:58) |
| Rango promedio | Master 51, Diamond 30, Emerald 9, Challenger 4, Grandmaster 3, Unranked 2, Gold 1 |
| Participantes | 1.000 |
| Campeones distintos | 164 |
| Snapshots de participante | 5.460 (40 a 80 por partida, minutos 5 a 40) |
| Snapshots de equipo (una vez normalizado) | 1.092 |
| Remontadas (ganó el equipo con menos oro final) | 8 partidas |
| Snapshots con Dragón Elder | 10 |
| Partidas con un campeón baneado por ambos equipos | 39 |
| Turnos sin ban | 23 de 1.000 |
| Participantes "FiddleSticks" | 5 |

## 8. Fuera de alcance

- **Ítems** (`item_0`…`item_6`, `ItemTbl.csv`): decisión del equipo.
- **Ping y FPS**: no existen en el dataset y el equipo decidió no simularlos.
- **Daño**: el dataset no lo trae en el tiempo.
