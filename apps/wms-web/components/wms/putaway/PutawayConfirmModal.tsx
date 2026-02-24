'use client'

import { toast } from 'sonner'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useConfirmPutaway } from '@/lib/api/putaway'
import type { PutawayTask } from '@/types/putaway'
import React from 'react'

interface PutawayConfirmModalProps {
  task: PutawayTask | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function PutawayConfirmModal({ task, open, onOpenChange }: PutawayConfirmModalProps) {
  const [locationId, setLocationId] = React.useState('')
  const confirmPutaway = useConfirmPutaway()

  React.useEffect(() => {
    if (open && task) setLocationId(task.suggestedLocationId ?? '')
  }, [open, task])

  function handleConfirm() {
    if (!task) return
    confirmPutaway.mutate(
      { id: task.id, data: { confirmedLocationId: locationId || undefined } },
      {
        onSuccess: () => { toast.success('Putaway confirmado.'); onOpenChange(false) },
        onError: () => toast.error('Erro ao confirmar putaway.'),
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Confirmar Localização</DialogTitle>
        </DialogHeader>
        {task && (
          <div className="space-y-4">
            <div className="text-sm text-muted-foreground">
              <p><strong>Produto:</strong> {task.productSku} — {task.productName}</p>
              <p><strong>Quantidade:</strong> {task.quantity}</p>
              {task.lotNumber && <p><strong>Lote:</strong> {task.lotNumber}</p>}
            </div>
            <div className="space-y-1">
              <Label>Localização</Label>
              <Input
                value={locationId}
                onChange={(e) => setLocationId(e.target.value)}
                placeholder={task.suggestedLocationCode ?? 'ID da localização'}
              />
              {task.suggestedLocationCode && (
                <p className="text-xs text-muted-foreground">Sugerida: {task.suggestedLocationCode}</p>
              )}
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => onOpenChange(false)}>Cancelar</Button>
              <Button onClick={handleConfirm} disabled={confirmPutaway.isPending}>
                {confirmPutaway.isPending ? 'Confirmando...' : 'Confirmar'}
              </Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
