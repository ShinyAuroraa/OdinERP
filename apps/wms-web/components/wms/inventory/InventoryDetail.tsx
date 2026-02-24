'use client'

import React from 'react'
import { PageHeader } from '@/components/shared/PageHeader'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { toast } from 'sonner'
import {
  useInventoryCount, useCountItems, useStartCount,
  useReconcile, useApproveCount, useCloseCount,
} from '@/lib/api/inventory'
import { useHasRole } from '@/hooks/useHasRole'
import { InventoryCountModal } from './InventoryCountModal'
import type { InventoryCountItem, InventoryCountStatus } from '@/types/inventory'

const STATUS_LABELS: Record<InventoryCountStatus, string> = {
  CREATED: 'Criada', IN_PROGRESS: 'Em Contagem', RECONCILED: 'Reconciliada',
  APPROVED: 'Aprovada', CLOSED: 'Fechada',
}

interface Props { countId: string }

export function InventoryDetail({ countId }: Props) {
  const { data: count, isLoading } = useInventoryCount(countId)
  const { data: itemsPage } = useCountItems(countId)
  const isSupervisor = useHasRole('WMS_SUPERVISOR')
  const startCount = useStartCount(countId)
  const reconcile = useReconcile(countId)
  const approve = useApproveCount(countId)
  const close = useCloseCount(countId)
  const [selectedItem, setSelectedItem] = React.useState<InventoryCountItem | null>(null)
  const [modalOpen, setModalOpen] = React.useState(false)

  if (isLoading) return <div className="p-6"><div className="animate-pulse h-64 bg-muted rounded-lg" /></div>
  if (!count) return <div className="p-6 text-muted-foreground">Sessão não encontrada.</div>

  const items = itemsPage?.content ?? []
  const allCounted = items.length > 0 && items.every((i) => i.status !== 'PENDING')

  function handleCountItem(item: InventoryCountItem) {
    setSelectedItem(item)
    setModalOpen(true)
  }

  return (
    <div className="p-6 space-y-4">
      <PageHeader
        title={`Inventário ${count.id.slice(0, 8)}...`}
        description={`Armazém: ${count.warehouseId} · ${new Date(count.createdAt).toLocaleDateString('pt-BR')}`}
        actions={<StatusBadge status={count.status} label={STATUS_LABELS[count.status]} />}
      />

      {/* Actions */}
      <div className="flex gap-2 flex-wrap">
        {count.status === 'CREATED' && isSupervisor && (
          <Button onClick={() => startCount.mutate(undefined, { onSuccess: () => toast.success('Contagem iniciada.'), onError: () => toast.error('Erro.') })} disabled={startCount.isPending}>
            Iniciar Contagem
          </Button>
        )}
        {count.status === 'IN_PROGRESS' && allCounted && isSupervisor && (
          <Button onClick={() => reconcile.mutate(undefined, { onSuccess: () => toast.success('Reconciliação concluída.'), onError: () => toast.error('Erro.') })} disabled={reconcile.isPending}>
            Reconciliar
          </Button>
        )}
        {count.status === 'RECONCILED' && isSupervisor && (
          <Button onClick={() => approve.mutate(undefined, { onSuccess: () => toast.success('Ajustes aprovados.'), onError: () => toast.error('Erro.') })} disabled={approve.isPending}>
            Aprovar Ajustes
          </Button>
        )}
        {count.status === 'APPROVED' && isSupervisor && (
          <Button variant="outline" onClick={() => close.mutate(undefined, { onSuccess: () => toast.success('Inventário fechado.'), onError: () => toast.error('Erro.') })} disabled={close.isPending}>
            Fechar Inventário
          </Button>
        )}
      </div>

      {/* Summary */}
      <div className="grid grid-cols-3 gap-3">
        <Card>
          <CardContent className="pt-4 text-center">
            <p className="text-2xl font-bold">{count.totalItems}</p>
            <p className="text-xs text-muted-foreground">Total</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4 text-center">
            <p className="text-2xl font-bold text-green-600">{count.countedItems}</p>
            <p className="text-xs text-muted-foreground">Contados</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4 text-center">
            <p className="text-2xl font-bold text-destructive">{count.divergentItems}</p>
            <p className="text-xs text-muted-foreground">Divergências</p>
          </CardContent>
        </Card>
      </div>

      {/* Items */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Itens ({items.length})</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          {items.map((item) => (
            <div key={item.id} className="flex items-center justify-between p-3 rounded-lg border">
              <div>
                <p className="font-medium text-sm">{item.productSku} — {item.productName}</p>
                <p className="text-xs text-muted-foreground">
                  {item.locationCode}{item.lotNumber ? ` · Lote: ${item.lotNumber}` : ''}
                  {' · '}Esperado: {item.expectedQuantity}
                  {item.countedQuantity !== undefined ? ` · Contado: ${item.countedQuantity}` : ''}
                </p>
              </div>
              <div className="flex items-center gap-2">
                {item.divergence !== undefined && item.divergence !== 0 && (
                  <Badge variant={item.divergence > 0 ? 'secondary' : 'destructive'}>
                    {item.divergence > 0 ? '+' : ''}{item.divergence}
                  </Badge>
                )}
                {item.status === 'PENDING' && count.status === 'IN_PROGRESS' && (
                  <Button variant="outline" size="sm" onClick={() => handleCountItem(item)}>Contar</Button>
                )}
              </div>
            </div>
          ))}
          {items.length === 0 && <p className="text-sm text-muted-foreground">Nenhum item na sessão.</p>}
        </CardContent>
      </Card>

      <InventoryCountModal countId={countId} item={selectedItem} open={modalOpen} onOpenChange={setModalOpen} />
    </div>
  )
}
