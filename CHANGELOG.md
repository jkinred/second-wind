# Changelog

All notable changes to YB Second Wind. Format follows Keep a Changelog; versions follow SemVer.

## [0.1.0] — 2026-10-02

First release. Clean rewrite from the protocol specification in `docs/PROTOCOL.md`.

### Added
- Conversation list grouped by contact, with unread and queued indicators.
- Thread view with per-message state: ⏳ Queued → ↑ Sending n/m → → YB, each with a plain-language explanation on tap.
- Composer pinned above the keyboard; live counter of characters (including addresses), parts and estimated credits (1 per 50 characters + 1 per SMS recipient).
- Status chip and device panel: connection, credits with age, last phone pull, configured satellite inbox-check and tracking intervals, storage warning, last connection and rung, firmware, mode.
- Favourites (starred or named recipients), recents, "My group", and picking a single e-mail/phone from phone contacts without contacts permission.
- Plain-language banners with a remedy for every known failure: Bluetooth off (with turn-on prompt), no device, missing/rejected credentials, unreachable, stale pairing, dropped connection.
- Troubleshooting page: log viewer with copy, forget-pairing-and-reconnect.
- Automatic recovery when another phone has paired with the Yellowbrick since the last connection.

### Protocol correctness
- Stable per-part ids persisted with each message; retransmission after a lost confirmation reuses the same id (idempotent on the device). Confirmations are correlated exactly; unknown ids are logged and ignored.
- Ids allocated to avoid every id still remembered by the phone; thread ids avoid pending messages.
- STATUS bytes 3–5 decoded (storage high-water, tracking interval, inbox-check interval).
