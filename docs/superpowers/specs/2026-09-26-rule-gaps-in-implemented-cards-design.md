# Rule Gaps in Implemented Cards — Design

**Status:** implemented (plan
`docs/superpowers/plans/2026-09-26-rule-gaps-in-implemented-cards.md`)
**Date:** 2026-09-26
**Supersedes nothing.** Closes the deferrals recorded in
`2026-09-19-visions-conspiracy-walker-design.md` (Vision-play cards) and
`2026-09-19-campaign-walker-design.md` (Vow of Peace's second sentence).

## Goal

The walker migrations dropped parts of the printed text of cards that can
already appear in play. This phase restores them:

- Vow of Obedience, Secret Police and Sacred Ground forbid playing a Vision
  faceup.
- Book Binders rewards its holder when another player plays a Vision faceup.
- Vow of Obedience's REST power.
- Vow of Peace's second sentence: attackers cannot sacrifice against its
  holder.

To do this without rejecting a choice after the player has made it, the walker
learns to hide any option that would break a `Restriction`. That makes
`Restriction` the one tool a future "cannot" card needs.

## Scope

In scope:

- Restriction look-ahead in the walker (section 1).
- Moving the ruler helpers out of Travel (section 2).
- The five card powers listed above (sections 3 to 5).

Out of scope, with where each goes:

- **Off-turn modifier selection.** No implemented card needs it. Every
  defender-side card works through the battle-plan `Offer` path, and every
  `SelectedModifier` changes its own player's action. Sneak Attack (#98) is
  the first card that needs it, together with an acting player that differs
  from the active player. Both move to Catalog batch 2's engine-work list.
- **Desecrated Ground** (E08 ruined). It lets an Exile keep any number of
  revealed Visions, which turns `PlayerState.revealedVision` into a
  collection. That touches `CardPlay.planVision`, the projection DTOs,
  `VisionVictoryEligibility`, the frontend and the save format. E08 never
  appears in generated games, since the Nomad homeland becomes E06. It becomes
  its own roadmap item.
- **Consent.** "Enemies cannot" rules are always enforced, as Toll Roads is.
  The Consent phase adds consent through the ignore hooks.
- **Empire rulers.** The all-Exile game has none. The Empire phase decides how
  an Empire-ruled Secret Police site binds players.
- **Log lines for powers.** A power that removes a decision or hides an option
  leaves no line in the Game Log. The next phase, Power log lines, gives
  powers a generic way to declare them.

## Rulings

- **Conspiracy is a Vision.** The rules reference calls it "not a victory
  Vision". Sacred Ground names it as an exception, which implies the others
  include it. So Vow of Obedience, Secret Police and Book Binders apply to the
  Conspiracy. Only Sacred Ground exempts it.
- **Secret Police's ruler.** A site is unruled only before setup places
  warbands and during the Chronicle, so the Police site always has a ruler in
  play. When a player rules it, it binds every other player whose pawn is at a
  site that player rules. When the Bandits rule it, it binds every player whose
  pawn is at any Bandit-ruled site, the Police site included.
- **Sacred Ground** binds every player whose pawn is not at its site,
  including that site's ruler.
- **Book Binders** says "another player", not "enemy", so any other player's
  faceup Vision triggers it. Keeping a Vision faceup from a Search counts as
  playing it. When the chosen bank holds fewer than two favor, the holder takes
  what is there.
- **Vow of Peace** binds only while its holder has it faceup, as its first
  sentence does. In a Conquest the defender is the ruler of the targets.

## 1. Restriction look-ahead

### Problem

`restrictionViolations(tree, powers, state, activePlayer, answered)` walks the
tree as the answers so far shape it. A `Restriction` hooked on
`ActionCardPlayedFaceup` only sees that window once the player has answered
"faceup", because only then does the card-play `Branch` select the
`CardPlayedFaceup` hook. The player sees the choice, makes it, and is refused.
The Vision-play design says the game offers only what is legal, so this is
wrong. Adding a flag for each rule to the procedure (the Silver Tongue
approach through `PlacementRules`) would work, but every future "cannot" card
would need its own flag.

### Design

Before the walker offers a decision, it tests each option against the
restrictions:

1. **Probe.** For each option of a `Decide`, the walker appends a hypothetical
   `Answered` for that option to the answers so far. It then runs
   `restrictionViolations` over the tree those answers derive. An option that
   yields a violation the answers so far do not already yield is not offered.
   Comparing against that baseline keeps a violation that no option causes
   (one a state change earlier in the same command brought about) from
   emptying every decision after it; the answer-time check still reports
   such a violation.
2. **Query kinds.**
   - `ChooseOne` and `ChooseMany` probe each option on its own.
   - `ChooseAmount` probes each value from `min` to `max` and narrows the
     range to the permitted values. When the permitted values are not one
     contiguous range, the range is left unchanged and the answer-time check
     decides.
   - Other query kinds are not probed.
3. **One place.** The probe applies to every `Decide`, windowed or not, so the
   card-play placement `Decide` needs no new window. It runs in one function
   that both the live walk (when it parks) and `leafAt` / `parkedDecide` call.
   So the walk, `accepts`, the projector and `WalkerSimulation` all see the
   same options. This is the same guarantee `OptionRestriction` gives.
4. **Emptied decisions.** A required decision whose every option is removed is
   reported by the existing emptied-decision violation. At an action's start
   that hides the start control through `WalkerSimulation.starts`, as it does
   today.
5. **One level deep.** The walk inside a probe does not probe its own
   decisions. A `probing` flag on `WalkerPowers`, which already travels
   everywhere the probe's traversal goes, turns probing off, so probing never
   recurses and its cost stays linear in the number of options. The probe
   runs only when some power contributes a `Restriction`. A restriction that only a combination of later answers breaks
   still rejects at answer time, as it does today, as a backstop.
6. **Answers reach restrictions.** `restrictionViolations`' `ctxFor` passes
   `answered` into `PowerCtx`. Today it drops them, so a `Restriction` cannot
   read what was chosen.
7. **Purity.** The probe is a pure function of state, answers and powers,
   which the walker's fold already requires (a park position must address the
   same node on resume).

