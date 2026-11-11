# TP Integrador Bases de Datos: Telemetría de partidas de League of Legends

UNSAM, Tecnicatura en Programación Informática.

Aplicación que analiza partidas de League of Legends combinando un motor
relacional (PostgreSQL) para partidas, participantes, campeones y roles, con un
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

## Cómo probar la aplicación

Se necesitan tres cosas corriendo a la vez, cada una en su terminal: las bases
(Docker), el backend (puerto 8080) y el frontend (puerto 5173).

### Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| Docker Desktop | Reciente (en Windows, con WSL 2) | PostgreSQL e InfluxDB |
| JDK | 21 | Backend |
| Node.js | 18 o más nuevo | Frontend |

Para verificar: `docker --version`, `java -version` (tiene que decir 21) y `node -v`.

### 1. Bases de datos

Con Docker Desktop abierto, desde la raíz del proyecto:

```bash
docker compose up -d
docker compose ps                      # postgres e influxdb en "healthy"
docker logs lol-influxdb-loader        # termina con "Snapshots cargados correctamente."
```

La primera vez descarga las imágenes y carga los datos (ver la sección anterior).

### 2. Backend

```bash
cd backend
./gradlew bootRun                      # en Windows sin Git Bash: gradlew.bat bootRun
```

Está listo cuando aparece `Started LolBackendApplication`. La terminal queda
ocupada mientras el servidor corre (Gradle muestra `EXECUTING`): es normal.
Para comprobarlo, abrir <http://localhost:8080/partidas> en el navegador.

Si el puerto 8080 está ocupado por otro programa:

```bash
SERVER_PORT=8081 ./gradlew bootRun
```

Los endpoints y el formato de las respuestas están en [`backend/README.md`](backend/README.md).

### 3. Frontend

```bash
cd frontend
cp .env.example .env                   # VITE_API_URL apunta al backend
npm install                            # solo la primera vez
npm run dev
```

Abrir <http://localhost:5173>. Si el backend corre en otro puerto, cambiar
`VITE_API_URL` en `frontend/.env` (por ejemplo, `http://localhost:8081`) y
volver a correr `npm run dev`.

### 4. Qué probar

1. **Partidas:** el listado muestra las 100 partidas con fecha, duración, ganador y oro total.
2. **Partida:** "Ver detalle" despliega la curva de oro y los jugadores de cada equipo con su K/D/A.
3. **Jugador:** "Ver métricas" abre el oro, la experiencia y el CS minuto a minuto.

Por defecto, los listados de partidas y de jugadores se leen de PostgreSQL.
Para comprobar el backend polimórfico, se puede arrancar el backend leyéndolos
de InfluxDB; la aplicación tiene que mostrar exactamente los mismos datos:

```bash
MOTOR_POR_DEFECTO=influx ./gradlew bootRun
```

### 5. Tests del backend

```bash
cd backend
./gradlew test                         # unitarios, no necesitan bases
./gradlew testIntegracion              # contra PostgreSQL e InfluxDB (docker compose up -d)
```

### Apagar todo

`Ctrl + C` en las terminales del backend y del frontend, y después:

```bash
docker compose down                    # conserva los datos; con -v los borra
```

### Problemas comunes en Windows

| Síntoma | Solución |
|---|---|
| Docker Desktop muestra "There was a problem with WSL" | Instalar WSL en PowerShell como administrador: `wsl --install --no-distribution` y reiniciar |
| `docker: command not found` en Git Bash | Cerrar y volver a abrir Git Bash después de instalar Docker Desktop |
| `failed to connect to the docker API` | Docker Desktop no está abierto o todavía no dice "Engine running" |
| `java -version` no muestra nada o no es 21 | Instalar el JDK 21 (`winget install -e --id EclipseAdoptium.Temurin.21.JDK`) y ponerlo primero en el `PATH` con `JAVA_HOME` |
| `Unable to delete directory ... build` | Cerrar VS Code, correr `./gradlew --stop` y borrar `backend/build` |
| `Port 8080 was already in use` | Usar `SERVER_PORT=8081` (ver el paso 2) |

## Dataset

[LoL Match Intervals: 2 Million In-Game Snapshots](https://www.kaggle.com/datasets/nathansmallcalder/league-of-legends-match-interval-snapshots-2026)
(Kaggle). El repo incluye un recorte de 100 partidas.

## Cómo trabajamos

- Cada tarea es un issue del [Project](https://github.com/users/VanitasXIV/projects/4).
- Una rama por issue (`feature/...`, `docs/...`, `fix/...`), con PR a `beta` que diga `Closes #N`.
- `main` recibe `beta` cuando hay una versión estable.
