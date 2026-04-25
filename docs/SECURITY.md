# Security

## Threat model (milestone 1)

- **Operator account compromise** — single biggest risk; mitigated by RBAC + audit logs (TODO: SSO + 2FA).
- **Provider key leakage** — keys never on Android; backend-only via env / vault.
- **Injection / XSS / SSRF** — input validated at every API boundary; `httpx` calls go to known endpoints only.
- **Database tampering** — append-only audit log; immutable artifact storage with content hash.
- **Mobile device theft** — limited cache; SQLCipher TODO; remote logout TODO.

## Auth

- Backend auth skeleton TODO (Bearer token + role claims). RBAC roles planned: `viewer`, `analyst`, `coordinator`, `admin`.
- Android stores a session token in `EncryptedSharedPreferences` (TODO; current build uses DataStore for non-secrets only).

## Secrets

- `.env.example` contains placeholders only.
- Real secrets must come from a vault (AWS Secrets Manager / GCP Secret Manager / HashiCorp Vault).
- CI must reject any commit that adds a `.env` file with real values.

## Network

- Android → backend over HTTPS only in staging/prod.
- Backend → external providers over HTTPS.
- Reverse proxy (nginx) terminates TLS in staging; production should add WAF.

## Audit

- Every external provider call is audit-logged (`provider_call`, `provider_error`).
- Every Level-3 review action is audit-logged with reviewer ID, timestamp, and note.
- Audit log should ship to an immutable store (object lock, write-once) in production.

## Dependencies

- Backend pinned in `requirements.txt`; renovate / dependabot recommended.
- Android pinned in `gradle/libs.versions.toml`.

## Future

- SBOMs for backend + Android.
- Penetration test before staging → production cut-over.
- Annual privacy/security review.
