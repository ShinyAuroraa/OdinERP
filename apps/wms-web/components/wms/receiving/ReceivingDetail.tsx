'use client'

import React from 'react'
import { toast } from 'sonner'
import { CheckCircle, Clock, AlertTriangle, ArrowRight } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { useReceivingNote, useStartConference, useCompleteNote, useApproveDivergences } from '@/lib/api/receiving'
import { useHasRole } from '@/hooks/useHasRole'
import { ReceivingItemModal } from './ReceivingItemModal'
import type { ReceivingNoteItem, ReceivingItemStatus, ReceivingNoteStatus } from '@/types/receiving'

const STATUS_LABELS: Record<ReceivingNoteStatus, string> = {
  PENDING: 'Pendente', IN_PROGRESS: 'Em Conferência', COMPLETED: 'Concluída', FLAGGED: 'Com Divergências',
}
const STATUS_VARIANTS: Record<ReceivingNoteStatus, 'default' | 'success' | 'warning' | 'destructive'> = {
  PENDING: 'default', IN_PROGRESS: 'warning', COMPLETED: 'success', FLAGGED: 'destructive',
}
const ITEM_STATUS_LABELS: Record<ReceivingItemStatus, string> = {
  PENDING: 'Pendente', CONFIRMED: 'Confirmado', FLAGGED: 'Divergência',
}

interface ReceivingDetailProps {
  noteId: string
}

export function ReceivingDetail({ noteId }: ReceivingDetailProps) {
  const { data: note, isLoading } = useReceivingNote(noteId)
  const startConference = useStartConference(noteId)
  const completeNote = useCompleteNote(noteId)
  const approveDivergences = useApproveDivergences(noteId)
  const isSupervisor = useHasRole('WMS_SUPERVISOR')

  const [selectedItem, setSelectedItem] = React.useState<ReceivingNoteItem | null>(null)
  const [modalOpen, setModalOpen] = React.useState(false)

  if (isLoading) {
    return <div className="p-6"><div className="animate-pulse h-64 bg-muted rounded-lg" /></div>
  }

  if (!note) return <div className="p-6 text-muted-foreground">Nota não encontrada.</div>

  const hasFlagged = note.items.some((i) => i.status === 'FLAGGED')
  const allConfirmed = note.items.every((i) => i.status === 'CONFIRMED')

  function handleConfirmItem(item: ReceivingNoteItem) {
    setSelectedItem(item)
    setModalOpen(true)
  }

  return (
    <div className="p-6 space-y-4">
      <PageHeader
        title={`Nota ${note.noteNumber}`}
        description={`Fornecedor: ${note.supplierId ?? 'N/A'} · Ref. OC: ${note.purchaseOrderRef ?? 'N/A'}`}
        actions={
          <StatusBadge
            status={note.status}
            label={STATUS_LABELS[note.status]}
            variant={STATUS_VARIANTS[note.status]}
          />
        }
      />

      {/* Status Steps */}
      <div className="flex items-center gap-2 text-sm">
        {['Criada', 'Em Conferência', 'Concluída'].map((step, i) => (
          <React.Fragment key={step}>
            <div className={`flex items-center gap-1 ${i === 0 ? 'text-primary' : i === 1 && note.status === 'IN_PROGRESS' ? 'text-primary' : i === 2 && note.status === 'COMPLETED' ? 'text-primary' : 'text-muted-foreground'}`}>
              {i === 2 && note.status === 'COMPLETED' ? (
                <CheckCircle className="h-4 w-4" />
              ) : i === 1 && note.status === 'IN_PROGRESS' ? (
                <Clock className="h-4 w-4" />
              ) : null}
              {step}
            </div>
            {i < 2 && <ArrowRight className="h-3 w-3 text-muted-foreground" />}
          </React.Fragment>
        ))}
        {hasFlagged && (
          <Badge variant="destructive" className="ml-2">
            <AlertTriangle className="h-3 w-3 mr-1" />
            Divergências
          </Badge>
        )}
      </div>

      {/* Actions */}
      <div className="flex gap-2">
        {note.status === 'PENDING' && (
          <Button
            onClick={() => startConference.mutate(undefined, { onSuccess: () => toast.success('Conferência iniciada.'), onError: () => toast.error('Erro ao iniciar conferência.') })}
            disabled={startConference.isPending}
          >
            {startConference.isPending ? 'Iniciando...' : 'Iniciar Conferência'}
          </Button>
        )}
        {note.status === 'IN_PROGRESS' && allConfirmed && !hasFlagged && (
          <Button
            onClick={() => completeNote.mutate(undefined, { onSuccess: () => toast.success('Nota concluída.'), onError: () => toast.error('Erro ao concluir nota.') })}
            disabled={completeNote.isPending}
          >
            {completeNote.isPending ? 'Finalizando...' : 'Finalizar Recebimento'}
          </Button>
        )}
        {note.status === 'FLAGGED' && isSupervisor && (
          <Button
            variant="destructive"
            onClick={() => approveDivergences.mutate(undefined, { onSuccess: () => toast.success('Divergências aprovadas.'), onError: () => toast.error('Erro ao aprovar divergências.') })}
            disabled={approveDivergences.isPending}
          >
            {approveDivergences.isPending ? 'Aprovando...' : 'Aprovar Divergências'}
          </Button>
        )}
      </div>

      {/* Items */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Itens ({note.items.length})</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          {note.items.map((item) => (
            <div key={item.id} className="flex items-center justify-between p-3 rounded-lg border">
              <div>
                <p className="font-medium text-sm">{item.productSku} — {item.productName}</p>
                <p className="text-xs text-muted-foreground">
                  Esperado: {item.expectedQuantity}
                  {item.receivedQuantity !== undefined && ` · Recebido: ${item.receivedQuantity}`}
                  {item.lotNumber && ` · Lote: ${item.lotNumber}`}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <StatusBadge
                  status={item.status}
                  label={ITEM_STATUS_LABELS[item.status]}
                  variant={item.status === 'CONFIRMED' ? 'success' : item.status === 'FLAGGED' ? 'destructive' : 'default'}
                />
                {note.status === 'IN_PROGRESS' && item.status === 'PENDING' && (
                  <Button variant="outline" size="sm" onClick={() => handleConfirmItem(item)}>
                    Confirmar
                  </Button>
                )}
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      <ReceivingItemModal
        noteId={noteId}
        item={selectedItem}
        open={modalOpen}
        onOpenChange={setModalOpen}
      />
    </div>
  )
}
