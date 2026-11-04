import { Fragment, useState } from 'react'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
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
import { useNavigate, useParams } from 'react-router-dom'
import { generarPartida, listarPartidas } from '../api/partidas'
import { Estado } from '../components/Estado'
import { JugadorModal } from '../components/JugadorModal'
import { PartidaDetalle } from '../components/PartidaDetalle'
import { useAsync } from '../hooks/useAsync'
import { COLOR_AZUL, COLOR_ROJO, ORO } from '../theme'
import { formatDuracion, formatFecha, formatMiles } from '../utils/format'

export function PartidasPage() {
  const { partidaId, jugadorId } = useParams()
  const navigate = useNavigate()
  const seleccionada = partidaId ? Number(partidaId) : null
  const jugadorAbierto = jugadorId ? Number(jugadorId) : null

  const { data, loading, error, reload } = useAsync(listarPartidas, [])

  const [generando, setGenerando] = useState(false)
  const [errorGenerar, setErrorGenerar] = useState<string>()

  async function generar() {
    setGenerando(true)
    setErrorGenerar(undefined)
    try {
      const nueva = await generarPartida()
      reload()
      navigate(`/partidas/${nueva.id}`)
    } catch (e) {
      setErrorGenerar(e instanceof Error ? e.message : 'No se pudo generar la partida.')
    } finally {
      setGenerando(false)
    }
  }

  const alternar = (id: number) => navigate(seleccionada === id ? '/' : `/partidas/${id}`)

  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h1" component="h1">
            Partidas
          </Typography>
          <Typography color="text.secondary">Elegí una partida para ver su detalle.</Typography>
        </Box>
        <Button variant="contained" onClick={generar} disabled={generando}>
          {generando ? 'Generando…' : 'Generar nueva partida'}
        </Button>
      </Box>

      {errorGenerar && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setErrorGenerar(undefined)}>
          {errorGenerar}
        </Alert>
      )}

      <Estado loading={loading && !data} error={error} onRetry={reload}>
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>ID</TableCell>
                <TableCell>Fecha</TableCell>
                <TableCell>Duración</TableCell>
                <TableCell>Resultado</TableCell>
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
                      <TableCell colSpan={6} sx={{ p: 0, borderBottom: abierta ? undefined : 'none' }}>
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
