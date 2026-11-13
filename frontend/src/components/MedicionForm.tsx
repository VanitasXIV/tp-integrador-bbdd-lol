import { useState, type FormEvent } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import type { Snapshot } from '../types'

interface Props {
  /** Valores iniciales: vacío para el alta; el snapshot existente para la edición (#14). */
  inicial?: Snapshot
  onGuardar: (valores: Snapshot) => Promise<void>
  onCancelar: () => void
}

type Campo = keyof Snapshot

const CAMPOS: { name: Campo; label: string }[] = [
  { name: 'minuto', label: 'Minuto' },
  { name: 'oroActual', label: 'Oro en mano' },
  { name: 'oroTotal', label: 'Oro total' },
  { name: 'cs', label: 'CS' },
  { name: 'csJungla', label: 'CS de jungla' },
  { name: 'xp', label: 'XP' },
  { name: 'nivel', label: 'Nivel' },
  { name: 'kills', label: 'Kills' },
  { name: 'deaths', label: 'Deaths' },
  { name: 'assists', label: 'Assists' },
]

const NIVEL_MAXIMO = 20

const vacio = () => Object.fromEntries(CAMPOS.map((c) => [c.name, ''])) as Record<Campo, string>

/** Las mismas reglas que valida el backend, para avisar antes de enviar. */
function validar(v: Snapshot): string[] {
  const errores: string[] = []
  if (v.minuto < 1) errores.push('El minuto tiene que ser 1 o mayor.')
  if (v.oroActual > v.oroTotal) errores.push('El oro en mano no puede superar al oro total.')
  if (v.nivel < 1 || v.nivel > NIVEL_MAXIMO) errores.push(`El nivel tiene que estar entre 1 y ${NIVEL_MAXIMO}.`)
  return errores
}

/** Formulario de alta de un snapshot (#13); con `inicial` sirve para la edición (#14). */
export function MedicionForm({ inicial, onGuardar, onCancelar }: Props) {
  const [valores, setValores] = useState<Record<Campo, string>>(() =>
    inicial
      ? (Object.fromEntries(CAMPOS.map((c) => [c.name, String(inicial[c.name])])) as Record<Campo, string>)
      : vacio(),
  )
  const [errores, setErrores] = useState<string[]>([])
  const [guardando, setGuardando] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const vacios = CAMPOS.filter((c) => valores[c.name].trim() === '')
    const invalidos = CAMPOS.filter((c) => {
      const n = Number(valores[c.name])
      return valores[c.name].trim() !== '' && (!Number.isInteger(n) || n < 0)
    })
    if (vacios.length || invalidos.length) {
      setErrores([
        ...(vacios.length ? [`Completá: ${vacios.map((c) => c.label).join(', ')}.`] : []),
        ...(invalidos.length
          ? [`Tienen que ser enteros iguales o mayores a 0: ${invalidos.map((c) => c.label).join(', ')}.`]
          : []),
      ])
      return
    }
    const snapshot = Object.fromEntries(CAMPOS.map((c) => [c.name, Number(valores[c.name])])) as unknown as Snapshot
    const propios = validar(snapshot)
    if (propios.length) {
      setErrores(propios)
      return
    }
    setErrores([])
    setGuardando(true)
    try {
      await onGuardar(snapshot)
    } catch (err) {
      // Errores del backend: 400 (validación), 404 (participante) o 409 (minuto repetido).
      setErrores([err instanceof Error ? err.message : 'No se pudo guardar la medición.'])
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Box component="form" onSubmit={submit} noValidate aria-label={inicial ? 'Editar medición' : 'Nueva medición'}>
      <Typography variant="h4" sx={{ mb: 1.5 }}>
        {inicial ? `Editar minuto ${inicial.minuto}` : 'Nueva medición'}
      </Typography>
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(5, 1fr)' } }}>
        {CAMPOS.map(({ name, label }) => (
          <TextField
            key={name}
            name={name}
            label={label}
            size="small"
            value={valores[name]}
            // En la edición el minuto identifica al snapshot: no se puede cambiar.
            disabled={Boolean(inicial) && name === 'minuto'}
            onChange={(e) => setValores((v) => ({ ...v, [name]: e.target.value }))}
            slotProps={{ htmlInput: { inputMode: 'numeric' } }}
          />
        ))}
      </Box>
      {errores.length > 0 && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {errores.map((e) => (
            <div key={e}>{e}</div>
          ))}
        </Alert>
      )}
      <Box sx={{ display: 'flex', gap: 1, mt: 2, justifyContent: 'flex-end' }}>
        <Button variant="outlined" onClick={onCancelar} disabled={guardando}>
          Cancelar
        </Button>
        <Button type="submit" variant="contained" disabled={guardando}>
          {guardando ? 'Guardando…' : inicial ? 'Guardar cambios' : 'Agregar medición'}
        </Button>
      </Box>
    </Box>
  )
}
