import { auth } from '@/lib/auth';

export default auth((req) => {
  const isAuthenticated = !!req.auth;
  const isDashboard = req.nextUrl.pathname.startsWith('/dashboard');

  if (!isAuthenticated && isDashboard) {
    return Response.redirect(new URL('/login', req.url));
  }
});

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico).*)'],
};