`OptionRestriction` stays. It is the cheaper tool when a card names a specific
option of a windowed decision.

### Tests

In the walker suites, using test-only powers:

- A `Restriction` whose window appears only after an answer removes that
  option from the parked `Decide`, from `accepts` and from
  `WalkerSimulation.previewParked`.
- A submitted forbidden answer is still rejected.
- `ChooseMany` and `ChooseAmount` narrowing, including the non-contiguous case
  that leaves the range unchanged.
- A required decision emptied by the probe makes `starts` false.
- The probe does not recurse, pinned by counting the traversals for one
  decision.
- A `Restriction` reads `ctx.answered`.

## 2. Ruler helpers move out of Travel

`TravelRulers` (in `powers/travel/TravelPayments.scala`) has nothing to do
with Travel, and Secret Police needs it. As a separate commit that changes no
behaviour:

1. Rename it to `SiteRulers` and move it to `gameplay/SiteRulers.scala`,
   beside `PowerAccess`. It keeps `rulerOf`, `rulerOfCard` and `siteOf`.
2. Delete `isEnemy`. Callers use
   `SiteRule.enemies(ruler, SiteRuler.Player(player))` from
   `model/World.scala`, so the model holds the only definition of an enemy. The
   two agree except when the Empire rules. `rulerOfCard` already drops an
   Empire ruler, so no caller reaches that case.

The Toll Roads and Grasping Vines suites and `WorldModelSuite` cover the
move.

## 3. Vision-play restrictions

Each card is a `Restriction` at `ActionCardPlayedFaceup` that matches
`CardPlayedFaceup(card: VisionId, _)`. With the look-ahead, the faceup
placement disappears from the placement decision instead of being refused.
"The player" is the one playing the Vision, `ctx.activePlayer`. The Sneak
Attack work may later replace that with an acting player.

Secret Police and Sacred Ground bind players who have no access to the card,
so they find the card on the map, as Toll Roads and the Fortress do, rather
than through `PowerAccess`.

A restriction binds only while the Vision is still where the play started: in
the player's temporary hand or among their facedown advisers. Once the card
has moved, the hook describes a play already made, and a later command's
restriction check must not refuse it again because the holder's state has
changed since.

