# Normalización del modelo

Este documento muestra cómo el dataset pasa de su estructura inicial al modelo
relacional en Tercera Forma Normal (3FN) implementado en
`database/modelo_relacional.sql`. El análisis de entidades, dependencias y
calidad de datos que lo sustenta está en `documentacion/analisis_dataset.md`.

## 0. Estructura inicial (sin normalizar)

El dataset trae tres archivos relacionados (`matches.csv`,
`processed_summoner_data.csv` e `intervals.csv`) más un catálogo de campeones.
Para analizarlo como una única relación se parte de la **tabla universal**
que resulta de unirlos: una fila por participante y por minuto.

```
UNIVERSAL(
  match_id, game_duration, game_date, game_version, patch_version,
  winning_team, game_mode, queue_id, region, average_rank,
  blue_bans, red_bans,                                         -- de matches.csv
  player_id, participant_id, team_id, champion, role,
  individual_position,                                         -- de processed_summoner_data.csv
  minute, current_gold, total_gold, cs, jungle_cs, xp, level,
  kills, deaths, assists,                                      -- del participante en el minuto
  team_kills, team_inhibitors, team_towers, team_dragons_fire,
  team_dragons_water, team_dragons_earth, team_dragons_air,
  team_dragons_chemtech, team_dragons_hextech, team_dragons,
  team_barons, team_void_grubs, team_heralds,                  -- del equipo en el minuto
  gold_diff, xp_diff, team_gold_diff                           -- diferencias precalculadas
)
```

Las columnas `item_0` … `item_6` del dataset quedan fuera de alcance por
decisión del equipo.

Ejemplo real: partida `EUW1_7677509245`, minuto 20 (columnas seleccionadas).

| player_id | minute | duración | ganador | blue_bans | team_id | role | champion | total_gold | kills | team_kills | team_towers | team_gold_diff |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 303141 | 20 | 1590 | 200 | 21,91,69,238,266 | 100 | TOP | Gragas | 8538 | 4 | 8 | 3 | -2205 |
| 303142 | 20 | 1590 | 200 | 21,91,69,238,266 | 100 | JUNGLE | Belveth | 6797 | 0 | 8 | 3 | -2205 |
| 303143 | 20 | 1590 | 200 | 21,91,69,238,266 | 100 | MIDDLE | Fizz | 6904 | 2 | 8 | 3 | -2205 |
| … | | | | | | | | | | | | |
| 303146 | 20 | 1590 | 200 | 21,91,69,238,266 | 200 | TOP | Zyra | 6798 | 0 | 12 | 3 | 2205 |

Problemas visibles: la duración, el ganador y los bans se repiten en las 50
filas de la partida; las columnas `team_*` se repiten en las 5 filas de cada
equipo; `blue_bans` guarda cinco valores en un solo campo. Las anomalías de
inserción, actualización y eliminación que esto produce están detalladas en
`analisis_dataset.md` (sección 5).

**Clave de UNIVERSAL**: `(player_id, minute)`. Aunque los archivos usan
`match_id` en todas las filas, `player_id` ya determina la partida (es un ID
de participación, no de persona), así que no forma parte de la clave mínima.

## 1. Primera Forma Normal (1FN)

**Regla**: todos los valores son atómicos y no hay grupos repetidos.

| Violación | Dónde | Solución |
|---|---|---|
| Lista de valores en un campo | `blue_bans`, `red_bans` (ej. `"21,91,69,238,266"`) | Se separan en una relación propia, con una fila por ban |
| Grupo repetido | `item_0` … `item_6` | Fuera de alcance (no se modelan ítems) |

El orden de la lista tiene significado (es el turno del ban), así que se
conserva como atributo. El valor `-1` significa "turno sin ban" y no genera
fila.

```
BANS(match_id, lado, turno, campeon_id)
  clave: (match_id, lado, turno)

UNIVERSAL_1FN = UNIVERSAL sin blue_bans ni red_bans
  clave: (player_id, minute)
```

Ejemplo: la lista azul `"21,91,69,238,266"` de `EUW1_7677509245` se convierte en

| match_id | lado | turno | campeon_id |
|---|---|---|---|
| EUW1_7677509245 | 100 | 1 | 21 (Miss Fortune) |
| EUW1_7677509245 | 100 | 2 | 91 (Talon) |
| EUW1_7677509245 | 100 | 3 | 69 (Cassiopeia) |
| EUW1_7677509245 | 100 | 4 | 238 (Zed) |
| EUW1_7677509245 | 100 | 5 | 266 (Aatrox) |

