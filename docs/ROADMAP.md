# Oath Digital Roadmap

This is the project-level source of truth for planned work. The Main Thread
maintains priorities and status. Implementation work runs in separate Codex
tasks, and an item moves to Done only after its changes and verification have
been reviewed.

## Now

No item is currently assigned.

## Next

The next milestone has not yet been selected.

## Later

- [ ] **X6 — Complete production authentication and deployment**
  - [x] Provider-neutral users, OIDC identity links, memberships, digest-only
    sessions, shared HSQL lifecycle, membership authorization, authenticated
    projections/commands/bootstrap, and session-cookie/CSRF validation.
  - [ ] Add OIDC Authorization Code + PKCE, session issuance/rotation/logout,
    secure cookie-setting responses, and frontend login/session-expiry UX.
  - [ ] Add membership-management UX, rate limiting, audit logging, and the
    final non-loopback deployment gate. Until then, keep development routes
    loopback-only.
- [ ] **L3 — Licensed game assets with permanent visual fallbacks**
- [ ] **L4 — Saved-game browser and replay navigation**
- [ ] **L5 — Asynchronous accounts, invitations, and notifications**
- [ ] **L6 — Incremental implementation of remaining phases and rules**
- [ ] **L7 — Incremental synchronization transport**
  - Replace complete-snapshot polling with conditional responses, projection
    deltas, long polling, SSE, or another push transport when scale or latency
    justifies the added server lifecycle complexity.

## Done

- [x] **Gameplay UI information and generic card decisions.** Starting-adviser
  and Search choices share one private typed decision protocol and accessible
  inline Actions panel; Search has local keep/discard ordering and explicit authoritative
  placement/replacement resolution. Typed pile displays preserve facedown World
  Deck privacy while exposing public regional-discard tops; detailed sites,
  viewer-relative redacted player boards, bottom development controls, player
  selectors, and a loopback-only raw event log are projected and rendered
  without new assets.

- [x] **L6d — Bounded Economy action slice.** Exile-only, unaltered-Foundation
  Muster and Trade share authoritative denizen access, suit/adviser matching,
  typed rule resolution, limited resource movement, replay-validated v6 events,
  persisted commands, scoped projections, HTTP intents, and Scala.js controls.

- [x] **L6c — Rest and turn advancement.** Act lifecycle validation is
  action-neutral. Bounded exile Rest now returns controlled card resources,
  reveals secrets, refreshes Supply, clears per-turn state, advances the
  player/round, and enters the next Wake through replay-validated v5 events,
  persisted commands, scoped projections, HTTP routes, and Scala.js controls.

- [x] Establish the Scala/Scala.js build, HRF reference baseline, core domain
  model, source-cited rules layer, and complete reviewed component catalog.
- [x] Implement authoritative versioned events, deterministic replay,
  optimistic application services, and the schema-v3 HSQLDB event/identity
  store with concurrency, restart, and malformed-history hardening.
- [x] Deliver the exile-only introductory setup and responsive image-independent
  server UI, including player-scoped controls, reconnect/polling, restart, and
  authenticated membership/session/CSRF foundations.
- [x] Implement bounded Wake, Travel, and Search through mixed v2-v4 history,
  including typed Travel modifiers, server-prepared Search draws, private
  pending decisions, replay validation, and Scala.js controls.
- [x] Modularize gameplay into `OathRules`, phase/action modules, typed rule
  resolution, and consistently named runtime application, wire, server, and
  frontend boundaries while retaining then-current replay behavior.

The combined milestone passes 210 JVM tests, 51 Scala.js tests, the Scala.js
linker, runtime-catalog validation, and a persisted three-player browser smoke
test through Take Wealth, End Wake, Act selection, responsive site rendering,
reload reconstruction, and disconnect/reconnect recovery.

## Coordination rules

- The Main Thread owns this file and prioritization.
- Each implementation item gets a separate Codex task with explicit file
  ownership and acceptance criteria.
- Parallel tasks must avoid editing the same files or packages.
- Tasks run focused checks while parallel work is active. They must not run
  `clean` against shared build outputs.
- After parallel tasks finish, the Main Thread reviews the combined diff and
  runs the complete build, catalog validation, and test suite once.
- Completion reports must include changed files, verification commands, test
  counts, unresolved risks, and the resulting commit.
- Before the first public release, event formats and fixtures may change in
  place; backward compatibility, migrations, and version bumps are not required.
  Update the current writer, reader, replay tests, fixtures, and development
  data together. Public release establishes the compatibility baseline.
