import { useState } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'

interface Props {
  partidaId: string
  onConfirmar: () => Promise<void>
  onCancelar: () => void
}

export function ConfirmarBorradoPartida({ partidaId, onConfirmar, onCancelar }: Props) {
  const [borrando, setBorrando] = useState(false)
  const [error, setError] = useState<string>()

  async function confirmar() {
    setBorrando(true)
    setError(undefined)
    try {
      await onConfirmar()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo borrar la partida.')
      setBorrando(false)
    }
  }

  return (
    <Dialog open onClose={borrando ? undefined : onCancelar} aria-labelledby="titulo-borrar-partida">
      <DialogTitle id="titulo-borrar-partida">¿Borrar la partida #{partidaId}?</DialogTitle>
      <DialogContent>
        <DialogContentText>
          Se eliminarán la partida, sus participantes y sus métricas. Esta acción no se puede deshacer.
        </DialogContentText>
        {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancelar} disabled={borrando}>
          Cancelar
        </Button>
        <Button color="error" variant="contained" onClick={confirmar} disabled={borrando}>
          {borrando ? 'Borrando…' : 'Borrar partida'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