Talon (91) también aparece en la lista roja de esa partida: los dos equipos
pueden banear el mismo campeón. Lo que no ocurre en ningún caso del dataset
es que un equipo repita su propio ban, por eso `(match_id, lado, campeon_id)`
también es único.

## 2. Segunda Forma Normal (2FN)

**Regla**: está en 1FN y ningún atributo no clave depende de una parte de la
clave.

La clave de `UNIVERSAL_1FN` es `(player_id, minute)`. Todo lo que describe al
participante o a su partida depende solo de `player_id`, sin importar el
minuto:

```
player_id → match_id, participant_id, team_id, champion, role,
            individual_position,
            game_duration, game_date, game_version, patch_version,
            winning_team, game_mode, queue_id, region, average_rank
```

Se separan en una relación cuya clave es `player_id`:

```
PARTICIPANTE_2FN(player_id, match_id, participant_id, team_id, champion,
                 role, individual_position, game_duration, game_date,
                 game_version, patch_version, winning_team, game_mode,
                 queue_id, region, average_rank)
  clave: player_id

SNAPSHOT_2FN(player_id, minute, current_gold, total_gold, cs, jungle_cs,
             xp, level, kills, deaths, assists, team_kills, team_inhibitors,
             team_towers, team_dragons_*, team_dragons, team_barons,
             team_void_grubs, team_heralds, gold_diff, xp_diff,
             team_gold_diff)
  clave: (player_id, minute)
```

Ahora cada atributo de `SNAPSHOT_2FN` depende de la clave completa: el oro de
un jugador cambia minuto a minuto, y los datos de su equipo también.

## 3. Tercera Forma Normal (3FN)

**Regla**: está en 2FN y ningún atributo no clave depende de otro atributo no
clave (no hay dependencias transitivas).

### 3.1 Datos de la partida dentro del participante

```
player_id → match_id → game_duration, game_date, game_version, winning_team, average_rank, …
```

Los datos de la partida dependen del participante solo a través de
`match_id`. Se separan en **PARTIDAS** con clave `match_id`, y en el
participante queda `match_id` como clave foránea.

Dentro de PARTIDAS quedan dos casos más:

| Atributo | Problema | Decisión |
|---|---|---|
| `patch_version` | `game_version → patch_version` (es su primer número): transitiva | Se elimina; se obtiene en la vista `v_resumen_partidas` |
| `game_mode`, `queue_id`, `region` | Toman un único valor en todo el dataset (`CLASSIC`, `420`, `euw1`) | No se almacenan; se documentan como alcance del dataset |

**Decisión: el rango es de la partida, no del jugador.** `average_rank` es
el promedio de los 10 participantes y depende solo de `match_id`. Además, el
dataset no identifica a las personas entre partidas, así que no existe una
entidad "jugador" que pueda tener un rango propio. Queda como
`partidas.rango_promedio`.

### 3.2 Lado del participante

```
participant_id → team_id      (1–5 → 100, 6–10 → 200; se cumple en las 399.540 filas)
```

Guardar los dos crea una dependencia transitiva. Se conserva el **lado**
(`team_id`), que es lo que usan las consultas, y se descarta
`participant_id`. El participante queda identificado de forma natural por
`(partida, lado, rol)`, que es única en todo el dataset.

`individual_position` también se descarta, pero por calidad de datos y no por
normalización: repite posiciones dentro de un equipo y tiene valores
inválidos, mientras que `role` nunca se repite (ver `analisis_dataset.md`, §6).

### 3.3 Campeón y rol

El campeón aparece con dos representaciones distintas: por **nombre** en los
participantes y por **ID** en los bans. Eso permite inconsistencias (en el
dataset, "FiddleSticks" en un archivo y "Fiddlesticks" en otro).

```
campeon_id → nombre
```

Se crea el catálogo **CAMPEONES**, con el ID oficial de Riot como clave, y
participantes y bans lo referencian por clave foránea. Del mismo modo, los
cinco roles pasan al catálogo **ROLES**, que guarda también su nombre en
castellano y su orden de presentación.

### 3.4 Datos del equipo dentro del snapshot

Las 13 columnas `team_*` no dependen del jugador sino de su equipo:

```
(player_id, minute) → (match_id, team_id, minute) → team_towers, team_barons, …
```

Se verificó en el dataset completo que son idénticas en las 5 filas de cada
(partida, equipo, minuto). Se separan en **SNAPSHOTS_EQUIPO** con clave
`(partida, lado, minuto)`.

### 3.5 Columnas derivadas

Hay columnas que no agregan información porque se calculan a partir de otras.
Guardarlas obliga a mantenerlas sincronizadas (si se corrige el oro de un
jugador, `team_gold_diff` y `gold_diff` quedan desactualizados).

