import { cn } from "@/lib/utils";

import s from "./conveyor.module.css";

const DESCRIPTION =
  "Drawing: invoices ride a conveyor belt through a scan gate. Invoices that pass continue as verified; one that does not is pushed by a sorting arm into a bin labelled needs review.";

function Card({ className, vendor, amount, done }: { className?: string; vendor: string; amount: string; done: string }) {
  return (
    <div className={cn(s.card, className)}>
      <p className={s.vendor}>{vendor}</p>
      <p className={s.amount}>{amount}</p>
      <p className={s.status}>
        <span className={s.stIn}>incoming</span>
        <span className={s.stScan}>scanning</span>
        <span className={s.stDone}>{done}</span>
      </p>
    </div>
  );
}

/**
 * The hero drawing: a conveyor belt, a scan gate over it, three invoices (one in the gate, one verified, one flagged),
 * a sorting arm and a bin. Pure markup and CSS; the text inside is part of the picture, so the whole thing is one image
 * for assistive technology. Vendor names are from the synthetic samples in the repository.
 */
export function ConveyorScene({ className }: { className?: string }) {
  return (
    <div className={cn(s.viewport, className)} role="img" aria-label={DESCRIPTION}>
      <div className={s.scene} aria-hidden>
        <div className={s.glow} />
        <p className={s.gateLabel}>sums, tax, dates, duplicates</p>
        <p className={s.chip}>
          <span className={cn(s.chipDot, "animate-pulse-soft")} />
          checking live
        </p>

        <div className={s.belt} />
        <div className={s.lintel} />
        <div className={s.gate}>
          <div className={s.beam} />
        </div>

        <Card className={s.cardA} vendor="Alpenrain Logistik" amount="€ 2,380.00" done="✓ verified" />
        <Card className={s.cardB} vendor="Nordlicht Software" amount="€ 1,428.00" done="✓ verified" />
        <Card className={s.cardC} vendor="Kuhn Elektrotechnik" amount="€ 1,500.00" done="flagged" />

        <div className={s.armPost} />
        <div className={s.arm} />
        <div className={s.bin}>
          <p className={s.binLabel}>needs review</p>
        </div>
      </div>
    </div>
  );
}
