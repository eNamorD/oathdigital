# Oath Digital Roadmap

This is the project-level source of truth for planned work. The Main Thread
maintains priorities and status. Implementation work runs in separate Codex
tasks, and an item moves to Done only after its changes and verification have
been reviewed.

## Now

No item is currently assigned.

## Next

**Campaign expansion**, building on the verified single-site bandit Conquest
slice. Remaining work is player defenders, optional additional same-ruler
sites, accessible partial-force browser selection (the engine/API already
accept partial force), executable battle plans, multi-site loss/placement, Raid
outcomes, and the displaced-Oathkeeper tie decision.

**Forge** follows Campaign, when conquest can make player-ruled sites
reachable and its rule/modifier interactions meaningfully legal.

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

- [x] **Bounded Campaign — single-site bandit Conquest.** Fixed unaltered,
  all-Exile games can spend 2 Supply to attack the mandatory pawn-site bandits
  through typed target selection. Server-recorded physical attack/defense dice,
  explicit sacrifice and conquest-placement decisions, finite force movement,
  replay validation, scoped HTTP/Scala.js controls, persistence/reload, and the
  shared bandit-refill-then-Supremacy boundary are tested. Relevant unsupported
  Campaign/battle-plan powers across the actor's full access reject explicitly.
  The browser labels and commits all board warbands; authoritative engine/API
  partial-force support is retained for later UI work. Player defenders,
  additional targets, Raid, and executable battle plans remain deferred; see
  `docs/architecture/bounded-campaign.md`.

- [x] **Bounded first-game Oathkeeper/Usurper ending.** The fixed unaltered,
  all-Exile profile now evaluates Supremacy through one state-based path at
  completed action boundaries, retains a tied current holder without inventing
  a general F7 resolver, honors the no-Empire limiter, flips Oathkeeper to
  Usurper on Wake, and records a retained-Usurper Wake victory. Replay-validated
  events in the current pre-release format, HSQL reload, HTTP/client projection,
  and image-independent UI status are covered. The displaced-holder
  tied-recipient choice remains attached to future Campaign decision work; no
  Campaign, Vision, Empire ending, or Chronicle behavior was added.

- [x] **Campaign prerequisite 2 — reusable board target selection.** A typed,
  server-authorized selection protocol now represents sites, site cards with
  denizen/edifice identity, player advisers, and player relics independently
  from private card decisions. Setup placement auto-activates; Travel, Muster,
  and separate favor/secret Trade modes use action-first selection with
  projected costs/yields, accessible candidate controls, stable selected state,
  cardinality-gated multi-select support, and stale-context clearing. Adviser,
  relic, and multi-site hooks are prepared but no Campaign mechanics activate
  them.

- [x] **Campaign prerequisite — authoritative site rule and force
  presentation.** `SiteForces` remains the sole stored source and directly
  derives unruled, Bandit, shared Empire, or current-player rule. Shared
  player/same-ruler/enemy checks now support Economy access and Pass consent;
  public typed projections, strict HTTP/Scala.js decoding, accessible labels,
  and stable player/Empire/Bandit colors expose every site's physical force.
  Corrupt lineage mappings fail explicitly. Bandit refill remains deferred to
  Campaign because no general action-completion seam exists.

- [x] **L6e — Bounded Recover action slice.** Exile-only,
  unaltered-Foundation Recover spends Supply for recorded pairs of typed defense
  dice, accumulates shields and doublers across rolls, permits an unsuccessful
  stop, and privately takes exactly one facedown site relic on success. Typed
  pending procedure state, replay-validated v7 events, injected server
  randomness, authenticated intents, viewer-relative projections, and inline
  Scala.js controls preserve deterministic replay and hidden information.

- [x] **Gameplay UI information and generic card decisions.** Starting-adviser
  and Search choices share one private typed decision protocol and accessible
  inline Actions panel; Search has local keep/discard ordering and explicit authoritative
  placement/replacement resolution. Typed pile displays expose public card-back
  types for the World Deck and regional discards while card fronts remain hidden; detailed sites,
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

The combined milestone passes 243 JVM tests, 73 Scala.js tests, the Scala.js
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
