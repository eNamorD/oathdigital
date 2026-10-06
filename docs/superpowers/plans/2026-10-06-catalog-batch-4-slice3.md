# Catalog Batch 4, Slice 3 (ACTION Powers) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement eight ACTION powers: Terror Spells (61), Witch's Bargain (77), Inquisitor (38), Relic Thief (95), Ancient Binding (23), Convoys (151), Singing Mask (R28) and Assassin (80), with Assassin's adviser limit.

**Architecture:**

- **Each card is a `PaidAction`** (an ACTION power whose cost the engine places on the card) in `gameplay/powers/action/`. Each card's object moves out of `catalog/holding/` into its power's file.
- **Assassin is also a `ContributingPower`**, like Insomnia: its "you can only have two advisers" is a `HolderAdviserLimit` at `SearchPlayAdviser`, registered in `WalkerPowerCatalog`, and `AdviserLimit.of` reads it.
- **Powers-side refactors**, each built in the task that first needs it, without changing the behavior or tests of the powers they come from:
  - R5 from the spec, `PowerAnswers.last`, reads a `Repeat` loop's latest answer (Witch's Bargain).
  - `SecretMoves.faceupFirst` is Sleight of Hand's flip, shared with Witch's Bargain.
  - `SecretBurns.keeping` is Book Burning's burn, shared with Ancient Binding.
  - `LostTongue.protects` exposes Lost Tongue's existing check, so Relic Thief can leave protected relics out before its roll.
- **Registration:**
  - Witch's Bargain, Inquisitor, Relic Thief and Assassin join `OtherActionPowers`.
  - Terror Spells, Ancient Binding, Convoys and Singing Mask join `WorldActionPowers`.
- **No engine change.** No new operation, window, query, option kind, `NoteArg` kind, protocol change or frontend change.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change.

**Spec:** Read both documents before starting:

- `docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md`: the sections "The bar", "Powers-side refactors", "Seams used for the first time", "Implementation notes from the verification", "Log lines", "Slice 3: ACTION powers", "Testing" and "Verify at plan time".
- `docs/superpowers/specs/2026-10-05-catalog-batch-4-rulings.md`: "Rules that apply to the whole batch" and the per-card rulings under "Slice 3: ACTION powers".

The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

## Global Constraints

