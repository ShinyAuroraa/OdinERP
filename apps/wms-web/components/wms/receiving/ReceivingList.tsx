'use client'

import React from 'react'
import { Plus } from 'lucide-react'
import { DataTable } from '@/components/shared/DataTable'
import { PageHeader } from '@/components/shared/PageHeader'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useReceivingNotes } from '@/lib/api/receiving'
import { useHasRole } from '@/hooks/useHasRole'
import { ReceivingForm } from './ReceivingForm'
import type { ReceivingNote, ReceivingNoteStatus } from '@/types/receiving'
import type { ColumnDef } from '@tanstack/react-table'
import Link from 'next/link'

const STATUS_LABELS: Record<ReceivingNoteStatus, string> = {
  PENDING: 'Pendente',
  IN_PROGRESS: 'Em Conferência',
  COMPLETED: 'Concluída',
  FLAGGED: 'Com Divergências',
}

const STATUS_VARIANTS: Record<ReceivingNoteStatus, 'default' | 'success' | 'warning' | 'destructive'> = {
  PENDING: 'default',
  IN_PROGRESS: 'warning',
  COMPLETED: 'success',
  FLAGGED: 'destructive',
}

export function ReceivingList() {
  const [statusFilter, setStatusFilter] = React.useState<string>('ALL')
  const [formOpen, setFormOpen] = React.useState(false)
  const isOperator = useHasRole('WMS_OPERATOR')

  const filters = statusFilter !== 'ALL' ? { status: statusFilter } : undefined
  const { data: notes, isLoading } = useReceivingNotes(filters)

  const columns: ColumnDef<ReceivingNote>[] = [
    {
      accessorKey: 'noteNumber',
      header: 'Nº Nota',
      cell: ({ row }) => (
        <Link href={`/receiving/${row.original.id}`} className="font-medium hover:underline text-primary">
          {row.original.noteNumber}
        </Link>
      ),
    },
    { accessorKey: 'warehouseName', header: 'Armazém' },
    {
      accessorKey: 'supplierId',
      header: 'Fornecedor',
      cell: ({ row }) => row.original.supplierId ?? '—',
    },
    {
      accessorKey: 'purchaseOrderRef',
      header: 'Ref. OC',
      cell: ({ row }) => row.original.purchaseOrderRef ?? '—',
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ row }) => (
        <StatusBadge
          status={row.original.status}
          label={STATUS_LABELS[row.original.status]}
          variant={STATUS_VARIANTS[row.original.status]}
        />
      ),
    },
    {
      accessorKey: 'createdAt',
      header: 'Data',
      cell: ({ row }) => new Date(row.original.createdAt).toLocaleDateString('pt-BR'),
    },
    {
      id: 'actions',
      header: 'Ações',
      cell: ({ row }) => (
        <Link href={`/receiving/${row.original.id}`}>
          <Button variant="outline" size="sm">Ver</Button>
        </Link>
      ),
    },
  ]

  return (
    <div className="p-6 space-y-4">
      <PageHeader
        title="Recebimento de Mercadorias"
        description="Gerencie notas de recebimento e o processo de conferência"
        actions={
          isOperator ? (
            <Button onClick={() => setFormOpen(true)} size="sm">
              <Plus className="h-4 w-4 mr-2" />
              Nova Nota
            </Button>
          ) : null
        }
      />

      <div className="flex gap-2">
        <Select value={statusFilter} onValueChange={setStatusFilter}>
          <SelectTrigger className="w-48">
            <SelectValue placeholder="Filtrar por status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Todos os status</SelectItem>
            <SelectItem value="PENDING">Pendente</SelectItem>
            <SelectItem value="IN_PROGRESS">Em Conferência</SelectItem>
            <SelectItem value="COMPLETED">Concluída</SelectItem>
            <SelectItem value="FLAGGED">Com Divergências</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <DataTable
        columns={columns}
        data={notes ?? []}
        loading={isLoading}
        emptyMessage="Nenhuma nota de recebimento cadastrada."
        searchPlaceholder="Buscar por número ou fornecedor..."
        searchable
      />

      <ReceivingForm open={formOpen} onOpenChange={setFormOpen} />
    </div>
  )
}
