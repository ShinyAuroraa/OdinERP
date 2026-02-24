'use client'

import React from 'react'
import Link from 'next/link'
import { Plus } from 'lucide-react'
import { DataTable } from '@/components/shared/DataTable'
import { PageHeader } from '@/components/shared/PageHeader'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { Button } from '@/components/ui/button'
import { useInventoryCounts, useCreateInventoryCount } from '@/lib/api/inventory'
import { useHasRole } from '@/hooks/useHasRole'
import { toast } from 'sonner'
import type { InventoryCount, InventoryCountStatus } from '@/types/inventory'
import type { ColumnDef } from '@tanstack/react-table'

const STATUS_LABELS: Record<InventoryCountStatus, string> = {
  CREATED: 'Criada', IN_PROGRESS: 'Em Contagem', RECONCILED: 'Reconciliada',
  APPROVED: 'Aprovada', CLOSED: 'Fechada',
}

const columns: ColumnDef<InventoryCount>[] = [
  {
    accessorKey: 'id',
    header: 'ID',
    cell: ({ row }) => (
      <Link href={`/inventory/${row.original.id}`} className="font-medium hover:underline text-primary">
        {row.original.id.slice(0, 8)}...
      </Link>
    ),
  },
  {
    accessorKey: 'status',
    header: 'Status',
    cell: ({ row }) => <StatusBadge status={row.original.status} label={STATUS_LABELS[row.original.status]} />,
  },
  { accessorKey: 'totalItems', header: 'Itens' },
  { accessorKey: 'countedItems', header: 'Contados' },
  { accessorKey: 'divergentItems', header: 'Divergências' },
  {
    accessorKey: 'createdAt',
    header: 'Data',
    cell: ({ row }) => new Date(row.original.createdAt).toLocaleDateString('pt-BR'),
  },
  {
    id: 'actions',
    header: 'Ações',
    cell: ({ row }) => (
      <Link href={`/inventory/${row.original.id}`}>
        <Button variant="outline" size="sm">Ver</Button>
      </Link>
    ),
  },
]

export function InventoryList() {
  const isSupervisor = useHasRole('WMS_SUPERVISOR')
  const { data: counts, isLoading } = useInventoryCounts()
  const createCount = useCreateInventoryCount()

  function handleCreate() {
    createCount.mutate(
      { warehouseId: '' },
      {
        onSuccess: () => toast.success('Sessão de inventário criada.'),
        onError: () => toast.error('Erro ao criar sessão de inventário.'),
      },
    )
  }

  return (
    <div className="p-6 space-y-4">
      <PageHeader
        title="Inventário Físico"
        description="Gerencie sessões de contagem e reconciliação de estoque"
        actions={
          isSupervisor ? (
            <Button onClick={handleCreate} size="sm" disabled={createCount.isPending}>
              <Plus className="h-4 w-4 mr-2" />
              Nova Sessão
            </Button>
          ) : null
        }
      />
      <DataTable
        columns={columns}
        data={counts ?? []}
        loading={isLoading}
        emptyMessage="Nenhuma sessão de inventário."
        searchPlaceholder="Buscar..."
        searchable
      />
    </div>
  )
}
