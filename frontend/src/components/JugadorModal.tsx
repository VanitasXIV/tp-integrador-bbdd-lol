import { useMemo, type ReactElement } from 'react'
import CloseIcon from '@mui/icons-material/Close'
import Box from '@mui/material/Box'
import Dialog from '@mui/material/Dialog'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Typography from '@mui/material/Typography'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { listarMediciones, obtenerPartida } from '../api/partidas'
import { useAsync } from '../hooks/useAsync'
import { COLOR_AZUL, COLOR_ROJO, HEXTECH, ORO } from '../theme'
import { Estado } from './Estado'
import { MedicionesTabla } from './MedicionesTabla'

interface Props {
  partidaId: number
  jugadorId: number
  onClose: () => void
}

const tooltipStyle = { background: '#0A1428', border: `1px solid ${ORO}` }

function Grafico({ titulo, children }: { titulo: string; children: ReactElement }) {
  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 1 }}>
        {titulo}
      </Typography>
      <div style={{ width: '100%', height: 170 }}>
        <ResponsiveContainer>{children}</ResponsiveContainer>
      </div>
    </Box>
  )
}

export function JugadorModal({ partidaId, jugadorId, onClose }: Props) {
  const { data, loading, error, reload } = useAsync(
    async () => {
      const [partida, mediciones] = await Promise.all([
        obtenerPartida(partidaId),
        listarMediciones(partidaId, jugadorId),
      ])
      return { jugador: partida.jugadores.find((j) => j.id === jugadorId), mediciones }
    },
    [partidaId, jugadorId],
  )

  const ordenadas = useMemo(
    () => [...(data?.mediciones ?? [])].sort((a, b) => a.minuto - b.minuto),
    [data],
  )

  const jugador = data?.jugador
  const colorEquipo = jugador?.equipo === 'ROJO' ? COLOR_ROJO : COLOR_AZUL

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md" scroll="paper" aria-labelledby="titulo-jugador">
      <DialogTitle
        id="titulo-jugador"
        component="div"
        sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}
      >
        <Box>
          <Typography variant="h2" component="h2" sx={{ color: colorEquipo }}>
            {jugador ? `${jugador.nombre} · ${jugador.campeon}` : `Jugador ${jugadorId}`}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Métricas en la partida #{partidaId}
          </Typography>
        </Box>
        <IconButton aria-label="Cerrar métricas" onClick={onClose}>
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent dividers sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
        <Estado loading={loading && !data} error={error} onRetry={reload}>
          <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' } }}>
            <Grafico titulo="Oro por minuto">
              <LineChart data={ordenadas}>
                <CartesianGrid stroke="#1E2328" strokeDasharray="3 3" />
                <XAxis dataKey="minuto" stroke="#A09B8C" />
                <YAxis stroke="#A09B8C" />
                <Tooltip contentStyle={tooltipStyle} />
                <Line type="monotone" dataKey="oro" stroke={ORO} strokeWidth={3} />
              </LineChart>
            </Grafico>
            <Grafico titulo="Daño por minuto">
              <BarChart data={ordenadas}>
                <CartesianGrid stroke="#1E2328" strokeDasharray="3 3" />
                <XAxis dataKey="minuto" stroke="#A09B8C" />
                <YAxis stroke="#A09B8C" />
                <Tooltip contentStyle={tooltipStyle} />
                <Bar dataKey="dano" fill={COLOR_AZUL} />
              </BarChart>
            </Grafico>
            <Grafico titulo="Farmeo (CS)">
              <LineChart data={ordenadas}>
                <CartesianGrid stroke="#1E2328" strokeDasharray="3 3" />
                <XAxis dataKey="minuto" stroke="#A09B8C" />
                <YAxis stroke="#A09B8C" />
                <Tooltip contentStyle={tooltipStyle} />
                <Line type="monotone" dataKey="cs" stroke={HEXTECH} strokeWidth={3} />
              </LineChart>
            </Grafico>
          </Box>

          <Box>
            <Typography variant="h3" sx={{ mb: 1 }}>
              Mediciones
            </Typography>
            <MedicionesTabla mediciones={ordenadas} />
          </Box>
        </Estado>
      </DialogContent>
    </Dialog>
  )
}
