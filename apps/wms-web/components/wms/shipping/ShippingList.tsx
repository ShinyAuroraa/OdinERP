'use client'

import React from 'react'
import { type ColumnDef } from '@tanstack/react-table'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable } from '@/components/shared/DataTable'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Button } from '@/components/ui/button'
import { toast } from 'sonner'
import { useShipments, useShipOrder, useCancelShipment } from '@/lib/api/shipping'
import { ShippingModal } from './ShippingModal'
import type { Shipment, ShipmentStatus } from '@/types/shipping'
import { useHasRole } from '@/hooks/useHasRole'

const STATUS_LABELS: Record<ShipmentStatus, string> = {
  PENDING: 'Pendente',
  SHIPPED: 'Despachado',
  DELIVERED: 'Entregue',
  CANCELLED: 'Cancelado',
}

function ActionButtons({ shipment }: { shipment: Shipment }) {
  const ship = useShipOrder(shipment.id)
  const cancel = useCancelShipment(shipment.id)
  const isSupervisor = useHasRole('WMS_SUPERVISOR')

  return (
    <div className="flex gap-1">
      {shipment.status === 'PENDING' && (
        <Button size="sm" variant="outline" disabled={ship.isPending}
          onClick={() => ship.mutate(undefined, {
            onSuccess: () => toast.success('Despacho realizado.'),
            onError: () => toast.error('Erro ao despachar.'),
          })}>
          Despachar
        </Button>
      )}
      {isSupervisor && shipment.status === 'PENDING' && (
        <Button size="sm" variant="ghost" disabled={cancel.isPending}
          onClick={() => cancel.mutate(undefined, {
            onSuccess: () => toast.success('Expedição cancelada.'),
            onError: () => toast.error('Erro ao cancelar.'),
          })}>
          Cancelar
        </Button>
      )}
    </div>
  )
}

export function ShippingList() {
  const [modalOpen, setModalOpen] = React.useState(false)
  const [status, setStatus] = React.useState<ShipmentStatus | 'ALL'>('ALL')
  const { data, isLoading } = useShipments(status !== 'ALL' ? { status } : undefined)
  const shipments = data?.content ?? []

  const columns: ColumnDef<Shipment>[] = [
    { accessorKey: 'shippingNumber', header: 'Expedição' },
    { accessorKey: 'packingOrderId', header: 'Packing' },
    { accessorKey: 'carrier', header: 'Transportadora', cell: ({ row }) => row.original.carrier ?? '—' },
    { accessorKey: 'vehiclePlate', header: 'Veículo', cell: ({ row }) => row.original.vehiclePlate ?? '—' },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ row }) => (
        <StatusBadge status={row.original.status} label={STATUS_LABELS[row.original.status]} />
      ),
    },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) => <ActionButtons shipment={row.original} />,
    },
  ]

  if (isLoading) {
    return <div className="p-6"><div className="animate-pulse h-64 bg-muted rounded-lg" /></div>
  }

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-start justify-between">
        <PageHeader title="Shipping" description="Expedição e despacho de ordens" />
        <Button onClick={() => setModalOpen(true)}>Nova Expedição</Button>
      </div>
      <div className="flex gap-3">
        <Select value={status} onValueChange={(v) => setStatus(v as ShipmentStatus | 'ALL')}>
          <SelectTrigger className="w-48">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Todos</SelectItem>
            <SelectItem value="PENDING">Pendente</SelectItem>
            <SelectItem value="SHIPPED">Despachado</SelectItem>
            <SelectItem value="DELIVERED">Entregue</SelectItem>
            <SelectItem value="CANCELLED">Cancelado</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <DataTable
        columns={columns}
        data={shipments}
        loading={isLoading}
        emptyMessage="Nenhuma expedição encontrada."
      />
      <ShippingModal open={modalOpen} onClose={() => setModalOpen(false)} />
    </div>
  )
}
