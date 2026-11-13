// Consultas de ejemplo para explorar los datos de lol_snapshots.
// El rango temporal lo controla el selector de fechas de InfluxDB.

// 1. Oro total por equipo y minuto.
//
// total_gold existe en participant_snapshot.
// Sumamos el oro de los cinco jugadores de cada equipo.
gold_by_team = from(bucket: "lol_snapshots")
  |> range(
    start: v.timeRangeStart,
    stop: v.timeRangeStop
  )
  |> filter(fn: (r) =>
    r._measurement == "participant_snapshot" and
    r._field == "total_gold"
  )
  |> group(columns: ["match_id", "side", "_time"])
  |> sum(column: "_value")

gold_by_team
  |> yield(name: "gold_by_team")


// 2. Evolución temporal de un participante.
//
// Estos valores corresponden a una partida y jugador existentes
// en el dataset actual.
participant_timeline = from(bucket: "lol_snapshots")
  |> range(
    start: v.timeRangeStart,
    stop: v.timeRangeStop
  )
  |> filter(fn: (r) =>
    r._measurement == "participant_snapshot" and
    r.match_id == "EUW1_7702595107" and
    r.participant_id == "287451"
  )
  |> filter(fn: (r) =>
    r._field == "current_gold" or
    r._field == "total_gold" or
    r._field == "cs_count" or
    r._field == "xp" or
    r._field == "level"
  )
  |> sort(columns: ["_time"])

participant_timeline
  |> yield(name: "participant_timeline")


// 3. Objetivos de los equipos.
//
// Devuelve torres, inhibidores, dragones, barones y heraldos.
team_objectives = from(bucket: "lol_snapshots")
  |> range(
    start: v.timeRangeStart,
    stop: v.timeRangeStop
  )
  |> filter(fn: (r) =>
    r._measurement == "team_snapshot"
  )
  |> filter(fn: (r) =>
    r._field =~ /dragons|towers|barons|inhibitors|heralds/
  )
  |> sort(columns: ["_time"])

team_objectives
  |> yield(name: "team_objectives")