import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Form } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { WmsFormField } from './FormField'

const schema = z.object({ warehouseName: z.string().min(1, 'Nome obrigatório') })
type FormData = z.infer<typeof schema>

function TestForm({ defaultValues = {} }: { defaultValues?: Partial<FormData> }) {
  const form = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { warehouseName: '', ...defaultValues },
  })

  return (
    <Form {...form}>
      <form>
        <WmsFormField name="warehouseName" label="Nome do Armazém" required>
          <Input data-testid="warehouse-input" />
        </WmsFormField>
      </form>
    </Form>
  )
}

describe('WmsFormField', () => {
  it('renderiza label com asterisco para campos obrigatórios', () => {
    render(<TestForm />)
    expect(screen.getByText('Nome do Armazém')).toBeInTheDocument()
    expect(screen.getByText('*')).toBeInTheDocument()
  })

  it('renderiza o input filho corretamente', () => {
    render(<TestForm />)
    expect(screen.getByTestId('warehouse-input')).toBeInTheDocument()
  })
})
