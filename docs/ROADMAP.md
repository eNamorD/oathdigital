# Oath Digital Roadmap

This is the forward-looking project-level source of truth for planned work.
When a task is done, it should be deleted from this file.

Refrain from labeling phases with numbers, as the roadmap items may shift
in priority. The Later items keep their X and L labels for consistency with
specs.

## Now

**Phase - Cleanup tasks** holds what the finished Catalog batch 3 and
Card classes phases left behind, bugs and small UI fixes from the
2026-10-01 playtest. **Phase - Catalog batch 4** is in progress: slices
1a and 1b are done.

## Next

Work toward a playable all-Exile alpha before expanding into the Chronicle,
campaign-continuity and Empire rules.

### Phase - Cleanup tasks

From the playtest of 2026-10-01:

- [ ] **Find out why an off-LAN player's new connections time out.** An
  incognito window failed for the host and for an off-LAN player, while
  their normal windows worked. On the host, private mode is not the cause: a
  normal-window reload with the cache disabled fails too, because the router
  does not reliably loop back to its public address (quick start, step 6), and
  open tabs kept working only by reusing their connection. An off-LAN player
  does not loop back. Have them reload with the cache disabled and open
  `/health/ready` in a private window, then check the port forward and the
  host firewall.
- [ ] **Review the action buttons.** They feel unresponsive, and some appear
  for things the player does not expect.

Left behind by earlier phases:

- [ ] **Shrink cached catalog fields.** Card classes made powers read
  their cards from card objects, yet 99 classes still take a
  `catalog: ExecutableCatalog` constructor parameter (power classes, the
  projectors, the Game Log and the Campaign plan files), and 88
  `forCatalog(catalog)` constructors thread it, many only to pass it on.
  Remove each parameter and constructor as its power is touched.
- [ ] **Migrate `CatalogNames` to card objects.** 264 test files look
  cards up by name through `CatalogNames` (the test readability sweep of
  2026-10-02 moved numeric ids onto it). Move them to direct object
  references such as `AlchemistCard`.

### Phase - Board decision follow-ups

- Move Travel onto board decisions; it still picks its destination through
  the older board targets.
- Add reasons to the remaining option builders as playtests find options
  that people wonder about.

### Phase - Campaign battle summary

Left out of the Game Log action sections
([spec](superpowers/specs/2026-10-05-log-action-sections-design.md)), which
answered the 2026-10-01 playtest's "make it clear what a Campaign's battle
did":

- [ ] A summary of a Campaign's battle, opened by clicking one of its log
  lines: attack and defense dice, battle plans, sacrificed warbands, the
  totals and the winner. It is not always visible.
- [ ] The same summary filling in while a Campaign runs: the dice on each
  side as battle plans are chosen, and the sacrificed warbands.

### Phase - Catalog batch 4

Implement the 47 cards that the verification of 2026-10-05 found buildable
with no engine change. The
[design](superpowers/specs/2026-10-05-catalog-batch-4-design.md) and the
[per-card rulings](superpowers/specs/2026-10-05-catalog-batch-4-rulings.md)
hold the slices, the shared scorers and the restriction-icon rule.

- [ ] 2. Restriction icons and card moves: the restriction-icon rule, Roving
  Terror, Homesteaders, Resettle, Moving Market.
- [ ] 3. ACTION powers: Terror Spells, Witch's Bargain, Inquisitor, Relic
  Thief, Ancient Binding, Convoys, Singing Mask, Assassin.
- [ ] 4. When played: Revelation, Blackmail, Dissent, Wizard's Conclave,
  Bandit Chief, A Round of Ale, Call for Help.
- [ ] 5. Modifiers and restrictions: Vow of Poverty, Vow of Beastkin, Defame,
  Mounted Library, Curfew, Awaited Return, Special Envoy, Tyrant, A Fast
  Steed, Forced Labor, News from Afar.

### Phase - Follow-up actions

An action queued to run after the current one, as its own walker, with its
costs waived. A queued action is a full action: the player selects modifiers
for it, and it gets its own Game Log lines and roll feedback. Today Knights
Errant nests a Campaign inside Muster instead, and that Campaign posts no
Game Log lines.

