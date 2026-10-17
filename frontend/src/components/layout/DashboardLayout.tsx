import { useEffect, useId, useRef, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { findNavItem } from '@/app/navigation';
import { SidebarNav } from '@/components/layout/SidebarNav';
import { TopBar } from '@/components/layout/TopBar';
import { useMerchantSelection } from '@/features/merchants/MerchantContext';
import { Badge } from '@/components/ui/Badge';

/**
 * Authenticated-area visual shell. Access control is enforced by ProtectedRoute + backend JWT.
 */
export function DashboardLayout() {
  const location = useLocation();
  const { selectedMerchant } = useMerchantSelection();
  const [mobileOpen, setMobileOpen] = useState(false);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const titleId = useId();
  const navItem = findNavItem(location.pathname);
  const pageTitle = navItem?.label ?? 'Dashboard';

  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!mobileOpen) {
      return;
    }

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMobileOpen(false);
        menuButtonRef.current?.focus();
      }
    };

    document.addEventListener('keydown', onKeyDown);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    closeButtonRef.current?.focus();

    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [mobileOpen]);

  return (
    <div className="min-h-screen bg-pf-surface lg:grid lg:grid-cols-[16.5rem_1fr]">
      <aside
        className="hidden bg-pf-sidebar lg:sticky lg:top-0 lg:block lg:h-screen lg:overflow-y-auto"
        aria-label="Primary"
      >
        <SidebarNav />
      </aside>

      {mobileOpen ? (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-labelledby={titleId}>
          <button
            type="button"
            className="absolute inset-0 bg-slate-900/50"
            aria-label="Close navigation overlay"
            onClick={() => {
              setMobileOpen(false);
              menuButtonRef.current?.focus();
            }}
          />
          <div
            id="mobile-sidebar"
            className="relative flex h-full w-[min(18rem,88vw)] flex-col bg-pf-sidebar shadow-pf-md"
          >
            <div className="sr-only" id={titleId}>
              Navigation menu
            </div>
            <button
              ref={closeButtonRef}
              type="button"
              className="absolute top-3 right-3 rounded-pf p-2 text-white hover:bg-white/10"
              aria-label="Close navigation menu"
              onClick={() => {
                setMobileOpen(false);
                menuButtonRef.current?.focus();
              }}
            >
              Close
            </button>
            <SidebarNav onNavigate={() => setMobileOpen(false)} />
          </div>
        </div>
      ) : null}

      <div className="flex min-w-0 flex-col">
        <TopBar
          title={pageTitle}
          mobileOpen={mobileOpen}
          onToggleMobile={() => setMobileOpen((open) => !open)}
          menuButtonRef={menuButtonRef}
        />

        <div className="border-b border-pf-border bg-pf-panel px-4 py-2 sm:px-6">
          <div className="flex flex-wrap items-center gap-2 text-xs text-pf-muted">
            <span>PayFlow</span>
            <span aria-hidden>/</span>
            <span className="font-medium text-pf-ink-soft">
              {selectedMerchant?.name ?? 'No merchant selected'}
            </span>
            <span aria-hidden>/</span>
            <span className="font-medium text-pf-ink-soft">{pageTitle}</span>
            <Badge tone="info">Sandbox</Badge>
          </div>
        </div>

        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <div className="mx-auto max-w-6xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
