export type Equipo = 'AZUL' | 'ROJO'

export interface Partida {
  /** Id de Riot, por ejemplo "EUW1_7697601501" */
  id: string
  fecha: string // ISO 8601
  duracionSegundos: number
  equipoGanador: Equipo
  /** Rango promedio de los jugadores, en inglés como en la base ("MASTER", "DIAMOND"…) */
  rangoPromedio: string
  oroTotal: number
}

export interface PuntoOro {
  minuto: number
  /** Positivo = ventaja del equipo azul, negativo = ventaja del rojo */
  diferencia: number
}

export interface Jugador {
  id: number
  nombre: string
  equipo: Equipo
  campeon: string
  kills: number
  deaths: number
  assists: number
}

export interface PartidaDetalle extends Partida {
  curvaOro: PuntoOro[]
  jugadores: Jugador[]
}

export interface Medicion {
  id: number
  minuto: number
  oro: number
  cs: number
  /** El dataset no tiene daño por minuto: se muestra la experiencia. */
  xp: number
}


/** Estado completo de un participante en un minuto (consulta individual, #12). */
export interface Snapshot {
  minuto: number
  oroActual: number
  oroTotal: number
  cs: number
  csJungla: number
  xp: number
  nivel: number
  kills: number
  deaths: number
  assists: number
}

/** Objetivos acumulados de un equipo en un minuto (InfluxDB, team_snapshot). */
export interface ObjetivosEquipo {
  minuto: number
  /** 100 = azul, 200 = rojo */
  lado: number
  torres: number
  inhibidores: number
  dragonesFuego: number
  dragonesAgua: number
  dragonesTierra: number
  dragonesAire: number
  dragonesQuimtec: number
  dragonesHextech: number
  dragonesElder: number
  barones: number
  larvasVacio: number
  heraldos: number
}
