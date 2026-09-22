# Oath Digital Roadmap

This is the forward-looking project-level source of truth for planned work.
When a task is done, it should be deleted from this file. Past work can be
identified by looking through specs and plans in `docs/superpowers/`.

Refrain from labeling phases with numbers, as the roadmap items may shift
in priority. (Some items may still be labeled as such, for consistency with specs)

## Now

**Phase — Randomized setup for alpha** is active. Phase 3's first batch (30
denizens, 12 edifice faces, 15 relics, the Wandering Flame phase power and the
Mob card play modifier) is complete and merged: designed in the
[powers design](superpowers/specs/2026-09-20-powers-design.md), with per-power
rulings in the [powers rulings](superpowers/specs/2026-09-20-powers-rulings.md).
All five slices (0 foundations, 1a-1d When Played/ACTION/WAKE powers, 2
modifiers/restrictions/triggers, 3 battle plans, 4 banner faces) are
implemented. The rest of the denizen/relic/edifice catalog is not needed for
the alpha and stays future work (tracked generally under **L6** below).

## Next

Work toward a playable all-Exile alpha before expanding into the Empire and
campaign-continuity rules.

### Phase - Randomized setup for alpha

Every game starts with sites randomized. Each Homeland is seeded with its
suit's edifice card, preferring an implemented one when the suit has one.
The top 30 cards of the world deck are randomized, and consist of
implemented denizens (and visions, as usual).

Designed in the [Chronicle and randomized setup](superpowers/specs/2026-09-21-chronicle-setup-design.md)
spec: setup becomes a function of a Chronicle (shaped like the TTS export format)
plus recorded shuffle orders, runs on the walker, and a generator produces a
random first-game Chronicle. Built in three slices, each with its own plan.

Slice 1 (Chronicle model, generator and port; trusted, development, and
authenticated bootstrap now build from a Chronicle) is done -- plan at
[docs/superpowers/plans/2026-09-21-chronicle-setup-slice1.md](superpowers/plans/2026-09-21-chronicle-setup-slice1.md).
Slices 2 (setup on the walker) and 3 (the E02/E06/E22 powers) remain.

- [ ] **Deferred: derive the lineage from the color on the server.** The color
  is always associated with a lineage, but the trusted creation request still
  carries a free-form `lineageId`, and the host page sends `<color>-lineage`.
  The server should derive the lineage and the request should drop `lineageId`.
- [ ] **Deferred: generate the game ID on the server.** The host page generates
  it in the browser (`manual-<time>-<random>`) and generates a new one when the
  server reports a duplicate. The server should assign it and return it in the
  creation response, and the request should drop `gameId`.
- [ ] **Deferred: setup follow-ups.** Simultaneous setup effects are resolved by
  the Chancellor or first player (site order is used until then). Player choices
  earlier in setup once foundations and legacies exist, such as the Chancellor
  choosing Recent or Forgotten sites. The Chronicle string codec for TTS import
  and export, including the sections that format has not defined yet. Later-game
  setup (Empire `world` sites, stored denizens and relics). WHEN EXPLORED
  triggers once an explore procedure exists.

### Phase 5 - All-Exile alpha readiness

The distribution and runtime foundation, trusted-alpha seat access, the alpha
data policy, and a two-machine LAN and HTTPS acceptance run of the local
`0.1.0-alpha.1` build are done. The run's
[record](testing/alpha-acceptance-0.1.0-alpha.1.md) passed with one NGINX
log-redaction mitigation and a throwaway local CA. What remains is release
operations. Deferred findings and requested UI changes are in the
[Phase 5 follow-ups](operations/phase-5-follow-ups.md).

1. [ ] Complete release operations: publish multi-architecture Linux OCI images
   for `linux/amd64` and `linux/arm64`, automate a GitHub prerelease, document
   browser support and firewall/reverse-proxy/TLS requirements, and publish a
   short host/player quick-start. Bundled-runtime archives for macOS arm64,
   Windows x64, and Linux x64 with a double-click Start are implemented; their
   workflow run and acceptance rows 17-22 remain unexecuted. Guidance and a
   gated workflow are committed,
   but the `linux/amd64` and `linux/arm64` Buildx smokes, GitHub Actions run,
   GHCR manifest publication, and GitHub prerelease remain unexecuted, as does
   the container smoke of the trusted-seat flow (no Docker daemon on
   2026-09-09). Rebuild before release, because the tested archive predates the
   documentation fixes made afterwards. Rerun the complete verification and
   packaged smoke gates, and a new acceptance record if the release commit
   differs from the tested one, before each alpha build.

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
- [ ] **Deferred: an adviser-slot decision option.** `DecisionOptionRef` names a
  card by identity, and `WalkerDecisionProjector` drops any decision that names
  a card its viewer may not identify, so a decision cannot offer another
  player's facedown adviser. Relics have an identity-free `RelicSlot`
  reference, and advisers have none. Ivory Eye works around it with `Button`
  options keyed by owner and adviser position, which show a label and no card.
  An `AdviserSlot(owner, slot)` reference, like `RelicSlot`, would let the
  panel present the slot as a facedown card. It touches the model, the answer
  codec, the projector and the frontend, and any future power that targets a
  facedown adviser would use it.

