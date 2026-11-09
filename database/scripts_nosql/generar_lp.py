"""Genera el archivo Line Protocol que se carga en InfluxDB.

Combina los datos temporales con la informacion de participantes y partidas,
y genera los measurements participant_snapshot y team_snapshot.
"""

from pathlib import Path

import pandas as pd


# El script esta en database/scripts_nosql/, por eso parents[2] apunta a la
# raiz del repositorio sin depender del directorio desde donde se lo ejecute.
ROOT = Path(__file__).resolve().parents[2]
DATASET_DIR = ROOT / "database" / "dataset_crudo"
OUTPUT_FILE = ROOT / "database" / "scripts_nosql" / "snapshots.lp"

# Archivos de entrada del dataset y archivo de salida de InfluxDB.
INTERVALS_FILE = DATASET_DIR / "intervals.csv"
PARTICIPANTS_FILE = DATASET_DIR / "processed_summoner_data.csv"
MATCHES_FILE = DATASET_DIR / "matches.csv"
CHAMPIONS_FILE = DATASET_DIR / "ChampionTbl.csv"


# Fields que pertenecen a cada snapshot de participante.
PARTICIPANT_FIELDS = [
    "current_gold",
    "total_gold",
    "cs_count",
    "jungle_cs",
    "xp",
    "level",
    "kills",
    "deaths",
    "assists",
]

# Fields que pertenecen a cada snapshot de equipo.
TEAM_FIELDS = [
    "towers",
    "inhibitors",
    "dragons_fire",
    "dragons_water",
    "dragons_earth",
    "dragons_air",
    "dragons_chemtech",
    "dragons_hextech",
    "dragons_elder",
    "barons",
    "void_grubs",
    "heralds",
]


def escape_tag(value: object) -> str:
    """Escapa valores según el formato Line Protocol."""
    # Line Protocol usa espacios, comas, signos igual y backslash como
    # caracteres especiales dentro de los tags; por eso deben escaparse.
    return (
        str(value)
        .replace("\\", "\\\\")
        .replace(" ", "\\ ")
        .replace(",", "\\,")
        .replace("=", "\\=")
    )


def integer_value(row: pd.Series, column: str) -> int:
    # Los fields se escriben como enteros agregando la letra i al valor.
    value = pd.to_numeric(row[column], errors="raise")

    if pd.isna(value):
        raise ValueError(f"Valor nulo en la columna {column}")

    return int(value)


def load_data() -> pd.DataFrame:
    # intervals contiene las metricas temporales. Los otros archivos aportan
    # los datos descriptivos necesarios para construir los tags.
    intervals = pd.read_csv(INTERVALS_FILE)
    intervals = intervals.rename(columns={"cs": "cs_count"})
    participants = pd.read_csv(PARTICIPANTS_FILE)
    matches = pd.read_csv(MATCHES_FILE)
    champions = pd.read_csv(CHAMPIONS_FILE)

    # Se arma un catalogo case-insensitive para resolver diferencias como
    # FiddleSticks/Fiddlesticks usando el nombre canonico.
    canonical_champions = (
        champions.assign(
            champion_key=champions["ChampionName"].astype(str).str.casefold()
        )
        .set_index("champion_key")["ChampionName"]
        .to_dict()
    )

    participants["champion_key"] = (
        participants["champion"].astype(str).str.casefold()
    )
    participants["champion"] = participants["champion_key"].map(
        canonical_champions
    )

    if participants["champion"].isna().any():
        unknown = participants.loc[
            participants["champion"].isna(), "champion_key"
        ].unique()
        raise ValueError(f"Campeones no encontrados en el catálogo: {unknown}")

    # Solo necesitamos estas columnas estructurales para enriquecer los
    # snapshots temporales.
    participants = participants[
        ["id", "match_id", "team_id", "role", "champion"]
    ]

    # Vinculamos cada snapshot con su participante mediante player_id = id.
    result = intervals.merge(
        participants,
        left_on=["player_id", "match_id"],
        right_on=["id", "match_id"],
        how="inner",
        validate="many_to_one",
    )

    # Incorporamos game_date para calcular el timestamp real de cada punto.
    result = result.merge(
        matches[["match_id", "game_date"]],
        on="match_id",
        how="inner",
        validate="many_to_one",
    )

    if len(result) != len(intervals):
        raise ValueError(
            f"El JOIN perdió filas: intervals={len(intervals)}, "
            f"resultado={len(result)}"
        )

    # Riot identifica los lados como 100 (azul) y 200 (rojo).
    result["side"] = result["team_id"].astype(int).astype(str)
    result["role"] = result["role"].astype(str).str.upper()

    # Se interpreta game_date como UTC y se suma el minuto de juego. El
    # resultado queda en segundos Unix para cargarlo con precision s.
    game_dates = pd.to_datetime(result["game_date"], utc=True)
    timestamps = game_dates + pd.to_timedelta(result["minute"], unit="m")
    result["timestamp"] = timestamps.astype("int64") // 1_000_000_000

    return result


