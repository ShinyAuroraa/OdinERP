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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useHasRole } from '@/hooks/useHasRole'
import { useProducts, useDeleteProduct } from '@/lib/api/products'
import type { ProductWms, StorageType } from '@/types/product'
import { StorageTypeBadge } from './StorageTypeBadge'
import { ProductForm } from './ProductForm'

const STORAGE_TYPES: StorageType[] = ['DRY', 'REFRIGERATED', 'FROZEN', 'HAZARDOUS', 'OVERSIZED', 'CONTROLLED']

export function ProductList() {
  const isAdmin = useHasRole('WMS_ADMIN')
  const [storageTypeFilter, setStorageTypeFilter] = useState<StorageType | 'ALL'>('ALL')
  const { data: products, isLoading } = useProducts(
    storageTypeFilter !== 'ALL' ? { storageType: storageTypeFilter } : undefined,
  )
  const deleteProduct = useDeleteProduct()

  const [formOpen, setFormOpen] = useState(false)
  const [editingProduct, setEditingProduct] = useState<ProductWms | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<ProductWms | null>(null)

  const columns: ColumnDef<ProductWms>[] = [
    { accessorKey: 'sku', header: 'SKU' },
    { accessorKey: 'name', header: 'Nome' },
    {
      accessorKey: 'storageType',
      header: 'Tipo Armaz.',
      cell: ({ row }) => <StorageTypeBadge storageType={row.original.storageType} />,
    },
    {
      id: 'controls',
      header: 'Controles',
      cell: ({ row }) => {
        const p = row.original
        const controls = [
          p.controlsLot && 'Lote',
          p.controlsSerial && 'Serial',
          p.controlsExpiry && 'Validade',
          p.requiresSanitaryVigilance && 'ANVISA',
        ].filter(Boolean)
        return controls.length > 0 ? (
          <span className="text-xs text-muted-foreground">{controls.join(', ')}</span>
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        )
      },
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ row }) => <StatusBadge status={row.original.status} />,
    },
    ...(isAdmin
      ? [
          {
            id: 'actions',
            header: 'Ações',
            cell: ({ row }: { row: { original: ProductWms } }) => (
              <div className="flex gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => { setEditingProduct(row.original); setFormOpen(true) }}
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

  if (isLoading) return <LoadingState variant="page" />

  return (
    <>
      <PageHeader
        title="Produtos WMS"
        description="Gerencie o catálogo de produtos e seus atributos de armazenagem."
        actions={
          isAdmin ? (
            <Button onClick={() => { setEditingProduct(null); setFormOpen(true) }}>
              <Plus className="h-4 w-4 mr-2" />
              Novo Produto
            </Button>
          ) : undefined
        }
      />

      <div className="flex gap-4 mb-4">
        <Select
          value={storageTypeFilter}
          onValueChange={(v) => setStorageTypeFilter(v as StorageType | 'ALL')}
        >
          <SelectTrigger className="w-48">
            <SelectValue placeholder="Tipo de armazenagem" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Todos os tipos</SelectItem>
            {STORAGE_TYPES.map((type) => (
              <SelectItem key={type} value={type}>{type}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <DataTable
        columns={columns}
        data={products ?? []}
        searchable
        searchPlaceholder="Buscar por SKU ou nome..."
        pagination
        emptyMessage="Nenhum produto cadastrado."
      />

      {isAdmin && (
        <ProductForm
          open={formOpen}
          onOpenChange={(open) => { setFormOpen(open); if (!open) setEditingProduct(null) }}
          product={editingProduct}
        />
      )}

      <ConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title={`Deletar "${deleteTarget?.name}"?`}
        description="Esta ação não pode ser desfeita."
        confirmLabel="Deletar"
        variant="destructive"
        onConfirm={() => {
          if (!deleteTarget) return
          deleteProduct.mutate(deleteTarget.id, {
            onSuccess: () => { toast.success('Produto removido.'); setDeleteTarget(null) },
            onError: () => toast.error('Erro ao remover produto.'),
          })
        }}
      />
    </>
  )
}
