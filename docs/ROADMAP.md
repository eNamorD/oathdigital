# Oath Digital Roadmap

This is the forward-looking project-level source of truth for planned work.
When a task is done, it should be deleted from this file.

Refrain from labeling phases with numbers, as the roadmap items may shift
in priority. (Some items may still be labeled as such, for consistency with specs)

## Now

**Phase - Power log lines** is next, then **Phase - Catalog batch 2**.

## Next

Work toward a playable all-Exile alpha before expanding into the Empire and
campaign-continuity rules.

### Phase - Power log lines

Powers change what an action does without saying so in the Game Log. A
phase power used as an action logs a generic "Used {card}", and a modifier
is named only on its action's start line. Automatic powers and Restrictions
leave no line at all: when Vow of Peace removes the attacker's sacrifice
decision, nothing records why. Each power should be able to declare the lines
it contributes where it takes effect, such as "Used Vow of Peace to skip the
sacrifice step", through one generic mechanism rather than a case per card.
The generic line stays as a fallback. The mechanism has to cover powers that
remove or hide something, which record no operation of their own. The
[design](superpowers/specs/2026-09-26-power-log-lines-design.md) covers the
mechanism and the line for every implemented power.

Slice 1 merged on 2026-09-26: the mechanism, with Vow of Peace and Gambling
Hall as its first powers. Slice 2 merged on 2026-09-26: every phase power
has its own line. Slice 3 merged on 2026-09-26: every removed and hidden
option has its line. Slice 4 merged on 2026-09-26: every added effect
and altered procedure has its line. Slice 5 gives every setup rule its
line.

### Phase - Catalog batch 2

Implement 23 denizens and 8 relics, which brings every suit to the 10
denizens a generated world deck holds, so every card in it works. 37 of 255
denizens and 16 of 48 relics were implemented before this phase; after it 60
and 24 are. The [design](superpowers/specs/2026-09-26-catalog-batch-2-design.md)
lists the cards, eight engine additions, five slices by kind of power and
every card's log line, and its
[rulings appendix](superpowers/specs/2026-09-26-catalog-batch-2-rulings.md)
settles each card. Each card writes its Game Log line through the Power log
lines `Note` mechanism as it lands.

Slice 1 is done: Animal Playmates, Birdsong, Royal Stables and Forgotten
Vault, with the Travel Supply reduction. Slices 2 to 5 remain: battle plans,
actions on yourself, actions on others, then triggers and when-played powers.

Cards left for a later batch because they need engine work first:

- The Gathering and Whispering Stone need a nested Negotiation with its own
  participants.
- Golem Legions needs Muster to allow a per-source exception to the
  token-free rule and to accept the actor's own advisers as sources.
- Sneak Attack needs an off-turn nested action: a Campaign whose acting
  player is not the active player, including selecting modifiers off turn.
  Campaign powers read `ctx.activePlayer` as the attacker today
  (`PlanContext`, `BattlePlan`, `VowOfPeaceContribution`). Recorded in the
  powers design and the walker ownership and phases design.
- Peace Envoy, Marsh Spirit, True Names and Code of Honor restrict which
  battle plans a side may choose, which no contribution can hook yet.
- Council Arbiter, Deed Writer and Traveling Negotiator widen who may
  negotiate and what a deal may hold.

### Phase - Cleanup tasks

- [ ] **Implement every site power.** Each site's own power must work,
  including the River's. Site powers are declared like other powers, as
  contributions on the operation they change.
- [ ] **Log the post-action checks.** The checks that run after an action, such
  as the bandit refill and the Oathkeeper check, change the game without a Game
  Log line. Each should write a line saying what it did, for example which site
  the bandits refilled or who became Oathkeeper.

### Phase - Consent system

A way for players to request and give consent, including per-player
permissions that approve it automatically. It interacts with certain
Restrictions, probably through their "ignore" features. Narrow Pass and later
cards need it, and it is a system of its own, separate from Negotiation.

### Phase 5 - All-Exile alpha readiness

