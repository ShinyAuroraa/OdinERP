import { PickingDetail } from '@/components/wms/picking/PickingDetail'

interface Props {
  params: Promise<{ orderId: string }>
}

export default async function PickingDetailPage({ params }: Props) {
  const { orderId } = await params
  return <PickingDetail orderId={orderId} />
}
