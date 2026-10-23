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

## Dataset

[LoL Match Intervals: 2 Million In-Game Snapshots](https://www.kaggle.com/datasets/nathansmallcalder/league-of-legends-match-interval-snapshots-2026)
(Kaggle). El repo incluye un recorte de 100 partidas.

## Cómo trabajamos

- Cada tarea es un issue del [Project](https://github.com/users/VanitasXIV/projects/4).
- Una rama por issue (`feature/...`, `docs/...`, `fix/...`), con PR a `main` que diga `Closes #N`.

> En construcción: las instrucciones para levantar el proyecto se agregan a medida que avanza.