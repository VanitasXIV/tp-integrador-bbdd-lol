#!/usr/bin/env python3
"""Genera database/datos_iniciales.sql a partir del recorte CSV del repositorio.

Uso desde la raíz del repositorio:
    python database/generar_datos_iniciales.py

Solo carga datos estructurales en PostgreSQL. No procesa intervals.csv:
los snapshots de participantes y equipos se cargan en InfluxDB (issue #6).
No requiere dependencias externas (Python 3.9+).
"""

import csv
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATASET = ROOT / "dataset_crudo"
SALIDA = ROOT / "datos_iniciales.sql"

ROLES = [
    ("TOP", "Top", 1),
    ("JUNGLE", "Jungla", 2),
    ("MIDDLE", "Medio", 3),
    ("BOTTOM", "Tirador", 4),
    ("UTILITY", "Soporte", 5),
]


def leer_csv(nombre: str) -> list[dict[str, str]]:
    ruta = DATASET / nombre
    if not ruta.is_file():
        sys.exit(f"No se encuentra el archivo requerido: {ruta}")
    with ruta.open("r", encoding="utf-8-sig", newline="") as archivo:
        return list(csv.DictReader(archivo))


def sql_texto(valor: str) -> str:
    """Escapa un literal de texto para PostgreSQL."""
    return "'" + valor.replace("'", "''") + "'"


def valores_sql(filas: list[tuple[str, ...]]) -> str:
    return ",\n".join("    (" + ", ".join(fila) + ")" for fila in filas)


def generar() -> str:
    campeones_csv = leer_csv("ChampionTbl.csv")
    partidas_csv = leer_csv("matches.csv")
    participantes_csv = leer_csv("processed_summoner_data.csv")

    # El ID 0 representa "No Champion" y no es un campeón seleccionable.
    campeones = [f for f in campeones_csv if int(f["ChampionId"]) != 0]
    campeones_por_nombre = {
        f["ChampionName"].casefold(): int(f["ChampionId"]) for f in campeones
    }

    # Validaciones explícitas: si cambia el recorte, no generar un SQL incompleto.
    if len(campeones) != 172:
        sys.exit(f"Se esperaban 172 campeones (sin ID 0), se encontraron {len(campeones)}.")
    if len(partidas_csv) != 100:
        sys.exit(f"Se esperaban 100 partidas, se encontraron {len(partidas_csv)}.")
    if len(participantes_csv) != 1000:
        sys.exit(
            "Se esperaban 1.000 participantes, "
            f"se encontraron {len(participantes_csv)}."
        )

    roles_en_dataset = {f["role"] for f in participantes_csv}
    roles_definidos = {codigo for codigo, _, _ in ROLES}
    if roles_en_dataset != roles_definidos:
        sys.exit(
            "Los roles del CSV no coinciden con el catálogo esperado. "
            f"CSV={sorted(roles_en_dataset)}; catálogo={sorted(roles_definidos)}"
        )

    ids_partidas = {f["match_id"] for f in partidas_csv}
    ids_participantes = [f["id"] for f in participantes_csv]
    if len(ids_participantes) != len(set(ids_participantes)):
        sys.exit("Hay IDs de participante duplicados en processed_summoner_data.csv.")

    participantes_sin_partida = sorted(
        {f["match_id"] for f in participantes_csv} - ids_partidas
    )
    if participantes_sin_partida:
        sys.exit(f"Hay participantes asociados a partidas inexistentes: {participantes_sin_partida[:5]}")

    participantes_sin_campeon = sorted(
        {f["champion"] for f in participantes_csv
         if f["champion"].casefold() not in campeones_por_nombre}
    )
    if participantes_sin_campeon:
        sys.exit(f"No se encontraron estos campeones en ChampionTbl.csv: {participantes_sin_campeon}")

    # Orden estable para que dos ejecuciones sobre los mismos CSV produzcan
    # exactamente el mismo archivo SQL.
    campeones.sort(key=lambda f: int(f["ChampionId"]))
    partidas_csv.sort(key=lambda f: f["match_id"])
    participantes_csv.sort(key=lambda f: int(f["id"]))

    filas_campeones = [
        (str(int(f["ChampionId"])), sql_texto(f["ChampionName"]))
        for f in campeones
    ]
    filas_roles = [
        (sql_texto(codigo), sql_texto(nombre), str(orden))
        for codigo, nombre, orden in ROLES
    ]
    filas_partidas = []
    for f in partidas_csv:
        filas_partidas.append((
            sql_texto(f["match_id"]),
            f"TIMESTAMP {sql_texto(f['game_date'])}",
            str(int(f["game_duration"])),
            sql_texto(f["game_version"]),
            str(int(f["winning_team"])),
            sql_texto(f["average_rank"].upper()),
        ))

    filas_participantes = []
    for f in participantes_csv:
        campeon_id = campeones_por_nombre[f["champion"].casefold()]
        filas_participantes.append((
            str(int(f["id"])),
            sql_texto(f["match_id"]),
            str(int(f["team_id"])),
            sql_texto(f["role"].upper()),
            str(campeon_id),
        ))

    return f"""-- =====================================================================
-- Datos iniciales del modelo relacional - TP Integrador LoL (UNSAM)
-- ARCHIVO GENERADO por database/generar_datos_iniciales.py
-- No editar manualmente: volver a ejecutar el generador si cambian los CSV.
--
-- Ejecutar DESPUÉS de database/modelo_relacional.sql.
-- Los snapshots de intervals.csv NO se cargan aquí: corresponden a InfluxDB.
-- =====================================================================

BEGIN;

-- Catálogo de campeones: 172 registros; se excluye ID 0 (No Champion).
INSERT INTO campeones (id, nombre) VALUES
{valores_sql(filas_campeones)};

-- Catálogo de roles: 5 registros.
INSERT INTO roles (codigo, nombre, orden) VALUES
{valores_sql(filas_roles)};

-- Partidas: 100 registros.
INSERT INTO partidas
    (id, fecha_inicio, duracion_segundos, version_juego, lado_ganador, rango_promedio)
VALUES
{valores_sql(filas_partidas)};

-- Participantes: 1.000 registros. El campeón se resuelve por nombre
-- ignorando mayúsculas/minúsculas (ej. FiddleSticks = Fiddlesticks).
INSERT INTO participantes (id, partida_id, lado, rol, campeon_id) VALUES
{valores_sql(filas_participantes)};

COMMIT;

-- Verificación rápida de cantidades esperadas:
-- SELECT (SELECT COUNT(*) FROM campeones) AS campeones,
--        (SELECT COUNT(*) FROM roles) AS roles,
--        (SELECT COUNT(*) FROM partidas) AS partidas,
--        (SELECT COUNT(*) FROM participantes) AS participantes;
"""


def main() -> None:
    contenido = generar()
    SALIDA.write_text(contenido, encoding="utf-8", newline="\n")
    print(f"Generado: {SALIDA.relative_to(ROOT.parent)}")
    print("Registros: 172 campeones, 5 roles, 100 partidas y 1.000 participantes.")
    print("No se cargaron snapshots: pertenecen a InfluxDB (issue #6).")


if __name__ == "__main__":
    main()
