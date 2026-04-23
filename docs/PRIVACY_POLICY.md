# SearchAid Privacy Policy

**Last updated:** April 23, 2026

## Overview

SearchAid is a missing-person search assistance tool designed to help families and volunteers coordinate searches for vulnerable individuals. This policy describes how the app collects, uses, and protects personal information.

## Data We Collect

### Person profiles
- Name, age, photo, physical description
- Medical conditions (e.g., Alzheimer's, dementia)
- Known locations and historical places
- Social media handles and aliases
- Family notes

### Case information
- Last seen location (GPS coordinates)
- Clothing description
- Witness reports
- Search leads from open web sources

### Device data
- Approximate and precise location (with permission)
- No device identifiers or advertising IDs are collected

## How We Use Data

All data is used exclusively for:
- Building and managing missing-person cases
- Searching publicly available open sources (web pages, public social profiles, web archives)
- Generating probability-based search zones
- Coordinating search activities between operators and volunteers

We do **not**:
- Sell or share personal data with third parties
- Use data for advertising or marketing
- Access private accounts, messages, or emails
- Use illegal databases or data leaks

## Data Storage

- All data is stored locally on the device in an encrypted database (SQLCipher AES-256)
- Database encryption key is protected by Android Keystore hardware security
- No data is transmitted to our servers (offline-first architecture)
- When online search features are used, queries are sent to:
  - Google Custom Search API (for web and image search)
  - Wayback Machine CDX API (for web archive search)
- These third-party services have their own privacy policies

## Data Security

- Database encrypted with SQLCipher (AES-256)
- Encryption key stored in hardware-backed Android Keystore
- All network traffic uses HTTPS with certificate pinning
- No logging of sensitive data in release builds
- Device backup of app data is disabled
- Full audit trail of all search actions

## Permissions

| Permission | Purpose |
|-----------|---------|
| Internet | Search web sources, load images |
| Fine location | Mark last seen location, position search zones |
| Coarse location | Approximate positioning when GPS unavailable |

## Data Retention

- Data persists on the device until the user deletes it or uninstalls the app
- Closed cases can be deleted by the operator at any time
- Uninstalling the app removes all local data

## Children's Privacy

SearchAid is designed for adult operators (family members, volunteers, search coordinators). The app is not directed at children under 13.

## Open Source Searches

All web and social media searches are conducted through:
- Google Custom Search API (public web pages only)
- Wayback Machine (publicly archived web snapshots)

No private accounts, messages, or restricted content is accessed. Only publicly available information is retrieved.

## Your Rights

You have the right to:
- View all data stored in the app
- Delete any profile, case, or search data
- Disable location permissions at any time
- Uninstall the app to remove all data

## Changes to This Policy

We may update this policy from time to time. Changes will be noted with an updated date at the top.

## Contact

For questions about this privacy policy or data handling, contact the app developer.
