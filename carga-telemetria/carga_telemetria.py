import os
import pandas as pd
from influxdb_client import InfluxDBClient, Point, WritePrecision
from influxdb_client.client.write_api import SYNCHRONOUS

# --- CONFIGURACIÓN DE INFLUXDB ---
URL = "http://localhost:8086"
TOKEN = "tu-token-de-influxdb-aqui"  # Reemplaza con tu token de InfluxDB
ORG = "unsam"
BUCKET = "lol_telemetry"

# Offset en segundos sumado al tiempo de juego. 0 = el minuto 5 queda en 1970-01-01 00:05:00.
# Si querés que se vea en fechas actuales, poné un epoch fijo (ej: 1735689600 = 2025-01-01).
BASE_EPOCH = 1735689600

def cargar_telemetria_csv(ruta_csv):
    print(f"Leyendo el archivo CSV desde: {ruta_csv}...")
    try:
        df = pd.read_csv(ruta_csv)
        if "seconds" not in df.columns:
            df["seconds"] = df["minute"] * 60
    except Exception as e:
        print(f"Error al leer el archivo CSV: {e}")
        return

    client = InfluxDBClient(url=URL, token=TOKEN, org=ORG)
    write_api = client.write_api(write_options=SYNCHRONOUS)

    print("Conectado a InfluxDB. Procesando y subiendo datos...")
    
    puntos = []
    for index, row in df.iterrows():
        # Cálculo del total de dragones sumando los tipos disponibles en el CSV
        total_dragons = (
            int(row.get("team_dragons_fire", 0)) +
            int(row.get("team_dragons_water", 0)) +
            int(row.get("team_dragons_earth", 0)) +
            int(row.get("team_dragons_air", 0)) +
            int(row.get("team_dragons_chemtech", 0)) +
            int(row.get("team_dragons_hextech", 0))
        )

        # Creación del punto respetando exactamente la estructura solicitada
        point = Point("lol_match_telemetry") \
            .tag("match_id", str(row["match_id"])) \
            .tag("participant_id", str(row["player_id"])) \
            .tag("team_id", str(row.get("team_id", "unknown"))) \
            .field("current_gold", float(row["current_gold"])) \
            .field("total_gold", float(row["total_gold"])) \
            .field("cs_count", int(row["cs"])) \
            .field("jungle_cs", int(row["jungle_cs"])) \
            .field("xp", float(row["xp"])) \
            .field("level", int(row["level"])) \
            .field("kills", int(row["kills"])) \
            .field("deaths", int(row["deaths"])) \
            .field("assists", int(row["assists"])) \
            .field("gold_diff", float(row["gold_diff"])) \
            .field("xp_diff", float(row["xp_diff"])) \
            .field("team_gold_diff", float(row["team_gold_diff"])) \
            .field("team_towers", int(row.get("team_towers", 0))) \
            .field("team_dragons", int(total_dragons)) \
            .field("team_barons", int(row.get("team_barons", 0))) \
            .time(BASE_EPOCH + int(row["seconds"]), WritePrecision.S)  # segundos de juego como timestamp

        puntos.append(point)

    # Escritura por lote en InfluxDB
    try:
        write_api.write(bucket=BUCKET, org=ORG, record=puntos, write_precision=WritePrecision.S)
        print(f"¡Éxito! Se cargaron {len(puntos)} registros de telemetría en InfluxDB.")
    except Exception as e:
        print(f"Error al escribir en InfluxDB: {e}")
    finally:
        client.close()

if __name__ == "__main__":
    csv_path = os.path.join("data", "intervals-nuevo.csv")
    cargar_telemetria_csv(csv_path)