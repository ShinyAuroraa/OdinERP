'use client'

import { useParams } from 'next/navigation'
import { ReceivingDetail } from '@/components/wms/receiving/ReceivingDetail'

export default function ReceivingDetailPage() {
  const { id } = useParams<{ id: string }>()
  return <ReceivingDetail noteId={id} />
}
