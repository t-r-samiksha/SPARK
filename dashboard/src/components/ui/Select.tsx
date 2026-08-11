import { forwardRef, useId, type SelectHTMLAttributes } from 'react';

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  invalid?: boolean;
  mono?: boolean;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { invalid = false, mono = false, id: externalId, className = '', children, ...rest },
  ref,
) {
  const generatedId = useId();
  const selectId = externalId || generatedId;

  return (
    <select
      ref={ref}
      id={selectId}
      className={`select ${mono ? 'select--mono' : ''} ${className}`.trim()}
      aria-invalid={invalid || undefined}
      {...rest}
    >
      {children}
    </select>
  );
});
