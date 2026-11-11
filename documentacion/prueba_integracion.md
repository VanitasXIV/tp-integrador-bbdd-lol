# Prueba de integración con ambos motores

Prueba de punta a punta del recorrido frontend → backend → (PostgreSQL | InfluxDB), hecha el 11/11/2026 sobre la rama `beta` (después del merge de los issues #11 a #16).

## Entorno

| Componente | Cómo se levantó |
|---|---|
| Bases de datos | `docker compose up -d` en la raíz (PostgreSQL 16 en el puerto 5433, InfluxDB 2.7 en el 8086, con los datos iniciales cargados) |
| Backend | `SERVER_PORT=8081 ./gradlew bootRun` (el puerto 8080 estaba ocupado en la PC de prueba) |
| Frontend | `npm run dev` con `VITE_API_URL=http://localhost:8081` |
| Navegador | Chrome, con la caché del sitio borrada antes de cada recorrido |

La prueba se hizo dos veces, cambiando solo el motor de los listados:

1. `MOTOR_POR_DEFECTO=postgres` (valor por defecto)
2. `MOTOR_POR_DEFECTO=influx`

## Resultados

| Verificación | Resultado |
|---|---|
| Recorrido de las 3 pantallas (partidas, partida, jugador) | ✅ Sin errores en la consola del navegador, con los dos motores |
| `GET /motor` informa el motor elegido | ✅ `postgres` en la primera vuelta, `influx` en la segunda; el header `X-Motor` de las respuestas coincide |
| El listado de partidas es igual con los dos motores | ✅ 100 partidas, respuesta idéntica con `?motor=postgres` y `?motor=influx` |
| El listado de participantes es igual con los dos motores | ✅ 1.000 participantes; los 10 jugadores coinciden en las 100 partidas |
| Las pantallas muestran lo mismo con cada motor | ✅ El texto del listado y del detalle de `EUW1_7707901123` es idéntico en las dos vueltas |
| Alta de un snapshot (InfluxDB) | ✅ Minuto 7 de Ambessa (`EUW1_7707901123`): aparece en la tabla y en los gráficos |
| Consulta individual | ✅ El panel muestra los 9 valores del snapshot recién creado |
| Modificación | ✅ Kills 0 → 2 y assists 0 → 1; InfluxDB devuelve los valores nuevos y la cantidad de snapshots no cambia (sobrescribe, no duplica) |
| Baja con confirmación | ✅ Con "Cancelar" no se borra; con "Borrar", `GET` del minuto 7 devuelve 404 |
| Validaciones | ✅ Oro en mano mayor al oro total (frontend), minuto repetido (409), minuto fuera de la duración (400), participante de otra partida (404) |
| La curva de oro coincide con `team_gold_diff` del dataset | ✅ 546 puntos (100 partidas × minutos con snapshot), todos iguales |

Al terminar se borró el snapshot de prueba, así que los datos quedaron como los carga Docker Compose.

## Tests automatizados del backend

```bash
cd backend
./gradlew test               # unitarios (servicios, motor, validaciones)
./gradlew testIntegracion    # contra PostgreSQL e InfluxDB: listados iguales, listado y detalle iguales, CRUD
```

## Capturas

Las capturas están en [`capturas/`](capturas/) para la sección "Ejemplos de funcionamiento" del informe.

| Archivo | Qué muestra |
|---|---|
| `01_listado_partidas_postgres.jpg` | Listado de partidas (PostgreSQL) |
| `02_detalle_partida_postgres.jpg` | Detalle: curva de oro, objetivos y equipos (PostgreSQL) |
| `03_metricas_jugador.jpg` | Métricas de un jugador por minuto (InfluxDB) |
| `04_alta_formulario.jpg` | Formulario de alta de un snapshot |
| `05_alta_y_consulta.jpg` | Snapshot creado y su consulta individual |
| `06_validacion_oro_en_mano.jpg` | Validación del frontend: oro en mano mayor al oro total |
| `07_minuto_repetido_409.png` | Error 409 del backend: el minuto ya tiene snapshot |
| `08_minuto_fuera_de_rango_400.png` | Error 400 del backend: minuto fuera de la duración de la partida |
| `09_edicion.jpg` | Edición de un snapshot (minuto bloqueado) |
| `10_confirmar_borrado.jpg` | Confirmación antes de borrar |
| `11_listado_partidas_influx.jpg` | Listado de partidas con `MOTOR_POR_DEFECTO=influx` |
| `12_detalle_partida_influx.jpg` | Detalle de la partida con `MOTOR_POR_DEFECTO=influx` |
| `13_motor_influx.png` | `GET /motor` con InfluxDB como motor por defecto |
