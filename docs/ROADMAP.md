# Oath Digital Roadmap

This is the project-level source of truth for planned work. The Main Thread
maintains priorities and status. Implementation work runs in separate Codex
tasks, and an item moves to Done only after its changes and verification have
been reviewed.

## Now

**Phase 1 - All-Exile Negotiation** is active in the implementation task. It
uses a multi-party, participant-authored deal with unanimous consent, atomic
favor/relic transfers, and binding hidden-information disclosures.

## Next

Work toward a playable all-Exile alpha before expanding into the Empire and
campaign-continuity rules.

### Phase 1 - Complete base actions

1. [x] Complete Campaign Raid without expanding the battle-plan catalog: targets,
   defense, losses, transfers, private-card disposal, favor burn, pawn
   relocation, and terminal windows.
2. [x] Implement Forge.
3. [x] Implement banners and Challenge, including base banner resource movement.
4. [x] Implement the core minor actions: play/discard a facedown adviser,
   reveal/peek at relics, withdraw warbands from the pawn's site while leaving
   one, and deploy board warbands to that site when the player rules it.
5. Implement ordinary all-Exile Negotiation as an atomic, consented favor/relic
   exchange. Citizenship remains deferred with the Empire.

### Phase 2 - Complete all-Exile goals and endings

1. Generalize Oathkeeper qualification beyond Supremacy.
2. Implement Vision reveal, qualification, victory, and Conspiracy.
3. Implement all-Exile round endings and War Exhaustion.
4. Complete suspended-decision and action-boundary processing needed by these
   procedures.

### Phase 3 - Powers and battle plans

Implement powers after their base procedures are stable, grouped by mechanics
and timing rather than one file per card: legality and cost modifiers, resource
and card movement, roll transforms, losing-force replacement, nested actions,
phase/victory triggers, and then the remaining attacker, defender, and bandit
battle plans. Add Foundation, Legacy, relic, edifice, banner, site, and Vision
handlers through the same typed boundaries. Unknown relevant handlers continue
to reject explicitly until implemented.

### Phase 4 - Player-facing action history

Add a human-readable action log similar to HRF's log, but derive it from
authoritative event batches through a typed semantic formatter. Group related
events into one player action, preserve stable sequence references for replay,
color player labels, and distinguish major actions, minor actions, decisions,
resource changes, rolls, and victory checks. Produce public and player-scoped
projections so hidden draws, facedown identities, and private choices are never
leaked. Keep the raw loopback development event log separate.

### Phase 5 - All-Exile alpha readiness

1. Produce a versioned server distribution with the optimized frontend bundled,
   a documented launcher, and no local sbt/Node requirement.
2. Support configurable bind address, port, public base URL, persistent database
   path, and clean database initialization/migration.
3. Replace development identity assumptions with a minimal safe alpha access
   flow for hosts and invited seats; do not expose the loopback development shim
   on a network.
4. Verify two or more browsers on separate machines can create/join, reconnect,
   reload persisted games, and complete representative multi-player turns over
   a LAN. Document firewall and reverse-proxy/TLS requirements for Internet
   hosting.
5. Add health/startup diagnostics, actionable logs, graceful shutdown, backup
   and restore guidance, browser-support expectations, and a clear alpha data
   reset/upgrade policy.
6. Publish a short host/player quick-start and run a packaged-build smoke test
   from a clean environment before each alpha build.

### Phase 6 - Empire and campaign continuity

After the all-Exile alpha, implement Chancellor/Citizen roles, Imperial forces,
Grand Scepter and Reliquary behavior, Citizenship through Negotiation,
Successor goals, forced and self-exile, Imperial setup/turn/end rules, and the
remaining production authentication work needed for broader hosting.

Then implement the Chronicle and persistent campaign: generalized later-game
setup, Atlas transitions, Chronicle tasks, world reconstruction, Reliquary
changes, Foundation mutation, Legacy activation/scoring, Oathkeeper goal
changes, era scoring, saved-campaign continuation, and campaign browsing.

### Phase 7 - Gameplay completeness gate

Audit every rulebook procedure and every runtime component handler against the
traceability matrix; close remaining hidden-information, simultaneous-ordering,
randomness, consent, and nested-action gaps; then run complete replay,
persistence, server, Scala.js, packaged-network, and browser acceptance gates.

## Later

- [ ] **X6 — Complete production authentication and deployment**
  - [x] Provider-neutral users, OIDC identity links, memberships, digest-only
    sessions, shared HSQL lifecycle, membership authorization, authenticated
    projections/commands/bootstrap, and session-cookie/CSRF validation.
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

## Done

- [x] **Phase 1 - Core minor actions.** During Act, players can play or discard
  a facedown adviser as if searched, privately inspect relics at their site,
  reveal a held facedown relic, and move legal quantities of their warbands to
  or from their pawn's site for 0 Supply. Commands and replay enforce card
  restrictions, Homeland replacement, site-play favor, regional discards,
  private relic knowledge, site rule, and the final-warband limit. Conspiracy
  and relevant printed modifiers reject through an audited transitional power
  registry until Phase 3. Authenticated transport, owner redaction, persistence
  and reopen, action-boundary evaluation, and accessible Scala.js controls are
  covered.

- [x] **Phase 1 - Bounded banners and Challenge.** The fixed Mob and Wandering
  Flame faces now support typed public holder/resource state, the common
  zero-Supply action for adding faceup favor or secrets, and a 1-Supply
  Challenge with strict eligibility, co-location, atomic transfer, and
  authoritative replay. People's Favor distributes deterministically to the
  least-stocked bank with leftmost ties; Wandering Flame exposes only its
  genuine least-site tie choices to the challenger. Projection, authenticated
  transport, persistence/reopen, and Scala.js controls preserve decision
  ownership. Altered banner faces and their additional powers remain deferred
  to the powers phase.

