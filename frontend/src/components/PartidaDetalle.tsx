import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { obtenerPartida } from '../api/partidas'
import { useAsync } from '../hooks/useAsync'
import { COLOR_AZUL, COLOR_ROJO } from '../theme'
import type { Equipo, Jugador } from '../types'
import { CurvaOro } from './CurvaOro'
import { Estado } from './Estado'

interface Props {
  partidaId: number
  onVerJugador: (jugadorId: number) => void
}

function TablaEquipo(props: { equipo: Equipo; jugadores: Jugador[]; onVerJugador: (id: number) => void }) {
  const { equipo, jugadores, onVerJugador } = props
  const color = equipo === 'AZUL' ? COLOR_AZUL : COLOR_ROJO
  return (
    <Box sx={{ overflowX: 'auto' }}>
      <Typography variant="h4" sx={{ color, mb: 1 }}>
        Equipo {equipo === 'AZUL' ? 'azul' : 'rojo'}
      </Typography>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Jugador</TableCell>
            <TableCell>Campeón</TableCell>
            <TableCell>K/D/A</TableCell>
            <TableCell />
          </TableRow>
        </TableHead>
        <TableBody>
          {jugadores.map((j) => (
            <TableRow key={j.id}>
              <TableCell>{j.nombre}</TableCell>
              <TableCell>
                <Chip size="small" label={j.campeon} variant="outlined" sx={{ borderColor: color }} />
              </TableCell>
              <TableCell>
                {j.kills}/{j.deaths}/{j.assists}
              </TableCell>
              <TableCell align="right">
                <Button size="small" variant="outlined" onClick={() => onVerJugador(j.id)}>
                  Ver métricas
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  )
}

export function PartidaDetalle({ partidaId, onVerJugador }: Props) {
  const { data, loading, error, reload } = useAsync(() => obtenerPartida(partidaId), [partidaId])

  return (
    <Box sx={{ p: 2.5, display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Estado loading={loading} error={error} onRetry={reload}>
        {data && (
          <>
            <Box>
              <Typography variant="h3" sx={{ mb: 1 }}>
                Curva de oro
              </Typography>
              <CurvaOro datos={data.curvaOro} />
            </Box>
            <Box
              sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}
            >
              <TablaEquipo
                equipo="AZUL"
                jugadores={data.jugadores.filter((j) => j.equipo === 'AZUL')}
                onVerJugador={onVerJugador}
              />
              <TablaEquipo
                equipo="ROJO"
                jugadores={data.jugadores.filter((j) => j.equipo === 'ROJO')}
                onVerJugador={onVerJugador}
              />
            </Box>
          </>
        )}
      </Estado>
    </Box>
  )
}
