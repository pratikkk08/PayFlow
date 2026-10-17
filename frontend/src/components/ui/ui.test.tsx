import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Input } from '@/components/ui/Input';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorState } from '@/components/ui/ErrorState';

describe('UI primitives accessibility', () => {
  it('associates Input labels and error messages', () => {
    render(<Input label="Email" error="Required" />);
    const field = screen.getByLabelText('Email');
    expect(field).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('alert')).toHaveTextContent('Required');
  });

  it('exposes ErrorState as an alert', () => {
    render(<ErrorState description="Request failed" />);
    expect(screen.getByRole('alert')).toHaveTextContent('Request failed');
  });

  it('renders Skeleton as decorative', () => {
    render(<Skeleton className="h-4 w-24" />);
    expect(screen.getByTestId('skeleton')).toHaveAttribute('aria-hidden');
  });
});
