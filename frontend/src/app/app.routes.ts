import { Routes } from '@angular/router';
import { authGuard, platformGuard, societyGuard, permissionGuard } from './core/guards';
import { MODULES } from './features/module-definitions';
const utility = () => import('./features/utility-page').then((m) => m.UtilityPage);
export const routes: Routes = [
  {
    path: 'reconnect',
    loadComponent: () => import('./features/reconnect').then((m) => m.Reconnect),
  },
  { path: 'signup', loadComponent: () => import('./features/signup').then((m) => m.Signup) },
  {
    path: 'membership-requests',
    canActivate: [authGuard, societyGuard, permissionGuard],
    data: { permission: 'MEMBERSHIP_MANAGE' },
    loadComponent: () => import('./features/membership-requests').then((m) => m.MembershipRequests),
  },
  { path: 'login', loadComponent: () => import('./features/login').then((m) => m.Login) },
  {
    path: 'forgot-password',
    loadComponent: () => import('./features/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    path: 'select-society',
    canActivate: [authGuard],
    loadComponent: () => import('./features/select-society').then((m) => m.SelectSociety),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard, societyGuard],
    loadComponent: () => import('./features/dashboard').then((m) => m.Dashboard),
  },
  {
    path: 'platform',
    canActivate: [authGuard, platformGuard],
    loadComponent: () => import('./features/dashboard').then((m) => m.Dashboard),
  },
  ...Object.entries(MODULES).map(([path, def]) => ({
    path,
    canActivate: def.platform
      ? [authGuard, platformGuard]
      : [authGuard, societyGuard, permissionGuard],
    data: { module: path, permission: def.permission },
    loadComponent: () => import('./features/resource-page').then((m) => m.ResourcePage),
  })),
  {
    path: 'branding',
    canActivate: [authGuard, platformGuard],
    data: {
      page: 'branding',
      title: 'Application branding',
      description: 'A familiar identity for every community.',
    },
    loadComponent: utility,
  },
  {
    path: 'roles',
    canActivate: [authGuard, platformGuard],
    data: {
      page: 'roles',
      title: 'Roles & permissions',
      description: 'Thoughtful access, clear responsibilities.',
    },
    loadComponent: utility,
  },
  {
    path: 'diagnostics',
    canActivate: [authGuard, platformGuard],
    data: {
      page: 'diagnostics',
      title: 'System diagnostics',
      description: 'A clear view of your platform’s connection.',
    },
    loadComponent: utility,
  },
  {
    path: 'settings',
    canActivate: [authGuard, societyGuard, permissionGuard],
    data: {
      page: 'settings',
      title: 'Society settings',
      description: 'The details that make this community yours.',
      permission: 'SOCIETY_UPDATE',
    },
    loadComponent: utility,
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    data: {
      page: 'profile',
      title: 'Your profile',
      description: 'Your account and your communities.',
    },
    loadComponent: utility,
  },
  {
    path: 'notifications',
    canActivate: [authGuard, societyGuard],
    data: {
      page: 'notifications',
      title: 'Notifications',
      description: 'The little updates that keep you connected.',
    },
    loadComponent: utility,
  },
  {
    path: 'search',
    canActivate: [authGuard, societyGuard],
    data: {
      page: 'search',
      title: 'Search your society',
      description: 'Find what you need, close to home.',
    },
    loadComponent: utility,
  },
  {
    path: 'reports',
    canActivate: [authGuard, societyGuard, permissionGuard],
    data: {
      page: 'reports',
      title: 'Reports',
      description: 'A practical snapshot of your community.',
      permission: 'REPORT_VIEW',
    },
    loadComponent: utility,
  },
  {
    path: 'forbidden',
    data: {
      page: 'forbidden',
      title: 'This area needs permission',
      description: 'Your current role does not have access. Contact your society administrator.',
    },
    loadComponent: utility,
  },
  {
    path: 'offline',
    data: {
      page: 'offline',
      title: 'You are offline',
      description: 'Your application is ready. Reconnect to load current society records.',
    },
    loadComponent: utility,
  },
  {
    path: 'server-unavailable',
    data: {
      page: 'server-unavailable',
      title: 'Society server is currently unavailable',
      description: 'Please try again when the society server is online.',
    },
    loadComponent: utility,
  },
  {
    path: 'unexpected-error',
    data: {
      page: 'unexpected-error',
      title: 'Something went wrong',
      description: 'Please return home and try again.',
    },
    loadComponent: utility,
  },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: '**',
    data: {
      page: 'not-found',
      title: 'We couldn’t find that page',
      description: 'The link may have changed. Let’s get you back home.',
    },
    loadComponent: utility,
  },
];
