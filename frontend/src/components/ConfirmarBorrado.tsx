import { useState } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'

interface Props {
  minuto: number
  descripcion: string
  onConfirmar: () => Promise<void>
  onCancelar: () => void
}

/** Pide confirmación antes de borrar un snapshot (#15). */
export function ConfirmarBorrado({ minuto, descripcion, onConfirmar, onCancelar }: Props) {
  const [borrando, setBorrando] = useState(false)
  const [error, setError] = useState<string>()

  async function confirmar() {
    setBorrando(true)
    setError(undefined)
    try {
      await onConfirmar()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo borrar la medición.')
      setBorrando(false)
    }
  }

  return (
    <Dialog open onClose={borrando ? undefined : onCancelar} aria-labelledby="titulo-borrar">
      <DialogTitle id="titulo-borrar">¿Borrar la medición del minuto {minuto}?</DialogTitle>
      <DialogContent>
        <DialogContentText>
          Se elimina de InfluxDB el snapshot de {descripcion} en el minuto {minuto}. Esta acción no se puede deshacer.
        </DialogContentText>
        {error && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {error}
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancelar} disabled={borrando}>
          Cancelar
        </Button>
        <Button color="error" variant="contained" onClick={confirmar} disabled={borrando}>
          {borrando ? 'Borrando…' : 'Borrar'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
