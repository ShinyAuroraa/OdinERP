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
import { useCreateWarehouse, useUpdateWarehouse } from '@/lib/api/warehouses'
import type { Warehouse } from '@/types/warehouse'

const schema = z.object({
  code: z.string().min(1, 'Código obrigatório').max(20),
  name: z.string().min(1, 'Nome obrigatório').max(100),
  location: z.string().optional(),
  capacitySqMeters: z.coerce.number().min(0).optional(),
  description: z.string().optional(),
})

type FormData = z.infer<typeof schema>

interface WarehouseFormProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  warehouse?: Warehouse | null
}

export function WarehouseForm({ open, onOpenChange, warehouse }: WarehouseFormProps) {
  const isEditing = !!warehouse
  const createWarehouse = useCreateWarehouse()
  const updateWarehouse = useUpdateWarehouse(warehouse?.id ?? '')

  const form = useForm<FormData>({
    resolver: zodResolver(schema) as Resolver<FormData>,
    defaultValues: {
      code: warehouse?.code ?? '',
      name: warehouse?.name ?? '',
      location: warehouse?.location ?? '',
      capacitySqMeters: warehouse?.capacitySqMeters ?? undefined,
      description: warehouse?.description ?? '',
    },
  })

  React.useEffect(() => {
    if (open) {
      form.reset({
        code: warehouse?.code ?? '',
        name: warehouse?.name ?? '',
        location: warehouse?.location ?? '',
        capacitySqMeters: warehouse?.capacitySqMeters ?? undefined,
        description: warehouse?.description ?? '',
      })
    }
  }, [open, warehouse, form])

  function onSubmit(data: FormData) {
    if (isEditing) {
      updateWarehouse.mutate(data, {
        onSuccess: () => {
          toast.success('Armazém atualizado com sucesso.')
          onOpenChange(false)
        },
        onError: () => toast.error('Erro ao atualizar armazém.'),
      })
    } else {
      createWarehouse.mutate(data, {
        onSuccess: () => {
          toast.success('Armazém criado com sucesso.')
          onOpenChange(false)
        },
        onError: () => toast.error('Erro ao criar armazém.'),
      })
    }
  }

  const isPending = createWarehouse.isPending || updateWarehouse.isPending

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Editar Armazém' : 'Novo Armazém'}</DialogTitle>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <WmsFormField name="code" label="Código" required>
              <Input placeholder="WH-001" disabled={isEditing} />
            </WmsFormField>
            <WmsFormField name="name" label="Nome" required>
              <Input placeholder="Armazém Principal" />
            </WmsFormField>
            <WmsFormField name="location" label="Localização">
              <Input placeholder="São Paulo, SP" />
            </WmsFormField>
            <WmsFormField name="capacitySqMeters" label="Capacidade (m²)">
              <Input type="number" min={0} step="0.01" placeholder="1000" />
            </WmsFormField>
            <WmsFormField name="description" label="Descrição">
              <Textarea placeholder="Descrição do armazém..." rows={3} />
            </WmsFormField>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                Cancelar
              </Button>
              <Button type="submit" disabled={isPending}>
                {isPending ? 'Salvando...' : isEditing ? 'Salvar' : 'Criar'}
              </Button>
            </div>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