- **Slice order.** Slices 1a, 1b and 2 are on `main` (slice 2 merged as b08d96bf). Check with `git log main --oneline | grep "record catalog batch 4 slice 2"`. Every card move in this slice runs under slice 2's restriction-icon rule.
- **Build rules.** `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails the build.
  - Each file below lists the imports it needs.
  - A refutable pattern in a `val` (such as `val Vector(a, b) = ...`) also fails, so bind each value separately. A case-class extractor of its own type (`val Chosen(t, done) = choose(...)`) and a tuple of a tuple type are irrefutable and are fine.
- **File size.** Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - No power name appears in walker sources.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Note templates.**
  - A note template starts with an argument or a capital letter.
  - Every phase power declares a key named exactly `used` (`NoteKey.Used`). `PowerNoteCatalogSuite`'s "every phase power but a forced one declares its own used line" checks this.
  - A phase power's own line is key `used` or `used.<variant>`; either replaces "Used {card}". Any other key, such as Witch's Bargain's `secret`, follows the generic "Used {card}" line.
- **Log lines, exactly** (spec, "Log lines", "Slice 3: ACTION powers", with the key changes of plan-time ruling 1). The acting player is not named, except as `{Blue}` where the line is about one of their own cards.

  | Card | Key | Line | When |
  |---|---|---|---|
  | Terror Spells | `used` | Terror Spells: Killed {n} warbands. | warbands died |
  | Terror Spells | `used.none` | Terror Spells: The {Darkest Secret} was not held. | without the Darkest Secret |
  | Terror Spells | `used.empty` | Terror Spells: No warband was in the region. | with it, and nothing to kill |
  | Witch's Bargain | `secret` | Witch's Bargain: Gave {1 secret} to {Blue} and took {n favor}. | each such exchange |
  | Witch's Bargain | `favor` | Witch's Bargain: Gave {2 favor} to {Blue} and took {n secrets}. | each such exchange |
  | Witch's Bargain | `used` | Witch's Bargain: No exchange was made. | no exchange, or nobody at the site |
  | Inquisitor | `used.<vision>.right` | Inquisitor: Guessed {name} and discarded {card} from {Blue}. | a right guess; covers the Discarded and Revealed lines |
  | Inquisitor | `used.<vision>.wrong` | Inquisitor: Guessed {name} for {Blue}'s Vision, wrongly. | a wrong guess |
  | Inquisitor | `used` | Inquisitor: No player at the site held a facedown Vision. | nothing to guess |
  | Relic Thief | `used` | Relic Thief: Rolled no shield and took {relic} from {Blue}. | taken |
  | Relic Thief | `used.kept` | Relic Thief: Rolled a shield; {Blue} kept {relic}. | kept |
  | Relic Thief | `used.none` | Relic Thief: No faceup relic could be taken. | nothing to choose |
  | Ancient Binding | `used` | Ancient Binding: Burned {n secrets}. | secrets burnt |
  | Ancient Binding | `used.none` | Ancient Binding: No secret was burned. | none |
  | Convoys | `used` | Convoys: Moved the {pile} onto the {pile}. | always, an empty pile included |
  | Singing Mask | `used` | Singing Mask: Moved {n favor} from {the Beast bank} to the {People's Favor}. | favor moved onto the banner |
  | Singing Mask | `used.returned` | Singing Mask: Moved {n favor} from the {People's Favor} to {the Beast bank}. | favor moved off it |
  | Singing Mask | `used.none` | Singing Mask: No favor moved. | nothing asked, or 0 chosen |
  | Assassin | `used` | Assassin: Discarded {card} from {Blue}'s advisers. | discarded; covers the Discarded line |
  | Assassin | `used.none` | Assassin: No faceup adviser could be discarded. | nothing to discard |

  `{name}` is literal text in each Vision's own key. Every decision whose answer a line names is declared narrated, so it writes no generic "Chose" line. No line for Assassin's adviser limit.
- **Batch rulings that every task applies** (rulings, "Rules that apply to the whole batch"):
  - **Card backs are public.** Offering a facedown Vision's slot discloses nothing.
  - **"Your site" and "your region"** mean the pawn's site and region.
  - **Restriction icons govern play, moves and swaps** (slice 2's `RestrictionIcons`). No card in this slice moves between advisers and sites.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare against them at the end. This plan adds 44 server tests and no frontend test.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

These settle what the spec and rulings leave open. Each one says what it costs if it is wrong.

1. **A plain `used` key.** `PowerNoteCatalogSuite` requires every phase power to declare a key named `used`. Three of the spec's key sets have none, so:
   - Witch's Bargain's "No exchange was made." is key `used`, not `used.none`. When exchanges happen, the generic "Used Witch's Bargain" line comes first and each exchange's line follows it.
   - Inquisitor's "No player at the site held a facedown Vision." is key `used`, not `used.none`.
   - Relic Thief's "Rolled no shield and took {relic} from {Blue}." is key `used`, not `used.took`.
   - Task 9 corrects the spec's table. Cost if wrong: renaming a key later orphans the notes of saved games, so decide before the slice merges.
2. **Generic lines** (the spec's "verify" items).
   - `DetailLines` writes a warband line only for a `Move` to a site, so a `Kill` writes none: Terror Spells covers nothing.
   - A relic's `Take` between play areas writes no line, so Relic Thief covers nothing. The roll keeps its generic "Rolled" line.
   - A secret `Burn` writes no line, so Ancient Binding covers nothing.
   - Favor and secrets moving between boards, between a bank and a banner, and cards moving between discard piles write no line: Witch's Bargain, Singing Mask and Convoys cover nothing.
   - `Discard.Vision` and `Discard.Denizen` write "Discarded {card} to the X discard", which Inquisitor's right guess and Assassin cover.
3. **A right guess makes the Vision known to every player** (product owner, 2026-10-06), so players can later browse the cards they know in the discard piles.
   - A card entering a discard pile is forgotten (`CardKnowledgeMoves`). A card in a pile is identified only by a viewer whose knowledge records it (`GamePresentationProjector.identifiesCard`).
   - So the batch discards the Vision, then reveals it in the pile: `Reveal(vision, Location.RegionalDiscard(region))`, as Brass Horse reveals a pile's top card.
     - A discarded card has no orientation state, since a pile is always facedown, so the reveal does not turn it over. The card stays facedown in the pile, and every player's knowledge records it (`OperationStateAdapter.isDiscardLook`, `CardFaceOperations.seenByAll`).
     - A batch validates its operations in turn, so the reveal sees the card already in the pile.
   - The reveal writes a generic "Revealed {card}" line. Inquisitor's line covers it with the Discarded line, since both come from the step before the line. The line's `{card}` then reads as the Vision to every viewer, as the spec says.
   - The guess's batch is **not** required. A refused discard is skipped rather than failing the path, so the search never hides one guess, which would disclose the Vision.
   - Verified: an `AdviserSlot` option on a facedown card projects as "{owner}'s facedown adviser N" with a card back (`WalkerDecisionProjector`), so the question is not suppressed. Task 3's first test shows it offered.
4. **Relic Thief checks Lost Tongue itself.**
   - Lost Tongue's restriction refuses the `Take`. But the search stops at a roll and counts the path as legal (`WalkerSearch.hides`), so the refusal would come only after the dice, and the command would fail.
   - Task 4 makes Lost Tongue's existing check public as `LostTongue.protects(ready, taker, owner)`, and Relic Thief leaves such relics out of its question.
   - Cost if wrong: none for play. A second protection rule added later would need the same treatment.
5. **Relic Thief's roll.** It uses its own pool, `relic-thief`, with an automatic roll, as Gambling Hall does. "No shield" is a shield total (`RollResults.score`) of 0, so a Doubler alone is no shield. A relic of defense 0 rolls nothing and is taken.
6. **Ancient Binding leaves each player at most 1 secret on their player UI** (product owner, 2026-10-06).
   - The player UI's total (`PlayerSecretSummary.totalSecrets`) is a player's board secrets plus, for the acting player only, every secret on a card in play (`InPlayCardResources`): advisers, relics, and the denizens, edifices and relics at sites. Only the acting player places secrets on cards during a turn.
   - So the actor burns every secret on their board and every secret on a card in play, except the one on Ancient Binding. Secrets on cards at sites they rule are burnt with the rest. The actor's UI then lists only Ancient Binding's secret.
   - Every other player burns their board down to one secret, keeping a faceup one if they have one, as Book Burning does.
   - The Darkest Secret's secrets stay.
   - The burn and its line sit in one `Branch`. An empty batch records no step, so a line after it would read the cost's step and count the burnt cost secret.
   - Cost if wrong: a secret on another player's adviser burns too. During the actor's turn the engine counts it as the actor's, so leaving it would list it on the actor's UI.
7. **Witch's Bargain's loops.**
   - The outer `Repeat` runs until the latest answer is Stop. The inner one runs while the latest answer is a player or an exchange. Both read `PowerAnswers.last` over the two decisions, since `PowerAnswers.one` reads only a decision's first answer.
   - The player question is asked even with one candidate, as Sleight of Hand's is.
   - Each exchange is one required batch.
   - The walker's search stops at a decision it revisits in the same state (`ProcedureWalker.reach`), so "Pick a different player" cannot make the search recurse for ever.
8. **Terror Spells' question.**
   - The places are the sites of the pawn's region holding warbands, in map order, then the boards of players whose pawn is in the region, in seat order. Bandits are killed like any other kind.
   - It asks only when more than one split kills the total: when the places' caps of 2 sum to more than the total. Otherwise each place loses its cap. This also keeps `Distribute`'s well-formedness: two variable slots and no forced total.
9. **Singing Mask's three questions.**
   - First the card. It offers faceup denizens and ruined edifices at the pawn's site, through `SiteCards` with every suit, but only those whose suit's bank or the People's Favor holds favor.
   - Then the direction, asked only when both sides hold favor.
   - Then the amount, from 0 to the smaller of 2 and what the giving side holds.
   - Cost if wrong: a card whose bank and banner are both empty is not offered, though choosing it could only move 0.
10. **Convoys' buttons.** Each button reads "Move the {Region} discard pile", in region order. The pile moves card by card, bottom first, each to the top of the pawn's region's pile, so its order is kept on top.
11. **Registration and pins.**
    - Each suite runs through the production catalogs (`PhasePowerHarness`, `ActPowerHarness`), so an unregistered power fails its suite.
    - `PowerKindsCatalogSuite` lists no ACTION power.
    - Assassin's limit is pinned through `AdviserLimit.of` and a card play.
12. **Filler cards.**
    - Ancient Binding is filler in `HomelandLineSuite`, `CardSlotsSuite`, `SiteDiscardFirstSuite`, `SearchProcedureSuite` and `CardPlayReasonsSuite`.
    - Convoys is filler in `RuledCardsSuite` and `LeagueTreatySuite`, which use it as a card. `GreatCrusadeSuite`, `PledgeOfDefenseSuite` and `RivalKhanSuite` take it through `PlanDriver.planless`, which refuses a card with an implemented power, so Task 6 swaps it there for Hospitality, which is not implemented.
    - Each of these uses the card as a card, not its power, and Tasks 5 and 6 run them.

## File Structure

Production, under `src/main/scala/oathdigital/`:

| File | Change | Responsibility | Task |
|---|---|---|---|
| `gameplay/powers/action/TerrorSpells.scala` | create | Terror Spells' card and ACTION | 1 |
| `gameplay/powers/PowerAnswers.scala` | modify | R5: `last` | 2 |
| `gameplay/powers/action/SecretMoves.scala` | create | one secret off a board, faceup first | 2 |
| `gameplay/powers/action/SleightOfHand.scala` | modify | takes through `SecretMoves` | 2 |
| `gameplay/powers/action/WitchsBargain.scala` | create | Witch's Bargain's card and ACTION | 2 |
| `gameplay/powers/action/Inquisitor.scala` | create | Inquisitor's card and ACTION | 3 |
| `gameplay/powers/targeting/LostTongue.scala` | modify | `protects` | 4 |
| `gameplay/powers/action/RelicThief.scala` | create | Relic Thief's card and ACTION | 4 |
| `gameplay/powers/SecretBurns.scala` | create | a board's secrets burnt down to a kept number | 5 |
| `gameplay/powers/campaign/BookBurning.scala` | modify | burns through `SecretBurns` | 5 |
| `gameplay/powers/action/AncientBinding.scala` | create | Ancient Binding's card and ACTION | 5 |
| `gameplay/powers/action/Convoys.scala` | create | Convoys' card and ACTION | 6 |
| `gameplay/powers/action/SingingMask.scala` | create | Singing Mask's card and ACTION | 7 |
| `gameplay/powers/action/Assassin.scala` | create | Assassin's card, ACTION and adviser limit | 8 |
| `gameplay/powers/AdviserLimit.scala` | modify | reads Assassin's limit | 8 |
| `gameplay/powers/WalkerPowerCatalog.scala` | modify | registers Assassin's limit | 8 |
| `gameplay/powers/action/OtherActionPowers.scala` | modify | registers Witch's Bargain, Inquisitor, Relic Thief, Assassin | 2, 3, 4, 8 |
| `gameplay/powers/action/WorldActionPowers.scala` | modify | registers Terror Spells, Ancient Binding, Convoys, Singing Mask | 1, 5, 6, 7 |
| `catalog/holding/HoldingArcaneDenizens.scala` | modify | Terror Spells, Witch's Bargain and Inquisitor leave | 1, 2, 3 |
| `catalog/holding/HoldingDiscordDenizens.scala` | modify | Relic Thief and Assassin leave | 4, 8 |
| `catalog/holding/HoldingNomadDenizens.scala` | modify | Ancient Binding and Convoys leave | 5, 6 |
| `catalog/holding/HoldingRelics.scala` | modify | Singing Mask leaves | 7 |
| `gameplay/cards/NewFoundations.scala` | modify | imports each moved card from `gameplay.powers.action` | 1–8 |

Tests, under `src/test/scala/oathdigital/`:

| File | Change | Tests | Task |
|---|---|---|---|
| `gameplay/powers/action/TerrorSpellsSuite.scala` | create | 5 | 1 |
| `gameplay/powers/PowerAnswersSuite.scala` | create | 2 | 2 |
| `gameplay/powers/action/WitchsBargainSuite.scala` | create | 6 | 2 |
| `gameplay/powers/action/InquisitorSuite.scala` | create | 5 | 3 |
| `gameplay/powers/action/RelicThiefSuite.scala` | create | 6 | 4 |
| `gameplay/powers/action/AncientBindingSuite.scala` | create | 5 | 5 |
| `gameplay/powers/action/ConvoysSuite.scala` | create | 4 | 6 |
| `gameplay/powers/action/SingingMaskSuite.scala` | create | 5 | 7 |
| `gameplay/powers/action/AssassinSuite.scala` | create | 6 | 8 |

Docs: `docs/ROADMAP.md` and the batch 4 design spec, in Task 9.

**Moving a card out of its holding file.** Each task names the object. Delete the whole `object ...Card` definition, its lines and the blank line after it, and paste it unchanged at the top of the new power file, as the code blocks below show. Every holding file keeps a use of each name it imports, so no holding import changes in this slice. In `NewFoundations.scala`, add the card's name to the `import oathdigital.gameplay.powers.action.{...}` list in alphabetical order. The card lists below it name the object already and do not change.

---

### Task 1: Terror Spells

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/TerrorSpells.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala` (remove `TerrorSpellsCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `TerrorSpellsCard`)
- Create: `src/test/scala/oathdigital/gameplay/powers/action/TerrorSpellsSuite.scala`

**Interfaces:**
- Consumes: `PaidAction`, `PowerAnswers.distribution`, `BannerRules.holder`, `PowerAccess.pawnSite`, `PlayerForceKind.of`.
- Produces: `object TerrorSpellsCard`; `case object TerrorSpells` with `decisionId = "power.terror-spells.kills"`, `Kills = 2`, `killed`, `unheld`, `empty`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/TerrorSpellsSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class TerrorSpellsSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val spells = CatalogNames.denizen("Terror Spells")
  private val source = DecisionOptionRef.Denizen(spells)
  /** p1's pawn site and Broken Peaks, p2's, lie in the Cradle; Buried
    * Giant, p3's, lies in the Provinces. */
  private val home = CatalogNames.site("Ancient City")
  private val peaks = CatalogNames.site("Broken Peaks")
  private val giant = CatalogNames.site("Buried Giant")

  /** p1's Act at Ancient City beside Terror Spells, holding the Darkest
    * Secret. Every board keeps the quiet table's 3 warbands. */
  private def staged: Table = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).denizen(spells, at = home)
    .darkestSecret(Some(p1), 1)

  private def killing(amounts: (DecisionOptionRef, Int)*): DecisionAnswer =
    DecisionAnswer.DistributeAnswer(amounts.toVector.map((ref, amount) =>
      DistributeAmount(ref, amount)))

  test("without the Darkest Secret it pays its cost and kills nothing"):
    val done = accepted(use(staged.darkestSecret(None, 1).ready, TerrorSpells,
      source))
    assert(backToActing(done))
    val end = Look(after(done))
    assertEquals(end.tokensOn(spells), Tokens(0, 1))
    assertEquals(Vector(p1, p2, p3).map(end.boardWarbands), Vector(3, 3, 3))
    assertEquals(NoteText.said(TerrorSpells, done.events), Vector(
      NoteText.Said("used.none", "The Darkest Secret was not held.",
        covers = false)))

  test("with one way to kill two it asks nothing, the actor's own board " +
      "included"):
    val done = accepted(use(staged.boardWarbands(p2, 0).ready, TerrorSpells,
      source))
    assert(backToActing(done))
    assertEquals(Look(after(done)).boardWarbands(p1), 1)
    assertEquals(NoteText.said(TerrorSpells, done.events), Vector(
      NoteText.Said(NoteKey.Used, "Killed 2 warbands.", covers = false)))

  test("two warbands in two places both die without asking"):
    val done = accepted(use(staged.boardWarbands(p1, 1).boardWarbands(p2, 1)
      .ready, TerrorSpells, source))
    assert(backToActing(done))
    val end = Look(after(done))
    assertEquals(Vector(p1, p2, p3).map(end.boardWarbands), Vector(0, 0, 3))

  test("it asks how two die across the region's sites and boards, and " +
      "offers nothing elsewhere"):
    val ready = staged.boardWarbands(p1, 0).bandits(peaks, 2)
      .warbandsAt(giant, p3, 1).ready
    val started = accepted(use(ready, TerrorSpells, source))
    assert(awaits(started, TerrorSpells.decisionId),
      parked.parkedDecision(started.state).toString)
    assertEquals(offered(started, p1), Some(Vector("site" -> peaks.value,
      "player" -> p2.value)))
    val done = accepted(answer(started, p1, TerrorSpells.decisionId,
      killing(DecisionOptionRef.Site(peaks) -> 1,
        DecisionOptionRef.Player(p2) -> 1)))
    assert(backToActing(done))
    val end = Look(after(done))
    assertEquals(end.forces(peaks), SiteForces.Occupied(ForceKind.Bandit, 1))
    assertEquals(end.boardWarbands(p2), 2)
    assertEquals(NoteText.said(TerrorSpells, done.events), Vector(
      NoteText.Said(NoteKey.Used, "Killed 2 warbands.", covers = false)))
    val events = started.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assertWireRoundTrips(events)

  test("with no warband in the region the line says so"):
    val done = accepted(use(staged.boardWarbands(p1, 0).boardWarbands(p2, 0)
      .ready, TerrorSpells, source))
    assert(backToActing(done))
    assertEquals(NoteText.said(TerrorSpells, done.events), Vector(
      NoteText.Said("used.empty", "No warband was in the region.",
        covers = false)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.TerrorSpellsSuite"`
Expected: compile failure, `TerrorSpells` is not found.

- [ ] **Step 3: Write Terror Spells**

Remove `object TerrorSpellsCard` from `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/action/TerrorSpells.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

object TerrorSpellsCard extends Denizen(DenizenId("61"), "Terror Spells", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.terror-spells"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Kill any two warbands in your region _(at sites " +
      "or on boards, even yours)_ if you hold the Darkest Secret.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Terror Spells (card 61), ACTION: place 1 secret on this card, then, if
  * the player holds the Darkest Secret, kill two warbands in the region of
  * their pawn, at sites or on boards, even their own.
  *
  * Without the Darkest Secret the power is still offered: the cost stays
  * paid and nothing else happens. With it, the places are read live after
  * the cost: each site of the region holding warbands, in map order, then
  * the board of each player whose pawn is in the region, the player's own
  * included, in seat order. Two warbands die, or every one when the region
  * holds fewer. When only one split kills them, they die without asking;
  * otherwise one `Distribute` asks how many die in each place, at most two
  * in one. Each place's kill is one `Kill` of its kind, bandits included. A
  * site the kill empties is refilled with bandits by the existing refill
  * after the action.
  *
  * No generic line exists for a kill, so its line covers nothing.
  */
case object TerrorSpells extends PaidAction(TerrorSpellsCard.power):
  val decisionId: String = "power.terror-spells.kills"
  val Kills: Int = 2
  /** "Killed {n} warbands." */
  val killed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Plural(0, " warband.", " warbands.")))
  /** "The {Darkest Secret} was not held." */
  val unheld: NoteKey = NoteKey("used.none", Vector(NotePart.Text("The "),
    NotePart.Arg(0), NotePart.Text(" was not held.")))
  /** "No warband was in the region." */
  val empty: NoteKey = NoteKey("used.empty", Vector(
    NotePart.Text("No warband was in the region.")))
  override def noteKeys: Vector[NoteKey] = Vector(killed, unheld, empty)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => kill(live, player, pending)),
    Note(id, killNote(_, player, source)))))

  /** One place warbands stand in the region: how many, and of what kind. */
  private final case class Place(ref: DecisionOptionRef, at: Location,
      kind: ForceKind, count: Int):
    /** The most that may die here. */
    def most: Int = math.min(Kills, count)

  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    BannerRules.holder(ready.game.current, Banner.DarkestSecret)
      .contains(player)

  /** The places holding warbands in the region of the player's pawn: sites
    * in map order, then boards in seat order. None while the player does
    * not hold the Darkest Secret. */
  private def places(ready: ReadyGame, player: PlayerId): Vector[Place] =
    val current = ready.game.current
    val map = current.map
    if !holds(ready, player) then Vector.empty
    else PowerAccess.pawnSite(ready, player).flatMap(map.regionOf).toVector
      .flatMap { region =>
        val sites = map.inPlay.filter(map.regionOf(_).contains(region))
        val atSites = sites.flatMap(site =>
          map.sites.get(site).map(_.forces).collect {
            case SiteForces.Occupied(kind, count) if count > 0 =>
              Place(DecisionOptionRef.Site(site), Location.Site(site), kind,
                count)
          })
        val onBoards = current.players.filter(held =>
            held.pawnSite.exists(sites.contains) && held.board.warbands > 0)
          .flatMap(held => PlayerForceKind.of(ready, held).map(kind =>
            Place(DecisionOptionRef.Player(held.player),
              Location.PlayArea(held.player), kind, held.board.warbands)))
        atSites ++ onBoards
      }

  /** How many die: two, or every warband when the region holds fewer. */
  private def total(found: Vector[Place]): Int =
    math.min(Kills, found.map(_.count).sum)

  /** Whether more than one split kills the total. */
  private def asks(found: Vector[Place]): Boolean =
    found.map(_.most).sum > total(found)

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] =
    val found = places(ready, player)
    if !asks(found) then Vector.empty
    else Vector(Decide(decisionId, player, DecisionQuery.Distribute.exactly(
      found.map(place => DistributeSlot(place.ref, 0, place.most, None)),
      total(found), Some("Terror Spells: kill two warbands in your region"),
      "Kill warbands")))

  /** Each place's cap when there is one split; the answer otherwise. */
  private def kill(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    val found = places(ready, player)
    if !asks(found) then Right(found.flatMap(place => killAt(place, place.most)))
    else PowerAnswers.distribution(pending, decisionId)
      .toRight(PowerAnswers.missing(decisionId))
      .flatMap(_.filter(_.amount > 0).foldLeft[Either[OathViolation,
          Vector[CoreOperation]]](Right(Vector.empty)) { (done, row) =>
        done.flatMap(ops => found.find(_.ref == row.ref)
          .toRight(OathViolation.InvalidEventOrder(
            s"${row.ref.wireId} holds no warband Terror Spells can kill"))
          .map(place => ops ++ killAt(place, row.amount)))
      })

  private def killAt(place: Place, amount: Int): Vector[CoreOperation] =
    if amount <= 0 then Vector.empty
    else Vector(Kill(Piece.Warbands(place.kind, amount),
      PositionedLocation(place.at)))

  /** The warbands that died in the step before the note; with none, why. A
    * step that killed nothing, such as the cost's, reads as none. */
  private def killNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).map { card =>
      val lost = states.previous.fold(0) { case (before, after) =>
        inPlay(before) - inPlay(after) }
      if lost > 0 then killed(card, NoteArg.Number(lost))
      else if !holds(states.now, player) then
        unheld(card, NoteArg.Banner(Banner.DarkestSecret))
      else empty(card)
    }

  /** Warbands on boards and at sites, of every kind. */
  private def inPlay(ready: ReadyGame): Int =
    val current = ready.game.current
    current.players.map(_.board.warbands).sum +
      current.map.sites.values.map(_.forces).collect {
        case SiteForces.Occupied(_, count) => count }.sum
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`:

- Add `TerrorSpells` to the first `Vector[PhasePower](...)`, after `BanditPaymaster`.
- In the doc comment, change "The phase powers of catalog batch 3's slice 3c and catalog batch 4's Roving Terror, actions on sites, banks and banners," to "The phase powers of catalog batch 3's slice 3c and catalog batch 4's slices 2 and 3, actions on sites, banks and banners,". Keep the comment's line wrap within 80 columns.

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `TerrorSpellsCard` to the `gameplay.powers.action` import, after `TavernSongsCard`.

- [ ] **Step 5: Run the suite and the note catalog**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.TerrorSpellsSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite"`
Expected: PASS, 5 tests in `TerrorSpellsSuite`.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/TerrorSpells.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/action/TerrorSpellsSuite.scala
git commit -m "feat(action): Terror Spells kills two warbands in the region for the Darkest Secret's holder"
```

---

### Task 2: `PowerAnswers.last`, `SecretMoves` and Witch's Bargain

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/PowerAnswers.scala` (add `last`)
- Create: `src/test/scala/oathdigital/gameplay/powers/PowerAnswersSuite.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/SecretMoves.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/SleightOfHand.scala` (class doc comment; `take`)
- Create: `src/main/scala/oathdigital/gameplay/powers/action/WitchsBargain.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala` (remove `WitchsBargainCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `WitchsBargainCard`)
- Create: `src/test/scala/oathdigital/gameplay/powers/action/WitchsBargainSuite.scala`

**Interfaces:**
- Consumes: `PaidAction`, `PlayerFacts.player`, `NoteSupport.favor`, `NoteSupport.secrets`.
- Produces:
  - `PowerAnswers.last(answered: Vector[Answered], decisions: String*): Option[DecisionOptionRef]`, which slice 4's Call for Help also uses.
  - `private[action] object SecretMoves` with `faceupFirst(holder: PlayerState, move: CoreOperation): Vector[CoreOperation]`.
  - `case object WitchsBargain` with `playerDecisionId = "power.witchs-bargain.player"`, `exchangeDecisionId = "power.witchs-bargain.exchange"`, the buttons `giveSecret`, `giveFavor`, `switch`, `stop`, and the keys `forSecret`, `forFavor`, `nothing`.

- [ ] **Step 1: Write the failing `PowerAnswers` suite**

Create `src/test/scala/oathdigital/gameplay/powers/PowerAnswersSuite.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.model._
import oathdigital.testkit.Table.p1

class PowerAnswersSuite extends munit.FunSuite:
  private def chose(decision: String, key: String): Answered =
    Answered(decision, DecisionAnswer.ChooseOneAnswer(
      DecisionOptionRef.Button(key)), p1)

  /** A loop asked "a" twice, with "b" between. */
  private val looped = Vector(chose("a", "first"), chose("b", "between"),
    chose("a", "second"))

  test("last reads a loop's latest answer, where one reads its first"):
    assertEquals(PowerAnswers.one(PendingTree(Vector("0"), looped), "a"),
      Some(DecisionOptionRef.Button("first")))
    assertEquals(PowerAnswers.last(looped, "a"),
      Some(DecisionOptionRef.Button("second")))

  test("last over several decisions reads whichever was answered last, and " +
      "nothing when none was"):
    assertEquals(PowerAnswers.last(looped.init, "a", "b"),
      Some(DecisionOptionRef.Button("between")))
    assertEquals(PowerAnswers.last(looped, "c"), None)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.PowerAnswersSuite"`
Expected: compile failure, `last` is not a member of `PowerAnswers`.

- [ ] **Step 3: Add `last`**

In `src/main/scala/oathdigital/gameplay/powers/PowerAnswers.scala`, add after `one`:

```scala
  /** The option the latest answer to any of `decisions` chose. A `Repeat`
    * asks its decisions once per pass and `one` reads only the first
    * answer, so a loop reads its latest pass with this, and a loop over
    * several decisions reads whichever was answered last. A note reads
    * `NoteStates.answered`, and a tree reads `PendingTree.answered`. */
  def last(answered: Vector[Answered], decisions: String*)
      : Option[DecisionOptionRef] = answered.reverseIterator.collectFirst {
    case Answered(id, DecisionAnswer.ChooseOneAnswer(ref), _)
        if decisions.contains(id) => ref
  }
```

- [ ] **Step 4: Run it**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.PowerAnswersSuite"`
Expected: PASS, 2 tests.

- [ ] **Step 5: Share Sleight of Hand's flip**

Create `src/main/scala/oathdigital/gameplay/powers/action/SecretMoves.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.model._

/** One secret leaving a player's board, faceup first, for Sleight of Hand
  * and Witch's Bargain.
  *
  * A one-secret move from a board holding both orientations is ambiguous
  * (`AmbiguousSecretOrientation`), so the batch flips the board's facedown
  * secrets up first, moves the secret, and flips the same number back down.
  * The net effect is exactly "one faceup secret moves". A board of one
  * orientation moves its secret as it lies.
  */
private[action] object SecretMoves:
  /** `move`, of one secret off `holder`'s board, faceup first. */
  def faceupFirst(holder: PlayerState, move: CoreOperation)
      : Vector[CoreOperation] =
    val faceDown = holder.board.faceDownSecrets
    if holder.board.faceUpSecrets > 0 && faceDown > 0 then Vector(
      FlipSecrets(holder.player, faceDown, SecretSide.FaceDown,
        SecretSide.FaceUp),
      move,
      FlipSecrets(holder.player, faceDown, SecretSide.FaceUp,
        SecretSide.FaceDown))
    else Vector(move)
```

In `src/main/scala/oathdigital/gameplay/powers/action/SleightOfHand.scala`:

- Replace `take` with:

  ```scala
  private def take(actor: PlayerId, target: PlayerState)
      : Vector[CoreOperation] = SecretMoves.faceupFirst(target,
    Take(Piece.Secrets(1), actor, Location.PlayArea(target.player),
      Location.PlayArea(actor)))
  ```

- In the class doc comment, replace the last paragraph ("The take is one `Take`. A target holding both orientations makes a bare one-secret `Take` ambiguous ... The net effect is exactly "one faceup secret moves".") with:

  ```scala
    * The take is one `Take`, faceup first ([[SecretMoves]]).
  ```

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.SleightOfHandSuite"`
Expected: PASS, unchanged.

- [ ] **Step 6: Write the failing Witch's Bargain suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/WitchsBargainSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class WitchsBargainSuite extends munit.FunSuite:
  import PhasePowerHarness._
  import WitchsBargain.{exchangeDecisionId, giveFavor, giveSecret,
    playerDecisionId, stop, switch}

  private val bargain = CatalogNames.denizen("Witch's Bargain")
  private val source = DecisionOptionRef.Denizen(bargain)
  private val home = CatalogNames.site("Ancient City")
  private val chooseP2 = playerDecisionId -> DecisionOptionRef.Player(p2)

  /** p1's Act at Ancient City beside Witch's Bargain, with 3 faceup secrets
    * and 4 favor. p2 stands there too, with 3 favor, 1 faceup and 1
    * facedown secret. p3 stays at Buried Giant. */
  private def staged: Table = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).denizen(bargain, at = home)
    .secrets(p1, faceUp = 3).favor(p1, 4)
    .pawn(p2, at = home).favor(p2, 3).secrets(p2, faceUp = 1, faceDown = 1)

  /** Each walk in turn: Witch's Bargain used, then each answer p1 gave. */
  private def walksAnswering(ready: ReadyGame,
      answers: (String, DecisionOptionRef)*): Vector[OathTransition] =
    answers.foldLeft(Vector(accepted(use(ready, WitchsBargain, source)))) {
      case (done, (decision, ref)) =>
        done :+ accepted(answer(done.last, p1, decision, pick(ref)))
    }

  test("it places a secret and asks for another player at the actor's " +
      "site, not the actor or players elsewhere"):
    val t = accepted(use(staged.ready, WitchsBargain, source))
    assert(awaits(t, playerDecisionId), parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector("player" -> p2.value)))
    assertEquals(Look(after(t)).tokensOn(bargain), Tokens(0, 1))

  test("giving a secret takes up to 2 favor, and it asks again"):
    val traded = walksAnswering(staged.favor(p2, 1).ready, chooseP2,
      exchangeDecisionId -> giveSecret).last
    assert(awaits(traded, exchangeDecisionId),
      parked.parkedDecision(traded.state).toString)
    val end = Look(after(traded))
    // 3 secrets less 1 placed and 1 traded; 4 favor and the 1 p2 held.
    assertEquals((end.faceUpSecrets(p1), end.favor(p1)), (1, 5))
    assertEquals((end.faceUpSecrets(p2), end.faceDownSecrets(p2),
      end.favor(p2)), (2, 1, 0))
    assertEquals(NoteText.said(WitchsBargain, traded.events), Vector(
      NoteText.Said("secret",
        s"Gave 1 secret to ${p2.value} and took 1 favor.", covers = false)))

  test("giving 2 favor takes a secret, faceup first"):
    val traded = walksAnswering(staged.ready, chooseP2,
      exchangeDecisionId -> giveFavor).last
    val end = Look(after(traded))
    assertEquals((end.favor(p1), end.faceUpSecrets(p1)), (2, 3))
    assertEquals((end.favor(p2), end.faceUpSecrets(p2),
      end.faceDownSecrets(p2)), (5, 0, 1))
    assertEquals(NoteText.said(WitchsBargain, traded.events), Vector(
      NoteText.Said("favor",
        s"Gave 2 favor to ${p2.value} and took 1 secret.", covers = false)))

  test("the loops run until Stop, and Pick a different player asks for the " +
      "player again"):
    val ready = staged.pawn(p3, at = home).ready
    val walks = walksAnswering(ready, chooseP2,
      exchangeDecisionId -> giveSecret,
      exchangeDecisionId -> switch,
      playerDecisionId -> DecisionOptionRef.Player(p3),
      exchangeDecisionId -> giveFavor,
      exchangeDecisionId -> stop)
    assertEquals(offered(walks(1), p1), Some(Vector(
      "button" -> giveSecret.key, "button" -> giveFavor.key,
      "button" -> switch.key, "button" -> stop.key)))
    assert(awaits(walks(3), playerDecisionId),
      parked.parkedDecision(walks(3).state).toString)
    assert(backToActing(walks.last))
    val events = walks.flatMap(_.events)
    assertEquals(NoteText.said(WitchsBargain, events), Vector(
      NoteText.Said("secret",
        s"Gave 1 secret to ${p2.value} and took 2 favor.", covers = false),
      NoteText.Said("favor",
        s"Gave 2 favor to ${p3.value} and took 1 secret.", covers = false)))
    assertEquals(replayed(ready, events), Right(walks.last.state))
    assertWireRoundTrips(events)

  test("an exchange the actor cannot pay in full is not offered, nor Pick a " +
      "different player without a second player"):
    // The cost takes p1's only secret, and 1 favor cannot pay 2.
    val chosen = walksAnswering(staged.secrets(p1, faceUp = 1).favor(p1, 1)
      .ready, chooseP2).last
    assertEquals(offered(chosen, p1), Some(Vector("button" -> stop.key)))

  test("with no exchange made, or nobody at the site, the line says so"):
    val stopped = walksAnswering(staged.ready, chooseP2,
      exchangeDecisionId -> stop)
    val alone = accepted(use(Table.start.turn(p1, Phase.Act)
      .pawn(p1, at = home).denizen(bargain, at = home).ready, WitchsBargain,
      source))
    assert(backToActing(stopped.last))
    assert(backToActing(alone))
    Vector(stopped.flatMap(_.events), alone.events).foreach(events =>
      assertEquals(NoteText.said(WitchsBargain, events), Vector(
        NoteText.Said(NoteKey.Used, "No exchange was made.", covers = false))))
