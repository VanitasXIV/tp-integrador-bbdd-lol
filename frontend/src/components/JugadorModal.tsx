import { useMemo, useState, type ReactElement } from 'react'
import CloseIcon from '@mui/icons-material/Close'
import AddIcon from '@mui/icons-material/Add'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
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
import {
  crearSnapshot,
  listarMediciones,
  modificarSnapshot,
  obtenerPartida,
  obtenerSnapshot,
} from '../api/partidas'
import { useAsync } from '../hooks/useAsync'
import { COLOR_AZUL, COLOR_ROJO, HEXTECH, ORO } from '../theme'
import { Estado } from './Estado'
import { MedicionForm } from './MedicionForm'
import { MedicionesTabla } from './MedicionesTabla'
import { SnapshotDetalle } from './SnapshotDetalle'

interface Props {
  partidaId: string
  jugadorId: number
  onClose: () => void
}

/** Carga el snapshot completo y abre el formulario en modo edición (#14). */
function EdicionSnapshot(props: {
  partidaId: string
  jugadorId: number
  minuto: number
  onGuardado: (minuto: number) => void
  onCancelar: () => void
}) {
  const { partidaId, jugadorId, minuto, onGuardado, onCancelar } = props
  const { data, loading, error, reload } = useAsync(
    () => obtenerSnapshot(partidaId, jugadorId, minuto),
    [partidaId, jugadorId, minuto],
  )
  return (
    <Estado loading={loading} error={error} onRetry={reload}>
      {data && (
        <MedicionForm
          key={minuto}
          inicial={data}
          onCancelar={onCancelar}
          onGuardar={async (valores) => {
            await modificarSnapshot(partidaId, jugadorId, valores)
            onGuardado(valores.minuto)
          }}
        />
      )}
    </Estado>
  )
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
  const [minutoAbierto, setMinutoAbierto] = useState<number | null>(null)
  const [creando, setCreando] = useState(false)
  const [editando, setEditando] = useState<number | null>(null)
  /** Cambia después de cada alta o edición para que el detalle del snapshot se vuelva a pedir. */
  const [version, setVersion] = useState(0)

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
            <Grafico titulo="Experiencia (XP)">
              <BarChart data={ordenadas}>
                <CartesianGrid stroke="#1E2328" strokeDasharray="3 3" />
                <XAxis dataKey="minuto" stroke="#A09B8C" />
                <YAxis stroke="#A09B8C" />
                <Tooltip contentStyle={tooltipStyle} />
                <Bar dataKey="xp" fill={COLOR_AZUL} />
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
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Typography variant="h3">Mediciones</Typography>
              {!creando && (
                <Button
                  size="small"
                  variant="contained"
                  startIcon={<AddIcon />}
                  onClick={() => {
                    setEditando(null)
                    setCreando(true)
                  }}
                >
                  Agregar medición
                </Button>
              )}
            </Box>
            <MedicionesTabla
              mediciones={ordenadas}
              seleccionado={editando ?? minutoAbierto}
              onVer={setMinutoAbierto}
              onEditar={(minuto) => {
                setCreando(false)
                setEditando(minuto)
              }}
            />
          </Box>

          {creando && (
            <MedicionForm
              onCancelar={() => setCreando(false)}
              onGuardar={async (valores) => {
                const nuevo = await crearSnapshot(partidaId, jugadorId, valores)
                setCreando(false)
                reload()
                setVersion((v) => v + 1)
                setMinutoAbierto(nuevo.minuto)
              }}
            />
          )}

          {editando !== null && (
            <EdicionSnapshot
              partidaId={partidaId}
              jugadorId={jugadorId}
              minuto={editando}
              onCancelar={() => setEditando(null)}
              onGuardado={(minuto) => {
                setEditando(null)
                reload()
                setVersion((v) => v + 1)
                setMinutoAbierto(minuto)
              }}
            />
          )}

          {minutoAbierto !== null && (
            <SnapshotDetalle
              key={`${minutoAbierto}-${version}`}
              partidaId={partidaId}
              jugadorId={jugadorId}
              minuto={minutoAbierto}
              onClose={() => setMinutoAbierto(null)}
            />
          )}
        </Estado>
      </DialogContent>

    </Dialog>
  )
}
