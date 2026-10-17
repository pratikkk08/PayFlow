import type { LucideIcon } from 'lucide-react';
import {
  CreditCard,
  KeyRound,
  LayoutDashboard,
  RefreshCcw,
  Settings,
  Store,
  Webhook,
} from 'lucide-react';

export type NavItem = {
  label: string;
  to: string;
  icon: LucideIcon;
  description: string;
};

export const DASHBOARD_NAV: NavItem[] = [
  {
    label: 'Overview',
    to: '/dashboard',
    icon: LayoutDashboard,
    description: 'Merchant activity summary. Live analytics will connect in a later task.',
  },
  {
    label: 'Payments',
    to: '/payments',
    icon: CreditCard,
    description: 'Simulated payment list and detail views are not connected yet.',
  },
  {
    label: 'Refunds',
    to: '/refunds',
    icon: RefreshCcw,
    description: 'Refund history and create flows are not connected yet.',
  },
  {
    label: 'Merchants',
    to: '/merchants',
    icon: Store,
    description: 'Create and manage sandbox merchant accounts for your user.',
  },
  {
    label: 'API Keys',
    to: '/api-keys',
    icon: KeyRound,
    description: 'Create, list, and revoke sandbox TEST API keys for the selected merchant.',
  },
  {
    label: 'Webhooks',
    to: '/webhooks',
    icon: Webhook,
    description: 'Register HTTPS webhook endpoints and manage sandbox event subscriptions.',
  },
  {
    label: 'Settings',
    to: '/settings',
    icon: Settings,
    description: 'Account and workspace settings are not connected yet.',
  },
];

export function findNavItem(pathname: string): NavItem | undefined {
  return DASHBOARD_NAV.find((item) => pathname === item.to || pathname.startsWith(`${item.to}/`));
}
