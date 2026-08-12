interface SkeletonProps {
  width?: string;
  height?: string;
  className?: string;
}

export function Skeleton({ width = '100%', height = '0.875rem', className = '' }: SkeletonProps) {
  return <span className={`skeleton ${className}`.trim()} style={{ width, height }} aria-hidden="true" />;
}
