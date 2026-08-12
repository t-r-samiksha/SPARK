import React, { forwardRef, useId, type InputHTMLAttributes } from 'react';

export interface TextInputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
  mono?: boolean;
  startAdornment?: React.ReactNode;
}

export const TextInput = forwardRef<HTMLInputElement, TextInputProps>(function TextInput(
  { invalid = false, mono = false, startAdornment, id: externalId, className = '', ...rest },
  ref,
) {
  const generatedId = useId();
  const inputId = externalId || generatedId;

  return (
    <div className={`input-wrapper ${startAdornment ? 'has-adornment' : ''}`}>
      {startAdornment && <div className="input-adornment">{startAdornment}</div>}
      <input
        ref={ref}
        id={inputId}
        className={`input ${mono ? 'input--mono' : ''} ${className}`.trim()}
        aria-invalid={invalid || undefined}
        {...rest}
      />
    </div>
  );
});
