'use client'

import React from 'react'
import { DataTable } from '@/components/shared/DataTable'
import { PageHeader } from '@/components/shared/PageHeader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useAuditLog } from '@/lib/api/audit'
import { useHasRole } from '@/hooks/useHasRole'
import { apiClient } from '@/lib/api/client'
import { toast } from 'sonner'
import type { AuditLogEntry } from '@/types/audit'
import type { ColumnDef } from '@tanstack/react-table'

const columns: ColumnDef<AuditLogEntry>[] = [
  {
    accessorKey: 'createdAt',
    header: 'Data/Hora',
    cell: ({ row }) => new Date(row.original.createdAt).toLocaleString('pt-BR'),
  },
  { accessorKey: 'entityType', header: 'Entidade' },
  { accessorKey: 'actionType', header: 'Ação' },
  { accessorKey: 'performedBy', header: 'Usuário' },
  {
    id: 'details',
    header: 'Detalhes',
    cell: ({ row }) => row.original.details
      ? <span className="text-xs text-muted-foreground">{JSON.stringify(row.original.details).slice(0, 60)}...</span>
      : '—',
  },
]

export function AuditLogList() {
  const isAdmin = useHasRole('WMS_ADMIN')
  const [entityType, setEntityType] = React.useState('ALL')
  const [from, setFrom] = React.useState('')
  const [to, setTo] = React.useState('')

  const filters = {
    entityType: entityType !== 'ALL' ? entityType : undefined,
    from: from || undefined,
    to: to || undefined,
    size: 50,
  }
  const { data, isLoading } = useAuditLog(filters)

  async function handleExport() {
    try {
      const qs = new URLSearchParams()
      if (from) qs.set('from', from)
      if (to) qs.set('to', to)
      await apiClient(`/audit/log/export${qs.toString() ? `?${qs}` : ''}`)
      toast.success('Exportação iniciada.')
    } catch {
      toast.error('Erro ao exportar log de auditoria.')
    }
  }

  return (
    <div className="p-6 space-y-4">
      <PageHeader
        title="Log de Auditoria"
        description="Registro imutável de todas as operações"
        actions={
          isAdmin ? (
            <Button variant="outline" size="sm" onClick={handleExport}>Exportar JSON</Button>
          ) : null
        }
      />
      <div className="flex gap-2 flex-wrap">
        <Select value={entityType} onValueChange={setEntityType}>
          <SelectTrigger className="w-48">
            <SelectValue placeholder="Tipo de entidade" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Todas as entidades</SelectItem>
            <SelectItem value="STOCK_MOVEMENT">Movimentos</SelectItem>
            <SelectItem value="RECEIVING_NOTE">Recebimento</SelectItem>
            <SelectItem value="PUTAWAY_TASK">Putaway</SelectItem>
            <SelectItem value="QUARANTINE_TASK">Quarentena</SelectItem>
            <SelectItem value="TRANSFER">Transferências</SelectItem>
            <SelectItem value="INVENTORY_COUNT">Inventário</SelectItem>
          </SelectContent>
        </Select>
        <div className="flex items-center gap-1">
          <Label className="text-xs whitespace-nowrap">De:</Label>
          <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className="w-36" />
        </div>
        <div className="flex items-center gap-1">
          <Label className="text-xs whitespace-nowrap">Até:</Label>
          <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} className="w-36" />
        </div>
      </div>
      <DataTable
        columns={columns}
        data={data?.content ?? []}
        loading={isLoading}
        emptyMessage="Nenhum registro de auditoria."
        searchPlaceholder="Buscar..."
        searchable
      />
    </div>
  )
}
