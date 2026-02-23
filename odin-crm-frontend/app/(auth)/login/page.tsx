import { Alert, AlertDescription } from '@/components/ui/alert';
import { SignInButton } from '@/components/auth/SignInButton';

const ERROR_MESSAGES: Record<string, string> = {
  OAuthSignin: 'Erro ao iniciar o login. Tente novamente.',
  OAuthCallback: 'Erro no retorno do Keycloak. Tente novamente.',
  OAuthCreateAccount: 'Não foi possível criar a conta. Contate o administrador.',
  OAuthAccountNotLinked: 'Esta conta já está vinculada a outro método de login.',
  AccessDenied: 'Acesso negado. Você não tem permissão para acessar este sistema.',
  RefreshAccessTokenError: 'Sua sessão expirou. Por favor, faça login novamente.',
  Default: 'Ocorreu um erro durante o login. Tente novamente.',
};

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  const { error } = await searchParams;
  const errorMessage = error ? (ERROR_MESSAGES[error] ?? ERROR_MESSAGES.Default) : null;

  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="w-full max-w-sm space-y-6 px-4">
        <div className="space-y-2 text-center">
          <h1 className="text-2xl font-semibold tracking-tight">ODIN CRM</h1>
          <p className="text-sm text-muted-foreground">
            Faça login com suas credenciais corporativas
          </p>
        </div>

        {errorMessage && (
          <Alert variant="destructive">
            <AlertDescription>{errorMessage}</AlertDescription>
          </Alert>
        )}

        <SignInButton />
      </div>
    </div>
  );
}
