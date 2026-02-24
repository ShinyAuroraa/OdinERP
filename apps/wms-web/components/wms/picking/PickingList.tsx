'use client'

import React from 'react'
import { type ColumnDef } from '@tanstack/react-table'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable } from '@/components/shared/DataTable'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Button } from '@/components/ui/button'
import { usePickingOrders } from '@/lib/api/picking'
import type { PickingOrder, PickingOrderStatus } from '@/types/picking'
import Link from 'next/link'

const STATUS_LABELS: Record<PickingOrderStatus, string> = {
  PENDING: 'Pendente',
  IN_PROGRESS: 'Em Andamento',
  COMPLETED: 'Concluído',
  CANCELLED: 'Cancelado',
}

export function PickingList() {
  const [status, setStatus] = React.useState<PickingOrderStatus | 'ALL'>('ALL')
  const { data, isLoading } = usePickingOrders(status !== 'ALL' ? { status } : undefined)
  const orders = data?.content ?? []

  const columns: ColumnDef<PickingOrder>[] = [
    { accessorKey: 'orderNumber', header: 'Ordem' },
    { accessorKey: 'customerName', header: 'Cliente', cell: ({ row }) => row.original.customerName ?? '—' },
    { accessorKey: 'priority', header: 'Prioridade' },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ row }) => (
        <StatusBadge status={row.original.status} label={STATUS_LABELS[row.original.status]} />
      ),
    },
    {
      accessorKey: 'pickedItems',
      header: 'Itens',
      cell: ({ row }) => `${row.original.pickedItems}/${row.original.totalItems}`,
    },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) => (
        <Link href={`/picking/${row.original.id}`}>
          <Button variant="outline" size="sm">Ver</Button>
        </Link>
      ),
    },
  ]

  if (isLoading) {
    return <div className="p-6"><div className="animate-pulse h-64 bg-muted rounded-lg" /></div>
  }

  return (
    <div className="p-6 space-y-4">
      <PageHeader title="Picking" description="Ordens de separação de produtos" />
      <div className="flex gap-3">
        <Select value={status} onValueChange={(v) => setStatus(v as PickingOrderStatus | 'ALL')}>
          <SelectTrigger className="w-48">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Todos</SelectItem>
            <SelectItem value="PENDING">Pendente</SelectItem>
            <SelectItem value="IN_PROGRESS">Em Andamento</SelectItem>
            <SelectItem value="COMPLETED">Concluído</SelectItem>
            <SelectItem value="CANCELLED">Cancelado</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <DataTable
        columns={columns}
        data={orders}
        loading={isLoading}
        emptyMessage="Nenhuma ordem de picking encontrada."
      />
    </div>
  )
}
