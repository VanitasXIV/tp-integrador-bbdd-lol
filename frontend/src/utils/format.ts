export function formatDuracion(segundos: number): string {
  const m = Math.floor(segundos / 60)
  const s = segundos % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

/** Fecha y hora, como se guardaron en la base (UTC). */
export function formatFecha(iso: string): string {
  return new Date(iso).toLocaleString('es-AR', { dateStyle: 'short', timeStyle: 'short' })
}

/** Nombres de los rangos como se ven en el cliente de LoL en español. */
const RANGOS: Record<string, string> = {
  IRON: 'Hierro',
  BRONZE: 'Bronce',
  SILVER: 'Plata',
  GOLD: 'Oro',
  PLATINUM: 'Platino',
  EMERALD: 'Esmeralda',
  DIAMOND: 'Diamante',
  MASTER: 'Maestro',
  GRANDMASTER: 'Gran Maestro',
  CHALLENGER: 'Retador',
  UNRANKED: 'Sin clasificar',
}

export function formatRango(rango: string): string {
  return RANGOS[rango] ?? rango
}

export function formatMiles(n: number): string {
  return n.toLocaleString('es-AR')
}