- Hunting Party: a Campaign after a Search of the world deck, spending no
  Supply.
- Search Party: a card played after its WHEN PLAYED, as if searched. Nested
  inside the WHEN PLAYED, the played card fires neither its own WHEN PLAYED
  nor the card-play triggers (`WalkerHooks.gathered` is keyed by window), and
  Search re-reads its live hand on a single-card draw.
- Migrate Knights Errant onto it, and unblock Second Wind (a Travel, then a
  Campaign).
- Recorded in the
  [catalog batch 4 rulings](superpowers/specs/2026-10-05-catalog-batch-4-rulings.md#moved-out-of-the-batch).

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
  the Eye, Sigil of the Heart, Favored Son, Magician's Code (its 2 secrets
  count toward placing more than the banner holds, but the start gate runs
  before any modifier).
- **Lowering the attacker's force after it is answered:** Bear Traps.
- **A Travel that can pause on a decision:** Hospitality, Way Station (the
  non-ruler's choice to pay should appear only when Way Station is
  selected).
- **Other:** Pilgrimage (shuffling the Dispossessed), Autumn Wind (a combined
  shuffle and deal), Signal Trees (a plan effect that depends on state it
  changes), Tracker (memory across turns), Keep (replacing a Raid's defeat),
  Obsidian Cage (a new warband store), Secret Signal (no generic gain window),
  and False Prophet, True Oath and The Red Seer (the Oathkeeper goal is not modelled).

### Phase - Consent system

A way for players to request and give consent, including per-player
permissions that approve it automatically. It interacts with certain
Restrictions, probably through their "ignore" features. Narrow Pass and later
cards need it, and it is a system of its own, separate from Negotiation.

### Phase - All-Exile alpha readiness

`0.1.0-alpha.2` and `0.1.0-alpha.3` shipped as GitHub prereleases through the
[alpha release workflow](operations/releases.md), each with bundled archives
for macOS arm64, Windows x64 and Linux x64. The last manual acceptance run is
[recorded](testing/alpha-acceptance-0.1.0-alpha.2.md); `0.1.0-alpha.3` shipped
without one. Deferred findings and requested UI changes are in the
[Phase 5 follow-ups](operations/phase-5-follow-ups.md).

- [ ] **Unanswered off-turn decisions.** A parked decision blocks every
  command until its owner answers. There are no timeouts, forfeits or host
  resolution. Recorded in the walker ownership and phases design.
- [ ] **Reconnection UX.** After a client loses its connection it should
  retry with exponential backoff and show a **Try Again** button. Recorded
  in the Phase 5 follow-ups.
- [ ] **Alpha deferred defects.** Items 7, 12, 13, 16 and 18 of the
  [follow-ups](operations/phase-5-follow-ups.md#deferred-defects): bracketed
  `[::1]` rejected as loopback, the frontend linker on the test classpath,
  the test `index.html` fixture shadowing the generated one, the hardcoded
  `oathdigital:root` assertion, and no Docker `HEALTHCHECK`. Also document
  host bind mounts at `/var/lib/oathdigital` in the configuration guide.
- [ ] **A lighter way to test separate-machine play.** The manual
  [acceptance record](operations/alpha-acceptance.md) takes two physical
  machines and a lot of hand-recorded evidence, so `0.1.0-alpha.3` shipped
  without one. Find a cheaper check for what it covers (LAN reachability,
  seat links opening on another machine, reconnect and restart, HTTPS proxy
  and origin behavior, and the bundled archives' first launch on each OS),
  ideally something a release run can execute or that takes minutes by hand.
  Then decide what happens to the record.

### Phase - Negotiation terms and promises

A Negotiation deal holds favor and relic transfers and disclosures only. Add
promises about future actions, transfers of secrets, advisers, sites and
banners, remote and private Negotiation, a registry of term kinds, and
eligibility powers. Record ignored Negotiation rules per participant rather
than for the actor only. Citizenship offers belong to the Empire phase.

### Phase - Chronicle steps

After the all-Exile alpha, implement the Chronicle and persistent campaign:
generalized later-game setup, Atlas transitions, Chronicle tasks, world
reconstruction, Reliquary changes, Foundation mutation, Legacy
activation/scoring, Oathkeeper goal changes, era scoring, saved-campaign
continuation, and campaign browsing.

- [ ] **End-of-game Chronicle steps.**
- [ ] **Enduring (Ancient City).** Its cards are not discarded in the
  Chronicle Phase's Shape Empire step, which the engine does not have yet.
- [ ] **Chronicle import and export.** The Chronicle string codec for the TTS
  format, including the sections that format has not defined yet.
- [ ] **Foundations.**
- [ ] **Legacies.**
- [ ] **Eras.**

### Phase - Empire

After the Chronicle steps phase, implement Chancellor/Citizen roles, Imperial
forces, Grand Scepter and Reliquary behavior, Citizenship through Negotiation,
Successor goals, forced and self-exile, Imperial setup/turn/end rules, and the
remaining production authentication work needed for broader hosting.

### Phase - Gameplay completeness gate

Audit every rulebook procedure and every runtime component handler against the
traceability matrix; close remaining hidden-information, simultaneous-ordering,
randomness, consent, and nested-action gaps; then run complete replay,
persistence, server, Scala.js, packaged-network, and browser acceptance gates.

## Deferred items

Unscheduled work, grouped by area. Pull an item into a phase when it blocks
one.

### Setup deferred items

- [ ] **Simultaneous setup effects.** The Chancellor or first player should
  resolve simultaneous setup effects; site order is used until then. Still to
  add: player choices earlier in setup once foundations and legacies exist,
  such as the Chancellor choosing Recent or Forgotten sites; later-game setup
  (Empire `world` sites, stored denizens and relics); and WHEN EXPLORED
  triggers once an explore procedure exists. The Chronicle string codec is
  under **Phase - Chronicle steps**.
- [ ] **Desecrated Ground.** E08's ruined face lets an Exile at its
  site keep any number of revealed Visions, which turns
  `PlayerState.revealedVision` into a collection across card play, projection,
  Vision victory, the frontend and the save format. E08 never appears in
  generated games, since the Nomad homeland becomes E06. Do it when E08 can
  appear. Recorded in the rule gaps design of 2026-09-26.

### Game Log deferred items

- [ ] **Show a parked action's progress in the waiting message.**
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

- [ ] **Town Meeting's ruling.** Town Meeting splits its favor across the
  banks, as Alchemist does, but its text may mean one bank for all, as
  Fabled Feast's does. The
  [catalog batch 3 rulings](superpowers/specs/2026-09-29-catalog-batch-3-rulings.md)
  leave it unresolved. A product-owner answer settles it; one bank would
  reuse Fabled Feast's question.
- [ ] **Limit cards that count or ignore advisers.** `CardPlayRules` holds
  one adviser limit that only lowers, plus the played card's own limit, which
  fits Silver Tongue, Insomnia and Assassin. Family Wagon (removes the limit
  for Nomad advisers), Pied Piper (ignores the limit), Marriage (counts as
  one adviser) and Lost Tapestry (facedown Visions do not count) change what
  counts toward the limit, so they need a counting rule that
  `CardSlots.adviserSlots` and `AdviserLimit` share.
- [ ] **Plan-restricting powers, Bag of Siegeworks and Empire defenders.**
  Peace Envoy and other powers that restrict which plans a side may choose have
  no contribution to hook on yet. Bag of Siegeworks has a reviewed-catalog entry
  in `CampaignPowers` and no plan. Empire defenders are not modelled. The reviewed
  catalog's entries for Outriders, Brass Army and Watchdog are inert since slice
  3b and go with the reviewed catalog.
- [ ] **Walker follow-ups.** Make the Recover roll automatic like the
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
- [ ] **The Grand Council and Festival banner faces.** They are listed
  as synthetic ids in the reviewed catalog and have no behaviour.
- [ ] **Mercenaries' player-chosen sign.** Mercenaries adds attack dice
  when its user attacks and removes defense dice from the attacker when it
  defends. The card lets the player choose the sign, and the plan fixes it by side.
  Choosing it needs a decision inside the plan and a preview that shows both.
- [ ] **Record a bandit's applied battle plan as an event.** A player's
  plan is a recorded answer that later windows read. A bandit defender applies its
  cost-free plans without asking, so `CampaignPlanApplication` records the use as
  a `ModifyDicePool` marker under `campaign.plan-applied.<kind>.<id>`. The marker
  shows in the journal as a dice-pool change and suites that count a Campaign's
  `ModifyDicePool` operations see it. A dedicated recorded operation would say
  what happened.
- [ ] **A public view of a revealed temporary hand.** The Truthful Harp
  reveals the cards it draws by recording a `Peek` for every other player. No
  operation reveals a card in a temporary hand and the hand is projected to its
  owner only, so no view shows the reveal to the other players yet.
- [ ] **A board slot for distributions and Sticky Fire without a choice.**
  Warning Signals names the defender's board by a player option in its distribution, which
  the panel shows as a player name, and Sticky Fire asks its question even when a yes
  changes nothing (against bandits it only costs the favor). A board option, and skipping a
  question whose answers are the same, need a small change to the option vocabulary.

### Engine deferred items

- [ ] **One seam for "who may veto an operation".** `OperationPolicy`
  (an exact-shape allowlist, used by `StateBasedOperationPolicy` and
  `MinorActionOperationPolicy` on the legacy-event paths) and
  `OperationRestriction` (contextual reasons from powers) answer the same
  question through two interfaces. Migrating the two policies onto
  `OperationRestriction` would leave one seam, but it reorders precedence on
  the legacy-event path (allowlist reasons come before shape reasons today)
  and needs its own preservation argument. Recorded during the operation
  family consolidation of 2026-09-24.
- [ ] **Key `CardKnowledge` by card id.** Knowledge is stored per
  place: `siteRelics` by viewer and site, `heldRelics` and `advisers` by
  viewer, and an owner's knowledge is implicit in `identifiesCard`. The Game
  Log phase patches the gaps this leaves (an ex-owner and a site peeker both
  lose a card's name when it moves) by copying knowledge as cards move. A
  single map from card id to the players who know it would make "knowledge
  follows the card" structural instead of maintained per move. Recorded in
  the Game Log design of 2026-09-26.
- [ ] **Retire the reviewed-catalog machinery.**
  `ReviewedPowerInspector` and `PowerRuntime` are still used by 8 main files,
  the six stub objects (`ActionPowers`, `MusterPowers`, `RestPowers`,
  `NegotiationPowers`, `CampaignPowers`, `RecoverPowers`) remain,
  and `IndexedRuleSource.handlerIds`, a temporary compatibility projection, is
  used only by tests. Move the remaining users onto the executable catalog and
  delete the reviewed catalog.
- [ ] **Decide whether to keep `DeltaMeaning`.** Nothing reads it.
  The retention decision of 2026-09-24 said to revisit it once the action
  history was designed, and the Game Log does not use it.
- [ ] **Frontend and protocol clean-up.** Decision option kinds are
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
  in the phased sequence above; the next denizens and relics come
  from **Phase - Catalog batch 4** and **Phase - Catalog batch - engine-blocked**)
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
  shape and inspection design. The playtest of 2026-10-01 asks whether the
  whole table, not only the map, should move to an HTML5 canvas.
- [ ] **Clicking a banner shows its details.** Dark Revolution and The
  People's Favor are clickable like other cards, opening their details.
- [ ] **Match the map's aspect ratio to the physical board.** The rendered
  map's proportions differ from the actual game map's.
- [ ] **Undo system.** A player can take back a step before it commits
  anything others have seen. No intent exists today to leave a walker once it
  has started.
  - [ ] Cancel a started action before any state change: a misclicked Minor
    Actions button should not commit the player to playing a facedown
    adviser. Card play spec 2 (board decisions) keeps the facedown-adviser
    pick before the walker starts, outside the board decision machinery, so
    that it keeps its Cancel; with undo, that pick can move into the walker
    as an ordinary board decision.
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
  Scala.js linker, the card catalog suite, the architecture and documentation-link
  checks, and diff validation.
- Before the first public release, event formats and fixtures may change in
  place; backward compatibility, migrations, and version bumps are not required.
  Update the current writer, reader, replay tests, fixtures, and development
  data together. Public release establishes the compatibility baseline.
