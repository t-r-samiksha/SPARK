import type {
  DisasterEvent,
  DisasterToggleBody,
  HealthResponse,
  IncidentType,
  IncidentsResponse,
  RecommendedCap,
  RevokeBody,
  RevokeResult,
} from './types';

/**
 * API base URL. Defaults to a RELATIVE /api/v1 so requests stay same-origin (the backend
 * sets no CORS headers). In dev, Vite proxies /api → the backend (vite.config.ts).
 * Override with VITE_API_BASE_URL for a standalone deployment behind a reverse proxy.
 */
export const API_BASE: string = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';

/** Admin shared secret for the X-Admin-Key header. Never hardcoded — env only. */
export const ADMIN_KEY: string | undefined = import.meta.env.VITE_ADMIN_KEY || undefined;

export const hasAdminKey = (): boolean => typeof ADMIN_KEY === 'string' && ADMIN_KEY.length > 0;

export class ApiError extends Error {
  readonly status: number;
  readonly code: number | null;

  constructor(status: number, message: string, code: number | null = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

/** Full URL for an API path under the configured base, e.g. `/admin/incidents` → `/api/v1/admin/incidents`. */
function apiUrl(path: string): string {
  return `${API_BASE}${path}`;
}

/**
 * Server origin of the backend, used for the health endpoint: the backend registers
 * GET /health at the SERVER ROOT (backend/src/server.ts), outside the /api/v1 prefix.
 * For an absolute API_BASE we take its origin; for the relative default we return ''
 * so the health URL is root-relative on the same origin (proxied in dev).
 */
function serverRootUrl(): string {
  return /^https?:\/\//.test(API_BASE) ? new URL(API_BASE).origin : '';
}

async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');

  const body: BodyInit | undefined = init.body ?? undefined;
  if (body !== undefined && !(body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  let response: Response;
  try {
    response = await fetch(url, { ...init, headers, body });
  } catch {
    throw new ApiError(0, `Cannot reach the SPARK backend at ${API_BASE}`);
  }

  const text = await response.text();
  let payload: unknown = null;
  let serverMessage: string | undefined;
  if (text) {
    try {
      payload = JSON.parse(text);
      if (payload && typeof payload === 'object' && 'error' in payload) {
        serverMessage = String((payload as { error: unknown }).error);
      }
    } catch {
      payload = null;
    }
  }

  if (!response.ok) {
    throw new ApiError(
      response.status,
      serverMessage ?? `Request failed with status ${response.status}`,
    );
  }

  return payload as T;
}

/** GET /health — connection check for the status bar. Lives at the server root, not under /api/v1. */
export function getHealth(): Promise<HealthResponse> {
  return request<HealthResponse>(`${serverRootUrl()}/health`, {
    headers: adminHeaders(),
  });
}

/** GET /admin/incidents?type=… */
export function getIncidents(type: IncidentType = 'all'): Promise<IncidentsResponse> {
  return request<IncidentsResponse>(apiUrl(`/admin/incidents?type=${type}`), {
    headers: adminHeaders(),
  });
}

/** POST /admin/revoke */
export function revokeDevice(body: RevokeBody): Promise<RevokeResult> {
  return request<RevokeResult>(apiUrl('/admin/revoke'), {
    method: 'POST',
    headers: adminHeaders(),
    body: JSON.stringify(body),
  });
}

/** POST /admin/disaster/toggle */
export function toggleDisaster(body: DisasterToggleBody): Promise<DisasterEvent> {
  return request<DisasterEvent>(apiUrl('/admin/disaster/toggle'), {
    method: 'POST',
    headers: adminHeaders(),
    body: JSON.stringify(body),
  });
}

/**
 * GET /limit/recommendation.
 * The backend currently returns 501 Not Implemented; callers must handle ApiError(501)
 * as the intentional "cap model not wired" state rather than an error.
 */
export function getRecommendedCap(): Promise<RecommendedCap> {
  return request<RecommendedCap>(apiUrl('/limit/recommendation'), {
    headers: adminHeaders(),
  });
}

function adminHeaders(): HeadersInit {
  return hasAdminKey() ? { 'X-Admin-Key': ADMIN_KEY as string } : {};
}
