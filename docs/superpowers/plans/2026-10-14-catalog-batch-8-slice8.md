# Catalog Batch 8, Slice 8 (Effective Suit) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Pledge to Discord (223) and Spiteful Mirror (R41).

**Architecture:**

- **One effective suit.** A new `oathdigital.gameplay.EffectiveSuit` replaces `AdviserSuits`.
  - `EffectiveSuit.of` gives a card's suit as the rules see it: Discord for a faceup adviser of a player who has Pledge to Discord faceup, otherwise the printed suit.
  - `EffectiveSuit.traded` gives the suit of a Trade's or Muster's source card: Discord when the walk's answers pick Spiteful Mirror, otherwise `of`.
  - `EffectiveSuit.count` counts a player's faceup advisers of a suit, Marriage as two (`weight`).
  - Every reader of an adviser's suit goes through `of`, including the bank that favor on an adviser returns to. Every reader of a traded or mustered card's suit goes through `traded`.
  - `EffectiveSuit` lives in `gameplay`, not in `gameplay/actions` or `gameplay/powers`, because the operation pipeline reads it too. It holds the three card objects its rules name: Marriage, Pledge to Discord and Spiteful Mirror.
- **Off-turn settlement.** `PayCostSettlement` settles an off-turn payment onto a Pledge holder's adviser to the Discord bank. The pipeline has no catalog, and Pledge needs none: the card is Discord whatever it prints.
- **Family Wagon and Vow of Union ask when Pledge is buried.** `SuitOnlyAdvisers` becomes an `ExecutedTrigger` (slice 6).
  - When a holder of the rule also has Pledge faceup, and some faceup advisers keep the rule by their printed suit, the arrival rule buries only the advisers that go whatever the order.
  - After the batch, the trigger asks the active player one Partition question: which of those advisers to bury before Pledge. It then buries them, then Pledge. The rest revert to their printed suit and stay.
- **Spiteful Mirror** is a selected Trade and Muster modifier.
  - Its first pick in a turn pays 1 secret onto the relic and records `RecordPowerUse`. Later picks that turn are free.
  - It posts "Acting as if {card} is a Discord card." once the source is chosen.
  - `SelectedModifier.selectionPayments` stops being `final` so the Mirror can override it.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`, sections:
  - "Goal and scope";
  - "The bar" (the slice 6 and slice 8 bullets);
  - "Slice 8: Effective suit";
  - "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: "Rules that apply to the whole batch" and the slice 8 entries.

Task 1 amends both with this plan's rulings. Read them again after Task 1.

**Starts from:** `main` with slice 7 done, at `d2c51dd6` or later. Since slice 7, refactors have changed imports: `NewFoundations` and `WalkerPowerCatalog` import the powers packages by wildcard.

Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it.
  - Each new file below lists the imports it needs.
  - When a card moves out of a holding file, remove any import that file no longer uses.
  - A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). Check with `wc -l`.
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
  - `oathdigital.gameplay.EffectiveSuit` imports neither `gameplay/actions` nor `gameplay/powers`. It may import `gameplay.powerresolver.ModifierPicks`.
- **Tree addresses.** No procedure tree gains, loses or moves a node. Spiteful Mirror's line is a `Note` a transform adds inside an existing window. Family Wagon's question is a trigger follow-up, which the walker addresses under the batch's own node.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`. An untracked `src/test/scala/oathdigital/gameplay/HiddenScopeProbeSuite.scala` belongs to another session; leave it alone.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Names, exactly:**
  - File `src/main/scala/oathdigital/gameplay/EffectiveSuit.scala`, package `oathdigital.gameplay`:
    - `object MarriageCard`, moved from `gameplay/actions/economy/AdviserSuits.scala`;
    - `object PledgeToDiscordCard`, moved from `catalog/holding/HoldingDiscordDenizens.scala`;
    - `object SpitefulMirrorCard`, moved from `catalog/holding/HoldingRelics.scala`;
    - `object EffectiveSuit` with:
      - `def weight(card: CardId): Int`;
      - `def pledged(ready: ReadyGame, card: CardId): Boolean`;
      - `def of(catalog: ExecutableCatalog, ready: ReadyGame, card: CardId): Option[Suit]`;
      - `def count(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId, suit: Suit): Int`;
      - `def mirrored(answered: Vector[Answered]): Boolean`;
      - `def traded(catalog: ExecutableCatalog, ready: ReadyGame, answered: Vector[Answered], ref: DecisionOptionRef): Option[Suit]`.
  - `MusterSource.resolve` gains a last parameter, `answered: Vector[Answered] = Vector.empty`.
  - `EnemyPlans.suitOf(catalog: ExecutableCatalog, ready: ReadyGame, ref: DecisionOptionRef): Option[Suit]`.
  - `FavorBySuit.counts(catalog: ExecutableCatalog, ready: ReadyGame, cards: Vector[CardId]): Vector[(Suit, Int)]`.
  - In `oathdigital.gameplay.powers.economy`:
    - file `PledgeToDiscord.scala`: `case object PledgeToDiscord extends ContributingPower`;
    - file `SpitefulMirror.scala`: `final case class SpitefulMirror` and `object SpitefulMirror` with `val id`, `val useRef: PowerUseRef`, `val acting: NoteKey` and `def forCatalog`.
  - In `SuitOnlyAdvisers`:
    - the trait extends `ExecutedTrigger` and gains `def ruleName: String` and `def pledgeDecisionId: String`;
    - the object gains `val buryKey: String = "bury"`, `val keepKey: String = "keep"` and `def pledgeHeading(ruleName: String, suit: Suit, holderAnswers: Boolean): String`.
  - `FamilyWagon.pledgeDecisionId = "power.family-wagon.pledge"`; `VowOfUnionAdvisers.pledgeDecisionId = "power.vow-of-union.pledge"`.

---

## Rulings made at plan time

The product owner settled these on 2026-10-14. Task 1 writes them into the design and the rulings appendix.

1. **Faceup only.** Pledge to Discord changes only its holder's faceup advisers. A facedown adviser still has no suit, and a facedown Pledge changes nothing.
2. **Pledge reaches every reader of the holder's adviser suits.** Marriage counts as two Discord. This goes further than the design's list. For example:
   - Honor Guard can target the holder, who has no Order adviser.
   - Second Chance can target the holder, who has a Discord adviser.
   - Resettle and Twin Brother cannot take a Nomad adviser of the holder.
   - Military Parade pays out of the Discord bank only.
   - Rotting Fortress, Nature Worship, Rival Khan, Cracked Sage, True Names, Ballot Box, Hospitality, Vow of Beastkin and every `RuledCards` reader see Discord.

   Silver Tongue and the travel payments read only site cards, so Pledge never reaches them.
3. **Favor going back from a Pledge holder's adviser** goes to the Discord bank: at Rest cleanup, in an off-turn cost settlement, and when the adviser is discarded or buried. The bank is read while the card is still the holder's adviser.
4. **A card played faceup to a Pledge holder's adviser slot reads Discord.** Wild Cry and Saddle Makers therefore do not fire for it. A card played to a site, and the Homeland check, read its printed suit. Family Wagon's warning on an adviser play is unchanged, because a Family Wagon holder never keeps Pledge.
5. **The client shows printed suits.** The projection is unchanged.
6. **Spiteful Mirror turns the source card Discord, and only it.** Favor comes from the Discord bank, the Discord advisers match, and every reader of the traded or mustered card sees Discord: Defame, Downtrodden, Cup of Plenty, Animal Playmates, Birdsong, Vow of Beastkin and Curfew. It applies to Muster and to both Trades. A Trade for secrets counts the Discord advisers.
7. **Spiteful Mirror's payment.**
   - Its first pick in a turn pays 1 secret onto the relic and records `RecordPowerUse(PowerUseRef(Act, Card(R41), relic.spiteful-mirror))`.
   - Later picks that turn are offered free.
   - When the player cannot pay, the first pick shows the generic "Needs 1 secret; you have 0".
   - It is offered only while its player holds the relic, like any modifier.
8. **Lines.** Pledge writes none. Spiteful Mirror writes "Spiteful Mirror: Acting as if {card} is a Discord card." once the source is chosen, before the cost, on every action it is picked for. The Trade or Muster tag names it as a picked modifier.
9. **Family Wagon with Pledge.** Simultaneous effects are ordered by the active player. Family Wagon (and Vow of Union, for Beasts) resolves Pledge in three steps:
   1. **The automatic bury.** The arrival rule buries every adviser that does not keep the rule by its printed suit, except Pledge.
   2. **The question.** If faceup advisers that keep the rule by their printed suit remain, the trigger asks the active player after the batch. The question is a Partition over those advisers, with two sections: "Bury before Pledge to Discord" and "Keep in advisers". Its confirm label is "Bury".
   3. **The follow-up.** It buries the advisers placed in the first section, then Pledge. The rest revert and stay. With no such advisers, nothing is asked, and the automatic bury takes Pledge too.

   The question posts no "Chose" line. Each bury posts the existing "Buried {cards}." line. The heading:
   - When the holder is the active player: "Family Wagon: Depending on when you bury Pledge to Discord, you can keep all or none of your Nomad advisers. Choose which Nomad advisers you keep."
   - When another player answers: "Family Wagon: Depending on when Pledge to Discord is buried, its holder keeps all or none of their Nomad advisers. Choose which they keep."

