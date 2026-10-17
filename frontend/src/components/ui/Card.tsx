import type { HTMLAttributes, ReactNode } from 'react';

type CardProps = HTMLAttributes<HTMLElement> & {
  children: ReactNode;
  as?: 'div' | 'section' | 'article';
};

export function Card({ children, className = '', as: Tag = 'div', ...props }: CardProps) {
  return (
    <Tag
      className={`rounded-pf border border-pf-border bg-pf-panel p-5 shadow-pf-sm ${className}`}
      {...props}
    >
      {children}
    </Tag>
  );
}
