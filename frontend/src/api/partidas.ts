import { http } from './client'
import type { Medicion, Partida, PartidaDetalle } from '../types'

// Ajustá estas rutas a los endpoints reales del backend.
export const listarPartidas = () => http<Partida[]>('/partidas')

export const obtenerPartida = (id: number) => http<PartidaDetalle>(`/partidas/${id}`)

export const listarMediciones = (partidaId: number, jugadorId: number) =>
  http<Medicion[]>(`/partidas/${partidaId}/jugadores/${jugadorId}/mediciones`)

// Pide al backend que genere una partida nueva y devuelve la partida creada.
export const generarPartida = () => http<Partida>('/partidas', { method: 'POST' })
