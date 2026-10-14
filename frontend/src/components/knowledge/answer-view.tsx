"use client";

import { AlertTriangle, BookOpen, ShieldAlert } from "lucide-react";
import { Fragment } from "react";

import type { KnowledgeAnswer } from "@/lib/api/types";

/** Plain-text answer with [n] markers turned into citation chips. Never rendered as HTML. */
function CitedText({ text, valid }: { text: string; valid: Set<number> }) {
  const parts = text.split(/(\[\d+\])/g);
  return (
    <p className="text-[15px] leading-relaxed">
      {parts.map((part, i) => {
        const m = /^\[(\d+)\]$/.exec(part);
        if (m && valid.has(Number(m[1]))) {
          return (
            <a
              key={i}
              href={`#source-${m[1]}`}
              className="mx-0.5 inline-grid size-[18px] place-items-center rounded bg-accent-soft align-text-top font-mono text-[10px] font-semibold text-accent no-underline hover:bg-accent hover:text-accent-fg"
              aria-label={`Source ${m[1]}`}
            >
              {m[1]}
            </a>
          );
        }
        return <Fragment key={i}>{part}</Fragment>;
      })}
    </p>
  );
}

export function AnswerView({ answer }: { answer: KnowledgeAnswer }) {
  const valid = new Set(answer.sources.map((s) => s.n));
  return (
    <div className="space-y-4" aria-live="polite">
      {answer.injectionSuspected && (
        <p className="flex gap-2 rounded-control border border-bad/40 bg-bad/10 p-3 text-sm text-bad" role="alert">
          <ShieldAlert className="mt-0.5 size-4 shrink-0" /> One of the retrieved documents contains text that looks like instructions to an AI. It was
          treated as plain reference data.
        </p>
      )}
      <CitedText text={answer.answer} valid={valid} />
      {answer.insufficientContext && (
        <p className="flex items-start gap-2 text-[13px] text-warn">
          <AlertTriangle className="mt-0.5 size-3.5 shrink-0" /> Your knowledge base does not cover this. Add the relevant policy under Knowledge to get a sourced answer.
        </p>
      )}
      {answer.sources.length > 0 && (
        <div>
          <div className="label-mono mb-2 flex items-center gap-2">
            <BookOpen className="size-3" /> Sources
          </div>
          <ol className="space-y-2">
            {answer.sources.map((s) => {
              const cited = answer.citations.includes(s.n);
              return (
                <li
                  key={s.n}
                  id={`source-${s.n}`}
                  className={`scroll-mt-24 rounded-control border p-3 text-[13px] ${cited ? "border-accent/40 bg-accent-soft/20" : "border-line bg-bg/40"}`}
                >
                  <div className="mb-1 flex items-center justify-between gap-3">
                    <span className="font-medium">
                      <span className="mr-2 font-mono text-accent">[{s.n}]</span>
                      {s.title}
                    </span>
                    <span className="shrink-0 font-mono text-[10px] text-dim" title="Cosine similarity to the question">
                      {cited ? "cited · " : ""}match {Math.round(s.score * 100)}%
                    </span>
                  </div>
                  <p className="whitespace-pre-line leading-relaxed text-muted">{s.excerpt}</p>
                </li>
              );
            })}
          </ol>
        </div>
      )}
      {answer.model && <p className="font-mono text-[10px] text-dim">answered by {answer.model}</p>}
    </div>
  );
}
