'use client'

import React from 'react'
import { DataTable } from '@/components/shared/DataTable'
import { PageHeader } from '@/components/shared/PageHeader'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useQuarantineTasks, useStartQuarantine } from '@/lib/api/putaway'
import { useHasRole } from '@/hooks/useHasRole'
import { toast } from 'sonner'
import { QuarantineDecideModal } from './QuarantineDecideModal'
import type { QuarantineTask, QuarantineTaskStatus } from '@/types/putaway'
import type { ColumnDef } from '@tanstack/react-table'

const STATUS_LABELS: Record<QuarantineTaskStatus, string> = {
  PENDING: 'Pendente', IN_PROGRESS: 'Em Revisão', DECIDED: 'Decidido', CANCELLED: 'Cancelado',
}
const STATUS_VARIANTS: Record<QuarantineTaskStatus, 'default' | 'success' | 'warning' | 'destructive'> = {
  PENDING: 'default', IN_PROGRESS: 'warning', DECIDED: 'success', CANCELLED: 'destructive',
}

export function QuarantineList() {
  const [statusFilter, setStatusFilter] = React.useState('ALL')
  const [decideTask, setDecideTask] = React.useState<QuarantineTask | null>(null)
  const isSupervisor = useHasRole('WMS_SUPERVISOR')
  const startQuarantine = useStartQuarantine()

  const filters = statusFilter !== 'ALL' ? { status: statusFilter } : undefined
  const { data: tasks, isLoading } = useQuarantineTasks(filters)

  function handleStart(task: QuarantineTask) {
    startQuarantine.mutate(task.id, {
      onSuccess: () => { toast.success('Revisão iniciada.'); setDecideTask(task) },
      onError: () => toast.error('Erro ao iniciar revisão.'),
    })
  }

  const columns: ColumnDef<QuarantineTask>[] = [
    { accessorKey: 'productSku', header: 'Produto' },
    { accessorKey: 'productName', header: 'Nome' },
    { accessorKey: 'lotNumber', header: 'Lote', cell: ({ row }) => row.original.lotNumber ?? '—' },
    { accessorKey: 'quantity', header: 'Qtd.' },
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
        if (task.status === 'PENDING' && isSupervisor) {
          return (
            <Button variant="outline" size="sm" onClick={() => handleStart(task)} disabled={startQuarantine.isPending}>
              Iniciar Revisão
            </Button>
          )
        }
        if (task.status === 'IN_PROGRESS' && isSupervisor) {
          return (
            <Button variant="outline" size="sm" onClick={() => setDecideTask(task)}>
              Decidir
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
        title="Fila de Quarentena"
        description="Gerencie itens em quarentena — libere, devolva ou sucate"
      />
      <Select value={statusFilter} onValueChange={setStatusFilter}>
        <SelectTrigger className="w-48">
          <SelectValue placeholder="Filtrar por status" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="ALL">Todos</SelectItem>
          <SelectItem value="PENDING">Pendente</SelectItem>
          <SelectItem value="IN_PROGRESS">Em Revisão</SelectItem>
          <SelectItem value="DECIDED">Decidido</SelectItem>
          <SelectItem value="CANCELLED">Cancelado</SelectItem>
        </SelectContent>
      </Select>
      <DataTable
        columns={columns}
        data={tasks ?? []}
        loading={isLoading}
        emptyMessage="Nenhuma tarefa de quarentena pendente."
        searchPlaceholder="Buscar por produto ou lote..."
        searchable
      />
      <QuarantineDecideModal
        task={decideTask}
        open={!!decideTask}
        onOpenChange={(open) => !open && setDecideTask(null)}
      />
    </div>
  )
}
