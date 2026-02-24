'use client'

import React from 'react'
import { PageHeader } from '@/components/shared/PageHeader'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { toast } from 'sonner'
import { usePickingOrder, usePickingItems, useCompletePickingOrder, useCancelPickingOrder } from '@/lib/api/picking'
import { PickingConfirmModal } from './PickingConfirmModal'
import type { PickingItem } from '@/types/picking'
import { useHasRole } from '@/hooks/useHasRole'

interface Props {
  orderId: string
}

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendente',
  IN_PROGRESS: 'Em Andamento',
  COMPLETED: 'Concluído',
  CANCELLED: 'Cancelado',
  PICKED: 'Coletado',
  PARTIAL: 'Parcial',
  SKIPPED: 'Ignorado',
}

export function PickingDetail({ orderId }: Props) {
  const [selectedItem, setSelectedItem] = React.useState<PickingItem | null>(null)
  const { data: order, isLoading } = usePickingOrder(orderId)
  const { data: items = [] } = usePickingItems(orderId)
  const complete = useCompletePickingOrder(orderId)
  const cancel = useCancelPickingOrder(orderId)
  const isSupervisor = useHasRole('WMS_SUPERVISOR')

  if (isLoading) {
    return <div className="p-6"><div className="animate-pulse h-64 bg-muted rounded-lg" /></div>
  }
  if (!order) {
    return <div className="p-6 text-muted-foreground">Ordem não encontrada.</div>
  }

  function handleComplete() {
    complete.mutate(undefined, {
      onSuccess: () => toast.success('Ordem completada.'),
      onError: () => toast.error('Erro ao completar ordem.'),
    })
  }

  function handleCancel() {
    cancel.mutate(undefined, {
      onSuccess: () => toast.success('Ordem cancelada.'),
      onError: () => toast.error('Erro ao cancelar ordem.'),
    })
  }

  const allPicked = items.length > 0 && items.every((i) => i.status !== 'PENDING')

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-start justify-between">
        <PageHeader
          title={`Picking ${order.orderNumber}`}
          description={order.customerName ?? order.productionOrderId ?? ''}
        />
        <div className="flex gap-2">
          {order.status === 'IN_PROGRESS' && allPicked && (
            <Button onClick={handleComplete} disabled={complete.isPending}>Completar Ordem</Button>
          )}
          {isSupervisor && order.status !== 'COMPLETED' && order.status !== 'CANCELLED' && (
            <Button variant="destructive" onClick={handleCancel} disabled={cancel.isPending}>Cancelar</Button>
          )}
        </div>
      </div>

      <div className="flex gap-3 items-center">
        <StatusBadge status={order.status} label={STATUS_LABELS[order.status]} />
        <span className="text-sm text-muted-foreground">Prioridade: {order.priority}</span>
        <span className="text-sm text-muted-foreground">{order.pickedItems}/{order.totalItems} itens</span>
      </div>

      <Card>
        <CardHeader><CardTitle className="text-base">Itens da Ordem</CardTitle></CardHeader>
        <CardContent>
          {items.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nenhum item encontrado.</p>
          ) : (
            <div className="space-y-3">
              {items.map((item) => (
                <div key={item.id} className="flex items-center justify-between border rounded-lg p-3">
                  <div className="space-y-1">
                    <p className="font-medium text-sm">{item.productSku} — {item.productName}</p>
                    <p className="text-xs text-muted-foreground">
                      Local: {item.locationCode}
                      {item.lotNumber && ` | Lote: ${item.lotNumber}`}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      Solicitado: {item.requestedQuantity} | Coletado: {item.pickedQuantity}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge variant={item.status === 'PICKED' ? 'default' : 'secondary'}>
                      {STATUS_LABELS[item.status] ?? item.status}
                    </Badge>
                    {item.status === 'PENDING' && order.status !== 'COMPLETED' && (
                      <Button size="sm" variant="outline" onClick={() => setSelectedItem(item)}>
                        Confirmar
                      </Button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <PickingConfirmModal
        orderId={orderId}
        item={selectedItem}
        open={!!selectedItem}
        onClose={() => setSelectedItem(null)}
      />
    </div>
  )
}
