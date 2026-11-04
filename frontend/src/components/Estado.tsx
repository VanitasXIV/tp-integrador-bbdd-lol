import type { ReactNode } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'

interface Props {
  loading: boolean
  error?: string
  onRetry?: () => void
  children: ReactNode
}

export function Estado({ loading, error, onRetry, children }: Props) {
  if (error) {
    return (
      <Alert
        severity="error"
        action={
          onRetry && (
            <Button color="inherit" size="small" onClick={onRetry}>
              Reintentar
            </Button>
          )
        }
      >
        {error}
      </Alert>
    )
  }
  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress aria-label="Cargando" />
      </Box>
    )
  }
  return <>{children}</>
}
