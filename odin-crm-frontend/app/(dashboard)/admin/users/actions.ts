'use server';

import { auth } from '@/lib/auth';
import { env } from '@/lib/env';
import { revalidatePath } from 'next/cache';

async function getAdminToken(): Promise<string> {
  const session = await auth();
  if (!session?.user.roles.includes('crm-admin')) {
    throw new Error('Unauthorized');
  }
  return session.accessToken;
}

export async function updateUserRoles(keycloakId: string, roles: string[]): Promise<void> {
  const token = await getAdminToken();
  const res = await fetch(
    `${env.NEXT_PUBLIC_API_URL}/api/v1/admin/users/${keycloakId}/roles`,
    {
      method: 'PATCH',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ roles }),
    }
  );
  if (!res.ok) throw new Error(`Failed to update roles: ${res.status}`);
  revalidatePath('/admin/users');
}

export async function updateUserStatus(keycloakId: string, enabled: boolean): Promise<void> {
  const token = await getAdminToken();
  const res = await fetch(
    `${env.NEXT_PUBLIC_API_URL}/api/v1/admin/users/${keycloakId}/status`,
    {
      method: 'PATCH',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ enabled }),
    }
  );
  if (!res.ok) throw new Error(`Failed to update status: ${res.status}`);
  revalidatePath('/admin/users');
}
