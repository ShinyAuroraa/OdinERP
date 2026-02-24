import 'server-only';
import { env } from '@/lib/env';

export interface UserAdminDto {
  keycloakId: string;
  nome: string;
  email: string | null;
  cargo: string | null;
  enabled: boolean;
  roles: string[];
  lastLogin: string | null;
}

export async function listAdminUsers(accessToken: string): Promise<UserAdminDto[]> {
  const res = await fetch(`${env.NEXT_PUBLIC_API_URL}/api/v1/admin/users`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error(`Failed to list users: ${res.status}`);
  return res.json() as Promise<UserAdminDto[]>;
}