- [x] **Phase 1 - Bounded Forge.** A ruling Exile at a printed Forge site can
  spend 1 Supply, assign the exact printed favor/secret multiset one apiece to
  three empty denizens, and take the authoritative relic-deck top facedown.
  Stable typed assignments, finite favor banks, owner-only projection,
  pre-port command validation, durable replay facts, authenticated/development
  transport, persistence/reload, and accessible stale-safe Scala.js controls
  are tested. The audited current component vocabulary has no base Forge
  modifier; changed or unknown active handler vocabularies block explicitly.

- [x] **Phase 1 - Complete base Campaign Raid.** Raid has typed canonical pawn,
  relic, and banner targets; player-owned plan windows; recorded dice, losses,
  and ordered resolution; exact People’s Favor returns and Darkest Secret
  burns; owner-redacted facedown adviser/relic disposal with Conspiracy boxed;
  and attacker-owned non-Travel pawn relocation. Lost-Raid force effects use a
  validated co-location origin. Commands, replay, transport, persistence,
  projection, and Scala.js controls reject stale or tampered facts while
  additional powers and battle plans remain deferred.

- [x] **Campaign plan architecture and defender stage.** Campaign now
  orchestrates attacker and defender plan windows through registered handlers
  with stable source identity, typed costs/effects, generic projection, and
  replay validation. Outriders, Brass Army, the Oathkeeper/Usurper defense
  bonus, and deterministic Watchdog use that boundary without component IDs in
  Campaign or projection. Player defenders act during their own scoped window;
  their procedure view is redacted again once that window closes. Reserved
  extension effects reject explicitly until a matching executor exists.

- [x] **Campaign expansion — player-defender Conquest baseline.** The mandatory
  target's ruler is recorded as a typed player defender and every optional
  target must share that ruler. Defense aggregates public target forces;
  victory removes the complete defending force, kills half rounded down,
  returns survivors to the defender board, and reuses atomic attacker
  placement. Both attacker and defender losses use registered, replay-validated
  disposition policies. Titled defenders and unknown defender-relevant powers
  block until their decision windows exist, while known attacker-only powers do
  not block defense. Projection and command legality share the complete check;
  no facedown information or defender controls are exposed.

- [x] **Campaign expansion — complete multi-site bandit Conquest.** The pawn
  site is mandatory and any legal same-ruler sites may be selected in canonical
  order, subject to Pass. One battle aggregates their defense and bandit force;
  victory resolves every target atomically and distributes surviving attackers
  through a replay-validated per-site allocation. A registered stable-ID loss
  policy emits applied remove/preserve/relocate/replace effects, prevents
  placement over uncleared forces, and provides the extension seam for powers
  that modify or replace losing-force behavior. Refill and Supremacy run once
  after complete placement; a displaced Oathkeeper chooses among tied leaders
  through a durable owner-scoped decision.

- [x] **Campaign expansion — partial-force formation UI.** Selecting the
  mandatory pawn-site target now opens a local, projection-backed formation
  step with server-authored force bounds, available warbands, Supply cost, and
  pre-plan dice. Accessible direct/decrement/increment controls require an
  explicit confirmation; Back and Cancel remain local. Stale context clears
  formation, inactive viewers receive none, and unaffordable Campaigns project
  no action. A player with no board warbands receives the legal `0..0` empty-pool
  formation. Command validation and replay retain authority.

- [x] **Campaign expansion — ordered plans and Brass Army.** The attacker may
  use distinct accessible plans once each in an authoritative chosen order,
  then explicitly finish the plan window before the server rolls exactly once.
  Outriders composes with paid Brass Army; the latter places one secret on an
  empty faceup held relic and adds four attack dice without increasing physical
  force. Events, replay, private projection, HTTP, persistence, and UI preserve
  plan order, costs, reveals, modifiers, dice, and excess-skull scoring while
  rejecting duplicates, tampering, stale choices, and pre-validation RNG use.

- [x] **Campaign expansion — first optional attacker battle plan.** Campaign
  now pauses before attack randomness for an actor-private choice between an
  explicit skip and each accessible Outriders source. Selecting Outriders may
  reveal a facedown card and records its stable source, exact handler, costs,
  mechanical result, and physical dice; replay recalculates the attack and
  ignored skull losses. Rejected commands consume no randomness, malformed
  client choice shapes fail decoding, and every other relevant plan remains
  conservatively blocked.

- [x] **Campaign expansion prerequisite — typed power boundary.** All eight
  Campaign timing windows are explicit, accessible powers are discovered and
  ordered by stable source and exact handler ID, and faceup `Vow of Peace` is
  the sole safely executable mandatory handler. Facedown passive text is
  inactive. Optional plans and relevant unimplemented effects reject with
  their source identity until a choice, cost, and recorded-resolution contract
  exists; no printed option is inferred or auto-selected.

- [x] **Bounded Campaign — single-site bandit Conquest.** Fixed unaltered,
  all-Exile games can spend 2 Supply to attack the mandatory pawn-site bandits
  through typed target selection. Server-recorded physical attack/defense dice,
  explicit sacrifice and conquest-placement decisions, finite force movement,
  replay validation, scoped HTTP/Scala.js controls, persistence/reload, and the
  shared bandit-refill-then-Supremacy boundary are tested. Relevant unsupported
  Campaign/battle-plan powers across the actor's full access reject explicitly.
  The browser exposes the authoritative `0..available` force range and requires
  explicit formation confirmation. Player defenders,
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

The combined milestone passes 295 JVM tests, 84 Scala.js tests, the Scala.js
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
