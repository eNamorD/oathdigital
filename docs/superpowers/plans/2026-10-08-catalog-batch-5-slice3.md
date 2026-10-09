# Catalog Batch 5, Slice 3 (Adviser Counting) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Marriage (148), Pied Piper (182) and Lost Tapestry (R29). Marriage counts as two Hearth advisers in every count by suit. Pied Piper and a holder's facedown Visions under Lost Tapestry do not count toward the adviser limit. Pied Piper's ACTION moves it to another player and takes two favor.

**Architecture:**

- **The weighted suit count lives beside `MusterSource`.**
  - `AdviserSuits` and `MarriageCard` go in `gameplay/actions/economy/AdviserSuits.scala`, because Muster and Trade read the count and `gameplay/actions` never imports `gameplay/powers`. `HallOfMinistersCard` in `gameplay/operations` is the precedent for a card object outside powers.
  - `MusterSource.matching` delegates to the count, and a new `RuledCards.count` weighs the cards a ruler rules.
  - The registered Marriage power (`gameplay/powers/economy/Marriage.scala`) contributes nothing. It exists so the card counts as implemented.
- **Card play checks the area as it would be after the play.**
  - `CardPlayRules` drops `playedCardLimit`. It gains:
    - per-card limits, which hold while their card is a faceup adviser
    - a set of adviser faces that do not count
    - `fits(advisers)`
  - `CardSlots.adviserSlots` offers a new slot when the area after it fits. Otherwise it offers each Replace whose result fits. A Replace of an adviser that does not count is shown unavailable with the reason "Does not count toward the adviser limit".
  - `HolderAdviserLimit` (Silver Tongue, Insomnia, Assassin) moves onto the per-card limit, so replacing the limiting card itself is checked without its limit.
- **Exemptions are Transforms at `SearchPlayAdviser`.** Pied Piper marks itself faceup. Lost Tapestry marks its holder's facedown Visions, and a Vision played facedown. `AdviserLimit.ignored` and `AdviserLimit.towardLimit` read the same facts for Horned Mask and the board projection.
- **Pied Piper's ACTION** has Charming Friend's shape: a target question, a required `Give` of the card and a best-effort `Take` of up to 2 favor.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. The frontend gets no change.

**Spec:** Read both documents before starting:

- `docs/superpowers/specs/2026-10-08-catalog-batch-5-design.md`: "The bar", "Slice 3: Adviser counting", "Seams used for the first time" and "Testing".
- `docs/superpowers/specs/2026-10-08-catalog-batch-5-rulings.md`: "Rules that apply to the whole batch" and "Slice 3: Adviser counting".

Task 1 amends both with the rulings made at plan time, so read them after Task 1 too. The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails it.
  - Each file below lists the imports it needs.
  - A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - No power name appears in walker sources.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Log lines omit the actor.** A power's line names only other players, or a player acting off turn.
- **The reason, exactly:** "Does not count toward the adviser limit" (`CardPlayRules.notCounted`).
- **Pied Piper's lines, exactly:**
  - "Moved to {Blue}'s advisers and took {2 favor} from them."
  - "Moved to {Blue}'s advisers. {Blue} had no favor to take."
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 27 server tests and no frontend test.
  - It also rewrites four existing tests: two in `CardPlayRulesSuite` and two in `CardSlotsSuite`.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

The product owner settled these on 2026-10-08. Task 1 writes them into the spec.

1. **Where the count lives.** `AdviserSuits` sits in `gameplay/actions/economy` with `MarriageCard`, and a Marriage power with no contributions is registered so the card counts as implemented.
2. **How card play learns the exemptions.** `CardPlayRules` carries the faces that do not count, set by Transforms at `SearchPlayAdviser`. `AdviserLimit.towardLimit` reads the same facts.
3. **Limits are checked against the state after the play.** Powers related to adviser limits are evaluated against the advisers as they would be once the card is played:
   - Pied Piper played faceup, or a Vision played facedown under Lost Tapestry, takes a free slot even at the limit, and only that slot is offered.
   - A facedown Pied Piper has no power, so it counts.
   - A card that sets a limit (Silver Tongue) sets it only while it is a faceup adviser after the play. Replacing it is checked without its limit.
4. **Horned Mask at full** shows an adviser that does not count unavailable, with the same reason.
5. **The board projection** adds one slot per adviser that does not count, so the empty slots drawn match the free room. This is a sixth approved change outside `gameplay/powers/`.
6. **Pied Piper's ACTION:**
   - Its target is any other player, at any site.
   - The secret paid stays on the card when it moves.
   - The move is a `Give` and the favor a `Take` of up to 2.
   - Its two lines are in Global Constraints.

Also settled at plan time:

7. **The callers sorted.** These move onto the weighted count:
   - `MusterSource.matching`, so Muster and Trade: warbands, favor and secrets.
   - Town Meeting, Fabled Feast and Wizards Conclave, through `RuledCards.count`.
   - Great Crusade and Pledge of Defense, through `GreatCrusade.nomads`.
   - Nature Worship.
   - Military Parade.

   These keep reading cards, because they list targets or check for one: Curfew, Resettle, Twin Brother, Ballot Box, Dissent, Kindred Warriors, Lost Tongue, Diplomat, Disgraced Captain, Battle Axes, Rival Khan, Cracked Sage, Cup of Plenty, Vow of Beastkin, Rotting Fortress, Honor Guard and Second Chance. Tribute Spoils feeds `FavorBySuit` site cards only, and Marriage is adviser-only.
8. **Tests only where Marriage shows.** Marriage is Hearth, so only the Hearth-reading callers get a Marriage test: Muster and Trade through `matching`, Town Meeting, Fabled Feast and Military Parade. Wizards Conclave (Arcane), Great Crusade (Nomad) and Nature Worship (Beast) move onto the helpers with their existing tests as guard. The helpers' own tests pin the weight.
9. **Family Wagon's unbounded limit waits for slice 4**, so the card lands whole. The design's slice 3 paragraph on it moves to slice 4.

---

### Task 1: Write the plan-time rulings into the spec

**Files:**
- Modify: `docs/superpowers/specs/2026-10-08-catalog-batch-5-design.md`
- Modify: `docs/superpowers/specs/2026-10-08-catalog-batch-5-rulings.md`

- [ ] **Step 1: Amend "The bar"**

In the design, replace `The product owner approved five` with `The product owner approved six`. Then replace the bullet

```markdown
- **The adviser limit count** in `CardSlots.adviserSlots` and
  `AdviserLimit.of` (slice 3).
```

with

```markdown
- **The adviser limit count** in `CardPlayRules`, `CardSlots.adviserSlots`
  and `AdviserLimit` (slice 3), and **the weighted suit count**
  `AdviserSuits` beside `MusterSource` (slice 3, placed at plan time).
- **The board's adviser slots** in `GamePresentationProjector` add one slot
  per adviser that does not count (slice 3, approved at plan time).
```

- [ ] **Step 2: Replace the slice 3 section of the design**

Replace everything from `## Slice 3: Adviser counting` up to, not including, `## Slice 4: The adviser arrival pass and Family Wagon` with:

```markdown
## Slice 3: Adviser counting

### The weighted suit count

`AdviserSuits.count(catalog, ready, player, suit)` counts a player's faceup
advisers of a suit, with a faceup Marriage counting as two.
`AdviserSuits.weight(card)` gives the weight. Both live in
`gameplay/actions/economy` beside `MusterSource`, with `MarriageCard`: Muster
and Trade read the count, and `gameplay/actions` never imports
`gameplay/powers`. The registered Marriage power adds nothing; it makes the
card count as implemented.

These callers count and move onto the weight:
- `MusterSource.matching`, so Muster and Trade: warbands, favor and secrets
- Town Meeting, Fabled Feast and Wizards Conclave, through
  `RuledCards.count`
- Great Crusade and Pledge of Defense, through `GreatCrusade.nomads`
- Nature Worship
- Military Parade, which lists a Marriage twice for `FavorBySuit`

These list targets or check for one, and stay on cards: Curfew, Resettle,
Twin Brother, Ballot Box, Dissent, Kindred Warriors, Lost Tongue, Diplomat,
Disgraced Captain, Battle Axes, Rival Khan, Cracked Sage, Cup of Plenty, Vow
of Beastkin, Rotting Fortress, Honor Guard and Second Chance. Tribute Spoils
feeds `FavorBySuit` site cards only, and Marriage is adviser-only.

### The count toward the limit

Limits are checked against the advisers as they would be after the card is
played. `CardPlayRules`:
- keeps `adviserLimit`, a limit whatever is held
- gains per-card limits, `limitWhileFaceUp(card, limit)`, which hold while
  their card is a faceup adviser after the play. Silver Tongue's, Insomnia's
  and Assassin's `HolderAdviserLimit` moves onto it, and `playedCardLimit`
  goes.
- gains the adviser faces that do not count, `ignoreLimit(face)`
- answers `fits(advisers)` for the advisers after a play

`CardSlots.adviserSlots` offers a new slot when the area after it fits, and
only that. Otherwise it offers each Replace whose result fits. A Replace of
an adviser that does not count is shown unavailable with the reason "Does
not count toward the adviser limit", because replacing it frees no room.
With no Replace that fits, it offers nothing.

The faces that do not count:
- Pied Piper, faceup. A facedown Pied Piper has no power and counts.
- The holder's facedown Visions, while the holder has Lost Tapestry faceup.

Each power marks its faces with a Transform at `SearchPlayAdviser`.
`AdviserLimit.ignored` and `AdviserLimit.towardLimit` read the same facts
for Horned Mask and the board. Marriage counts once, as any adviser does.

Horned Mask is full when the count toward the limit reaches the limit. Its
discard question shows an adviser that does not count unavailable, with the
same reason. The board projection adds one slot per adviser that does not
count, so the empty slots drawn match the free room.

### Pied Piper

Pied Piper's ACTION (cost one secret, placed on the card) asks which other
player takes it, any player at any site. It moves Pied Piper, with its
secret, from the actor's advisers into that player's, faceup, with a
required `Give`. It then takes up to two favor from that player with a
`Take`. The receiver may be at or over the limit, since Pied Piper does not
count. `RestrictionIcons` allows the move, because Pied Piper is
adviser-only. Its lines:
- "Moved to {Blue}'s advisers and took {2 favor} from them."
- "Moved to {Blue}'s advisers. {Blue} had no favor to take."
```

