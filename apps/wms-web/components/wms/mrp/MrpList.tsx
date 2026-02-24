'use client'

import React from 'react'
import { type ColumnDef } from '@tanstack/react-table'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable } from '@/components/shared/DataTable'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { Badge } from '@/components/ui/badge'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useMaterialRequests } from '@/lib/api/mrp'
import type { ProductionMaterialRequest, MaterialRequestStatus } from '@/types/mrp'

const STATUS_LABELS: Record<MaterialRequestStatus, string> = {
  PENDING: 'Pendente',
  RESERVING: 'Reservando',
  PICKING_PENDING: 'Picking Pendente',
  DELIVERED: 'Entregue',
  STOCK_SHORTAGE: 'Sem Estoque',
  COMPENSATED: 'Compensado',
}

export function MrpList() {
  const [status, setStatus] = React.useState<MaterialRequestStatus | 'ALL'>('ALL')
  const { data, isLoading } = useMaterialRequests(status !== 'ALL' ? { status } : undefined)
  const requests = data?.content ?? []

  const columns: ColumnDef<ProductionMaterialRequest>[] = [
    { accessorKey: 'id', header: 'ID', cell: ({ row }) => row.original.id.slice(0, 8) },
    { accessorKey: 'productionOrderId', header: 'Ordem Produção', cell: ({ row }) => row.original.productionOrderNumber ?? row.original.productionOrderId.slice(0, 8) },
    {
      id: 'items',
      header: 'Itens',
      cell: ({ row }) => row.original.items.length,
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ row }) => (
        <div className="flex items-center gap-2">
          <StatusBadge status={row.original.status} label={STATUS_LABELS[row.original.status]} />
          {row.original.status === 'STOCK_SHORTAGE' && (
            <Badge variant="destructive" className="text-[10px]">Alerta</Badge>
          )}
        </div>
      ),
    },
    {
      accessorKey: 'createdAt',
      header: 'Data',
      cell: ({ row }) => new Date(row.original.createdAt).toLocaleDateString('pt-BR'),
    },
  ]

  if (isLoading) {
    return <div className="p-6"><div className="animate-pulse h-64 bg-muted rounded-lg" /></div>
  }

  return (
    <div className="p-6 space-y-4">
      <PageHeader title="MRP" description="Integração WMS↔MRP — Requisições de Material" />
      <div className="flex gap-3">
        <Select value={status} onValueChange={(v) => setStatus(v as MaterialRequestStatus | 'ALL')}>
          <SelectTrigger className="w-56">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Todos</SelectItem>
            <SelectItem value="PENDING">Pendente</SelectItem>
            <SelectItem value="RESERVING">Reservando</SelectItem>
            <SelectItem value="PICKING_PENDING">Picking Pendente</SelectItem>
            <SelectItem value="DELIVERED">Entregue</SelectItem>
            <SelectItem value="STOCK_SHORTAGE">Sem Estoque</SelectItem>
            <SelectItem value="COMPENSATED">Compensado</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <DataTable
        columns={columns}
        data={requests}
        loading={isLoading}
        emptyMessage="Nenhuma requisição de material encontrada."
      />
    </div>
  )
}
