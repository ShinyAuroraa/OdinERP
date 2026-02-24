'use client'

import React from 'react'
import { type ColumnDef } from '@tanstack/react-table'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable } from '@/components/shared/DataTable'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Button } from '@/components/ui/button'
import { toast } from 'sonner'
import { usePackingOrders, useStartPacking, useCompletePacking } from '@/lib/api/packing'
import type { PackingOrder, PackingOrderStatus } from '@/types/packing'

const STATUS_LABELS: Record<PackingOrderStatus, string> = {
  PENDING: 'Pendente',
  IN_PROGRESS: 'Em Embalagem',
  COMPLETED: 'Concluído',
  CANCELLED: 'Cancelado',
}

function ActionButtons({ order }: { order: PackingOrder }) {
  const start = useStartPacking(order.id)
  const complete = useCompletePacking(order.id)

  if (order.status === 'PENDING') {
    return (
      <Button size="sm" variant="outline" disabled={start.isPending}
        onClick={() => start.mutate(undefined, {
          onSuccess: () => toast.success('Embalagem iniciada.'),
          onError: () => toast.error('Erro ao iniciar embalagem.'),
        })}>
        Iniciar
      </Button>
    )
  }
  if (order.status === 'IN_PROGRESS') {
    return (
      <Button size="sm" disabled={complete.isPending}
        onClick={() => complete.mutate(undefined, {
          onSuccess: () => toast.success('Embalagem concluída.'),
          onError: () => toast.error('Erro ao concluir embalagem.'),
        })}>
        Concluir
      </Button>
    )
  }
  return null
}

export function PackingList() {
  const [status, setStatus] = React.useState<PackingOrderStatus | 'ALL'>('ALL')
  const { data, isLoading } = usePackingOrders(status !== 'ALL' ? { status } : undefined)
  const orders = data?.content ?? []

  const columns: ColumnDef<PackingOrder>[] = [
    { accessorKey: 'packingOrderNumber', header: 'Ordem' },
    { accessorKey: 'pickingOrderId', header: 'Picking Origem' },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ row }) => (
        <StatusBadge status={row.original.status} label={STATUS_LABELS[row.original.status]} />
      ),
    },
    {
      accessorKey: 'packedItems',
      header: 'Volumes/Itens',
      cell: ({ row }) => `${row.original.packedItems}/${row.original.totalItems}`,
    },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) => <ActionButtons order={row.original} />,
    },
  ]

  if (isLoading) {
    return <div className="p-6"><div className="animate-pulse h-64 bg-muted rounded-lg" /></div>
  }

  return (
    <div className="p-6 space-y-4">
      <PageHeader title="Packing" description="Estação de embalagem de produtos" />
      <div className="flex gap-3">
        <Select value={status} onValueChange={(v) => setStatus(v as PackingOrderStatus | 'ALL')}>
          <SelectTrigger className="w-48">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Todos</SelectItem>
            <SelectItem value="PENDING">Pendente</SelectItem>
            <SelectItem value="IN_PROGRESS">Em Embalagem</SelectItem>
            <SelectItem value="COMPLETED">Concluído</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <DataTable
        columns={columns}
        data={orders}
        loading={isLoading}
        emptyMessage="Nenhuma ordem de packing encontrada."
      />
    </div>
  )
}
