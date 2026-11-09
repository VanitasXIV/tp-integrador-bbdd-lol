const BASE_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

export async function http<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  if (!res.ok) {
    throw new Error(`El backend respondió ${res.status} en ${path}`)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}
