'use client'

import React from 'react'
import { DataTable } from '@/components/shared/DataTable'
import { PageHeader } from '@/components/shared/PageHeader'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { usePutawayTasks, useStartPutaway } from '@/lib/api/putaway'
import { useHasRole } from '@/hooks/useHasRole'
import { toast } from 'sonner'
import { PutawayConfirmModal } from './PutawayConfirmModal'
import type { PutawayTask, PutawayTaskStatus } from '@/types/putaway'
import type { ColumnDef } from '@tanstack/react-table'

const STATUS_LABELS: Record<PutawayTaskStatus, string> = {
  PENDING: 'Pendente', IN_PROGRESS: 'Em Andamento', COMPLETED: 'Concluído', CANCELLED: 'Cancelado',
}
const STATUS_VARIANTS: Record<PutawayTaskStatus, 'default' | 'success' | 'warning' | 'destructive'> = {
  PENDING: 'default', IN_PROGRESS: 'warning', COMPLETED: 'success', CANCELLED: 'destructive',
}

export function PutawayList() {
  const [statusFilter, setStatusFilter] = React.useState('ALL')
  const [confirmTask, setConfirmTask] = React.useState<PutawayTask | null>(null)
  const isOperator = useHasRole('WMS_OPERATOR')
  const startPutaway = useStartPutaway()

  const filters = statusFilter !== 'ALL' ? { status: statusFilter } : undefined
  const { data: tasks, isLoading } = usePutawayTasks(filters)

  function handleStart(task: PutawayTask) {
    startPutaway.mutate(task.id, {
      onSuccess: () => { toast.success('Tarefa iniciada.'); setConfirmTask(task) },
      onError: () => toast.error('Erro ao iniciar tarefa.'),
    })
  }

  const columns: ColumnDef<PutawayTask>[] = [
    { accessorKey: 'productSku', header: 'Produto' },
    { accessorKey: 'productName', header: 'Nome' },
    { accessorKey: 'lotNumber', header: 'Lote', cell: ({ row }) => row.original.lotNumber ?? '—' },
    { accessorKey: 'quantity', header: 'Qtd.' },
    {
      accessorKey: 'suggestedLocationCode',
      header: 'Localização Sugerida',
      cell: ({ row }) => row.original.suggestedLocationCode ?? '—',
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ row }) => (
        <StatusBadge status={row.original.status} label={STATUS_LABELS[row.original.status]} variant={STATUS_VARIANTS[row.original.status]} />
      ),
    },
    {
      id: 'actions',
      header: 'Ações',
      cell: ({ row }) => {
        const task = row.original
        if (task.status === 'PENDING' && isOperator) {
          return (
            <Button variant="outline" size="sm" onClick={() => handleStart(task)} disabled={startPutaway.isPending}>
              Iniciar
            </Button>
          )
        }
        if (task.status === 'IN_PROGRESS' && isOperator) {
          return (
            <Button variant="outline" size="sm" onClick={() => setConfirmTask(task)}>
              Confirmar
            </Button>
          )
        }
        return null
      },
    },
  ]

  return (
    <div className="p-6 space-y-4">
      <PageHeader
        title="Fila de Putaway"
        description="Aloque mercadorias recebidas nas localizações do armazém"
      />
      <Select value={statusFilter} onValueChange={setStatusFilter}>
        <SelectTrigger className="w-48">
          <SelectValue placeholder="Filtrar por status" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="ALL">Todos</SelectItem>
          <SelectItem value="PENDING">Pendente</SelectItem>
          <SelectItem value="IN_PROGRESS">Em Andamento</SelectItem>
          <SelectItem value="COMPLETED">Concluído</SelectItem>
          <SelectItem value="CANCELLED">Cancelado</SelectItem>
        </SelectContent>
      </Select>
      <DataTable
        columns={columns}
        data={tasks ?? []}
        loading={isLoading}
        emptyMessage="Nenhuma tarefa de putaway pendente."
        searchPlaceholder="Buscar por produto ou lote..."
        searchable
      />
      <PutawayConfirmModal task={confirmTask} open={!!confirmTask} onOpenChange={(open) => !open && setConfirmTask(null)} />
    </div>
  )
}
