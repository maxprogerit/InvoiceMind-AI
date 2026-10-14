import * as React from "react";

import { cn } from "@/lib/utils";

export function Table({ className, ...props }: React.ComponentProps<"table">) {
  return (
    <div className="w-full overflow-x-auto">
      <table className={cn("w-full border-separate border-spacing-0 text-sm", className)} {...props} />
    </div>
  );
}

export function THead({ className, ...props }: React.ComponentProps<"thead">) {
  return <thead className={className} {...props} />;
}

export function TBody({ className, ...props }: React.ComponentProps<"tbody">) {
  return <tbody className={className} {...props} />;
}

/** Header cell: the mono uppercase caption style from the mockups. */
export function TH({ className, ...props }: React.ComponentProps<"th">) {
  return <th className={cn("label-mono h-10 border-b border-line px-3 text-left font-medium first:pl-4 last:pr-4", className)} {...props} />;
}

export function TR({ className, ...props }: React.ComponentProps<"tr">) {
  return <tr className={cn("group", className)} {...props} />;
}

export function TD({ className, ...props }: React.ComponentProps<"td">) {
  return <td className={cn("h-[52px] border-b border-line/70 px-3 align-middle first:pl-4 last:pr-4", className)} {...props} />;
}
