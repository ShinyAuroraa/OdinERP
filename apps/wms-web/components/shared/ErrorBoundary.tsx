'use client'

import * as React from 'react'
import { AlertTriangle, RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface ErrorBoundaryState {
  hasError: boolean
  error: Error | null
}

interface ErrorBoundaryProps {
  children: React.ReactNode
  fallback?: React.ReactNode
  onError?: (error: Error, info: React.ErrorInfo) => void
}

/**
 * ErrorBoundary — class component React para captura de erros.
 * Exibe mensagem amigável com botão "Tentar novamente".
 */
export class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    this.props.onError?.(error, info)
  }

  reset = () => {
    this.setState({ hasError: false, error: null })
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback
      }

      return (
        <div className="flex flex-col items-center justify-center gap-4 rounded-lg border border-destructive/20 bg-destructive/5 p-8 text-center">
          <AlertTriangle className="h-10 w-10 text-destructive" />
          <div className="space-y-1">
            <h3 className="font-semibold text-destructive">Algo deu errado</h3>
            <p className="text-sm text-muted-foreground">
              {this.state.error?.message ?? 'Ocorreu um erro inesperado. Tente novamente.'}
            </p>
          </div>
          <Button variant="outline" size="sm" onClick={this.reset} className="gap-2">
            <RefreshCw className="h-4 w-4" />
            Tentar novamente
          </Button>
        </div>
      )
    }

    return this.props.children
  }
}

/**
 * useErrorBoundary — hook para trigger programático de ErrorBoundary.
 * Throw o erro para o boundary mais próximo.
 */
export function useErrorBoundary() {
  const [, setError] = React.useState<Error | null>(null)

  const throwError = React.useCallback((error: Error) => {
    setError(() => {
      throw error
    })
  }, [])

  return { throwError }
}
