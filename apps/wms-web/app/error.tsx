'use client'

import { useEffect } from 'react'
import { AlertTriangle } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface ErrorProps {
  error: Error & { digest?: string }
  reset: () => void
}

export default function GlobalError({ error, reset }: ErrorProps) {
  useEffect(() => {
    console.error('[WMS] Unhandled error:', error)
  }, [error])

  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="flex flex-col items-center gap-6 p-8 text-center max-w-md">
        <div className="flex h-16 w-16 items-center justify-center rounded-full bg-destructive/10">
          <AlertTriangle className="h-8 w-8 text-destructive" />
        </div>
        <div className="space-y-2">
          <h2 className="text-2xl font-bold">Algo deu errado</h2>
          <p className="text-muted-foreground">
            Ocorreu um erro inesperado. Por favor, tente novamente.
          </p>
          {error.digest && (
            <p className="text-xs text-muted-foreground font-mono">ID: {error.digest}</p>
          )}
        </div>
        <Button onClick={reset}>Tentar novamente</Button>
      </div>
    </div>
  )
}
