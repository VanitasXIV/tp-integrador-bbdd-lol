import { useState, type FormEvent } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import TextField from '@mui/material/TextField'
import type { Medicion, MedicionInput } from '../types'

interface Props {
  inicial?: Medicion
  onGuardar: (valores: MedicionInput) => Promise<void>
  onCancelar?: () => void
}

type Campo = keyof MedicionInput
const CAMPOS: { name: Campo; label: string }[] = [
  { name: 'minuto', label: 'Minuto' },
  { name: 'oro', label: 'Oro' },
  { name: 'cs', label: 'CS' },
  { name: 'dano', label: 'Daño' },
]

export function MedicionForm({ inicial, onGuardar, onCancelar }: Props) {
  const [valores, setValores] = useState<Record<Campo, string>>({
    minuto: inicial?.minuto.toString() ?? '',
    oro: inicial?.oro.toString() ?? '',
    cs: inicial?.cs.toString() ?? '',
    dano: inicial?.dano.toString() ?? '',
  })
  const [error, setError] = useState<string>()
  const [guardando, setGuardando] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const nums = CAMPOS.map((c) => Number(valores[c.name]))
    const vacio = CAMPOS.some((c) => valores[c.name].trim() === '')
    if (vacio || nums.some((n) => !Number.isInteger(n) || n < 0)) {
      setError('Completá todos los campos con números enteros iguales o mayores a 0.')
      return
    }
    setError(undefined)
    setGuardando(true)
    try {
      await onGuardar({ minuto: nums[0], oro: nums[1], cs: nums[2], dano: nums[3] })
      if (!inicial) setValores({ minuto: '', oro: '', cs: '', dano: '' })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo guardar la medición.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Box
      component="form"
      onSubmit={submit}
      noValidate
      sx={{
        display: 'grid',
        gap: 2,
        gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(4, 1fr) auto' },
        alignItems: 'start',
      }}
    >
      {CAMPOS.map(({ name, label }) => (
        <TextField
          key={name}
          label={label}
          size="small"
          value={valores[name]}
          onChange={(e) => setValores((v) => ({ ...v, [name]: e.target.value }))}
          slotProps={{ htmlInput: { inputMode: 'numeric' } }}
          error={Boolean(error)}
        />
      ))}
      <Box sx={{ display: 'flex', gap: 1 }}>
        <Button type="submit" variant="contained" disabled={guardando}>
          {inicial ? 'Guardar cambios' : 'Agregar medición'}
        </Button>
        {onCancelar && (
          <Button variant="outlined" onClick={onCancelar}>
            Cancelar
          </Button>
        )}
      </Box>
      {error && (
        <Box role="alert" sx={{ gridColumn: '1 / -1', color: 'error.main', fontSize: 14 }}>
          {error}
        </Box>
      )}
    </Box>
  )
}
