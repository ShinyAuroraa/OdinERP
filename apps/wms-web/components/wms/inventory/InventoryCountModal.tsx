'use client'

import React from 'react'
import { useForm } from 'react-hook-form'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toast } from 'sonner'
import { useSubmitCountItem } from '@/lib/api/inventory'
import type { InventoryCountItem } from '@/types/inventory'

interface Props {
  countId: string
  item: InventoryCountItem | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

interface FormData {
  countedQuantity: number
  notes?: string
}

export function InventoryCountModal({ countId, item, open, onOpenChange }: Props) {
  const { register, handleSubmit, reset } = useForm<FormData>()
  const submit = useSubmitCountItem(countId, item?.id ?? '')

  React.useEffect(() => {
    if (item) reset({ countedQuantity: item.expectedQuantity })
  }, [item, reset])

  function onSubmit(data: FormData) {
    submit.mutate(
      { countedQuantity: Number(data.countedQuantity), notes: data.notes },
      {
        onSuccess: () => {
          toast.success('Contagem registrada.')
          onOpenChange(false)
        },
        onError: () => toast.error('Erro ao registrar contagem.'),
      },
    )
  }

  if (!item) return null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Contar Item</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <p className="text-sm font-medium">{item.productSku} — {item.productName}</p>
            <p className="text-xs text-muted-foreground">
              Localização: {item.locationCode}{item.lotNumber ? ` · Lote: ${item.lotNumber}` : ''}
            </p>
            <p className="text-xs text-muted-foreground">Esperado: {item.expectedQuantity}</p>
          </div>
          <div className="space-y-1">
            <Label htmlFor="countedQuantity">Quantidade Contada</Label>
            <Input
              id="countedQuantity"
              type="number"
              min="0"
              {...register('countedQuantity', { required: true, valueAsNumber: true })}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="notes">Observações</Label>
            <Input id="notes" placeholder="Opcional..." {...register('notes')} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Cancelar</Button>
            <Button type="submit" disabled={submit.isPending}>
              {submit.isPending ? 'Salvando...' : 'Confirmar Contagem'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
