"""
Recorte reproducible del dataset "LoL Match Intervals: 2 Million In-Game Snapshots".

Fuente: https://www.kaggle.com/datasets/nathansmallcalder/league-of-legends-match-interval-snapshots-2026

El dataset completo pesa ~300 MB (intervals.csv supera el límite de 100 MB de
GitHub), así que el repo guarda solo un recorte de 100 partidas. Este script
lo genera a partir de los archivos originales de Kaggle.

Uso:
    1. Descargar el dataset de Kaggle y descomprimirlo en una carpeta,
       por ejemplo database/dataset_crudo/kaggle/ (está en .gitignore).
    2. python recortar_dataset.py database/dataset_crudo/kaggle

No requiere dependencias: solo la biblioteca estándar de Python 3.9+.
Las filas se copian sin modificar (es el dataset CRUDO); la limpieza y la
normalización se hacen después, al cargar las bases.

Criterios de selección (ver documentacion/analisis_dataset.md):
    - partida de enero de 2026, parche 16
    - duración entre 20 y 40 minutos (excluye remakes y rendiciones tempranas)
    - match_id del servidor EUW1
    - con snapshots en intervals.csv
    - sin participantes con individual_position inválida (Invalid / TOPJUNGLE)
    - sin snapshots con current_gold negativo
De las partidas que cumplen, se eligen 100 al azar con semilla fija.
"""
import csv
import random
import sys
from pathlib import Path

SEMILLA = 2026
CANTIDAD = 100
DURACION_MIN_S = 20 * 60
DURACION_MAX_S = 40 * 60
POSICIONES_INVALIDAS = {"Invalid", "TOPJUNGLE"}

ARCHIVOS = {
    "matches": "matches.csv",
    "summoners": "processed_summoner_data.csv",
    "intervals": "intervals.csv",
    "campeones": "ChampionTbl.csv",
}


def buscar(origen: Path, nombre: str) -> Path:
    """Acepta el nombre exacto o con prefijo (ej. 'abc123-matches.csv')."""
    exacto = origen / nombre
    if exacto.exists():
        return exacto
    candidatos = sorted(origen.glob(f"*{nombre}"))
    if not candidatos:
        sys.exit(f"No encuentro {nombre} en {origen}")
    return candidatos[0]


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("Uso: python recortar_dataset.py <carpeta con los CSV de Kaggle>")
    origen = Path(sys.argv[1])
    destino = Path(__file__).resolve().parent
    rutas = {k: buscar(origen, v) for k, v in ARCHIVOS.items()}

    # 1) Recorrer intervals: qué partidas tienen snapshots y cuáles tienen oro negativo.
    con_snapshots, oro_negativo = set(), set()
    with open(rutas["intervals"], newline="", encoding="utf-8") as f:
        for fila in csv.DictReader(f):
            con_snapshots.add(fila["match_id"])
            if int(fila["current_gold"]) < 0:
                oro_negativo.add(fila["match_id"])

    # 2) Partidas con posiciones inválidas.
    posicion_invalida = set()
    with open(rutas["summoners"], newline="", encoding="utf-8") as f:
        for fila in csv.DictReader(f):
            if fila["individual_position"] in POSICIONES_INVALIDAS:
                posicion_invalida.add(fila["match_id"])

    # 3) Filtrar partidas candidatas.
    candidatas = []
    with open(rutas["matches"], newline="", encoding="utf-8") as f:
        for fila in csv.DictReader(f):
            mid = fila["match_id"]
            if (fila["game_date"].startswith("2026-01")
                    and fila["patch_version"] == "16"
                    and DURACION_MIN_S <= int(fila["game_duration"]) <= DURACION_MAX_S
                    and mid.startswith("EUW1_")
                    and mid in con_snapshots
                    and mid not in oro_negativo
                    and mid not in posicion_invalida):
                candidatas.append(mid)

    # Orden estable antes de muestrear: el resultado no depende del orden del archivo.
    candidatas.sort()
    elegidas = set(random.Random(SEMILLA).sample(candidatas, CANTIDAD))
    print(f"Partidas candidatas: {len(candidatas)} -> elegidas: {len(elegidas)}")

    # 4) Copiar las filas de las partidas elegidas, sin modificarlas.
    def copiar(clave: str, salida: str) -> int:
        n = 0
        with open(rutas[clave], newline="", encoding="utf-8") as fin, \
             open(destino / salida, "w", newline="", encoding="utf-8") as fout:
            lector = csv.reader(fin)
            escritor = csv.writer(fout, lineterminator="\n")
            encabezado = next(lector)
            escritor.writerow(encabezado)
            col = encabezado.index("match_id")
            for fila in lector:
                if fila[col] in elegidas:
                    escritor.writerow(fila)
                    n += 1
        return n

    for clave, salida in (("matches", "matches.csv"),
                          ("summoners", "processed_summoner_data.csv"),
                          ("intervals", "intervals.csv")):
        print(f"{salida}: {copiar(clave, salida)} filas")

    # El catálogo de campeones se copia completo (lo usan también los bans).
    with open(rutas["campeones"], newline="", encoding="utf-8") as fin, \
         open(destino / "ChampionTbl.csv", "w", newline="", encoding="utf-8") as fout:
        escritor = csv.writer(fout, lineterminator="\n")
        n = -1
        for fila in csv.reader(fin):
            escritor.writerow(fila)
            n += 1
    print(f"ChampionTbl.csv: {n} filas")


if __name__ == "__main__":
    main()
