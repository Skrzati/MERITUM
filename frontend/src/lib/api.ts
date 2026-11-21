import type { Problem } from "./types";

export const USER_STORAGE_KEY = "meritum.demoUserId";
const USER_HEADER = "X-Demo-User-Id";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly problem: Problem,
  ) {
    super(problem.detail || problem.title || `Błąd HTTP ${status}`);
  }
}

export function currentUserId(): string | null {
  try {
    return window.localStorage.getItem(USER_STORAGE_KEY);
  } catch {
    return null;
  }
}

function headers(extra?: HeadersInit): Headers {
  const h = new Headers(extra);
  const id = currentUserId();
  if (id) h.set(USER_HEADER, id);
  return h;
}

async function toError(res: Response): Promise<ApiError> {
  let problem: Problem = { status: res.status };
  try {
    problem = { ...problem, ...(await res.json()) };
  } catch {
    /* odpowiedź bez treści JSON */
  }
  return new ApiError(res.status, problem);
}

/** Wywołanie API przez proxy Next.js; nagłówek X-Demo-User-Id dodawany automatycznie. */
export async function api<T>(path: string, init: { method?: string; body?: unknown; form?: FormData } = {}): Promise<T> {
  const res = await fetch(path, {
    method: init.method ?? (init.body !== undefined || init.form ? "POST" : "GET"),
    headers: headers(init.body !== undefined ? { "Content-Type": "application/json" } : undefined),
    body: init.form ?? (init.body !== undefined ? JSON.stringify(init.body) : undefined),
    cache: "no-store",
  });
  if (!res.ok) throw await toError(res);
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as T;
}

/** Pobiera plik (np. PDF) z nagłówkiem użytkownika i zapisuje go w przeglądarce. */
export async function download(path: string, filename: string): Promise<void> {
  const res = await fetch(path, { headers: headers(), cache: "no-store" });
  if (!res.ok) throw await toError(res);
  saveBlob(await res.blob(), filename);
}

export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}
