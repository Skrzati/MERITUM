import type { LoginResponse, Problem } from "./types";

export const USER_STORAGE_KEY = "meritum.demoUserId";
const TOKEN_STORAGE_KEY = "meritum.accessToken";
const IDP_STORAGE_KEY = "meritum.identityProvider";
const USER_HEADER = "X-Demo-User-Id";
const UNAUTHORIZED = 401;

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly problem: Problem,
  ) {
    super(problem.detail || problem.title || `Błąd HTTP ${status}`);
  }
}

function read(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null): void {
  try {
    if (value === null) window.localStorage.removeItem(key);
    else window.localStorage.setItem(key, value);
  } catch {
    /* tryb prywatny / zablokowany storage — sesja tylko w pamięci nagłówka demo */
  }
}

export function currentUserId(): string | null {
  return read(USER_STORAGE_KEY);
}

export function accessToken(): string | null {
  return read(TOKEN_STORAGE_KEY);
}

/** Dostawca tożsamości wybrany w oknie logowania (tylko do wyświetlenia — token wydaje backend). */
export function identityProviderLabel(): string | null {
  return read(IDP_STORAGE_KEY);
}

/** Bearer JWT z gov-login; X-Demo-User-Id wyłącznie jako fallback, gdy tokena brak. */
function headers(extra?: HeadersInit): Headers {
  const h = new Headers(extra);
  const token = accessToken();
  const id = currentUserId();
  if (token) h.set("Authorization", `Bearer ${token}`);
  else if (id) h.set(USER_HEADER, id);
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

/** Wygasły lub odrzucony token: usuwamy go i powtarzamy żądanie raz (fallback do nagłówka demo). */
async function authorizedFetch(path: string, init: RequestInit, extraHeaders?: HeadersInit): Promise<Response> {
  const hadToken = !!accessToken();
  const res = await fetch(path, { ...init, headers: headers(extraHeaders), cache: "no-store" });
  if (res.status !== UNAUTHORIZED || !hadToken) return res;
  write(TOKEN_STORAGE_KEY, null);
  return fetch(path, { ...init, headers: headers(extraHeaders), cache: "no-store" });
}

/** Wywołanie API przez proxy Next.js z nagłówkiem uwierzytelnienia. */
export async function api<T>(path: string, init: { method?: string; body?: unknown; form?: FormData } = {}): Promise<T> {
  const res = await authorizedFetch(
    path,
    {
      method: init.method ?? (init.body !== undefined || init.form ? "POST" : "GET"),
      body: init.form ?? (init.body !== undefined ? JSON.stringify(init.body) : undefined),
    },
    init.body !== undefined ? { "Content-Type": "application/json" } : undefined,
  );
  if (!res.ok) throw await toError(res);
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as T;
}

/** Symulacja Węzła Krajowego / SSO: backend wydaje JWT (HS256), który zapisujemy do kolejnych żądań. */
export async function govLogin(userId: number, providerLabel?: string): Promise<LoginResponse> {
  const res = await fetch("/api/auth/gov-login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId }),
    cache: "no-store",
  });
  if (!res.ok) throw await toError(res);
  const login = (await res.json()) as LoginResponse;
  write(TOKEN_STORAGE_KEY, login.accessToken);
  write(USER_STORAGE_KEY, String(login.user.id));
  write(IDP_STORAGE_KEY, providerLabel ?? login.identityProvider);
  return login;
}

/** Gdy logowanie się nie powiodło — bez tokena, tylko nagłówek demo (nie zostawiamy tokena poprzedniej osoby). */
export function fallbackToDemoHeader(userId: number): void {
  write(TOKEN_STORAGE_KEY, null);
  write(IDP_STORAGE_KEY, null);
  write(USER_STORAGE_KEY, String(userId));
}

/** Wywołanie AI przez backend-core (audyt AI Act art. 26). */
export const coreAi = (operation: string, cycleId: number) => `/api/core-ai/${operation}?cycleId=${cycleId}`;

/** Pobiera plik (np. PDF) z nagłówkiem uwierzytelnienia i zapisuje go w przeglądarce. */
export async function download(path: string, filename: string): Promise<void> {
  const res = await authorizedFetch(path, {});
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
