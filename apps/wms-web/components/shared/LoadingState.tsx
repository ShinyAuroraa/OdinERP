import * as React from 'react'
import { Loader2 } from 'lucide-react'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

interface LoadingStateProps {
  variant?: 'spinner' | 'skeleton' | 'page'
  rows?: number
  className?: string
  label?: string
}

/**
 * LoadingState — estados de loading padronizados para WMS.
 * - spinner: ícone centralizado animado
 * - skeleton: grade de N linhas skeleton
 * - page: skeleton de página completa (header + tabela)
 */
export function LoadingState({ variant = 'spinner', rows = 5, className, label }: LoadingStateProps) {
  if (variant === 'spinner') {
    return (
      <div className={cn('flex flex-col items-center justify-center gap-3 py-12', className)}>
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        {label && <p className="text-sm text-muted-foreground">{label}</p>}
      </div>
    )
  }

  if (variant === 'skeleton') {
    return (
      <div className={cn('space-y-3', className)}>
        {Array.from({ length: rows }).map((_, i) => (
          <Skeleton key={i} className="h-10 w-full" />
        ))}
      </div>
    )
  }

  // variant === 'page'
  return (
    <div className={cn('space-y-6', className)}>
      {/* Header skeleton */}
      <div className="flex items-start justify-between">
        <div className="space-y-2">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-4 w-72" />
        </div>
        <Skeleton className="h-9 w-28" />
      </div>
      {/* Table skeleton */}
      <div className="rounded-md border">
        <div className="border-b bg-muted/50 p-4">
          <div className="flex gap-4">
            {[2, 3, 4, 3].map((w, i) => (
              <Skeleton key={i} className={`h-4 w-${w * 8}`} />
            ))}
          </div>
        </div>
        {Array.from({ length: rows }).map((_, i) => (
          <div key={i} className="border-b p-4">
            <div className="flex gap-4">
              {[2, 3, 4, 3].map((w, j) => (
                <Skeleton key={j} className={`h-4 w-${w * 8}`} />
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
