import React from 'react';

export interface FieldProps {
  label: string;
  htmlFor?: string;
  hint?: React.ReactNode;
  error?: string;
  badge?: React.ReactNode;
  required?: boolean;
  children: React.ReactNode;
}

export function Field({
  label,
  htmlFor,
  hint,
  error,
  badge,
  required,
  children,
}: FieldProps) {
  return (
    <div className="field">
      <div className="field__head">
        <label className="field__label" htmlFor={htmlFor}>
          {label}
          {required && <span className="field__required" aria-hidden="true"> *</span>}
        </label>
        {badge && <div className="field__badge">{badge}</div>}
      </div>
      {children}
      {hint && !error && <div className="field__hint">{hint}</div>}
      {error && (
        <div className="field__error" role="alert">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
          <span>{error}</span>
        </div>
      )}
    </div>
  );
}