## File map

- Create:
  - `src/main/scala/oathdigital/gameplay/EffectiveSuit.scala`: the three cards and `EffectiveSuit`.
  - `src/main/scala/oathdigital/gameplay/powers/economy/PledgeToDiscord.scala`.
  - `src/main/scala/oathdigital/gameplay/powers/economy/SpitefulMirror.scala`.
  - `src/test/scala/oathdigital/gameplay/powers/economy/PledgeToDiscordSuite.scala`: 3 tests.
  - `src/test/scala/oathdigital/gameplay/powers/cardplay/FamilyWagonPledgeSuite.scala`: 3 tests.
  - `src/test/scala/oathdigital/gameplay/powers/economy/SpitefulMirrorSuite.scala`: 6 tests.
- Rename: `src/test/scala/oathdigital/gameplay/AdviserSuitsSuite.scala` to `EffectiveSuitSuite.scala`, plus 6 tests.
- Delete: `src/main/scala/oathdigital/gameplay/actions/economy/AdviserSuits.scala`.
- Modify, engine (Task 2):
  - `gameplay/actions/economy/MusterSource.scala`, `EconomyTree.scala`;
  - `gameplay/powers/RuledCards.scala`;
  - `gameplay/operations/PayCostSettlement.scala`;
  - `gameplay/phases/RestCleanup.scala`;
  - `gameplay/actions/CardPlay.scala`;
  - `gameplay/powers/economy/Marriage.scala`, `gameplay/cards/NewFoundations.scala`;
  - `catalog/holding/HoldingDiscordDenizens.scala`, `HoldingRelics.scala`.
- Modify, readers (Task 3), all under `gameplay/powers`:
  - `economy/`: `Defame`, `Downtrodden`, `SuitSupplyWaiver`, `VowOfBeastkin`, `Curfew`;
  - `action/`: `BallotBox`, `HonorGuard`, `Resettle`, `SecondChance`, `ArmedMob`, `Assassin`, `BedOfRoots`, `CrystalVial`;
  - `campaign/`: `PlanContext`, `TrueNames`, `EnemyPlans`, `BeastTamer`, `FavorBySuit`, `MilitaryParade`, `TributeSpoils`, `NatureWorship`, `GreatCrusade`, `PlanDiscard`;
  - `wake/`: `Hunger`, `HornedMask`;
  - `targeting/FortressRules`, `whenplayed/TwinBrother`, `travel/Hospitality`;
  - `cardplay/`: `WildCry`, `SaddleMakers`;
  - `WalkerPowerCatalog`.
  - Test: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`.
- Modify, Family Wagon (Task 4): `gameplay/powers/cardplay/SuitOnlyAdvisers.scala`, `FamilyWagon.scala`, `VowOfUnionAdvisers.scala`.
- Modify, Spiteful Mirror (Task 5): `gameplay/powers/SelectedModifier.scala`, `gameplay/powers/ActionModifiers.scala`, `PowerKindsCatalogSuite`.

---

### Task 1: Write the plan-time rulings into the design and the rulings appendix

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`

**Interfaces:** none.

- [ ] **Step 1: Replace the slice 8 bullet of "The bar"**

In the design, replace the bullet that starts `- **An effective suit** (slice 8).` with:

```markdown
- **An effective suit** (slice 8, detailed at plan time).
  `EffectiveSuit.of` gives a card's suit as the rules see it: Discord for a
  faceup adviser of a player who has Pledge to Discord faceup, otherwise
  its printed suit. `EffectiveSuit.traded` gives a Trade's or Muster's
  source suit: Discord when the walk's answers pick Spiteful Mirror,
  otherwise `of`. Every reader of an adviser's suit goes through `of`,
  including the bank that favor on an adviser returns to, and every reader
  of a traded or mustered card's suit goes through `traded`.
  `EffectiveSuit` replaces `AdviserSuits` and keeps Marriage's weight. It
  lives in `gameplay`, because the operation pipeline reads it too: an
  off-turn payment onto a Pledge holder's adviser settles to the Discord
  bank. The projection still sends printed suits.
- **Family Wagon asks when Pledge is buried** (slice 8, added at plan
  time). `SuitOnlyAdvisers` becomes a trigger on executed operations, so
  that the active player can order the burials when Pledge to Discord sits
  beside advisers that keep the rule by their printed suit.
```

- [ ] **Step 2: Add the plan-time rulings to the design's Slice 8**

In the design, after the paragraph under `## Slice 8: Effective suit`, add:

```markdown
### Plan-time rulings

- **Pledge reaches every reader of its holder's faceup adviser suits,**
  the design's list and every other: Honor Guard, Second Chance, Resettle,
  Twin Brother, Military Parade, Rotting Fortress and the battle plans
  that read an enemy's advisers among them. Silver Tongue and the travel
  payments read only site cards.
- **Favor going back from a Pledge holder's adviser** goes to the Discord
  bank, at Rest, in an off-turn settlement, and on a discard or bury.
- **A card played faceup to a Pledge holder's advisers reads Discord,** so
  Wild Cry and Saddle Makers do not fire for it.
- **The client shows printed suits.**
- **Spiteful Mirror** turns only the source card Discord. Its first pick in
  a turn pays the secret and records the use; later picks are free.
- **Family Wagon with Pledge** buries at once what goes whatever the order,
  then asks the active player which advisers that keep the rule by their
  printed suit to bury before Pledge.
```

- [ ] **Step 3: Rewrite the rulings appendix's Slice 8 entries**

In the rulings appendix, replace everything between `## Slice 8: Effective suit` and `## Slice 9: Darkest Secret lens` with:

```markdown
## Slice 8: Effective suit

- **223 Pledge to Discord.** "Your advisers all have the [suit-discord] suit
  instead of their printed suit. Marriage counts as two [suit-discord]."
  - Every rule that reads the suit of the holder's faceup advisers sees
    Discord. Marriage counts as two Discord advisers. A facedown adviser
    still has no suit, and a facedown Pledge changes nothing.
  - Favor going back from the holder's adviser goes to the Discord bank:
    at Rest, in an off-turn cost settlement, and on a discard or bury.
  - A card played faceup to the holder's adviser slot reads Discord, so
    Wild Cry and Saddle Makers do not fire for it. A card played to a site
    reads its printed suit.
  - The client shows printed suits.
  - **With Family Wagon or Vow of Union.** Simultaneous effects are ordered
    by the active player. Every adviser that does not keep the rule by its
    printed suit, Pledge aside, is buried at once. When faceup advisers
    that keep it by their printed suit remain, the active player sorts
    them between "Bury before Pledge to Discord" and "Keep in advisers"
    (confirm "Bury"). Those placed first are buried, then Pledge; the rest
    revert and stay. With none, Pledge goes in the first bury.
    - The heading, when the holder is the active player: "Family Wagon:
      Depending on when you bury Pledge to Discord, you can keep all or
      none of your Nomad advisers. Choose which Nomad advisers you keep."
    - When another player answers: "Family Wagon: Depending on when Pledge
      to Discord is buried, its holder keeps all or none of their Nomad
      advisers. Choose which they keep."
    - The question posts no "Chose" line; each bury posts "Buried
      {cards}.".
  - Pledge writes no line.
- **R41 Spiteful Mirror.** "[secret] This turn, you may act as if any cards
  you use to trade or muster are [suit-discord] cards."
  - An optional Trade and Muster modifier. Picked, it turns the source card
    Discord: favor comes from the Discord bank, the Discord advisers match,
    and every reader of the traded or mustered card sees Discord.
  - Its first pick in a turn pays 1 secret onto the relic and records the
    use for the turn. Later picks that turn are free. The player may leave
    it unpicked on any action.
  - It is offered only while its player holds the relic.
  - Its line, once the source is chosen: "Spiteful Mirror: Acting as if
    {card} is a Discord card."
```

- [ ] **Step 4: Check the links**

Run: `python3 scripts/check-markdown-links.py`
Expected: no finding.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md \
  docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md
git commit -m "docs(batch8): slice 8 plan-time rulings"
```

---

### Task 2: The effective suit

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/EffectiveSuit.scala`
- Delete: `src/main/scala/oathdigital/gameplay/actions/economy/AdviserSuits.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/economy/MusterSource.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/RuledCards.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/PayCostSettlement.scala`
- Modify: `src/main/scala/oathdigital/gameplay/phases/RestCleanup.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/Marriage.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala`
- Rename and modify: `src/test/scala/oathdigital/gameplay/AdviserSuitsSuite.scala` to `EffectiveSuitSuite.scala`

**Interfaces:**
- Produces: everything under "Names, exactly" for `EffectiveSuit.scala`, and `MusterSource.resolve`'s `answered`.

- [ ] **Step 1: Rename the suite and write the failing tests**

```bash
git mv src/test/scala/oathdigital/gameplay/AdviserSuitsSuite.scala \
  src/test/scala/oathdigital/gameplay/EffectiveSuitSuite.scala
```

