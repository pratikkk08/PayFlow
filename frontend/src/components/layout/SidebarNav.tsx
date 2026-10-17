import { NavLink } from 'react-router-dom';
import { Shield } from 'lucide-react';
import { DASHBOARD_NAV } from '@/app/navigation';

type SidebarNavProps = {
  onNavigate?: () => void;
  id?: string;
};

export function SidebarNav({ onNavigate, id }: SidebarNavProps) {
  return (
    <div id={id} className="flex h-full flex-col">
      <div className="flex items-center gap-2.5 border-b border-white/10 px-5 py-5">
        <Shield className="h-6 w-6 text-blue-300" aria-hidden />
        <div>
          <p className="text-sm font-semibold tracking-wide text-white">PayFlow</p>
          <p className="text-xs text-pf-sidebar-muted">Sandbox console</p>
        </div>
      </div>

      <nav className="flex-1 space-y-1 px-3 py-4" aria-label="Dashboard">
        {DASHBOARD_NAV.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/dashboard'}
              onClick={onNavigate}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-pf px-3 py-2.5 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-300 ${
                  isActive
                    ? 'bg-white/10 text-white'
                    : 'text-pf-sidebar-muted hover:bg-white/5 hover:text-white'
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <Icon className="h-4 w-4 shrink-0" aria-hidden />
                  <span>{item.label}</span>
                  {isActive ? <span className="sr-only">(current)</span> : null}
                </>
              )}
            </NavLink>
          );
        })}
      </nav>

      <p className="border-t border-white/10 px-5 py-4 text-xs text-pf-sidebar-muted">
        Sandbox console — merchant and payment UIs arrive in later tasks.
      </p>
    </div>
  );
}
