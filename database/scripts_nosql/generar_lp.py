import os
import pandas as pd

CSV_PARTICIPANTS = os.path.join("database", "dataset_crudo", "intervals.csv")
OUTPUT_LP = os.path.join("database", "scripts_nosql", "snapshots.lp")
BASE_EPOCH = 1735689600

def generar_line_protocol():
    os.makedirs(os.path.dirname(OUTPUT_LP), exist_ok=True)
    print("Leyendo dataset crudo...")
    
    try:
        df = pd.read_csv(CSV_PARTICIPANTS)
        # Reemplazar valores nulos por 0 para evitar que rompa el parsing
        df = df.fillna(0)
    except Exception as e:
        print(f"Error al leer el CSV: {e}")
        return

    lines = []
    
    print("Procesando puntos correctamente...")
    for _, row in df.iterrows():
        match_id = str(row["match_id"])
        participant_id = str(row["player_id"])
        side = str(row.get("side", "unknown"))
        role = str(row.get("role", "unknown"))
        champion = str(row.get("champion", "unknown"))
        
        seconds = int(row.get("seconds", int(row["minute"]) * 60))
        timestamp = (BASE_EPOCH + seconds) * 1_000_000_000

        # Conversiones limpias a entero
        c_gold = int(float(row['current_gold']))
        t_gold = int(float(row['total_gold']))
        cs_val = int(float(row['cs']))
        j_cs = int(float(row['jungle_cs']))
        xp_val = int(float(row['xp']))
        lvl = int(float(row['level']))
        kills = int(float(row['kills']))
        deaths = int(float(row['deaths']))
        assists = int(float(row['assists']))

        # 1. participant_snapshot (Línea plana y sin variables divididas)
        line_part = (
            f"participant_snapshot,match_id={match_id},participant_id={participant_id},side={side},role={role},champion={champion} "
            f"current_gold={c_gold}i,total_gold={t_gold}i,cs_count={cs_val}i,jungle_cs={j_cs}i,xp={xp_val}i,level={lvl}i,kills={kills}i,deaths={deaths}i,assists={assists}i "
            f"{timestamp}"
        )
        lines.append(line_part)

        # 2. team_snapshot
        t_towers = int(float(row.get('team_towers', 0)))
        t_inhibs = int(float(row.get('team_inhibitors', 0)))
        d_fire = int(float(row.get('team_dragons_fire', 0)))
        d_water = int(float(row.get('team_dragons_water', 0)))
        d_earth = int(float(row.get('team_dragons_earth', 0)))
        d_air = int(float(row.get('team_dragons_air', 0)))
        d_chem = int(float(row.get('team_dragons_chemtech', 0)))
        d_hex = int(float(row.get('team_dragons_hextech', 0)))
        total_dragons = d_fire + d_water + d_earth + d_air + d_chem + d_hex
        barons = int(float(row.get('team_barons', 0)))
        void_grubs = int(float(row.get('team_void_grubs', 0)))

        line_team = (
            f"team_snapshot,match_id={match_id},side={side} "
            f"towers={t_towers}i,inhibitors={t_inhibs}i,dragons_fire={d_fire}i,dragons_water={d_water}i,dragons_earth={d_earth}i,dragons_air={d_air}i,dragons_chemtech={d_chem}i,dragons_hextech={d_hex}i,dragons_elder={total_dragons}i,barons={barons}i,void_grubs={void_grubs}i "
            f"{timestamp}"
        )
        lines.append(line_team)

    # Usar newline="\n" evita que Windows agregue el maldito '\r' que rompe el timestamp en InfluxDB
    with open(OUTPUT_LP, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))
        
    print(f"¡Archivo .lp regenerado sin '\r' en: {OUTPUT_LP}!")

if __name__ == "__main__":
    generar_line_protocol()