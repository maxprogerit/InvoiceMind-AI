import { cva, type VariantProps } from "class-variance-authority";
import { Slot } from "radix-ui";
import * as React from "react";

import { cn } from "@/lib/utils";

export const buttonVariants = cva(
  "inline-flex shrink-0 items-center justify-center gap-2 whitespace-nowrap rounded-control font-mono text-[13px] font-medium transition-[background,border-color,color,box-shadow,transform] duration-150 active:scale-[0.98] disabled:pointer-events-none disabled:opacity-45 [&_svg]:size-4 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        default: "bg-accent text-accent-fg hover:bg-accent-hover shadow-[0_0_0_1px_rgb(255_107_87/0.0)] hover:shadow-glow",
        outline: "border border-line-strong bg-transparent text-fg hover:border-accent/60 hover:bg-surface-2",
        secondary: "bg-surface-2 text-fg hover:bg-surface-3",
        ghost: "text-muted hover:bg-surface-2 hover:text-fg",
        destructive: "border border-bad/40 bg-bad/10 text-bad hover:bg-bad/20",
        success: "border border-ok/40 bg-ok/10 text-ok hover:bg-ok/20",
        link: "text-accent underline-offset-4 hover:underline",
      },
      size: {
        default: "h-10 px-4",
        sm: "h-8 px-3 text-xs",
        lg: "h-12 px-6 text-sm",
        icon: "size-9",
        /* a default-height button that fills its container, e.g. the call to action of a card */
        block: "h-10 w-full px-4",
      },
    },
    compoundVariants: [
      /*
       * Large primary surfaces (the large and the full-width button) use the soft accent: a solid coral block that size
       * dominates the screen. The label stays coral on the soft fill (5.5:1, above the 4.5:1 AA minimum) and the border
       * keeps the edge. Small primary buttons keep the full accent, where it is a signal rather than a surface.
       */
      {
        variant: "default",
        size: ["lg", "block"],
        className:
          "border border-accent/40 bg-accent-soft text-accent hover:border-accent hover:bg-accent-soft hover:text-accent-hover",
      },
    ],
    defaultVariants: { variant: "default", size: "default" },
  },
);

export interface ButtonProps extends React.ComponentProps<"button">, VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

export function Button({ className, variant, size, asChild = false, ...props }: ButtonProps) {
  const Comp = asChild ? Slot.Root : "button";
  return <Comp className={cn(buttonVariants({ variant, size }), className)} {...props} />;
}
