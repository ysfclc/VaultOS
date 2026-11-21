# Community presence and spontaneous meetups roadmap

_Status: proposed product direction, not implemented. Added 2026-11-21._

## Product opportunity

VaultOS can grow from a personal, local-first utility into an opt-in tool that helps trusted people turn shared availability and physical proximity into real-world connection. The core job is not social-media posting or passive location tracking. It is helping people notice a useful, welcome opportunity to meet in person, with minimal friction and strong control over privacy.

Example: Yusuf and Mehmet mutually share live presence. When they happen to be near one another, VaultOS can notify each person that the other is nearby, allowing either to suggest a quick coffee, cigarette break, walk or catch-up. Neither person should be able to track the other unilaterally.

## Proposed feature set

### C-01 — Mutual nearby notifications (core concept)
- A user creates or joins a trusted circle and explicitly invites contacts.
- Live location sharing is a mutual, revocable relationship: both sides must accept before proximity matching is active.
- Notify when two opted-in members enter a user-configurable proximity range, with sensible defaults and cooldowns to prevent repeated alerts.
- Notification wording should communicate approximate proximity, not expose an exact pin by default.
- Either person can start a lightweight "meet now?" invitation; the recipient can accept, decline or ignore without social penalty.
- Sharing can be paused instantly, scheduled, time-limited or restricted to selected circles.
- No public discovery, stranger matching or hidden/background sharing.

### C-02 — Availability and intent
- Optional short-lived statuses such as "free for 15 min", "coffee?", "walk", "lunch", "smoke break", "working nearby" or "not available".
- Status visibility is limited to selected trusted circles and expires automatically.
- A nearby alert can be suppressed unless both people are marked available, if users prefer fewer interruptions.
- One-tap availability windows support spontaneous plans without requiring a chat thread.

### C-03 — Group convergence
- Show when multiple members of a trusted group are in the same general area, subject to mutual permissions.
- Suggest a low-friction group meetup when several people overlap nearby.
- Let one member propose a temporary meetup point or time; others can accept/decline.
- Support reunions at a chosen destination (e.g. everyone is heading toward the same cafe or event) without exposing continuous exact location to the group.

### C-04 — Temporary event and occasion circles
- Create an ephemeral circle for a concert, wedding, conference, trip, festival, university day or night out.
- Members opt in with an expiry time; discover friends who are nearby or also attending.
- Event circle and location sharing end automatically at the event expiry unless renewed.
- A QR invite can make joining an in-person group fast while preserving explicit acceptance.

### C-05 — Shared routines and useful local overlap
- Optional, user-created routines such as "usually at this coworking space on Tuesdays" or "walking after work".
- Notify only when users deliberately publish a routine/availability window and a trusted contact overlaps; do not infer sensitive routines from background location.
- Potential use cases: coworking together, lunch breaks, dog walks, gym sessions, study sessions, commuting part of the way together, and sharing a taxi/ride when both explicitly want it.
- Keep this as a later extension; never infer or announce someone's home, workplace or habitual schedule.

### C-06 — Practical handoff and proximity reminders
- Optional reminders when a trusted contact is nearby and the user has explicitly tagged something to hand over (keys, a book, borrowed item, document).
- Both parties see the context only when the owner chooses to share it.
- This is a utility use case, not a reason to keep location sharing permanently active.

## Experience principles

1. **Mutual consent, not follower tracking.** Both users explicitly accept the relationship and the location-sharing purpose.
2. **Approximate by default.** Proximity state is enough for a nudge; exact location is shared only through a separate, explicit action.
3. **Visible control.** Persistent, understandable sharing indicator; one-tap pause/stop; per-person and per-circle controls.
4. **Time-bounded by default.** Sharing sessions expire automatically; users can choose a longer duration knowingly.
5. **No pressure.** Accept, decline, ignore, mute and block are first-class actions. No read receipts or public activity score.
6. **Quiet intelligence.** Deduplicate alerts, use entry/exit hysteresis and cooldowns, respect quiet hours and battery constraints.
7. **Safety first.** Block/revoke immediately, prevent re-invites from blocked users, rate-limit invitations, and make sharing state auditable to the user.
8. **No location history by default.** Do not build a movement timeline or retain raw location trails for engagement analytics.
9. **Local-first remains the default for existing VaultOS features.** Community presence is a separately opt-in connected capability; it must not silently turn the personal vault into a cloud product.

## Architecture and privacy decision gate

The current VaultOS product contract says content stays on-device, no account/sync/analytics/cloud service is required, and the manifest has no INTERNET permission. Real-time cross-device proximity cannot be delivered under that offline-only contract. It requires a separately designed connected service and Android location/background execution permissions.

Before implementation, decide and document:
- Whether community presence is a separately installed/optional connected module or a deliberate expansion of the main app's privacy contract.
- Identity and contact invitation model, including verified ownership and account recovery.
- Secure transport and storage; avoid storing raw location longer than strictly required to calculate proximity.
- Whether proximity matching can use short-lived coarse location/geohash or on-device comparison where feasible; exact-location relay must not be the default.
- Android foreground-service/background-location policy, battery impact, permission education and OEM-specific behavior.
- Abuse threat model: stalking, coercion, compromised accounts, spoofing, unwanted invitations and location inference.
- Data retention, deletion, export, incident response and legal/privacy review for relevant jurisdictions (including GDPR).
- A transparent, testable consent state machine and audit trail.

No location or contact permission should be added until this gate is approved. Do not weaken VaultOS's existing local-only behavior as a shortcut.

## Delivery sequence

- **Discovery:** validate the need with a small set of trusted-circle users; define the mutual consent and threat models; compare foreground-only, time-bounded live sessions and always-on modes.
- **Prototype:** mock the trusted-circle, sharing controls, nearby state and meet-now invitation flows with simulated locations. No real location collection or backend.
- **Technical spike:** prove Android background reliability, battery use, approximate proximity matching, revocation latency and notification deduplication on supported Android/OEM versions.
- **Private alpha:** small invited group, explicit mutual opt-in, short-lived sessions, approximate proximity alerts and one-to-one meet-now invitations only.
- **Safety and reliability gate:** test pause/revoke/block, permission removal, offline transitions, stale locations, spoofing, account compromise, quiet hours and data deletion.
- **Expansion:** group convergence, temporary event circles, availability statuses, routines and practical handoff reminders only after alpha evidence supports them.

## Success measures

Measure useful outcomes rather than time spent in the app:
- Percentage of proximity alerts that lead to an invitation and mutually accepted meetup.
- Meetups accepted/completed per active opted-in circle.
- User-reported usefulness of alerts and false-positive/missed-opportunity rates.
- Retention of trusted circles without increasing notification opt-outs.
- Battery consumption, location freshness and revocation latency.
- Safety incidents, unwanted-contact reports and time to block/revoke (target: zero tolerance for unresolved severe cases).

Do not optimize for continuous location sharing, number of alerts, screen time or social pressure.

## Explicitly out of scope for the first version

- Public nearby-people map or open stranger discovery.
- Exact location visible automatically to all circle members.
- Permanent movement history or inferred home/work patterns.
- Gamification, leaderboards, streaks or engagement bait.
- Automatic meetups, messages or location sharing without an explicit user action.
