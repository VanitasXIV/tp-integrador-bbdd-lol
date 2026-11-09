export type Equipo = 'AZUL' | 'ROJO'

export interface Partida {
  id: number
  fecha: string // ISO 8601
  duracionSegundos: number
  equipoGanador: Equipo
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
  dano: number
}

