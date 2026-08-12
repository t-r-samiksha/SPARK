import { useId, type ChangeEvent } from 'react';

export interface ToggleProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label?: string;
  disabled?: boolean;
  hint?: string;
  id?: string;
}

export function Toggle({
  checked,
  onChange,
  label,
  disabled = false,
  hint,
  id: externalId,
}: ToggleProps) {
  const generatedId = useId();
  const toggleId = externalId || generatedId;

  return (
    <div className="toggle-container">
      <label className="toggle" htmlFor={toggleId}>
        <input
          id={toggleId}
          type="checkbox"
          role="switch"
          checked={checked}
          disabled={disabled}
          aria-checked={checked}
          onChange={(e: ChangeEvent<HTMLInputElement>) => onChange(e.target.checked)}
        />
        <span className="toggle__track" aria-hidden="true">
          <span className="toggle__thumb" />
        </span>
        {label && <span className="toggle__label">{label}</span>}
      </label>
      {hint && <span className="toggle__hint">{hint}</span>}
    </div>
  );
}
