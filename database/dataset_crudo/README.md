# Dataset crudo

Recorte de 100 partidas del dataset
[LoL Match Intervals: 2 Million In-Game Snapshots](https://www.kaggle.com/datasets/nathansmallcalder/league-of-legends-match-interval-snapshots-2026)
(autor: Nathan Smallcalder, Kaggle). Los datos provienen de la API oficial de
Riot Games (endpoints `match-v5` y `timeline-v5`) y corresponden a partidas
Ranked Solo/Duo del servidor EUW.

Las filas están **copiadas sin modificar**: es el punto de partida del análisis
y la normalización (ver `documentacion/analisis_dataset.md`).

| Archivo | Filas | Contenido |
|---|---|---|
| `matches.csv` | 100 | Una fila por partida |
| `processed_summoner_data.csv` | 1.000 | Una fila por participante (10 por partida) |
| `intervals.csv` | 5.460 | Una fila por participante cada 5 minutos |
| `ChampionTbl.csv` | 173 | Catálogo completo de campeones |

El archivo `ItemTbl.csv` del dataset original no se incluye: el equipo decidió
no modelar ítems.

## Licencia

En Kaggle la licencia figura como "Other (specified in description)", pero la
descripción no la especifica. Se usa con fines exclusivamente académicos,
citando la fuente.

## Cómo regenerar el recorte

El dataset completo pesa ~300 MB (`intervals.csv` supera el límite de 100 MB
de GitHub), por eso el repo guarda solo el recorte.

1. Descargar el dataset de Kaggle y descomprimirlo en `database/dataset_crudo/kaggle/`
   (esa carpeta está en `.gitignore`).
2. Desde la raíz del repo:

   ```bash
   python database/dataset_crudo/recortar_dataset.py database/dataset_crudo/kaggle
   ```

El script usa solo la biblioteca estándar de Python y una semilla fija
(`2026`): siempre genera las mismas 100 partidas.

### Criterios de selección

- Partidas de enero de 2026, parche 16.
- Duración entre 20 y 40 minutos (excluye remakes y rendiciones tempranas).
- `match_id` del servidor EUW1.
- Con snapshots en `intervals.csv`.
- Sin participantes con `individual_position` inválida (`Invalid`, `TOPJUNGLE`).
- Sin snapshots con `current_gold` negativo.

De las 39.954 partidas del dataset cumplen 32.670; de ellas se eligen 100 al azar.