Replace the file's content with:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.operations.PayCostSettlement
import oathdigital.gameplay.phases.RestCleanupPlan
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.RuledCards
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** `EffectiveSuit`: a card's suit as the rules see it. A faceup Marriage
  * counts as two advisers of its suit (catalog batch 5 rulings, "Counts as
  * applies everywhere"), and Pledge to Discord makes its holder's faceup
  * advisers Discord (catalog batch 8 rulings, slice 8). */
class EffectiveSuitSuite extends munit.FunSuite:
  private val marriage = CatalogNames.denizen("Marriage")
  private val homesteaders = CatalogNames.denizen("Homesteaders")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val pledge = CatalogNames.denizen("Pledge to Discord")
  private val mirror = CatalogNames.relic("Spiteful Mirror")

  test("a faceup Marriage counts as two Hearth advisers, and facedown as " +
      "none"):
    val faceup = Table.start.adviser(p1, "Marriage")
      .adviser(p1, "Homesteaders").ready
    assertEquals(EffectiveSuit.count(catalog, faceup, p1, Suit.Hearth), 3)
    assertEquals(EffectiveSuit.count(catalog, faceup, p1, Suit.Beast), 0)
    val facedown = Table.start.adviser(p1, "Marriage", facedown = true)
      .adviser(p1, "Homesteaders").ready
    assertEquals(EffectiveSuit.count(catalog, facedown, p1, Suit.Hearth), 1)

  test("a ruler's count weighs Marriage, while its list names it once"):
    val ready = Table.start.adviser(p1, "Marriage").ready
    val ruler = SiteRuler.Player(p1)
    val listed = RuledCards.of(catalog, ready, ruler, Suit.Hearth)
    assertEquals(listed.count(_ == marriage), 1)
    assertEquals(RuledCards.count(catalog, ready, ruler, Suit.Hearth),
      listed.size + 1)

  test("with Pledge to Discord faceup, its holder's faceup advisers are " +
      "Discord, Marriage as two, while other cards keep their suit"):
    val ready = Table.start.adviser(p1, pledge).adviser(p1, marriage)
      .adviser(p1, wrestlers).adviser(p2, homesteaders)
      .denizen("Alchemist", at = "Ancient City").ready
    // Pledge 1, Marriage 2, Wrestlers 1.
    assertEquals(EffectiveSuit.count(catalog, ready, p1, Suit.Discord), 4)
    assertEquals(EffectiveSuit.count(catalog, ready, p1, Suit.Hearth), 0)
    assertEquals(EffectiveSuit.of(catalog, ready, wrestlers),
      Some(Suit.Discord))
    assertEquals(EffectiveSuit.of(catalog, ready, homesteaders),
      Some(Suit.Hearth))
    assertEquals(EffectiveSuit.of(catalog, ready,
      CatalogNames.denizen("Alchemist")), Some(Suit.Arcane))

  test("a facedown Pledge changes nothing, and a facedown adviser beside " +
      "a faceup Pledge counts for no suit"):
    val facedownPledge = Table.start.adviser(p1, pledge, facedown = true)
      .adviser(p1, wrestlers).ready
    assertEquals(EffectiveSuit.of(catalog, facedownPledge, wrestlers),
      Some(Suit.Order))
    val facedownAdviser = Table.start.adviser(p1, pledge)
      .adviser(p1, wrestlers, facedown = true).ready
    assertEquals(EffectiveSuit.count(catalog, facedownAdviser, p1,
      Suit.Discord), 1)

  test("a Pledge holder rules its advisers as Discord"):
    val ready = Table.start.adviser(p1, pledge).adviser(p1, wrestlers).ready
    val ruler = SiteRuler.Player(p1)
    assertEquals(RuledCards.of(catalog, ready, ruler, Suit.Discord),
      Vector[CardId](pledge, wrestlers))
    assertEquals(RuledCards.of(catalog, ready, ruler, Suit.Order),
      Vector.empty[CardId])

  test("the traded suit is Discord once Spiteful Mirror is picked"):
    val ready = Table.start.denizen(wrestlers, at = "Ancient City").ready
    val source = DecisionOptionRef.Denizen(wrestlers)
    val picked = Vector(Answered(
      ModifierPicks.decisionId(PowerWindow.TradeModifierSelection),
      ChooseOneAnswer(DecisionOptionRef.Relic(mirror)), p1))
    assertEquals(EffectiveSuit.traded(catalog, ready, Vector.empty, source),
      Some(Suit.Order))
    assertEquals(EffectiveSuit.traded(catalog, ready, picked, source),
      Some(Suit.Discord))

  test("an off-turn payment onto a Pledge holder's adviser settles to the " +
      "Discord bank"):
    // p1 is the active player on the starting table; p2 pays off turn.
    val ready = Table.start.adviser(p2, pledge).adviser(p2, wrestlers).ready
    val pay = PayCost(p2, Location.OnCard(wrestlers), Cost(favor = 1),
      intoOccupied = true, matchingBank = Some(Suit.Order))
    assertEquals(PayCostSettlement.prepare(ready, pay),
      Right(pay.copy(matchingBank = Some(Suit.Discord), offTurn = true)))

  test("Rest returns the favor on a Pledge holder's adviser to the Discord " +
      "bank"):
    val ready = Table.start.adviser(p1, pledge).adviser(p1, wrestlers)
      .tokens(wrestlers, favor = 2).ready
    assertEquals(RestCleanupPlan.derive(catalog, ready, p1)
      .map(_.returnedFavor), Right(Map(Suit.Discord -> 2)))
```

- [ ] **Step 2: Run the suite to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.EffectiveSuitSuite"`
Expected: compilation fails, because `EffectiveSuit` does not exist.

- [ ] **Step 3: Create `EffectiveSuit.scala`**

Cut `MarriageCard` out of `gameplay/actions/economy/AdviserSuits.scala`, `PledgeToDiscordCard` out of `HoldingDiscordDenizens.scala`, and `SpitefulMirrorCard` out of `HoldingRelics.scala`. Paste each, unchanged, into the new file below, and delete `AdviserSuits.scala`.

```scala
package oathdigital.gameplay

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower, Relic}
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.model._

// MarriageCard, PledgeToDiscordCard and SpitefulMirrorCard, moved here
// unchanged.

/** A card's suit as the rules see it (catalog batch 8, slice 8).
  *
  * Pledge to Discord makes every faceup adviser of its holder Discord, while
  * Pledge itself is faceup among those advisers. A faceup Marriage counts
  * as two advisers of its suit, any other faceup adviser as one, and a
  * facedown adviser has no suit (catalog batch 5 rulings, "Counts as applies
  * everywhere"). Every reader of an adviser's suit reads `of`, including
  * the bank the favor on an adviser goes back to. Every reader of the card
  * a Trade or Muster draws on reads `traded`, which also sees Spiteful
  * Mirror.
  *
  * It lives in `gameplay`, below `actions` and `powers`, because the
  * operation pipeline reads `pledged` too. It names the cards its rules
  * read, as `AdviserSuits` named Marriage. Their registered powers add
  * nothing.
  */
object EffectiveSuit:
  /** How many advisers of its suit `card` counts as, faceup. */
  def weight(card: CardId): Int = if card == MarriageCard.id then 2 else 1

  /** Whether `card` is a faceup adviser of a player who has Pledge to
    * Discord faceup. It needs no catalog: such a card is Discord whatever
    * it prints. */
  def pledged(ready: ReadyGame, card: CardId): Boolean =
    ready.game.current.players.exists(player =>
      faceUp(player, card) && faceUp(player, PledgeToDiscordCard.id))

  /** `card`'s suit: Discord when [[pledged]], otherwise its printed suit. */
  def of(catalog: ExecutableCatalog, ready: ReadyGame, card: CardId)
      : Option[Suit] =
    if pledged(ready, card) then Some(Suit.Discord)
    else catalog.suitOf(card)

  /** `player`'s faceup advisers of `suit`, each by its weight. */
  def count(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      suit: Suit): Int =
    ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.advisers).collect {
        case DenizenState(id, Orientation.FaceUp, _)
            if of(catalog, ready, id).contains(suit) => weight(id)
      }.sum

  /** Whether the walk's answers pick Spiteful Mirror, for a Trade or a
    * Muster. */
  def mirrored(answered: Vector[Answered]): Boolean =
    Vector(PowerWindow.TradeModifierSelection,
      PowerWindow.MusterModifierSelection).exists(window =>
      ModifierPicks.isPicked(answered, window, SpitefulMirrorCard.id))

  /** The suit of the card `ref` names, as the Trade or Muster that draws on
    * it reads it: Discord once Spiteful Mirror is picked, otherwise
    * [[of]]. `None` for a reference that names no card. */
  def traded(catalog: ExecutableCatalog, ready: ReadyGame,
      answered: Vector[Answered], ref: DecisionOptionRef): Option[Suit] =
    cardOf(ref).flatMap(card =>
      if mirrored(answered) then Some(Suit.Discord)
      else of(catalog, ready, card))

  private def cardOf(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Edifice(id) => Some(id)
    case _ => None

  private def faceUp(player: PlayerState, card: CardId): Boolean =
    player.advisers.exists {
      case DenizenState(held, Orientation.FaceUp, _) => held == card
      case _ => false
    }
```

Remove any import `HoldingDiscordDenizens.scala` and `HoldingRelics.scala` no longer use. `Locked` and `AdviserOnly` stay used in `HoldingDiscordDenizens.scala` (False Prophet, Tracker); check with the build.

- [ ] **Step 4: Point the imports at the moved cards**

- `Marriage.scala`: `import oathdigital.gameplay.MarriageCard`. In its doc, `AdviserSuits.weight` becomes `EffectiveSuit.weight`.
- `NewFoundations.scala`: replace `import oathdigital.gameplay.actions.economy.MarriageCard` with:

```scala
import oathdigital.gameplay.{MarriageCard, PledgeToDiscordCard,
  SpitefulMirrorCard}
```

`oathdigital.catalog.holding._` no longer provides the two moved cards. The card lists already name them.

- [ ] **Step 5: The Trade and Muster source**

In `MusterSource.scala`:
- `resolve` gains a last parameter, `answered: Vector[Answered] = Vector.empty`. Its suit line becomes:

```scala
    suit <- EffectiveSuit.traded(catalog, state, answered, ref).toRight(
      OathViolation.UnsupportedEconomyState(s"no catalog suit for $id"))
```

- Add to `resolve`'s doc: "The suit is the one the action reads (`EffectiveSuit.traded`): Discord once Spiteful Mirror is picked in `answered`."
- `matching` becomes `EffectiveSuit.count(catalog, state, actor, suit)`, and its doc names `EffectiveSuit` in place of `AdviserSuits`.
- Add `import oathdigital.gameplay.EffectiveSuit`.

In `EconomyTree.scala`, `afterSource` gains a last parameter, `answered: Vector[Answered]`, passed to `MusterSource.resolve`. The `Branch` in `afterModifiers` passes `pending.answered`:

```scala
    Branch((ready, pending) => answered(pending, kind.decisionId).toVector
      .flatMap(ref => afterSource(catalog, ready, actor, kind, ref,
        pending.answered).fold(error => Vector[Operation](fail(error)),
          identity)))),
```

- [ ] **Step 6: Ruled cards, Rest cleanup, settlement and the card-play discard**

`RuledCards.scala`:

```scala
    (advisers ++ atSites).filter(card =>
      EffectiveSuit.of(catalog, ready, card).contains(suit))
```

and `count` maps `EffectiveSuit.weight`. Replace the import of `AdviserSuits` with `import oathdigital.gameplay.{EffectiveSuit, SiteRulers}`. In the docs, `AdviserSuits.weight` becomes `EffectiveSuit.weight`. Add to the object's doc: "An adviser's suit is its effective suit (`EffectiveSuit`), so a Pledge to Discord holder rules its advisers as Discord."

`RestCleanup.scala`, in `RestCleanupPlan.derive`: `catalog.suitOf(card.id)` becomes `EffectiveSuit.of(catalog, ready, card.id)`. Import it with `PlayerResourceSources`. In the two docs, "printed suit bank" becomes "suit bank (`EffectiveSuit`: a Pledge to Discord holder's advisers return to Discord)".

`PayCostSettlement.scala`, the off-turn case:

```scala
      case pay: PayCost if pay.player != ready.game.current.turn.activePlayer =>
        val bank = pay.placedAt match
          case Location.OnCard(card) if EffectiveSuit.pledged(ready, card) =>
            Some(Suit.Discord)
          case _ => pay.matchingBank
        if pay.cost.favor > 0 && bank.isEmpty then
          Left(OathViolation.CoreOperationRejected("no-matching-bank",
            "favor paid outside the payer's turn needs the card's suit bank"))
        else Right(pay.copy(matchingBank = bank, offTurn = true))
```

Add `import oathdigital.gameplay.EffectiveSuit`. Add to the object's doc: "A payment onto a faceup adviser of a Pledge to Discord holder settles to the Discord bank, whatever bank the caller named: the caller has no state to read."

`CardPlay.scala`, in `selectedDiscard`: `catalog.suitOf(id)` becomes `EffectiveSuit.of(catalog, ready, id)`. The replaced adviser is read before it leaves.

- [ ] **Step 7: Run the suite, then everything**

Run: `./sbtw "testOnly oathdigital.gameplay.EffectiveSuitSuite oathdigital.gameplay.MusterProcedureSuite"`
Expected: PASS, 6 new tests.

Run: `./sbtw test`
Expected: PASS, the baseline plus 6.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/EffectiveSuit.scala \
  src/main/scala/oathdigital/gameplay/actions/economy/AdviserSuits.scala \
  src/main/scala/oathdigital/gameplay/actions/economy/MusterSource.scala \
  src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala \
  src/main/scala/oathdigital/gameplay/powers/RuledCards.scala \
  src/main/scala/oathdigital/gameplay/operations/PayCostSettlement.scala \
  src/main/scala/oathdigital/gameplay/phases/RestCleanup.scala \
  src/main/scala/oathdigital/gameplay/actions/CardPlay.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/Marriage.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala \
  src/test/scala/oathdigital/gameplay/AdviserSuitsSuite.scala \
  src/test/scala/oathdigital/gameplay/EffectiveSuitSuite.scala
git commit -m "feat(batch8): one effective suit for advisers and traded cards"
```

---

### Task 3: Every reader of an adviser's suit, and Pledge to Discord

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/PledgeToDiscord.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/PledgeToDiscordSuite.scala`
- Modify: the reader files under "File map, readers"
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes from Task 2: `EffectiveSuit.of`, `count`, `weight`, `traded`; `PledgeToDiscordCard`.
- Produces: `EnemyPlans.suitOf(catalog, ready, ref)`, `FavorBySuit.counts(catalog, ready, cards)`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/economy/PledgeToDiscordSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.{PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.HonorGuard
import oathdigital.gameplay.powers.cardplay.WildCry
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, MenuPlay, PhasePowerHarness,
  PowerRules, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Pledge to Discord (catalog batch 8, slice 8): every reader of its
  * holder's faceup adviser suits sees Discord. One test per kind of
  * reader: a Trade's matching advisers, a target rule with the bank a
  * bury returns to, and a card played to the holder's advisers. */
class PledgeToDiscordSuite extends munit.FunSuite:
  private val pledge = CatalogNames.denizen("Pledge to Discord")
  private val marriage = CatalogNames.denizen("Marriage")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val home = CatalogNames.site("Ancient City")
  /** A plain Discord denizen. */
  private val plainDiscord = SearchFixture.anyPlainDenizensOf(Suit.Discord)
    .head
  private val rules = PowerRules.withWalkerPowers

  test("a Trade with a Discord card counts every faceup adviser of the " +
      "holder, Marriage as two"):
    val ready = Table.start.pawn(p1, at = home).adviser(p1, pledge)
      .adviser(p1, marriage).adviser(p1, wrestlers)
      .denizen(plainDiscord, at = home).secrets(p1, 1)
      .bankFavor(Suit.Discord, 6).ready
    val begun = accepted(MenuPlay.started(rules, Ready(ready), p1,
      ActionRef.Trade, Vector(DecisionOptionRef.Button("favor"))))
    val done = accepted(rules.resolveWalker(begun.state, p1,
      TradeProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(plainDiscord))))
    val end = Look(done.state)
    // 1, and 4 more: Pledge, Marriage twice, Wrestlers.
    assertEquals(end.favor(p1), Look(ready).favor(p1) + 5)
    assertEquals(end.bankFavor(Suit.Discord), 1)

  test("Honor Guard targets a holder with no Order adviser, and the " +
      "buried adviser's favor goes to the Discord bank"):
    val guard = CatalogNames.denizen("Honor Guard")
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, home)
      .adviser(p1, guard).favor(p1, 3).pawn(p2, at = home)
      .adviser(p2, pledge).adviser(p2, wrestlers)
      .tokens(wrestlers, favor = 1).ready
    val power = HonorGuard.forCatalog(catalog)
    val source = DecisionOptionRef.Denizen(guard)
    val asked = accepted(PhasePowerHarness.use(ready, power, source))
    assertEquals(PhasePowerHarness.offered(asked, p1), Some(Vector(
      "denizen" -> pledge.value, "denizen" -> wrestlers.value)))
    val PhasePowerHarness.UsedAndAnswered(_, done) =
      PhasePowerHarness.useAndAnswer(ready, power, source,
        HonorGuard.decisionId, DecisionOptionRef.Denizen(wrestlers))
    val end = PhasePowerHarness.after(done)
    assertEquals(end.banks.favor.getOrElse(Suit.Discord, 0),
      ready.banks.favor.getOrElse(Suit.Discord, 0) + 1)
    assertEquals(end.banks.favor.getOrElse(Suit.Order, 0),
      ready.banks.favor.getOrElse(Suit.Order, 0))

  test("a Beast card played faceup to the holder's advisers is Discord, " +
      "so Wild Cry gives nothing; played to a site it gives"):
    import PowerFixture.actor
    val wildCry = CatalogNames.denizen("Wild Cry")
    val beast = CatalogNames.denizen("Errand Boy")
    val fillers = Vector("Homesteaders", "Land Warden")
      .map(CatalogNames.denizen(_))
    val ready = Table.from(SearchFixture.staged(Vector(beast) ++ fillers,
      supply = 5)).denizen(wildCry, at = home).adviser(actor, pledge)
      .unchecked
    // 5 Supply less the world Search's 2.
    val faceup = SearchFixture.after(SearchFixture.play(ready,
      Vector(WildCry.id), beast, PlayedAs.Faceup))
    assertEquals(Look(faceup).supply(actor), 3)
    val atSite = SearchFixture.after(SearchFixture.play(ready,
      Vector(WildCry.id), beast, PlayedAs.AtSite))
    assertEquals(Look(atSite).supply(actor), 4)
