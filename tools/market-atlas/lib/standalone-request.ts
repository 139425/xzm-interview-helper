function configuredOrigin(value: string, name: string): string {
  const parsed = new URL(value);
  if (!['http:', 'https:'].includes(parsed.protocol) || parsed.origin !== value) {
    throw new Error(`${name} must contain HTTP(S) origins without paths or trailing slashes`);
  }
  return parsed.origin;
}

// Nginx overwrites both forwarding headers before requests reach this service.
// Forwarded origins are used only when the operator explicitly allows them.
export function standaloneRequest(request: Request, publicOrigin?: string, publicOrigins?: string): Request {
  if (!publicOrigin) {
    if (publicOrigins) throw new Error('MARKET_ATLAS_PUBLIC_ORIGINS requires MARKET_ATLAS_PUBLIC_ORIGIN');
    return request;
  }
  const canonical = configuredOrigin(publicOrigin, 'MARKET_ATLAS_PUBLIC_ORIGIN');
  const allowed = new Set([canonical]);
  if (publicOrigins) {
    for (const value of publicOrigins.split(',')) allowed.add(configuredOrigin(value.trim(), 'MARKET_ATLAS_PUBLIC_ORIGINS'));
  }
  let selected = canonical;
  const protocol = request.headers.get('X-Forwarded-Proto');
  const host = request.headers.get('X-Forwarded-Host');
  if ((protocol === 'http' || protocol === 'https') && host && !/[\s,/@?#\\]/.test(host)) {
    try {
      const candidate = new URL(`${protocol}://${host}`).origin;
      if (allowed.has(candidate)) selected = candidate;
    } catch {
      // Invalid forwarding values retain the configured canonical origin.
    }
  }
  const origin = new URL(selected);
  const url = new URL(request.url);
  url.protocol = origin.protocol;
  url.hostname = origin.hostname;
  url.port = origin.port;
  const headers = new Headers(request.headers);
  for (const name of [...headers.keys()]) {
    if (name.startsWith('oai-authenticated-user-')) headers.delete(name);
  }
  return new Request(url, new Request(request, { headers }));
}
