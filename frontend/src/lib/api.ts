import { config } from "./config";

export type ResultStatus = "ok" | "not_found" | "invalid";

export interface ConvertResult {
  input: string;
  status: ResultStatus;
  handle: string | null;
  channelId: string | null;
  title: string | null;
  thumbnailUrl: string | null;
  reason: string | null;
}

export interface ConvertResponse {
  results: ConvertResult[];
}

/** RFC 9457 ProblemDetail(backend が返す) */
export interface ProblemDetail {
  status?: number;
  title?: string;
  detail?: string;
  max?: number;
  resetAt?: string;
  retryAfterSeconds?: number;
}

export type ApiErrorKind =
  | "unauthorized"
  | "too_many_inputs"
  | "bad_request"
  | "rate_limited"
  | "quota_exceeded"
  | "upstream"
  | "maintenance"
  | "network"
  | "unknown";

export class ApiError extends Error {
  constructor(
    readonly kind: ApiErrorKind,
    readonly status: number | null,
    readonly problem: ProblemDetail | null,
  ) {
    super(problem?.detail ?? problem?.title ?? kind);
    this.name = "ApiError";
  }
}

export interface Credentials {
  username: string;
  password: string;
}

function kindOf(status: number, problem: ProblemDetail | null): ApiErrorKind {
  switch (status) {
    case 401:
      return "unauthorized";
    case 400:
      return problem?.max !== undefined ? "too_many_inputs" : "bad_request";
    case 429:
      // 429 は Quota 枯渇(resetAt あり)とレートリミットの両方で使う
      return problem?.resetAt ? "quota_exceeded" : "rate_limited";
    case 502:
      return "upstream";
    case 503:
      return "maintenance";
    default:
      return "unknown";
  }
}

function basicAuthHeader({ username, password }: Credentials): string {
  // btoa は Latin-1 のみなので UTF-8 にエンコードしてから
  const bytes = new TextEncoder().encode(`${username}:${password}`);
  return `Basic ${btoa(String.fromCharCode(...bytes))}`;
}

/**
 * POST /api/v1/convert。個別行の失敗は結果に含まれ、全体の失敗は ApiError で投げる。
 */
export async function convert(
  inputs: string[],
  credentials?: Credentials,
): Promise<ConvertResponse> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (credentials) headers["Authorization"] = basicAuthHeader(credentials);

  let response: Response;
  try {
    response = await fetch(`${config.apiBaseUrl}/api/v1/convert`, {
      method: "POST",
      headers,
      body: JSON.stringify({ inputs }),
    });
  } catch {
    throw new ApiError("network", null, null);
  }

  if (response.ok) return (await response.json()) as ConvertResponse;

  let problem: ProblemDetail | null = null;
  try {
    problem = (await response.json()) as ProblemDetail;
  } catch {
    problem = null;
  }
  throw new ApiError(kindOf(response.status, problem), response.status, problem);
}