```

Read `PhasePowerHarness` for `use`, `useAndAnswer`, `UsedAndAnswered` and `after` (`HonorGuardSuite` uses them through `import PhasePowerHarness._`), and follow their real signatures. `MenuPlay.started` and `rules.resolveWalker` are used the same way in `SmallFriendsSuite`.

- [ ] **Step 2: Run the suite to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.PledgeToDiscordSuite"`
Expected: the Trade test passes already: `matching` reads `EffectiveSuit` since Task 2. The Honor Guard test fails, with no option offered or with the favor in the Order bank. The Wild Cry test fails with Supply 4 after the faceup play.

- [ ] **Step 3: Route every adviser reader through `EffectiveSuit`**

In each file below, add `import oathdigital.gameplay.EffectiveSuit`, merging it into an existing `oathdigital.gameplay.{...}` import where one exists. Then make the change. `ready` is the `ReadyGame` already in scope at each site.

The source readers of a Trade or Muster, which read `traded` with the walk's answers:

- `economy/Defame.scala`: `discordSource` becomes:

```scala
  private def discordSource(ready: ReadyGame, pending: PendingTree)
      : Boolean = PowerAnswers.one(pending, TradeProcedure.decisionId)
    .flatMap(EffectiveSuit.traded(catalog, ready, pending.answered, _))
    .contains(Suit.Discord)
```

  `offer` calls `discordSource(ready, pending)`. In the class doc, "a denizen or an edifice by its suit" becomes "by its suit as the Trade reads it (`EffectiveSuit.traded`)".
