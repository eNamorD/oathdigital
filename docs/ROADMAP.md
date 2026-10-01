# Oath Digital Roadmap

This is the forward-looking project-level source of truth for planned work.
When a task is done, it should be deleted from this file.

Refrain from labeling phases with numbers, as the roadmap items may shift
in priority. (Some items may still be labeled as such, for consistency with specs)

## Now

**Phase - Cleanup tasks** is done except for one item blocked on the
Chronicle Phase. **Phase - Catalog batch 3** is next, then **Phase - Card
classes**.

## Next

Work toward a playable all-Exile alpha before expanding into the Empire and
campaign-continuity rules.

### Phase - Catalog batch 3

69 denizens and relics that copy an implemented power's shape and need no
engine change, in eight slices, plus a fix to Circlet of Command and Forgotten
Vault. Designed in the
[Catalog batch 3 design](superpowers/specs/2026-09-29-catalog-batch-3-design.md)
with its [rulings](superpowers/specs/2026-09-29-catalog-batch-3-rulings.md).

Slice 1a is done: the battle plans Cracking Ground, Walled Garden, Banner
Breakers, Extra Provisions, Village Constable, Encirclement, Bandit Standard
and Rival Khan. Slice 1b is done: the battle plans Disgraced Captain, Battle
Axes, Great Crusade, Pledge of Defense, The Great Levy, Rain Boots and
Garrison Armory. Slices 1c to 4 remain.

### Catalog - to verify

Cards that probably need no engine change, each with one point to check
first. A check that passes moves the card into a later no-engine batch, and
one that fails moves it to the engine-blocked phase. From the survey of
2026-09-29:

- **Challenge modifier:** Magician's Code (the first Challenge modifier; the
  `ChallengePlacement` window exists).
- **Battle plan rescoring:** Rusting Ray, Fae Battalion (hollow swords, as
  Outriders rescores), War Tortoise (both sides), Lancers (doubling the
  score), Mounted Patrol (half the pool, computed at application),
  Fearsome General (a passive attack-score change).
