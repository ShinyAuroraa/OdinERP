'use client'

import React, { useState } from 'react'
import type { ColumnDef } from '@tanstack/react-table'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { DataTable } from '@/components/shared/DataTable'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { ConfirmDialog } from '@/components/shared/ConfirmDialog'
import { LoadingState } from '@/components/shared/LoadingState'
import { PageHeader } from '@/components/shared/PageHeader'
import { Button } from '@/components/ui/button'
import { useHasRole } from '@/hooks/useHasRole'
import { useWarehouses, useDeleteWarehouse } from '@/lib/api/warehouses'
import type { Warehouse } from '@/types/warehouse'
import { WarehouseForm } from './WarehouseForm'

export function WarehouseList() {
  const isAdmin = useHasRole('WMS_ADMIN')
  const { data: warehouses, isLoading } = useWarehouses()
  const deleteWarehouse = useDeleteWarehouse()

  const [formOpen, setFormOpen] = useState(false)
  const [editingWarehouse, setEditingWarehouse] = useState<Warehouse | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Warehouse | null>(null)

  const columns: ColumnDef<Warehouse>[] = [
    { accessorKey: 'code', header: 'Código' },
    { accessorKey: 'name', header: 'Nome' },
    { accessorKey: 'location', header: 'Localização' },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ row }) => <StatusBadge status={row.original.status} />,
    },
    {
      accessorKey: 'zonesCount',
      header: 'Zonas',
      cell: ({ row }) => row.original.zonesCount ?? '—',
    },
    ...(isAdmin
      ? [
          {
            id: 'actions',
            header: 'Ações',
            cell: ({ row }: { row: { original: Warehouse } }) => (
              <div className="flex gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    setEditingWarehouse(row.original)
                    setFormOpen(true)
                  }}
                  aria-label={`Editar ${row.original.name}`}
                >
                  <Pencil className="h-4 w-4" />
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setDeleteTarget(row.original)}
                  aria-label={`Deletar ${row.original.name}`}
                >
                  <Trash2 className="h-4 w-4 text-destructive" />
                </Button>
              </div>
            ),
          },
        ]
      : []),
  ]

  if (isLoading) {
    return <LoadingState variant="page" />
  }

  return (
    <>
      <PageHeader
        title="Armazéns"
        description="Gerencie a estrutura física dos armazéns do tenant."
        actions={
          isAdmin ? (
            <Button
              onClick={() => {
                setEditingWarehouse(null)
                setFormOpen(true)
              }}
            >
              <Plus className="h-4 w-4 mr-2" />
              Novo Armazém
            </Button>
          ) : undefined
        }
      />

      <DataTable
        columns={columns}
        data={warehouses ?? []}
        searchable
        searchPlaceholder="Buscar armazém..."
        pagination
        emptyMessage="Nenhum armazém cadastrado."
      />

      {isAdmin && (
        <WarehouseForm
          open={formOpen}
          onOpenChange={(open) => {
            setFormOpen(open)
            if (!open) setEditingWarehouse(null)
          }}
          warehouse={editingWarehouse}
        />
      )}

      <ConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title={`Deletar "${deleteTarget?.name}"?`}
        description="Esta ação não pode ser desfeita. Todos os dados do armazém serão removidos."
        confirmLabel="Deletar"
        variant="destructive"
        onConfirm={() => {
          if (!deleteTarget) return
          deleteWarehouse.mutate(deleteTarget.id, {
            onSuccess: () => {
              toast.success(`Armazém "${deleteTarget.name}" removido.`)
              setDeleteTarget(null)
            },
            onError: () => {
              toast.error('Erro ao remover armazém.')
            },
          })
        }}
      />
    </>
  )
}
