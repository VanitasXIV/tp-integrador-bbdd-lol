# TP Integrador Bases de Datos: Telemetría de partidas de League of Legends

UNSAM, Tecnicatura en Programación Informática.

Aplicación que analiza partidas de League of Legends combinando un motor
relacional (PostgreSQL) para partidas, participantes, campeones y bans, con un
motor de series temporales (InfluxDB) para los snapshots de cada partida,
accedidos desde un backend polimórfico.

## Stack

| Capa | Tecnología |
|---|---|
| Frontend | React + TypeScript |
| Backend | Spring Boot + Kotlin |
| Bases de datos | PostgreSQL + InfluxDB (Docker) |

## Estructura

```
backend/         API Spring Boot + Kotlin
frontend/        Interfaz React + TypeScript
database/        Scripts SQL, scripts NoSQL y dataset
documentacion/   Informe, DER y análisis
```

## Bases de datos con Docker

El entorno local utiliza dos motores de persistencia:

| Motor | Host | Puerto | Datos |
|---|---|---:|---|
| PostgreSQL 16 | `localhost` | `5433` | Partidas, participantes, campeones y roles |
| InfluxDB 2.7 | `localhost` | `8086` | Snapshots temporales de participantes y equipos |

### Credenciales locales

PostgreSQL:

```text
Base: lol_telemetry
Usuario: lol_user
Clave: lol_dev_password
```

InfluxDB:

```text
Organización: unsam
Bucket: lol_snapshots
Token: tp-bbdd-token-local
Retención: infinita
```

Estos valores son únicamente para desarrollo local y se pueden configurar en
`.env`. El archivo `.env` no debe commitearse; para compartir la configuración
se debe actualizar `.env.example`.

### Levantar el entorno

Desde la raíz del proyecto:

```bash
docker compose up -d
```

En el primer arranque, PostgreSQL ejecuta automáticamente, en este orden:

1. `database/modelo_relacional.sql`
2. `database/datos_iniciales.sql`

InfluxDB crea la organización, el bucket y el token, y luego carga los
snapshots mediante `database/scripts_nosql/01_cargar_snapshots.sh` a partir de
`database/scripts_nosql/snapshots.lp`.

Los scripts de inicialización se ejecutan solamente cuando los volúmenes están
vacíos. Para eliminar los datos y volver a cargar todo desde cero:

```bash
docker compose down -v
docker compose up -d
```

Para revisar el estado de los servicios:

```bash
docker compose ps
docker compose logs postgres
docker compose logs influxdb
```

## Dataset

[LoL Match Intervals: 2 Million In-Game Snapshots](https://www.kaggle.com/datasets/nathansmallcalder/league-of-legends-match-interval-snapshots-2026)
(Kaggle). El repo incluye un recorte de 100 partidas.

## Cómo trabajamos

- Cada tarea es un issue del [Project](https://github.com/users/VanitasXIV/projects/4).
- Una rama por issue (`feature/...`, `docs/...`, `fix/...`), con PR a `main` que diga `Closes #N`.

> En construcción: las instrucciones para levantar el proyecto se agregan a medida que avanza.
