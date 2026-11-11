import CloseIcon from '@mui/icons-material/Close'
import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import { obtenerSnapshot } from '../api/partidas'
import { useAsync } from '../hooks/useAsync'
import { ORO } from '../theme'
import type { Snapshot } from '../types'
import { formatMiles } from '../utils/format'
import { Estado } from './Estado'

interface Props {
  partidaId: string
  jugadorId: number
  minuto: number
  onClose: () => void
}

const CAMPOS: { clave: keyof Snapshot; etiqueta: string; miles?: boolean }[] = [
  { clave: 'oroActual', etiqueta: 'Oro en mano', miles: true },
  { clave: 'oroTotal', etiqueta: 'Oro total', miles: true },
  { clave: 'cs', etiqueta: 'CS' },
  { clave: 'csJungla', etiqueta: 'CS de jungla' },
  { clave: 'xp', etiqueta: 'XP', miles: true },
  { clave: 'nivel', etiqueta: 'Nivel' },
  { clave: 'kills', etiqueta: 'Kills' },
  { clave: 'deaths', etiqueta: 'Deaths' },
  { clave: 'assists', etiqueta: 'Assists' },
]

/** Consulta individual de un snapshot (#12): se pide al backend por participante + minuto. */
export function SnapshotDetalle({ partidaId, jugadorId, minuto, onClose }: Props) {
  const { data, loading, error, reload } = useAsync(
    () => obtenerSnapshot(partidaId, jugadorId, minuto),
    [partidaId, jugadorId, minuto],
  )

  return (
    <Paper variant="outlined" sx={{ p: 2, borderColor: ORO }} aria-label={`Snapshot del minuto ${minuto}`}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
        <Typography variant="h4">Minuto {minuto}</Typography>
        <IconButton size="small" aria-label="Cerrar snapshot" onClick={onClose}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </Box>
      <Estado loading={loading} error={error} onRetry={reload}>
        {data && (
          <Box
            component="dl"
            sx={{ m: 0, display: 'grid', gap: 1.5, gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(3, 1fr)' } }}
          >
            {CAMPOS.map(({ clave, etiqueta, miles }) => (
              <Box key={clave}>
                <Typography component="dt" variant="body2" color="text.secondary">
                  {etiqueta}
                </Typography>
                <Typography component="dd" sx={{ m: 0, fontWeight: 600 }}>
                  {miles ? formatMiles(data[clave]) : data[clave]}
                </Typography>
              </Box>
            ))}
          </Box>
        )}
      </Estado>
    </Paper>
  )
}
