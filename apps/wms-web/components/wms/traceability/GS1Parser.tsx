'use client'

import React from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { toast } from 'sonner'
import { useParseGS1 } from '@/lib/api/traceability'
import type { GS1ParsedResponse } from '@/types/traceability'

export function GS1Parser() {
  const [gs1Code, setGs1Code] = React.useState('')
  const [result, setResult] = React.useState<GS1ParsedResponse | null>(null)
  const parseGS1 = useParseGS1()

  function handleParse() {
    if (!gs1Code.trim()) return
    parseGS1.mutate(
      { gs1Code: gs1Code.trim() },
      {
        onSuccess: (data) => setResult(data),
        onError: () => toast.error('Erro ao fazer parse do código GS1.'),
      },
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex gap-2">
        <Input
          placeholder="Cole o código GS1 aqui..."
          value={gs1Code}
          onChange={(e) => setGs1Code(e.target.value)}
          className="font-mono"
        />
        <Button onClick={handleParse} disabled={!gs1Code.trim() || parseGS1.isPending}>
          {parseGS1.isPending ? 'Processando...' : 'Parse GS1'}
        </Button>
      </div>

      {result && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Resultado do Parse</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 gap-3 text-sm">
              {result.gtin && (
                <div>
                  <Label className="text-xs text-muted-foreground">GTIN</Label>
                  <p className="font-mono">{result.gtin}</p>
                </div>
              )}
              {result.lotNumber && (
                <div>
                  <Label className="text-xs text-muted-foreground">Lote</Label>
                  <p>{result.lotNumber}</p>
                </div>
              )}
              {result.serialNumber && (
                <div>
                  <Label className="text-xs text-muted-foreground">Série</Label>
                  <p>{result.serialNumber}</p>
                </div>
              )}
              {result.expiryDate && (
                <div>
                  <Label className="text-xs text-muted-foreground">Validade</Label>
                  <p>{result.expiryDate}</p>
                </div>
              )}
              {result.sscc && (
                <div>
                  <Label className="text-xs text-muted-foreground">SSCC</Label>
                  <p className="font-mono">{result.sscc}</p>
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
