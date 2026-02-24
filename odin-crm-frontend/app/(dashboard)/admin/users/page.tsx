import { auth } from '@/lib/auth';
import { redirect } from 'next/navigation';
import { listAdminUsers } from '@/lib/api/admin';
import { UserManagementClient } from '@/components/admin/UserManagementClient';

export default async function AdminUsersPage() {
  const session = await auth();
  if (!session) redirect('/login');

  if (!session.user.roles.includes('crm-admin')) {
    redirect('/dashboard');
  }

  const users = await listAdminUsers(session.accessToken);

  return (
    <div>
      <h1 className="text-2xl font-semibold text-foreground mb-6">Gerenciamento de Usuários</h1>
      <UserManagementClient users={users} />
    </div>
  );
}
