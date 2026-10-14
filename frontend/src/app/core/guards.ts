import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionService } from './session.service';
export const authGuard: CanActivateFn = () => {
  const session = inject(SessionService),
    router = inject(Router);
  return session.restorePending()
    ? router.createUrlTree(['/reconnect'])
    : session.user()
      ? true
      : router.createUrlTree(['/login']);
};
export const platformGuard: CanActivateFn = () =>
  inject(SessionService).user()?.superAdmin ? true : inject(Router).createUrlTree(['/forbidden']);
export const societyGuard: CanActivateFn = () =>
  inject(SessionService).context() ? true : inject(Router).createUrlTree(['/select-society']);
export const permissionGuard: CanActivateFn = (route) => {
  const session = inject(SessionService);
  return session.can(route.data['permission'])
    ? true
    : inject(Router).createUrlTree(['/forbidden']);
};
