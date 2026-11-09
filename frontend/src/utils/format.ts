export function formatDuracion(segundos: number): string {
  const m = Math.floor(segundos / 60)
  const s = segundos % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

export function formatFecha(iso: string): string {
  return new Date(iso).toLocaleDateString('es-AR')
}

export function formatMiles(n: number): string {
  return n.toLocaleString('es-AR')
}
