import * as React from 'react'

interface PageHeaderProps {
  title: string
  description?: string
  actions?: React.ReactNode
}

/**
 * PageHeader — cabeçalho padrão de página WMS.
 * Exibe título H1, descrição opcional e slot de ações à direita.
 */
export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <div className="flex items-start justify-between gap-4 pb-6">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {description && <p className="text-sm text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex items-center gap-2 shrink-0">{actions}</div>}
    </div>
  )
}
