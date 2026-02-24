import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import React from 'react'
import { ConfirmDialog } from './ConfirmDialog'

describe('ConfirmDialog', () => {
  it('renderiza title e description quando aberto', () => {
    render(
      <ConfirmDialog
        open={true}
        onOpenChange={vi.fn()}
        title="Cancelar ordem?"
        description="Esta ação não pode ser desfeita."
        onConfirm={vi.fn()}
      />,
    )
    expect(screen.getByText('Cancelar ordem?')).toBeInTheDocument()
    expect(screen.getByText('Esta ação não pode ser desfeita.')).toBeInTheDocument()
  })

  it('chama onConfirm ao clicar em Confirmar', () => {
    const onConfirm = vi.fn()
    const onOpenChange = vi.fn()
    render(
      <ConfirmDialog
        open={true}
        onOpenChange={onOpenChange}
        title="Confirmar?"
        description="Tem certeza?"
        onConfirm={onConfirm}
        confirmLabel="Confirmar ação"
      />,
    )
    fireEvent.click(screen.getByText('Confirmar ação'))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('exibe ícone de alerta na variante destructive', () => {
    render(
      <ConfirmDialog
        open={true}
        onOpenChange={vi.fn()}
        title="Deletar?"
        description="Isso é permanente."
        onConfirm={vi.fn()}
        variant="destructive"
      />,
    )
    expect(screen.getByText('Ação irreversível')).toBeInTheDocument()
  })

  it('não renderiza nada quando open=false', () => {
    render(
      <ConfirmDialog
        open={false}
        onOpenChange={vi.fn()}
        title="Título oculto"
        description="Desc oculta"
        onConfirm={vi.fn()}
      />,
    )
    expect(screen.queryByText('Título oculto')).not.toBeInTheDocument()
  })
})
