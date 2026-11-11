import { Fragment } from 'react'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Alert from '@mui/material/Alert'
import Chip from '@mui/material/Chip'
import Collapse from '@mui/material/Collapse'
import Paper from '@mui/material/Paper'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import CircularProgress from '@mui/material/CircularProgress'
import Stack from '@mui/material/Stack'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { crearPartidaSimulada, listarPartidas } from '../api/partidas'
import { Estado } from '../components/Estado'
import { JugadorModal } from '../components/JugadorModal'
import { PartidaDetalle } from '../components/PartidaDetalle'
import { useAsync } from '../hooks/useAsync'
import { COLOR_AZUL, COLOR_ROJO, ORO } from '../theme'
import { formatDuracion, formatFecha, formatMiles, formatRango } from '../utils/format'

export function PartidasPage() {
  const { partidaId, jugadorId } = useParams()
  const navigate = useNavigate()
  const seleccionada = partidaId ?? null
  const jugadorAbierto = jugadorId ? Number(jugadorId) : null
  const [creando, setCreando] = useState(false)
  const [mensaje, setMensaje] = useState<string>()
  const [errorCreacion, setErrorCreacion] = useState<string>()

  const { data, loading, error, reload } = useAsync(listarPartidas, [])

  const alternar = (id: string) => navigate(seleccionada === id ? '/' : `/partidas/${id}`)

  const crearPartida = async () => {
    setCreando(true)
    setMensaje(undefined)
    setErrorCreacion(undefined)
    try {
      const partida = await crearPartidaSimulada()
      setMensaje(`Partida ${partida.id} creada.`)
      reload()
    } catch (e) {
      setErrorCreacion(e instanceof Error ? e.message : 'No se pudo crear la partida.')
    } finally {
      setCreando(false)
    }
  }

  return (
    <>
      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'stretch', sm: 'center' }} spacing={2} sx={{ mb: 3 }}>
        <Box>
        <Typography variant="h1" component="h1">
          Partidas
        </Typography>
        <Typography color="text.secondary">Elegí una partida para ver su detalle.</Typography>
        </Box>
        <Button variant="contained" onClick={crearPartida} disabled={creando} startIcon={creando ? <CircularProgress size={18} color="inherit" /> : undefined}>
          {creando ? 'Simulando partida…' : 'Simular partida'}
        </Button>
      </Stack>

      {mensaje && <Alert severity="success" onClose={() => setMensaje(undefined)} sx={{ mb: 2 }}>{mensaje}</Alert>}
      {errorCreacion && <Alert severity="error" onClose={() => setErrorCreacion(undefined)} sx={{ mb: 2 }}>{errorCreacion}</Alert>}

      <Estado loading={loading && !data} error={error} onRetry={reload}>
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>ID</TableCell>
                <TableCell>Fecha</TableCell>
                <TableCell>Duración</TableCell>
                <TableCell>Resultado</TableCell>
                <TableCell>Rango promedio</TableCell>
                <TableCell>Oro total</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {data?.map((p) => {
                const abierta = seleccionada === p.id
                const colorGanador = p.equipoGanador === 'AZUL' ? COLOR_AZUL : COLOR_ROJO
                return (
                  <Fragment key={p.id}>
                    <TableRow
                      selected={abierta}
                      sx={abierta ? { '& > td': { borderBottom: 'none' }, boxShadow: `inset 3px 0 0 ${ORO}` } : undefined}
                    >
                      <TableCell>#{p.id}</TableCell>
                      <TableCell>{formatFecha(p.fecha)}</TableCell>
                      <TableCell>{formatDuracion(p.duracionSegundos)}</TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          variant="outlined"
                          label={`Victoria ${p.equipoGanador === 'AZUL' ? 'azul' : 'roja'}`}
                          sx={{ color: colorGanador, borderColor: colorGanador }}
                        />
                      </TableCell>
                      <TableCell>{formatRango(p.rangoPromedio)}</TableCell>
                      <TableCell>{formatMiles(p.oroTotal)}</TableCell>
                      <TableCell align="right">
                        <Button
                          size="small"
                          variant={abierta ? 'contained' : 'outlined'}
                          aria-expanded={abierta}
                          onClick={() => alternar(p.id)}
                          endIcon={
                            <ExpandMoreIcon
                              sx={{ transform: abierta ? 'rotate(180deg)' : 'none', transition: 'transform .2s' }}
                            />
                          }
                        >
                          {abierta ? 'Ocultar detalle' : 'Ver detalle'}
                        </Button>
                      </TableCell>
                    </TableRow>
                    <TableRow>
                      <TableCell colSpan={7} sx={{ p: 0, borderBottom: abierta ? undefined : 'none' }}>
                        <Collapse in={abierta} timeout="auto" unmountOnExit>
                          <PartidaDetalle
                            partidaId={p.id}
                            onVerJugador={(id) => navigate(`/partidas/${p.id}/jugadores/${id}`)}
                          />
                        </Collapse>
                      </TableCell>
                    </TableRow>
                  </Fragment>
                )
              })}
            </TableBody>
          </Table>
          {data?.length === 0 && (
            <Typography color="text.secondary" sx={{ p: 3 }}>
              No hay partidas cargadas.
            </Typography>
          )}
        </TableContainer>
      </Estado>

      {seleccionada !== null && jugadorAbierto !== null && (
        <JugadorModal
          partidaId={seleccionada}
          jugadorId={jugadorAbierto}
          onClose={() => navigate(`/partidas/${seleccionada}`)}
        />
      )}
    </>
  )
}
