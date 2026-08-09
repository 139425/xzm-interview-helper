const DEFAULT_API_BASE = "/xzm";

const trimTrailingSlashes = (value) => value.replace(/\/+$/, "");

/**
 * Resolve the API root without baking a server IP into the browser bundle.
 *
 * Production uses the current origin and lets Nginx proxy /xzm to Spring.
 * Development uses the same path through Vite's local proxy. An explicit
 * absolute URL is still supported for isolated integration environments, but
 * an insecure HTTP URL is rejected on HTTPS pages because browsers block it
 * as mixed content.
 */
export function resolveApiBase(
  configured = import.meta.env.VITE_API_BASE,
  locationLike = typeof window !== "undefined" ? window.location : null,
) {
  const candidate = String(configured || "").trim();
  if (!candidate) return DEFAULT_API_BASE;

  const normalized = trimTrailingSlashes(candidate);
  if (
    locationLike?.protocol === "https:" &&
    /^http:\/\//i.test(normalized)
  ) {
    console.warn(
      "[runtime] Ignored insecure VITE_API_BASE on an HTTPS page; using same-origin /xzm.",
    );
    return DEFAULT_API_BASE;
  }

  return normalized || DEFAULT_API_BASE;
}

export const API_BASE_URL = resolveApiBase();
