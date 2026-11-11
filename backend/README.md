# Backend — Spring Boot + Kotlin

API REST de la telemetría de partidas. Lee PostgreSQL (datos estructurales) e InfluxDB (snapshots por minuto).

## Cómo correrlo

Requisitos: JDK 21 y las bases levantadas con Docker Compose desde la raíz del repo (`docker compose up -d`).

```bash
cd backend
./gradlew bootRun          # levanta la API en http://localhost:8080
./gradlew test             # tests unitarios (no necesitan bases)
./gradlew testIntegracion  # tests contra PostgreSQL e InfluxDB (necesitan docker compose)
```

En Windows: `gradlew.bat bootRun`.

### Configuración

Todo tiene valores por defecto que coinciden con `docker-compose.yaml`. Se pueden cambiar con variables de entorno:

| Variable | Default | Para qué |
|---|---|---|
| `MOTOR_POR_DEFECTO` | `postgres` | Motor de los listados cuando el request no indica uno (`postgres` o `influx`) |
| `POSTGRES_HOST` / `POSTGRES_PORT` / `POSTGRES_DB` | `localhost` / `5433` / `lol_telemetry` | Conexión a PostgreSQL |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `lol_user` / `lol_dev_password` | |
| `INFLUXDB_URL` | `http://localhost:8086` | Conexión a InfluxDB |
| `INFLUXDB_TOKEN` / `INFLUXDB_ORG` / `INFLUXDB_BUCKET` | `tp-bbdd-token-local` / `unsam` / `lol_snapshots` | |
| `CORS_ORIGENES` | `http://localhost:5173` | Orígenes del frontend (separados por coma) |
| `SERVER_PORT` | `8080` | |

## Arquitectura

```
api/          controladores REST + manejo de errores (JSON uniforme)
servicio/     lógica de aplicación: no importa nada de SQL ni de Flux
repositorio/  interfaces + implementaciones por motor
  postgres/   JdbcTemplate con SQL explícito
  influx/     cliente oficial de InfluxDB con consultas Flux
motor/        enum Motor y resolución del motor de cada request
dominio/      modelos compartidos
```

### Dónde está el polimorfismo

Los **listados de partidas y de participantes** existen en los dos motores, así que tienen una interfaz y dos implementaciones:

| Interfaz | PostgreSQL | InfluxDB |
|---|---|---|
| `PartidaRepository.listar()` | tabla `partidas` | primer `team_snapshot` de cada `match_id` menos 5 minutos |
| `ParticipanteRepository.listarPorPartida(id)` | `participantes` + `campeones` + `roles` | tags `participant_id`, `side`, `role`, `champion` de `participant_snapshot` |

El servicio recibe todas las implementaciones y elige una según el motor del request (`RepositoriosPorMotor`). Las dos devuelven exactamente lo mismo para las 100 partidas y los 1.000 participantes (`IntegracionTest`).

Los datos que existen en **un solo motor** se leen siempre de ahí:

- Solo PostgreSQL (`PartidaInfoRepository`): duración, versión del juego, lado ganador y rango promedio.
- Solo InfluxDB (`SnapshotRepository`): snapshots de participante (CRUD), oro total, curva de oro y objetivos de equipo.

### Cómo se elige el motor

En cada request, en este orden:

1. Parámetro `?motor=postgres|influx`
2. Header `X-Motor: postgres|influx`
3. Variable `MOTOR_POR_DEFECTO`

Las respuestas de los listados incluyen el header `X-Motor` con el motor que se usó.

## Contrato de la API

Base: `http://localhost:8080`. Fechas en ISO-8601 sin zona (`2026-01-23T08:21:47`, UTC). Lados: `100` = azul, `200` = rojo.

### `GET /motor`

```json
{ "porDefecto": "postgres", "disponibles": ["postgres", "influx"] }
```

Los nombres de los campos siguen los tipos del frontend (`frontend/src/types.ts`).

### `GET /partidas` — polimórfico

```json
[
  {
    "id": "EUW1_7707901123",
    "fecha": "2026-01-30T00:20:43",
    "duracionSegundos": 1824,
    "equipoGanador": "AZUL",
    "parche": "16.2",
    "rangoPromedio": "DIAMOND",
    "oroTotal": 129035
  }
]
```

- `id` y `fecha` salen del motor elegido.
- `duracionSegundos`, `equipoGanador`, `parche` y `rangoPromedio` salen de PostgreSQL.
- `oroTotal` sale de InfluxDB: la suma del oro total de los 10 jugadores en su último snapshot.

Ordenado de la partida más reciente a la más antigua.

### `GET /partidas/{id}` — polimórfico

