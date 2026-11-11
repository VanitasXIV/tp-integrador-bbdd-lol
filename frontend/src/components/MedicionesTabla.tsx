import Button from '@mui/material/Button'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import type { Medicion } from '../types'
import { formatMiles } from '../utils/format'

interface Props {
  mediciones: Medicion[]
  /** Minuto del snapshot abierto, para resaltar la fila. */
  seleccionado?: number | null
  onVer: (minuto: number) => void
  onEditar: (minuto: number) => void
  onBorrar: (minuto: number) => void
}

export function MedicionesTabla({ mediciones, seleccionado, onVer, onEditar, onBorrar }: Props) {
  if (mediciones.length === 0) {
    return (
      <Typography color="text.secondary">
        Esta partida todavía no tiene mediciones para este jugador.
      </Typography>
    )
  }
  return (
    <TableContainer>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Minuto</TableCell>
            <TableCell>Oro</TableCell>
            <TableCell>CS</TableCell>
            <TableCell>XP</TableCell>
            <TableCell />
          </TableRow>
        </TableHead>
        <TableBody>
          {mediciones.map((m) => (
            <TableRow key={m.id} hover selected={seleccionado === m.minuto}>
              <TableCell>{m.minuto}</TableCell>
              <TableCell>{formatMiles(m.oro)}</TableCell>
              <TableCell>{m.cs}</TableCell>
              <TableCell>{formatMiles(m.xp)}</TableCell>
              <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                <Button size="small" variant="outlined" onClick={() => onVer(m.minuto)}>
                  Ver
                </Button>
                <Button size="small" variant="outlined" sx={{ ml: 1 }} onClick={() => onEditar(m.minuto)}>
                  Editar
                </Button>
                <Button size="small" variant="outlined" color="error" sx={{ ml: 1 }} onClick={() => onBorrar(m.minuto)}>
                  Borrar
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
