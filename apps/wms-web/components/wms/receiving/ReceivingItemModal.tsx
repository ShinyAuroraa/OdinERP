'use client'

import React from 'react'
import { useForm, type Resolver } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Form } from '@/components/ui/form'
import { WmsFormField } from '@/components/shared/FormField'
import { useConfirmItem } from '@/lib/api/receiving'
import type { ReceivingNoteItem } from '@/types/receiving'

const schema = z.object({
  receivedQuantity: z.coerce.number().min(0, 'Quantidade deve ser ≥ 0'),
  lotNumber: z.string().optional(),
  manufacturingDate: z.string().optional(),
  expiryDate: z.string().optional(),
  gs1Code: z.string().optional(),
  serialNumbers: z.string().optional(),
})

type FormData = z.infer<typeof schema>

interface ReceivingItemModalProps {
  noteId: string
  item: ReceivingNoteItem | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function ReceivingItemModal({ noteId, item, open, onOpenChange }: ReceivingItemModalProps) {
  const confirmItem = useConfirmItem(noteId)

  const form = useForm<FormData>({
    resolver: zodResolver(schema) as Resolver<FormData>,
    defaultValues: {
      receivedQuantity: item?.expectedQuantity ?? 0,
      lotNumber: '',
      manufacturingDate: '',
      expiryDate: '',
      gs1Code: '',
      serialNumbers: '',
    },
  })

  React.useEffect(() => {
    if (open && item) {
      form.reset({ receivedQuantity: item.expectedQuantity, lotNumber: '', manufacturingDate: '', expiryDate: '', gs1Code: '', serialNumbers: '' })
    }
  }, [open, item, form])

  function onSubmit(data: FormData) {
    if (!item) return
    const serialNumbers = data.serialNumbers
      ? data.serialNumbers.split('\n').map((s) => s.trim()).filter(Boolean)
      : []
    confirmItem.mutate(
      {
        itemId: item.id,
        data: {
          receivedQuantity: data.receivedQuantity,
          lotNumber: data.lotNumber || undefined,
          manufacturingDate: data.manufacturingDate || undefined,
          expiryDate: data.expiryDate || undefined,
          gs1Code: data.gs1Code || undefined,
          serialNumbers: serialNumbers.length > 0 ? serialNumbers : undefined,
        },
      },
      {
        onSuccess: () => { toast.success('Item confirmado.'); onOpenChange(false) },
        onError: () => toast.error('Erro ao confirmar item.'),
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Confirmar Recebimento — {item?.productSku}</DialogTitle>
        </DialogHeader>
        {item && (
          <div className="text-sm text-muted-foreground mb-2">
            {item.productName} · Esperado: <strong>{item.expectedQuantity}</strong>
          </div>
        )}
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-3">
            <WmsFormField name="receivedQuantity" label="Quantidade Recebida" required>
              <Input type="number" min={0} step="0.001" />
            </WmsFormField>
            <WmsFormField name="lotNumber" label="Número de Lote">
              <Input placeholder="LOT-2026-001" />
            </WmsFormField>
            <div className="grid grid-cols-2 gap-3">
              <WmsFormField name="manufacturingDate" label="Fabricação">
                <Input type="date" />
              </WmsFormField>
              <WmsFormField name="expiryDate" label="Validade">
                <Input type="date" />
              </WmsFormField>
            </div>
            <WmsFormField name="gs1Code" label="Código GS1">
              <Input placeholder="(01)07891234567890" />
            </WmsFormField>
            <WmsFormField name="serialNumbers" label="Números de Série (um por linha)">
              <Textarea placeholder="SN-001&#10;SN-002&#10;SN-003" rows={3} />
            </WmsFormField>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Cancelar</Button>
              <Button type="submit" disabled={confirmItem.isPending}>
                {confirmItem.isPending ? 'Confirmando...' : 'Confirmar'}
              </Button>
            </div>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
