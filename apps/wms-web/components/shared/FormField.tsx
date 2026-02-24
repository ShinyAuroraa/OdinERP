'use client'

import * as React from 'react'
import {
  FormControl,
  FormDescription,
  FormItem,
  FormLabel,
  FormMessage,
  FormField as RHFFormField,
} from '@/components/ui/form'
import { useFormContext, type FieldPath, type FieldValues } from 'react-hook-form'

interface WmsFormFieldProps<TFieldValues extends FieldValues = FieldValues> {
  name: FieldPath<TFieldValues>
  label: string
  description?: string
  required?: boolean
  children: React.ReactNode
}

/**
 * WmsFormField — wrapper sobre shadcn/ui FormField + React Hook Form.
 * Uso: deve estar dentro de <Form {...form}> com react-hook-form context.
 *
 * @example
 * <WmsFormField name="warehouseName" label="Nome do Armazém" required>
 *   <Input {...form.register('warehouseName')} />
 * </WmsFormField>
 */
export function WmsFormField<TFieldValues extends FieldValues = FieldValues>({
  name,
  label,
  description,
  required,
  children,
}: WmsFormFieldProps<TFieldValues>) {
  const form = useFormContext<TFieldValues>()

  return (
    <RHFFormField
      control={form.control}
      name={name}
      render={() => (
        <FormItem>
          <FormLabel>
            {label}
            {required && <span className="ml-1 text-destructive">*</span>}
          </FormLabel>
          <FormControl>{children as React.ReactElement}</FormControl>
          {description && <FormDescription>{description}</FormDescription>}
          <FormMessage />
        </FormItem>
      )}
    />
  )
}
