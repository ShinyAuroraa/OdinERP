import { InventoryDetail } from '@/components/wms/inventory/InventoryDetail'

interface Props {
  params: Promise<{ countId: string }>
}

export default async function InventoryDetailPage({ params }: Props) {
  const { countId } = await params
  return <InventoryDetail countId={countId} />
}