```

- [ ] **Step 7: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.WitchsBargainSuite"`
Expected: compile failure, `WitchsBargain` is not found.

- [ ] **Step 8: Write Witch's Bargain**

Remove `object WitchsBargainCard` from `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/action/WitchsBargain.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, PowerAnswers}
import oathdigital.model._

object WitchsBargainCard extends Denizen(DenizenId("77"), "Witch's Bargain", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.witchs-bargain"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Give [secret] to a player whose pawn is at your " +
      "site to take [favor] [favor] from them, or give [favor] " +
      "[favor] for [secret], any number of times.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Witch's Bargain (card 77), ACTION: place 1 secret on this card, then
  * give a secret to a player whose pawn is at your site to take 2 favor
  * from them, or give 2 favor for a secret, any number of times.
  *
  * Two loops. The outer one asks for another player at the player's site;
  * the inner one asks what to do with them: "Give a secret, take 2 favor",
  * "Give 2 favor, take a secret", "Pick a different player" and "Stop". A
  * give must be paid in full, so an exchange is offered only while the
  * player can pay it, and "Pick a different player" only while a second
  * player is at the site. "Stop" ends both loops. "Pick a different player"
  * ends the inner one, and the outer one asks again. Nobody consents.
  *
  * The take is best effort: up to 2 favor, or one secret when they hold any.
  * A secret moves faceup first on either side ([[SecretMoves]]). Each
  * exchange is one required batch and writes its own line. With nobody at
  * the site, or no exchange made, one line says so.
  *
  * A loop's guard runs at its pass boundaries, after the pass's last
  * answer, so both guards read the latest answer to either question
  * (`PowerAnswers.last`).
  */
case object WitchsBargain extends PaidAction(WitchsBargainCard.power):
  val playerDecisionId: String = "power.witchs-bargain.player"
  val exchangeDecisionId: String = "power.witchs-bargain.exchange"
  /** The favor each exchange gives or takes. */
  val Favor: Int = 2
  val giveSecret: DecisionOptionRef.Button =
    DecisionOptionRef.Button("give-secret")
  val giveFavor: DecisionOptionRef.Button = DecisionOptionRef.Button("give-favor")
  val switch: DecisionOptionRef.Button = DecisionOptionRef.Button("switch")
  val stop: DecisionOptionRef.Button = DecisionOptionRef.Button("stop")
  /** "Gave {1 secret} to {Blue} and took {n favor}." */
  val forSecret: NoteKey = exchangeKey("secret")
  /** "Gave {2 favor} to {Blue} and took {n secrets}." */
  val forFavor: NoteKey = exchangeKey("favor")
  /** "No exchange was made." */
  val nothing: NoteKey = NoteKey(NoteKey.Used, Vector(
    NotePart.Text("No exchange was made.")))
  override def noteKeys: Vector[NoteKey] = Vector(forSecret, forFavor, nothing)
  override def narratedDecisions: Set[String] =
    Set(playerDecisionId, exchangeDecisionId)

  private def exchangeKey(name: String): NoteKey = NoteKey(name, Vector(
    NotePart.Text("Gave "), NotePart.Arg(0), NotePart.Text(" to "),
    NotePart.Arg(1), NotePart.Text(" and took "), NotePart.Arg(2),
    NotePart.Text(".")))

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Repeat((_, pending) => !latest(pending.answered).contains(stop),
      Sequence(Vector[Operation](
        Branch((live, _) => askPlayer(live, player)),
        Repeat((_, pending) => latest(pending.answered).exists(exchanging),
          Sequence(Vector[Operation](
            Branch((live, pending) => askExchange(live, player, pending)),
            BuildOps((live, pending) => exchange(live, player, pending),
              required = true),
            Note(id, exchangeNote(_, player, source)))))))),
    Note(id, nothingNote(_, source)))))

  /** The latest answer to either question. */
  private def latest(answered: Vector[Answered]): Option[DecisionOptionRef] =
    PowerAnswers.last(answered, playerDecisionId, exchangeDecisionId)

  /** Whether the inner loop goes on after `ref`: a player was just chosen,
    * or an exchange was just made. */
  private def exchanging(ref: DecisionOptionRef): Boolean = ref match
    case _: DecisionOptionRef.Player => true
    case other => other == giveSecret || other == giveFavor

  /** The player the latest player answer chose. */
  private def partner(answered: Vector[Answered]): Option[PlayerId] =
    PowerAnswers.last(answered, playerDecisionId).collect {
      case DecisionOptionRef.Player(chosen) => chosen }

  /** The other players whose pawn is at the player's site, in seat order. */
  private def others(ready: ReadyGame, actor: PlayerId): Vector[PlayerId] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.collect {
        case other if other.player != actor && other.pawnSite.contains(site) =>
          other.player })

  private def askPlayer(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    others(ready, actor) match
      case Vector() => Vector.empty
      case found => Vector(Decide(playerDecisionId, actor,
        DecisionQuery.ChooseOne(found.map(other =>
          DecisionOption.Player(DecisionOptionRef.Player(other))),
          heading = Some("Witch's Bargain: choose a player at your site to " +
            "bargain with"))))

  /** The exchanges the player can pay in full, a second player when one is
    * at the site, and Stop. Asked once a player is chosen. */
  private def askExchange(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    PlayerFacts.player(ready, actor).toOption
      .filter(_ => partner(pending.answered).nonEmpty).toVector.map { held =>
        val board = held.board
        Decide(exchangeDecisionId, actor, DecisionQuery.ChooseOne(
          Option.when(board.faceUpSecrets + board.faceDownSecrets > 0)(
            DecisionOption.Button(giveSecret, "Give a secret, take 2 favor"))
            .toVector ++
          Option.when(board.favor >= Favor)(
            DecisionOption.Button(giveFavor, "Give 2 favor, take a secret")) ++
          Option.when(others(ready, actor).size > 1)(
            DecisionOption.Button(switch, "Pick a different player")) :+
          DecisionOption.Button(stop, "Stop"),
          heading = Some("Witch's Bargain: make an exchange, or stop")))
      }

  /** The exchange the latest answer chose, with the chosen player. Nothing
    * after "Pick a different player" or "Stop". */
  private def exchange(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    latest(pending.answered) match
      case Some(choice) if choice == giveSecret || choice == giveFavor => for
          other <- partner(pending.answered)
            .toRight(PowerAnswers.missing(playerDecisionId))
          mine <- PlayerFacts.player(ready, actor)
          theirs <- PlayerFacts.player(ready, other)
        yield
          if choice == giveSecret then secretForFavor(mine, theirs)
          else favorForSecret(mine, theirs)
      case _ => Right(Vector.empty)

  /** One secret given, faceup first, for up to 2 favor. */
  private def secretForFavor(mine: PlayerState, theirs: PlayerState)
      : Vector[CoreOperation] =
    SecretMoves.faceupFirst(mine, Give(Piece.Secrets(1), mine.player,
      Location.PlayArea(mine.player), Location.PlayArea(theirs.player))) ++
      Option.when(theirs.board.favor > 0)(Take(
        Piece.Favor(math.min(Favor, theirs.board.favor)), mine.player,
        Location.PlayArea(theirs.player), Location.PlayArea(mine.player)))

  /** 2 favor given for one secret, faceup first, when they hold any. */
  private def favorForSecret(mine: PlayerState, theirs: PlayerState)
      : Vector[CoreOperation] =
    val held = theirs.board.faceUpSecrets + theirs.board.faceDownSecrets
    Vector[CoreOperation](Give(Piece.Favor(Favor), mine.player,
      Location.PlayArea(mine.player), Location.PlayArea(theirs.player))) ++
      (if held == 0 then Vector.empty
       else SecretMoves.faceupFirst(theirs, Take(Piece.Secrets(1),
         mine.player, Location.PlayArea(theirs.player),
         Location.PlayArea(mine.player))))

  /** The exchange the step before restates. Nothing after "Pick a
    * different player" or "Stop". */
  private def exchangeNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    choice <- latest(states.answered)
    other <- partner(states.answered)
    step <- states.previous
    note <- restated(card, choice, other, step, actor)
  yield note

  /** What was given, to whom, and what the player took in `step`. */
  private def restated(card: PowerSourceRef, choice: DecisionOptionRef,
      other: PlayerId, step: NoteSupport.Step, actor: PlayerId)
      : Option[PowerNote] =
    if choice == giveSecret then Some(forSecret(card,
      NoteArg.Amount(1, NoteUnit.Secret), NoteArg.Player(other),
      NoteArg.Amount(NoteSupport.favor(step, actor), NoteUnit.Favor)))
    else if choice == giveFavor then Some(forFavor(card,
      NoteArg.Amount(Favor, NoteUnit.Favor), NoteArg.Player(other),
      NoteArg.Amount(NoteSupport.secrets(step, actor), NoteUnit.Secret)))
    else None

  /** "No exchange was made." when no exchange was. */
  private def nothingNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = PowerSourceRef.of(source).filterNot(_ =>
    states.answered.exists {
      case Answered(`exchangeDecisionId`, DecisionAnswer.ChooseOneAnswer(ref),
          _) => ref == giveSecret || ref == giveFavor
      case _ => false
    }).map(nothing(_))
```