What remains is release operations. The last acceptance run is
[recorded](testing/alpha-acceptance-0.1.0-alpha.1.md). Deferred findings and
requested UI changes are in the
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
2. [ ] **Unanswered off-turn decisions.** A parked decision blocks every
   command until its owner answers. There are no timeouts, forfeits or host
   resolution. Recorded in the walker ownership and phases design.
3. [ ] **Reconnection UX.** After a client loses its connection it should
   retry with exponential backoff and show a **Try Again** button. Recorded
   in the Phase 5 follow-ups.
4. [ ] **Investigate UI work missing from the packaged build.** The operator
   did not see earlier UI changes in the `0.1.0-alpha.1` build. Recorded in
   the Phase 5 follow-ups.
5. [ ] **Phase 5 deferred defects.** Items 7, 12, 13, 16 and 18 of the
   [follow-ups](operations/phase-5-follow-ups.md#deferred-defects): bracketed
   `[::1]` rejected as loopback, the frontend linker on the test classpath,
   the test `index.html` fixture shadowing the generated one, the hardcoded
   `oathdigital:root` assertion, and no Docker `HEALTHCHECK`. Also document
   host bind mounts at `/var/lib/oathdigital` in the configuration guide.

### Setup deferred items

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
  choosing Recent or Forgotten sites. Later-game
  setup (Empire `world` sites, stored denizens and relics). WHEN EXPLORED
  triggers once an explore procedure exists. The Chronicle string codec is
  under **Phase - Empire and campaign continuity**.
- [ ] **Deferred: Desecrated Ground.** E08's ruined face lets an Exile at its
  site keep any number of revealed Visions, which turns
  `PlayerState.revealedVision` into a collection across card play, projection,
  Vision victory, the frontend and the save format. E08 never appears in
  generated games, since the Nomad homeland becomes E06. Do it when E08 can
  appear. Recorded in the rule gaps design of 2026-09-26.

### Game Log deferred items

- [ ] **Deferred: show a parked action's progress in the waiting message.**
  The Game Log posts a line only once its facts are complete, so an action
  that is still parked says nothing about its choices so far. The table's
  "Waiting on player" message is the place to show that progress. Recorded in
  the Game Log design of 2026-09-26.
- [ ] **Deferred: Game Log features the design left out.** Click-to-highlight,
  filters and search, timestamps, a server-side read marker, failed victory
  checks and negotiation proposals. Replay navigation is **L4**. Recorded in
  the Game Log design of 2026-09-26.

- [ ] **Deferred: explain restricted options where they are offered.** A
  restricted option, or a missing action control, carries a hover note (or
  similar) saying which power restricts it and why. Most Restrictions would
  then need no Game Log line. The hide hook's note from the Power log lines
  phase is the natural source. Recorded in the
  [Power log lines design](superpowers/specs/2026-09-26-power-log-lines-design.md).
- [ ] **Deferred: warn before a choice that has no effect.** The rules let a
  player pay for a power or pick an option that then does nothing: a
  modifier selected for an action it will not change (Cup of Plenty,
  Animal Playmates or Birdsong with a card of the wrong suit), a power whose
  target is empty, or a bank with no stock. The game should warn the player
  before they commit, without refusing the choice. Recorded while designing
  Catalog batch 2.

### Powers-related deferred items

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
- [ ] **Deferred: plan-restricting powers, Bag of Siegeworks and Empire defenders.**
  Peace Envoy and other powers that restrict which plans a side may choose have
  no contribution to hook on yet. Bag of Siegeworks has a reviewed-catalog entry
  in `CampaignPowers` and no plan. Empire defenders are not modelled. The reviewed
  catalog's entries for Outriders, Brass Army and Watchdog are inert since slice
  3b and go with the reviewed catalog.
- [ ] **Deferred: walker follow-ups.** Make the Recover roll automatic like the
  others. Audit the first-game rules the walker actions
  dropped as gates (exile-only roles, unaltered Foundations, inactive legacies).
  Add the remaining attacker, defender and bandit battle plan families,
  non-deterministic loss choices, and the additional Raid, victory, defeat and
  At End handlers that the Campaign timing windows already expose.
  At an action's start command, window folds and the restriction look-ahead
  see no walker procedure (`PowerCtx.procedure` is `None`), while every later
  command sees it. Make them agree before a Restriction reads the procedure.
  `WalkerDecisionProjector.waiting` reads only a parked decision's heading and
  Negotiation query, which the look-ahead never narrows, so its second
  `parkedDecide` could skip the look-ahead like the other identity-only callers.
- [ ] **Deferred: the Grand Council and Festival banner faces.** They are listed
  as synthetic ids in the reviewed catalog and have no behaviour.
- [ ] **Deferred: Mercenaries' player-chosen sign.** Mercenaries adds attack dice
  when its user attacks and removes defense dice from the attacker when it
  defends. The card lets the player choose the sign, and the plan fixes it by side.
  Choosing it needs a decision inside the plan and a preview that shows both.
- [ ] **Deferred: record a bandit's applied battle plan as an event.** A player's
  plan is a recorded answer that later windows read. A bandit defender applies its
  cost-free plans without asking, so `CampaignPlanApplication` records the use as
  a `ModifyDicePool` marker under `campaign.plan-applied.<kind>.<id>`. The marker
  shows in the journal as a dice-pool change and suites that count a Campaign's
  `ModifyDicePool` operations see it. A dedicated recorded operation would say
  what happened.
- [ ] **Deferred: a public view of a revealed temporary hand.** The Truthful Harp
  reveals the cards it draws by recording a `Peek` for every other player. No
  operation reveals a card in a temporary hand and the hand is projected to its
  owner only, so no view shows the reveal to the other players yet.
- [ ] **Deferred: a board slot for distributions and Sticky Fire without a choice.**
  Warning Signals names the defender's board by a player option in its distribution, which
  the panel shows as a player name, and Sticky Fire asks its question even when a yes
  changes nothing (against bandits it only costs the favor). A board option, and skipping a
  question whose answers are the same, need a small change to the option vocabulary.

### Engine deferred items

- [ ] **Deferred: one seam for "who may veto an operation".** `OperationPolicy`
  (an exact-shape allowlist, used by `StateBasedOperationPolicy` and
  `MinorActionOperationPolicy` on the legacy-event paths) and
  `OperationRestriction` (contextual reasons from powers) answer the same
  question through two interfaces. Migrating the two policies onto
  `OperationRestriction` would leave one seam, but it reorders precedence on
  the legacy-event path (allowlist reasons come before shape reasons today)
  and needs its own preservation argument. Recorded during the operation
  family consolidation of 2026-09-24.
- [ ] **Deferred: key `CardKnowledge` by card id.** Knowledge is stored per
  place: `siteRelics` by viewer and site, `heldRelics` and `advisers` by
  viewer, and an owner's knowledge is implicit in `identifiesCard`. The Game
  Log phase patches the gaps this leaves (an ex-owner and a site peeker both
  lose a card's name when it moves) by copying knowledge as cards move. A
  single map from card id to the players who know it would make "knowledge
  follows the card" structural instead of maintained per move. Recorded in
  the Game Log design of 2026-09-26.
- [ ] **Deferred: retire the reviewed-catalog machinery.**
  `ReviewedPowerInspector` and `PowerRuntime` are still used by 12 main files,
  and `IndexedRuleSource.handlerIds`, a temporary compatibility projection, is
  used only by tests. Move the remaining users onto the executable catalog and
  delete the reviewed catalog.
- [ ] **Deferred: decide whether to keep `DeltaMeaning`.** Nothing reads it.
  The retention decision of 2026-09-24 said to revisit it once the action
  history was designed, and the Game Log does not use it.
- [ ] **Deferred: frontend and protocol clean-up.** Decision option kinds are
  bare string literals in the frontend, and the id grammars (`stableKey`,
  decision-id prefixes) have no shared home. The projection has no protocol
  version field. `WalkerDecisionProjectorSuite` fabricates parks instead of
  reaching them through a Situation. `OathViolation.InvalidSearchPlacement`
  also reports card-play refusals outside Search (a facedown adviser play, a
  forbidden faceup Vision), and renaming it changes the wire format.
  Table-session clean-up is parked: no
  `SeatMode` enum, the `HttpGameClient` downcast for the raw event history in
  `TableSession`, and duplicate jsdom tests. Recorded in the typed decision
  form and table session designs.

### Phase - Negotiation terms and promises

A Negotiation deal holds favor and relic transfers and disclosures only. Add
promises about future actions, transfers of secrets, advisers, sites and
banners, remote and private Negotiation, a registry of term kinds, and
eligibility powers. Record ignored Negotiation rules per participant rather
than for the actor only. Citizenship offers belong to the Empire phase.

### Phase - Empire and campaign continuity

After the all-Exile alpha, implement Chancellor/Citizen roles, Imperial forces,
Grand Scepter and Reliquary behavior, Citizenship through Negotiation,
Successor goals, forced and self-exile, Imperial setup/turn/end rules, and the
remaining production authentication work needed for broader hosting.

Then implement the Chronicle and persistent campaign: generalized later-game
setup, Atlas transitions, Chronicle tasks, world reconstruction, Reliquary
changes, Foundation mutation, Legacy activation/scoring, Oathkeeper goal
changes, era scoring, saved-campaign continuation, and campaign browsing.

- [ ] **End-of-game Chronicle steps.**
- [ ] **Chronicle import and export.** The Chronicle string codec for the TTS
  format, including the sections that format has not defined yet.
- [ ] **Foundations.**
- [ ] **Legacies.**
- [ ] **Eras.**

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
- [ ] **L3 — Licensed game assets with permanent visual fallbacks**, including
  suit and restriction icons on card faces.
- [ ] **L4 — Saved-game browser and replay navigation** (campaign-continuity
  phase)
- [ ] **L5 — Asynchronous accounts, invitations, and notifications**
- [ ] **L6 — Incremental implementation of remaining phases and rules** (tracked
  in the phased sequence above; the next denizens and relics are
  **Phase - Catalog batch 2**)
- [ ] **L7 — Incremental synchronization transport**
  - Replace complete-snapshot polling with conditional responses, projection
    deltas, long polling, SSE, or another push transport when scale or latency
    justifies the added server lifecycle complexity.
  - A cheaper first step keeps redaction intact: a change-check that returns
    the viewer's current sequence (or a 304), fetching the projection only
    when it advanced. See the
    [Phase 5 follow-ups](operations/phase-5-follow-ups.md#snapshot-polling-sends-a-full-projection-on-every-tick).
- [ ] **L8 — Migrate the component catalog into typed Scala objects.**
  `docs/catalog/new-foundations-component-catalog.json` is hand-authored JSON,
  read at runtime and validated against its schema. Move denizens, relics,
  edifices, legacies, and sites into typed Scala objects (or a compile-time
  generated loader) so suit, restrictions, and modifiers are looked up as
  fields instead of string-keyed JSON traversal. The catalog already indexes
  components by ID and by the powers they print.
- [ ] **Table UI rework.** Rework the layout of player areas and sites, and
  port the map to haunt-roll-fail's canvas approach. Recorded in the card
  shape and inspection design.
- [ ] **In-game chat.**
- [ ] **Public Chronicle pages.** The winner of a game may write their summary
  of it, and players can browse all the summaries.
- [ ] **Private lineage pages.** Each lineage has its own private notebook for
  notes.
- [ ] **Distribution beyond the alpha.** Intel macOS and Linux arm64 archives,
  code signing and notarization, native installers, and spectator links.
  Fingerprint asset names so they can be cached long-term again.

## Standing rules

- The verification gate is the complete JVM and Scala.js suites, the optimized
  Scala.js linker, runtime-catalog validation and equality, the architecture and
  documentation-link checks, and diff validation.
- Before the first public release, event formats and fixtures may change in
  place; backward compatibility, migrations, and version bumps are not required.
  Update the current writer, reader, replay tests, fixtures, and development
  data together. Public release establishes the compatibility baseline.
