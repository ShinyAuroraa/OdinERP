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
import { Checkbox } from '@/components/ui/checkbox'
import { Form } from '@/components/ui/form'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { FormField, FormItem, FormLabel, FormControl } from '@/components/ui/form'
import { WmsFormField } from '@/components/shared/FormField'
import { useCreateProduct, useUpdateProduct } from '@/lib/api/products'
import type { ProductWms, StorageType } from '@/types/product'

const STORAGE_TYPES: StorageType[] = ['DRY', 'REFRIGERATED', 'FROZEN', 'HAZARDOUS', 'OVERSIZED', 'CONTROLLED']
const STORAGE_TYPE_LABELS: Record<StorageType, string> = {
  DRY: 'Seco', REFRIGERATED: 'Refrigerado', FROZEN: 'Congelado',
  HAZARDOUS: 'Perigoso', OVERSIZED: 'Carga Pesada', CONTROLLED: 'Controlado',
}

const schema = z.object({
  sku: z.string().min(1, 'SKU obrigatório').max(50),
  name: z.string().min(1, 'Nome obrigatório').max(200),
  description: z.string().optional(),
  storageType: z.enum(['DRY', 'REFRIGERATED', 'FROZEN', 'HAZARDOUS', 'OVERSIZED', 'CONTROLLED']),
  lengthM: z.coerce.number().min(0).optional(),
  widthM: z.coerce.number().min(0).optional(),
  heightM: z.coerce.number().min(0).optional(),
  weightKg: z.coerce.number().min(0).optional(),
  unitsPerLocation: z.coerce.number().min(1).optional(),
  ean13: z.string().optional(),
  gs1128: z.string().optional(),
  controlsLot: z.boolean().default(false),
  controlsSerial: z.boolean().default(false),
  controlsExpiry: z.boolean().default(false),
  requiresSanitaryVigilance: z.boolean().default(false),
})

type FormData = z.infer<typeof schema>

interface ProductFormProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  product?: ProductWms | null
}

export function ProductForm({ open, onOpenChange, product }: ProductFormProps) {
  const isEditing = !!product
  const createProduct = useCreateProduct()
  const updateProduct = useUpdateProduct(product?.id ?? '')

  const form = useForm<FormData>({
    resolver: zodResolver(schema) as Resolver<FormData>,
    defaultValues: {
      sku: '', name: '', description: '', storageType: 'DRY',
      controlsLot: false, controlsSerial: false, controlsExpiry: false,
      requiresSanitaryVigilance: false,
    },
  })

  React.useEffect(() => {
    if (open && product) {
      form.reset({
        sku: product.sku, name: product.name, description: product.description ?? '',
        storageType: product.storageType, lengthM: product.lengthM, widthM: product.widthM,
        heightM: product.heightM, weightKg: product.weightKg, unitsPerLocation: product.unitsPerLocation,
        ean13: product.ean13 ?? '', gs1128: product.gs1128 ?? '',
        controlsLot: product.controlsLot, controlsSerial: product.controlsSerial,
        controlsExpiry: product.controlsExpiry, requiresSanitaryVigilance: product.requiresSanitaryVigilance,
      })
    } else if (open && !product) {
      form.reset({ sku: '', name: '', description: '', storageType: 'DRY',
        controlsLot: false, controlsSerial: false, controlsExpiry: false, requiresSanitaryVigilance: false })
    }
  }, [open, product, form])

  function onSubmit(data: FormData) {
    if (isEditing) {
      updateProduct.mutate(data, {
        onSuccess: () => { toast.success('Produto atualizado.'); onOpenChange(false) },
        onError: () => toast.error('Erro ao atualizar produto.'),
      })
    } else {
      createProduct.mutate(data, {
        onSuccess: () => { toast.success('Produto criado.'); onOpenChange(false) },
        onError: () => toast.error('Erro ao criar produto.'),
      })
    }
  }

  const isPending = createProduct.isPending || updateProduct.isPending

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Editar Produto' : 'Novo Produto'}</DialogTitle>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <WmsFormField name="sku" label="SKU" required>
                <Input placeholder="SKU-001" disabled={isEditing} />
              </WmsFormField>
              <WmsFormField name="name" label="Nome" required>
                <Input placeholder="Produto Exemplo" />
              </WmsFormField>
            </div>

            <WmsFormField name="description" label="Descrição">
              <Textarea placeholder="Descrição do produto..." rows={2} />
            </WmsFormField>

            <FormField
              control={form.control}
              name="storageType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Tipo de Armazenagem <span className="text-destructive">*</span></FormLabel>
                  <FormControl>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger><SelectValue placeholder="Selecione" /></SelectTrigger>
                      <SelectContent>
                        {STORAGE_TYPES.map((type) => (
                          <SelectItem key={type} value={type}>{STORAGE_TYPE_LABELS[type]}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormControl>
                </FormItem>
              )}
            />

            <div className="grid grid-cols-3 gap-4">
              <WmsFormField name="lengthM" label="Comprimento (m)"><Input type="number" step="0.001" /></WmsFormField>
              <WmsFormField name="widthM" label="Largura (m)"><Input type="number" step="0.001" /></WmsFormField>
              <WmsFormField name="heightM" label="Altura (m)"><Input type="number" step="0.001" /></WmsFormField>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <WmsFormField name="weightKg" label="Peso (kg)"><Input type="number" step="0.001" /></WmsFormField>
              <WmsFormField name="unitsPerLocation" label="Unid./Localização"><Input type="number" min={1} /></WmsFormField>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <WmsFormField name="ean13" label="EAN-13"><Input placeholder="7891234567890" /></WmsFormField>
              <WmsFormField name="gs1128" label="GS1-128"><Input placeholder="(01)07891234567890" /></WmsFormField>
            </div>

            <div>
              <p className="text-sm font-medium mb-2">Controles de Rastreabilidade</p>
              <div className="grid grid-cols-2 gap-2">
                {([
                  { name: 'controlsLot' as const, label: 'Controla por Lote' },
                  { name: 'controlsSerial' as const, label: 'Controla por Serial' },
                  { name: 'controlsExpiry' as const, label: 'Controla Validade' },
                  { name: 'requiresSanitaryVigilance' as const, label: 'Vigilância Sanitária (ANVISA)' },
                ] as const).map(({ name, label }) => (
                  <FormField
                    key={name}
                    control={form.control}
                    name={name}
                    render={({ field }) => (
                      <FormItem className="flex items-center gap-2">
                        <FormControl>
                          <Checkbox checked={field.value} onCheckedChange={field.onChange} />
                        </FormControl>
                        <FormLabel className="text-sm font-normal cursor-pointer">{label}</FormLabel>
                      </FormItem>
                    )}
                  />
                ))}
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Cancelar</Button>
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
