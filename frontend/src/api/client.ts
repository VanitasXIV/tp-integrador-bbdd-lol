/** Error HTTP con el código y el mensaje que devolvió el backend. */
export class ErrorBackend extends Error {
  constructor(
    readonly status: number,
    mensaje: string,
  ) {
    super(mensaje)
  }
}

/** El backend responde los errores como { mensaje, errores: [...] }. */
async function mensajeDeError(res: Response, path: string): Promise<string> {
  try {
    const cuerpo = (await res.json()) as { mensaje?: string; errores?: string[] }
    if (cuerpo.mensaje) {
      return cuerpo.errores?.length ? `${cuerpo.mensaje}: ${cuerpo.errores.join('; ')}` : cuerpo.mensaje
    }
  } catch {
    // El cuerpo no era JSON: se usa el mensaje genérico.
  }
  return `El backend respondió ${res.status} en ${path}`
}

const BASE_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

export async function http<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  if (!res.ok) {
    throw new ErrorBackend(res.status, await mensajeDeError(res, path))
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}