```json
{
  "id": "EUW1_7697601501",
  "fecha": "2026-01-23T08:21:47",
  "duracionSegundos": 1528,
  "duracion": "25:28",
  "equipoGanador": "ROJO",
  "ladoGanador": 200,
  "oroTotal": 106027,
  "versionJuego": "16.2.740.1491",
  "parche": "16.2",
  "rangoPromedio": "MASTER",
  "curvaOro": [
    { "minuto": 5, "oroAzul": 8229, "oroRojo": 10080, "diferencia": -1851 }
  ],
  "jugadores": [
    {
      "id": 5021,
      "nombre": "Top",
      "lado": 100,
      "equipo": "AZUL",
      "rol": "TOP",
      "campeon": "Kennen",
      "kills": 2,
      "deaths": 8,
      "assists": 3,
      "oroTotal": 8756,
      "cs": 170,
      "nivel": 14
    }
  ]
}
```

- Los jugadores (ordenados por lado y por rol: TOP, JUNGLE, MIDDLE, BOTTOM, UTILITY) salen del motor elegido.
- El dataset no tiene nombres de invocador: `nombre` es el nombre del rol (tabla `roles`).
- `kills`, `deaths`, `assists`, `oroTotal`, `cs` y `nivel` son del último snapshot del jugador en InfluxDB (0 si no tiene snapshots).
- `curvaOro` es la misma lista que devuelve `GET /partidas/{id}/curva-oro`.

### `DELETE /partidas/{id}`

Elimina la partida y sus participantes en PostgreSQL, además de sus snapshots de
participante y equipo en InfluxDB. Responde `204`; si la partida no existe,
responde `404`.

### `GET /partidas/{id}/jugadores/{jugadorId}/mediciones`

Los snapshots de un jugador con el formato de "medición" del frontend, ordenados por minuto (InfluxDB, solo lectura). `id` es el minuto y `oro` es el oro total.

```json
[
  { "id": 5, "minuto": 5, "oro": 1605, "oroActual": 405, "cs": 34, "csJungla": 0, "xp": 2055, "nivel": 5, "kills": 0, "deaths": 1, "assists": 0 }
]
```

El dataset no tiene daño por minuto. Para dar de alta, modificar o borrar, se usan las rutas de snapshots (abajo).

### `GET /partidas/{id}/curva-oro`

Oro total de cada equipo por minuto, calculado en InfluxDB. `diferencia` positiva = ventaja azul.

```json
[ { "minuto": 5, "oroAzul": 8229, "oroRojo": 10080, "diferencia": -1851 } ]
```

### `GET /partidas/{id}/objetivos`

Objetivos acumulados de cada equipo por minuto (InfluxDB).

```json
[
  {
    "minuto": 5, "lado": 100, "torres": 0, "inhibidores": 0,
    "dragonesFuego": 0, "dragonesAgua": 0, "dragonesTierra": 0, "dragonesAire": 0,
    "dragonesQuimtec": 0, "dragonesHextech": 0, "dragonesElder": 0,
    "barones": 0, "larvasVacio": 0, "heraldos": 0
  }
]
```

### Snapshots de un participante (CRUD en InfluxDB)

Ruta base: `/partidas/{partidaId}/participantes/{participanteId}/snapshots`

| Método | Ruta | Respuesta |
|---|---|---|
| `GET` | `/` | `200` lista de snapshots ordenada por minuto |
| `GET` | `/{minuto}` | `200` snapshot · `404` si no existe |
| `POST` | `/` | `201` snapshot creado · `409` si ya hay uno en ese minuto |
| `PUT` | `/{minuto}` | `200` snapshot modificado · `404` si no existe |
| `DELETE` | `/{minuto}` | `204` · `404` si no existe |

Cuerpo de `POST` (en `PUT` el minuto sale de la URL y se puede omitir):

```json
{ "minuto": 7, "oroActual": 300, "oroTotal": 1500, "cs": 30, "csJungla": 0, "xp": 2000, "nivel": 5, "kills": 1, "deaths": 0, "assists": 2 }
```

Validaciones (`400` con la lista completa de errores):

- `minuto` entre 1 y la duración de la partida (redondeada hacia arriba)
- ningún valor negativo
- `oroActual` ≤ `oroTotal`
- `nivel` entre 1 y 20

Si la partida no existe o el participante no pertenece a esa partida: `404`.

### Errores

Siempre con el mismo formato:

```json
{ "mensaje": "Datos inválidos", "errores": ["oroActual no puede superar a oroTotal", "nivel debe estar entre 1 y 20"] }
```

| Código | Cuándo |
|---|---|
| `400` | Datos inválidos, JSON mal formado, motor desconocido, parámetro con tipo incorrecto |
| `404` | Partida, participante o snapshot inexistente |
| `409` | Alta de un snapshot en un minuto que ya existe |

## Notas de implementación en InfluxDB

- El tiempo de cada punto es `fecha_inicio + minuto`. Por eso los snapshots necesitan la fecha de inicio, que vive en PostgreSQL.
- Alta y modificación escriben el mismo punto: en InfluxDB, escribir un punto con la misma serie y el mismo tiempo lo sobrescribe. El servicio decide si corresponde `409` o `404` antes de escribir.
- Los tags del punto escrito (`match_id`, `participant_id`, `side`, `role`, `champion`) se toman del participante en PostgreSQL, para que coincidan con la serie existente.
- La baja usa la API de delete con un predicado sobre `participant_id` en un rango de un segundo.
