'use client'

import React from 'react'
import { Tag, Barcode, Calendar, Shield } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { LoadingState } from '@/components/shared/LoadingState'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { StorageTypeBadge } from './StorageTypeBadge'
import type { ProductWms } from '@/types/product'

interface ProductDetailProps {
  product: ProductWms
  isLoading?: boolean
}

export function ProductDetail({ product, isLoading }: ProductDetailProps) {
  if (isLoading) return <LoadingState variant="page" />

  const activeControls = [
    product.controlsLot && { label: 'Lote', Icon: Tag },
    product.controlsSerial && { label: 'Serial', Icon: Barcode },
    product.controlsExpiry && { label: 'Validade', Icon: Calendar },
    product.requiresSanitaryVigilance && { label: 'ANVISA', Icon: Shield },
  ].filter(Boolean) as { label: string; Icon: React.ComponentType<{ className?: string }> }[]

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
      <Card>
        <CardHeader><CardTitle className="text-base">Informações Gerais</CardTitle></CardHeader>
        <CardContent className="space-y-3 text-sm">
          <div className="flex justify-between">
            <span className="text-muted-foreground">SKU</span>
            <span className="font-medium">{product.sku}</span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-muted-foreground">Status</span>
            <StatusBadge status={product.status} />
          </div>
          <div className="flex justify-between items-center">
            <span className="text-muted-foreground">Tipo de Armazenagem</span>
            <StorageTypeBadge storageType={product.storageType} />
          </div>
          {product.lengthM && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">Dimensões (L×C×A)</span>
              <span>{product.lengthM}m × {product.widthM}m × {product.heightM}m</span>
            </div>
          )}
          {product.weightKg && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">Peso</span>
              <span>{product.weightKg} kg</span>
            </div>
          )}
          {product.unitsPerLocation && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">Unidades/Localização</span>
              <span>{product.unitsPerLocation}</span>
            </div>
          )}
          {product.description && (
            <div>
              <span className="text-muted-foreground">Descrição</span>
              <p className="mt-1">{product.description}</p>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle className="text-base">Rastreabilidade</CardTitle></CardHeader>
        <CardContent>
          {activeControls.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nenhum controle de rastreabilidade ativo.</p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {activeControls.map(({ label, Icon }) => (
                <Badge key={label} variant="secondary" className="gap-1">
                  <Icon className="h-3 w-3" />
                  {label}
                </Badge>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {(product.ean13 || product.gs1128) && (
        <Card className="md:col-span-2">
          <CardHeader><CardTitle className="text-base">Códigos GS1</CardTitle></CardHeader>
          <CardContent className="grid grid-cols-2 gap-4 text-sm">
            {product.ean13 && (
              <div>
                <p className="text-muted-foreground mb-1">EAN-13</p>
                <code className="font-mono">{product.ean13}</code>
              </div>
            )}
            {product.gs1128 && (
              <div>
                <p className="text-muted-foreground mb-1">GS1-128</p>
                <code className="font-mono">{product.gs1128}</code>
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  )
}
