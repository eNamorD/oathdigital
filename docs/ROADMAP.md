# Oath Digital Roadmap

This is the forward-looking project-level source of truth for planned work.
When a task is done, it should be deleted from this file. Past work can be
identified by looking through specs and plans in `docs/superpowers/`.

Refrain from labeling phases with numbers, as the roadmap items may shift
in priority. (Some items may still be labeled as such, for consistency with specs)

## Now

**Phase 3 — Powers and battle plans** is active. The first batch (30 denizens,
12 edifice faces, 15 relics, the Wandering Flame phase power and the Mob card
play modifier) is designed in the
[powers design](superpowers/specs/2026-09-20-powers-design.md), and its
per-power rulings are in the
[powers rulings](superpowers/specs/2026-09-20-powers-rulings.md). It is built
in five slices: verify and extend the shared engine pieces (slice 0), When
Played, ACTION and WAKE powers (1), modifiers, persistent rules and card-play
triggers (2), battle plans (3), and the banner faces (4). Each slice gets its own
plan under `docs/superpowers/plans/` before any code is written.

## Next

Work toward a playable all-Exile alpha before expanding into the Empire and
campaign-continuity rules.

### Phase 3 - Powers and battle plans

Powers are declared as contributions to walker procedures, grouped by mechanics
and timing rather than one file per card: legality and cost modifiers, resource
and card movement, roll transforms, losing-force replacement, nested actions,
phase and victory triggers, and battle plans. The base procedures are ported, so
the remaining work is the catalog itself: the first batch above, then the rest
of the denizens, relics, edifices, Foundation, Legacy, banner, site and Vision
powers through the same contribution boundaries. The engine never infers
mechanics from rules text; unimplemented handlers stay explicit.

### Phase 5 - All-Exile alpha readiness

The distribution and runtime foundation is in place. The outstanding container
release gate and deferred review findings are recorded in
[Phase 5 follow-ups](operations/phase-5-follow-ups.md).

1. [ ] Replace development identity assumptions with a minimal safe alpha
   access flow for hosts and invited seats; do not expose the loopback
   development shim on a network.
2. [ ] Verify two or more browsers on separate machines can create/join,
   reconnect, reload persisted games, and complete representative multi-player
   turns over a LAN.
3. [ ] Add backup and restore guidance plus a clear alpha data reset and upgrade
   policy.
4. [ ] Complete release operations: publish multi-architecture Linux OCI images
   for `linux/amd64` and `linux/arm64`, automate a GitHub prerelease, document
   browser support and firewall/reverse-proxy/TLS requirements, and publish a
   short host/player quick-start. Rerun the complete verification and packaged
   smoke gates before each alpha build.

### Phase - Player-facing action history

Add a human-readable action log similar to HRF's log, but derive it from
authoritative event batches through a typed semantic formatter. Group related
events into one player action, preserve stable sequence references for replay,
color player labels, and distinguish major actions, minor actions, decisions,
resource changes, rolls, and victory checks. Produce public and player-scoped
projections so hidden draws, facedown identities, and private choices are never
leaked. Keep the raw loopback development event log separate.

### Powers-related deferred items

- [ ] **Deferred: walker follow-ups.** Make the Recover roll automatic like the
  others, and add real consent (for Narrow Pass and beyond) as its own system
  separate from Negotiation. Audit the first-game rules the walker actions
  dropped as gates (exile-only roles, unaltered Foundations, inactive legacies).
  Add the remaining attacker, defender and bandit battle plan families,
  non-deterministic loss choices, and the additional Raid, victory, defeat and
  At End handlers that the Campaign timing windows already expose.
- [ ] **Deferred: walker-native card play through card slots.** Card play still
  runs through the legacy `CardPlay.legalChoices` and `plannedOperations` helpers
  rather than through Operations, so a power cannot change the placement
  procedure. Powers that need to (People's Favor: Mob may discard a site
  denizen first, even at a full site) can today reach only the adviser limits
  exposed by `CardPlayProcedure.PlacementTree`. The redesign plays a card to a
  card slot instead of to a site. By default the options are the empty slots
  at the actor's site and the actor's empty adviser slots. When no adviser slot
  is empty, slots holding discardable advisers also become options. Site-card
  discards, Homeland replacement, the revealed-Vision replacement and the
  Silver Tongue limit all become contributions to the slot options. It touches
  Search, facedown-adviser play, Conspiracy, the projections and the frontend,
  so it needs its own spec. Until then Mob uses a single `PlacementRules` value
  on `PlacementTree` that carries the adviser limits and a
  "may discard a site card first" permission.

### Phase - Empire and campaign continuity

After the all-Exile alpha, implement Chancellor/Citizen roles, Imperial forces,
Grand Scepter and Reliquary behavior, Citizenship through Negotiation,
Successor goals, forced and self-exile, Imperial setup/turn/end rules, and the
remaining production authentication work needed for broader hosting.

Then implement the Chronicle and persistent campaign: generalized later-game
setup, Atlas transitions, Chronicle tasks, world reconstruction, Reliquary
changes, Foundation mutation, Legacy activation/scoring, Oathkeeper goal
changes, era scoring, saved-campaign continuation, and campaign browsing.

### Phase - Gameplay completeness gate

Audit every rulebook procedure and every runtime component handler against the
traceability matrix; close remaining hidden-information, simultaneous-ordering,
randomness, consent, and nested-action gaps; then run complete replay,
persistence, server, Scala.js, packaged-network, and browser acceptance gates.

## Later

- [ ] **X6 — Complete production authentication and deployment**
  - [ ] Add OIDC Authorization Code + PKCE, session issuance/rotation/logout,
    secure cookie-setting responses, and frontend login/session-expiry UX.
  - [ ] Add membership-management UX, rate limiting, audit logging, and the
    final production deployment gate. The alpha phase may add a narrower safe
    invitation flow first; until then, keep development routes loopback-only.
- [ ] **L3 — Licensed game assets with permanent visual fallbacks**
- [ ] **L4 — Saved-game browser and replay navigation** (campaign-continuity
  phase)
- [ ] **L5 — Asynchronous accounts, invitations, and notifications**
- [ ] **L6 — Incremental implementation of remaining phases and rules** (tracked
  in the phased sequence above)
- [ ] **L7 — Incremental synchronization transport**
  - Replace complete-snapshot polling with conditional responses, projection
    deltas, long polling, SSE, or another push transport when scale or latency
    justifies the added server lifecycle complexity.

## Standing rules

- The verification gate is the complete JVM and Scala.js suites, the optimized
  Scala.js linker, runtime-catalog validation and equality, the architecture and
  documentation-link checks, and diff validation.
- Before the first public release, event formats and fixtures may change in
  place; backward compatibility, migrations, and version bumps are not required.
  Update the current writer, reader, replay tests, fixtures, and development
  data together. Public release establishes the compatibility baseline.
