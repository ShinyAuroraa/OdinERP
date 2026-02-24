'use client'

import React from 'react'
import { useForm, useFieldArray, type Resolver } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Plus, Trash2 } from 'lucide-react'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Form } from '@/components/ui/form'
import { WmsFormField } from '@/components/shared/FormField'
import { useCreateReceivingNote } from '@/lib/api/receiving'

const itemSchema = z.object({
  productId: z.string().min(1, 'Produto obrigatório'),
  expectedQuantity: z.coerce.number().min(1, 'Quantidade deve ser ≥ 1'),
})

const schema = z.object({
  warehouseId: z.string().min(1, 'Armazém obrigatório'),
  dockLocationId: z.string().min(1, 'Doca obrigatória'),
  supplierId: z.string().optional(),
  purchaseOrderRef: z.string().optional(),
  items: z.array(itemSchema).min(1, 'Adicione ao menos um item'),
})

type FormData = z.infer<typeof schema>

interface ReceivingFormProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function ReceivingForm({ open, onOpenChange }: ReceivingFormProps) {
  const createNote = useCreateReceivingNote()

  const form = useForm<FormData>({
    resolver: zodResolver(schema) as Resolver<FormData>,
    defaultValues: {
      warehouseId: '',
      dockLocationId: '',
      supplierId: '',
      purchaseOrderRef: '',
      items: [{ productId: '', expectedQuantity: 1 }],
    },
  })

  const { fields, append, remove } = useFieldArray({ control: form.control, name: 'items' })

  React.useEffect(() => {
    if (open) form.reset({
      warehouseId: '', dockLocationId: '', supplierId: '', purchaseOrderRef: '',
      items: [{ productId: '', expectedQuantity: 1 }],
    })
  }, [open, form])

  function onSubmit(data: FormData) {
    createNote.mutate(data, {
      onSuccess: () => { toast.success('Nota de recebimento criada.'); onOpenChange(false) },
      onError: () => toast.error('Erro ao criar nota de recebimento.'),
    })
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Nova Nota de Recebimento</DialogTitle>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <WmsFormField name="warehouseId" label="Armazém" required>
                <Input placeholder="ID do armazém" />
              </WmsFormField>
              <WmsFormField name="dockLocationId" label="Doca de Chegada" required>
                <Input placeholder="ID da doca" />
              </WmsFormField>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <WmsFormField name="supplierId" label="Fornecedor">
                <Input placeholder="Fornecedor" />
              </WmsFormField>
              <WmsFormField name="purchaseOrderRef" label="Referência OC">
                <Input placeholder="OC-12345" />
              </WmsFormField>
            </div>

            <div>
              <div className="flex items-center justify-between mb-2">
                <p className="text-sm font-medium">Itens</p>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => append({ productId: '', expectedQuantity: 1 })}
                >
                  <Plus className="h-3 w-3 mr-1" />
                  Adicionar Item
                </Button>
              </div>
              <div className="space-y-2">
                {fields.map((field, index) => (
                  <div key={field.id} className="flex gap-2 items-start">
                    <div className="flex-1">
                      <WmsFormField name={`items.${index}.productId`} label={`Produto ${index + 1}`} required>
                        <Input placeholder="ID do produto" />
                      </WmsFormField>
                    </div>
                    <div className="w-32">
                      <WmsFormField name={`items.${index}.expectedQuantity`} label="Qtd. Esperada" required>
                        <Input type="number" min={1} />
                      </WmsFormField>
                    </div>
                    {fields.length > 1 && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="mt-6"
                        onClick={() => remove(index)}
                      >
                        <Trash2 className="h-4 w-4 text-destructive" />
                      </Button>
                    )}
                  </div>
                ))}
              </div>
            </div>

            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Cancelar</Button>
              <Button type="submit" disabled={createNote.isPending}>
                {createNote.isPending ? 'Criando...' : 'Criar Nota'}
              </Button>
            </div>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
