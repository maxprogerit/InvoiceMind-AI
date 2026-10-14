"use client";

import { motion } from "motion/react";
import { ArrowRight } from "lucide-react";

const CHIPS = ["OCR", "Document analysis", "Export"];
const FLOW = ["invoice", "data", "table", "export"];

/** Left half of the sign-in screen: the "title card" from the product mockup. */
export function Hero() {
  return (
    <div className="relative flex flex-col justify-between gap-12 px-8 pb-10 pt-16 sm:px-14 lg:min-h-dvh lg:py-20">
      <div>
        <motion.p
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="font-mono text-sm font-medium uppercase tracking-[0.3em] text-accent"
        >
          Document processing
        </motion.p>

        <h1 className="mt-8 select-none text-[clamp(4.5rem,13vw,9.5rem)] font-bold leading-[0.86] tracking-[-0.045em]">
          <motion.span
            className="block bg-gradient-to-b from-fg to-fg/70 bg-clip-text text-transparent"
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.05 }}
          >
            Invoice
          </motion.span>
          <motion.span
            className="block text-accent"
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.15 }}
          >
            Mind
          </motion.span>
        </h1>

        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.6, delay: 0.35 }}
          className="mt-8 max-w-md text-2xl leading-snug text-muted sm:text-[1.75rem]"
        >
          Manage invoices in one place: scan, review, and export.
        </motion.p>
      </div>

      <div className="space-y-6">
        <div className="flex flex-wrap gap-3">
          {CHIPS.map((chip, i) => (
            <motion.span
              key={chip}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.4, delay: 0.5 + i * 0.08 }}
              className="rounded-lg border border-line-strong px-4 py-2 font-mono text-[15px] font-medium"
            >
              {chip}
            </motion.span>
          ))}
        </div>
        <p className="flex flex-wrap items-center gap-x-2 font-mono text-[15px] text-dim" aria-label="Pipeline: invoice to data to table to export">
          {FLOW.map((step, i) => (
            <motion.span
              key={step}
              className="flex items-center gap-2"
              initial={{ color: "var(--color-dim)" }}
              animate={{ color: ["var(--color-dim)", "var(--color-accent)", "var(--color-dim)"] }}
              transition={{ duration: 2.4, delay: i * 0.6, repeat: Infinity, repeatDelay: FLOW.length * 0.6 - 2.4 + 1.2 }}
            >
              {step}
              {i < FLOW.length - 1 && <ArrowRight className="size-3.5" aria-hidden />}
            </motion.span>
          ))}
        </p>
      </div>
    </div>
  );
}
