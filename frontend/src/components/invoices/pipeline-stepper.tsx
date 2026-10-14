import { Check, X } from "lucide-react";

import type { InvoiceStatus } from "@/lib/api/types";
import { PIPELINE_STEPS } from "@/lib/status";
import { cn } from "@/lib/utils";

/** Upload -> OCR + AI -> Validation -> Decision, driven purely by the invoice status. */
export function PipelineStepper({ status, className }: { status: InvoiceStatus; className?: string }) {
  const failed = status === "FAILED";
  const rejected = status === "REJECTED";
  const activeIndex = PIPELINE_STEPS.findIndex((s) => !s.reached(status));

  return (
    <ol className={cn("flex items-center", className)} aria-label="Processing pipeline">
      {PIPELINE_STEPS.map((step, i) => {
        const done = step.reached(status) && !(failed && i >= 1);
        const current = !failed && i === activeIndex;
        const isFailedStep = failed && i === 1;
        const isRejectedStep = rejected && i === PIPELINE_STEPS.length - 1;
        return (
          <li key={step.key} className="flex flex-1 items-center last:flex-none">
            <div className="flex flex-col items-center gap-1.5">
              <span
                className={cn(
                  "grid size-6 place-items-center rounded-full border text-[10px] transition-colors",
                  done && !isRejectedStep && "border-ok/60 bg-ok/15 text-ok",
                  (isFailedStep || isRejectedStep) && "border-bad/60 bg-bad/15 text-bad",
                  current && "border-accent bg-accent-soft text-accent",
                  !done && !current && !isFailedStep && "border-line-strong text-dim",
                )}
              >
                {isFailedStep || isRejectedStep ? <X className="size-3" /> : done ? <Check className="size-3" /> : current ? <span className="size-1.5 animate-pulse-soft rounded-full bg-accent" /> : i + 1}
              </span>
              <span className={cn("whitespace-nowrap font-mono text-[10px]", current ? "text-accent" : done ? "text-muted" : "text-dim")}>
                {step.label}
              </span>
            </div>
            {i < PIPELINE_STEPS.length - 1 && (
              <span className={cn("mx-2 mb-5 h-px flex-1", PIPELINE_STEPS[i + 1]!.reached(status) && !failed ? "bg-ok/50" : "bg-line-strong")} aria-hidden />
            )}
          </li>
        );
      })}
    </ol>
  );
}
