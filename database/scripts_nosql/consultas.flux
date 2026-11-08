import "influxdata/influxdb/v1"

// --- 1. Curva de ventaja de oro por minuto (Equipo Azul y Rojo) ---
// Filtra las métricas de equipos para analizar el oro total acumulado por minuto
gold_advantage = from(bucket: "lol_snapshots")
  |> range(start: 2026-01-01T00:00:00Z)
  |> filter(fn: (r) => r["_measurement"] == "team_snapshot")
  |> filter(fn: (r) => r["_field"] == "total_gold")
  // Puedes pivotar por lado (blue/red) para contrastar directamente las curvas si lo requieres

// --- 2. Métricas de un participante en el tiempo ---
// Permite rastrear oro, súbditos (cs) y experiencia de un jugador específico por ID
participant_timeline = from(bucket: "lol_snapshots")
  |> range(start: 2026-01-01T00:00:00Z)
  |> filter(fn: (r) => r["_measurement"] == "participant_snapshot")
  |> filter(fn: (r) => r["participant_id"] == "1") // Cambiar por el ID deseado
  |> filter(fn: (r) => r["_field"] == "current_gold" or r["_field"] == "cs_count" or r["_field"] == "xp" or r["_field"] == "level")

// --- 3. Objetivos por equipo (Torres, Dragones, Barones) ---
// Agrupa el control de objetivos estratégicos por partida y lado
team_objectives = from(bucket: "lol_snapshots")
  |> range(start: 2026-01-01T00:00:00Z)
  |> filter(fn: (r) => r["_measurement"] == "team_snapshot")
  |> filter(fn: (r) => r["_field"] =~ /dragons|towers|barons|inhibitors/)