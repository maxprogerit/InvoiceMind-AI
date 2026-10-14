import createClient from "openapi-fetch";

import { msUntilExpiry, useAuth } from "@/lib/auth/store";

import type { paths } from "./schema";
import type { AuthUser, ErrorBody } from "./types";

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public fieldErrors?: Record<string, string>,
    public requestId?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

const AUTH_PATHS = ["/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh", "/api/v1/auth/logout"];
const isAuthPath = (url: string) => AUTH_PATHS.some((p) => url.includes(p));

interface AuthResponse {
  accessToken: string;
  user: AuthUser;
}

let refreshing: Promise<boolean> | null = null;

/**
 * Exchanges the httpOnly refresh cookie for a new access token. Single-flight: concurrent callers
 * share one request, which matters because refresh tokens rotate and a second parallel call would
 * otherwise look like token reuse to the server.
 */
export function refreshSession(): Promise<boolean> {
  refreshing ??= (async () => {
    try {
      const res = await fetch("/api/v1/auth/refresh", {
        method: "POST",
        credentials: "same-origin",
        headers: { "X-Requested-With": "invoicemind" },
      });
      if (!res.ok) {
        // Only a 401 means the session is really gone; a 429/5xx must not sign anyone out.
        if (res.status === 401) useAuth.getState().clear();
        return false;
      }
      const body = (await res.json()) as AuthResponse;
      useAuth.getState().setSession(body.accessToken, body.user);
      return true;
    } catch {
      return false;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

/** Returns a token that is valid for at least the next 20 s, refreshing first when needed. */
export async function getValidToken(): Promise<string | null> {
  const { token } = useAuth.getState();
  if (token && msUntilExpiry(token) > 20_000) return token;
  return (await refreshSession()) ? useAuth.getState().token : null;
}

/** fetch() with the bearer token attached and one transparent refresh-and-retry on 401. */
export async function authFetch(input: Request | string, init?: RequestInit): Promise<Response> {
  const request = new Request(input, init);
  const retryCopy = request.clone();
  const url = request.url;

  if (!isAuthPath(url)) {
    const token = await getValidToken();
    if (token) request.headers.set("Authorization", `Bearer ${token}`);
  }
  const response = await fetch(request);
  if (response.status !== 401 || isAuthPath(url)) return response;

  if (!(await refreshSession())) return response;
  retryCopy.headers.set("Authorization", `Bearer ${useAuth.getState().token}`);
  return fetch(retryCopy);
}

export const api = createClient<paths>({ baseUrl: "", fetch: (req) => authFetch(req) });

export async function toApiError(res: Response): Promise<ApiError> {
  let body: Partial<ErrorBody> = {};
  try {
    body = (await res.clone().json()) as Partial<ErrorBody>;
  } catch {
    /* not JSON (proxy error page etc.) */
  }
  const fallback =
    res.status === 413 ? "The file is too large (max 15 MB)" : res.status >= 500 ? "Something went wrong on our side" : res.statusText;
  return new ApiError(res.status, body.code ?? "error", body.message ?? (fallback || "Request failed"), body.fieldErrors, body.requestId);
}

/** Unwraps an openapi-fetch result: returns data or throws a typed ApiError. */
export async function unwrap<T>(result: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T> {
  const { data, response } = await result;
  if (!response.ok) throw await toApiError(response);
  return data as T;
}

/** Authenticated GET/POST returning a Blob (document preview, exports). */
export async function fetchBlob(path: string): Promise<{ blob: Blob; filename?: string; rows?: number }> {
  const res = await authFetch(path);
  if (!res.ok) throw await toApiError(res);
  const disposition = res.headers.get("Content-Disposition") ?? "";
  const filename = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition)?.[1];
  const rows = res.headers.get("X-Row-Count");
  return { blob: await res.blob(), filename: filename ? decodeURIComponent(filename) : undefined, rows: rows ? Number(rows) : undefined };
}

/** Multipart upload with real progress events (fetch cannot report upload progress). */
export function uploadInvoice(
  file: File,
  onProgress: (fraction: number) => void,
  signal?: AbortSignal,
): Promise<{ id: string }> {
  const send = (token: string | null) =>
    new Promise<{ status: number; body: string }>((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open("POST", "/api/v1/invoices");
      if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);
      xhr.upload.onprogress = (e) => e.lengthComputable && onProgress(e.loaded / e.total);
      xhr.onload = () => resolve({ status: xhr.status, body: xhr.responseText });
      xhr.onerror = () => reject(new ApiError(0, "network", "Network error while uploading"));
      xhr.onabort = () => reject(new ApiError(0, "aborted", "Upload cancelled"));
      signal?.addEventListener("abort", () => xhr.abort());
      const form = new FormData();
      form.append("file", file);
      xhr.send(form);
    });

  return (async () => {
    let res = await send(await getValidToken());
    if (res.status === 401 && (await refreshSession())) res = await send(useAuth.getState().token);
    if (res.status !== 202) {
      let body: Partial<ErrorBody> = {};
      try {
        body = JSON.parse(res.body) as Partial<ErrorBody>;
      } catch {
        /* proxy responses (e.g. nginx 413) are not JSON */
      }
      const message = body.message ?? (res.status === 413 ? "The file is too large (max 15 MB)" : "Upload failed");
      throw new ApiError(res.status, body.code ?? "upload_failed", message);
    }
    return JSON.parse(res.body) as { id: string };
  })();
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}
