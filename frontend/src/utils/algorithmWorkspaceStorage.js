import { readStoredAuth } from './authSession'

const STORAGE_VERSION = 'v2'
const LEGACY_OWNER_KEY = `algorithmWorkspace:${STORAGE_VERSION}:legacyOwner`
const UNATTRIBUTED_OWNER = '__unattributed__'
const SUPPORTED_KINDS = new Set(['draft', 'customCase'])
const LEGACY_PREFIXES = ['algorithmDraft:', 'algorithmCustomCase:']

function normalizePart(value) {
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

function legacyKey(kind, slug, language) {
  const prefix = kind === 'draft' ? 'algorithmDraft' : 'algorithmCustomCase'
  return `${prefix}:${slug}:${language}`
}

function scopedKey(ownerId, kind, slug, language) {
  return [
    'algorithmWorkspace',
    STORAGE_VERSION,
    encodeURIComponent(ownerId),
    kind,
    encodeURIComponent(slug),
    encodeURIComponent(language),
  ].join(':')
}

function normalizeInput({ ownerId, kind, slug, language = 'java' }) {
  const normalizedOwnerId = normalizePart(ownerId)
  const normalizedSlug = normalizePart(slug)
  const normalizedLanguage = normalizePart(language).toLowerCase()
  if (
    !normalizedOwnerId ||
    !normalizedSlug ||
    !normalizedLanguage ||
    !SUPPORTED_KINDS.has(kind)
  ) {
    return null
  }
  return {
    ownerId: normalizedOwnerId,
    kind,
    slug: normalizedSlug,
    language: normalizedLanguage,
  }
}

/**
 * Captures the authenticated owner for a workspace instance. Call this once
 * when the view is created so a cross-tab account switch cannot redirect an
 * already-open editor's autosave into another user's namespace.
 */
export function resolveAlgorithmWorkspaceOwner() {
  try {
    const { token, userInfo } = readStoredAuth()
    if (!token || !userInfo) return null
    return normalizePart(userInfo.userId ?? userInfo.user_id ?? userInfo.id) || null
  } catch {
    return null
  }
}

/**
 * Must run once during application bootstrap, before a new account can log in.
 * A legacy value is attributable only when a complete persisted auth snapshot
 * already exists at boot. Logged-out legacy data is quarantined because its
 * original owner cannot be proven.
 */
export function initializeAlgorithmWorkspaceMigration() {
  try {
    if (localStorage.getItem(LEGACY_OWNER_KEY)) return
    const hasLegacyWorkspace = Array.from(
      { length: localStorage.length },
      (_, index) => localStorage.key(index),
    ).some((key) => LEGACY_PREFIXES.some((prefix) => key?.startsWith(prefix)))
    if (!hasLegacyWorkspace) return

    const authenticatedOwner = resolveAlgorithmWorkspaceOwner()
    localStorage.setItem(
      LEGACY_OWNER_KEY,
      authenticatedOwner || UNATTRIBUTED_OWNER,
    )
  } catch {
    // Without a durable owner claim, legacy values remain unreadable.
  }
}

/**
 * Reads only the authenticated user's namespace.
 *
 * Legacy unscoped values are available only to the owner proven at application
 * bootstrap. They are copied once and then removed. Unattributed data and data
 * attributed to another user always fail closed.
 */
export function readAlgorithmWorkspaceValue(input) {
  const normalized = normalizeInput(input)
  if (!normalized) return null

  try {
    const destination = scopedKey(
      normalized.ownerId,
      normalized.kind,
      normalized.slug,
      normalized.language,
    )
    const stored = localStorage.getItem(destination)
    if (stored !== null) return stored

    const legacy = legacyKey(
      normalized.kind,
      normalized.slug,
      normalized.language,
    )
    const legacyValue = localStorage.getItem(legacy)
    if (legacyValue === null) return null

    const claimedOwner = localStorage.getItem(LEGACY_OWNER_KEY)
    if (claimedOwner !== normalized.ownerId) return null

    try {
      localStorage.setItem(destination, legacyValue)
      localStorage.removeItem(legacy)
    } catch {
      // The claimed owner may still use the legacy value for this session.
      // Keeping the owner claim prevents every other account from reading it.
    }
    return legacyValue
  } catch {
    return null
  }
}

export function saveAlgorithmWorkspaceValue(input, value) {
  const normalized = normalizeInput(input)
  if (!normalized) return false

  try {
    localStorage.setItem(
      scopedKey(
        normalized.ownerId,
        normalized.kind,
        normalized.slug,
        normalized.language,
      ),
      String(value ?? ''),
    )
    return true
  } catch {
    return false
  }
}

export const algorithmWorkspaceStorageKeys = {
  scoped(input) {
    const normalized = normalizeInput(input)
    return normalized
      ? scopedKey(
          normalized.ownerId,
          normalized.kind,
          normalized.slug,
          normalized.language,
        )
      : null
  },
  legacyOwner: LEGACY_OWNER_KEY,
  unattributedOwner: UNATTRIBUTED_OWNER,
}
