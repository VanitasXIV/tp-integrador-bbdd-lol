import os
import pandas as pd

def probar_una_sola_partida(ruta_csv):
    print(f"Leyendo el archivo CSV desde: {ruta_csv}...\n")
    
    try:
        df = pd.read_csv(ruta_csv)
    except Exception as e:
        print(f"Error al leer el archivo CSV: {e}")
        return

    if df.empty or "match_id" not in df.columns:
        print("El archivo CSV está vacío o no contiene la columna 'match_id'.")
        return

    # Si el CSV todavía no trae la columna en segundos, la calculamos desde los minutos
    if "seconds" not in df.columns:
        df["seconds"] = df["minute"] * 60

    # 1. Tomamos estrictamente EL PRIMER match_id que aparece en el CSV
    primer_match_id = df["match_id"].iloc[0]
    
    # 2. Filtramos el DataFrame para quedarnos SOLAMENTE con los registros de ese match_id
    df_una_partida = df[df["match_id"] == primer_match_id].copy()

    # 3. Calculamos el total de dragones sumando los tipos disponibles
    df_una_partida["team_dragons"] = (
        df_una_partida.get("team_dragons_fire", 0) +
        df_una_partida.get("team_dragons_water", 0) +
        df_una_partida.get("team_dragons_earth", 0) +
        df_una_partida.get("team_dragons_air", 0) +
        df_una_partida.get("team_dragons_chemtech", 0) +
        df_una_partida.get("team_dragons_hextech", 0)
    )

    # 4. Seleccionamos las columnas de Tags y Fields exactas para InfluxDB
    columnas_influx = [
        "match_id", "player_id", "team_id",
        "minute", "seconds", "current_gold", "total_gold", "cs", "jungle_cs", 
        "xp", "level", "kills", "deaths", "assists", 
        "gold_diff", "xp_diff", "team_gold_diff", 
        "team_towers", "team_dragons", "team_barons"
    ]

    columnas_disponibles = [col for col in columnas_influx if col in df_una_partida.columns]
    df_filtrado = df_una_partida[columnas_disponibles].copy()

    # Tiempo de juego legible (mm:ss) y timestamp tal como lo va a ver InfluxDB (precisión en segundos)
    df_filtrado["tiempo_juego"] = df_filtrado["seconds"].apply(lambda s: f"{s // 60:02d}:{s % 60:02d}")
    df_filtrado["timestamp_influx"] = pd.to_datetime(df_filtrado["seconds"], unit="s")

    # Configuramos pandas para ver toda la tabla limpia en la terminal
    pd.set_option('display.max_columns', None)
    pd.set_option('display.max_rows', None)
    pd.set_option('display.width', 1000)

    print(f"--- MOSTRANDO ÚNICAMENTE LA PARTIDA: {primer_match_id} ---")
    print(df_filtrado)
    print("-" * 80)
    print(f" Total de registros en esta partida: {len(df_filtrado)}")
    print(f"  Segundos de juego en la partida: {sorted(df_filtrado['seconds'].unique().tolist())}")

if __name__ == "__main__":
    csv_path = os.path.join("data", "intervals-nuevo.csv")
    probar_una_sola_partida(csv_path)