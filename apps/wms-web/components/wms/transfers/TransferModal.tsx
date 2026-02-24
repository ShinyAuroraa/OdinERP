'use client'

import React from 'react'
import { useForm } from 'react-hook-form'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toast } from 'sonner'
import { useCreateTransfer } from '@/lib/api/transfers'

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
}

interface FormData {
  productId: string
  lotId?: string
  quantity: number
  fromLocationId: string
  toLocationId: string
  notes?: string
}

export function TransferModal({ open, onOpenChange }: Props) {
  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormData>()
  const createTransfer = useCreateTransfer()

  function onSubmit(data: FormData) {
    createTransfer.mutate(
      { ...data, quantity: Number(data.quantity) },
      {
        onSuccess: () => {
          toast.success('Transferência criada.')
          reset()
          onOpenChange(false)
        },
        onError: () => toast.error('Erro ao criar transferência.'),
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Nova Transferência Interna</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-3">
          <div className="space-y-1">
            <Label htmlFor="productId">ID do Produto *</Label>
            <Input id="productId" {...register('productId', { required: true })} placeholder="uuid do produto" />
            {errors.productId && <p className="text-xs text-destructive">Campo obrigatório.</p>}
          </div>
          <div className="space-y-1">
            <Label htmlFor="lotId">ID do Lote</Label>
            <Input id="lotId" {...register('lotId')} placeholder="Opcional" />
          </div>
          <div className="space-y-1">
            <Label htmlFor="quantity">Quantidade *</Label>
            <Input id="quantity" type="number" min="1" {...register('quantity', { required: true, valueAsNumber: true })} />
            {errors.quantity && <p className="text-xs text-destructive">Campo obrigatório.</p>}
          </div>
          <div className="space-y-1">
            <Label htmlFor="fromLocationId">Localização Origem *</Label>
            <Input id="fromLocationId" {...register('fromLocationId', { required: true })} placeholder="ID da localização de origem" />
            {errors.fromLocationId && <p className="text-xs text-destructive">Campo obrigatório.</p>}
          </div>
          <div className="space-y-1">
            <Label htmlFor="toLocationId">Localização Destino *</Label>
            <Input id="toLocationId" {...register('toLocationId', { required: true })} placeholder="ID da localização de destino" />
            {errors.toLocationId && <p className="text-xs text-destructive">Campo obrigatório.</p>}
          </div>
          <div className="space-y-1">
            <Label htmlFor="notes">Observações</Label>
            <Input id="notes" {...register('notes')} placeholder="Motivo da transferência..." />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => { reset(); onOpenChange(false) }}>Cancelar</Button>
            <Button type="submit" disabled={createTransfer.isPending}>
              {createTransfer.isPending ? 'Criando...' : 'Criar Transferência'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
