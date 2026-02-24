'use client'

import React from 'react'
import { Plus } from 'lucide-react'
import { DataTable } from '@/components/shared/DataTable'
import { PageHeader } from '@/components/shared/PageHeader'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useTransfers, useConfirmTransfer, useCancelTransfer } from '@/lib/api/transfers'
import { useHasRole } from '@/hooks/useHasRole'
import { toast } from 'sonner'
import { TransferModal } from './TransferModal'
import type { InternalTransfer, TransferStatus } from '@/types/transfers'
import type { ColumnDef } from '@tanstack/react-table'

const STATUS_LABELS: Record<TransferStatus, string> = {
  PENDING: 'Pendente', CONFIRMED: 'Confirmada', CANCELLED: 'Cancelada',
}

export function TransferList() {
  const [statusFilter, setStatusFilter] = React.useState('ALL')
  const [modalOpen, setModalOpen] = React.useState(false)
  const isOperator = useHasRole('WMS_OPERATOR')
  const isSupervisor = useHasRole('WMS_SUPERVISOR')

  const filters = statusFilter !== 'ALL' ? { status: statusFilter, size: 50 } : { size: 50 }
  const { data, isLoading } = useTransfers(filters)

  function RowActions({ transfer }: { transfer: InternalTransfer }) {
    const confirm = useConfirmTransfer(transfer.id)
    const cancel = useCancelTransfer(transfer.id)
    if (transfer.status !== 'PENDING') return null
    return (
      <div className="flex gap-1">
        {isSupervisor && (
          <Button variant="outline" size="sm" onClick={() => confirm.mutate({}, { onSuccess: () => toast.success('Transferência confirmada.'), onError: () => toast.error('Erro.') })} disabled={confirm.isPending}>
            Confirmar
          </Button>
        )}
        <Button variant="ghost" size="sm" onClick={() => cancel.mutate({}, { onSuccess: () => toast.success('Cancelada.'), onError: () => toast.error('Erro.') })} disabled={cancel.isPending}>
          Cancelar
        </Button>
      </div>
    )
  }

  const columns: ColumnDef<InternalTransfer>[] = [
    { accessorKey: 'productSku', header: 'SKU' },
    { accessorKey: 'productName', header: 'Produto' },
    { accessorKey: 'lotNumber', header: 'Lote', cell: ({ row }) => row.original.lotNumber ?? '—' },
    { accessorKey: 'quantity', header: 'Qtd.' },
    { accessorKey: 'fromLocationCode', header: 'Origem' },
    { accessorKey: 'toLocationCode', header: 'Destino' },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ row }) => <StatusBadge status={row.original.status} label={STATUS_LABELS[row.original.status]} />,
    },
    {
      accessorKey: 'createdAt',
      header: 'Data',
      cell: ({ row }) => new Date(row.original.createdAt).toLocaleDateString('pt-BR'),
    },
    {
      id: 'actions',
      header: 'Ações',
      cell: ({ row }) => <RowActions transfer={row.original} />,
    },
  ]

  return (
    <div className="p-6 space-y-4">
      <PageHeader
        title="Transferências Internas"
        description="Movimentação de estoque entre localizações"
        actions={
          isOperator ? (
            <Button onClick={() => setModalOpen(true)} size="sm">
              <Plus className="h-4 w-4 mr-2" />
              Nova Transferência
            </Button>
          ) : null
        }
      />
      <Select value={statusFilter} onValueChange={setStatusFilter}>
        <SelectTrigger className="w-48">
          <SelectValue placeholder="Filtrar por status" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="ALL">Todos</SelectItem>
          <SelectItem value="PENDING">Pendente</SelectItem>
          <SelectItem value="CONFIRMED">Confirmada</SelectItem>
          <SelectItem value="CANCELLED">Cancelada</SelectItem>
        </SelectContent>
      </Select>
      <DataTable
        columns={columns}
        data={data?.content ?? []}
        loading={isLoading}
        emptyMessage="Nenhuma transferência interna."
        searchPlaceholder="Buscar por produto..."
        searchable
      />
      <TransferModal open={modalOpen} onOpenChange={setModalOpen} />
    </div>
  )
}
