import { PlaceholderPage } from '@/features/dashboard/PlaceholderPage';
import { DASHBOARD_NAV } from '@/app/navigation';

const meta = DASHBOARD_NAV.find((item) => item.to === '/settings')!;

export function SettingsPage() {
  return <PlaceholderPage title={meta.label} description={meta.description} />;
}
