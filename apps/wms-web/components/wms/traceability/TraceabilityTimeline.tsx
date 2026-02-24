'use client'

import React from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import type { LotTraceability, SerialTraceability } from '@/types/traceability'

interface Props {
  data: LotTraceability | SerialTraceability
  searchType: 'lot' | 'serial'
}

export function TraceabilityTimeline({ data, searchType }: Props) {
  const title = searchType === 'lot'
    ? `Lote: ${'lotNumber' in data ? data.lotNumber : ''}`
    : `Série: ${'serialNumber' in data ? data.serialNumber : ''}`

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{title} — {data.productSku} · {data.productName}</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-3">
          {data.movements.map((m, i) => (
            <div key={m.id ?? i} className="flex gap-3 items-start">
              <div className="mt-1 h-2 w-2 rounded-full bg-primary shrink-0" />
              <div className="space-y-0.5">
                <div className="flex items-center gap-2">
                  <Badge variant="secondary" className="text-[10px]">{m.movementType}</Badge>
                  <span className="text-xs text-muted-foreground">
                    {new Date(m.createdAt).toLocaleString('pt-BR')}
                  </span>
                </div>
                <p className="text-sm">
                  {m.fromLocationCode ? `${m.fromLocationCode} → ` : ''}{m.toLocationCode ?? '—'}
                  {' · '}Qtd: {m.quantity}
                </p>
                {m.operatorName && (
                  <p className="text-xs text-muted-foreground">Operador: {m.operatorName}</p>
                )}
              </div>
            </div>
          ))}
          {data.movements.length === 0 && (
            <p className="text-sm text-muted-foreground">Nenhum movimento registrado.</p>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
