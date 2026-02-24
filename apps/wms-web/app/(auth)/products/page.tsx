'use client'

import { ProductList } from '@/components/wms/products/ProductList'
import { ErrorBoundary } from '@/components/shared/ErrorBoundary'

export default function ProductsPage() {
  return (
    <ErrorBoundary>
      <ProductList />
    </ErrorBoundary>
  )
}
