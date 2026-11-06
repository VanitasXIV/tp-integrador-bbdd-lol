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
}

export function MedicionesTabla({ mediciones }: Props) {
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
            <TableCell>Daño</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {mediciones.map((m) => (
            <TableRow key={m.id} hover>
              <TableCell>{m.minuto}</TableCell>
              <TableCell>{formatMiles(m.oro)}</TableCell>
              <TableCell>{m.cs}</TableCell>
              <TableCell>{formatMiles(m.dano)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
