export interface User {
  id: string;
  name: string;
  email: string | null;
  mobile: string | null;
  superAdmin: boolean;
  status: string;
}
export interface Society {
  id: string;
  name: string;
  code: string;
  timezone: string;
  status: string;
  address?: string;
  city?: string;
  logoUrl?: string;
}
export interface Membership {
  id: string;
  societyId: string;
  roles: string;
  society: Society;
}
export interface Context {
  society: Society;
  permissions: string[];
  roles: string[];
  flats: string[];
}
export interface Branding {
  applicationName: string;
  shortName: string;
  logoUrl?: string;
  faviconUrl?: string;
  footerText?: string;
  supportEmail?: string;
  supportPhone?: string;
}
// API records have module-specific fields; forms and tables use a shared projection.
export type Row = { id: string; [key: string]: any };
export interface Option {
  value: string;
  label: string;
}
export interface Field {
  key: string;
  label: string;
  type?: string;
  required?: boolean;
  options?: string[];
  lookup?: string;
  max?: number;
  hint?: string;
  value?: any;
}
export interface ModuleDefinition {
  title: string;
  singular: string;
  description: string;
  icon: string;
  endpoint: string;
  permission?: string;
  createPermission?: string;
  platform?: boolean;
  columns: { key: string; label: string }[];
  fields: Field[];
  edit?: boolean;
}