- `economy/Downtrodden.scala`: `suitOf(pending)` becomes `suitOf(ready, pending)`:

```scala
  private def suitOf(ready: ReadyGame, pending: PendingTree): Option[Suit] =
    PowerAnswers.one(pending, MusterProcedure.decisionId)
      .flatMap(EffectiveSuit.traded(catalog, ready, pending.answered, _))
```

  `bonus` calls `suitOf(ready, pending)`.
- `economy/SuitSupplyWaiver.scala`: `suitOf` takes `ready` and reads `PowerAnswers.one(pending, decisionId).flatMap(EffectiveSuit.traded(catalog, ready, pending.answered, _))`. `unlessFree` passes `ready`. In the doc, "The suit is read off the answered card" gains "as the action reads it (`EffectiveSuit.traded`)".
- `economy/VowOfBeastkin.scala`, in `refused`: `suit <- catalog.suitOf(card)` becomes `suit <- EffectiveSuit.traded(catalog, ctx.state, ctx.answered, ref)`.

The adviser readers, which read `of`:

- `economy/Curfew.scala`, `ruled`: `EffectiveSuit.of(catalog, ready, card).exists(...)`. It must agree with `RuledCards.of`, which now reads the same suit.
- `action/BallotBox.scala`, `matched`: the advisers' line (`.flatMap(catalog.suitOf).toSet` after the advisers) becomes `.flatMap(EffectiveSuit.of(catalog, ready, _)).toSet`. The site cards' line stays printed.
- `action/HonorGuard.scala`:
  - `ordered(owner)` becomes `ordered(ready, owner)`, reading `EffectiveSuit.of(catalog, ready, card).contains(Suit.Order)`. `candidates` passes `ready`.
  - In `burial`, `suit = EffectiveSuit.of(catalog, ready, chosen.card.id)`.
- `action/Resettle.scala`, `candidates`: `EffectiveSuit.of(catalog, ready, id).contains(Suit.Nomad)`.
- `action/SecondChance.scala`, `candidates`: `EffectiveSuit.of(catalog, ready, card).exists(Suits.contains)`.
- `action/ArmedMob.scala`, `action/Assassin.scala`, `action/BedOfRoots.scala`, `action/CrystalVial.scala`, `wake/Hunger.scala`: the bury's or discard's `catalog.suitOf(<card>)` becomes `EffectiveSuit.of(catalog, ready, <card>)`. Crystal Vial's site cards are unaffected, since `of` reads them printed.
- `wake/HornedMask.scala`, in the discard of the actor's adviser (the `case d: DenizenState => catalog.suitOf(d.id)` line): `EffectiveSuit.of(catalog, ready, d.id)`. `returnsOf`, which reads a site card, stays printed.
- `campaign/PlanContext.scala`, `enemyHasAdviser`: `EffectiveSuit.of(catalog, ready, held).contains(suit)`.
- `campaign/EnemyPlans.scala`, `suitOf` takes the state:

```scala
  /** The suit of the plan named by `ref`: its source card's, as the rules
    * see it (`EffectiveSuit`). A relic and the title have none. */
  def suitOf(catalog: ExecutableCatalog, ready: ReadyGame,
      ref: DecisionOptionRef): Option[Suit] = ref match
    case DecisionOptionRef.Denizen(id) => EffectiveSuit.of(catalog, ready, id)
    case DecisionOptionRef.Edifice(id) => EffectiveSuit.of(catalog, ready, id)
    case _ => None
```

  `TrueNames.matched` and `BeastTamer.bars` pass `ctx.state`.
- `campaign/TrueNames.scala`, `suits`: `EffectiveSuit.of(catalog, ready, held)`.
- `campaign/FavorBySuit.scala`, `counts` takes `ready: ReadyGame` after `catalog` and reads `EffectiveSuit.of(catalog, ready, card)`. `MilitaryParade` passes `use.ready`; `TributeSpoils` passes the state its own caller reads (`use.ready`).
- `campaign/MilitaryParade.scala`: `AdviserSuits.weight` becomes `EffectiveSuit.weight`, in code and doc.
- `campaign/NatureWorship.scala`, `otherBeasts`: `EffectiveSuit.of(catalog, context.ready, held).contains(Suit.Beast)` and `EffectiveSuit.weight(held)`.
- `campaign/GreatCrusade.scala`: `AdviserSuits.weight` becomes `EffectiveSuit.weight`.
- `campaign/PlanDiscard.scala`, `operations`: `suit <- EffectiveSuit.of(catalog, ready, card).toRight(...)`. A plan from the user's advisers is read before it leaves.
- `targeting/FortressRules.scala`, `holdsBeastAdviser`: `EffectiveSuit.of(catalog, ready, card).contains(Suit.Beast)`.
- `whenplayed/TwinBrother.scala`, `candidates`: replace the two lines `definition <- catalog.denizen(card).toVector` and `if definition.suit == Suit.Nomad` with `if EffectiveSuit.of(catalog, ready, card).contains(Suit.Nomad)`.
- `travel/Hospitality.scala`, `matching`: `AdviserSuits.count` becomes `EffectiveSuit.count`. The site half stays printed.
- `cardplay/WildCry.scala`, `appliesAt`: `EffectiveSuit.of(catalog, ctx.state, card).contains(Suit.Beast)`. The hook runs after the play, so a card played to the advisers is read there.
- `cardplay/SaddleMakers.scala`, `reward`: `suit <- EffectiveSuit.of(catalog, ctx.state, card).filter(SaddleMakers.Suits)`.

Update each doc that names `AdviserSuits` or says "printed suit" of an adviser to name `EffectiveSuit`.

- [ ] **Step 4: Check that only printed readers remain**

Run: `grep -rn "suitOf\|AdviserSuits\|definition.suit" src/main/scala/oathdigital/gameplay | grep -v "EffectiveSuit"`

Expected: only these, which read cards that are not advisers, or that are being played to a site:
- `actions/economy/MusterSource.scala` (`atSite`);
- `actions/CardPlay.scala` (the site play and the site discard);
- `actions/cardplay/CardSlots.scala` (the Homeland check);
- `operations/PowerOperations.scala` (`Costs.onCard`; `PayCostSettlement` corrects the bank);
- `powers/action/`: `BrassHorse`, `MemoryOfNature`, `SiteCards`, `TamingCharm`, `BallotBox` (the site half);
- `powers/campaign/`: `SaltTheEarth`, `SignalTrees`, `WalledGarden`;
- `powers/rest/`: `LeagueTreatyContribution`, `SilverTongue`;
- `powers/search/SpinningBee`, `powers/setup/ProvingGroundsRules`;
- `powers/travel/`: `TravelPayments`, `ForestPaths`, `Hospitality` (the site half);
- `powers/whenplayed/`: `AnimalHost`, `CallForHelp`, `GreatHerd`, `RegionDiscard`;
- `powers/wake/HornedMask.scala` (`returnsOf`);
- `powers/economy/EconomySources.scala`;
- `powers/cardplay/SuitOnlyAdvisers.scala` (Task 4 settles it);
- the private helpers named `suitOf` in `SuitSupplyWaiver`, `Downtrodden` and `RegionDiscard`, and `EnemyPlans.suitOf`.

Any other line reads an adviser: route it through `EffectiveSuit` and say so in the report.

- [ ] **Step 5: Register Pledge to Discord**

Create `src/main/scala/oathdigital/gameplay/powers/economy/PledgeToDiscord.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.PledgeToDiscordCard
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution}
import oathdigital.model._

/** Pledge to Discord (card 223, adviser-only, locked): "Your advisers all
  * have the [suit-discord] suit instead of their printed suit. Marriage
  * counts as two [suit-discord]."
  *
  * The rule is `EffectiveSuit.of`, which every reader of an adviser's suit
  * reads. This power hooks no window. It is registered so the card counts
  * as implemented. It writes no line.
  */
case object PledgeToDiscord extends ContributingPower:
  val id: PowerId = PledgeToDiscordCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
```

