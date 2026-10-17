import { useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Building2, Check, ChevronDown, Plus } from 'lucide-react';
import { useMerchantSelection } from '@/features/merchants/MerchantContext';
import { MerchantStatusBadge } from '@/features/merchants/MerchantStatusBadge';
import { Button } from '@/components/ui/Button';

export function MerchantSelector() {
  const {
    merchants,
    selectedMerchant,
    selectMerchant,
    isLoading,
    isError,
    refetch,
  } = useMerchantSelection();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const menuId = useId();

  useEffect(() => {
    if (!open) {
      return;
    }
    const onPointerDown = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const label = isLoading
    ? 'Loading…'
    : selectedMerchant?.name ?? (merchants.length === 0 ? 'No merchants' : 'Select merchant');

  return (
    <div className="relative" ref={rootRef}>
      <button
        type="button"
        className="inline-flex max-w-[16rem] items-center gap-2 rounded-pf border border-pf-border bg-pf-panel px-3 py-1.5 text-sm text-pf-muted shadow-pf-sm"
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-controls={menuId}
        aria-label="Merchant selector"
        onClick={() => setOpen((value) => !value)}
      >
        <Building2 className="h-4 w-4 shrink-0 text-pf-primary" aria-hidden />
        <span className="hidden min-w-0 sm:inline">
          <span className="text-pf-muted">Merchant</span>{' '}
          <span className="font-medium text-pf-ink-soft">{label}</span>
        </span>
        <span className="truncate font-medium text-pf-ink-soft sm:hidden">{label}</span>
        <ChevronDown className="h-4 w-4 shrink-0" aria-hidden />
      </button>

      {open ? (
        <div
          id={menuId}
          role="listbox"
          aria-label="Owned merchants"
          className="absolute right-0 z-30 mt-2 w-72 rounded-pf border border-pf-border bg-pf-panel p-1 shadow-pf-md"
        >
          {isError ? (
            <div className="space-y-2 px-3 py-2 text-sm">
              <p className="text-pf-danger">Could not load merchants.</p>
              <Button
                size="sm"
                variant="secondary"
                onClick={() => {
                  refetch();
                }}
              >
                Retry
              </Button>
            </div>
          ) : null}

          {!isError && merchants.length === 0 && !isLoading ? (
            <div className="space-y-2 px-3 py-3 text-sm">
              <p className="text-pf-muted">No merchants yet. Create one to continue.</p>
              <Link to="/merchants/new" onClick={() => setOpen(false)}>
                <Button size="sm">
                  <Plus className="h-4 w-4" aria-hidden />
                  Create merchant
                </Button>
              </Link>
            </div>
          ) : null}

          {merchants.map((merchant) => {
            const selected = merchant.id === selectedMerchant?.id;
            return (
              <button
                key={merchant.id}
                type="button"
                role="option"
                aria-selected={selected}
                className="flex w-full items-start gap-2 rounded-pf px-3 py-2 text-left text-sm hover:bg-slate-50"
                onClick={() => {
                  selectMerchant(merchant.id);
                  setOpen(false);
                }}
              >
                <span className="mt-0.5 w-4 shrink-0 text-pf-primary">
                  {selected ? <Check className="h-4 w-4" aria-hidden /> : null}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium text-pf-ink">{merchant.name}</span>
                  <span className="mt-1 inline-flex">
                    <MerchantStatusBadge status={merchant.status} />
                  </span>
                </span>
              </button>
            );
          })}

          {merchants.length > 0 ? (
            <div className="border-t border-pf-border p-1">
              <Link
                to="/merchants"
                className="block rounded-pf px-3 py-2 text-sm font-medium text-pf-primary hover:bg-pf-primary-soft"
                onClick={() => setOpen(false)}
              >
                Manage merchants
              </Link>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
