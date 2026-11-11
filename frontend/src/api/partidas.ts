import { http } from './client'
import type { Medicion, Partida, PartidaDetalle, Snapshot } from '../types'

// Ajustá estas rutas a los endpoints reales del backend.
export const listarPartidas = () => http<Partida[]>('/partidas')

export const obtenerPartida = (id: string) => http<PartidaDetalle>(`/partidas/${id}`)

export const listarMediciones = (partidaId: string, jugadorId: number) =>
  http<Medicion[]>(`/partidas/${partidaId}/jugadores/${jugadorId}/mediciones`)

/** Un snapshot puntual (participante + minuto). Responde 404 si no existe. */
export const obtenerSnapshot = (partidaId: string, jugadorId: number, minuto: number) =>
  http<Snapshot>(`/partidas/${partidaId}/participantes/${jugadorId}/snapshots/${minuto}`)

/** Alta de un snapshot (#13). Responde 409 si el minuto ya tiene snapshot y 400 si los datos no son válidos. */
export const crearSnapshot = (partidaId: string, jugadorId: number, snapshot: Snapshot) =>
  http<Snapshot>(`/partidas/${partidaId}/participantes/${jugadorId}/snapshots`, {
    method: 'POST',
    body: JSON.stringify(snapshot),
  })
