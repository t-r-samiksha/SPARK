import React from 'react';

export interface FeedbackProps {
  tone?: 'danger' | 'warn' | 'success' | 'info' | 'neutral';
  code?: string;
  live?: 'polite' | 'assertive' | 'off';
  onDismiss?: () => void;
  children: React.ReactNode;
}

export function Feedback({
  tone = 'neutral',
  code,
  live = 'polite',
  onDismiss,
  children,
}: FeedbackProps) {
  return (
    <div
      className={`feedback feedback--${tone}`}
      role={tone === 'danger' ? 'alert' : 'status'}
      aria-live={live}
    >
      {code && <span className="feedback__code">{code}</span>}
      <div className="feedback__content">{children}</div>
      {onDismiss && (
        <button
          type="button"
          className="feedback__dismiss"
          onClick={onDismiss}
          aria-label="Dismiss message"
        >
          ×
        </button>
      )}
    </div>
  );
}
