import { useEffect, useId, useRef, useState, type RefObject } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronDown, LogOut, Menu, UserRound, X } from 'lucide-react';
import { useAuth } from '@/features/auth/AuthContext';
import { MerchantSelector } from '@/components/layout/MerchantSelector';
import { Button } from '@/components/ui/Button';

type TopBarProps = {
  title: string;
  mobileOpen: boolean;
  onToggleMobile: () => void;
  menuButtonRef?: RefObject<HTMLButtonElement | null>;
};

export function TopBar({ title, mobileOpen, onToggleMobile, menuButtonRef }: TopBarProps) {
  const { user, logout, status } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const menuId = useId();

  useEffect(() => {
    if (!menuOpen) {
      return;
    }
    const onPointerDown = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [menuOpen]);

  async function onLogout() {
    setLoggingOut(true);
    try {
      await logout();
      navigate('/login', { replace: true });
    } finally {
      setLoggingOut(false);
      setMenuOpen(false);
    }
  }

  const displayName = user?.fullName?.trim() || user?.email || 'Account';
  const displayEmail = user?.email;

  return (
    <header className="sticky top-0 z-20 border-b border-pf-border bg-pf-panel/95 backdrop-blur">
      <div className="flex items-center justify-between gap-3 px-4 py-3 sm:px-6">
        <div className="flex min-w-0 items-center gap-3">
          <Button
            ref={menuButtonRef}
            variant="ghost"
            size="sm"
            className="lg:hidden"
            aria-expanded={mobileOpen}
            aria-controls="mobile-sidebar"
            aria-label={mobileOpen ? 'Close navigation menu' : 'Open navigation menu'}
            onClick={onToggleMobile}
          >
            {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </Button>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-pf-ink">{title}</p>
            <p className="hidden text-xs text-pf-muted sm:block">Operator console</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <MerchantSelector />

          <div className="relative" ref={menuRef}>
            <button
              type="button"
              className="inline-flex max-w-[14rem] items-center gap-2 rounded-pf border border-pf-border bg-pf-panel px-2.5 py-1.5 text-sm text-pf-ink-soft shadow-pf-sm"
              aria-label={user ? `Account menu for ${displayName}` : 'Account menu'}
              aria-expanded={menuOpen}
              aria-haspopup="menu"
              aria-controls={menuId}
              onClick={() => setMenuOpen((open) => !open)}
              disabled={status !== 'authenticated'}
            >
              <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-pf-primary-soft text-pf-primary">
                <UserRound className="h-4 w-4" aria-hidden />
              </span>
              <span className="hidden min-w-0 flex-col items-start sm:flex">
                {user ? (
                  <>
                    <span className="truncate font-medium text-pf-ink">{displayName}</span>
                    {displayEmail && displayEmail !== displayName ? (
                      <span className="truncate text-xs text-pf-muted">{displayEmail}</span>
                    ) : null}
                  </>
                ) : (
                  <span className="text-pf-muted">Loading account…</span>
                )}
              </span>
              <ChevronDown className="hidden h-4 w-4 sm:block" aria-hidden />
            </button>

            {menuOpen ? (
              <div
                id={menuId}
                role="menu"
                className="absolute right-0 mt-2 w-56 rounded-pf border border-pf-border bg-pf-panel p-1 shadow-pf-md"
              >
                <div className="border-b border-pf-border px-3 py-2 sm:hidden">
                  <p className="truncate text-sm font-medium text-pf-ink">{displayName}</p>
                  {displayEmail ? (
                    <p className="truncate text-xs text-pf-muted">{displayEmail}</p>
                  ) : null}
                </div>
                <button
                  type="button"
                  role="menuitem"
                  className="flex w-full items-center gap-2 rounded-pf px-3 py-2 text-sm text-pf-ink-soft hover:bg-slate-50 disabled:opacity-60"
                  onClick={() => void onLogout()}
                  disabled={loggingOut}
                >
                  <LogOut className="h-4 w-4" aria-hidden />
                  {loggingOut ? 'Signing out…' : 'Sign out'}
                </button>
              </div>
            ) : null}
          </div>
        </div>
      </div>
    </header>
  );
}
