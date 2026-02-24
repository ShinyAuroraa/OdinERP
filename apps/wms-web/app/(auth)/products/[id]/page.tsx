'use client'

import React, { useState } from 'react'
import { useParams } from 'next/navigation'
import { ArrowLeft } from 'lucide-react'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/shared/PageHeader'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { LoadingState } from '@/components/shared/LoadingState'
import { ErrorBoundary } from '@/components/shared/ErrorBoundary'
import { ConfirmDialog } from '@/components/shared/ConfirmDialog'
import { useHasRole } from '@/hooks/useHasRole'
import { useProduct, useDeleteProduct } from '@/lib/api/products'
import { ProductDetail } from '@/components/wms/products/ProductDetail'
import { ProductForm } from '@/components/wms/products/ProductForm'
import { toast } from 'sonner'
import { useRouter } from 'next/navigation'

export default function ProductDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { data: product, isLoading } = useProduct(id)
  const isAdmin = useHasRole('WMS_ADMIN')
  const deleteProduct = useDeleteProduct()
  const router = useRouter()
  const [editOpen, setEditOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)

  if (isLoading) return <LoadingState variant="page" />
  if (!product) return <p className="text-muted-foreground">Produto não encontrado.</p>

  return (
    <ErrorBoundary>
      <div className="space-y-6">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="sm" asChild>
            <Link href="/products">
              <ArrowLeft className="h-4 w-4 mr-1" />
              Produtos
            </Link>
          </Button>
        </div>

        <PageHeader
          title={product.name}
          description={`SKU: ${product.sku}`}
          actions={
            <div className="flex items-center gap-2">
              <StatusBadge status={product.status} />
              {isAdmin && (
                <>
                  <Button variant="outline" size="sm" onClick={() => setEditOpen(true)}>
                    Editar
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => setDeleteOpen(true)} className="text-destructive">
                    Desativar
                  </Button>
                </>
              )}
            </div>
          }
        />

        <ProductDetail product={product} />

        {isAdmin && (
          <>
            <ProductForm open={editOpen} onOpenChange={setEditOpen} product={product} />
            <ConfirmDialog
              open={deleteOpen}
              onOpenChange={setDeleteOpen}
              title={`Deletar "${product.name}"?`}
              description="Esta ação não pode ser desfeita."
              confirmLabel="Deletar"
              variant="destructive"
              onConfirm={() => {
                deleteProduct.mutate(product.id, {
                  onSuccess: () => { toast.success('Produto removido.'); router.push('/products') },
                  onError: () => toast.error('Erro ao remover produto.'),
                })
              }}
            />
          </>
        )}
      </div>
    </ErrorBoundary>
  )
}
