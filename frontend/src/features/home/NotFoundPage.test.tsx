import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { HomePage } from '@/features/home/HomePage';
import { NotFoundPage } from '@/features/home/NotFoundPage';

describe('NotFound route', () => {
  it('renders the 404 page for unknown paths', () => {
    const router = createMemoryRouter(
      [
        { path: '/', element: <HomePage /> },
        { path: '*', element: <NotFoundPage /> },
      ],
      { initialEntries: ['/missing-route'] },
    );

    render(<RouterProvider router={router} />);

    expect(screen.getByRole('heading', { name: /page not found/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /back to home/i })).toHaveAttribute('href', '/');
  });
});
