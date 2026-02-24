import { auth } from '@/lib/auth';

export default auth((req) => {
  const isAuthenticated = !!req.auth;
  const { pathname } = req.nextUrl;

  const isProtected = pathname.startsWith('/dashboard') || pathname.startsWith('/admin');

  if (!isAuthenticated && isProtected) {
    return Response.redirect(new URL('/login', req.url));
  }
});

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico).*)'],
};
