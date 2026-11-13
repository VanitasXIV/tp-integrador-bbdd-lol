#!/bin/bash
set -euo pipefail

echo "Cargando snapshots de League of Legends en InfluxDB..."

test -f /data/snapshots.lp

MARKER_FILE="/var/lib/loader/.snapshots_loaded"

if [ -f "$MARKER_FILE" ]; then
  echo "Los snapshots ya fueron cargados. No se vuelve a ejecutar la carga."
  exit 0
fi

influx write \
  --host "${INFLUX_HOST:-http://influxdb:8086}" \
  --org "${DOCKER_INFLUXDB_INIT_ORG:?Falta la organización de InfluxDB}" \
  --bucket "${DOCKER_INFLUXDB_INIT_BUCKET:?Falta el bucket de InfluxDB}" \
  --token "${DOCKER_INFLUXDB_INIT_ADMIN_TOKEN:?Falta el token de InfluxDB}" \
  --file /data/snapshots.lp \
  --precision s

touch "$MARKER_FILE"
echo "Snapshots cargados correctamente."
