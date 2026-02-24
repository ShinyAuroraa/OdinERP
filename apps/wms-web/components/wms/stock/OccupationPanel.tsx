'use client'

import React from 'react'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { useWarehouseOccupation } from '@/lib/api/stock'
import type { ZoneOccupation } from '@/types/stock'

function ZoneCard({ zone }: { zone: ZoneOccupation }) {
  const pct = zone.occupancyPercent
  const variant = pct >= 95 ? 'destructive' : pct >= 80 ? 'outline' : 'secondary'
  const label = pct >= 95 ? 'Crítico' : pct >= 80 ? 'Atenção' : 'Normal'

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle className="text-sm font-medium">{zone.zoneName}</CardTitle>
          {pct >= 80 && <Badge variant={variant}>{label}</Badge>}
        </div>
      </CardHeader>
      <CardContent className="space-y-2">
        <div className="h-2 w-full bg-muted rounded-full overflow-hidden">
          <div className="h-full bg-primary rounded-full" style={{ width: `${Math.min(pct, 100)}%` }} />
        </div>
        <p className="text-xs text-muted-foreground">
          {zone.usedCapacity.toLocaleString('pt-BR')} / {zone.totalCapacity.toLocaleString('pt-BR')} ({pct.toFixed(1)}%)
        </p>
      </CardContent>
    </Card>
  )
}

export function OccupationPanel() {
  const { data: warehouses, isLoading } = useWarehouseOccupation()

  if (isLoading) {
    return <div className="p-6"><div className="animate-pulse h-64 bg-muted rounded-lg" /></div>
  }

  return (
    <div className="p-6 space-y-6">
      <PageHeader title="Ocupação do Armazém" description="Taxa de ocupação por zona" />
      {(warehouses ?? []).map((wh) => (
        <div key={wh.warehouseId} className="space-y-3">
          <h3 className="font-semibold text-base">{wh.warehouseName}</h3>
          <p className="text-sm text-muted-foreground">
            Total: {wh.usedCapacity.toLocaleString('pt-BR')} / {wh.totalCapacity.toLocaleString('pt-BR')} ({wh.occupancyPercent.toFixed(1)}%)
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {wh.zones.map((zone) => (
              <ZoneCard key={zone.zoneId} zone={zone} />
            ))}
          </div>
        </div>
      ))}
      {(!warehouses || warehouses.length === 0) && (
        <p className="text-muted-foreground text-sm">Nenhum dado de ocupação disponível.</p>
      )}
    </div>
  )
}
