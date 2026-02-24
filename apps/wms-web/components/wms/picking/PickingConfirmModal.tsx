'use client'

import React from 'react'
import { useForm } from 'react-hook-form'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toast } from 'sonner'
import { useConfirmPickItem } from '@/lib/api/picking'
import type { PickingItem } from '@/types/picking'

interface Props {
  orderId: string
  item: PickingItem | null
  open: boolean
  onClose: () => void
}

interface FormValues {
  pickedQuantity: number
}

export function PickingConfirmModal({ orderId, item, open, onClose }: Props) {
  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>()
  const confirm = useConfirmPickItem(orderId, item?.id ?? '')

  function onSubmit(values: FormValues) {
    confirm.mutate(
      { pickedQuantity: Number(values.pickedQuantity) },
      {
        onSuccess: () => {
          toast.success('Coleta confirmada.')
          reset()
          onClose()
        },
        onError: () => toast.error('Erro ao confirmar coleta.'),
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) { reset(); onClose() } }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Confirmar Coleta</DialogTitle>
        </DialogHeader>
        {item && (
          <p className="text-sm text-muted-foreground">
            {item.productSku} — {item.productName} | Localização: {item.locationCode}
            {item.lotNumber && ` | Lote: ${item.lotNumber}`}
          </p>
        )}
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="pickedQuantity">Quantidade coletada *</Label>
            <Input
              id="pickedQuantity"
              type="number"
              min={0}
              {...register('pickedQuantity', { required: 'Obrigatório', min: { value: 0, message: 'Mínimo 0' } })}
            />
            {errors.pickedQuantity && <p className="text-xs text-destructive">{errors.pickedQuantity.message}</p>}
          </div>
          <div className="flex gap-2 justify-end">
            <Button type="button" variant="outline" onClick={() => { reset(); onClose() }}>Cancelar</Button>
            <Button type="submit" disabled={confirm.isPending}>
              {confirm.isPending ? 'Salvando...' : 'Confirmar'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
