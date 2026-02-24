'use client'

import React from 'react'
import { useForm } from 'react-hook-form'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toast } from 'sonner'
import { useCreateShipment } from '@/lib/api/shipping'
import type { CreateShipmentRequest } from '@/types/shipping'

interface Props {
  open: boolean
  onClose: () => void
}

export function ShippingModal({ open, onClose }: Props) {
  const { register, handleSubmit, reset, formState: { errors } } = useForm<CreateShipmentRequest>()
  const create = useCreateShipment()

  function onSubmit(values: CreateShipmentRequest) {
    create.mutate(values, {
      onSuccess: () => {
        toast.success('Expedição criada com sucesso.')
        reset()
        onClose()
      },
      onError: () => toast.error('Erro ao criar expedição.'),
    })
  }

  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) { reset(); onClose() } }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Nova Expedição</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="packingOrderId">ID Ordem de Packing *</Label>
            <Input id="packingOrderId" {...register('packingOrderId', { required: 'Obrigatório' })} />
            {errors.packingOrderId && <p className="text-xs text-destructive">{errors.packingOrderId.message}</p>}
          </div>
          <div className="space-y-2">
            <Label htmlFor="carrier">Transportadora</Label>
            <Input id="carrier" {...register('carrier')} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="vehiclePlate">Placa do Veículo</Label>
            <Input id="vehiclePlate" {...register('vehiclePlate')} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="estimatedDelivery">Previsão de Entrega</Label>
            <Input id="estimatedDelivery" type="date" {...register('estimatedDelivery')} />
          </div>
          <div className="flex gap-2 justify-end">
            <Button type="button" variant="outline" onClick={() => { reset(); onClose() }}>Cancelar</Button>
            <Button type="submit" disabled={create.isPending}>
              {create.isPending ? 'Criando...' : 'Criar Expedição'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