| Card | Forbids a faceup Vision when | Conspiracy |
|---|---|---|
| Vow of Obedience (#121) | the player holds it as a faceup adviser | forbidden |
| Secret Police (#113) | it is faceup at site S in play, with ruler R = `SiteRulers.rulerOfCard`; the player's pawn is at a site ruled by R; and `SiteRule.enemies(R, Player(player))` | forbidden |
| Sacred Ground (E08 intact) | E08's intact face is at a site in play and the player's pawn is not at that site | allowed |

An Empire-ruled Secret Police site binds no one, because `rulerOfCard`
returns nothing for it.

**Vow of Obedience's REST** ("Take [favor] from any one favor bank") becomes a
`PhasePower`, following Silver Tongue's REST. The holder chooses among banks
that hold favor, a single stocked bank is chosen automatically, and the power
is not offered when every bank is empty. It replaces the reviewed stub
`RestPowers.VowOfObedience`. The catalog marks the power `persistent: false`
because of its REST. It is registered the way Silver Tongue is, since that
card also combines a rule with a REST: in both the walker and the phase power
catalogs, with the default automatic resolution rather than one read from the
catalog flag. The three rules and Book Binders are registered together in
`CardPlayTriggers`.

The stale fail-closed description in
`docs/architecture/bounded-visions-and-conspiracy.md` is rewritten to describe
these restrictions.

### Tests

One suite per card under `gameplay/powers/cardplay/`, laid out like
`GossipSuite`:

- The faceup placement is not offered, and a submitted faceup answer is
  rejected.
- Discard and facedown placement are still offered.
- Nothing is forbidden when the condition fails: a facedown Vow, a pawn at a
  site with a different ruler, the Police site's own ruler, a pawn at Sacred
  Ground, a ruined E08.
- Conspiracy: forbidden by Vow of Obedience and Secret Police, allowed by
  Sacred Ground.
- Both entry points: a Search, and playing a facedown adviser.
- Bandit rule: Secret Police at a Bandit-ruled site binds a player at another
  Bandit-ruled site.
- Vow of Obedience's REST: a choice of stocked banks, one stocked bank, all
  banks empty.
- Registration and status: `PowerImplementationStatusSuite`,
  `ImplementedCardCatalogSuite` and `PowerKindsCatalogSuite`.

E08 never appears in generated games, so its tests place it with
`CardStaging`.

## 4. Book Binders

Book Binders (#140) follows Gossip (`powers/cardplay/Gossip.scala`): a
`Transform` on the `ActionCardPlayedFaceup` hook.

- It applies when the played card is a `VisionId`, the Conspiracy included,
  and a player other than the one playing it holds Book Binders as a faceup
  adviser.
- It appends a `Decide` owned by that holder, off turn, over the favor banks
  that hold favor, as League Treaty does
  (`rest/LeagueTreatyContribution.scala`). A single stocked bank is chosen
  without asking. When every bank is empty, nothing is appended.
- The holder takes `min(2, bank)` favor from the chosen bank.

Both entry points run the same hook, so both trigger it.

### Tests

- Another player's faceup Vision gives the holder an off-turn choice, then
  two favor.
- The holder's own faceup Vision does nothing.
- A facedown Book Binders does nothing.
- A bank with one favor gives one.
- The Conspiracy triggers it.
- A single stocked bank is taken without a decision.

## 5. Vow of Peace, second sentence

`VowOfPeaceContribution` gains a `Transform` at `CampaignSacrificeSelection`
beside its existing `Restriction` at `CampaignActionEligibility`.

- It finds the defender with `CampaignSetup.setup`, as `PlanContext.of` does.
- When the defender is `CampaignDefender.Player(id)` and `id` holds Vow of
  Peace faceup, it returns `Vector.empty`. The sacrifice decision disappears
  and `CampaignSetup.sacrificed` reads zero.
- It does nothing otherwise.

A `Restriction` on the amount would also work with the look-ahead, but it
would leave a decision whose only answer is zero. Removing the decision is
clearer.

### Tests

- An attacker against a faceup holder is not asked to sacrifice, and the
  battle uses zero.
- An attacker against anyone else is still asked.
- A facedown Vow does nothing.
- In a Conquest, the targets' ruler is the defender.
- The defender's own sacrifice, such as Wrestlers, is unaffected.

## Order of work

1. `SiteRulers` move (section 2).
2. Restriction look-ahead and the `answered` fix (section 1).
3. Vow of Obedience, Secret Police, Sacred Ground and Vow of Obedience's REST
   (section 3), with the architecture doc fix.
4. Book Binders (section 4).
5. Vow of Peace (section 5).
6. Traceability rows and the roadmap entry updated.

## Verification

- `./sbtw test` and `./sbtw "frontend/test"`.
- `python3 scripts/check-markdown-links.py`.
- A browser smoke check on a scratch database: a Search reaches its
  placement decision and a play completes. A live game cannot be arranged to
  deal Vow of Obedience and a Vision together, so the hidden faceup option is
  proven by the suites.
