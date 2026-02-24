'use client'

import React from 'react'
import { toast } from 'sonner'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useDecideQuarantine } from '@/lib/api/putaway'
import { QUARANTINE_DECISION_LABELS } from '@/types/putaway'
import type { QuarantineTask, QuarantineDecision } from '@/types/putaway'

interface QuarantineDecideModalProps {
  task: QuarantineTask | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function QuarantineDecideModal({ task, open, onOpenChange }: QuarantineDecideModalProps) {
  const [decision, setDecision] = React.useState<QuarantineDecision | ''>('')
  const [qualityNotes, setQualityNotes] = React.useState('')
  const decideQuarantine = useDecideQuarantine()

  React.useEffect(() => {
    if (open) { setDecision(''); setQualityNotes('') }
  }, [open])

  function handleDecide() {
    if (!task || !decision) return
    decideQuarantine.mutate(
      { id: task.id, data: { decision, qualityNotes: qualityNotes || undefined } },
      {
        onSuccess: () => { toast.success('Decisão registrada.'); onOpenChange(false) },
        onError: () => toast.error('Erro ao registrar decisão.'),
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Decisão de Quarentena</DialogTitle>
        </DialogHeader>
        {task && (
          <div className="space-y-4">
            <div className="text-sm text-muted-foreground">
              <p><strong>Produto:</strong> {task.productSku} — {task.productName}</p>
              <p><strong>Quantidade:</strong> {task.quantity}</p>
              {task.lotNumber && <p><strong>Lote:</strong> {task.lotNumber}</p>}
            </div>
            <div className="space-y-1">
              <Label>Decisão *</Label>
              <Select value={decision} onValueChange={(v) => setDecision(v as QuarantineDecision)}>
                <SelectTrigger>
                  <SelectValue placeholder="Selecione uma decisão" />
                </SelectTrigger>
                <SelectContent>
                  {(Object.keys(QUARANTINE_DECISION_LABELS) as QuarantineDecision[]).map((key) => (
                    <SelectItem key={key} value={key}>{QUARANTINE_DECISION_LABELS[key]}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1">
              <Label>Observações de Qualidade</Label>
              <Textarea
                value={qualityNotes}
                onChange={(e) => setQualityNotes(e.target.value)}
                placeholder="Descreva os problemas encontrados..."
                rows={3}
              />
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => onOpenChange(false)}>Cancelar</Button>
              <Button onClick={handleDecide} disabled={!decision || decideQuarantine.isPending}>
                {decideQuarantine.isPending ? 'Registrando...' : 'Confirmar Decisão'}
              </Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