- [ ] **Deferred: a public view of a revealed temporary hand.** The Truthful Harp
  reveals the cards it draws by recording a `Peek` for every other player. No
  operation reveals a card in a temporary hand and the hand is projected to its
  owner only, so no view shows the reveal to the other players yet.

- [ ] **Deferred: a board slot for distributions and Sticky Fire without a choice.**
  Warning Signals names the defender's board by a player option in its distribution, which
  the panel shows as a player name, and Sticky Fire asks its question even when a yes
  changes nothing (against bandits it only costs the favor). A board option, and skipping a
  question whose answers are the same, need a small change to the option vocabulary.

- [ ] **Deferred: offer a nested Campaign only when it would be accepted.**
  Knights Errant runs a Campaign inside a Muster and offers it whenever a
  Campaign is legal. A restriction on the whole Campaign (Vow of Peace, the
  Fortress start refusal) rejects the player's "campaign" answer, so a Vow of
  Peace holder is offered a Campaign that is then refused, and can only decline.
  Offering it only when it would be accepted needs the power to ask the walker
  whether the answer would pass its restrictions, which `PowerCtx` cannot do
  today. It is accepted until then.

- [ ] **Deferred: locked cards as a generic operation restriction.** Locking is
  enforced today by `DiscardRestrictions` (a faceup locked adviser, an intact
  edifice, a modifier selected for the running action, and the Hall of
  Ministers). Each path that discards a card in play attaches it, and a coverage
  test fails when a new discarding file forgets to. The intended design is a
  `Locked` `OperationRestriction` that any locked card mixes in. It is
  registered once with the validator when powers and restrictions are scanned,
  and it refuses any `Move`, `Flip` or `Swap` of that particular card (a discard
  is a `Move`), and skips `Bury`, which ignores locked. Restrictions are a
  per-call argument of `OperationPipeline.run` today, supplied only by a
  `BuildOps` node. Walker steps all run through `ProcedureWalker.recordBatch`,
  so registration is a `restrictions` field on `WalkerPowers` merged there and
  built by `OathRules` from the catalog and the state. `MinorActions` and
  `StateBasedEvaluation` call the pipeline directly and need it too. It would
  retire `DiscardRestrictions`' locked rules, `CardPlay`'s locked-adviser check
  and Horned Mask's filter, and needs an audit of every step that legitimately
  moves a locked card (negotiation swaps, Chronicle). Roughly one task of 300
  lines, with regression risk in the Negotiation and Campaign suites.

- [ ] **Deferred: the Grand Council and Festival banner faces.** They are listed
  as synthetic ids in the reviewed catalog and have no behaviour.

- [ ] **Deferred: Mercenaries' player-chosen sign.** Mercenaries adds attack dice
  when its user attacks and removes defense dice from the attacker when it
  defends. The card lets the player choose the sign, and the plan fixes it by side.
  Choosing it needs a decision inside the plan and a preview that shows both.

- [ ] **Deferred: plan-restricting powers, Bag of Siegeworks and Empire defenders.**
  Peace Envoy and other powers that restrict which plans a side may choose have
  no contribution to hook on yet. Bag of Siegeworks has a reviewed-catalog entry
  in `CampaignPowers` and no plan. Empire defenders are not modelled. The reviewed
  catalog's entries for Outriders, Brass Army and Watchdog are inert since slice
  3b and go with the reviewed catalog.

- [ ] **Deferred: record a bandit's applied battle plan as an event.** A player's
  plan is a recorded answer that later windows read. A bandit defender applies its
  cost-free plans without asking, so `CampaignPlanApplication` records the use as
  a `ModifyDicePool` marker under `campaign.plan-applied.<kind>.<id>`. The marker
  shows in the journal as a dice-pool change and suites that count a Campaign's
  `ModifyDicePool` operations see it. A dedicated recorded operation would say
  what happened.

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
