import type { SelectHTMLAttributes } from "react";

type Props = SelectHTMLAttributes<HTMLSelectElement>;

export function Select({ className = "", children, ...props }: Props) {
  return (
    <select className={`input ${className}`.trim()} {...props}>
      {children}
    </select>
  );
}
