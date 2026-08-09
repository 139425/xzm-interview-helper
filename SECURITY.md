# Security and private-data policy

## Do not commit

- Any populated `.env` file or local provider configuration.
- API keys, JWT secrets, database credentials, certificates, SSH material, or access tokens.
- Resumes, interview transcripts, user uploads, production database exports, or logs.
- RAG source documents and any derived retrieval/evaluation corpus.
- Chroma/HNSW indexes, SQLite databases, pickle files, lexical caches, or model artifacts.
- Production IP addresses, internal hostnames, release archives, or environment-specific deployment scripts.

The root `.gitignore` covers the known paths, but ignore rules are not a substitute for reviewing staged files.

## Before every release

```powershell
git status --short
git diff --cached --stat
git diff --cached --check
git grep -n -I -E "(BEGIN (RSA|OPENSSH|EC) PRIVATE KEY|api[_-]?key[[:space:]]*[:=]|password[[:space:]]*[:=]|secret[[:space:]]*[:=])"
```

Also inspect staged files larger than 5 MB and verify that `backend-ai/docs`, `backend-ai/chroma_db`, `backend-ai/evaluation`, `backend-java/docs`, and `frontend/tests/e2e` are absent.

## Local secret handling

Copy each `.env.example` to `.env` and populate values locally. Prefer a deployment secret manager in production. Do not place real values in Dockerfiles, Compose files, source code, screenshots, issue text, or CI logs.

## If a secret is exposed

1. Revoke or rotate it immediately.
2. Remove it from the entire Git history, not only the latest commit.
3. Invalidate related sessions and inspect provider/database audit logs.
4. Re-run repository and artifact scans before publishing again.

## Reporting

Please report suspected security problems privately to the repository owner. Do not include live credentials or private user data in a public issue.

