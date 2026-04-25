# Privacy

## Data minimization

- Only collect what is needed to investigate a missing-person case.
- Sensitive personal data (location, face images, contact details, minors, health/biometric-like data) is minimized, encrypted at rest in production, and never displayed without operator authentication.
- Android stores **only** a limited operator-scoped cache. Sensitive evidence and audit data live on the backend.

## Retention

- Default retention TODO: define per-case retention with operator-set lifetimes; archive then purge.
- Audit log: append-only, retained per legal requirement, with redaction tooling for subject-rights requests.

## Subject rights

- Provide a tool to redact / export / delete evidence on legitimate request.
- Person-of-interest data should be removable end-to-end (cache, S3, audit-log redaction).

## Children

- If a case involves a minor, the UI must show a `MINOR` flag and increase the required corroboration thresholds.

## Cross-language disclosure

- Hebrew, Russian, and English UI copies of every consent-relevant string must match the English source semantically. Translation review is part of the security/legal sign-off for every release.
