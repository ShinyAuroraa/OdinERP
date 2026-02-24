'use client'

import React, { useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import { Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { DataTable } from '@/components/shared/DataTable'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { ConfirmDialog } from '@/components/shared/ConfirmDialog'
import { LoadingState } from '@/components/shared/LoadingState'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Form } from '@/components/ui/form'
import { WmsFormField } from '@/components/shared/FormField'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useHasRole } from '@/hooks/useHasRole'
import { useZones, useCreateZone, useDeleteZone } from '@/lib/api/warehouses'
import type { Zone } from '@/types/warehouse'

const schema = z.object({
  code: z.string().min(1, 'Código obrigatório'),
  name: z.string().min(1, 'Nome obrigatório'),
  description: z.string().optional(),
})
type FormData = z.infer<typeof schema>

interface ZoneListProps {
  warehouseId: string
}

export function ZoneList({ warehouseId }: ZoneListProps) {
  const isAdmin = useHasRole('WMS_ADMIN')
  const { data: zones, isLoading } = useZones(warehouseId)
  const createZone = useCreateZone(warehouseId)
  const deleteZone = useDeleteZone(warehouseId)
  const [formOpen, setFormOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<Zone | null>(null)

  const form = useForm<FormData>({ resolver: zodResolver(schema), defaultValues: { code: '', name: '', description: '' } })

  const columns: ColumnDef<Zone>[] = [
    { accessorKey: 'code', header: 'Código' },
    { accessorKey: 'name', header: 'Nome' },
    { accessorKey: 'status', header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
    { accessorKey: 'aislesCount', header: 'Corredores', cell: ({ row }) => row.original.aislesCount ?? '—' },
    ...(isAdmin
      ? [{
          id: 'actions', header: 'Ações',
          cell: ({ row }: { row: { original: Zone } }) => (
            <Button variant="ghost" size="sm" onClick={() => setDeleteTarget(row.original)}>
              <Trash2 className="h-4 w-4 text-destructive" />
            </Button>
          ),
        }]
      : []),
  ]

  if (isLoading) return <LoadingState variant="skeleton" rows={3} />

  function onSubmit(data: FormData) {
    createZone.mutate(data, {
      onSuccess: () => { toast.success('Zona criada.'); setFormOpen(false); form.reset() },
      onError: () => toast.error('Erro ao criar zona.'),
    })
  }

  return (
    <div className="space-y-4">
      {isAdmin && (
        <div className="flex justify-end">
          <Button onClick={() => setFormOpen(true)} size="sm">
            <Plus className="h-4 w-4 mr-2" /> Adicionar Zona
          </Button>
        </div>
      )}
      <DataTable columns={columns} data={zones ?? []} emptyMessage="Nenhuma zona cadastrada." />

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader><DialogTitle>Nova Zona</DialogTitle></DialogHeader>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
              <WmsFormField name="code" label="Código" required><Input placeholder="ZN-01" /></WmsFormField>
              <WmsFormField name="name" label="Nome" required><Input placeholder="Zona A" /></WmsFormField>
              <WmsFormField name="description" label="Descrição"><Input placeholder="Opcional" /></WmsFormField>
              <div className="flex justify-end gap-2">
                <Button type="button" variant="outline" onClick={() => setFormOpen(false)}>Cancelar</Button>
                <Button type="submit" disabled={createZone.isPending}>{createZone.isPending ? 'Criando...' : 'Criar'}</Button>
              </div>
            </form>
          </Form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title={`Deletar zona "${deleteTarget?.name}"?`}
        description="Esta ação não pode ser desfeita."
        confirmLabel="Deletar"
        variant="destructive"
        onConfirm={() => {
          if (!deleteTarget) return
          deleteZone.mutate(deleteTarget.id, {
            onSuccess: () => { toast.success('Zona removida.'); setDeleteTarget(null) },
            onError: () => toast.error('Erro ao remover zona.'),
          })
        }}
      />
    </div>
  )
}