- **Battle plan loss interception:** Billowing Fog, Traveling Doctor, Shield
  Wall (Hospital and Sticky Fire's `CampaignLosses` wrapping; "ignore powers
  that kill all").
- **Other battle plans:** Kindred Warriors ("up to X" needs a ruling),
  Rangers (the defense pool changing after the attacker's plan), Salt the
  Earth (a `later` hook with a decision), Cursed Cauldron (a count of enemy
  kills), Sealing Ward (a persistent `CampaignGatherPools` dice add).
- **ACTION powers:** Terror Spells (two kills across sites and boards),
  Witch's Bargain (an exchange loop with no consent system), Inquisitor
  (guessing among Vision ids), Roving Terror, Homesteaders and Resettle
  (moving a card between sites or play areas, with capacity), Relic Thief (an
  automatic roll with a dynamic pool), Ancient Binding (which secret is kept),
  Convoys (moving a whole pile), Singing Mask (moving favor between a bank and
  a banner).
- **When played:** Revelation, Blackmail and Dissent (other players' decisions
  inside a when-played power), Wizard's Conclave (taking a banner, with a tie
  choice), Bandit Chief (which color to kill), A Round of Ale (reusing the Rest
  return steps), Search Party (one of 3 peeked cards, then Oracle-style
  placement), Call for Help (reveal, then discard from a regional pile).
- **Modifiers, restrictions and triggers:** Vow of Poverty (restricting Trade
  for favor, plus Vow of Obedience's Rest), Vow of Beastkin (a Muster source
  restriction), Assassin (Silver Tongue's limit plus Hunger), Defame (a
  decision and a burn after a Trade), Herald (an after-Campaign hook at the
  Campaign root), Diplomat (a `CampaignBeforeTargets` restriction with
  answers), Special Envoy (`EnterPhase(Rest)` inside a Travel tree), Way
  Station (an optional payment to the ruler, the inverse of Toll Roads),
  Moving Market and Mounted Library (a site move after a Trade; doubling the
  gain plus a discard), Forced Labor and Curfew (a Search or Trade restriction
  with a payment, the Toll Roads shape), Hunting Party (a nested Campaign after
  Search, the Knights Errant shape), Tyrant (a Travel transform with a choice
  of warband to kill).
- **Text that does not name its action** (needs a ruling first): News from
  Afar, Awaited Return, A Fast Steed, Scouts.

### Phase - Catalog batch - engine-blocked

Cards that need an engine change first, grouped by the missing mechanism.

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

Found by the survey of 2026-09-29:

- **A reroll operation:** Jinx, Master at Arms, Silver Charm, Unstable Summon
  (which also needs a variable X).
- **A Search source or draw other than the world deck or the actor's own
  regional discard** (a bottom draw, a single-card draw): Observatory, Errand
  Boy, Mushrooms, Spinning Bee, Painted Trumpet, Wine of Welcome, Land Warden,
  Cracked Horn, Ring of Devotion.
- **A choice of discard or placement destination:** Bracken, Baron, New
  Growth, Forest Warden, Vow of Wisdom.
- **A Muster or Trade source rule** (`MusterSource.resolve` accepts only
  token-free cards at the pawn's site): Map Library, Skilled Merchants, Old
  Songs, Pressgangs.
- **Acting as if elsewhere, or overriding a suit:** Small Friends, Friendly
  Familiar, Master of Disguise, Acting Troupe, Pledge to Discord, Spiteful
  Mirror, Yew Staff.
- **A nested Campaign or Travel, or an overridden actor site:** Wild Allies,
  Captains, Second Wind, Palanquin.
- **Raising or exempting the adviser limit** (`AdviserLimit` only lowers it):
  Pied Piper, Marriage, Family Wagon, Vow of Union, Vow of Wandering (which
  also blocks placing warbands), Lost Tapestry.
- **Plans that a power restricts or changes:** Beast Tamer, Specialist, Wild
  Mounts.
- **Overriding scoring or the victor:** Hearts and Minds, Careful Plans,
  Weeping Banner, Zealots.
- **A variable X or dice set to a face:** Ward of Silence, Arcane Armor,
  Mountain Giant.
- **New target kinds:** Relic Hunter, Glamor.
- **Restricting other cards' powers or placements:** Spell Breaker, Forest
  Council, City Wall, Giant Python, Ancient Bloodline.
- **New triggers** (a burn, a banner taken, a pawn move): Vow of Silence, Vow
  of Division, Secret Testament, Brass Army.
- **Challenge** (`legalBanners` is hard-coded, no ribbon adjustment): Sigil of
  the Eye, Sigil of the Heart, Favored Son.
- **Lowering the attacker's force after it is answered:** Bear Traps.
- **A Travel that can pause on a decision:** Hospitality.
- **Other:** Pilgrimage (shuffling the Dispossessed), Autumn Wind (a combined
  shuffle and deal), Signal Trees (a plan effect that depends on state it
  changes), Tracker (memory across turns), Keep (replacing a Raid's defeat),
  Obsidian Cage (a new warband store), Secret Signal (no generic gain window),
  and False Prophet, True Oath and The Red Seer (the Oathkeeper goal is not modelled).

### Phase - Card classes

Starts after **Phase - Catalog batch 3**, and needs its own design.
`docs/catalog/new-foundations-component-catalog.json` is hand-authored JSON,
read at runtime and validated against its schema. Move denizens, relics,
edifices, legacies, and sites into typed Scala classes, so suit, restrictions
and roles are compile-checked fields, and printed properties such as Locked
and the Grand Scepter become traits a card mixes in. This replaces the catalog
function the Global operation restrictions phase uses to build `Locked` and
`GrandScepter` restrictions, and the `forCatalog` lookup from a power to its
card. The catalog already indexes components by ID and by the powers they
print. Costs to weigh: 393 components, the `CatalogRef` version games are
pinned to, packaging and `--catalog-path`, `validate-component-catalog.py`,
and the architecture docs that call the JSON authoritative.

### Phase - Cleanup tasks

- [ ] **Enduring (Ancient City) waits for a Chronicle Phase.** Its cards are
  not discarded in the Chronicle Phase's Shape Empire step, which the engine
  does not have yet. The Pass's consent waits for the Consent system phase.

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
6. [ ] **A lighter way to test separate-machine play.** The manual
   [acceptance record](operations/alpha-acceptance.md) takes two physical
   machines and a lot of hand-recorded evidence, so `0.1.0-alpha.3` shipped
   without one. Find a cheaper check for what it covers (LAN reachability,
   seat links opening on another machine, reconnect and restart, HTTPS proxy
   and origin behavior, and the bundled archives' first launch on each OS),
   ideally something a release run can execute or that takes minutes by hand.
   Then decide what happens to the record.

### Setup deferred items

- [ ] **Simultaneous setup effects.** Simultaneous setup effects are resolved by
  the Chancellor or first player (site order is used until then). Player choices
  earlier in setup once foundations and legacies exist, such as the Chancellor
  choosing Recent or Forgotten sites. Later-game
  setup (Empire `world` sites, stored denizens and relics). WHEN EXPLORED
  triggers once an explore procedure exists. The Chronicle string codec is
  under **Phase - Empire and campaign continuity**.
- [ ] **Desecrated Ground.** E08's ruined face lets an Exile at its
  site keep any number of revealed Visions, which turns
  `PlayerState.revealedVision` into a collection across card play, projection,
  Vision victory, the frontend and the save format. E08 never appears in
  generated games, since the Nomad homeland becomes E06. Do it when E08 can
  appear. Recorded in the rule gaps design of 2026-09-26.

### Game Log deferred items

- [ ] **show a parked action's progress in the waiting message.**
  The Game Log posts a line only once its facts are complete, so an action
  that is still parked says nothing about its choices so far. The table's
  "Waiting on player" message is the place to show that progress. Recorded in
  the Game Log design of 2026-09-26.
- [ ] **Game Log features the design left out.** Click-to-highlight,
  filters and search, timestamps, a server-side read marker, failed victory
  checks and negotiation proposals. Replay navigation is **L4**. Recorded in
  the Game Log design of 2026-09-26.

- [ ] **Explain restricted options where they are offered.** A
  restricted option, or a missing action control, carries a hover note (or
  similar) saying which power restricts it and why. Most Restrictions would
  then need no Game Log line. The hide hook's note from the Power log lines
  phase is the natural source. Recorded in the
  [Power log lines design](superpowers/specs/2026-09-26-power-log-lines-design.md).
- [ ] **Warn before a choice that has no effect.** The rules let a
  player pay for a power or pick an option that then does nothing: a
  modifier selected for an action it will not change (Cup of Plenty,
  Animal Playmates or Birdsong with a card of the wrong suit), a power whose
  target is empty, or a bank with no stock. The game should warn the player
  before they commit, without refusing the choice. Recorded while designing
  Catalog batch 2.

### Powers-related deferred items

- [ ] **walker-native card play through card slots.** Card play still
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
- [ ] **plan-restricting powers, Bag of Siegeworks and Empire defenders.**
  Peace Envoy and other powers that restrict which plans a side may choose have
  no contribution to hook on yet. Bag of Siegeworks has a reviewed-catalog entry
  in `CampaignPowers` and no plan. Empire defenders are not modelled. The reviewed
  catalog's entries for Outriders, Brass Army and Watchdog are inert since slice
  3b and go with the reviewed catalog.
- [ ] **walker follow-ups.** Make the Recover roll automatic like the
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
- [ ] **the Grand Council and Festival banner faces.** They are listed
  as synthetic ids in the reviewed catalog and have no behaviour.
- [ ] **Mercenaries' player-chosen sign.** Mercenaries adds attack dice
  when its user attacks and removes defense dice from the attacker when it
  defends. The card lets the player choose the sign, and the plan fixes it by side.
  Choosing it needs a decision inside the plan and a preview that shows both.
- [ ] **record a bandit's applied battle plan as an event.** A player's
  plan is a recorded answer that later windows read. A bandit defender applies its
  cost-free plans without asking, so `CampaignPlanApplication` records the use as
  a `ModifyDicePool` marker under `campaign.plan-applied.<kind>.<id>`. The marker
  shows in the journal as a dice-pool change and suites that count a Campaign's
  `ModifyDicePool` operations see it. A dedicated recorded operation would say
  what happened.
- [ ] **a public view of a revealed temporary hand.** The Truthful Harp
  reveals the cards it draws by recording a `Peek` for every other player. No
  operation reveals a card in a temporary hand and the hand is projected to its
  owner only, so no view shows the reveal to the other players yet.
- [ ] **a board slot for distributions and Sticky Fire without a choice.**
  Warning Signals names the defender's board by a player option in its distribution, which
  the panel shows as a player name, and Sticky Fire asks its question even when a yes
  changes nothing (against bandits it only costs the favor). A board option, and skipping a
  question whose answers are the same, need a small change to the option vocabulary.

### Engine deferred items

- [ ] **one seam for "who may veto an operation".** `OperationPolicy`
  (an exact-shape allowlist, used by `StateBasedOperationPolicy` and
  `MinorActionOperationPolicy` on the legacy-event paths) and
  `OperationRestriction` (contextual reasons from powers) answer the same
  question through two interfaces. Migrating the two policies onto
  `OperationRestriction` would leave one seam, but it reorders precedence on
  the legacy-event path (allowlist reasons come before shape reasons today)
  and needs its own preservation argument. Recorded during the operation
  family consolidation of 2026-09-24.
- [ ] **key `CardKnowledge` by card id.** Knowledge is stored per
  place: `siteRelics` by viewer and site, `heldRelics` and `advisers` by
  viewer, and an owner's knowledge is implicit in `identifiesCard`. The Game
  Log phase patches the gaps this leaves (an ex-owner and a site peeker both
  lose a card's name when it moves) by copying knowledge as cards move. A
  single map from card id to the players who know it would make "knowledge
  follows the card" structural instead of maintained per move. Recorded in
  the Game Log design of 2026-09-26.
- [ ] **retire the reviewed-catalog machinery.**
  `ReviewedPowerInspector` and `PowerRuntime` are still used by 12 main files,
  and `IndexedRuleSource.handlerIds`, a temporary compatibility projection, is
  used only by tests. Move the remaining users onto the executable catalog and
  delete the reviewed catalog.
- [ ] **decide whether to keep `DeltaMeaning`.** Nothing reads it.
  The retention decision of 2026-09-24 said to revisit it once the action
  history was designed, and the Game Log does not use it.
- [ ] **frontend and protocol clean-up.** Decision option kinds are
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
- [ ] **Arranged-start event for game save and load.** A journal must begin
  with `GameStarted`, and no codec exists for a full `ReadyGame`. A journaled
  event that carries a whole arranged position (players, sites, decks, banks,
  turn, walker state) would let a stream begin mid-game: loading a saved game,
  scenarios, tutorials, and bug reproductions from a pasted position. It needs
  a `ReadyGame` wire codec, a game-log line, projection handling, and a
  decision on who may start one. The test-only replay origin from the service
  and log start-state project (2026-09-28) is the seam it would grow from.
  Recorded in that project's design.
- [ ] **L5 — Asynchronous accounts, invitations, and notifications**
- [ ] **L6 — Incremental implementation of remaining phases and rules** (tracked
  in the phased sequence above; the next denizens and relics are
  **Phase - Catalog batch 3**)
- [ ] **L7 — Incremental synchronization transport**
  - Replace complete-snapshot polling with conditional responses, projection
    deltas, long polling, SSE, or another push transport when scale or latency
    justifies the added server lifecycle complexity.
  - A cheaper first step keeps redaction intact: a change-check that returns
    the viewer's current sequence (or a 304), fetching the projection only
    when it advanced. See the
    [Phase 5 follow-ups](operations/phase-5-follow-ups.md#snapshot-polling-sends-a-full-projection-on-every-tick).
- [ ] **Table UI rework.** Rework the layout of player areas and sites, and
  port the map to haunt-roll-fail's canvas approach. Recorded in the card
  shape and inspection design.
- [ ] **Clicking a banner shows its details.** Dark Revolution and The
  People's Favor are clickable like other cards, opening their details.
- [ ] **Match the map's aspect ratio to the physical board.** The rendered
  map's proportions differ from the actual game map's.
- [ ] **Undo system.**
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
