'use client';

import { useState, useTransition } from 'react';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Switch } from '@/components/ui/switch';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { updateUserRoles, updateUserStatus } from '@/app/(dashboard)/admin/users/actions';
import type { UserAdminDto } from '@/lib/api/admin';

const ALL_ROLES = ['crm-admin', 'crm-gerente', 'crm-vendedor'] as const;

interface Props {
  users: UserAdminDto[];
}

export function UserManagementClient({ users }: Props) {
  const [isPending, startTransition] = useTransition();
  const [confirmUser, setConfirmUser] = useState<UserAdminDto | null>(null);
  const [editUser, setEditUser] = useState<UserAdminDto | null>(null);
  const [editRoles, setEditRoles] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);

  function handleToggleStatus(user: UserAdminDto, checked: boolean) {
    if (!checked) {
      // Disabling requires confirmation
      setConfirmUser(user);
    } else {
      startTransition(async () => {
        try {
          await updateUserStatus(user.keycloakId, true);
          setError(null);
        } catch {
          setError('Falha ao ativar usuário.');
        }
      });
    }
  }

  function handleConfirmDisable() {
    if (!confirmUser) return;
    const user = confirmUser;
    setConfirmUser(null);
    startTransition(async () => {
      try {
        await updateUserStatus(user.keycloakId, false);
        setError(null);
      } catch {
        setError('Falha ao desativar usuário.');
      }
    });
  }

  function openEditRoles(user: UserAdminDto) {
    setEditUser(user);
    setEditRoles([...user.roles]);
  }

  function toggleRole(role: string) {
    setEditRoles((prev) =>
      prev.includes(role) ? prev.filter((r) => r !== role) : [...prev, role]
    );
  }

  function handleSaveRoles() {
    if (!editUser) return;
    const user = editUser;
    const roles = [...editRoles];
    setEditUser(null);
    startTransition(async () => {
      try {
        await updateUserRoles(user.keycloakId, roles);
        setError(null);
      } catch {
        setError('Falha ao atualizar roles.');
      }
    });
  }

  return (
    <>
      {error && (
        <div className="mb-4 rounded-md border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Nome / Email</TableHead>
              <TableHead>Cargo</TableHead>
              <TableHead>Roles</TableHead>
              <TableHead>Ativo</TableHead>
              <TableHead className="text-right">Ações</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {users.map((user) => (
              <TableRow key={user.keycloakId} className={!user.enabled ? 'opacity-50' : ''}>
                <TableCell>
                  <div className="font-medium">{user.nome}</div>
                  <div className="text-xs text-muted-foreground">{user.email}</div>
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {user.cargo ?? '—'}
                </TableCell>
                <TableCell>
                  <div className="flex flex-wrap gap-1">
                    {user.roles.length > 0 ? (
                      user.roles.map((r) => (
                        <Badge key={r} variant="secondary" className="text-xs">
                          {r}
                        </Badge>
                      ))
                    ) : (
                      <span className="text-xs text-muted-foreground">sem roles</span>
                    )}
                  </div>
                </TableCell>
                <TableCell>
                  <Switch
                    checked={user.enabled}
                    disabled={isPending}
                    onCheckedChange={(checked) => handleToggleStatus(user, checked)}
                    aria-label={`Ativar/desativar ${user.nome}`}
                  />
                </TableCell>
                <TableCell className="text-right">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={isPending}
                    onClick={() => openEditRoles(user)}
                  >
                    Roles
                  </Button>
                </TableCell>
              </TableRow>
            ))}
            {users.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground py-8">
                  Nenhum usuário encontrado.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {/* Confirm disable dialog */}
      <AlertDialog open={!!confirmUser} onOpenChange={(open) => !open && setConfirmUser(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Desativar usuário?</AlertDialogTitle>
            <AlertDialogDescription>
              {confirmUser?.nome} perderá acesso ao sistema imediatamente. Esta ação pode ser
              revertida reativando o usuário.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancelar</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90"
              onClick={handleConfirmDisable}
            >
              Desativar
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Edit roles dialog */}
      <AlertDialog open={!!editUser} onOpenChange={(open) => !open && setEditUser(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Editar roles — {editUser?.nome}</AlertDialogTitle>
            <AlertDialogDescription>
              Selecione as roles do usuário no sistema.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="flex flex-col gap-3 py-4">
            {ALL_ROLES.map((role) => (
              <label key={role} className="flex items-center gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  className="size-4 rounded border-border accent-primary"
                  checked={editRoles.includes(role)}
                  onChange={() => toggleRole(role)}
                />
                <span className="text-sm">{role}</span>
              </label>
            ))}
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancelar</AlertDialogCancel>
            <AlertDialogAction onClick={handleSaveRoles} disabled={isPending}>
              Salvar
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
