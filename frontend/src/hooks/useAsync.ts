import { useCallback, useEffect, useRef, useState } from 'react'

interface AsyncState<T> {
  data?: T
  error?: string
  loading: boolean
}

export function useAsync<T>(fn: () => Promise<T>, deps: readonly unknown[]) {
  const [state, setState] = useState<AsyncState<T>>({ loading: true })
  const [tick, setTick] = useState(0)
  const fnRef = useRef(fn)
  fnRef.current = fn

  useEffect(() => {
    let cancelado = false
    setState((s) => ({ ...s, loading: true, error: undefined }))
    fnRef
      .current()
      .then((data) => {
        if (!cancelado) setState({ data, loading: false })
      })
      .catch((e: unknown) => {
        if (!cancelado) {
          setState((s) => ({
            ...s,
            loading: false,
            error: e instanceof Error ? e.message : 'Error desconocido',
          }))
        }
      })
    return () => {
      cancelado = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick])

  const reload = useCallback(() => setTick((t) => t + 1), [])
  return { ...state, reload }
}