- [ ] **Step 9: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`:

- Add `WitchsBargain` to the first `Vector[PhasePower](...)`, after `AmberFlame`.
- In the doc comment, change "The phase powers of catalog batch 2's slice 4, catalog batch 3's slice 3b and catalog batch 4's Resettle, actions on others," to "The phase powers of catalog batch 2's slice 4, catalog batch 3's slice 3b and catalog batch 4's slices 2 and 3, actions on others,". Keep the comment's line wrap within 80 columns.

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `WitchsBargainCard` to the `gameplay.powers.action` import, after `WhistleCard`.

- [ ] **Step 10: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.WitchsBargainSuite oathdigital.gameplay.powers.action.SleightOfHandSuite oathdigital.gameplay.powers.PowerAnswersSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite"`
Expected: PASS, 6 tests in `WitchsBargainSuite`.

- [ ] **Step 11: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/PowerAnswers.scala \
  src/main/scala/oathdigital/gameplay/powers/action/SecretMoves.scala \
  src/main/scala/oathdigital/gameplay/powers/action/SleightOfHand.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WitchsBargain.scala \
  src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/PowerAnswersSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/action/WitchsBargainSuite.scala
git commit -m "feat(action): Witch's Bargain trades secrets and favor with players at the site"
```

---

### Task 3: Inquisitor

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Inquisitor.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala` (remove `InquisitorCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `InquisitorCard`)
- Create: `src/test/scala/oathdigital/gameplay/powers/action/InquisitorSuite.scala`

**Interfaces:**
- Consumes: `PaidAction`, `VisionRules`' five ids, `CardPlay.nextRegion`, `NoteSupport.answer`, and `Reveal` of a card in a discard pile, which records it as known to every player (Brass Horse uses it too).
- Produces: `case object Inquisitor` with `visionDecisionId = "power.inquisitor.vision"`, `guessDecisionId = "power.inquisitor.guess"`, `visions: Vector[(VisionId, String)]`, `guess(vision: VisionId): DecisionOptionRef.Button`, `right` and `wrong` (maps from a Vision to its key), and `nothing`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/InquisitorSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class InquisitorSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val inquisitor = CatalogNames.denizen("Inquisitor")
  private val source = DecisionOptionRef.Denizen(inquisitor)
  private val home = CatalogNames.site("Ancient City")
  /** A plain Hearth denizen. */
  private val landWarden = CatalogNames.denizen("Land Warden")
  /** p2's Vision of Faith, its only adviser on the staged table. */
  private val faithSlot = DecisionOptionRef.AdviserSlot(p2, 0)

  /** p1's Act at Ancient City beside Inquisitor, with the quiet table's
    * secret to pay it. p2 stands there too, holding the Vision of Faith
    * facedown. */
  private def staged: Table = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).denizen(inquisitor, at = home)
    .pawn(p2, at = home).adviser(p2, VisionRules.Faith, facedown = true)

  /** The walks Inquisitor started, chose p2's Vision in, and guessed in. */
  private case class Guessed(started: OathTransition, chosen: OathTransition,
      guessed: OathTransition):
    def events: Vector[OathEvent] =
      started.events ++ chosen.events ++ guessed.events

  /** Whether `viewer`'s knowledge records `vision`, as a peek or a card
    * shown to the table records a card in a pile. */
  private def knowsVision(ready: ReadyGame, viewer: PlayerId,
      vision: VisionId): Boolean =
    ready.knowledge.advisers.getOrElse(viewer, Vector.empty).contains(vision)

  private def guessP2sVision(ready: ReadyGame, vision: VisionId): Guessed =
    val started = accepted(use(ready, Inquisitor, source))
    val chosen = accepted(answer(started, p1, Inquisitor.visionDecisionId,
      pick(faithSlot)))
    Guessed(started, chosen, accepted(answer(chosen, p1,
      Inquisitor.guessDecisionId, pick(Inquisitor.guess(vision)))))

  test("it places a secret and offers the facedown Visions of players at " +
      "the site by adviser slot, the actor's own included"):
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(inquisitor, at = home)
      .adviser(p1, VisionRules.Conspiracy, facedown = true)
      .pawn(p2, at = home).adviser(p2, landWarden)
      .adviser(p2, VisionRules.Faith, facedown = true)
      .adviser(p3, VisionRules.Rebellion, facedown = true).ready
    val t = accepted(use(ready, Inquisitor, source))
    assert(awaits(t, Inquisitor.visionDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector(
      "adviser-slot" -> s"${p1.value}:0", "adviser-slot" -> s"${p2.value}:1")))
    assertEquals(Look(after(t)).tokensOn(inquisitor), Tokens(0, 1))

  test("then it offers the five Vision names, the Conspiracy included"):
    val started = accepted(use(staged.ready, Inquisitor, source))
    val chosen = accepted(answer(started, p1, Inquisitor.visionDecisionId,
      pick(faithSlot)))
    assert(awaits(chosen, Inquisitor.guessDecisionId),
      parked.parkedDecision(chosen.state).toString)
    assertEquals(queryOf(chosen, p1).map(_.offeredOptions.map(_.label)),
      Some(Vector("Vision of Conquest", "Vision of Sanctuary",
        "Vision of Rebellion", "Vision of Faith", "Conspiracy")))

  test("a right guess discards the Vision to the next region's pile, every " +
      "player knows it there, and the line names it"):
    val ready = staged.ready
    val walked = guessP2sVision(ready, VisionRules.Faith)
    assert(backToActing(walked.guessed))
    val end = Look(after(walked.guessed))
    assertEquals(end.advisers(p2), Vector.empty[CardId])
    assertEquals(end.discardAfter(home).lastOption, Some(VisionRules.Faith))
    assertEquals(Vector(p1, p2, p3).map(knowsVision(after(walked.guessed), _,
      VisionRules.Faith)), Vector(true, true, true))
    assertEquals(NoteText.said(Inquisitor, walked.guessed.events), Vector(
      NoteText.Said("used.vision-of-faith.right",
        s"Guessed Vision of Faith and discarded ${VisionRules.Faith.value} " +
          s"from ${p2.value}.", covers = true)))
    assertEquals(replayed(ready, walked.events), Right(walked.guessed.state))
    assertWireRoundTrips(walked.events)

  test("a wrong guess changes nothing, and the line says so"):
    val walked = guessP2sVision(staged.ready, VisionRules.Conquest)
    assert(backToActing(walked.guessed))
    assertEquals(Look(after(walked.guessed)).advisers(p2),
      Vector[CardId](VisionRules.Faith))
    assert(!knowsVision(after(walked.guessed), p1, VisionRules.Faith))
    assertEquals(NoteText.said(Inquisitor, walked.guessed.events), Vector(
      NoteText.Said("used.vision-of-conquest.wrong",
        s"Guessed Vision of Conquest for ${p2.value}'s Vision, wrongly.",
        covers = true)))

  test("with no facedown Vision at the site it asks nothing, and the line " +
      "says so"):
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(inquisitor, at = home)
      .adviser(p3, VisionRules.Faith, facedown = true).ready
    val done = accepted(use(ready, Inquisitor, source))
    assert(backToActing(done))
    assertEquals(NoteText.said(Inquisitor, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        "No player at the site held a facedown Vision.", covers = true)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.InquisitorSuite"`
Expected: compile failure, `Inquisitor` is not found.

- [ ] **Step 3: Write Inquisitor**

Remove `object InquisitorCard` from `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/action/Inquisitor.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.{CardPlay, VisionRules}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object InquisitorCard extends Denizen(DenizenId("38"), "Inquisitor", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.inquisitor"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Guess the name of a facedown Vision _(even the " +
      "Conspiracy)_ held by a player whose pawn is at your site. " +
      "If you are correct, discard it.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Inquisitor (card 38), ACTION: place 1 secret on this card, then guess
  * the name of a facedown Vision held by a player whose pawn is at your
  * site, the player's own included. A right guess discards it.
  *
  * Two questions. The first offers each such Vision by its adviser slot, in
  * seat then adviser order: card backs are public, so a slot discloses
  * nothing, while a card option the viewer cannot identify would suppress
  * the question. The second offers the five Vision names as buttons, the
  * Conspiracy included. A right guess discards the Vision to the discard
  * pile of the region after the player's, then reveals it there: it stays
  * facedown in the pile, and every player then knows it. The batch
  * is not required, so the search never hides a guess, which would disclose
  * the Vision; a wrong guess runs nothing.
  *
  * The line names the guess in its own words, one note key per Vision,
  * since on a wrong guess a card argument would read as a card back to a
  * viewer who cannot identify it. Its line covers the right guess's generic
  * Discarded and Revealed lines.
  */
case object Inquisitor extends PaidAction(InquisitorCard.power):
  val visionDecisionId: String = "power.inquisitor.vision"
  val guessDecisionId: String = "power.inquisitor.guess"
  /** The five Visions a guess may name, in the order offered, with their
    * names. */
  val visions: Vector[(VisionId, String)] = Vector(
    VisionRules.Conquest -> "Vision of Conquest",
    VisionRules.Sanctuary -> "Vision of Sanctuary",
    VisionRules.Rebellion -> "Vision of Rebellion",
    VisionRules.Faith -> "Vision of Faith",
    VisionRules.Conspiracy -> "Conspiracy")

  /** The button that guesses `vision`: its id without "vision:". */
  def guess(vision: VisionId): DecisionOptionRef.Button =
    DecisionOptionRef.Button(vision.value.stripPrefix("vision:"))

  /** "Guessed {name} and discarded {card} from {Blue}.", one per Vision. */
  val right: Map[VisionId, NoteKey] = visions.map((vision, name) =>
    vision -> NoteKey(s"used.${guess(vision).key}.right", Vector(
      NotePart.Text(s"Guessed $name and discarded "), NotePart.Arg(0),
      NotePart.Text(" from "), NotePart.Arg(1), NotePart.Text(".")))).toMap
  /** "Guessed {name} for {Blue}'s Vision, wrongly.", one per Vision. */
  val wrong: Map[VisionId, NoteKey] = visions.map((vision, name) =>
    vision -> NoteKey(s"used.${guess(vision).key}.wrong", Vector(
      NotePart.Text(s"Guessed $name for "), NotePart.Arg(0),
      NotePart.Text("'s Vision, wrongly.")))).toMap
  /** "No player at the site held a facedown Vision." */
  val nothing: NoteKey = NoteKey(NoteKey.Used, Vector(
    NotePart.Text("No player at the site held a facedown Vision.")))
  override def noteKeys: Vector[NoteKey] =
    visions.flatMap((vision, _) => Vector(right(vision), wrong(vision))) :+
      nothing
  override def narratedDecisions: Set[String] =
    Set(visionDecisionId, guessDecisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askVision(live, player)),
    Branch((_, pending) => askGuess(player, pending)),
    BuildOps((live, pending) => discard(live, player, pending)),
    Note(id, guessedNote(_, source), covers = true))))

  private final case class Target(owner: PlayerId, slot: Int,
      vision: VisionId):
    def ref: DecisionOptionRef.AdviserSlot =
      DecisionOptionRef.AdviserSlot(owner, slot)

  /** The facedown Visions held by players whose pawn is at the player's
    * site, the player's own included, in seat then adviser order. */
  private def targets(ready: ReadyGame, actor: PlayerId): Vector[Target] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site => for
      held <- ready.game.current.players if held.pawnSite.contains(site)
      (adviser, slot) <- held.advisers.zipWithIndex
      vision <- adviser match
        case VisionState(id, Orientation.FaceDown) => Some(id)
        case _ => None
    yield Target(held.player, slot, vision))

  private def askVision(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    targets(ready, actor) match
      case Vector() => Vector.empty
      case found => Vector(Decide(visionDecisionId, actor,
        DecisionQuery.ChooseOne(found.map(target =>
          DecisionOption.AdviserSlot(target.ref)),
          heading = Some("Inquisitor: choose a facedown Vision held by a " +
            "player at your site"))))

  /** Asked once a Vision is chosen. */
  private def askGuess(actor: PlayerId, pending: PendingTree)
      : Vector[Operation] =
    if PowerAnswers.one(pending, visionDecisionId).isEmpty then Vector.empty
    else Vector(Decide(guessDecisionId, actor, DecisionQuery.ChooseOne(
      visions.map((vision, name) => DecisionOption.Button(guess(vision), name)),
      heading = Some("Inquisitor: guess the Vision's name"))))

  /** A right guess discards the Vision; a wrong one runs nothing. No Vision
    * answer means there was none to guess. */
  private def discard(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, visionDecisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => for
        target <- targets(ready, actor).find(_.ref == ref)
          .toRight(OathViolation.InvalidEventOrder(
            s"${ref.wireId} is not a facedown Vision Inquisitor can guess"))
        answer <- PowerAnswers.one(pending, guessDecisionId)
          .toRight(PowerAnswers.missing(guessDecisionId))
        guessed <- visions.collectFirst {
          case (vision, _) if guess(vision) == answer => vision }
          .toRight(OathViolation.InvalidEventOrder(
            s"${answer.wireId} names no Vision"))
        region <- PowerAccess.pawnSite(ready, actor)
          .flatMap(ready.game.current.map.regionOf).map(CardPlay.nextRegion)
          .toRight(OathViolation.PawnSiteMissing(actor))
      yield
        if guessed != target.vision then Vector.empty
        else Vector(Discard.Vision(target.vision,
            PositionedLocation(Location.PlayArea(target.owner)), region),
          Reveal(target.vision, Location.RegionalDiscard(region)))

  /** The guess and whose Vision it was about. It was right when that player
    * held the guessed Vision before the step and not after it. */
  private def guessedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = PowerSourceRef.of(source).map(card =>
    (NoteSupport.answer(states, visionDecisionId),
        NoteSupport.answer(states, guessDecisionId)) match
      case (Some(DecisionOptionRef.AdviserSlot(owner, _)), Some(answer)) =>
        visions.collectFirst { case (vision, _) if guess(vision) == answer =>
          if discarded(states, owner, vision) then
            right(vision)(card, NoteArg.Card(vision), NoteArg.Player(owner))
          else wrong(vision)(card, NoteArg.Player(owner))
        }.getOrElse(nothing(card))
      case _ => nothing(card))

  private def discarded(states: NoteStates, owner: PlayerId,
      vision: VisionId): Boolean = states.previous.exists {
    case (before, after) => holds(before, owner, vision) &&
      !holds(after, owner, vision) }

  private def holds(ready: ReadyGame, owner: PlayerId, vision: VisionId)
      : Boolean = ready.game.current.players.exists(held =>
    held.player == owner && held.advisers.exists(_.id == vision))
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`, add `Inquisitor` to the first `Vector[PhasePower](...)`, after `WitchsBargain`.

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `InquisitorCard` to the `gameplay.powers.action` import, after `HonorGuardCard`.

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.InquisitorSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite"`
Expected: PASS, 5 tests in `InquisitorSuite`.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/Inquisitor.scala \
  src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/action/InquisitorSuite.scala
git commit -m "feat(action): Inquisitor guesses a facedown Vision and discards it when right"
```

---

### Task 4: Relic Thief, and `LostTongue.protects`

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/targeting/LostTongue.scala` (add `protects`)
- Create: `src/main/scala/oathdigital/gameplay/powers/action/RelicThief.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala` (remove `RelicThiefCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `RelicThiefCard`)
- Create: `src/test/scala/oathdigital/gameplay/powers/action/RelicThiefSuite.scala`

**Interfaces:**
- Consumes: `PaidAction`, `RollResults.score` (`private[action]`), `ActPowerHarness` with `defenseDice`.
- Produces:
  - `LostTongue#protects(ready: ReadyGame, taker: PlayerId, owner: PlayerId): Boolean`.
  - `final case class RelicThief`, and its companion with `id`, `decisionId = "power.relic-thief.relic"`, `pool = PoolKey("relic-thief")`, `took`, `kept`, `nothing` and `forCatalog(catalog: ExecutableCatalog): RelicThief`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/RelicThiefSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerDice}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.{assertWireRoundTrips, ready}
import oathdigital.testkit.PhasePowerHarness.offered
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class RelicThiefSuite extends munit.FunSuite:
  import ActPowerHarness._

  private val walkerParked = new ParkedDecisionAssertions(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private val power = RelicThief.forCatalog(catalog)
  private val thief = CatalogNames.denizen("Relic Thief")
  private val source = DecisionOptionRef.Denizen(thief)
  private val home = CatalogNames.site("Ancient City")
  /** Defense 1. */
  private val whistle = CatalogNames.relic("Whistle")
  /** Defense 2. */
  private val ivoryEye = CatalogNames.relic("Ivory Eye")
  /** Defense 0. */
  private val harp = CatalogNames.relic("Truthful Harp")
  /** Defense 2. */
  private val clayRattle = CatalogNames.relic("Clay Rattle")
  private val lostTongue = CatalogNames.denizen("Lost Tongue")

  /** p1's Act at Ancient City holding Relic Thief, with the quiet table's 1
    * favor and 1 secret to pay it. p2 stands there too, holding Whistle
    * faceup. */
  private def staged: Table = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).adviser(p1, thief)
    .pawn(p2, at = home).relic(p2, whistle)

  /** The walk Relic Thief started and the walk after `relic` was chosen. */
  private case class Chosen(started: OathTransition, answered: OathTransition):
    def events: Vector[OathEvent] = started.events ++ answered.events

  /** Relic Thief used on `before` and `relic` chosen, the dice showing
    * `dice`. */
  private def chooseRelic(before: ReadyGame, relic: RelicId,
      dice: WalkerDice = WalkerDice.unavailable): Chosen =
    val rolling = rules(dice)
    val started = accepted(use(rolling, before, RelicThief.id, source))
    Chosen(started, accepted(answer(rolling, started.state,
      RelicThief.decisionId, DecisionOptionRef.Relic(relic))))

  test("it places a favor and a secret and offers the faceup relics of " +
      "other players at the site"):
    val before = staged.relic(p2, ivoryEye, facedown = true).relic(p1, harp)
      .relic(p3, clayRattle).ready
    val t = accepted(use(rules(), before, RelicThief.id, source))
    walkerParked.assertParked(t.state, ActionRef.UsePower(RelicThief.id),
      RelicThief.decisionId, p1)
    assertEquals(offered(t, p1), Some(Vector("relic" -> whistle.value)))
    assertEquals(Look(ready(t.state)).tokensOn(thief), Tokens(1, 1))

  test("rolling no shield takes the relic, and the line names its holder"):
    val before = staged.ready
    val blank = defenseDice(DefenseDieFace.Blank)
    val walked = chooseRelic(before, whistle, blank)
    walkerParked.assertResumed(walked.answered.state, Phase.Act, p1)
    val end = Look(ready(walked.answered.state))
    assertEquals(end.relics(p1), Vector(whistle))
    assertEquals(end.relics(p2), Vector.empty[RelicId])
    assertEquals(NoteText.said(power, walked.answered.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"Rolled no shield and took ${whistle.value} from ${p2.value}.",
        covers = false)))
    assertEquals(replayed(rules(blank), before, walked.events),
      ready(walked.answered.state))
    assertWireRoundTrips(walked.events)

  test("rolling a shield leaves the relic with its holder"):
    val walked = chooseRelic(staged.ready, whistle,
      defenseDice(DefenseDieFace.OneShield))
    assertEquals(Look(ready(walked.answered.state)).relics(p2), Vector(whistle))
    assertEquals(NoteText.said(power, walked.answered.events), Vector(
      NoteText.Said("used.kept",
        s"Rolled a shield; ${p2.value} kept ${whistle.value}.",
        covers = false)))

  test("a relic of defense 0 is taken without a roll"):
    // The dice are unavailable, so a roll would fail the command.
    val walked = chooseRelic(staged.relic(p2, harp).ready, harp)
    assertEquals(Look(ready(walked.answered.state)).relics(p1), Vector(harp))

  test("a relic Lost Tongue protects is not offered"):
    // p1 rules no Nomad card, so p2's faceup Lost Tongue keeps Whistle.
    val before = staged.adviser(p2, lostTongue).pawn(p3, at = home)
      .relic(p3, harp).ready
    val t = accepted(use(rules(), before, RelicThief.id, source))
    assertEquals(offered(t, p1), Some(Vector("relic" -> harp.value)))

  test("with no faceup relic to take it asks nothing, and the line says so"):
    val before = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .adviser(p1, thief).pawn(p2, at = home)
      .relic(p2, whistle, facedown = true).ready
    val done = accepted(use(rules(), before, RelicThief.id, source))
    walkerParked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", "No faceup relic could be taken.", covers = false)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.RelicThiefSuite"`
Expected: compile failure, `RelicThief` is not found.

- [ ] **Step 3: Expose Lost Tongue's check**

In `src/main/scala/oathdigital/gameplay/powers/targeting/LostTongue.scala`, add after `bannerShielded`:

```scala
  /** Whether this card keeps `taker` from taking `owner`'s relics and
    * banners: `owner` holds it faceup, and `taker` is another player who
    * rules no nomad card. A power that must leave such a take out of its
    * question reads this, as Relic Thief does before its roll, past which
    * the search does not look. */
  def protects(ready: ReadyGame, taker: PlayerId, owner: PlayerId): Boolean =
    protectedHolder(ready, taker).contains(owner)
