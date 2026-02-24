'use client'

import React from 'react'
import { PageHeader } from '@/components/shared/PageHeader'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { TraceabilityTimeline } from './TraceabilityTimeline'
import { GS1Parser } from './GS1Parser'
import { useLotTraceability, useSerialTraceability } from '@/lib/api/traceability'

export function TraceabilitySearch() {
  const [searchType, setSearchType] = React.useState<'lot' | 'serial'>('lot')
  const [searchValue, setSearchValue] = React.useState('')
  const [submitted, setSubmitted] = React.useState('')

  const lotResult = useLotTraceability(searchType === 'lot' ? submitted : '')
  const serialResult = useSerialTraceability(searchType === 'serial' ? submitted : '')

  const data = searchType === 'lot' ? lotResult.data : serialResult.data
  const isLoading = searchType === 'lot' ? lotResult.isLoading : serialResult.isLoading

  function handleSearch() {
    if (searchValue.trim()) setSubmitted(searchValue.trim())
  }

  return (
    <div className="p-6 space-y-4">
      <PageHeader title="Rastreabilidade" description="Rastreie lotes, séries e movimentos de estoque" />
      <Tabs defaultValue="search">
        <TabsList>
          <TabsTrigger value="search">Busca</TabsTrigger>
          <TabsTrigger value="gs1">GS1</TabsTrigger>
        </TabsList>

        <TabsContent value="search" className="space-y-4">
          <div className="flex gap-2">
            <Select value={searchType} onValueChange={(v) => { setSearchType(v as 'lot' | 'serial'); setSubmitted('') }}>
              <SelectTrigger className="w-36">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="lot">Lote</SelectItem>
                <SelectItem value="serial">Série</SelectItem>
              </SelectContent>
            </Select>
            <Input
              placeholder={searchType === 'lot' ? 'Número do lote...' : 'Número de série...'}
              value={searchValue}
              onChange={(e) => setSearchValue(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              className="max-w-sm"
            />
            <Button onClick={handleSearch} disabled={!searchValue.trim()}>
              Rastrear
            </Button>
          </div>

          {submitted && (
            isLoading ? (
              <div className="animate-pulse h-32 bg-muted rounded-lg" />
            ) : data ? (
              <TraceabilityTimeline data={data} searchType={searchType} />
            ) : (
              <p className="text-muted-foreground text-sm">Nenhum resultado encontrado para &quot;{submitted}&quot;.</p>
            )
          )}
        </TabsContent>

        <TabsContent value="gs1">
          <GS1Parser />
        </TabsContent>
      </Tabs>
    </div>
  )
}
