import { beforeEach, describe, expect, it } from 'vitest'
import {
  algorithmWorkspaceStorageKeys,
  initializeAlgorithmWorkspaceMigration,
  readAlgorithmWorkspaceValue,
  resolveAlgorithmWorkspaceOwner,
  saveAlgorithmWorkspaceValue,
} from '@/utils/algorithmWorkspaceStorage'

function authenticate(userId) {
  localStorage.setItem('token', `token-${userId}`)
  localStorage.setItem('userInfo', JSON.stringify({ userId }))
}

function workspaceInput(ownerId, kind = 'draft', slug = 'two-sum') {
  return { ownerId, kind, slug, language: 'java' }
}

describe('algorithm workspace storage isolation', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('namespaces drafts and custom cases by authenticated user id', () => {
    authenticate(7)
    const owner = resolveAlgorithmWorkspaceOwner()
    expect(owner).toBe('7')

    expect(saveAlgorithmWorkspaceValue(workspaceInput(owner), 'user-7-code'))
      .toBe(true)
    expect(saveAlgorithmWorkspaceValue(
      workspaceInput(owner, 'customCase'),
      '{"driver":"user-7-driver"}',
    )).toBe(true)

    authenticate(8)
    const otherOwner = resolveAlgorithmWorkspaceOwner()
    expect(otherOwner).toBe('8')
    expect(readAlgorithmWorkspaceValue(workspaceInput(otherOwner))).toBeNull()
    expect(readAlgorithmWorkspaceValue(
      workspaceInput(otherOwner, 'customCase'),
    )).toBeNull()

    expect(readAlgorithmWorkspaceValue(workspaceInput(owner)))
      .toBe('user-7-code')
  })

  it('migrates legacy values only for the authenticated owner proven at boot', () => {
    localStorage.setItem('algorithmDraft:two-sum:java', 'legacy-code')
    localStorage.setItem(
      'algorithmCustomCase:two-sum:java',
      '{"driver":"legacy-driver"}',
    )
    authenticate(7)

    initializeAlgorithmWorkspaceMigration()
    const owner = resolveAlgorithmWorkspaceOwner()
    expect(localStorage.getItem(algorithmWorkspaceStorageKeys.legacyOwner))
      .toBe(owner)
    expect(readAlgorithmWorkspaceValue(workspaceInput(owner)))
      .toBe('legacy-code')
    expect(readAlgorithmWorkspaceValue(workspaceInput(owner, 'customCase')))
      .toBe('{"driver":"legacy-driver"}')
    expect(localStorage.getItem('algorithmDraft:two-sum:java')).toBeNull()
    expect(localStorage.getItem('algorithmCustomCase:two-sum:java')).toBeNull()
  })

  it('quarantines unattributed legacy values instead of exposing them after login', () => {
    localStorage.setItem('algorithmDraft:two-sum:java', 'former-user-code')
    initializeAlgorithmWorkspaceMigration()

    expect(localStorage.getItem(algorithmWorkspaceStorageKeys.legacyOwner))
      .toBe(algorithmWorkspaceStorageKeys.unattributedOwner)

    authenticate(8)
    const owner = resolveAlgorithmWorkspaceOwner()
    expect(readAlgorithmWorkspaceValue(workspaceInput(owner))).toBeNull()
    expect(localStorage.getItem('algorithmDraft:two-sum:java'))
      .toBe('former-user-code')
  })
})