- [ ] **Step 3: Move Family Wagon's limit to slice 4**

In the design's slice 4 section `### Family Wagon`, after the paragraph that ends `Family Wagon's line names the buried cards each viewer may identify.`, add:

```markdown
Family Wagon makes its holder's limit unbounded. `AdviserLimit.of` and card
play's rules give its holder no limit before the lowering powers apply. It
waited for this slice so the card lands whole.
```

Then replace `Card play's slots give Family Wagon's holder no limit (slice 3).` with `Card play's slots give Family Wagon's holder no limit.`

- [ ] **Step 4: Replace the slice 3 rulings**

In the rulings, replace everything from `## Slice 3: Adviser counting` up to, not including, `## Slice 4: Adviser arrival` with:

```markdown
## Slice 3: Adviser counting

- **Limits are checked after the play.** Powers related to adviser limits
  are evaluated against the advisers as they would be once the card is
  played. A card that sets a limit, such as Silver Tongue, sets it only
  while it is a faceup adviser after the play.
- **148 Marriage.** Counts as two Hearth advisers in every count of the
  holder's advisers by suit, Muster and Trade matching included. Counts as
  one toward the adviser limit.
- **182 Pied Piper.** Faceup, it does not count toward the adviser limit:
  played faceup at the limit, it takes a free slot. Facedown it has no power
  and counts. Its ACTION moves Pied Piper, with the secret paid for it, into
  any other player's advisers, even when that player is at or over the
  limit, then takes two favor from them, best effort. If the receiver holds
  Family Wagon, the receiver buries Pied Piper (slice 4); the favor is still
  taken.
- **R29 Lost Tapestry.** The holder's facedown Visions do not count toward
  the holder's adviser limit. A Vision played facedown at the limit takes a
  free slot.
- **At the limit,** an adviser that does not count toward the limit is shown
  unavailable for Replace, and for Horned Mask's discard, with the reason
  "Does not count toward the adviser limit".
```

- [ ] **Step 5: Check the links**