```

- [ ] **Step 4: Write Relic Thief**

Remove `object RelicThiefCard` from `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/action/RelicThief.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.gameplay.powers.targeting.LostTongue
import oathdigital.model._

object RelicThiefCard extends Denizen(DenizenId("95"), "Relic Thief", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.relic-thief"),
    persistent = false, cost = Cost(favor = 1, secret = 1),
    text = "**ACTION:** Choose a faceup relic held by a player whose " +
      "pawn is at your site. Roll [defense-die] equal to its " +
      "defense. If you roll no [shield], take it.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Relic Thief (card 95, adviser-only), ACTION: place 1 favor and 1 secret
  * on this card, then choose a faceup relic held by another player whose
  * pawn is at your site, roll defense dice equal to its defense, and take it
  * if no shield is rolled.
  *
  * The relics are read live after the cost, in seat then relic order. A
  * relic Lost Tongue keeps the player from taking is not offered: the search
  * stops at the roll, so the `Take` its restriction refuses after the roll
  * would not hide the relic, and the command would fail after the dice. The
  * roll is automatic, from the walker's dice, on its own pool. A relic of
  * defense 0 rolls nothing and is taken. "No shield" is a shield total of
  * 0, so a Doubler alone is no shield. The take is one `Take`. With no relic
  * to choose, the cost stays paid and one line says so.
  *
  * No generic line exists for the take, so the line covers nothing, and the
  * roll keeps its "Rolled" line.
  */
final case class RelicThief private (catalog: ExecutableCatalog)
    extends PaidAction(RelicThiefCard.power):
  import RelicThief._

  private val lostTongue = LostTongue.forCatalog(catalog)

  override def noteKeys: Vector[NoteKey] = Vector(took, kept, nothing)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    Branch((live, pending) => roll(live, player, pending)),
    BuildOps((live, pending) => Right(steal(live, player, pending))),
    Note(this.id, stolenNote(_, player, source)))))

  private final case class Target(owner: PlayerId, relic: RelicId):
    def ref: DecisionOptionRef = DecisionOptionRef.Relic(relic)

  /** The faceup relics of the other players at the player's site that Lost
    * Tongue leaves takeable, in seat then relic order. */
  private def targets(ready: ReadyGame, actor: PlayerId): Vector[Target] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site => for
      other <- ready.game.current.players
      if other.player != actor && other.pawnSite.contains(site) &&
        !lostTongue.protects(ready, actor, other.player)
      relic <- other.relics.collect {
        case RelicState(id, Orientation.FaceUp, _) => id }
    yield Target(other.player, relic))

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    targets(ready, actor) match
      case Vector() => Vector.empty
      case found => Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
        found.map(target =>
          DecisionOption.Relic(DecisionOptionRef.Relic(target.relic))),
        heading = Some("Relic Thief: choose a faceup relic held by a player " +
          "at your site"))))

  private def chosen(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Option[Target] = PowerAnswers.one(pending, decisionId)
    .flatMap(ref => targets(ready, actor).find(_.ref == ref))

  private def defense(relic: RelicId): Int =
    catalog.relic(relic).fold(0)(_.defense)

  /** The chosen relic's defense dice, rolled at once; none at defense 0. */
  private def roll(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Vector[Operation] = chosen(ready, actor, pending)
    .map(target => defense(target.relic)).filter(_ > 0).toVector
    .flatMap(dice => Vector[Operation](ModifyDicePool(pool, dice),
      Roll(pool, DiceSpec(DiceKind.Defense), RollMode.Automatic)))

  /** The take, when the relic rolled nothing or the roll shows no shield. */
  private def steal(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Vector[CoreOperation] = chosen(ready, actor, pending).filter(target =>
    defense(target.relic) == 0 || RollResults.score(ready, pool) == 0)
    .toVector.map(target => Take(Piece.Card(target.relic), actor,
      Location.PlayArea(target.owner), Location.PlayArea(actor)))

  /** Whether the relic changed hands: the player holds it now, taken from
    * whoever held it before the step, or its holder kept it. */
  private def stolenNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).map(card =>
      NoteSupport.answer(states, decisionId) match
        case Some(DecisionOptionRef.Relic(relic)) =>
          holderOf(states.now, relic) match
            case Some(holder) if holder == actor => states.previous
              .flatMap { case (before, _) => holderOf(before, relic) }
              .fold(nothing(card))(owner =>
                took(card, NoteArg.Card(relic), NoteArg.Player(owner)))
            case Some(owner) =>
              kept(card, NoteArg.Player(owner), NoteArg.Card(relic))
            case None => nothing(card)
        case _ => nothing(card))

  private def holderOf(ready: ReadyGame, relic: RelicId): Option[PlayerId] =
    ready.game.current.players.find(_.relics.exists(_.id == relic))
      .map(_.player)

object RelicThief:
  val id: PowerId = RelicThiefCard.power.id
  val decisionId: String = "power.relic-thief.relic"
  /** Its own dice pool. */
  val pool: PoolKey = PoolKey("relic-thief")
  /** "Rolled no shield and took {relic} from {Blue}." */
  val took: NoteKey = NoteKey(NoteKey.Used, Vector(
    NotePart.Text("Rolled no shield and took "), NotePart.Arg(0),
    NotePart.Text(" from "), NotePart.Arg(1), NotePart.Text(".")))
  /** "Rolled a shield; {Blue} kept {relic}." */
  val kept: NoteKey = NoteKey("used.kept", Vector(
    NotePart.Text("Rolled a shield; "), NotePart.Arg(0),
    NotePart.Text(" kept "), NotePart.Arg(1), NotePart.Text(".")))
  /** "No faceup relic could be taken." */
  val nothing: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No faceup relic could be taken.")))

  def forCatalog(catalog: ExecutableCatalog): RelicThief =
    new RelicThief(catalog)
```

- [ ] **Step 5: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`, append `++ Vector(RelicThief.forCatalog(catalog))` after `Vector(Resettle.forCatalog(catalog))`.

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `RelicThiefCard` to the `gameplay.powers.action` import, after `RelicBreakerCard`.

- [ ] **Step 6: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.RelicThiefSuite oathdigital.gameplay.powers.targeting.LostTongueSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite"`
Expected: PASS, 6 tests in `RelicThiefSuite`; `LostTongueSuite` unchanged.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/targeting/LostTongue.scala \
  src/main/scala/oathdigital/gameplay/powers/action/RelicThief.scala \
  src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/action/RelicThiefSuite.scala
git commit -m "feat(action): Relic Thief rolls for a faceup relic and takes it on no shield"
```

---

### Task 5: `SecretBurns` and Ancient Binding

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/SecretBurns.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/BookBurning.scala` (import; `burn`)
- Create: `src/main/scala/oathdigital/gameplay/powers/action/AncientBinding.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala` (remove `AncientBindingCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `AncientBindingCard`)
- Create: `src/test/scala/oathdigital/gameplay/powers/action/AncientBindingSuite.scala`

**Interfaces:**
- Consumes: `PaidAction`, `InPlayCardResources.discover` (`gameplay`), `PlayerSecretSummary.derive` (in the suite).
- Produces: `object SecretBurns` with `keeping(held: PlayerState, kept: Int): Vector[CoreOperation]`; `case object AncientBinding` with `burned` and `nothing`.

- [ ] **Step 1: Share Book Burning's burn**

Create `src/main/scala/oathdigital/gameplay/powers/SecretBurns.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.model._

/** A board's secrets burnt down to the number its player keeps, for Book
  * Burning and Ancient Binding. Only a faceup secret can be burnt, so the
  * facedown ones go first: they are turned faceup and burnt in the same
  * batch. The secrets kept are faceup when any faceup one remains.
  */
object SecretBurns:
  /** Every secret on `held`'s board but `kept`. A board holding `kept` or
    * fewer loses nothing. A batch runs its operations in order, so the burn
    * sees the secrets just turned. */
  def keeping(held: PlayerState, kept: Int): Vector[CoreOperation] =
    val burnt = held.board.faceUpSecrets + held.board.faceDownSecrets - kept
    val turned = math.min(held.board.faceDownSecrets, burnt)
    if burnt <= 0 then Vector.empty
    else Option.when[CoreOperation](turned > 0)(FlipSecrets(held.player,
      turned, SecretSide.FaceDown, SecretSide.FaceUp)).toVector :+
      Burn.secrets(burnt, PositionedLocation(Location.PlayArea(held.player)))
```

In `src/main/scala/oathdigital/gameplay/powers/campaign/BookBurning.scala`:

- Change `import oathdigital.gameplay.powers.NoteSupport` to `import oathdigital.gameplay.powers.{NoteSupport, SecretBurns}`.
- Replace `burn` and its doc comment with:

  ```scala
  /** Every secret on `defender`'s board but one ([[SecretBurns]]). */
  private def burn(ready: ReadyGame, defender: PlayerId): Vector[CoreOperation] =
    ready.game.current.players.find(_.player == defender).toVector
      .flatMap(SecretBurns.keeping(_, 1))
  ```

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.BookBurningSuite"`
Expected: PASS, unchanged.

- [ ] **Step 2: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/AncientBindingSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PlayerSecretSummary
import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class AncientBindingSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val binding = CatalogNames.denizen("Ancient Binding")
  private val source = DecisionOptionRef.Denizen(binding)
  private val home = CatalogNames.site("Ancient City")
  /** Cards that each carry a secret on the staged table: p1's adviser and
    * relic, a denizen at p1's site, and p2's adviser. */
  private val elders = CatalogNames.denizen("Elders")
  private val whistle = CatalogNames.relic("Whistle")
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val landWarden = CatalogNames.denizen("Land Warden")

  /** p1's Act at Ancient City holding Ancient Binding, with 2 faceup and 2
    * facedown secrets. Elders, Whistle, Errand Boy at Ancient City and p2's
    * Land Warden carry 1 secret each. p2 holds 1 faceup and 2 facedown
    * secrets, and the Darkest Secret with 2 on it. p3 holds 2 facedown. */
  private def staged: ReadyGame = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).adviser(p1, binding)
    .secrets(p1, faceUp = 2, faceDown = 2)
    .adviser(p1, elders).tokens(elders, secrets = 1)
    .relic(p1, whistle).tokens(whistle, secrets = 1)
    .denizen(errandBoy, at = home).tokens(errandBoy, secrets = 1)
    .adviser(p2, landWarden).tokens(landWarden, secrets = 1)
    .secrets(p2, faceUp = 1, faceDown = 2).darkestSecret(Some(p2), 2)
    .secrets(p3, faceUp = 0, faceDown = 2).ready

  /** The secrets `player`'s board lists on the player UI. */
  private def listed(ready: ReadyGame, player: PlayerId): Int =
    PlayerSecretSummary.derive(ready, player).fold(fail(_), _.totalSecrets)

  test("the actor burns their board and every secret on a card in play but " +
      "the one on Ancient Binding"):
    val done = accepted(use(staged, AncientBinding, source))
    assert(backToActing(done))
    val end = Look(after(done))
    assertEquals((end.faceUpSecrets(p1), end.faceDownSecrets(p1)), (0, 0))
    assertEquals(Vector[CardId](binding, elders, whistle, errandBoy,
      landWarden).map(card => end.tokensOn(card).secrets),
      Vector(1, 0, 0, 0, 0))

  test("every other player keeps one secret, faceup if they have one, and " +
      "the Darkest Secret keeps its own"):
    val end = after(accepted(use(staged, AncientBinding, source)))
    val look = Look(end)
    assertEquals((look.faceUpSecrets(p2), look.faceDownSecrets(p2)), (1, 0))
    assertEquals((look.faceUpSecrets(p3), look.faceDownSecrets(p3)), (0, 1))
    assertEquals(end.game.current.banners.darkestSecret.secrets, 2)

  test("afterwards each player's board lists one secret, the actor's being " +
      "the one on Ancient Binding"):
    val before = staged
    assertEquals(Vector(p1, p2, p3).map(listed(before, _)), Vector(8, 3, 2))
    val end = after(accepted(use(before, AncientBinding, source)))
    assertEquals(Vector(p1, p2, p3).map(listed(end, _)), Vector(1, 1, 1))

  test("its line counts every secret burnt"):
    val ready = staged
    val done = accepted(use(ready, AncientBinding, source))
    // p1's 2 left after the cost, the 4 on cards, p2's 2 and p3's 1.
    assertEquals(NoteText.said(AncientBinding, done.events), Vector(
      NoteText.Said(NoteKey.Used, "Burned 9 secrets.", covers = false)))
    assertEquals(replayed(ready, done.events), Right(done.state))
    assertWireRoundTrips(done.events)

  test("with nothing to burn the line says so"):
    // The cost takes both of p1's secrets; p2 and p3 hold one each.
    val ready = Table.start.turn(p1, Phase.Act).adviser(p1, binding)
      .secrets(p1, faceUp = 2).ready
    val done = accepted(use(ready, AncientBinding, source))
    assert(backToActing(done))
    assertEquals(NoteText.said(AncientBinding, done.events), Vector(
      NoteText.Said("used.none", "No secret was burned.", covers = false)))
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.AncientBindingSuite"`
Expected: compile failure, `AncientBinding` is not found.

- [ ] **Step 4: Write Ancient Binding**

Remove `object AncientBindingCard` from `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/action/AncientBinding.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.InPlayCardResources
import oathdigital.gameplay.powers.SecretBurns
import oathdigital.model._

object AncientBindingCard extends Denizen(DenizenId("23"), "Ancient Binding", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.ancient-binding"),
    persistent = false, cost = Cost(secret = 1, secretBurnt = 1),
    text = "**ACTION:** Each player burns all [secret] from their board " +
      "and cards except their last [secret]. _(Keep only your " +
      "[secret] here.)_")
  val powers: Vector[PrintedPower] = Vector(power)

/** Ancient Binding (card 23), ACTION: place 1 secret on this card and burn
  * 1, then each player burns all secrets from their board and cards except
  * their last; the player keeps only the one on this card.
  *
  * Afterwards each player's board lists at most one secret
  * (`PlayerSecretSummary`). That count is a player's board, plus, for the
  * acting player, every secret on a card in play: during a turn only the
  * acting player places secrets on cards. So the player burns every secret
  * on their board and every secret on a card in play, at sites or in play
  * areas, except Ancient Binding's. Every other player burns all but one
  * secret from their board, keeping a faceup one if they have one, as Book
  * Burning does ([[SecretBurns]]). The Darkest Secret's secrets stay.
  * Everything burns in one batch, read live after the cost.
  *
  * One line counts every secret burnt, or says none was. The batch and its
  * line are chosen together in one `Branch`: an empty batch records no step,
  * and a line after it would read the cost's step instead.
  *
  * No generic line exists for a burn, so the line covers nothing.
  */
case object AncientBinding extends PaidAction(AncientBindingCard.power):
  /** "Burned {n secrets}." */
  val burned: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Burned "),
    NotePart.Arg(0), NotePart.Text(".")))
  /** "No secret was burned." */
  val nothing: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No secret was burned.")))
  override def noteKeys: Vector[NoteKey] = Vector(burned, nothing)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match
    case DecisionOptionRef.Denizen(card) => Right(Sequence(Vector[Operation](
      Branch((live, _) =>
        if burns(live, player, card).isEmpty then
          Vector(Note(id, _ => PowerSourceRef.of(source).map(nothing(_))))
        else Vector(
          BuildOps((state, _) => Right(burns(state, player, card))),
          Note(id, burnedNote(_, source)))))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not a denizen source"))

  /** The player's board and every card in play, then every other board
    * down to one secret, in seat order. */
  private def burns(ready: ReadyGame, actor: PlayerId, binding: DenizenId)
      : Vector[CoreOperation] =
    ready.game.current.players.flatMap(held =>
      SecretBurns.keeping(held, if held.player == actor then 0 else 1)) ++
      onCards(ready, binding)

  /** Every secret on a card in play but Ancient Binding: advisers and
    * relics in play areas, denizens, edifices and relics at sites. */
  private def onCards(ready: ReadyGame, binding: DenizenId)
      : Vector[CoreOperation] =
    val found = InPlayCardResources.discover(ready)
    val cards: Vector[(CardId, Int)] =
      found.denizens.map(card => (card.id: CardId) -> card.tokens.secrets) ++
        found.relics.map(relic => (relic.id: CardId) -> relic.tokens.secrets)
    cards.collect { case (card, secrets) if secrets > 0 && card != binding =>
      Burn.secrets(secrets, PositionedLocation(Location.OnCard(card))) }

  /** The secrets that left boards and cards in the step before the note. */
  private def burnedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    (before, after) <- states.previous
    count = held(before) - held(after)
    if count > 0
  yield burned(card, NoteArg.Amount(count, NoteUnit.Secret))

  /** Secrets on every board and on every card in play. */
  private def held(ready: ReadyGame): Int =
    ready.game.current.players.map(player =>
      player.board.faceUpSecrets + player.board.faceDownSecrets).sum +
      InPlayCardResources.discover(ready).secrets
```

- [ ] **Step 5: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`, add `AncientBinding` to the first `Vector[PhasePower](...)`, after `TerrorSpells`.

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `AncientBindingCard` to the `gameplay.powers.action` import, after `AmberFlameCard`.

- [ ] **Step 6: Run the suites, and the suites that hold Ancient Binding as filler**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.AncientBindingSuite oathdigital.gameplay.powers.campaign.BookBurningSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite *HomelandLineSuite *CardSlotsSuite *SiteDiscardFirstSuite *SearchProcedureSuite *CardPlayReasonsSuite"`
Expected: PASS, 5 tests in `AncientBindingSuite`; the rest unchanged.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/SecretBurns.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/BookBurning.scala \
  src/main/scala/oathdigital/gameplay/powers/action/AncientBinding.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/action/AncientBindingSuite.scala
git commit -m "feat(action): Ancient Binding burns every secret but each player's last"
```

---

### Task 6: Convoys

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Convoys.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala` (remove `ConvoysCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `ConvoysCard`)
- Create: `src/test/scala/oathdigital/gameplay/powers/action/ConvoysSuite.scala`

**Interfaces:**
- Consumes: `PaidAction`, `SearchSource.name`.
- Produces: `case object Convoys` with `decisionId = "power.convoys.pile"`, `ref(region: Region): DecisionOptionRef.Button` and `moved`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/ConvoysSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class ConvoysSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val convoys = CatalogNames.denizen("Convoys")
  private val source = DecisionOptionRef.Denizen(convoys)
  /** In the Cradle, and in the Provinces. */
  private val home = CatalogNames.site("Ancient City")
  private val giant = CatalogNames.site("Buried Giant")
  /** Plain cards for the piles. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val wildMounts = CatalogNames.denizen("Wild Mounts")
  private val mountainGiant = CatalogNames.denizen("Mountain Giant")

  /** p1's Act at Ancient City beside Convoys, site-only. The Cradle pile
    * holds Errand Boy; the Provinces pile holds Wild Mounts under Mountain
    * Giant. The Hinterland pile is empty. */
  private def staged: Table = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).denizen(convoys, at = home)
    .discarded(Region.Cradle, errandBoy)
    .discarded(Region.Provinces, wildMounts, mountainGiant)

  private def pileOf(ready: ReadyGame, region: Region): Vector[WorldCardId] =
    ready.game.current.commonCards.discard(region)

  /** The walk Convoys started and the walk after `region`'s pile was
    * chosen. */
  private case class Chosen(started: OathTransition, answered: OathTransition):
    def events: Vector[OathEvent] = started.events ++ answered.events

  private def choosePile(ready: ReadyGame, region: Region): Chosen =
    val started = accepted(use(ready, Convoys, source))
    Chosen(started, accepted(answer(started, p1, Convoys.decisionId,
      pick(Convoys.ref(region)))))

  test("it places a favor and offers the other regions' piles as buttons " +
      "with their own labels"):
    val t = accepted(use(staged.ready, Convoys, source))
    assert(awaits(t, Convoys.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector("button" -> "provinces",
      "button" -> "hinterland")))
    assertEquals(queryOf(t, p1).map(_.offeredOptions.map(_.label)),
      Some(Vector("Move the Provinces discard pile",
        "Move the Hinterland discard pile")))
    assertEquals(Look(after(t)).tokensOn(convoys), Tokens(1, 0))

  test("the chosen pile goes on top of the region's pile in its order, and " +
      "the line names both"):
    val ready = staged.ready
    val walked = choosePile(ready, Region.Provinces)
    assert(backToActing(walked.answered))
    val end = after(walked.answered)
    assertEquals(pileOf(end, Region.Cradle),
      Vector[WorldCardId](errandBoy, wildMounts, mountainGiant))
    assertEquals(pileOf(end, Region.Provinces), Vector.empty[WorldCardId])
    assertEquals(NoteText.said(Convoys, walked.answered.events), Vector(
      NoteText.Said(NoteKey.Used,
        "Moved the Provinces discard pile onto the Cradle discard pile.",
        covers = false)))
    assertEquals(replayed(ready, walked.events), Right(walked.answered.state))
    assertWireRoundTrips(walked.events)

  test("an empty pile may be chosen: nothing moves and the line still names " +
      "both"):
    val walked = choosePile(staged.ready, Region.Hinterland)
    assertEquals(pileOf(after(walked.answered), Region.Cradle),
      Vector[WorldCardId](errandBoy))
    assertEquals(NoteText.said(Convoys, walked.answered.events), Vector(
      NoteText.Said(NoteKey.Used,
        "Moved the Hinterland discard pile onto the Cradle discard pile.",
        covers = false)))

  test("the region is the pawn's: from the Provinces it offers the Cradle " +
      "and the Hinterland"):
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, at = giant)
      .denizen(convoys, at = giant).ready
    val t = accepted(use(ready, Convoys, source))
    assertEquals(offered(t, p1), Some(Vector("button" -> "cradle",
      "button" -> "hinterland")))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.ConvoysSuite"`
Expected: compile failure, `Convoys` is not found.

- [ ] **Step 3: Write Convoys**

Remove `object ConvoysCard` from `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala`. `SiteOnly` stays imported there: Spell Breaker, Way Station, Mounted Library and The Gathering use it.

Create `src/main/scala/oathdigital/gameplay/powers/action/Convoys.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object ConvoysCard extends Denizen(DenizenId("151"), "Convoys", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.convoys"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Take another region's discard pile and put it " +
      "on top of your region's discard pile _(even if empty)_.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Convoys (card 151, site-only), ACTION: place 1 favor on this card, then
  * take another region's discard pile and put it on top of your region's,
  * even if it is empty.
  *
  * Your region is the pawn's. The two other regions' piles are offered as
  * buttons with their own labels, in region order: the `DiscardPile` option
  * reads "Discard to the X discard", which asks something else. The pile
  * moves card by card, bottom first, each to the top of the pawn's region's
  * pile, so it keeps its order on top. An empty pile moves nothing, and the
  * line still names both piles. No generic line exists for these moves.
  */
case object Convoys extends PaidAction(ConvoysCard.power):
  val decisionId: String = "power.convoys.pile"
  /** "Moved the {pile} onto the {pile}." */
  val moved: NoteKey = NoteKey(NoteKey.Used, Vector(
    NotePart.Text("Moved the "), NotePart.Arg(0), NotePart.Text(" onto the "),
    NotePart.Arg(1), NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = Vector(moved)
  override def narratedDecisions: Set[String] = Set(decisionId)

  /** The button for `region`'s pile. */
  def ref(region: Region): DecisionOptionRef.Button =
    DecisionOptionRef.Button(region.key)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => move(live, player, pending)),
    Note(id, movedNote(_, player, source)))))

  private def home(ready: ReadyGame, actor: PlayerId): Option[Region] =
    PowerAccess.pawnSite(ready, actor).flatMap(ready.game.current.map.regionOf)

  /** The other regions, in region order. */
  private def others(ready: ReadyGame, actor: PlayerId): Vector[Region] =
    home(ready, actor).toVector.flatMap(at => Region.all.filterNot(_ == at))

  private def pile(region: Region): SearchSource =
    SearchSource.RegionalDiscard(region)

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    others(ready, actor) match
      case Vector() => Vector.empty
      case found => Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
        found.map(region => DecisionOption.Button(ref(region),
          s"Move the ${SearchSource.name(pile(region))}")),
        heading = Some("Convoys: put another region's discard pile on top " +
          "of your region's"))))

  /** The region whose pile `answered` chose. */
  private def chosen(ready: ReadyGame, actor: PlayerId,
      answered: Option[DecisionOptionRef]): Option[Region] =
    answered.flatMap(choice => others(ready, actor).find(ref(_) == choice))

  private def move(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = for
    to <- home(ready, actor).toRight(OathViolation.PawnSiteMissing(actor))
    from <- chosen(ready, actor, PowerAnswers.one(pending, decisionId))
      .toRight(PowerAnswers.missing(decisionId))
  yield ready.game.current.commonCards.discard(from).map(card => Move(
    Piece.Card(card), PositionedLocation(Location.RegionalDiscard(from)),
    PositionedLocation(Location.RegionalDiscard(to), StackPosition.Top)))

  private def movedNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    to <- home(states.now, actor)
    from <- chosen(states.now, actor, NoteSupport.answer(states, decisionId))
  yield moved(card, NoteArg.Pile(pile(from)), NoteArg.Pile(pile(to)))
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`, add `Convoys` to the first `Vector[PhasePower](...)`, after `AncientBinding`.

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `ConvoysCard` to the `gameplay.powers.action` import, after `ClayRattleCard`.

- [ ] **Step 5: Run the suites, and the suites that hold Convoys as filler**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.ConvoysSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite *RuledCardsSuite *LeagueTreatySuite *GreatCrusadeSuite *PledgeOfDefenseSuite *RivalKhanSuite"`
Expected: PASS, 4 tests in `ConvoysSuite`; the rest unchanged.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/Convoys.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/action/ConvoysSuite.scala
git commit -m "feat(action): Convoys puts another region's discard pile on the pawn's"
```

---

### Task 7: Singing Mask

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/SingingMask.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala` (remove `SingingMaskCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `SingingMaskCard`)
- Create: `src/test/scala/oathdigital/gameplay/powers/action/SingingMaskSuite.scala`

**Interfaces:**
- Consumes: `PaidAction`, `SiteCards` (`private[action]`), `NoteSupport.bankPaid`, `PowerAnswers.amount`.
- Produces: `final case class SingingMask`, and its companion with `id`, `cardDecisionId = "power.singing-mask.card"`, `directionDecisionId = "power.singing-mask.direction"`, `amountDecisionId = "power.singing-mask.amount"`, `Most = 2`, `toBanner`, `toBank`, `moved`, `returned`, `nothing` and `forCatalog(catalog: ExecutableCatalog): SingingMask`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/SingingMaskSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class SingingMaskSuite extends munit.FunSuite:
  import PhasePowerHarness._
  import SingingMask.{amountDecisionId, cardDecisionId, directionDecisionId,
    toBanner}

  private val power = SingingMask.forCatalog(catalog)
  private val mask = CatalogNames.relic("Singing Mask")
  private val source = DecisionOptionRef.Relic(mask)
  private val home = CatalogNames.site("Ancient City")
  /** A plain Beast denizen and a plain Hearth one. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val landWarden = CatalogNames.denizen("Land Warden")
  private val chooseErrandBoy: (String, DecisionAnswer) =
    cardDecisionId -> pick(DecisionOptionRef.Denizen(errandBoy))

  /** p1's Act at Ancient City holding Singing Mask, beside Errand Boy. The
    * Beast bank holds 3 favor and every other bank none. p2 holds the
    * People's Favor with `onBanner` favor on it. */
  private def staged(onBanner: Int): Table = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).relic(p1, mask).denizen(errandBoy, at = home)
    .banksHoldingOnly(Suit.Beast -> 3).peoplesFavor(Some(p2), onBanner)

  private def favorOnBanner(ready: ReadyGame): Int =
    ready.game.current.banners.peoplesFavor.favor

  /** Each walk in turn: Singing Mask used, then each answer p1 gave. */
  private def walksAnswering(ready: ReadyGame,
      answers: (String, DecisionAnswer)*): Vector[OathTransition] =
    answers.foldLeft(Vector(accepted(use(ready, power, source)))) {
      case (done, (decision, answered)) =>
        done :+ accepted(answer(done.last, p1, decision, answered))
    }

  test("it places a secret and offers the cards at the site whose bank or " +
      "the People's Favor holds favor"):
    val t = accepted(use(staged(onBanner = 0).denizen(landWarden, at = home)
      .ready, power, source))
    assert(awaits(t, cardDecisionId), parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector("denizen" -> errandBoy.value)))
    assertEquals(Look(after(t)).tokensOn(mask), Tokens(0, 1))

  test("with both stocked it asks the way, then up to 2, and moves favor to " +
      "the People's Favor"):
    val ready = staged(onBanner = 1).ready
    val walks = walksAnswering(ready, chooseErrandBoy,
      directionDecisionId -> pick(toBanner),
      amountDecisionId -> DecisionAnswer.ChooseAmountAnswer(2))
    assert(awaits(walks(1), directionDecisionId),
      parked.parkedDecision(walks(1).state).toString)
    assert(awaits(walks(2), amountDecisionId),
      parked.parkedDecision(walks(2).state).toString)
    assert(backToActing(walks.last))
    val end = after(walks.last)
    assertEquals((Look(end).bankFavor(Suit.Beast), favorOnBanner(end)), (1, 3))
    val events = walks.flatMap(_.events)
    assertEquals(NoteText.said(power, events), Vector(NoteText.Said(
      NoteKey.Used, "Moved 2 favor from the Beast bank to the People's Favor.",
      covers = false)))
    assertEquals(replayed(ready, events), Right(walks.last.state))
    assertWireRoundTrips(events)

  test("with only the People's Favor stocked it moves favor back without " +
      "asking the way, at most what it holds"):
    val chosen = walksAnswering(staged(onBanner = 1).banksHoldingOnly().ready,
      chooseErrandBoy).last
    assert(awaits(chosen, amountDecisionId),
      parked.parkedDecision(chosen.state).toString)
    assert(answer(chosen, p1, amountDecisionId,
      DecisionAnswer.ChooseAmountAnswer(2)).isLeft)
    val done = accepted(answer(chosen, p1, amountDecisionId,
      DecisionAnswer.ChooseAmountAnswer(1)))
    val end = after(done)
    assertEquals((Look(end).bankFavor(Suit.Beast), favorOnBanner(end)), (1, 0))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.returned",
      "Moved 1 favor from the People's Favor to the Beast bank.",
      covers = false)))

  test("moving 0 changes nothing, and the line says so"):
    val walks = walksAnswering(staged(onBanner = 0).ready, chooseErrandBoy,
      amountDecisionId -> DecisionAnswer.ChooseAmountAnswer(0))
    assert(backToActing(walks.last))
    assertEquals(Look(after(walks.last)).bankFavor(Suit.Beast), 3)
    assertEquals(NoteText.said(power, walks.last.events), Vector(
      NoteText.Said("used.none", "No favor moved.", covers = false)))

  test("with nothing to move it asks nothing, and the line says so"):
    val done = accepted(use(staged(onBanner = 0).banksHoldingOnly().ready,
      power, source))
    assert(backToActing(done))
    assertEquals(NoteText.said(power, done.events), Vector(
      NoteText.Said("used.none", "No favor moved.", covers = false)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.SingingMaskSuite"`
Expected: compile failure, `SingingMask` is not found.

- [ ] **Step 3: Write Singing Mask**

Remove `object SingingMaskCard` from `src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/action/SingingMask.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{ExecutableCatalog, PrintedPower, Relic}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object SingingMaskCard extends Relic(RelicId("R28"), "Singing Mask", value = 54, defense = 1):
  val power = PrintedPower(PowerId("relic.singing-mask"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Choose a denizen at your site. Move up to " +
      "[favor] [favor] between the matching favor bank and the " +
      "People's Favor.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Singing Mask (relic R28), ACTION: place 1 secret on this relic, choose a
  * denizen at your site, and move up to 2 favor between the matching favor
  * bank and the People's Favor, whoever holds it.
  *
  * Three questions, read live after the cost. The card: each faceup denizen
  * and ruined edifice at the pawn's site, in site order ([[SiteCards]]),
  * whose suit's bank or the People's Favor holds favor. The direction, only
  * when both hold favor; otherwise the side that holds favor gives. The
  * amount, from 0 to 2, at most what the giving side holds. The move is one
  * `Move`. With no card to choose nothing is asked, and with nothing moved
  * one line says so. No generic line exists for the move.
  */
final case class SingingMask private (catalog: ExecutableCatalog)
    extends PaidAction(SingingMaskCard.power):
  import SingingMask._

  private val siteCards = new SiteCards(catalog, Suit.all.toSet)
  private val banner =
    PositionedLocation(Location.OnBanner(Banner.PeoplesFavor))

  override def noteKeys: Vector[NoteKey] = Vector(moved, returned, nothing)
  override def narratedDecisions: Set[String] =
    Set(cardDecisionId, directionDecisionId, amountDecisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askCard(live, player)),
    Branch((live, pending) => askDirection(live, player, pending)),
    Branch((live, pending) => askAmount(live, player, pending)),
    BuildOps((live, pending) => Right(move(live, player, pending))),
    Note(this.id, movedNote(_, source)))))

  private def stock(ready: ReadyGame, suit: Suit): Int =
    ready.banks.favor.getOrElse(suit, 0)

  private def onBanner(ready: ReadyGame): Int =
    ready.game.current.banners.peoplesFavor.favor

  /** The cards at the pawn's site with favor to move, each with its suit. */
  private def cards(ready: ReadyGame, actor: PlayerId)
      : Vector[(SiteDenizenState, Suit)] =
    PowerAccess.pawnSite(ready, actor).toVector
      .flatMap(siteCards.at(ready, _))
      .filter((_, suit) => stock(ready, suit) > 0 || onBanner(ready) > 0)

  private def askCard(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    cards(ready, actor) match
      case Vector() => Vector.empty
      case found => Vector(Decide(cardDecisionId, actor,
        DecisionQuery.ChooseOne(found.map((card, _) => siteCards.option(card)),
          heading = Some("Singing Mask: choose a card at your site"))))

  /** The chosen card's suit. */
  private def suitChosen(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Option[Suit] = for
    answer <- PowerAnswers.one(pending, cardDecisionId)
    (_, suit) <- cards(ready, actor)
      .find((card, _) => siteCards.refOf(card) == answer)
  yield suit

  /** The ways favor can move for `suit`, toward the People's Favor first. */
  private def directions(ready: ReadyGame, suit: Suit)
      : Vector[DecisionOptionRef.Button] =
    Option.when(stock(ready, suit) > 0)(toBanner).toVector ++
      Option.when(onBanner(ready) > 0)(toBank)

  private def askDirection(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    suitChosen(ready, actor, pending).toVector.flatMap(suit =>
      if directions(ready, suit).size < 2 then Vector.empty
      else Vector(Decide(directionDecisionId, actor, DecisionQuery.ChooseOne(
        Vector(
          DecisionOption.Button(toBanner, "Move favor to the People's Favor"),
          DecisionOption.Button(toBank, s"Move favor to the $suit bank")),
        heading = Some("Singing Mask: choose which way the favor moves")))))

  /** The chosen suit and its way: the only open one, or the answer. */
  private def way(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Option[(Suit, DecisionOptionRef.Button)] =
    suitChosen(ready, actor, pending).flatMap(suit =>
      directions(ready, suit) match
        case Vector(only) => Some(suit -> only)
        case Vector(_, _) => PowerAnswers.one(pending, directionDecisionId)
          .collect { case chosen: DecisionOptionRef.Button
              if chosen == toBanner || chosen == toBank => suit -> chosen }
        case _ => None)

  /** The favor the giving side holds. */
  private def giving(ready: ReadyGame, suit: Suit,
      direction: DecisionOptionRef.Button): Int =
    if direction == toBanner then stock(ready, suit) else onBanner(ready)

  private def askAmount(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    way(ready, actor, pending).toVector.map((suit, direction) =>
      Decide(amountDecisionId, actor, DecisionQuery.ChooseAmount(0,
        math.min(Most, giving(ready, suit, direction)),
        Some("Singing Mask: move up to 2 favor"), "Move favor")))

  private def move(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Vector[CoreOperation] = (for
    (suit, direction) <- way(ready, actor, pending)
    amount <- PowerAnswers.amount(pending, amountDecisionId)
    if amount > 0
    bank = PositionedLocation(Location.FavorBank(suit))
  yield
    if direction == toBanner then Move(Piece.Favor(amount), bank, banner)
    else Move(Piece.Favor(amount), banner, bank)).toVector

  /** The move the step before the note made, or that none did. */
  private def movedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = PowerSourceRef.of(source).map(card =>
    states.previous.flatMap(restated(card, _)).getOrElse(nothing(card)))

  /** Favor onto the People's Favor from the bank that lost it, or off it to
    * the bank that gained it. */
  private def restated(card: PowerSourceRef, step: NoteSupport.Step)
      : Option[PowerNote] =
    val (before, after) = step
    val change = onBanner(after) - onBanner(before)
    if change > 0 then NoteSupport.bankPaid(step).map(suit => moved(card,
      NoteArg.Amount(change, NoteUnit.Favor), NoteArg.Bank(suit),
      NoteArg.Banner(Banner.PeoplesFavor)))
    else if change < 0 then Suit.all.find(suit =>
        stock(after, suit) > stock(before, suit)).map(suit => returned(card,
      NoteArg.Amount(-change, NoteUnit.Favor),
      NoteArg.Banner(Banner.PeoplesFavor), NoteArg.Bank(suit)))
    else None

object SingingMask:
  val id: PowerId = SingingMaskCard.power.id
  val cardDecisionId: String = "power.singing-mask.card"
  val directionDecisionId: String = "power.singing-mask.direction"
  val amountDecisionId: String = "power.singing-mask.amount"
  /** The most favor it moves. */
  val Most: Int = 2
  val toBanner: DecisionOptionRef.Button =
    DecisionOptionRef.Button("to-peoples-favor")
  val toBank: DecisionOptionRef.Button = DecisionOptionRef.Button("to-bank")
  /** "Moved {n favor} from {the Beast bank} to the {People's Favor}." */
  val moved: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Moved "),
    NotePart.Arg(0), NotePart.Text(" from "), NotePart.Arg(1),
    NotePart.Text(" to the "), NotePart.Arg(2), NotePart.Text(".")))
  /** "Moved {n favor} from the {People's Favor} to {the Beast bank}." */
  val returned: NoteKey = NoteKey("used.returned", Vector(
    NotePart.Text("Moved "), NotePart.Arg(0), NotePart.Text(" from the "),
    NotePart.Arg(1), NotePart.Text(" to "), NotePart.Arg(2),
    NotePart.Text(".")))
  /** "No favor moved." */
  val nothing: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No favor moved.")))

  def forCatalog(catalog: ExecutableCatalog): SingingMask =
    new SingingMask(catalog)
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`, append `++ Vector(SingingMask.forCatalog(catalog))` after `Vector(RovingTerror.forCatalog(catalog))`.

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `SingingMaskCard` to the `gameplay.powers.action` import, after `SiegeEnginesCard`.

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.SingingMaskSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite"`
Expected: PASS, 5 tests in `SingingMaskSuite`.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/SingingMask.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/action/SingingMaskSuite.scala
git commit -m "feat(action): Singing Mask moves favor between a bank and the People's Favor"
```

---

### Task 8: Assassin

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Assassin.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala` (remove `AssassinCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `AssassinCard`)
- Create: `src/test/scala/oathdigital/gameplay/powers/action/AssassinSuite.scala`

**Interfaces:**
- Consumes: `PaidAction`, `ContributingPower`, `HolderAdviserLimit` (`gameplay.powers.rest`), `CardPlay.nextRegion`.
- Produces: `final case class Assassin` (a `PaidAction` and a `ContributingPower`) with `limitFor(ready: ReadyGame, player: PlayerId): Option[Int]`, and its companion with `id`, `decisionId = "power.assassin.adviser"`, `HolderLimit = 2`, `discarded`, `nothing` and `forCatalog(catalog: ExecutableCatalog): Assassin`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/AssassinSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PlacementFixture
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{AdviserLimit, NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness,
  PlacementPicks, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class AssassinSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val power = Assassin.forCatalog(catalog)
  private val assassin = CatalogNames.denizen("Assassin")
  private val source = DecisionOptionRef.Denizen(assassin)
  private val home = CatalogNames.site("Ancient City")
  // Denizens whose powers these tests do not use.
  private val elders = CatalogNames.denizen("Elders")
  private val landWarden = CatalogNames.denizen("Land Warden")
  private val wildMounts = CatalogNames.denizen("Wild Mounts")
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val homesteaders = CatalogNames.denizen("Homesteaders")
  /** Locked and adviser-only. */
  private val insomnia = CatalogNames.denizen("Insomnia")
  /** The automatic production powers, as a command with no modifier walks. */
  private val automaticPowers = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)

  /** p1's Act at Ancient City holding Assassin faceup, with the quiet
    * table's 1 favor to pay it. p2 stands there too, holding Land Warden
    * faceup. */
  private def staged: Table = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).adviser(p1, assassin)
    .pawn(p2, at = home).adviser(p2, landWarden)

  /** The walk Assassin started and the walk after `card` was chosen. */
  private case class Chosen(started: OathTransition, answered: OathTransition):
    def events: Vector[OathEvent] = started.events ++ answered.events

  private def chooseAdviser(ready: ReadyGame, card: DenizenId): Chosen =
    val started = accepted(use(ready, power, source))
    Chosen(started, accepted(answer(started, p1, Assassin.decisionId,
      pick(DecisionOptionRef.Denizen(card)))))

  /** The faceup placement options of `card` from p1's hand, under the
    * production powers. */
  private def faceupPlacements(ready: ReadyGame, card: DenizenId)
      : Vector[DecisionOption] =
    val tree = PlacementFixture.build(ready, p1, card)
    PlacementFixture.placementOptions(ready, tree,
      PlacementFixture.park(ready, tree, automaticPowers), automaticPowers)
      .filter(option =>
        CardPlayProcedure.playedAs(option.ref).contains(PlayedAs.Faceup))

  test("it places a favor and offers the faceup advisers of players at the " +
      "site, the actor's own included, not locked, facedown or elsewhere"):
    val ready = staged.adviser(p1, elders)
      .adviser(p2, wildMounts, facedown = true).adviser(p2, insomnia)
      .adviser(p3, errandBoy).ready
    val t = accepted(use(ready, power, source))
    assert(awaits(t, Assassin.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector("denizen" -> elders.value,
      "denizen" -> landWarden.value)))
    assertEquals(Look(after(t)).tokensOn(assassin), Tokens(1, 0))

  test("the adviser is discarded to the next region's pile with its returns"):
    val ready = staged.tokens(landWarden, favor = 1, secrets = 1).ready
    val hearth = Look(ready).bankFavor(Suit.Hearth)
    val walked = chooseAdviser(ready, landWarden)
    assert(backToActing(walked.answered))
    val end = Look(after(walked.answered))
    assertEquals(end.advisers(p2), Vector.empty[CardId])
    assertEquals(end.discardAfter(home).lastOption, Some(landWarden))
    assertEquals(end.bankFavor(Suit.Hearth), hearth + 1)
    assertEquals(end.faceDownSecrets(p1), Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(replayed(ready, walked.events), Right(walked.answered.state))
    assertWireRoundTrips(walked.events)

  test("its line names the card and its owner, covering the Discarded line"):
    val done = chooseAdviser(staged.ready, landWarden).answered
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"Discarded ${landWarden.value} from ${p2.value}'s advisers.",
      covers = true)))

  test("with only the locked Assassin to discard it asks nothing, and the " +
      "line says so"):
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .adviser(p1, assassin).ready
    val done = accepted(use(ready, power, source))
    assert(backToActing(done))
    assertEquals(Look(after(done)).advisers(p1), Vector[CardId](assassin))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", "No faceup adviser could be discarded.", covers = true)))

  test("its holder may have only two advisers: a third faceup adviser " +
      "replaces one, and the locked Assassin cannot go"):
    val ready = Table.start.hand(p1, homesteaders).adviser(p1, assassin)
      .adviser(p1, landWarden, facedown = true).ready
    val faceup = faceupPlacements(ready, homesteaders)
    assertEquals(faceup.filter(_.available).map(PlacementPicks.replaces),
      Vector(Some(landWarden)))
    assertEquals(faceup.filterNot(_.available).map(PlacementPicks.replaces),
      Vector(Some(assassin)))

  test("the limit is its faceup holder's alone"):
    val faceup = Table.start.adviser(p1, assassin).ready
    assertEquals(AdviserLimit.of(catalog, faceup, p1), Assassin.HolderLimit)
    assertEquals(AdviserLimit.of(catalog, faceup, p2), AdviserLimit.Default)
    val facedown = Table.start.adviser(p1, assassin, facedown = true).ready
    assertEquals(AdviserLimit.of(catalog, facedown, p1), AdviserLimit.Default)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.AssassinSuite"`
Expected: compile failure, `Assassin` is not found.

- [ ] **Step 3: Write Assassin**

Remove `object AssassinCard` from `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/action/Assassin.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.gameplay.powers.rest.HolderAdviserLimit
import oathdigital.model._

object AssassinCard extends Denizen(DenizenId("80"), "Assassin", Suit.Discord) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.assassin"),
    persistent = false, cost = Cost(favor = 1),
    text = "You can only have two advisers. **ACTION:** Discard a " +
      "faceup adviser of a player whose pawn is at your site.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Assassin (card 80, adviser-only, locked): "You can only have two
  * advisers. ACTION: Discard a faceup adviser of a player whose pawn is at
  * your site."
  *
  * The limit is a [[HolderAdviserLimit]], as Silver Tongue's and Insomnia's
  * are, so it is registered in the walker catalog too, and `AdviserLimit`
  * reads it. It writes no line.
  *
  * The ACTION places 1 favor on this card, then offers the faceup denizen
  * advisers of every player whose pawn is at the player's site, the
  * player's own included, in seat then adviser order, read live. The chosen
  * card gets the standard discard: to the discard pile of the region after
  * the player's, its favor to its suit's bank and its secrets to the player
  * facedown. The discard is a required batch, so the Locked restriction
  * hides a locked adviser, Assassin among them, and the question passes
  * when nothing is left. With nothing to discard the cost stays paid and
  * one line says so. Its line covers the generic Discarded line.
  */
final case class Assassin private (catalog: ExecutableCatalog)
    extends PaidAction(AssassinCard.power) with ContributingPower:
  import Assassin._

  private val limit = HolderAdviserLimit(AssassinCard.id, HolderLimit)

  def source: RuleSourceRef = RuleSourceRef.GameRule(this.id.value)
  override def noteKeys: Vector[NoteKey] = Vector(discarded, nothing)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.SearchPlayAdviser -> Vector(limit.contribution))

  /** The adviser limit Assassin sets on `player`: its holder, and only
    * while it is faceup. */
  def limitFor(ready: ReadyGame, player: PlayerId): Option[Int] =
    limit.limitFor(ready, player)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => discard(live, player, pending),
      required = true),
    Note(this.id, discardedNote(_, source), covers = true))))

  private final case class Candidate(owner: PlayerId, card: DenizenState)

  /** The faceup denizen advisers of the players at the player's site, the
    * player's own included, in seat then adviser order. */
  private def candidates(ready: ReadyGame, actor: PlayerId): Vector[Candidate] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site => for
      held <- ready.game.current.players if held.pawnSite.contains(site)
      card <- held.advisers.collect {
        case faceup @ DenizenState(_, Orientation.FaceUp, _) => faceup }
    yield Candidate(held.player, card))

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    candidates(ready, actor) match
      case Vector() => Vector.empty
      case found => Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
        found.map(candidate =>
          DecisionOption.Denizen(DecisionOptionRef.Denizen(candidate.card.id))),
        heading = Some("Assassin: discard a faceup adviser of a player at " +
          "your site")), passWhenEmpty = true))

  /** No answer means nothing could be discarded. */
  private def discard(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => for
        chosen <- candidates(ready, actor)
          .find(candidate => DecisionOptionRef.Denizen(candidate.card.id) == ref)
          .toRight(OathViolation.InvalidEventOrder(
            s"${ref.wireId} is not an adviser Assassin can discard"))
        suit <- catalog.suitOf(chosen.card.id)
          .toRight(OathViolation.UnknownWorldCard(chosen.card.id))
        region <- PowerAccess.pawnSite(ready, actor)
          .flatMap(ready.game.current.map.regionOf).map(CardPlay.nextRegion)
          .toRight(OathViolation.PawnSiteMissing(actor))
      yield Vector[CoreOperation](Discard.Denizen(chosen.card.id,
        PositionedLocation(Location.PlayArea(chosen.owner)), region, suit,
        chosen.card.tokens.favor, chosen.card.tokens.secrets, actor,
        required = true))

  /** The card discarded and its owner, read before the discard. */
  private def discardedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = PowerSourceRef.of(source).map(card => (for
    case DecisionOptionRef.Denizen(chosen) <-
      NoteSupport.answer(states, decisionId)
    (before, _) <- states.previous
    owner <- before.game.current.players
      .find(_.advisers.exists(_.id == chosen))
  yield discarded(card, NoteArg.Card(chosen), NoteArg.Player(owner.player)))
    .getOrElse(nothing(card)))

object Assassin:
  val id: PowerId = AssassinCard.power.id
  val decisionId: String = "power.assassin.adviser"
  /** How many advisers the holder may have, in either orientation. */
  val HolderLimit: Int = 2
  /** "Discarded {card} from {Blue}'s advisers." */
  val discarded: NoteKey = NoteKey(NoteKey.Used, Vector(
    NotePart.Text("Discarded "), NotePart.Arg(0), NotePart.Text(" from "),
    NotePart.Arg(1), NotePart.Text("'s advisers.")))
  /** "No faceup adviser could be discarded." */
  val nothing: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No faceup adviser could be discarded.")))

  def forCatalog(catalog: ExecutableCatalog): Assassin =
    new Assassin(catalog)
```

- [ ] **Step 4: Register it and read its limit**

In `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`, append `++ Vector(Assassin.forCatalog(catalog))` after `Vector(RelicThief.forCatalog(catalog))`.

In `src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala`:

- Add `import oathdigital.gameplay.powers.action.Assassin` before the `rest` import.
- Replace the body of `of` with:

  ```scala
  def of(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId): Int =
    Vector(SilverTongue.forCatalog(catalog).limitFor(ready, player),
      Insomnia.limitFor(ready, player),
      Assassin.forCatalog(catalog).limitFor(ready, player))
      .flatten.minOption.getOrElse(Default)
  ```

- In the doc comment, the sentence that reads "which the [[oathdigital.gameplay.powers.rest.HolderAdviserLimit]] of Silver Tongue and of Insomnia narrows" (wrapped over two lines) becomes "... of Silver Tongue, of Insomnia and of Assassin narrows".

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`:

- Add `import oathdigital.gameplay.powers.action.Assassin` before the `banner` import.
- Add `Vector(Assassin.forCatalog(catalog)) ++` on the line after `Vector(Insomnia) ++`.
- In the doc comment, change "Silver Tongue's and Insomnia's adviser limits are inert until Search walks `SearchPlayAdviser`." to "Silver Tongue's, Insomnia's and Assassin's adviser limits are inert until Search walks `SearchPlayAdviser`."

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `AssassinCard` to the `gameplay.powers.action` import, after `ArmedMobCard`.

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.AssassinSuite oathdigital.gameplay.powers.rest.InsomniaSuite oathdigital.gameplay.powers.AdviserLimitSuite *AdviserLimitProjectionSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite *PowerCatalogUniquenessSuite"`
Expected: PASS, 6 tests in `AssassinSuite`; the rest unchanged.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/Assassin.scala \
  src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala \
  src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala \
  src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/action/AssassinSuite.scala
git commit -m "feat(action): Assassin discards a faceup adviser at the site and limits its holder to two"
```

---

### Task 9: Gates and docs

**Files:**
- Modify: `docs/ROADMAP.md`
- Modify: `docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md`

- [ ] **Step 1: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS. The server count is the baseline plus 44; the frontend count is unchanged.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

If a suite outside this slice fails, it most likely holds one of these cards as filler (plan-time ruling 12). Read the failing assertion. Change the filler to another card only if the test is about something else, and say so in the commit message.

- [ ] **Step 2: Update the roadmap**

In `docs/ROADMAP.md`:

- In the status paragraph, change "slices\n1a, 1b and 2 are done." to "slices\n1a, 1b, 2 and 3 are done." Keep the existing line wrap.
- Delete the bullet that starts "- [ ] 3. ACTION powers:" and its continuation line.

- [ ] **Step 3: Make the spec's slice 3 log table exact**

In `docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md`, under "### Slice 3: ACTION powers", replace the table (its header row through the row "| Assassin, key `used.none` | ...") with:

```markdown
| Card | Line | Covers |
|---|---|---|
| Terror Spells | Terror Spells: Killed {n} warbands. | |
| Terror Spells, key `used.none` | Terror Spells: The {Darkest Secret} was not held. | |
| Terror Spells, key `used.empty` | Terror Spells: No warband was in the region. | |
| Witch's Bargain, key `secret`, one line per exchange | Witch's Bargain: Gave {1 secret} to {Blue} and took {n favor}. | |
| Witch's Bargain, key `favor`, one line per exchange | Witch's Bargain: Gave {2 favor} to {Blue} and took {n secrets}. | |
| Witch's Bargain, no exchange made | Witch's Bargain: No exchange was made. | |
| Inquisitor, key `used.<vision>.right` | Inquisitor: Guessed {name} and discarded {card} from {Blue}. | the Discarded and Revealed lines |
| Inquisitor, key `used.<vision>.wrong` | Inquisitor: Guessed {name} for {Blue}'s Vision, wrongly. | |
| Inquisitor, nothing to guess | Inquisitor: No player at the site held a facedown Vision. | |
| Relic Thief | Relic Thief: Rolled no shield and took {relic} from {Blue}. | |
| Relic Thief, key `used.kept` | Relic Thief: Rolled a shield; {Blue} kept {relic}. | |
| Relic Thief, key `used.none` | Relic Thief: No faceup relic could be taken. | |
| Ancient Binding | Ancient Binding: Burned {n secrets}. | |
| Ancient Binding, key `used.none` | Ancient Binding: No secret was burned. | |
| Convoys | Convoys: Moved the {pile} onto the {pile}. | |
| Singing Mask | Singing Mask: Moved {n favor} from {the Beast bank} to the {People's Favor}. | |
| Singing Mask, key `used.returned` | Singing Mask: Moved {n favor} from the {People's Favor} to {the Beast bank}. | |
| Singing Mask, key `used.none` | Singing Mask: No favor moved. | |
| Assassin | Assassin: Discarded {card} from {Blue}'s advisers. | the Discarded line |
| Assassin, key `used.none` | Assassin: No faceup adviser could be discarded. | |
```

Then, in the paragraph below the table, replace "Its `{card}` on a right guess is the discarded Vision, which every viewer then identifies." with "Its `{card}` on a right guess is the discarded Vision, which every viewer then identifies: a `Reveal` in the pile records it as known to every player, and it stays facedown there." Then add this paragraph after that one:

```markdown
A phase power declares a key named `used` (`PowerNoteCatalogSuite`), so a
row with no key above is key `used`: Witch's Bargain's and Inquisitor's
lines for nothing done, and Relic Thief's take. No generic line exists for a
kill, a relic's take, a secret's burn, or favor, secrets and cards moving
between boards, banks, banners and piles, so those rows cover nothing.
```

- [ ] **Step 4: Record the slice's implementation notes in the spec**

In the same file's "Implementation notes from the verification" list, add after the "**Convoys' piles**" bullet:

```markdown
- **Relic Thief checks Lost Tongue itself.** The search stops at a roll, so
  Lost Tongue's refusal of the `Take` would come after the dice and fail the
  command. `LostTongue.protects` leaves such a relic out of the question.
- **Ancient Binding leaves each player at most one secret** on their player
  UI (`PlayerSecretSummary`), which for the acting player also counts every
  secret on a card in play. So the actor burns their board and every secret
  on a card in play, except the one on Ancient Binding; every other player
  burns their board down to one. The Darkest Secret's secrets stay.
- **Inquisitor's right guess** reveals the discarded Vision in its pile. It
  stays facedown there, and every player knows it and can later browse it.
```

- [ ] **Step 5: Commit**

```bash
git add docs/ROADMAP.md docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md
git commit -m "docs: record catalog batch 4 slice 3"
```
