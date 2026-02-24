'use client'

import React from 'react'
import { Warehouse, Package, ShoppingCart, AlertTriangle } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  useDashboardWarehouses,
  useDashboardProducts,
  useDashboardPendingPickings,
  useDashboardMrpAlerts,
  useDashboardRecentActivity,
} from '@/lib/api/dashboard'

function KpiCard({
  label,
  value,
  icon: Icon,
  description,
  alert,
}: {
  label: string
  value: number | string
  icon: React.ComponentType<{ className?: string }>
  description?: string
  alert?: boolean
}) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{label}</CardTitle>
        <Icon className={`h-4 w-4 ${alert ? 'text-destructive' : 'text-muted-foreground'}`} />
      </CardHeader>
      <CardContent>
        <div className={`text-2xl font-bold ${alert && Number(value) > 0 ? 'text-destructive' : ''}`}>
          {value}
        </div>
        {description && <p className="text-xs text-muted-foreground mt-1">{description}</p>}
      </CardContent>
    </Card>
  )
}

export function DashboardPage() {
  const { data: warehouses, isLoading: loadingWarehouses } = useDashboardWarehouses()
  const { data: products, isLoading: loadingProducts } = useDashboardProducts()
  const { data: pendingPickings, isLoading: loadingPickings } = useDashboardPendingPickings()
  const { data: mrpAlerts, isLoading: loadingMrp } = useDashboardMrpAlerts()
  const { data: recentActivity, isLoading: loadingActivity } = useDashboardRecentActivity()

  const isLoading = loadingWarehouses || loadingProducts || loadingPickings || loadingMrp

  if (isLoading) {
    return (
      <div className="p-6 space-y-4">
        <div className="animate-pulse h-8 bg-muted rounded w-48" />
        <div className="grid grid-cols-2 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="animate-pulse h-28 bg-muted rounded-lg" />
          ))}
        </div>
      </div>
    )
  }

  const totalWarehouses = warehouses?.length ?? 0
  const totalProducts = products?.length ?? 0
  const totalPendingPickings = pendingPickings?.totalElements ?? 0
  const totalMrpAlerts = mrpAlerts?.totalElements ?? 0
  const recentEntries = recentActivity?.content ?? []

  return (
    <div className="p-6 space-y-6">
      <PageHeader title="Dashboard" description="Visão geral operacional do armazém" />

      <div className="grid grid-cols-2 gap-4">
        <KpiCard
          label="Total Armazéns"
          value={totalWarehouses}
          icon={Warehouse}
          description="Armazéns ativos"
        />
        <KpiCard
          label="Total Produtos"
          value={totalProducts}
          icon={Package}
          description="Produtos cadastrados"
        />
        <KpiCard
          label="Picking Pendente"
          value={totalPendingPickings}
          icon={ShoppingCart}
          description="Ordens aguardando picking"
        />
        <KpiCard
          label="Alertas MRP"
          value={totalMrpAlerts}
          icon={AlertTriangle}
          description="Requisições sem estoque"
          alert={totalMrpAlerts > 0}
        />
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Atividade Recente</CardTitle>
        </CardHeader>
        <CardContent>
          {loadingActivity ? (
            <div className="space-y-2">
              {[1, 2, 3].map((i) => (
                <div key={i} className="animate-pulse h-8 bg-muted rounded" />
              ))}
            </div>
          ) : recentEntries.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nenhuma atividade recente.</p>
          ) : (
            <div className="space-y-2">
              {recentEntries.map((entry) => (
                <div key={entry.id} className="flex items-center justify-between py-1 border-b last:border-0">
                  <div>
                    <span className="text-sm font-medium">{entry.entityType}</span>
                    <span className="text-xs text-muted-foreground ml-2">{entry.actionType}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge variant="outline" className="text-[10px]">
                      {entry.performedByName ?? entry.performedBy}
                    </Badge>
                    <span className="text-xs text-muted-foreground">
                      {new Date(entry.createdAt).toLocaleDateString('pt-BR')}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
