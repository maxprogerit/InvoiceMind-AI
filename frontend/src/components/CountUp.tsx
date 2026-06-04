import { useEffect, useState } from "react";

interface CountUpProps {
  value: number;
  duration?: number;
  decimals?: number;
  suffix?: string;
  prefix?: string;
}

export function CountUp({ value, duration = 1100, decimals = 0, suffix = "", prefix = "" }: CountUpProps) {
  const [display, setDisplay] = useState(0);

  useEffect(() => {
    const start = performance.now();
    const startValue = display;
    const delta = value - startValue;

    const frame = (time: number) => {
      const progress = Math.min((time - start) / duration, 1);
      const next = startValue + delta * progress;
      setDisplay(next);
      if (progress < 1) {
        requestAnimationFrame(frame);
      }
    };

    requestAnimationFrame(frame);
  }, [duration, value]);

  return (
    <span>
      {prefix}
      {display.toFixed(decimals)}
      {suffix}
    </span>
  );
}
