import { http } from './client'
import type { Medicion, Partida, PartidaDetalle } from '../types'

// Ajustá estas rutas a los endpoints reales del backend.
export const listarPartidas = () => http<Partida[]>('/partidas')

export const obtenerPartida = (id: string) => http<PartidaDetalle>(`/partidas/${id}`)

export const listarMediciones = (partidaId: string, jugadorId: number) =>
  http<Medicion[]>(`/partidas/${partidaId}/jugadores/${jugadorId}/mediciones`)