| Columna | Se calcula como | Dónde se obtiene |
|---|---|---|
| `team_kills` | suma de `kills` de los 5 participantes del lado | consulta (`SUM … GROUP BY`) |
| `team_gold_diff` | oro total del lado − oro total del rival | consulta (curva de ventaja de oro) |
| `gold_diff`, `xp_diff` | oro / XP − los del rival con el mismo rol | consulta |
| `team_dragons` | suma de los seis tipos + Dragón Elder | consulta; se agrega `dragones_elder`, que el dataset no tenía como columna |
| `patch_version` | primer número de `game_version` | vista `v_resumen_partidas` |

**Decisión: la victoria es derivada y no se almacena.** Que un participante
haya ganado se obtiene comparando su lado con `partidas.lado_ganador`. Lo
mismo pasa con sus estadísticas finales (KDA, oro y CS al terminar): son su
último snapshot. Las dos cosas se exponen en la vista
`v_estadisticas_finales`.

## 4. Modelo final

| Tabla | Clave primaria | Otras claves candidatas | Claves foráneas |
|---|---|---|---|
| `campeones(id, nombre)` | `id` | `nombre` | — |
| `roles(codigo, nombre, orden)` | `codigo` | `nombre`, `orden` | — |
| `partidas(id, fecha_inicio, duracion_segundos, version_juego, lado_ganador, rango_promedio)` | `id` | — | — |
| `bans(partida_id, lado, turno, campeon_id)` | `(partida_id, lado, turno)` | `(partida_id, lado, campeon_id)` | partida, campeón |
| `participantes(id, partida_id, lado, rol, campeon_id)` | `id` | `(partida_id, lado, rol)`, `(partida_id, campeon_id)` | partida, rol, campeón |
| `snapshots_participante(participante_id, minuto, oro_actual, oro_total, cs, cs_jungla, xp, nivel, kills, deaths, assists)` | `(participante_id, minuto)` | — | participante |
| `snapshots_equipo(partida_id, lado, minuto, torres, inhibidores, dragones_fuego, dragones_agua, dragones_tierra, dragones_aire, dragones_quimtec, dragones_hextech, dragones_elder, barones, larvas_vacio, heraldos)` | `(partida_id, lado, minuto)` | — | partida |

### Verificación de 3FN

En cada tabla, todo atributo no clave depende de la clave, de la clave
completa y de nada más que la clave. El modelo cumple además la **Forma
Normal de Boyce-Codd**: los únicos determinantes de cada tabla son sus claves
candidatas (por ejemplo, en `participantes`, `id`, `(partida_id, lado, rol)`
y `(partida_id, campeon_id)` son las tres claves candidatas y no hay otra
dependencia).

Un caso que parece una dependencia transitiva y no lo es: en el juego el
nivel se sube al acumular XP, así que podría pensarse que `xp → nivel`. Hasta
el nivel 17 los umbrales son fijos, pero a partir del 18 no: hay jugadores en
nivel 18 con más XP que otros en nivel 19 o 20 (en el dataset completo, 4.102
valores de XP aparecen con más de un nivel). Como la dependencia no se cumple,
`nivel` se almacena.

### Verificación de que no se pierde información

Se cargó el recorte de 100 partidas en el modelo y se reconstruyó la tabla
universal con JOINs, recalculando las columnas derivadas:

- Las 5.460 filas de `intervals.csv`, comparando 33 columnas (partida,
  valores del participante, del equipo, derivadas, datos de la partida, lado,
  rol y campeón), coinciden exactamente.
- Las listas de bans de las 100 partidas se reconstruyen idénticas, con los
  `-1` en los turnos sin ban.

Lo único que no se recupera es lo que se descartó a propósito:
`participant_id`, `individual_position`, las columnas constantes y los ítems.

### Correspondencia con el dataset

| Dataset | Modelo |
|---|---|
| `matches.match_id`, `game_date`, `game_duration`, `game_version`, `winning_team`, `average_rank` | `partidas` |
| `matches.blue_bans`, `red_bans` | `bans` |
| `processed_summoner_data.id`, `match_id`, `team_id`, `role`, `champion` | `participantes` (el nombre del campeón pasa a `campeon_id`) |
| `ChampionTbl.csv` (sin el ID 0, "No Champion") | `campeones` |
| `intervals`: columnas del participante | `snapshots_participante` |
| `intervals`: columnas `team_*` no derivadas | `snapshots_equipo` |
| `patch_version`, `team_kills`, `team_dragons`, `team_gold_diff`, `gold_diff`, `xp_diff` | vistas y consultas |
| `participant_id`, `individual_position`, `game_mode`, `queue_id`, `region`, `item_*` | no se almacenan |