In `WalkerPowerCatalog.scala`, after `Vector(Marriage) ++` add `Vector(PledgeToDiscord) ++`.

In `PowerKindsCatalogSuite`, add `"denizen.pledge-to-discord"` to `persistentRules`, after `"relic.secret-testament"`.

- [ ] **Step 6: Run the tests to see them pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.PledgeToDiscordSuite oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: PASS, 3 new tests.

Run: `./sbtw test`
Expected: PASS, the baseline plus 9.

- [ ] **Step 7: Commit**

Stage every file Step 3 changed by its path, then:

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/PledgeToDiscord.scala \
  src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/PledgeToDiscordSuite.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(batch8): Pledge to Discord makes its holder's advisers Discord"
```

---

### Task 4: Family Wagon and Vow of Union ask when Pledge is buried

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/cardplay/SuitOnlyAdvisers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/cardplay/FamilyWagon.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/cardplay/VowOfUnionAdvisers.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/cardplay/FamilyWagonPledgeSuite.scala`

**Interfaces:**
- Consumes from Task 2: `EffectiveSuit.of`, `PledgeToDiscordCard`. From slice 6: `ExecutedTrigger`, `ExecutedBatch`.
- Produces: `SuitOnlyAdvisers.buryKey`, `keepKey`, `pledgeHeading`; `FamilyWagon.pledgeDecisionId`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/cardplay/FamilyWagonPledgeSuite.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{NoteText, PowerFixture, SearchFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.PartitionAnswer
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Unwrap.accepted

/** Family Wagon beside Pledge to Discord (catalog batch 8 rulings, slice
  * 8): what goes whatever the order is buried at once, then the active
  * player sorts the Nomads between burial before Pledge and keeping. */
class FamilyWagonPledgeSuite extends munit.FunSuite:
  import PowerFixture.actor

  private val power = FamilyWagon.forCatalog(catalog)
  private val wagon = CatalogNames.denizen("Family Wagon")
  private val pledge = CatalogNames.denizen("Pledge to Discord")
  private val elders = CatalogNames.denizen("Elders")
  private val rainBoots = CatalogNames.denizen("Rain Boots")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val fillers = Vector("Homesteaders", "Land Warden")
    .map(CatalogNames.denizen(_))

  /** The actor with Pledge to Discord and `advisers`, about to Search
    * Family Wagon off the deck. */
  private def pledgedWith(advisers: DenizenId*): ReadyGame =
    advisers.foldLeft(Table.from(SearchFixture.staged(
      Vector(wagon) ++ fillers, supply = 5)).adviser(actor, pledge))(
      (table, card) => table.adviser(actor, card)).unchecked

  private def playedFaceup(ready: ReadyGame): OathTransition =
    SearchFixture.play(ready, Vector.empty, wagon, PlayedAs.Faceup)

  private def wagonLines(events: Vector[OathEvent]): Vector[String] =
    NoteText.said(FamilyWagon.id, power.noteKeys, events).map(_.key)

  test("the Order adviser goes at once; the Nomads are sorted, and those " +
      "buried before Pledge return their favor to the Discord bank"):
    val ready = Table.from(pledgedWith(elders, rainBoots, wrestlers))
      .tokens(elders, favor = 1).unchecked
    val played = playedFaceup(ready)
    assertEquals(PhasePowerHarness.offered(played, actor), Some(Vector(
      "denizen" -> elders.value, "denizen" -> rainBoots.value)))
    val done = accepted(SearchFixture.rules.resolveWalker(played.state,
      actor, FamilyWagon.pledgeDecisionId, PartitionAnswer(Vector(
        DecisionPlacement(DecisionOptionRef.Denizen(elders),
          SuitOnlyAdvisers.buryKey),
        DecisionPlacement(DecisionOptionRef.Denizen(rainBoots),
          SuitOnlyAdvisers.keepKey)))))
    val end = SearchFixture.after(done)
    assertEquals(Look(end).advisers(actor),
      Vector[CardId](rainBoots, wagon))
    assertEquals(end.banks.favor.getOrElse(Suit.Discord, 0),
      ready.banks.favor.getOrElse(Suit.Discord, 0) + 1)
    assertEquals(wagonLines(played.events ++ done.events),
      Vector("buried", "buried"))

  test("with no Nomad beside Pledge, nothing is asked and every other " +
      "adviser is buried at once"):
    val played = playedFaceup(pledgedWith(wrestlers))
    assertEquals(Look(SearchFixture.after(played)).advisers(actor),
      Vector[CardId](wagon))
    assertEquals(wagonLines(played.events), Vector("buried"))

  test("the heading speaks to the holder, or names them as its holder"):
    assertEquals(SuitOnlyAdvisers.pledgeHeading("Family Wagon", Suit.Nomad,
      holderAnswers = true), "Family Wagon: Depending on when you bury " +
      "Pledge to Discord, you can keep all or none of your Nomad advisers. " +
      "Choose which Nomad advisers you keep.")
    assertEquals(SuitOnlyAdvisers.pledgeHeading("Vow of Union", Suit.Beast,
      holderAnswers = false), "Vow of Union: Depending on when Pledge to " +
      "Discord is buried, its holder keeps all or none of their Beast " +
      "advisers. Choose which they keep.")
```

`SearchFixture.play` throws on a refused step but does not need the walk to end, so it returns the walk parked on the question. If the Search has its own decisions after the play, the walk parks there after the answer instead; read `SearchFixture.after` and use the parked state as other Search suites do.

- [ ] **Step 2: Run the suite to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.FamilyWagonPledgeSuite"`
Expected: compilation fails, because `pledgeDecisionId`, `buryKey`, `keepKey` and `pledgeHeading` do not exist.

- [ ] **Step 3: Make `SuitOnlyAdvisers` a trigger**

In `SuitOnlyAdvisers.scala`:

1. The trait extends `ExecutedTrigger` in place of `ContributingPower`. Import `ExecutedBatch` and `ExecutedTrigger` from `powerresolver`, and drop `ContributingPower` from that import if nothing else uses it. Import `oathdigital.gameplay.{EffectiveSuit, PledgeToDiscordCard}`.

2. Add the members:

```scala
  /** The card's name, as its question names it. */
  def ruleName: String
  /** The question that orders Pledge to Discord's bury. */
  def pledgeDecisionId: String

  override def narratedDecisions: Set[String] = Set(pledgeDecisionId)
```

3. Replace `onArrival` with:

```scala
  override def onArrival(arrived: AdviserArrivals): Option[ArrivalFollowUp] =
    for
      holder <- holderOf(arrived.after)
      if arrived.arrivals.exists(_.player == holder)
      buriedHere = automatic(arrived.after, holder)
      if buriedHere.nonEmpty
    yield ArrivalFollowUp(
      buriedHere.flatMap(bury(arrived.after, _, holder, arrived.actor)),
      Note(id, _ => Some(line(holder, arrived.actor, buriedHere.map(_.id))),
        covers = true))

  /** After a batch that left Pledge to Discord faceup beside advisers that
    * keep the rule by their printed suit: the active player sorts those
    * advisers, then the ones placed to bury go, then Pledge. */
  def onExecuted(batch: ExecutedBatch): Option[Operation] = for
    holder <- holderOf(batch.now)
    waiting = ordered(batch.now, holder)
    if waiting.nonEmpty
  yield Sequence(Vector[Operation](
    Decide(pledgeDecisionId, batch.actor, DecisionQuery.Partition(
      Vector(
        DecisionSection(SuitOnlyAdvisers.buryKey,
          "Bury before Pledge to Discord", 0),
        DecisionSection(SuitOnlyAdvisers.keepKey, "Keep in advisers", 0)),
      waiting.map(adviser =>
        DecisionOption.Denizen(DecisionOptionRef.Denizen(adviser.id))),
      heading = Some(SuitOnlyAdvisers.pledgeHeading(ruleName, suit,
        holder == batch.actor)),
      confirmLabel = Some("Bury"))),
    BuildOps((ready, pending) => Right(
      buriedFirst(ordered(ready, holder), pending.answered)
        .flatMap(bury(ready, _, holder, batch.actor)) ++
      pledgeOf(ready, holder).toVector
        .flatMap(bury(ready, _, holder, batch.actor)))),
    // The bury has run when the note is built, so the advisers are read
    // from the state before it.
    Note(id, states => states.previous.map((before, _) => line(holder,
      batch.actor, buriedFirst(ordered(before, holder), states.answered)
        .map(adviser => adviser.id: CardId) :+ PledgeToDiscordCard.id)),
      covers = true)))
```

4. Add the helpers, and give `bury` the state:

```scala
  /** The advisers the arrival buries at once: every adviser but this card
    * that does not keep the rule. With Pledge to Discord faceup beside
    * advisers that keep it by their printed suit ([[ordered]]), Pledge and
    * those advisers wait for the trigger's question; the order of their
    * burials decides which stay. */
  private def automatic(ready: ReadyGame, holder: PlayerId)
      : Vector[AdviserState] =
    val others = SuitOnlyAdvisers.advisers(ready, holder)
      .filterNot(_.id == cardId)
    if ordered(ready, holder).isEmpty then
      others.filterNot(keeps(ready, _))
    else others.filterNot(adviser => adviser.id == PledgeToDiscordCard.id ||
      printedKeeps(adviser))

  /** With Pledge to Discord faceup among `holder`'s advisers, the faceup
    * advisers other than this card that keep the rule by their printed
    * suit, in held order: Pledge makes them Discord until it goes. */
  private def ordered(ready: ReadyGame, holder: PlayerId)
      : Vector[DenizenState] =
    if !SuitOnlyAdvisers.hasFaceUp(ready, holder, PledgeToDiscordCard.id)
    then Vector.empty
    else SuitOnlyAdvisers.advisers(ready, holder).collect {
      case adviser: DenizenState
          if adviser.id != cardId && printedKeeps(adviser) => adviser
    }

  /** The advisers among `waiting` the answer places to bury before
    * Pledge. */
  private def buriedFirst(waiting: Vector[DenizenState],
      answered: Vector[Answered]): Vector[DenizenState] =
    val placed = answered.reverseIterator.collectFirst {
      case Answered(`pledgeDecisionId`,
          DecisionAnswer.PartitionAnswer(placements), _) => placements
    }.getOrElse(Vector.empty)
    val first = placed.collect {
      case DecisionPlacement(DecisionOptionRef.Denizen(card), key)
          if key == SuitOnlyAdvisers.buryKey => card
    }.toSet
    waiting.filter(adviser => first(adviser.id))

  private def pledgeOf(ready: ReadyGame, holder: PlayerId)
      : Option[AdviserState] = SuitOnlyAdvisers.advisers(ready, holder)
    .find(_.id == PledgeToDiscordCard.id)

  /** Whether `adviser` stays under the rule now: a faceup card whose
    * effective suit is `suit`. */
  private def keeps(ready: ReadyGame, adviser: AdviserState): Boolean =
    adviser match
      case DenizenState(card, Orientation.FaceUp, _) =>
        EffectiveSuit.of(catalog, ready, card).contains(suit)
      case _ => false

  /** Whether `adviser` keeps the rule by its printed suit. */
  private def printedKeeps(adviser: AdviserState): Boolean =
    kept(AdviserFace.of(adviser))
```

   `bury(adviser, holder, actor)` becomes `bury(ready, adviser, holder, actor)`, and its suit is `EffectiveSuit.of(catalog, ready, card)`: an adviser buried while Pledge stands returns its favor to Discord.

   The card-play warning keeps `kept(played)`, which reads the printed suit. Add to `kept`'s doc: "The card-play warning reads it: a holder of the rule never keeps Pledge to Discord, so the played card's printed suit decides."

5. In the object, add:

```scala
  /** The section of the cards buried before Pledge to Discord. */
  val buryKey: String = "bury"
  /** The section of the cards kept. */
  val keepKey: String = "keep"

  /** The heading of the question that orders Pledge to Discord's bury. */
  def pledgeHeading(ruleName: String, suit: Suit, holderAnswers: Boolean)
      : String =
    if holderAnswers then s"$ruleName: Depending on when you bury Pledge " +
      s"to Discord, you can keep all or none of your $suit advisers. " +
      s"Choose which $suit advisers you keep."
    else s"$ruleName: Depending on when Pledge to Discord is buried, its " +
      s"holder keeps all or none of their $suit advisers. Choose which " +
      "they keep."
```

   `$suit` is the enum case's name, "Nomad" or "Beast" (`Suit.key` is the lower-case wire key).

6. Update the trait's doc. Add, after the list:

```scala
  * With Pledge to Discord faceup among the holder's advisers, every
  * adviser but the card is Discord, and the order of the burials decides
  * which stay (catalog batch 8 rulings, slice 8). The arrival buries what
  * goes whatever the order. When faceup advisers that keep the rule by
  * their printed suit remain, the trigger asks the active player, after
  * the batch, which of them go before Pledge (`onExecuted`). It buries
  * those, then Pledge, and the rest revert. Its question posts no "Chose"
  * line, and each bury posts its "Buried" line.
```

- [ ] **Step 4: Name the two rules**

In `FamilyWagon.scala`, add to the class:

```scala
  def ruleName: String = FamilyWagonCard.name
  def pledgeDecisionId: String = FamilyWagon.pledgeDecisionId
```

and to the object:

```scala
  val pledgeDecisionId: String = "power.family-wagon.pledge"
```

In `VowOfUnionAdvisers.scala`, the same with `VowOfUnionCard.name` and `"power.vow-of-union.pledge"`.

- [ ] **Step 5: Run the tests to see them pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.*"`
Expected: PASS, 3 new tests. `FamilyWagonSuite`, `FamilyWagonArrivalsSuite` and `VowOfUnionAdvisersSuite` pass unchanged.

Run: `./sbtw test`
Expected: PASS, the baseline plus 12.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/cardplay/SuitOnlyAdvisers.scala \
  src/main/scala/oathdigital/gameplay/powers/cardplay/FamilyWagon.scala \
  src/main/scala/oathdigital/gameplay/powers/cardplay/VowOfUnionAdvisers.scala \
  src/test/scala/oathdigital/gameplay/powers/cardplay/FamilyWagonPledgeSuite.scala
git commit -m "feat(batch8): Family Wagon asks when Pledge to Discord is buried"
```

---

### Task 5: Spiteful Mirror

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/SpitefulMirror.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/SpitefulMirrorSuite.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes from Task 2: `SpitefulMirrorCard`, `EffectiveSuit.traded` (already read by `MusterSource.resolve` and the source readers).
- Produces: `SpitefulMirror.id`, `useRef`, `acting`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/economy/SpitefulMirrorSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.{NoteText, SearchFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, MenuPlay,
  Outcomes, PhasePowerHarness, PowerRules, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class SpitefulMirrorSuite extends munit.FunSuite:
  private val mirror = CatalogNames.relic("Spiteful Mirror")
  private val power = SpitefulMirror.forCatalog(catalog)
  private val home = CatalogNames.site("Ancient City")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** A plain Discord denizen. */
  private val plainDiscord = SearchFixture.anyPlainDenizensOf(Suit.Discord)
    .head
  private val defame = CatalogNames.denizen("Defame")
  private val rules = PowerRules.withWalkerPowers
  private def modifiers(action: PowerWindow): String =
    ModifierPicks.decisionId(action)

  /** p1 at Ancient City holding Spiteful Mirror, a plain Discord adviser
    * and `secrets` faceup secrets, with Wrestlers at the site and 3 favor
    * in the Discord bank. */
  private def mirrorAtWrestlers(secrets: Int = 2): Table =
    Table.start.pawn(p1, at = home).relic(p1, mirror)
      .adviser(p1, plainDiscord).denizen(wrestlers, at = home)
      .secrets(p1, secrets).bankFavor(Suit.Discord, 3)

  /** `ready` with Spiteful Mirror already paid for this turn. */
  private def alreadyPaid(ready: ReadyGame): ReadyGame =
    ready.updateCurrent(current => current.copy(turn = current.turn.copy(
      usedPowers = current.turn.usedPowers + SpitefulMirror.useRef)))

  private def begun(ready: ReadyGame, action: ActionRef,
      selection: Vector[DecisionOptionRef]): OathTransition =
    accepted(MenuPlay.started(rules, Ready(ready), p1, action, selection))

  private val forFavor = Vector(DecisionOptionRef.Button("favor"))
  private val forSecrets = Vector(DecisionOptionRef.Button("secret"))

  /** `from`, then p1's answer `ref` at `decisionId`; the events run from
    * the action's start. */
  private def answeredWith(from: OathTransition, decisionId: String,
      ref: DecisionOptionRef): OathTransition =
    val next = accepted(rules.resolveWalker(from.state, p1, decisionId,
      ChooseOneAnswer(ref)))
    next.copy(events = from.events ++ next.events)

  private def mirrorPicked(from: OathTransition, window: PowerWindow)
      : OathTransition =
    answeredWith(from, modifiers(window), DecisionOptionRef.Relic(mirror))

  private def optionStates(at: OathTransition)
      : Option[Vector[(String, String, Option[String])]] =
    PhasePowerHarness.queryOf(at, p1).map(_.offeredOptions.map(option =>
      (option.id, option.state, option.reason)))

  test("the first pick pays a secret and records the use; the Trade's " +
      "card is Discord, and the line names it"):
    val ready = mirrorAtWrestlers().ready
    // No other modifier to pick, so the Trade starts by itself.
    val picked = mirrorPicked(begun(ready, ActionRef.Trade, forFavor),
      PowerWindow.TradeModifierSelection)
    val done = answeredWith(picked, TradeProcedure.decisionId,
      DecisionOptionRef.Denizen(wrestlers))
    val end = Look(done.state)
    assertEquals(end.tokensOn(mirror), Tokens(0, 1))
    assertEquals(end.faceUpSecrets(p1), 0)
    // 1, and 1 for the plain Discord adviser, from the Discord bank.
    assertEquals(end.favor(p1), Look(ready).favor(p1) + 2)
    assertEquals(end.bankFavor(Suit.Discord), 1)
    assertEquals(end.bankFavor(Suit.Order), Look(ready).bankFavor(Suit.Order))
    assert(Outcomes.ready(done.state).game.current.turn.usedPowers
      .contains(SpitefulMirror.useRef))
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events),
      Vector(NoteText.Said("acting",
        s"Acting as if ${wrestlers.value} is a Discord card.",
        covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events),
      Outcomes.ready(done.state))
    assertWireRoundTrips(done.events)

  test("paid for this turn, it is offered free, and picking it pays " +
      "nothing"):
    val ready = alreadyPaid(mirrorAtWrestlers(secrets = 1).ready)
    val asked = begun(ready, ActionRef.Trade, forFavor)
    assert(optionStates(asked).exists(_.contains(
      (mirror.value, "available", None))), optionStates(asked).toString)
    val done = answeredWith(mirrorPicked(asked,
      PowerWindow.TradeModifierSelection), TradeProcedure.decisionId,
      DecisionOptionRef.Denizen(wrestlers))
    val end = Look(done.state)
    assertEquals(end.tokensOn(mirror), Tokens(0, 0))
    // Only the Trade's own secret.
    assertEquals(end.faceUpSecrets(p1), 0)
    assertEquals(end.bankFavor(Suit.Discord), 1)

  test("unpaid, with no secret, it is unavailable with the shortfall"):
    val asked = begun(mirrorAtWrestlers(secrets = 0).ready, ActionRef.Muster,
      Vector.empty)
    assert(optionStates(asked).exists(_.contains((mirror.value,
      "unavailable", Some("Needs 1 secret; you have 0")))),
      optionStates(asked).toString)

  test("a Muster counts the Discord advisers"):
    val ready = mirrorAtWrestlers().favor(p1, 1).ready
    val done = answeredWith(mirrorPicked(begun(ready, ActionRef.Muster,
      Vector.empty), PowerWindow.MusterModifierSelection),
      MusterProcedure.decisionId, DecisionOptionRef.Denizen(wrestlers))
    // 1, and 1 for the plain Discord adviser; no Order adviser.
    assertEquals(Look(done.state).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 2)

  test("a Trade for secrets counts the Discord advisers"):
    val ready = mirrorAtWrestlers(secrets = 1).favor(p1, 2).ready
    val done = answeredWith(mirrorPicked(begun(ready, ActionRef.Trade,
      forSecrets), PowerWindow.TradeModifierSelection),
      TradeProcedure.decisionId, DecisionOptionRef.Denizen(wrestlers))
    // The Mirror's secret paid, then 1 gained for the Discord adviser.
    assertEquals(Look(done.state).faceUpSecrets(p1), 1)

  test("Defame reads the Trade's card as Discord"):
    val ready = mirrorAtWrestlers().adviser(p1, defame).favor(p2, 2).ready
    val bothPicked = answeredWith(mirrorPicked(begun(ready, ActionRef.Trade,
      forFavor), PowerWindow.TradeModifierSelection),
      modifiers(PowerWindow.TradeModifierSelection),
      DecisionOptionRef.Denizen(defame))
    val asked = answeredWith(bothPicked, TradeProcedure.decisionId,
      DecisionOptionRef.Denizen(wrestlers))
    assert(PhasePowerHarness.offered(asked, p1).exists(
      _.contains("player" -> p2.value)),
      PhasePowerHarness.offered(asked, p1).toString)
```

If a walk does not start by itself after the last pick, because another modifier is still offered, answer `ModifierPicks.start` at the modifier decision first. Check the option ids and kinds against `GolemLegionsSuite` and `SmallFriendsSuite`.

- [ ] **Step 2: Run the suite to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.SpitefulMirrorSuite"`
Expected: compilation fails, because `SpitefulMirror` does not exist.

- [ ] **Step 3: Let a modifier price its own pick**

In `SelectedModifier.scala`, `selectionPayments` loses its `final`:

```scala
  override def selectionPayments(ready: ReadyGame, actor: PlayerId)
      : Vector[CoreOperation] =
    if cost == Cost.free then Vector.empty else Vector(payment(actor))
```

Add to its doc, or above it: "A modifier whose price depends on the state, such as Spiteful Mirror's once per turn, overrides it and `selectable`."

- [ ] **Step 4: Write the power**

Create `src/main/scala/oathdigital/gameplay/powers/economy/SpitefulMirror.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{PowerAccess, SpitefulMirrorCard}
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{NoteSupport, SelectedModifier}
import oathdigital.model._

/** Spiteful Mirror (relic R41), a selected Trade and Muster modifier:
  * "[secret] This turn, you may act as if any cards you use to trade or
  * muster are [suit-discord] cards." (catalog batch 8 rulings, slice 8).
  *
  * Picked, it turns the source card Discord: `EffectiveSuit.traded` reads
  * the pick in the walk's answers, so the base gain, its matching advisers
  * and every reader of the source see Discord. The power itself only
  * prices the pick and writes the line.
  *
  * Its first pick in a turn pays 1 secret onto the relic and records the
  * use (`useRef`); later picks that turn are free. It is offered only while
  * its player holds the relic.
  *
  * Its line comes once the source is chosen, before the cost: "Acting as if
  * {card} is a Discord card."
  */
final case class SpitefulMirror private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  import SpitefulMirror._

  val cardId: RelicId = SpitefulMirrorCard.id
  def id: PowerId = SpitefulMirror.id
  def actions: Set[MajorActionType] =
    Set(MajorActionType.Trade, MajorActionType.Muster)
  override def cost: Cost = SpitefulMirrorCard.power.cost
  override def noteKeys: Vector[NoteKey] = Vector(acting)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeCost -> Vector(Transform((_, children) =>
      line(TradeProcedure.decisionId) +: children)),
    PowerWindow.MusterCost -> Vector(Transform((_, children) =>
      line(MusterProcedure.decisionId) +: children)))

  override def selectable(ready: ReadyGame, actor: PlayerId,
      answered: Vector[Answered]): Boolean =
    if used(ready) then
      PowerAccess.locate(ready, actor, cardId, answered).isDefined
    else super.selectable(ready, actor, answered)

  override def selectionPayments(ready: ReadyGame, actor: PlayerId)
      : Vector[CoreOperation] =
    if used(ready) then Vector.empty
    else Vector(payment(actor), RecordPowerUse(useRef))

  private def used(ready: ReadyGame): Boolean =
    ready.game.current.turn.usedPowers.contains(useRef)

  /** "Acting as if {card} is a Discord card.", naming the answer to the
    * action's source decision `decisionId`. */
  private def line(decisionId: String): Operation = Note(id, states =>
    NoteSupport.answer(states, decisionId).collect {
      case DecisionOptionRef.Denizen(card) => card: CardId
      case DecisionOptionRef.Edifice(card) => card: CardId
    }.map(card => acting(PowerSourceRef.Card(cardId), NoteArg.Card(card))))

object SpitefulMirror:
  val id: PowerId = SpitefulMirrorCard.power.id
  /** The record of this turn's payment. */
  val useRef: PowerUseRef = PowerUseRef(PowerTiming.Act,
    PowerSourceRef.Card(SpitefulMirrorCard.id), id)
  /** "Acting as if {card} is a Discord card." */
  val acting: NoteKey = NoteKey("acting", Vector(
    NotePart.Text("Acting as if "), NotePart.Arg(0),
    NotePart.Text(" is a Discord card.")))

  def forCatalog(catalog: ExecutableCatalog): SpitefulMirror =
    new SpitefulMirror(catalog)
```

- [ ] **Step 5: Register it**

In `ActionModifiers.scala`, add `SpitefulMirror` to the `economy` import, in alphabetical order. After `Vector(Downtrodden.forCatalog(catalog)) ++`, add:

```scala
      Vector(SpitefulMirror.forCatalog(catalog)) ++
```

In `PowerKindsCatalogSuite`, add `"relic.spiteful-mirror"` to `modifiers`, after `"denizen.secret-signal"`.

- [ ] **Step 6: Run the tests to see them pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.SpitefulMirrorSuite oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: PASS, 6 new tests.

If the line test finds no note, the `Note` may not see the source answer in `states.answered`. Check `NoteStates` and how `Defame`'s note reads its answer.

Run: `./sbtw test`
Expected: PASS, the baseline plus 18.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/SpitefulMirror.scala \
  src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/SpitefulMirrorSuite.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(batch8): Spiteful Mirror turns a Trade or Muster card Discord"
```

---

### Task 6: Gates and docs

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Check the client**

Run: `grep -rn "Partition\|partition" frontend/src/main/scala | head -20`

Family Wagon's question is a Partition asked by a trigger follow-up, outside any action panel the client may key on. If the client shows a Partition only inside a Search or a Forge, it must show this one too. If you find such a case, stop and report it before changing the client.

- [ ] **Step 2: Run the gates**

1. `./sbtw "test" "frontend/test"`. Expected: PASS, the server count the baseline plus 18, the frontend count unchanged.
2. `./sbtw frontend/fullOptJS`. Expected: success.
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`. Expected: no finding.

- [ ] **Step 3: Mark the slice done**

In the design's Status line, change "Slices 1 to 7 are done." to "Slices 1 to 8 are done."

In `docs/ROADMAP.md`:
- check the box of `**8. Effective suit:**`;
- in the Now section, change "slices 1 to 7 are done, and slice 8 is next" to "slices 1 to 8 are done, and slice 9 is next".

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md \
  docs/ROADMAP.md
git commit -m "docs(batch8): slice 8 done"
```
