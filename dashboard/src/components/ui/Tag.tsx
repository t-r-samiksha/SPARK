import React from 'react';

export interface TagProps {
  tone?: 'danger' | 'warning' | 'success' | 'info' | 'accent' | 'neutral';
  dot?: boolean;
  pulse?: boolean;
  className?: string;
  children: React.ReactNode;
}

export function Tag({
  tone = 'neutral',
  dot = false,
  pulse = false,
  className = '',
  children,
}: TagProps) {
  return (
    <span className={`tag tag--${tone} ${className}`}>
      {dot && (
        <span
          className={`tag__dot ${pulse ? 'tag__dot--pulse' : ''}`}
          aria-hidden="true"
        />
      )}
      {children}
    </span>
  );
}