Run: `python3 scripts/check-markdown-links.py`
Expected: pass.

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/specs/2026-10-08-catalog-batch-5-design.md docs/superpowers/specs/2026-10-08-catalog-batch-5-rulings.md
git commit -m "docs(spec): settle how slice 3 counts advisers after a play"
```

---

### Task 2: Marriage and the weighted suit count

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/actions/economy/AdviserSuits.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/Marriage.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/economy/MusterSource.scala` (`matching`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/RuledCards.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/TownMeeting.scala`, `FabledFeast.scala`, `WizardsConclave.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/GreatCrusade.scala`, `NatureWorship.scala`, `MilitaryParade.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala` (remove `MarriageCard`)
- Create: `src/test/scala/oathdigital/gameplay/AdviserSuitsSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/MusterProcedureSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/whenplayed/TownMeetingSuite.scala`, `FabledFeastSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/campaign/MilitaryParadeSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Produces:
  - `oathdigital.gameplay.actions.economy.MarriageCard`
  - `AdviserSuits.weight(card: CardId): Int`
  - `AdviserSuits.count(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId, suit: Suit): Int`
  - `RuledCards.count(catalog, ready, ruler: SiteRuler, suit: Suit): Int`
  - `oathdigital.gameplay.powers.economy.Marriage` (a `case object`)

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/AdviserSuitsSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.economy.AdviserSuits
import oathdigital.gameplay.powers.RuledCards
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** `AdviserSuits`: a player's advisers counted by suit, a faceup Marriage
  * counting as two (catalog batch 5 rulings, "Counts as applies
  * everywhere"). */
class AdviserSuitsSuite extends munit.FunSuite:
  test("a faceup Marriage counts as two Hearth advisers, and facedown as " +
      "none"):
    val faceup = Table.start.adviser(p1, "Marriage")
      .adviser(p1, "Homesteaders").ready
    assertEquals(AdviserSuits.count(catalog, faceup, p1, Suit.Hearth), 3)
    assertEquals(AdviserSuits.count(catalog, faceup, p1, Suit.Beast), 0)
    val facedown = Table.start.adviser(p1, "Marriage", facedown = true)
      .adviser(p1, "Homesteaders").ready
    assertEquals(AdviserSuits.count(catalog, facedown, p1, Suit.Hearth), 1)

  test("a ruler's count weighs Marriage, while its list names it once"):
    val ready = Table.start.adviser(p1, "Marriage").ready
    val ruler = SiteRuler.Player(p1)
    val listed = RuledCards.of(catalog, ready, ruler, Suit.Hearth)
    assertEquals(listed.count(_ == CatalogNames.denizen("Marriage")), 1)
    assertEquals(RuledCards.count(catalog, ready, ruler, Suit.Hearth),
      listed.size + 1)
```

In `MusterProcedureSuite.scala`, after the test `matching counts the actor's faceup advisers of the source's suit`, add:

```scala
  test("matching counts a faceup Marriage as two Hearth advisers"):
    assertEquals(MusterSource.matching(catalog,
      atAlchemist.adviser(p1, "Marriage").ready, p1, Suit.Hearth), 2)
```

In `TownMeetingSuite.scala`, at the end, add:

```scala
  test("a faceup Marriage counts as two Hearth cards"):
    // Town Meeting and Marriage make X = 3: the 3 favor across two banks
    // is taken unasked. Counted once, X = 2 would ask for the split.
    val ready = Table.start.adviser(p1, meeting).adviser(p1, "Marriage")
      .favor(p1, 1).banksHoldingOnly(Suit.Order -> 1, Suit.Hearth -> 2).ready
    val done = Outcomes.finished(play(ready, power, meeting))
    assertEquals(Look(done.treeless).favor(p1), 1 + 3)
    assertEquals(said(power, done.events), Vector(gained(3)))
```

In `FabledFeastSuite.scala`, at the end, add:

```scala
  test("a faceup Marriage counts as two Hearth cards"):
    // Fabled Feast and Marriage make X = 3, from the only stocked bank.
    val ready = Table.start.adviser(p1, feast).adviser(p1, "Marriage")
      .favor(p1, 1).banksHoldingOnly(Suit.Order -> 5).ready
    val done = Outcomes.finished(play(ready, power, feast))
    assertEquals(Look(done.treeless).favor(p1), 1 + 3)
    assertEquals(done.treeless.banks.favor(Suit.Order), 5 - 3)
    assertEquals(said(power, done.events), Vector(took(3, Suit.Order)))
```

In `MilitaryParadeSuite.scala`, add the field `private val marriage = planlessCard("Marriage")` after `arcane`, and at the end add:

```scala
  test("an enemy's faceup Marriage gives two Hearth favor"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withAdviser(withAdviserFor(base, base.rival, marriage,
      Orientation.FaceUp), card, Orientation.FaceUp)
    val before = OathState.Ready(b.ready)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assertEquals(Look(done.state).favor(b.actor),
      Look(before).favor(b.actor) + 2)
    assertEquals(Look(done.state).bankFavor(Suit.Hearth),
      Look(before).bankFavor(Suit.Hearth) - 2)
```

The test assumes the Hearth bank in `board(...)` holds at least 2 favor, as the Order bank does for the first test. If it holds fewer, stop and ask; do not lower the expected amount.

In `PowerKindsCatalogSuite.scala`, add `"denizen.marriage"` to the end of `persistentRules`, after `"denizen.vow-of-wandering"`.

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.AdviserSuitsSuite oathdigital.gameplay.MusterProcedureSuite oathdigital.gameplay.powers.whenplayed.TownMeetingSuite oathdigital.gameplay.powers.whenplayed.FabledFeastSuite oathdigital.gameplay.powers.campaign.MilitaryParadeSuite"`
Expected: compile failure: `AdviserSuits` and `RuledCards.count` are not found.

- [ ] **Step 3: Create the count and move the card**

Delete `object MarriageCard ...` (its six lines) from `HoldingHearthDenizens.scala`. Create `src/main/scala/oathdigital/gameplay/actions/economy/AdviserSuits.scala`:

```scala
package oathdigital.gameplay.actions.economy

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.model._

object MarriageCard extends Denizen(DenizenId("148"), "Marriage", Suit.Hearth) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.marriage"),
    persistent = true, cost = Cost.free,
    text = "Marriage counts as two [suit-hearth] advisers, but only " +
      "counts as one toward your adviser limit.")
  val powers: Vector[PrintedPower] = Vector(power)

/** A player's advisers counted by suit (catalog batch 5 rulings, "Counts as
  * applies everywhere"). A faceup Marriage counts as two advisers of its
  * suit, any other faceup adviser as one, and a facedown adviser has no
  * suit. A list of targets, or a check for having one adviser of a suit,
  * reads the cards themselves.
  *
  * It lives beside `MusterSource` because Muster and Trade read it, and
  * `gameplay/actions` never imports `gameplay/powers`. This is Marriage's
  * whole rule: its registered power adds nothing.
  */
object AdviserSuits:
  /** How many advisers of its suit `card` counts as, faceup. */
  def weight(card: CardId): Int = if card == MarriageCard.id then 2 else 1

  /** `player`'s faceup advisers of `suit`, each by its weight. */
  def count(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      suit: Suit): Int =
    ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.advisers).collect {
        case DenizenState(id, Orientation.FaceUp, _)
            if catalog.suitOf(id).contains(suit) => weight(id)
      }.sum
```

In `MusterSource.scala`, replace the body and doc of `matching`:

```scala
  /** The actor's faceup advisers of `suit`, a Marriage counting as two
    * (`AdviserSuits`): how many warbands, favor or secrets a Muster or Trade
    * adds to its base yield.
    */
  def matching(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      suit: Suit): Int = AdviserSuits.count(catalog, state, actor, suit)
```

In `RuledCards.scala`, add the import `import oathdigital.gameplay.actions.economy.AdviserSuits` and append to the object:

```scala

  /** How many cards of `suit` `ruler` rules, a faceup Marriage counting as
    * two (`AdviserSuits.weight`). */
  def count(catalog: ExecutableCatalog, ready: ReadyGame, ruler: SiteRuler,
      suit: Suit): Int =
    of(catalog, ready, ruler, suit).map(AdviserSuits.weight).sum
```

- [ ] **Step 4: Move the counting callers onto it**

- `TownMeeting.scala` and `FabledFeast.scala`: replace
  ```scala
      val count = RuledCards.of(catalog, live, SiteRuler.Player(actor),
        Suit.Hearth).size
  ```
  with
  ```scala
      val count = RuledCards.count(catalog, live, SiteRuler.Player(actor),
        Suit.Hearth)
  ```
  In FabledFeast's doc comment, replace `X is read live with `RuledCards.of`` with `X is read live with `RuledCards.count``, and rewrap the paragraph to 80 columns.
- `WizardsConclave.scala`, in `leaders`: replace
  ```scala
      RuledCards.of(catalog, ready, SiteRuler.Player(seat.player),
        Suit.Arcane).size)
  ```
  with
  ```scala
      RuledCards.count(catalog, ready, SiteRuler.Player(seat.player),
        Suit.Arcane))
  ```
- `GreatCrusade.scala`, in `nomads`: replace `context.cardsRuled(catalog, context.ruler, Suit.Nomad).count(_ != card) + 1` with
  ```scala
    context.cardsRuled(catalog, context.ruler, Suit.Nomad).filter(_ != card)
      .map(AdviserSuits.weight).sum + 1
  ```
  Add `import oathdigital.gameplay.actions.economy.AdviserSuits`.
- `NatureWorship.scala`, in `otherBeasts`: replace the `.count { ... }` block with
  ```scala
      .flatMap(_.advisers).collect {
        case DenizenState(held, Orientation.FaceUp, _)
            if held != cardId && catalog.suitOf(held).contains(Suit.Beast) =>
          AdviserSuits.weight(held)
      }.sum
  ```
  Add the same import.
- `MilitaryParade.scala`: replace `advisers` and its doc with
  ```scala
  /** `player`'s faceup advisers, a Marriage listed twice since it counts as
    * two (`AdviserSuits.weight`). */
  private def advisers(ready: ReadyGame, player: PlayerId): Vector[CardId] =
    ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.advisers.collect {
        case DenizenState(held, Orientation.FaceUp, _) => held })
      .flatMap(held => Vector.fill(AdviserSuits.weight(held))(held))
  ```
  Add the same import.

- [ ] **Step 5: Create and register the Marriage power**

Create `src/main/scala/oathdigital/gameplay/powers/economy/Marriage.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.MarriageCard
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution}
import oathdigital.model._

/** Marriage (card 148, adviser-only, locked): "Marriage counts as two Hearth
  * advisers, but only counts as one toward your adviser limit."
  *
  * The rule is `AdviserSuits.weight`, which every count of a player's
  * advisers by suit reads, and the limit counts every adviser once. This
  * power hooks no window. It is registered so the card counts as
  * implemented. It writes no line.
  */
case object Marriage extends ContributingPower:
  val id: PowerId = MarriageCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
```

In `WalkerPowerCatalog.scala`, add `import oathdigital.gameplay.powers.economy.Marriage` (merge it into the existing `economy.KnightsErrant` import as `economy.{KnightsErrant, Marriage}`), and add `Vector(Marriage) ++` after `Vector(KnightsErrant.forCatalog(catalog)) ++`.

In `NewFoundations.scala`, import `MarriageCard` from `oathdigital.gameplay.actions.economy`. The wildcard `catalog.holding._` no longer supplies it. Keep its place in the denizen list.

- [ ] **Step 6: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.AdviserSuitsSuite oathdigital.gameplay.MusterProcedureSuite oathdigital.gameplay.powers.whenplayed.* oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.cards.CardCatalogSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS. `CardCatalogSuite` now finds Marriage implemented outside the holding files.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/economy/AdviserSuits.scala src/main/scala/oathdigital/gameplay/powers/economy/Marriage.scala src/main/scala/oathdigital/gameplay/actions/economy/MusterSource.scala src/main/scala/oathdigital/gameplay/powers/RuledCards.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/TownMeeting.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/FabledFeast.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/WizardsConclave.scala src/main/scala/oathdigital/gameplay/powers/campaign/GreatCrusade.scala src/main/scala/oathdigital/gameplay/powers/campaign/NatureWorship.scala src/main/scala/oathdigital/gameplay/powers/campaign/MilitaryParade.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala src/test/scala/oathdigital/gameplay/AdviserSuitsSuite.scala src/test/scala/oathdigital/gameplay/MusterProcedureSuite.scala src/test/scala/oathdigital/gameplay/powers/whenplayed/TownMeetingSuite.scala src/test/scala/oathdigital/gameplay/powers/whenplayed/FabledFeastSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/MilitaryParadeSuite.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(economy): Marriage counts as two advisers of its suit"
```

---

### Task 3: Card play checks the area after the play

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/CardPlayRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/rest/HolderAdviserLimit.scala`
- Modify: `src/test/scala/oathdigital/gameplay/CardPlayRulesSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/CardSlotsSuite.scala`

**Interfaces:**
- Produces, in `oathdigital.gameplay.actions`:
  - `final case class AdviserFace(card: CardId, orientation: Orientation)`, with `AdviserFace.of(adviser: AdviserState): AdviserFace`
  - `CardPlayRules.limitWhileFaceUp(card: CardId, limit: Int): CardPlayRules`
  - `CardPlayRules.ignoreLimit(face: AdviserFace): CardPlayRules`
  - `CardPlayRules.limitOver(advisers: Vector[AdviserFace]): Int`, `counted(advisers): Int`, `fits(advisers): Boolean`
  - `CardPlayRules.notCounted: String`, "Does not count toward the adviser limit"
- Removes: `playedCardLimit`, `limitPlayedCard`, `limitAfter`.

- [ ] **Step 1: Rewrite the rules tests**

In `CardPlayRulesSuite.scala`:
- Change the import `import oathdigital.gameplay.actions.CardPlayRules` to `import oathdigital.gameplay.actions.{AdviserFace, CardPlayRules}`.
- Add the fields after `card`:
  ```scala
  private val tongue = CatalogNames.denizen("Silver Tongue")
  private val piper = CatalogNames.denizen("Pied Piper")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private def faceup(held: CardId) = AdviserFace(held, Orientation.FaceUp)
  private def facedown(held: CardId) = AdviserFace(held, Orientation.FaceDown)
  ```
- Replace the tests `one limit counts every adviser, faceup or facedown, and only lowers` and `the played card's own limit counts only when it is played faceup` with:

```scala
  test("one limit counts every adviser, faceup or facedown, and only lowers"):
    val rules = CardPlayRules.default.limitAdvisers(2)
    assert(rules.fits(Vector(faceup(card), facedown(tongue))))
    assert(!rules.fits(Vector(faceup(card), facedown(tongue),
      facedown(wrestlers))))
    assertEquals(CardPlayRules.default.limitAdvisers(5), CardPlayRules.default)

  test("a card's limit holds only while it is a faceup adviser after the " +
      "play"):
    // The rulebook checks a play against the state after it. Silver Tongue
    // facedown has no power, and a Silver Tongue the play replaces is gone.
    val rules = CardPlayRules.default.limitWhileFaceUp(tongue, 2)
    assertEquals(rules.limitOver(Vector(faceup(tongue), faceup(card))), 2)
    assertEquals(rules.limitOver(Vector(facedown(tongue), faceup(card))),
      CardPlayRules.DefaultAdviserLimit)
    assertEquals(rules.limitOver(Vector(faceup(card))),
      CardPlayRules.DefaultAdviserLimit)
    assertEquals(rules.limitWhileFaceUp(tongue, 3), rules)

  test("an adviser face that ignores the limit is not counted, in that " +
      "face only"):
    val rules = CardPlayRules.default.ignoreLimit(faceup(piper))
    val four = Vector(faceup(piper), faceup(card), facedown(tongue),
      faceup(wrestlers))
    assertEquals(rules.counted(four), 3)
    assert(rules.fits(four))
    assert(!rules.fits(four.updated(0, facedown(piper))))
```

`CardPlayRulesSuite` already imports `oathdigital.model._`, which gives `CardId` and `Orientation`.

- [ ] **Step 2: Rewrite and add the slot tests**

In `CardSlotsSuite.scala`:
- Change the first import to `import oathdigital.gameplay.actions.{AdviserFace, CardPlay, CardPlayRules, RuleNotes, VisionRules}`, wrapped to 80 columns.
- Add the fields after `curfew`:
  ```scala
  /** Faceup, it does not count toward the adviser limit. */
  private val piper = CatalogNames.denizen("Pied Piper")
  private val ignoresPiper = CardPlayRules.default
    .ignoreLimit(AdviserFace(piper, Orientation.FaceUp))
  ```
- In `the played card's own limit lowers only its faceup plays`, replace `CardPlayRules.default.limitPlayedCard(2)` with `CardPlayRules.default.limitWhileFaceUp(card, 2)`. Rename the test to `"the played card's own limit holds only when it is played faceup"`.
- Replace the test `a facedown adviser turned faceup must fit its own limit` with:

```scala
  test("a facedown adviser turned faceup must fit its own limit"):
    def ownLimit(held: DenizenId) =
      CardPlayRules.default.limitWhileFaceUp(held, 2)
    val three = Vector(rainBoots, binding, wrestlers)
      .foldLeft(atAncientCity)(_.adviser(p1, _, facedown = true)).ready
    assertEquals(advisers(slotsOf(three, wrestlers,
      CardPlay.Origin.FacedownAdviser, ownLimit(wrestlers)), faceup = true),
      Vector.empty)
    val two = Vector(rainBoots, binding)
      .foldLeft(atAncientCity)(_.adviser(p1, _, facedown = true)).ready
    assertEquals(advisers(slotsOf(two, binding,
      CardPlay.Origin.FacedownAdviser, ownLimit(binding)), faceup = true),
      Vector(adviserPlay(1, faceup = true)))
```

- After the test `the played card's own limit holds only when it is played faceup`, add:

```scala
  test("a played card that will not count takes a free slot at the limit, " +
      "and only that slot"):
    val full = holding(rainBoots, binding, wrestlers).hand(p1, piper).ready
    val slots = slotsOf(full, piper, rules = ignoresPiper)
    assertEquals(advisers(slots, faceup = true),
      Vector(adviserPlay(3, faceup = true)))
    // Facedown it has no power, so it counts.
    assertEquals(advisers(slots, faceup = false), Vector(
      replacing(adviserPlay(0, faceup = false), rainBoots),
      replacing(adviserPlay(1, faceup = false), binding),
      replacing(adviserPlay(2, faceup = false), wrestlers)))

  test("at the limit, an adviser that does not count is shown unavailable " +
      "for Replace"):
    val ready = atAncientCity.adviser(p1, piper)
      .adviser(p1, rainBoots, facedown = true)
      .adviser(p1, binding, facedown = true)
      .adviser(p1, wrestlers, facedown = true).hand(p1, card).ready
    assertEquals(advisers(slotsOf(ready, rules = ignoresPiper),
      faceup = true), Vector(
      refused(replacing(adviserPlay(0, faceup = true), piper),
        "Does not count toward the adviser limit"),
      replacing(adviserPlay(1, faceup = true), rainBoots),
      replacing(adviserPlay(2, faceup = true), binding),
      replacing(adviserPlay(3, faceup = true), wrestlers)))

  test("a play that replaces the card setting the limit is checked without " +
      "that limit"):
    // Wrestlers, faceup, limits p1 to two, and p1 holds three: only
    // replacing Wrestlers leaves an area within the limit.
    val rules = CardPlayRules.default.limitWhileFaceUp(wrestlers, 2)
    val ready = atAncientCity.adviser(p1, wrestlers)
      .adviser(p1, rainBoots, facedown = true)
      .adviser(p1, binding, facedown = true).hand(p1, card).ready
    assertEquals(advisers(slotsOf(ready, rules = rules), faceup = true),
      Vector(replacing(adviserPlay(0, faceup = true), wrestlers)))
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.CardPlayRulesSuite oathdigital.gameplay.CardSlotsSuite"`
Expected: compile failure: `AdviserFace`, `limitWhileFaceUp` and `ignoreLimit` are not members.

- [ ] **Step 4: Rewrite `CardPlayRules`**

In `CardPlayRules.scala`, replace the doc comment and the class, from `/** The rules a card play is planned under.` through `def permitSiteDiscard ...` (keep the object `CardPlayRules` below it), with:

```scala
/** An adviser as card play counts it: the card and the face it shows. */
final case class AdviserFace(card: CardId, orientation: Orientation)

object AdviserFace:
  def of(adviser: AdviserState): AdviserFace = adviser match
    case DenizenState(id, orientation, _) => AdviserFace(id, orientation)
    case VisionState(id, orientation) => AdviserFace(id, orientation)

/** The rules a card play is planned under. A power changes them through the
  * `SearchPlayAdviser` window, and card play reads nothing else, so the play
  * never names a power.
  *
  * The rulebook checks a play against the state after it (catalog batch 5
  * rulings, "Limits are checked after the play"). Card play therefore asks
  * `fits` of the advisers as they would be after each possible play.
  *  - `adviserLimit` is a limit on all of the player's advisers, whatever
  *    they hold.
  *  - `cardLimits` are the limits a card sets while it is a faceup adviser
  *    (Silver Tongue). A facedown card has no power, and a replaced card is
  *    gone, so neither sets its limit.
  *  - `uncounted` are the adviser faces that do not count toward the limit
  *    (a faceup Pied Piper; a facedown Vision under Lost Tapestry).
  *  - `siteDiscards` are the permissions to discard a card from the site a
  *    card is played to, one card each. They stack. The Homeland rule's is
  *    not among them: card play adds it at the Homeland of the played card's
  *    suit (`homeland`).
  *
  * Every change lowers a limit, exempts a face or adds a permission, so
  * contributions compose in any order.
  */
final case class CardPlayRules(
    adviserLimit: Int = CardPlayRules.DefaultAdviserLimit,
    cardLimits: Map[CardId, Int] = Map.empty,
    uncounted: Set[AdviserFace] = Set.empty,
    siteDiscards: Vector[DiscardPermission] = Vector.empty):

  /** The limit over `advisers`, the advisers after a play: `adviserLimit`,
    * lowered by each faceup card that sets a limit. */
  def limitOver(advisers: Vector[AdviserFace]): Int =
    advisers.collect { case AdviserFace(card, Orientation.FaceUp) => card }
      .flatMap(cardLimits.get).foldLeft(adviserLimit)(math.min)

  /** How many of `advisers` count toward the limit. */
  def counted(advisers: Vector[AdviserFace]): Int =
    advisers.count(face => !uncounted(face))

  /** Whether `advisers`, the advisers after a play, keep within the limit. */
  def fits(advisers: Vector[AdviserFace]): Boolean =
    counted(advisers) <= limitOver(advisers)

  /** Lowers the player's limit to at most `limit`. */
  def limitAdvisers(limit: Int): CardPlayRules =
    copy(adviserLimit = math.min(adviserLimit, limit))

  /** `card` sets a limit of `limit` while it is a faceup adviser. */
  def limitWhileFaceUp(card: CardId, limit: Int): CardPlayRules =
    copy(cardLimits = cardLimits.updated(card,
      cardLimits.get(card).fold(limit)(math.min(_, limit))))

  /** `face` does not count toward the limit. */
  def ignoreLimit(face: AdviserFace): CardPlayRules =
    copy(uncounted = uncounted + face)

  /** Adds one site discard. */
  def permitSiteDiscard(permission: DiscardPermission): CardPlayRules =
    copy(siteDiscards = siteDiscards :+ permission)
```

In `object CardPlayRules`, after `val default`, add:

```scala

  /** Why a Replace of an adviser that does not count is unavailable:
    * replacing it frees no room. */
  val notCounted: String = "Does not count toward the adviser limit"
```

- [ ] **Step 5: Check the slots after the play**

In `CardSlots.scala`:
- Change the import to `import oathdigital.gameplay.actions.{AdviserFace, CardPlay, CardPlayRules, DiscardPermission, VisionRules}`, wrapped to 80 columns.
- In the object's doc comment, replace the sentence `An adviser play offers the first empty slot below the limit or, with none, every held adviser marked Replace, and nothing at or past the limit.` with `An adviser play is checked against the advisers as they would be after it (`CardPlayRules.fits`): it offers a new slot when that fits, and only that; or else each held adviser marked Replace whose replacement fits, an adviser that does not count shown unavailable with its reason; and nothing when no replacement fits.` Rewrap to 80 columns.
- Replace `adviserSlots` with:

```scala
  private def adviserSlots(player: PlayerState, card: WorldCardId,
      origin: CardPlay.Origin, orientation: Orientation, rules: CardPlayRules)
      : Vector[(DecisionOption, SearchPlacement)] =
    val held = player.advisers
    val faces = held.map(AdviserFace.of)
    val played = AdviserFace(card, orientation)
    def slot(index: Int): DecisionOption = DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(player.player, index,
        orientation == Orientation.FaceUp))
    origin match
      case CardPlay.Origin.FacedownAdviser =>
        // The card turns faceup where it lies.
        val own = held.indexWhere(_.id == card)
        if own < 0 || !rules.fits(faces.updated(own, played)) then Vector.empty
        else Vector(slot(own) -> SearchPlacement.Adviser(orientation, None))
      case CardPlay.Origin.TemporaryHand =>
        if rules.fits(faces :+ played) then
          Vector(slot(held.size) -> SearchPlacement.Adviser(orientation, None))
        else replacements(faces, played, rules).map((index, fits) =>
          val option = DecisionOption.Replacing(slot(index), held(index).id)
          (if fits then option else DecisionOption.Unavailable(option,
            OptionReason.Written(CardPlayRules.notCounted))) ->
            SearchPlacement.Adviser(orientation, Some(held(index).id)))

  /** The held advisers a play with no free slot may replace, by index, and
    * whether the area after the replacement fits. One that does not fit is
    * kept only when it does not count, to be shown unavailable: replacing it
    * frees no room. Nothing when no replacement fits. */
  private def replacements(faces: Vector[AdviserFace], played: AdviserFace,
      rules: CardPlayRules): Vector[(Int, Boolean)] =
    val fitting = faces.indices.toVector.map(index =>
      index -> rules.fits(faces.updated(index, played)))
    if !fitting.exists(_._2) then Vector.empty
    else fitting.filter((index, fits) =>
      fits || rules.uncounted(faces(index)))
```

- In `judged`, keep an option that is already unavailable as it is. Replace its first line `planned.toOption.flatMap { operations =>` with:

```scala
    if !option.available then Some(option)
    else planned.toOption.flatMap { operations =>
```

and indent the rest of the method body by two spaces to match.

- [ ] **Step 6: Move `HolderAdviserLimit` onto the per-card limit**

In `HolderAdviserLimit.scala`, replace the doc comment's sentences from `While `cardId` is faceup in the acting player's advisers` through `so playing it faceup needs room under the limit.` with:

```scala
  * It is a transform at `SearchPlayAdviser`. While the acting player holds
  * `cardId`, in either face, or plays it, it sets `cardId`'s limit
  * (`CardPlayRules.limitWhileFaceUp`). Card play checks the advisers as they
  * would be after the play, so the limit holds only while the card is a
  * faceup adviser then: a facedown play sets none, and replacing the card
  * lifts it.
```

Keep `limitFor` as it is. Replace `val contribution` with:

```scala
  /** Whether `player` holds `cardId` among their advisers, in either face. */
  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player)
      .exists(_.advisers.exists(_.id == cardId))

  val contribution: Contribution = Transform((ctx, children) =>
    ctx.operation match {
      case tree: CardPlayProcedure.PlacementTree
          if tree.card == cardId || holds(ctx.state, ctx.activePlayer) =>
        tree.adjust(children)(_.limitWhileFaceUp(cardId, limit))
      case _ => children
    })
```

- [ ] **Step 7: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.CardPlayRulesSuite oathdigital.gameplay.CardSlotsSuite oathdigital.gameplay.powers.rest.* oathdigital.gameplay.powers.action.AssassinSuite oathdigital.gameplay.powers.wake.HornedMaskSuite"`
Expected: PASS. The Silver Tongue, Insomnia and Assassin suites pass unchanged.

If a suite name above does not exist, drop it from the command. Then run `./sbtw test` to find any other reader of the removed members, and stop and ask if one turns up outside the files listed.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/CardPlayRules.scala src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala src/main/scala/oathdigital/gameplay/powers/rest/HolderAdviserLimit.scala src/test/scala/oathdigital/gameplay/CardPlayRulesSuite.scala src/test/scala/oathdigital/gameplay/CardSlotsSuite.scala
git commit -m "feat(cardplay): check a play against the advisers after it"
```

---

### Task 4: Lost Tapestry

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/cardplay/LostTapestry.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala` (remove `LostTapestryCard`)
- Create: `src/test/scala/oathdigital/gameplay/powers/cardplay/LostTapestrySuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/AdviserLimitSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes: `AdviserFace`, `CardPlayRules.ignoreLimit`, `CardPlayRules.notCounted` (Task 3).
- Produces:
  - `LostTapestry.ignored(ready: ReadyGame, player: PlayerId): Vector[AdviserFace]`
  - `AdviserLimit.ignored(ready: ReadyGame, player: PlayerId): Vector[AdviserFace]`
  - `AdviserLimit.towardLimit(ready: ReadyGame, player: PlayerId): Int`

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/cardplay/LostTapestrySuite.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.{CardPlayRules, VisionRules}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Unwrap
import oathdigital.testkit.Unwrap.{accepted, present}

class LostTapestrySuite extends munit.FunSuite:
  private val actor = Table.p1
  private val tapestry = CatalogNames.relic("Lost Tapestry")
  private val faith = VisionRules.Faith
  private val plain = Vector("Threatening Roar", "Fae Merchant", "Wrestlers")
    .map(CatalogNames.denizen(_))
  /** A plain denizen in hand. */
  private val played = CatalogNames.denizen("Errand Boy")

  /** p1's Act at Ancient City, holding Lost Tapestry (facedown when
    * `facedownTapestry`), `held` as facedown advisers and `card` in hand. */
  private def holding(card: WorldCardId, held: Vector[WorldCardId],
      facedownTapestry: Boolean = false): ReadyGame =
    held.foldLeft(Table.start.turn(actor, Phase.Act)
      .pawn(actor, "Ancient City")
      .relic(actor, tapestry, facedown = facedownTapestry)
      .hand(actor, card))(_.adviser(actor, _, facedown = true)).ready

  /** The placement options of `card` from hand, with Lost Tapestry's power
    * in force, that play it `as`. */
  private def placementOptions(ready: ReadyGame, card: WorldCardId,
      as: PlayedAs): Vector[DecisionOption] =
    val tree = accepted(CardPlayProcedure.build(catalog, ready, actor, card,
      CardPlayProcedure.Origin.TemporaryHand))
    val powers = WalkerPowers(Vector(LostTapestry))
    val parked = Unwrap.as[WalkerOutcome.Parked](accepted(
      ProcedureWalker.advance(ready, tree, None, powers))).tree
    Unwrap.as[DecisionQuery.ChooseOne](present(
      ProcedureWalker.parkedDecide(ready, tree, parked, powers),
      "the placement decision").query).options
      .filter(option => CardPlayProcedure.playedAs(option.ref).contains(as))

  private def adviserPlay(index: Int, faceup: Boolean): DecisionOption =
    DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(actor, index, faceup))

  test("a Vision played facedown at the limit takes a free slot"):
    val ready = holding(faith, plain)
    assertEquals(placementOptions(ready, faith, PlayedAs.Facedown),
      Vector(adviserPlay(3, faceup = false)))

  test("a held facedown Vision is shown unavailable for Replace at the " +
      "limit"):
    val ready = holding(played, faith +: plain)
    assertEquals(placementOptions(ready, played, PlayedAs.Faceup), Vector(
      DecisionOption.Unavailable(DecisionOption.Replacing(
        adviserPlay(0, faceup = true), faith),
        OptionReason.Written(CardPlayRules.notCounted))) ++
      plain.zipWithIndex.map((card, index) => DecisionOption.Replacing(
        adviserPlay(index + 1, faceup = true), card)))

  test("a facedown Lost Tapestry keeps nothing from counting"):
    val ready = holding(faith, plain, facedownTapestry = true)
    assertEquals(placementOptions(ready, faith, PlayedAs.Facedown),
      plain.zipWithIndex.map((card, index) => DecisionOption.Replacing(
        adviserPlay(index, faceup = false), card)))
```

The parameter `as` would shadow `Unwrap.as`, so the suite calls it as `Unwrap.as`.

In `AdviserLimitSuite.scala`, add the imports `import oathdigital.gameplay.actions.{AdviserFace, VisionRules}` and `import oathdigital.model.Orientation`, then at the end add:

```scala
  test("a Lost Tapestry holder's facedown Visions do not count toward the " +
      "limit, and only while it is faceup"):
    val faith = VisionRules.Faith
    def held(facedownTapestry: Boolean) = Table.start
      .relic(p1, "Lost Tapestry", facedown = facedownTapestry)
      .adviser(p1, faith, facedown = true).adviser(p1, "Wrestlers").ready
    assertEquals(AdviserLimit.ignored(held(false), p1),
      Vector(AdviserFace(faith, Orientation.FaceDown)))
    assertEquals(AdviserLimit.towardLimit(held(false), p1), 1)
    assertEquals(AdviserLimit.towardLimit(held(true), p1), 2)
```

In `PowerKindsCatalogSuite.scala`, add `"relic.lost-tapestry"` to `persistentRules`.

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.LostTapestrySuite oathdigital.gameplay.powers.AdviserLimitSuite"`
Expected: compile failure: `LostTapestry`, `AdviserLimit.ignored` and `AdviserLimit.towardLimit` are not found.

- [ ] **Step 3: Create the card and its power**

Delete `object LostTapestryCard ...` (its six lines) from `HoldingRelics.scala`. Create `src/main/scala/oathdigital/gameplay/powers/cardplay/LostTapestry.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.gameplay.actions.AdviserFace
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Transform}
import oathdigital.model._

object LostTapestryCard extends Relic(RelicId("R29"), "Lost Tapestry", value = 85, defense = 1):
  val power = PrintedPower(PowerId("relic.lost-tapestry"),
    persistent = true, cost = Cost.free,
    text = "Facedown Visions do not count against your adviser limit.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Lost Tapestry (relic R29): "Facedown Visions do not count against your
  * adviser limit."
  *
  * While its holder has it faceup, a transform at `SearchPlayAdviser` marks
  * the holder's facedown Visions, and a Vision the holder plays, as not
  * counting facedown (`CardPlayRules.ignoreLimit`). Card play checks the
  * advisers as they would be after the play, so a Vision played facedown
  * takes a free slot at the limit, and a held one is shown unavailable for
  * Replace. `AdviserLimit` reads `ignored` for Horned Mask and the board.
  * It writes no line.
  */
case object LostTapestry extends ContributingPower:
  val id: PowerId = LostTapestryCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case tree: CardPlayProcedure.PlacementTree
            if holds(ctx.state, ctx.activePlayer) =>
          val played = tree.card match
            case vision: VisionId =>
              Vector(AdviserFace(vision, Orientation.FaceDown))
            case _ => Vector.empty
          tree.adjust(children)(rules =>
            (ignored(ctx.state, ctx.activePlayer) ++ played)
              .foldLeft(rules)(_.ignoreLimit(_)))
        case _ => children
      })))

  /** Whether `player` has Lost Tapestry faceup. */
  def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player)
      .exists(_.relics.exists {
        case RelicState(card, Orientation.FaceUp, _) =>
          card == LostTapestryCard.id
        case _ => false
      })

  /** The advisers of `player` that Lost Tapestry keeps from counting: their
    * facedown Visions, while they have it faceup. */
  def ignored(ready: ReadyGame, player: PlayerId): Vector[AdviserFace] =
    if !holds(ready, player) then Vector.empty
    else ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.advisers.collect {
        case VisionState(vision, Orientation.FaceDown) =>
          AdviserFace(vision, Orientation.FaceDown)
      })
```

- [ ] **Step 4: Teach `AdviserLimit` the count toward the limit**

In `AdviserLimit.scala`, add the imports `import oathdigital.gameplay.actions.AdviserFace` and `import oathdigital.gameplay.powers.cardplay.LostTapestry`. Append to the object:

```scala

  /** The advisers `player` holds that do not count toward the limit, as card
    * play's rules mark them: a Lost Tapestry holder's facedown Visions. */
  def ignored(ready: ReadyGame, player: PlayerId): Vector[AdviserFace] =
    LostTapestry.ignored(ready, player)

  /** How many of `player`'s advisers count toward the limit. Marriage counts
    * once, as any adviser does. */
  def towardLimit(ready: ReadyGame, player: PlayerId): Int =
    ready.game.current.players.find(_.player == player)
      .fold(0)(_.advisers.size) - ignored(ready, player).size
```

Add a sentence to the object's doc comment: ``ignored` and `towardLimit` read the facts by which card play's rules leave an adviser out of the count (Lost Tapestry, Pied Piper).`

- [ ] **Step 5: Register it and import the card**

In `WalkerPowerCatalog.scala`, add `import oathdigital.gameplay.powers.cardplay.LostTapestry` (merge it with the existing `cardplay.CardPlayTriggers` import as `cardplay.{CardPlayTriggers, LostTapestry}`). Add `Vector(LostTapestry) ++` after `Vector(Assassin.forCatalog(catalog)) ++`. Add a line to the doc comment's paragraph about adviser limits: `Lost Tapestry's and Pied Piper's exemptions are inert until Search walks `SearchPlayAdviser` too.`

In `NewFoundations.scala`, import `LostTapestryCard` from `oathdigital.gameplay.powers.cardplay`. Keep its place in the relic list.

- [ ] **Step 6: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.LostTapestrySuite oathdigital.gameplay.powers.AdviserLimitSuite oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.cards.CardCatalogSuite"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/cardplay/LostTapestry.scala src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala src/test/scala/oathdigital/gameplay/powers/cardplay/LostTapestrySuite.scala src/test/scala/oathdigital/gameplay/powers/AdviserLimitSuite.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(cardplay): Lost Tapestry keeps facedown Visions off the limit"
```

---

### Task 5: Pied Piper

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/PiedPiper.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala` (remove `PiedPiperCard`)
- Create: `src/test/scala/oathdigital/gameplay/powers/action/PiedPiperSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/AdviserLimitSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes: `AdviserFace`, `CardPlayRules.ignoreLimit`, `CardPlayRules.notCounted` (Task 3); `AdviserLimit.ignored` (Task 4).
- Produces:
  - `PiedPiperCard`
  - `PiedPiper` (a `case object`, `PaidAction with ContributingPower`), with `decisionId = "power.pied-piper.target"`, `Taken = 2`, the note keys `moved` and `broke`, and `ignored(ready, player): Vector[AdviserFace]`

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/action/PiedPiperSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.CardPlayRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap
import oathdigital.testkit.Unwrap.{accepted, present}

class PiedPiperSuite extends munit.FunSuite:
  import PowerFixture._
  import PhasePowerHarness._

  private val piper = CatalogNames.denizen("Pied Piper")
  private val source = DecisionOptionRef.Denizen(piper)
  private val receiver = Table.p2
  private val plain = Vector("Threatening Roar", "Fae Merchant", "Wrestlers")
    .map(CatalogNames.denizen(_))
  /** A plain denizen in hand. */
  private val played = CatalogNames.denizen("Errand Boy")

  private def favorOf(ready: ReadyGame, id: PlayerId = actor) =
    Look(ready).player(id).board.favor
  private def choose(id: PlayerId) = pick(DecisionOptionRef.Player(id))

  /** The actor's Act at Ancient City holding `held` as facedown advisers
    * after `first`, and `card` in hand. */
  private def playing(card: DenizenId, first: Table => Table,
      held: Vector[DenizenId]): ReadyGame =
    held.foldLeft(first(Table.start.turn(actor, Phase.Act)
      .pawn(actor, "Ancient City").hand(actor, card)))(
      _.adviser(actor, _, facedown = true)).ready

  /** The placement options of `card` from hand, with Pied Piper's power in
    * force, that play it `as`. */
  private def placementOptions(ready: ReadyGame, card: DenizenId,
      as: PlayedAs): Vector[DecisionOption] =
    val tree = accepted(CardPlayProcedure.build(catalog, ready, actor, card,
      CardPlayProcedure.Origin.TemporaryHand))
    val powers = WalkerPowers(Vector(PiedPiper))
    val parked = Unwrap.as[WalkerOutcome.Parked](accepted(
      ProcedureWalker.advance(ready, tree, None, powers))).tree
    Unwrap.as[DecisionQuery.ChooseOne](present(
      ProcedureWalker.parkedDecide(ready, tree, parked, powers),
      "the placement decision").query).options
      .filter(option => CardPlayProcedure.playedAs(option.ref).contains(as))

  private def adviserPlay(index: Int, faceup: Boolean): DecisionOption =
    DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(actor, index, faceup))

  private def replacingPlain(faceup: Boolean, from: Int)
      : Vector[DecisionOption] = plain.zipWithIndex.map((card, index) =>
    DecisionOption.Replacing(adviserPlay(index + from, faceup), card))

  /** The actor's Act at Ancient City holding Pied Piper faceup and one
    * secret. The receiver stands at Buried Giant, another site, with
    * `favor`. */
  private def staged(favor: Int = 3, secrets: Int = 1) =
    Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .adviser(actor, piper).secrets(actor, secrets)
      .pawn(receiver, "Buried Giant").favor(receiver, favor)

  private def movedTo(ready: ReadyGame) =
    val t = accepted(use(ready, PiedPiper, source))
    (t, accepted(answer(t, actor, PiedPiper.decisionId, choose(receiver))))

  test("played faceup at the limit it takes a free slot; facedown it needs " +
      "Replace"):
    val ready = playing(piper, identity, plain)
    assertEquals(placementOptions(ready, piper, PlayedAs.Faceup),
      Vector(adviserPlay(3, faceup = true)))
    assertEquals(placementOptions(ready, piper, PlayedAs.Facedown),
      replacingPlain(faceup = false, from = 0))

  test("held faceup at the limit, it is shown unavailable for Replace"):
    val ready = playing(played, _.adviser(actor, piper), plain)
    assertEquals(placementOptions(ready, played, PlayedAs.Faceup),
      DecisionOption.Unavailable(DecisionOption.Replacing(
        adviserPlay(0, faceup = true), piper),
        OptionReason.Written(CardPlayRules.notCounted)) +:
        replacingPlain(faceup = true, from = 1))

  test("held facedown, it counts toward the limit"):
    val ready = playing(played, _.adviser(actor, piper, facedown = true),
      plain.take(2))
    assertEquals(placementOptions(ready, played, PlayedAs.Faceup),
      DecisionOption.Replacing(adviserPlay(0, faceup = true), piper) +:
        plain.take(2).zipWithIndex.map((card, index) =>
          DecisionOption.Replacing(adviserPlay(index + 1, faceup = true),
            card)))

  test("its ACTION places a secret on it and offers every other player"):
    val ready = staged().ready
    val t = accepted(use(ready, PiedPiper, source))
    assert(awaits(t, PiedPiper.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, actor).map(_.toSet), Some(
      ready.game.current.players.map(_.player).filter(_ != actor)
        .map(other => "player" -> other.value).toSet))
    assertEquals(Look(after(t)).tokensOn(piper), Tokens(0, 1))

  test("the chosen player receives it with its secret and gives two favor"):
    val ready = staged().ready
    val (t, done) = movedTo(ready)
    assertEquals(Look(after(done)).advisers(receiver), Vector[CardId](piper))
    assertEquals(Look(after(done)).advisers(actor), Vector.empty)
    assertEquals(Look(after(done)).tokensOn(piper), Tokens(0, 1))
    assertEquals(favorOf(after(done), receiver), 1)
    assertEquals(favorOf(after(done)), favorOf(ready) + 2)
    assertEquals(NoteText.said(PiedPiper, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"Moved to ${receiver.value}'s advisers " +
        "and took 2 favor from them.", covers = false)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("a receiver at the limit still receives it"):
    val ready = plain.foldLeft(staged())(_.adviser(receiver, _)).ready
    val (_, done) = movedTo(ready)
    assertEquals(Look(after(done)).advisers(receiver), plain :+ piper)

  test("it takes what favor the receiver has, and says when there is none"):
    val (_, short) = movedTo(staged(favor = 1).ready)
    assertEquals(favorOf(after(short), receiver), 0)
    val ready = staged(favor = 0).ready
    val (_, broke) = movedTo(ready)
    assertEquals(favorOf(after(broke)), favorOf(ready))
    assertEquals(NoteText.said(PiedPiper, broke.events), Vector(
      NoteText.Said("used.empty", s"Moved to ${receiver.value}'s advisers. " +
        s"${receiver.value} had no favor to take.", covers = false)))

  test("it is unusable without a secret to place"):
    val ready = staged(secrets = 0).ready
    assert(!usableNow(ready).exists(_.power.id == PiedPiper.id))
    assertEquals(use(ready, PiedPiper, source).left.toOption,
      Some(OathViolation.InsufficientSecrets(1, 0)))
```

If the moved card arrives without its secret, stop and ask: the ruling says the secret travels with it.

In `AdviserLimitSuite.scala`, add at the end:

```scala
  test("a faceup Pied Piper does not count toward its holder's limit; " +
      "facedown it does"):
    val piper = CatalogNames.denizen("Pied Piper")
    def held(facedown: Boolean) = Table.start
      .adviser(p1, piper, facedown = facedown).adviser(p1, "Wrestlers").ready
    assertEquals(AdviserLimit.ignored(held(false), p1),
      Vector(AdviserFace(piper, Orientation.FaceUp)))
    assertEquals(AdviserLimit.towardLimit(held(false), p1), 1)
    assertEquals(AdviserLimit.towardLimit(held(true), p1), 2)
```

In `PowerKindsCatalogSuite.scala`, after the test `Vow of Obedience's rule is automatic whatever its flag: the flag is false because of its REST`, add:

```scala
  test("Pied Piper's limit is automatic whatever its flag: the flag is " +
      "false because of its ACTION"):
    assertEquals(flag("denizen.pied-piper"), Some(false))
    assertEquals(oathdigital.gameplay.powers.action.PiedPiper.resolution,
      PowerResolution.Automatic)
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.PiedPiperSuite oathdigital.gameplay.powers.AdviserLimitSuite"`
Expected: compile failure: `PiedPiper` is not found.

- [ ] **Step 3: Create the card and its power**

Delete `object PiedPiperCard ...` (its seven lines) from `HoldingBeastDenizens.scala`. Create `src/main/scala/oathdigital/gameplay/powers/action/PiedPiper.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{AdviserOnly, Denizen, PrintedPower}
import oathdigital.gameplay.actions.AdviserFace
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Transform}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object PiedPiperCard extends Denizen(DenizenId("182"), "Pied Piper", Suit.Beast) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.pied-piper"),
    persistent = false, cost = Cost(secret = 1),
    text = "This card ignores the adviser limit. **ACTION:** Move this " +
      "card to any other player's advisers. Take [favor] [favor] " +
      "from them.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Pied Piper (card 182, adviser-only): "This card ignores the adviser
  * limit. ACTION: Move this card to any other player's advisers. Take
  * [favor] [favor] from them."
  *
  * Faceup it does not count toward its holder's limit. A transform at
  * `SearchPlayAdviser` marks it (`CardPlayRules.ignoreLimit`) whenever the
  * acting player holds it or plays it, and card play checks the advisers as
  * they would be after the play. Facedown it has no power and counts.
  * `AdviserLimit` reads `ignored`.
  *
  * The ACTION places 1 secret on this card, then asks which other player
  * takes it: any player, at any site. The card moves into their advisers
  * with its secret, even past their limit, since it does not count there
  * either. Then up to 2 favor is taken from them, best effort. The move is
  * a required `Give`, so a restriction that refuses it fails the action
  * rather than skipping it.
  */
case object PiedPiper extends PaidAction(PiedPiperCard.power)
    with ContributingPower:
  val decisionId: String = "power.pied-piper.target"
  val Taken: Int = 2
  /** "Moved to {Blue}'s advisers and took {2 favor} from them." */
  val moved: NoteKey = NoteKey(NoteKey.Used, Vector(
    NotePart.Text("Moved to "), NotePart.Arg(0),
    NotePart.Text("'s advisers and took "), NotePart.Arg(1),
    NotePart.Text(" from them.")))
  /** "Moved to {Blue}'s advisers. {Blue} had no favor to take." */
  val broke: NoteKey = NoteKey("used.empty", Vector(
    NotePart.Text("Moved to "), NotePart.Arg(0),
    NotePart.Text("'s advisers. "), NotePart.Arg(1),
    NotePart.Text(" had no favor to take.")))

  def source: RuleSourceRef = RuleSourceRef.GameRule(this.id.value)
  override def noteKeys: Vector[NoteKey] = Vector(moved, broke)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case tree: CardPlayProcedure.PlacementTree
            if tree.card == PiedPiperCard.id ||
              holds(ctx.state, ctx.activePlayer) =>
          tree.adjust(children)(_.ignoreLimit(
            AdviserFace(PiedPiperCard.id, Orientation.FaceUp)))
        case _ => children
      })))

  /** The advisers of `player` that Pied Piper keeps from counting: itself,
    * faceup. */
  def ignored(ready: ReadyGame, player: PlayerId): Vector[AdviserFace] =
    advisers(ready, player).collect {
      case DenizenState(card, Orientation.FaceUp, _)
          if card == PiedPiperCard.id =>
        AdviserFace(card, Orientation.FaceUp)
    }

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => lead(live, player, pending), required = true),
    Note(this.id, ledNote(_, player, source)))))

  private def advisers(ready: ReadyGame, player: PlayerId)
      : Vector[AdviserState] =
    ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.advisers)

  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    advisers(ready, player).exists(_.id == PiedPiperCard.id)

  private def receivers(ready: ReadyGame, actor: PlayerId)
      : Vector[PlayerState] =
    ready.game.current.players.filter(_.player != actor)

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      receivers(ready, actor).map(p =>
        DecisionOption.Player(DecisionOptionRef.Player(p.player))),
      heading = Some("Pied Piper: move it to another player's advisers"))))

  private def lead(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    for
      ref <- PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId))
      receiver <- receivers(ready, actor)
        .find(p => DecisionOptionRef.Player(p.player) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not a player Pied Piper can move to"))
    yield
      val give = Give(Piece.Card(PiedPiperCard.id), actor,
        Location.PlayArea(actor), Location.PlayArea(receiver.player),
        required = true)
      val taken = math.min(Taken, receiver.board.favor)
      if taken == 0 then Vector(give)
      else Vector(give, Take(Piece.Favor(taken), actor,
        Location.PlayArea(receiver.player), Location.PlayArea(actor)))

  private def ledNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).flatMap(card =>
      NoteSupport.answer(states, decisionId).collect {
        case DecisionOptionRef.Player(receiver) =>
          val amount = states.previous.fold(0)(NoteSupport.favor(_, actor))
          if amount > 0 then moved(card, NoteArg.Player(receiver),
            NoteArg.Amount(amount, NoteUnit.Favor))
          else broke(card, NoteArg.Player(receiver), NoteArg.Player(receiver))
      })
```

If `Decide` or `BuildOps` takes its parameters under other names than Charming Friend's and Assassin's use above, follow those two files. They are the pattern.

- [ ] **Step 4: Let `AdviserLimit` read Pied Piper too**

In `AdviserLimit.scala`, change the import `import oathdigital.gameplay.powers.action.Assassin` to `import oathdigital.gameplay.powers.action.{Assassin, PiedPiper}`. Replace the body of `ignored` with `PiedPiper.ignored(ready, player) ++ LostTapestry.ignored(ready, player)`, and its doc with `The advisers `player` holds that do not count toward the limit, as card play's rules mark them: a faceup Pied Piper, and a Lost Tapestry holder's facedown Visions.`

- [ ] **Step 5: Register it and import the card**

- `OtherActionPowers.scala`: add `PiedPiper` to the end of the `Vector[PhasePower](SpoiledSupplies, CharmingFriend, ...)` list.
- `WalkerPowerCatalog.scala`: change the import to `import oathdigital.gameplay.powers.action.{Assassin, PiedPiper}`, and add `Vector(PiedPiper) ++` after `Vector(LostTapestry) ++`.
- `NewFoundations.scala`: import `PiedPiperCard` from `oathdigital.gameplay.powers.action`. Keep its place in the denizen list.

- [ ] **Step 6: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.PiedPiperSuite oathdigital.gameplay.powers.AdviserLimitSuite oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.cards.CardCatalogSuite oathdigital.gameplay.BackendArchitectureSuite"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/PiedPiper.scala src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala src/test/scala/oathdigital/gameplay/powers/action/PiedPiperSuite.scala src/test/scala/oathdigital/gameplay/powers/AdviserLimitSuite.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(powers): Pied Piper ignores the adviser limit and leads itself away"
```

---

### Task 6: Horned Mask and the board read the count

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala`
- Modify: `src/main/scala/oathdigital/application/GamePresentationProjector.scala` (line ~206)
- Modify: `src/test/scala/oathdigital/gameplay/powers/wake/HornedMaskSuite.scala`
- Modify: `src/test/scala/oathdigital/application/AdviserLimitProjectionSuite.scala`

**Interfaces:**
- Consumes: `AdviserLimit.ignored`, `AdviserLimit.towardLimit`, `AdviserFace.of`, `CardPlayRules.notCounted`.

- [ ] **Step 1: Write the failing tests**

In `HornedMaskSuite.scala`, add the field `private val piper = CatalogNames.denizen("Pied Piper")` after `tongue`, and after the test `a player who is not the Silver Tongue holder keeps the limit of three` add:

```scala
  test("a Pied Piper holder is full at three other advisers, and Pied " +
      "Piper is shown unavailable for the discard"):
    val ready = holding(piper, elders, fresh, wolves)
    val asked = takeInn(accepted(use(ready, power, source)))
    assert(awaits(asked, HornedMask.discardDecisionId),
      parked.parkedDecision(asked.state).toString)
    assertEquals(queryOf(asked, actor).map(_.offeredOptions
      .map(option => (option.id, option.state, option.reason))), Some(Vector(
      (piper.value, "unavailable",
        Some("Does not count toward the adviser limit")),
      (elders.value, "available", None), (fresh.value, "available", None),
      (wolves.value, "available", None))))

  test("Pied Piper beside two other advisers leaves room"):
    val ready = holding(piper, elders, fresh)
    val done = takeInn(accepted(use(ready, power, source)))
    parked.assertNotParked(done.state)
    assertEquals(Look(after(done)).advisers(actor).toSet,
      Set[CardId](piper, elders, fresh, inn))
```

In `AdviserLimitProjectionSuite.scala`, add the import `import oathdigital.gameplay.actions.VisionRules`, change `Table.{p1, p2}` to `Table.{p1, p2, p3}`, and add at the end:

```scala
  test("a board draws one more slot for each adviser that does not count"):
    val ready = Table.start.adviser(p1, "Pied Piper")
      .relic(p2, "Lost Tapestry")
      .adviser(p2, VisionRules.Faith, facedown = true).ready
    val limits = presentation.playerBoards(ready, Some(p1))
      .map(board => board.playerId -> board.adviserLimit).toMap
    assertEquals(limits(p1.value), 4)
    assertEquals(limits(p2.value), 4)
    assertEquals(limits(p3.value), 3)
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.wake.HornedMaskSuite oathdigital.application.AdviserLimitProjectionSuite"`
Expected: FAIL. Horned Mask asks with Pied Piper available, and leaves no room beside two others. The boards show 3.

- [ ] **Step 3: Read the count in Horned Mask**

In `HornedMask.scala`:
- Add `import oathdigital.gameplay.actions.{AdviserFace, CardPlayRules}`.
- Replace `full`:

```scala
  private def full(ready: ReadyGame, actor: PlayerId): Boolean =
    AdviserLimit.towardLimit(ready, actor) >=
      AdviserLimit.of(catalog, ready, actor)
```

- In `askDiscard`, replace `advisers(ready, actor).flatMap(a => DecisionOption.forRef(ref(a))),` with `discardable(ready, actor),`, and add:

```scala
  /** The discard question's options: every adviser, one that does not count
    * toward the limit shown unavailable, since discarding it frees no room.
    */
  private def discardable(ready: ReadyGame, actor: PlayerId)
      : Vector[DecisionOption] =
    val ignored = AdviserLimit.ignored(ready, actor).toSet
    advisers(ready, actor).flatMap(adviser =>
      DecisionOption.forRef(ref(adviser)).map(option =>
        if ignored(AdviserFace.of(adviser)) then DecisionOption.Unavailable(
          option, OptionReason.Written(CardPlayRules.notCounted))
        else option))
```

- In the class doc, where it says that "Full" is the player's adviser limit, add: `Full counts the advisers toward the limit (`AdviserLimit.towardLimit`), and an adviser that does not count is shown unavailable for the discard.` Rewrap to 80 columns.

- [ ] **Step 4: Draw the free room on the board**

In `GamePresentationProjector.scala`, replace `AdviserLimit.of(catalog, ready, player.player))` with:

```scala
        // An adviser that does not count takes a slot past the limit.
        AdviserLimit.of(catalog, ready, player.player) +
          AdviserLimit.ignored(ready, player.player).size)
```

- [ ] **Step 5: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.wake.HornedMaskSuite oathdigital.application.AdviserLimitProjectionSuite"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala src/main/scala/oathdigital/application/GamePresentationProjector.scala src/test/scala/oathdigital/gameplay/powers/wake/HornedMaskSuite.scala src/test/scala/oathdigital/application/AdviserLimitProjectionSuite.scala
git commit -m "feat(powers): Horned Mask and the board count advisers toward the limit"
```

---

### Task 7: Record the slice and run the gates

**Files:**
- Modify: `docs/ROADMAP.md` ("Now" paragraph; Phase - Catalog batch 5)

- [ ] **Step 1: Run the gates**

Run, in order:
1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass, with 27 server tests more than the baseline and the same frontend count.

- [ ] **Step 2: Update the roadmap**

In `docs/ROADMAP.md`:

- Delete the bullet `- [ ] **Slice 3: Adviser counting.** Marriage, Pied Piper, Lost Tapestry.` under "### Phase - Catalog batch 5".
- In "## Now", replace `**Phase - Catalog batch 5** is in progress: slices 1` / `and 2 are done.` (it spans two lines) with `**Phase - Catalog batch 5** is in progress: slices 1 to 3 are done.`, wrapped as the paragraph is.

Run `python3 scripts/check-markdown-links.py` again. Expected: pass.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: record catalog batch 5 slice 3"
```
