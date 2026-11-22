"use client";

import { SCALE } from "@/lib/labels";
import type { Criterion } from "@/lib/types";

/** Wybór ocen cząstkowych 1/3/5/7/9 dla każdego kryterium. */
export function ScorePicker({
  criteria,
  scores,
  onChange,
  expectations,
  disabled = false,
}: {
  criteria: Criterion[];
  scores: Record<string, number>;
  /** Ustalone na rozmowie rozpoczynającej oczekiwania (poziom 5 pkt) — przypomnienie przy wystawianiu ocen. */
  expectations?: Record<string, string> | null;
  onChange: (code: string, value: number) => void;
  disabled?: boolean;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <caption className="sr-only">Oceny cząstkowe</caption>
        <thead>
          <tr className="border-b border-slate-200 text-xs text-slate-600">
            <th className="py-2 pr-3 text-left">Kryterium</th>
            {SCALE.map((s) => (
              <th key={s.value} className="px-1 py-2 text-center font-medium">
                <span className="block text-base font-bold text-navy-900">{s.value}</span>
                {s.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {criteria.map((c) => (
            <tr key={c.code} className="border-b border-slate-100 last:border-0">
              <th scope="row" className="py-2 pr-3 text-left font-medium">
                {c.name} <span className="font-mono text-xs text-slate-600">{c.code}</span>
                {expectations?.[c.code] && (
                  <span className="mt-1 block max-w-xs whitespace-pre-wrap text-xs font-normal text-slate-700">
                    <span className="font-semibold">Oczekiwania (5 pkt):</span> {expectations[c.code]}
                  </span>
                )}
              </th>
              {SCALE.map((s) => {
                const checked = scores[c.code] === s.value;
                return (
                  <td key={s.value} className="px-1 py-2 text-center">
                    <label className="inline-flex cursor-pointer">
                      <input
                        type="radio"
                        name={`score-${c.code}`}
                        className="peer sr-only"
                        checked={checked}
                        disabled={disabled}
                        onChange={() => onChange(c.code, s.value)}
                      />
                      <span
                        className={`flex h-9 w-9 items-center justify-center rounded-md border text-sm font-bold peer-focus-visible:ring-2 peer-focus-visible:ring-navy-600 ${
                          checked
                            ? s.value === 1
                              ? "border-red-700 bg-red-700 text-white"
                              : "border-navy-700 bg-navy-700 text-white"
                            : "border-slate-300 bg-white text-slate-800 hover:bg-slate-50"
                        } ${disabled ? "cursor-not-allowed opacity-70" : ""}`}
                      >
                        <span className="sr-only">
                          {c.name}: {s.value} — {s.label}
                        </span>
                        <span aria-hidden>{s.value}</span>
                      </span>
                    </label>
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
