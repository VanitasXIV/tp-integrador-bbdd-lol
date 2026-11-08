#!/bin/bash
set -e

echo "Cargando snapshots de League of Legends en InfluxDB automáticamente..."
influx write \
  --org "${DOCKER_INFLUXDB_INIT_ORG}" \
  --bucket "${DOCKER_INFLUXDB_INIT_BUCKET}" \
  --token "${DOCKER_INFLUXDB_INIT_ADMIN_TOKEN}" \
  --file /docker-entrypoint-initdb.d/snapshots.lp

echo "¡Datos cargados con éxito en el inicio de InfluxDB!"