def validate_team_metrics(data: pd.DataFrame) -> None:
    # Las metricas de equipo se repiten en las cinco filas de sus jugadores.
    # Antes de deduplicarlas verificamos que esas copias sean consistentes.
    team_keys = ["match_id", "team_id", "minute"]

    source_team_fields = [
        "team_inhibitors",
        "team_towers",
        "team_dragons_fire",
        "team_dragons_water",
        "team_dragons_earth",
        "team_dragons_air",
        "team_dragons_chemtech",
        "team_dragons_hextech",
        "team_dragons",
        "team_barons",
        "team_void_grubs",
        "team_heralds",
    ]

    grouped = data.groupby(team_keys, dropna=False)[source_team_fields].nunique()

    inconsistent = grouped.gt(1).any(axis=1)

    if inconsistent.any():
        examples = inconsistent[inconsistent].head()
        raise ValueError(
            "Hay métricas de equipo diferentes dentro del mismo "
            f"(match_id, team_id, minute): {examples.index.tolist()}"
        )


def participant_line(row: pd.Series) -> str:
    # Los tags identifican la serie temporal de un participante.
    tags = (
        f"match_id={escape_tag(row['match_id'])},"
        f"participant_id={escape_tag(row['player_id'])},"
        f"side={escape_tag(row['side'])},"
        f"role={escape_tag(row['role'])},"
        f"champion={escape_tag(row['champion'])}"
    )

    # Los fields representan las metricas que cambian durante la partida.
    fields = ",".join(
        f"{field}={integer_value(row, field)}i"
        for field in PARTICIPANT_FIELDS
    )

    return f"participant_snapshot,{tags} {fields} {int(row['timestamp'])}"


def team_line(row: pd.Series) -> str:
    # team_dragons incluye los seis tipos y Elder. Elder se obtiene por
    # diferencia para no duplicar informacion.
    dragons = {
        "dragons_fire": integer_value(row, "team_dragons_fire"),
        "dragons_water": integer_value(row, "team_dragons_water"),
        "dragons_earth": integer_value(row, "team_dragons_earth"),
        "dragons_air": integer_value(row, "team_dragons_air"),
        "dragons_chemtech": integer_value(row, "team_dragons_chemtech"),
        "dragons_hextech": integer_value(row, "team_dragons_hextech"),
    }

    total_dragons = integer_value(row, "team_dragons")
    dragons_elder = total_dragons - sum(dragons.values())

    if dragons_elder < 0:
        raise ValueError(
            f"dragons_elder negativo en {row['match_id']} "
            f"equipo={row['side']} minuto={row['minute']}"
        )

    # Estos fields representan el estado del equipo en ese minuto.
    fields = {
        "towers": integer_value(row, "team_towers"),
        "inhibitors": integer_value(row, "team_inhibitors"),
        **dragons,
        "dragons_elder": dragons_elder,
        "barons": integer_value(row, "team_barons"),
        "void_grubs": integer_value(row, "team_void_grubs"),
        "heralds": integer_value(row, "team_heralds"),
    }

    field_text = ",".join(f"{name}={value}i" for name, value in fields.items())

    tags = (
        f"match_id={escape_tag(row['match_id'])},"
        f"side={escape_tag(row['side'])}"
    )

    return f"team_snapshot,{tags} {field_text} {int(row['timestamp'])}"


def generate_line_protocol() -> None:
    # Cargamos y validamos los datos antes de generar cualquier linea.
    data = load_data()
    validate_team_metrics(data)

    # Un orden estable facilita revisar y comparar el archivo generado.
    data = data.sort_values(
        ["match_id", "minute", "player_id"]
    ).reset_index(drop=True)

    # Se genera un punto por participante y minuto.
    participant_lines = [
        participant_line(row)
        for _, row in data.iterrows()
    ]

    # Las metricas de equipo aparecen repetidas por participante. Conservamos
    # una sola fila por partida, equipo y minuto.
    team_data = (
        data.sort_values(["match_id", "minute", "team_id"])
        .drop_duplicates(["match_id", "team_id", "minute"])
        .reset_index(drop=True)
    )

    # Se genera un punto por equipo y minuto.
    team_lines = [
        team_line(row)
        for _, row in team_data.iterrows()
    ]

    # Ambos measurements se cargan desde el mismo archivo Line Protocol.
    lines = participant_lines + team_lines

    # UTF-8 y saltos de linea Unix evitan problemas de parsing en InfluxDB.
    OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_FILE.write_text(
    "\n".join(lines) + "\n",
    encoding="utf-8",
    newline="\n",
    )

    print(f"participant_snapshot: {len(participant_lines)}")
    print(f"team_snapshot: {len(team_lines)}")
    print(f"Archivo generado: {OUTPUT_FILE}")


if __name__ == "__main__":
    generate_line_protocol()
