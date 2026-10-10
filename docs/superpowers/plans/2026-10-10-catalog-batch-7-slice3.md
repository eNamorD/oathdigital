# Catalog Batch 7, Slice 3 (Amounts and Fixed Faces) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement three battle plans whose user chooses an amount: Arcane Armor (206), Unstable Summon (225) and Mountain Giant (155).

**Architecture:**

- **A fixed-faces operation.** `SetDieFaces(pool, faces)` appends `faces` to the pool's recorded outcome and lowers the pool's dice by as many, so the roll that follows rolls only the rest. A new outcome starts with no throw counted (`throws = 0`), so the roll after it is throw 1 and the set dice are no part of its throw: no reroll changes them.
- **An amount inside a battle plan.** A plan's offer may carry a `PlanAmount`: the question its application asks the plan's user, how to read the amount from the answer, what each unit of it costs in secrets, and what the amount does. `CampaignPlanApplication` asks it after the plan's fixed costs, sacrifice and fixed effects, then pays for and applies the amount. So the plan window's dry run still pays the fixed costs, and stops at the question.
- **Its price.** An amount that costs secrets shows as "Cost: X secrets" (`OptionPrice.secretsX`). A plan with no amount possible is shown unavailable with why, through the offer's `withheld`.
- **The three plans.**
  - Unstable Summon asks a `ChooseAmount` and burns that many secrets from the Darkest Secret for as many attack dice.
  - Arcane Armor asks a `ChooseAmount` that costs a secret each. At the defense roll, a `wrapping` sets that many dice to double shields before the roll.
  - Mountain Giant pays its fixed secret, then asks a `ChooseOne` of one or three dice. It is discarded after the Campaign only when three was chosen.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md`: "Goal and scope", "The bar", "Catalog corrections", "Slice 1: Rerolls" and "Slice 2: Recover rerolls" (with their plan-time rulings), "Slice 3: Amounts and fixed faces", "Seams used for the first time" and "Testing".
- `docs/superpowers/specs/2026-10-09-catalog-batch-7-rulings.md`: "Rules that apply to the whole batch" and the three slice 3 entries.

Task 1 amends both with this plan's rulings, so read them again after Task 1.

**Starts from:** `main` with catalog batch 7 slices 1 and 2 merged. In `docs/ROADMAP.md`, "### Phase - Catalog batch 7 - Campaign dice" must show `- [x] Slice 1` and `- [x] Slice 2`, and `RollOutcome` in `src/main/scala/oathdigital/model/DicePoolState.scala` must have a `throws` field. If not, stop: this plan relies on slice 2's throw count.

Slices 1 and 2 were written from plans, and their executors may have adapted names or line positions. Where this plan quotes their code, read the file as it is on `main` and apply the change to what is there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails it.
  - Each file below lists the imports it needs.
  - A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). `WalkerOperationCodec.scala` is at 792 before this slice and 799 after Task 2; keep its new arms to the lines this plan gives.
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker sources.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Names, exactly:**
  - Operation: `SetDieFaces(pool: PoolKey, faces: Vector[DieFace])`, wire kind `"set-die-faces"`.
  - Model: `PlanAmount`, and `CampaignPlanOffer.amount: Option[PlanAmount] = None`.
  - Builders: `PlanAmounts.range`, `PlanAmounts.oneOf`, `PlanAmounts.button(amount)` (`DecisionOptionRef.Button("amount-{n}")`) and `PlanAmounts.chosen`.
  - Price: `OptionPrice.secretsX: Boolean = false`.
  - Decision ids: `campaign.unstable-summon`, `campaign.arcane-armor`, `campaign.mountain-giant`, each built from `CampaignProcedure.decisionPrefix`.
- **Texts, exactly:**
  - Mountain Giant's printed text: "±1 [attack-die] or ±3 [attack-die] At end, discard."
  - Unstable Summon's question: heading "Unstable Summon: burn how many secrets from the Darkest Secret, for an attack die each?", button "Burn". Its reason when unusable: "Needs the Darkest Secret with a secret on it".
  - Arcane Armor's question: heading "Arcane Armor: set how many defense dice to double shields, for a secret each?", button "Pay and set". Its reasons when unusable: "Needs 1 secret; you have 0", then "No defense dice to set".
  - Mountain Giant's question: heading "Mountain Giant: add 1 or 3 attack dice?" ("remove" for a defender); buttons "Add 1 attack die" and "Add 3 attack dice, and discard Mountain Giant after the Campaign" ("Remove …" for a defender).
  - The price line of an amount: "Cost: X secrets".
  - Lines:
    - "Unstable Summon: Burned 3 secrets from the Darkest Secret for 3 attack dice."
    - "Arcane Armor: Set 2 defense dice to double shields."
    - "Mountain Giant: Added 3 attack dice." ("Removed 3 attack dice." for a defender)
    - Mountain Giant's discard line, "Discarded after the Campaign.", only on three.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 22 server tests and no frontend test.
  - It changes these existing tests: `KindredWarriorsSuite` and `SaltTheEarthSuite` use Search Party where they used Mountain Giant as a card with no plan (Task 5), and `WalkerOperationWireSuite` round-trips one more operation (Task 2).
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

The product owner reviews these with the plan. Task 1 writes all of them into the spec.

1. **Where the question sits.** The design's bar says a plan asks "before it pays". This plan asks after the plan's fixed costs, its sacrifice and its fixed effects, then pays for and applies the amount. The plan window's dry run stops at the first question, so a fixed cost asked after it could be neither priced nor refused. Mountain Giant pays its secret, then asks one or three. Arcane Armor and Unstable Summon have no fixed cost, so for them this is "before it pays".
2. **Each plan asks its own decision.** `campaign.unstable-summon`, `campaign.arcane-armor` and `campaign.mountain-giant`, asked of the plan's user: a defender is asked inside the attacker's walk, as the defender's plan decision is. Under the Campaign prefix the Game Log is silent on the answer, and the plan writes its own line instead.
3. **An X price, and when it is withheld.** An amount that costs secrets shows "Cost: X secrets". Arcane Armor's X counts faceup secrets, the ones a payment can spend. With no faceup secret it is shown unavailable, "Needs 1 secret; you have 0"; with secrets but an empty defense pool, "No defense dice to set".
4. **Set dice are no part of a throw.** `SetDieFaces` creates the pool's outcome with no throw counted, so the defense roll after it is throw 1 (`campaign.defense.reroll.1`), and its throw is only the rolled dice. Jinx rerolls those and not the set ones. When every die is set, nothing is rolled and no reroll is asked. Arcane Armor's line comes at the roll, before the roll line, which names only the rolled dice. If no die is left to set, nothing is set and no line is written.
5. **Unstable Summon is shown unavailable, not hidden.** The ruling said it is "offered only while" its user holds the Darkest Secret with a secret on it. This plan shows it unavailable otherwise, "Needs the Darkest Secret with a secret on it", following the standing preference for hover reasons over hidden options.
6. **Mountain Giant's line.** The ruling said it "writes the usual plan dice line", but no plan writes one: the activation line names the card and the dice change is silent. Since its question is silent too, Mountain Giant writes "Added 3 attack dice." ("Removed 3 attack dice." for a defender). The line names no player, as Jinx's line on a defense roll names none: the card names whose plan it is.
7. **Mountain Giant's text.** The card leaves out the icon after "±3"; the holding catalog's text now reads "±1 [attack-die] or ±3 [attack-die] At end, discard." (product owner, 2026-10-09).

## File map

**The fixed-faces operation (Task 2):**
- `src/main/scala/oathdigital/model/CoreOperations.scala`: `SetDieFaces`.
- `src/main/scala/oathdigital/gameplay/operations/RollOperations.scala`: `setFaces`.
- `src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala`: applies it.
- `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`: its wire form.
- Tests: `RollOperationsSuite` gains three tests; `WalkerOperationWireSuite` round-trips it.

**An amount inside a battle plan, and Unstable Summon (Task 3):**
- `src/main/scala/oathdigital/model/CampaignTypes.scala`: `PlanAmount`, and `CampaignPlanOffer.amount`.
- `src/main/scala/oathdigital/gameplay/actions/campaign/PlanAmounts.scala`: new.
- `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanApplication.scala`: asks the amount and applies it.
- `src/main/scala/oathdigital/gameplay/powers/campaign/UnstableSummon.scala`: new. `UnstableSummonCard` moves out of `catalog/holding/HoldingDiscordDenizens.scala`.
- Registration: `PlanRules.scala`, and the import in `NewFoundations.scala`.
- Tests: new `UnstableSummonSuite`.

**An amount that costs secrets, and Arcane Armor (Task 4):**
- `src/main/scala/oathdigital/model/CampaignTypes.scala`: `PlanAmount.secretsEach`.
- `src/main/scala/oathdigital/model/Decisions.scala`: `OptionPrice.secretsX`.
- `src/main/scala/oathdigital/gameplay/actions/campaign/PlanAmounts.scala`: `range` takes `secretsEach`.
- `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanApplication.scala`: pays for the amount.
- `src/main/scala/oathdigital/gameplay/actions/campaign/PlanPrice.scala`: prices it.
- `src/main/scala/oathdigital/application/PriceDetails.scala`: words it.
- `src/main/scala/oathdigital/gameplay/powers/campaign/ArcaneArmor.scala`: new. `ArcaneArmorCard` moves out of `catalog/holding/HoldingArcaneDenizens.scala`.
- Registration: `PlanRules.scala`, and the import in `NewFoundations.scala`.
- Tests: new `ArcaneArmorSuite`; `PricedOptionProjectionSuite` gains one test.

**A choice of amounts, and Mountain Giant (Task 5):**
- `src/main/scala/oathdigital/gameplay/actions/campaign/PlanAmounts.scala`: `oneOf` and `button`.
- `src/main/scala/oathdigital/gameplay/powers/campaign/MountainGiant.scala`: new. `MountainGiantCard` moves out of `catalog/holding/HoldingNomadDenizens.scala`, with its corrected text.
- Registration: `PlanRules.scala`, and the import in `NewFoundations.scala`.
- Tests: new `MountainGiantSuite`; `KindredWarriorsSuite` and `SaltTheEarthSuite` use Search Party.

**Roadmap (Task 6):** `docs/ROADMAP.md`, and the design's status.

---

### Task 1: Write the plan-time rulings into the spec

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md`
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-7-rulings.md`

- [ ] **Step 1: Amend the design**

1. In the **Status** paragraph, replace `Slices 1 and 2 are done;` with `Slices 1 and 2 are done and slice 3 is planned;`. Re-wrap the paragraph to 78 characters.

2. In "The bar", replace the bullet that begins `- **A fixed-faces operation** (slice 3).` with:

```markdown
- **A fixed-faces operation** (slice 3). `SetDieFaces(pool, faces)` appends
  `faces` to the pool's outcome and lowers the pool's count by as many, so
  the roll that follows rolls only the rest. A new outcome starts with no
  throw counted, so the set dice are no part of the roll's throw.
```

3. In "The bar", replace the bullet that begins `- **Amounts and choices inside a battle plan** (slice 3).` with:

```markdown
- **Amounts and choices inside a battle plan** (slice 3). A plan's offer
  may carry a `PlanAmount`: a `ChooseAmount` or a `ChooseOne` its
  application asks its user after the plan's fixed costs, then the secrets
  each unit costs and what the amount does, as Sticky Fire and Wild Mounts
  ask a `ChooseOne` after. An amount that costs secrets is priced "Cost: X
  secrets".
```

4. In "### Catalog corrections", append:

```markdown
- **Mountain Giant**'s printed "±3" has no icon after it; it is
  `[attack-die]` (product owner, 2026-10-09). Slice 3 corrects the text in
  the holding catalog.
```

5. Append to "## Slice 3: Amounts and fixed faces", after its last paragraph:

```markdown
### Plan-time rulings

- **Where the question sits.** A plan asks its amount after its fixed
  costs, its sacrifice and its fixed effects, then pays for and applies the
  amount. The plan window's dry run stops at the question, so a fixed cost
  after it could be neither priced nor refused. Mountain Giant pays its
  secret, then asks one or three.
- **Each plan asks its own decision**: `campaign.unstable-summon`,
  `campaign.arcane-armor` and `campaign.mountain-giant`, asked of the
  plan's user. The Game Log is silent on the answer, and the plan writes
  its own line.
- **An X price.** An amount that costs secrets shows "Cost: X secrets".
  Arcane Armor's X counts faceup secrets. With none it is shown
  unavailable, "Needs 1 secret; you have 0"; with an empty defense pool,
  "No defense dice to set".
- **Set dice are no part of a throw.** The defense roll after
  `SetDieFaces` is throw 1, and its throw is only the rolled dice, so
  Jinx rerolls those and not the set ones. When every die is set, nothing
  is rolled and no reroll is asked. Arcane Armor's line comes at the roll,
  before the roll line, which names only the rolled dice.
- **Unstable Summon is shown unavailable, not hidden,** without the
  Darkest Secret or with no secret on it: "Needs the Darkest Secret with a
  secret on it".
- **Mountain Giant's line.** No plan writes a dice line of its own, and
  its question is silent, so Mountain Giant writes "Added 3 attack dice."
  ("Removed 3 attack dice." for a defender).
```

- [ ] **Step 2: Amend the rulings appendix**

1. In the Unstable Summon entry (`- **225 Unstable Summon.**`), replace `offered only while its user holds the Darkest Secret with at least one secret on it.` with `usable only while its user holds the Darkest Secret with at least one secret on it, and shown unavailable otherwise ("Needs the Darkest Secret with a secret on it").` Re-wrap the entry, keeping its indentation.

2. In the Mountain Giant entry (`- **155 Mountain Giant.**`), replace `It writes the usual plan dice line, and the discard line only on three.` with `It pays its secret, then asks one or three. Line: "Mountain Giant: Added 3 attack dice." ("Removed 3 attack dice." for a defender), and the discard line only on three.` Re-wrap the entry, keeping its indentation.

3. In the Arcane Armor entry (`- **206 Arcane Armor.**`), insert before `Line:` the sentence `Its user counts only faceup secrets. The set dice are not part of the roll's throw, so no reroll changes them.`. Re-wrap the entry, keeping its indentation.

- [ ] **Step 3: Check the links and commit**

Run: `python3 scripts/check-markdown-links.py`
Expected: pass.

```bash
git add docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md docs/superpowers/specs/2026-10-09-catalog-batch-7-rulings.md
git commit -m "docs(batch7): record slice 3 plan-time rulings"
```

---

### Task 2: The fixed-faces operation

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala` (after `Reroll`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/RollOperations.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala` (beside the `Reroll` arm)
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`
- Test: `src/test/scala/oathdigital/gameplay/operations/RollOperationsSuite.scala`, `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala`

**Interfaces:**
- Consumes: `RollOutcome(pool, count, faces, skulls, score, throws, rerolled)` and `RollOutcome.derive` (slice 2).
- Produces: `final case class SetDieFaces(pool: PoolKey, faces: Vector[DieFace]) extends PrimitiveOperation`, and `RollOperations.setFaces(ready: ReadyGame, pool: PoolKey, faces: Vector[DieFace]): Either[OperationError, ReadyGame]`. Task 4's Arcane Armor emits `SetDieFaces` as a leaf.

- [ ] **Step 1: Write the failing tests**

In `RollOperationsSuite`, add beside the class's existing `import AttackDieFace.{…}` line:

```scala
  import DefenseDieFace.{Blank, Doubler, TwoShields}
  private val defense = PoolKey("campaign.defense")

  /** The quiet table with the defense pool holding `dice` dice, and
    * `faces` recorded for it, if any. */
  private def defensePool(dice: Int, faces: Vector[DieFace] = Vector.empty)
      : ReadyGame =
    val (skulls, score) = RollOutcome.derive(faces)
    Table.start.ready.updateCurrent(current => current.copy(
      rollPools = Map(defense -> DicePoolState(dice)),
      rollOutcomes =
        if faces.isEmpty then Map.empty
        else Map(defense ->
          RollOutcome(defense, faces.size, faces, skulls, score))))
```

If an import already names any of these faces, merge rather than repeat it. Then append the tests:

```scala
  test("set faces start an outcome with no throw, and the pool holds " +
      "fewer dice"):
    val after = RollOperations.setFaces(defensePool(3), defense,
      Vector(TwoShields, TwoShields)).toOption.get
    assertEquals(after.game.current.rollPools(defense), DicePoolState(1))
    assertEquals(after.game.current.rollOutcomes(defense), RollOutcome(
      defense, 2, Vector(TwoShields, TwoShields), 0, 4, throws = 0))

  test("set faces join an outcome already recorded and rescore it"):
    // The Doubler rolled before doubles the set shields too.
    val after = RollOperations.setFaces(defensePool(1, Vector(Doubler)),
      defense, Vector(TwoShields)).toOption.get
    val outcome = after.game.current.rollOutcomes(defense)
    assertEquals(outcome.faces, Vector[DieFace](Doubler, TwoShields))
    assertEquals(outcome.score, 4)
    assertEquals(outcome.count, 2)
    assertEquals(outcome.throws, 1)
    assertEquals(after.game.current.rollPools(defense), DicePoolState(0))

  test("setting no dice, more than the pool holds, or faces of the other " +
      "kind fails"):
    assert(RollOperations.setFaces(defensePool(1), defense,
      Vector.empty).isLeft)
    assert(RollOperations.setFaces(defensePool(1), defense,
      Vector(TwoShields, TwoShields)).isLeft)
    assert(RollOperations.setFaces(defensePool(1, Vector(Blank)), defense,
      Vector(OneSword)).isLeft)
```

In `WalkerOperationWireSuite`, test "every CoreOperation variant round-trips through the walker codec", add after the line `Reroll(PoolKey("campaign.attack"), Vector(1)),`:

```scala
      SetDieFaces(PoolKey("campaign.defense"),
        Vector(DefenseDieFace.TwoShields, DefenseDieFace.TwoShields)),
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.RollOperationsSuite oathdigital.serialization.WalkerOperationWireSuite"`
Expected: compile failure, `Not found: SetDieFaces` and `value setFaces is not a member`.

- [ ] **Step 3: Add the operation**

In `CoreOperations.scala`, after `Reroll`, add:

```scala
/** Sets dice of `pool` to `faces` instead of rolling them (catalog batch 7,
  * slice 3: Arcane Armor). The faces join the pool's recorded outcome,
  * which starts with no throw counted when the pool has not rolled, and the
  * pool holds as many dice fewer, so the roll that follows rolls only the
  * rest. The set dice are no part of that roll's throw, so no reroll
  * changes them. Setting no dice, more dice than the pool holds, or faces
  * of another kind than the outcome's is an error.
  */
final case class SetDieFaces(pool: PoolKey, faces: Vector[DieFace])
    extends PrimitiveOperation:
  override val required: Boolean = true
```

In `RollOperations.scala`, update the object's doc comment to `/** The \`Reroll\` and \`SetDieFaces\` writes: rerolled faces replace recorded ones in place, and set faces join them. */`, then add after `reroll`:

```scala
  /** Set faces join the pool's outcome; a pool that has not rolled starts
    * one with no throw counted, so the roll after it is the first throw.
    * The skulls of the set faces are added to what the outcome records, and
    * the score is worked out again from every face, as `reroll` does. The
    * pool holds as many dice fewer.
    */
  def setFaces(ready: ReadyGame, pool: PoolKey, faces: Vector[DieFace])
      : Either[OperationError, ReadyGame] =
    val current = ready.game.current
    val held = current.rollPools.get(pool).fold(0)(_.count)
    val before = current.rollOutcomes.get(pool)
    val all = before.fold(Vector.empty[DieFace])(_.faces) ++ faces
    val (skulls, _) = RollOutcome.derive(faces)
    if faces.isEmpty || faces.size > held then Left(InvalidDescription(
      s"setting ${faces.size} dice of ${pool.value}, which holds $held"))
    else if !sameFamily(all) then Left(InvalidDescription(
      s"setting dice of ${pool.value} mixes attack and defense faces"))
    else
      val score = RollOutcome.derive(all)._2
      val outcome = before.fold(RollOutcome(pool, faces.size, faces, skulls,
        score, throws = 0))(previous => previous.copy(
          count = previous.count + faces.size, faces = all,
          skulls = previous.skulls + skulls, score = score))
      Right(ready.updateCurrent(state => state.copy(
        rollPools = state.rollPools.updated(pool,
          DicePoolState(held - faces.size)),
        rollOutcomes = state.rollOutcomes.updated(pool, outcome))))
```

`RollOutcome.derive` returns `(skulls, score)`; the refutable-pattern rule does not apply to a tuple pattern, which is irrefutable.

In `OperationApplication.scala`, after the `Reroll` arm, add:

```scala
      case (result, SetDieFaces(pool, faces)) =>
        result.flatMap(RollOperations.setFaces(_, pool, faces))
```

- [ ] **Step 4: Give it a wire form**

In `WalkerOperationCodec.scala`:

1. In `private type TurnOperation`, add `SetDieFaces` after `Reroll`. Wrap the union over three lines if it no longer fits two:

```scala
  private type TurnOperation = AdvanceVisionsDrawn.type | BeginTurn | EnterPhase |
    SetOathkeeper | RecordPowerUse | RecordCampaignResult | ModifyDicePool | Roll |
    ModifyRollOutcome | Reroll | SetDieFaces | QueueProcedure | Defer |
    TakeDeferred
```

2. In `encodeOperation`, after the `Reroll` arm, add:

```scala
      case SetDieFaces(pool, faces) => ujson.Obj(
        "kind" -> "set-die-faces", "pool" -> pool.value,
        "faces" -> ujson.Arr.from(faces.map(encodeDieFace)))
```

3. In `decodeTurnOperation`, after the `"reroll"` case, add:

```scala
    case "set-die-faces" => traverse(value("faces").arr.toVector)(face =>
      decodeDieFace(face.str, s"$path.faces")).map(faces =>
      SetDieFaces(PoolKey(value("pool").str), faces))
```

Run: `wc -l src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`
Expected: 800 or fewer.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.RollOperationsSuite oathdigital.serialization.WalkerOperationWireSuite"`
Expected: PASS.

Then run `./sbtw test`. Every `CoreOperation` match the compiler checks for exhaustiveness now covers `SetDieFaces`; if one does not, add the arm it reports beside that match's `Reroll` arm.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/model/CoreOperations.scala src/main/scala/oathdigital/gameplay/operations/RollOperations.scala src/main/scala/oathdigital/gameplay/operations/OperationApplication.scala src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala src/test/scala/oathdigital/gameplay/operations/RollOperationsSuite.scala src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala
git commit -m "feat(dice): a fixed-faces operation, set before a roll"
```

---

### Task 3: An amount inside a battle plan, and Unstable Summon

**Files:**
- Modify: `src/main/scala/oathdigital/model/CampaignTypes.scala` (`CampaignPlanOffer`, and a new `PlanAmount` before it)
- Create: `src/main/scala/oathdigital/gameplay/actions/campaign/PlanAmounts.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanApplication.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/UnstableSummon.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala` (remove `UnstableSummonCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala` (register)
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/UnstableSummonSuite.scala` (new)

**Interfaces:**
- Consumes: `BattlePlan`, `PlanContext`, `CampaignPlanOffer`, `CampaignPlanEffect`, `PlanDriver` (tests), `Burn.secrets`, `Table.darkestSecret(holder, secrets)` (tests).
- Produces:
  - `final case class PlanAmount(decisionId: String, query: DecisionQuery, amountOf: DecisionAnswer => Option[Int], effects: Int => Vector[CampaignPlanEffect])` in `oathdigital.model`.
  - `CampaignPlanOffer.amount: Option[PlanAmount] = None`.
  - `object PlanAmounts` in `oathdigital.gameplay.actions.campaign`, with `range(decisionId: String, min: Int, max: Int, heading: String, confirm: String)(effects: Int => Vector[CampaignPlanEffect]): PlanAmount` and `chosen(pending: PendingTree, amount: PlanAmount): Option[Int]`.
  - `case object UnstableSummon extends BattlePlan`, with `decisionId`, `heading`, `confirm`, `noBanner` and `burned`.

- [ ] **Step 1: Write the failing test**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/UnstableSummonSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.testkit.{CatalogNames, Look}

/** Unstable Summon: a free attacker's plan that burns X secrets from the
  * Darkest Secret its user holds for X attack dice. It is the first plan
  * that asks an amount (catalog batch 7, slice 3). */
class UnstableSummonSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Unstable Summon")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val onBanner =
    PositionedLocation(Location.OnBanner(Banner.DarkestSecret))

  private def lines(run: Run): Vector[NoteText.Said] =
    said(UnstableSummon, run.events)

  private def burned(text: String): NoteText.Said = NoteText.Said("burned",
    s"Burned $text.", covers = false)

  /** p1 attacks the bandit-ruled origin holding Unstable Summon faceup; the
    * Darkest Secret, with `bannerSecrets` on it, is held by p1, or by p2
    * when `attackerHoldsBanner` is false. */
  private def attackerWithSummon(bannerSecrets: Int,
      attackerHoldsBanner: Boolean = true): Board =
    val base = withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp)
    on(base)(_.darkestSecret(Some(
      if attackerHoldsBanner then base.actor else base.rival), bannerSecrets))

  test("the attacker burns every secret on the Darkest Secret for as many " +
      "attack dice, for nothing"):
    val b = attackerWithSummon(bannerSecrets = 3)
    val run = commit(rules(winningDice), b, 2)
    assert(run.offered(b.actor).contains(ref))
    assert(!run.options(b.actor).exists {
      case priced: DecisionOption.Priced => priced.ref == ref
      case _ => false
    })
    val asked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(awaits(asked, b.actor, UnstableSummon.decisionId))
    assertEquals(asked.query(b.actor), DecisionQuery.ChooseAmount(1, 3,
      Some(UnstableSummon.heading), UnstableSummon.confirm))
    val done = asked.answer(b.actor, UnstableSummon.decisionId,
      ChooseAmountAnswer(3))
    assertEquals(Look(done.state).secretsOnDarkestSecret, 0)
    assert(done.since(asked).contains(Burn.secrets(3, onBanner)))
    assert(done.since(asked).contains(
      ModifyDicePool(CampaignIds.attackPool, 3)))
    assert(!done.since(run).exists(_.isInstanceOf[PayCost]))
    assertEquals(lines(done), Vector(burned(
      "3 secrets from the Darkest Secret for 3 attack dice")))

  test("X may be as low as one, leaving the rest on the banner"):
    val b = attackerWithSummon(bannerSecrets = 3)
    val done = commit(rules(winningDice), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .answer(b.actor, UnstableSummon.decisionId, ChooseAmountAnswer(1))
    assertEquals(Look(done.state).secretsOnDarkestSecret, 2)
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, 1)))
    assertEquals(lines(done), Vector(burned(
      "1 secret from the Darkest Secret for 1 attack die")))

  test("without the Darkest Secret, or with no secret on it, it is shown " +
      "unavailable"):
    // Longbows keeps the attacker's plan decision asked.
    for b <- Vector(attackerWithSummon(3, attackerHoldsBanner = false),
        attackerWithSummon(0)) do
      val asked = withAdviser(b, "Longbows", Orientation.FaceUp)
      assertEquals(commit(rules(winningDice), asked, 2).withheld(b.actor,
        ref), Some(OptionReason.Written(UnstableSummon.noBanner)))
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.UnstableSummonSuite"`
Expected: compile failure, `Not found: UnstableSummon`.

- [ ] **Step 3: Add the amount to the model**

In `CampaignTypes.scala`, before `CampaignPlanOffer`'s doc comment, add:

```scala
/** The amount a battle plan asks its user for as it is applied (catalog
  * batch 7, slice 3). The plan's application asks `query` after the plan's
  * fixed costs, its sacrifice and its fixed effects, so a dry run of the
  * plan still pays what is fixed and stops at the question. `amountOf`
  * reads the amount from the answer, and `effects` are what that amount
  * does. A bandit defender is asked nothing, so its plan does nothing more.
  */
final case class PlanAmount(decisionId: String, query: DecisionQuery,
    amountOf: DecisionAnswer => Option[Int],
    effects: Int => Vector[CampaignPlanEffect])
```

In `CampaignPlanOffer`'s doc comment, append a paragraph:

```scala
  *
  * `amount` is the question the plan asks before what depends on its
  * answer (Unstable Summon's X). An offer is rebuilt when a walk resumes at
  * the question, so an amount's bounds may read the user's resources only
  * when the plan pays nothing before asking.
```

and add the field after `alone`:

```scala
    withheld: Option[OptionReason] = None, alone: Boolean = false,
    amount: Option[PlanAmount] = None)
```

- [ ] **Step 4: Add the builders**

Create `src/main/scala/oathdigital/gameplay/actions/campaign/PlanAmounts.scala`:

```scala
package oathdigital.gameplay.actions.campaign

import oathdigital.model._

/** The amounts battle plans ask for (`PlanAmount`), and the read of the
  * answer (catalog batch 7, slice 3). */
object PlanAmounts:
  /** An amount from `min` to `max`, asked as a `ChooseAmount`. */
  def range(decisionId: String, min: Int, max: Int, heading: String,
      confirm: String)(effects: Int => Vector[CampaignPlanEffect])
      : PlanAmount =
    PlanAmount(decisionId,
      DecisionQuery.ChooseAmount(min, max, Some(heading), confirm), {
        case DecisionAnswer.ChooseAmountAnswer(amount) => Some(amount)
        case _ => None
      }, effects)

  /** The amount the plan's question was answered with in this walk. */
  def chosen(pending: PendingTree, amount: PlanAmount): Option[Int] =
    pending.answered.reverseIterator.collectFirst {
      case Answered(id, answer, _) if id == amount.decisionId => answer
    }.flatMap(amount.amountOf)
```

- [ ] **Step 5: Ask the amount in the application**

In `CampaignPlanApplication.scala`:

1. In the class doc comment, replace `a slot each for revealing the source, paying, sacrificing, and then one per effect,` with `a slot each for revealing the source, paying, sacrificing, then one per effect, then the amount question and what its answer does,`. After the bullet that begins `- A warband sacrifice asks`, add:

```scala
  *  - A plan with an amount asks it of its user last, so a dry run pays
  *    what is fixed and stops at the question, then applies what the answer
  *    does.
```

2. Replace `children` with:

```scala
  override val children: Vector[Operation] = Vector[Operation](
    Branch((ready, _) => reveal(ready)),
    Branch((_, _) => pay),
    Branch((ready, _) => sacrifice(ready))) ++
    offered.offer.effects.map(effect) ++ Vector[Operation](
      Branch((_, _) => ask),
      Branch((_, pending) => chosenEffects(pending)),
      Branch((_, _) => marker))
```

3. After `marker`, add:

```scala
  /** The plan's amount question, asked of its user. A bandit defender is
    * asked nothing. */
  private def ask: Vector[Operation] = (offered.offer.amount, user) match
    case (Some(amount), Some(player)) =>
      Vector(Decide(amount.decisionId, player, amount.query))
    case _ => Vector.empty

  /** What the answered amount does. */
  private def chosenEffects(pending: PendingTree): Vector[Operation] =
    offered.offer.amount.toVector.flatMap(amount =>
      PlanAmounts.chosen(pending, amount).toVector.flatMap(chosen =>
        amount.effects(chosen).map(effect)))
```

- [ ] **Step 6: Write Unstable Summon**

Delete `object UnstableSummonCard` from `HoldingDiscordDenizens.scala`. Create `src/main/scala/oathdigital/gameplay/powers/campaign/UnstableSummon.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignProcedure, PlanAmounts}
import oathdigital.model._

object UnstableSummonCard extends Denizen(DenizenId("225"),
    "Unstable Summon", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.unstable-summon"),
    persistent = false, cost = Cost.free,
    text = "If you hold the Darkest Secret, burn X [secret] from it to " +
      "collect X [attack-die].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Unstable Summon (card 225), an attacker's battle plan: "If you hold the
  * Darkest Secret, burn X [secret] from it to collect X [attack-die]."
  *
  * It is free. While its user holds the Darkest Secret with a secret on it,
  * they choose X, from one to the secrets on the banner: X secrets burn from
  * the banner, and X attack dice join the attack pool. Otherwise it is shown
  * unavailable.
  *
  * It writes "Burned {n secrets} from the Darkest Secret for {n} attack
  * dice."
  */
case object UnstableSummon extends BattlePlan:
  val cardId: DenizenId = UnstableSummonCard.id
  val id: PowerId = UnstableSummonCard.power.id
  /** Under the Campaign's prefix, so a parked question is a Campaign
    * decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix +
    "unstable-summon"
  val heading: String = "Unstable Summon: burn how many secrets from the " +
    "Darkest Secret, for an attack die each?"
  val confirm: String = "Burn"
  val noBanner: String = "Needs the Darkest Secret with a secret on it"
  /** "Burned {n secrets} from the {Darkest Secret} for {n} attack dice." */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Text("Burned "),
    NotePart.Arg(0), NotePart.Text(" from the "), NotePart.Arg(1),
    NotePart.Text(" for "), NotePart.Arg(2),
    NotePart.Plural(2, " attack die.", " attack dice.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = Vector(burned)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map { source =>
      val secrets = context.ready.game.current.banners.darkestSecret.secrets
      val offer = CampaignPlanOffer(source,
        "Unstable Summon: burn secrets for attack dice", Vector.empty,
        Vector.empty)
      if !context.holds(Banner.DarkestSecret) || secrets < 1 then
        offer.copy(withheld = Some(OptionReason.Written(noBanner)))
      else offer.copy(amount = Some(PlanAmounts.range(decisionId, 1,
        secrets, heading, confirm)(summon)))
    }

  /** X secrets burnt from the banner, X attack dice, and the line. */
  private def summon(count: Int): Vector[CampaignPlanEffect] = Vector(
    CampaignPlanEffect.Run(Vector(Burn.secrets(count,
      PositionedLocation(Location.OnBanner(Banner.DarkestSecret))))),
    CampaignPlanEffect.AddAttackDice(count),
    CampaignPlanEffect.Run(Vector(Note(id, _ => Some(burned(
      PowerSourceRef.Card(cardId), NoteArg.Amount(count, NoteUnit.Secret),
      NoteArg.Banner(Banner.DarkestSecret), NoteArg.Number(count)))))))
```

- [ ] **Step 7: Register it**

In `NewFoundations.scala`, add `UnstableSummonCard` to the `import oathdigital.gameplay.powers.campaign.{…}` list, keeping it in alphabetical order where the list is.

In `PlanRules.scala`, add after `Vector(MasterAtArms.forCatalog(catalog)) ++`:

```scala
      Vector(UnstableSummon) ++
```

and append to the object's doc comment's list, before ` and Herald`: `, Unstable Summon (an amount asked when applied)`.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.CampaignPlanWindowSuite oathdigital.catalog.* oathdigital.gameplay.cards.*"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/model/CampaignTypes.scala src/main/scala/oathdigital/gameplay/actions/campaign/PlanAmounts.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanApplication.scala src/main/scala/oathdigital/gameplay/powers/campaign/UnstableSummon.scala src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala src/test/scala/oathdigital/gameplay/powers/campaign/UnstableSummonSuite.scala
git commit -m "feat(batch7): Unstable Summon burns banner secrets for attack dice"
```

---

### Task 4: An amount that costs secrets, and Arcane Armor

**Files:**
- Modify: `src/main/scala/oathdigital/model/CampaignTypes.scala` (`PlanAmount`)
- Modify: `src/main/scala/oathdigital/model/Decisions.scala` (`OptionPrice`)
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/PlanAmounts.scala` (`range`)
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanApplication.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/PlanPrice.scala`
- Modify: `src/main/scala/oathdigital/application/PriceDetails.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/ArcaneArmor.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala` (remove `ArcaneArmorCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala` (register)
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/ArcaneArmorSuite.scala` (new), `src/test/scala/oathdigital/application/PricedOptionProjectionSuite.scala`

**Interfaces:**
- Consumes: `SetDieFaces` (Task 2); `PlanAmount`, `PlanAmounts.range`, the application's `ask` and `chosenEffects` (Task 3); `RerollChoice.decisionId(pool, number)` and Jinx (slice 1); `PowerStubs.ContributingStub` (tests).
- Produces:
  - `PlanAmount.secretsEach: Int = 0`, after `effects`.
  - `PlanAmounts.range(decisionId, min, max, heading, confirm, secretsEach: Int = 0)(effects)`.
  - `OptionPrice.secretsX: Boolean = false`, after `warbands`.
  - `case object ArcaneArmor extends BattlePlan`, with `decisionId`, `heading`, `confirm`, `noSecret`, `noDefenseDice` and `set`.

- [ ] **Step 1: Write the failing tests**

In `PricedOptionProjectionSuite`, append:

```scala
  test("an amount's price words X secrets"):
    assertEquals(PriceDetails.of(OptionPrice(secretsX = true)),
      Vector("Cost: X secrets"))
```

Create `src/test/scala/oathdigital/gameplay/powers/campaign/ArcaneArmorSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.dice.RerollChoice
import oathdigital.gameplay.powerresolver.{FoldPhase, Transform}
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{RollPayload, WalkerPowers,
  WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look}
import oathdigital.testkit.PowerStubs.ContributingStub

/** Arcane Armor: a defender's plan costing X secrets, which sets X defense
  * dice to double shields at the defense roll, before the rest are rolled.
  * It is the first plan whose amount costs secrets, and the first use of
  * fixed faces before a roll (catalog batch 7, slice 3). */
class ArcaneArmorSuite extends munit.FunSuite:
  import AttackDieFace.OneSword
  import DefenseDieFace.{Blank, TwoShields}
  private val card = CatalogNames.denizen("Arcane Armor")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val jinx = CatalogNames.denizen("Jinx")

  private def lines(run: Run): Vector[NoteText.Said] =
    said(ArcaneArmor, run.events)

  private def setLine(dice: String): NoteText.Said = NoteText.Said("set",
    s"Set $dice to double shields.", covers = false)

  /** Dice that roll four swords for the attack and `defense` for the
    * defense, failing on any other count. */
  private def fourSwordsAnd(defense: DefenseDieFace*) =
    dice(attack = Vector.fill(4)(OneSword), defense = defense.toVector)

  /** Production rules with `fourSwordsAnd(defense)`. */
  private def rolling(defense: DefenseDieFace*): OathRules =
    rules(fourSwordsAnd(defense*))

  /** p2 rules the origin, which prints two defense dice, with two warbands,
    * and holds the Oathkeeper title, Arcane Armor faceup and `secrets`
    * faceup secrets. */
  private def defenderWithArcaneArmor(secrets: Int): Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.secrets(base.rival, faceUp = secrets))

  /** p1 commits four warbands against `b`, and p2 chooses Arcane Armor. */
  private def chosen(b: Board, game: OathRules,
      powers: WalkerPowers = WalkerPowerCatalog.default(catalog)): Run =
    commit(game, b, 4, powers = powers)
      .pick(b.rival, CampaignIds.defenderPlan, ref)

  /** Every production power and `stub`. */
  private def productionAnd(stub: ContributingStub): WalkerPowers =
    val all = WalkerPowerCatalog.default(catalog)
    all.copy(powers = all.powers :+ stub)

  /** Rules with `powers` and `fourSwordsAnd(defense)`. */
  private def rollingWith(powers: WalkerPowers,
      defense: DefenseDieFace*): OathRules =
    new OathRules(catalog, walkerPowerCatalog = powers,
      walkerDice = fourSwordsAnd(defense*))

  /** A power that removes `n` defense dice once both pools are gathered. */
  private def removesDefenseDiceAfterGathering(n: Int): ContributingStub =
    ContributingStub(PowerId("test.removes-defense-dice"), Map(
      PowerWindow.CampaignGatherPools -> Vector(Transform((_, children) =>
        children :+ ModifyDicePool(CampaignIds.defensePool, -n),
        FoldPhase.Late))))

  /** A power that removes `n` defense dice just before the defense roll,
    * ahead of every other power's steps there. */
  private def removesDefenseDiceBeforeRoll(n: Int): ContributingStub =
    ContributingStub(PowerId("test.removes-defense-dice"), Map(
      PowerWindow.CampaignDefenseRoll -> Vector(Transform((_, children) =>
        ModifyDicePool(CampaignIds.defensePool, -n) +: children,
        FoldPhase.Late))))

  test("the defender pays a secret to set one defense die to double " +
      "shields, and the other is rolled"):
    val b = defenderWithArcaneArmor(secrets = 2)
    val asked = chosen(b, rolling(Blank))
    assert(awaits(asked, b.rival, ArcaneArmor.decisionId))
    val done = asked.answer(b.rival, ArcaneArmor.decisionId,
      ChooseAmountAnswer(1)).finishPlanWindows
    assertEquals(done.result.defenseFaces, Vector(TwoShields, Blank))
    // Two shields, plus the two warbands at the origin.
    assertEquals(done.result.defenseScore, 4)
    // Paid off turn, so the secret settles facedown at once.
    assertEquals(Look(done.state).faceUpSecrets(b.rival), 1)
    assertEquals(Look(done.state).faceDownSecrets(b.rival), 1)
    assertEquals(lines(done), Vector(setLine("1 defense die")))

  test("X runs from one to the smaller of the defender's secrets and the " +
      "defense dice"):
    def asked(secrets: Int): DecisionQuery =
      val b = defenderWithArcaneArmor(secrets)
      chosen(b, rolling()).query(b.actor)
    assertEquals(asked(3), DecisionQuery.ChooseAmount(1, 2,
      Some(ArcaneArmor.heading), ArcaneArmor.confirm))
    assertEquals(asked(1), DecisionQuery.ChooseAmount(1, 1,
      Some(ArcaneArmor.heading), ArcaneArmor.confirm))

  test("with every defense die set, nothing is rolled for the defense"):
    val b = defenderWithArcaneArmor(secrets = 3)
    val done = chosen(b, rolling())
      .answer(b.rival, ArcaneArmor.decisionId, ChooseAmountAnswer(2))
      .finishPlanWindows
    assertEquals(done.result.defenseFaces, Vector(TwoShields, TwoShields))
    assertEquals(done.result.defenseScore, 6)
    val rolled = done.events.collect {
      case WalkerStepRecorded(_, RollPayload(pool, _, _), _, _) => pool }
    assertEquals(rolled, Vector(CampaignIds.attackPool))
    assertEquals(lines(done), Vector(setLine("2 defense dice")))

  test("its price reads X secrets, and without a secret it is shown " +
      "unavailable"):
    val b = defenderWithArcaneArmor(secrets = 1)
    val option = commit(rolling(), b, 4).options(b.actor)
      .find(_.ref == ref)
    assertEquals(option.collect {
      case DecisionOption.Priced(_, price) => price
    }, Some(OptionPrice(secretsX = true)))
    val poor = defenderWithArcaneArmor(secrets = 0)
    assertEquals(commit(rolling(), poor, 4).withheld(poor.actor, ref),
      Some(OptionReason.Written(ArcaneArmor.noSecret)))

  test("with no defense dice it is shown unavailable"):
    val powers = productionAnd(removesDefenseDiceAfterGathering(2))
    val b = defenderWithArcaneArmor(secrets = 2)
    assertEquals(commit(rollingWith(powers), b, 4, powers = powers)
      .withheld(b.actor, ref, powers),
      Some(OptionReason.Written(ArcaneArmor.noDefenseDice)))

  test("a pool shrunk below X by the roll sets only what it holds, and " +
      "the secrets stay spent"):
    val powers = productionAnd(removesDefenseDiceBeforeRoll(1))
    val b = defenderWithArcaneArmor(secrets = 2)
    val done = chosen(b, rollingWith(powers), powers)
      .answer(b.rival, ArcaneArmor.decisionId, ChooseAmountAnswer(2))
      .finishPlanWindows
    assertEquals(done.result.defenseFaces, Vector(TwoShields))
    assertEquals(Look(done.state).faceDownSecrets(b.rival), 2)
    assertEquals(lines(done), Vector(setLine("1 defense die")))

  test("Jinx after the defense roll rerolls only the rolled die"):
    val base = defenderWithArcaneArmor(secrets = 2)
    val b = withAdviserFor(base, base.rival, jinx, Orientation.FaceUp)
    // The set die is no throw, so the roll after it is the first.
    val reroll = RerollChoice.decisionId(CampaignIds.defensePool, 1)
    val asked = chosen(b, rolling(Blank))
      .answer(b.rival, ArcaneArmor.decisionId, ChooseAmountAnswer(1))
      .finishPlanWindows
    assert(awaits(asked, b.rival, reroll))
    val done = asked.pick(b.rival, reroll, DecisionOptionRef.Denizen(jinx))
    assertEquals(done.since(asked).collect {
      case Reroll(pool, indices, _) => pool -> indices
    }, Vector(CampaignIds.defensePool -> Vector(1)))

  test("a bandit defender does not apply it"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    val done = commit(rolling(Blank, Blank), b, 4).finishPlanWindows
    assert(!done.ops.exists(_.isInstanceOf[SetDieFaces]))
    assertEquals(done.result.defenseFaces, Vector(Blank, Blank))

  test("a replayed game reaches the same faces"):
    val b = defenderWithArcaneArmor(secrets = 2)
    val done = chosen(b, rolling(Blank))
      .answer(b.rival, ArcaneArmor.decisionId, ChooseAmountAnswer(1))
      .finishPlanWindows
    // Replay rolls nothing, and these rules have no dice source.
    val silent = rules()
    val replay = done.events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(b.ready)))((state, event) =>
      state.flatMap(silent.evolve(_, event)))
    assertEquals(replay.map(ready(_).game.current.lastCampaignResult),
      Right(ready(done.state).game.current.lastCampaignResult))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.ArcaneArmorSuite oathdigital.application.PricedOptionProjectionSuite"`
Expected: compile failure, `Not found: ArcaneArmor` and `secretsX`.

- [ ] **Step 3: Price an amount that costs secrets**

In `CampaignTypes.scala`, in `PlanAmount`'s doc comment, replace `and \`effects\` are what that amount does.` with `\`secretsEach\` is what each unit costs, placed on the plan's card, and \`effects\` are what that amount does.`, and add the field:

```scala
final case class PlanAmount(decisionId: String, query: DecisionQuery,
    amountOf: DecisionAnswer => Option[Int],
    effects: Int => Vector[CampaignPlanEffect], secretsEach: Int = 0)
```

In `Decisions.scala`, add to `OptionPrice`:

```scala
final case class OptionPrice(favor: Int = 0, secrets: Int = 0,
    favorBurnt: Int = 0, secretsBurnt: Int = 0, warbands: Int = 0,
    secretsX: Boolean = false):
```

and add to its doc comment (or start one, if it has none): `\`secretsX\` marks a price in secrets that the player chooses when the option is applied (Arcane Armor's X).`

In `PlanAmounts.range`, add the parameter and pass it:

```scala
  /** An amount from `min` to `max`, asked as a `ChooseAmount`, each unit
    * costing `secretsEach` secrets. */
  def range(decisionId: String, min: Int, max: Int, heading: String,
      confirm: String, secretsEach: Int = 0)(
      effects: Int => Vector[CampaignPlanEffect]): PlanAmount =
    PlanAmount(decisionId,
      DecisionQuery.ChooseAmount(min, max, Some(heading), confirm), {
        case DecisionAnswer.ChooseAmountAnswer(amount) => Some(amount)
        case _ => None
      }, effects, secretsEach)
```

In `PlanPrice.of`, replace the final `if … else paid` with:

```scala
    val sacrificed =
      if offer.costs.contains(CampaignPlanCost.SacrificeWarband) then
        paid.copy(warbands = 1)
      else paid
    if offer.amount.exists(_.secretsEach > 0) then
      sacrificed.copy(secretsX = true)
    else sacrificed
```

and add to its doc comment: `An amount's secrets are paid after the question the dry run stops at, so they are read from the offer and priced as X.`

In `PriceDetails.of`, after the `line(price.secrets, …)` entry, add:

```scala
    if price.secretsX then "Cost: X secrets" else "",
```

- [ ] **Step 4: Pay for the amount in the application**

In `CampaignPlanApplication.scala`:

1. In `children`, insert a slot between `ask` and `chosenEffects`:

```scala
      Branch((_, _) => ask),
      Branch((_, pending) => payAmount(pending)),
      Branch((_, pending) => chosenEffects(pending)),
```

2. In the class doc comment, extend the amount bullet: `… stops at the question, then pays what each unit costs onto the plan's card and applies what the answer does.`

3. After `ask`, add:

```scala
  /** What the answered amount costs, placed onto the plan's card. */
  private def payAmount(pending: PendingTree): Vector[Operation] =
    val owed = for
      amount <- offered.offer.amount
      if amount.secretsEach > 0
      player <- user
      chosen <- PlanAmounts.chosen(pending, amount)
    yield player -> Cost(secret = chosen * amount.secretsEach)
    owed.toVector.map { case (player, cost) =>
      CampaignPlans.cardOf(source) match
        case Some(card) => payment(Costs.onCard(player, card, cost,
          catalog, intoOccupied = true))
        case None =>
          refuse("the title has no card to place a plan's cost on")
    }
```

- [ ] **Step 5: Write Arcane Armor**

Delete `object ArcaneArmorCard` from `HoldingArcaneDenizens.scala`. Create `src/main/scala/oathdigital/gameplay/powers/campaign/ArcaneArmor.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignIds,
  CampaignProcedure, PlanAmounts}
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

object ArcaneArmorCard extends Denizen(DenizenId("206"), "Arcane Armor",
    Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.arcane-armor"),
    persistent = false, cost = Cost.free,
    text = "X [secret] Set X [defense-die] you have collected to their " +
      "[shield][shield] face instead of rolling them.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Arcane Armor (card 206), a defender's battle plan: "X [secret] Set X
  * [defense-die] you have collected to their [shield][shield] face instead
  * of rolling them."
  *
  * Its user chooses X, from one to the smaller of their faceup secrets and
  * the dice in the defense pool, and pays X secrets onto the card, which
  * settle at once because a defender pays off turn. With no X possible it
  * is shown unavailable. A bandit defender has no secrets, so it never
  * applies the plan.
  *
  * At the defense roll, X dice are set to double shields and the rest are
  * rolled. If the pool holds fewer than X by then, only as many as it holds
  * are set, and the secrets stay spent. The set dice are no part of the
  * roll's throw, so no reroll changes them.
  *
  * It writes "Set {n} defense dice to double shields." at the roll.
  */
case object ArcaneArmor extends BattlePlan:
  val cardId: DenizenId = ArcaneArmorCard.id
  val id: PowerId = ArcaneArmorCard.power.id
  /** Under the Campaign's prefix, so a parked question is a Campaign
    * decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix + "arcane-armor"
  val heading: String = "Arcane Armor: set how many defense dice to " +
    "double shields, for a secret each?"
  val confirm: String = "Pay and set"
  val noSecret: String = "Needs 1 secret; you have 0"
  val noDefenseDice: String = "No defense dice to set"
  /** "Set {n} defense dice to double shields." */
  val set: NoteKey = NoteKey("set", Vector(NotePart.Text("Set "),
    NotePart.Arg(0), NotePart.Plural(0, " defense die to double shields.",
      " defense dice to double shields.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(set)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map { source =>
      val secrets = context.user.flatMap(player => context.ready.game
        .current.players.find(_.player == player))
        .fold(0)(_.board.faceUpSecrets)
      val dice = pool(context.ready)
      val offer = CampaignPlanOffer(source,
        "Arcane Armor: set defense dice to double shields", Vector.empty,
        Vector.empty)
      if secrets < 1 then
        offer.copy(withheld = Some(OptionReason.Written(noSecret)))
      else if dice < 1 then
        offer.copy(withheld = Some(OptionReason.Written(noDefenseDice)))
      else offer.copy(amount = Some(PlanAmounts.range(decisionId, 1,
        math.min(secrets, dice), heading, confirm, secretsEach = 1)(
        _ => Vector.empty)))
    }

  override def wrapping: Map[PowerWindow, BattlePlan.Wrap] = Map(
    PowerWindow.CampaignDefenseRoll -> ((_, _, children) =>
      setDice +: children))

  /** X dice set to double shields, as many as the pool holds now, and the
    * line saying how many. */
  private def setDice: Operation = Branch((ready, pending) =>
    PowerAnswers.amount(pending, decisionId).map(math.min(_, pool(ready)))
      .filter(_ > 0).toVector.flatMap(count => Vector[Operation](
        SetDieFaces(CampaignIds.defensePool,
          Vector.fill(count)(DefenseDieFace.TwoShields)),
        Note(id, _ => Some(set(PowerSourceRef.Card(cardId),
          NoteArg.Number(count)))))))

  /** The dice the defense pool holds. */
  private def pool(ready: ReadyGame): Int =
    ready.game.current.rollPools.get(CampaignIds.defensePool).fold(0)(_.count)
```

- [ ] **Step 6: Register it**

In `NewFoundations.scala`, add `ArcaneArmorCard` to the `import oathdigital.gameplay.powers.campaign.{…}` list.

In `PlanRules.scala`, add after `Vector(UnstableSummon) ++`:

```scala
      Vector(ArcaneArmor) ++
```

and append to the doc comment's list, before ` and Herald`: `, Arcane Armor (defense dice set before the roll)`.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.powers.dice.* oathdigital.gameplay.CampaignPlanWindowSuite oathdigital.application.PricedOptionProjectionSuite oathdigital.catalog.* oathdigital.gameplay.cards.*"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/model/CampaignTypes.scala src/main/scala/oathdigital/model/Decisions.scala src/main/scala/oathdigital/gameplay/actions/campaign/PlanAmounts.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanApplication.scala src/main/scala/oathdigital/gameplay/actions/campaign/PlanPrice.scala src/main/scala/oathdigital/application/PriceDetails.scala src/main/scala/oathdigital/gameplay/powers/campaign/ArcaneArmor.scala src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala src/test/scala/oathdigital/gameplay/powers/campaign/ArcaneArmorSuite.scala src/test/scala/oathdigital/application/PricedOptionProjectionSuite.scala
git commit -m "feat(batch7): Arcane Armor sets defense dice to double shields"
```

---

### Task 5: A choice of amounts, and Mountain Giant

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/PlanAmounts.scala` (`oneOf`, `button`)
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/MountainGiant.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala` (remove `MountainGiantCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import)
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala` (register)
- Modify: `src/test/scala/oathdigital/gameplay/powers/campaign/KindredWarriorsSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/campaign/SaltTheEarthSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/MountainGiantSuite.scala` (new)

**Interfaces:**
- Consumes: `PlanAmount` and the application's amount slots (Tasks 3 and 4); `PlanDice.effect(side, dice)`; `PlanDiscard.afterCampaign` and `PlanDiscard.discarded`; `PowerAnswers.one`.
- Produces: `PlanAmounts.oneOf(decisionId: String, heading: String, choices: Vector[(Int, String)])(effects: Int => Vector[CampaignPlanEffect]): PlanAmount`, `PlanAmounts.button(amount: Int): DecisionOptionRef.Button`, and `final case class MountainGiant` with `MountainGiant.forCatalog`, `decisionId`, `Three`, `added` and `removed`.

- [ ] **Step 1: Give the two suites another card with no plan**

Mountain Giant now offers a plan, so `PlanDriver.planless` refuses it. Search Party is a Nomad denizen with no implemented power.

- In `KindredWarriorsSuite`, replace `planless("Mountain Giant", "Baron")` with `planless("Search Party", "Baron")`, and `planlessCard("Mountain Giant")` with `planlessCard("Search Party")`.
- In `SaltTheEarthSuite`, replace `private val giant = planlessCard("Mountain Giant")` with `private val searchParty = planlessCard("Search Party")`, and rename the two other uses of `giant` to `searchParty`.

- [ ] **Step 2: Write the failing test**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/MountainGiantSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, PlanAmounts}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Mountain Giant: a plan for either side costing a secret, then one or
  * three attack dice, added or removed. It is discarded after the
  * Campaign only when three was chosen. */
class MountainGiantSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Mountain Giant")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val one = PlanAmounts.button(1)
  private val three = PlanAmounts.button(MountainGiant.Three)
  private val discarded = NoteText.Said("discarded",
    "Discarded after the Campaign.", covers = false)

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    MountainGiant.id, Vector(MountainGiant.added, MountainGiant.removed,
      PlanDiscard.discarded), run.events)

  /** p1 attacks the bandit-ruled origin holding Mountain Giant faceup and
    * one faceup secret. */
  private def attackerWithGiant: Board =
    withSecrets(withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp), 1)

  /** p2 rules the origin with two warbands and holds the Oathkeeper title,
    * Mountain Giant faceup and `secrets` faceup secrets. */
  private def defenderWithGiant(secrets: Int): Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.secrets(base.rival, faceUp = secrets))

  test("its text names the attack die the card leaves out after ±3"):
    assertEquals(MountainGiantCard.power.text,
      "±1 [attack-die] or ±3 [attack-die] At end, discard.")

  test("after the secret is paid, it asks one or three"):
    val b = attackerWithGiant
    val asked = commit(rules(winningDice), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(awaits(asked, b.actor, MountainGiant.decisionId))
    assertEquals(Look(asked.state).adviserTokens(b.actor, card),
      Tokens(0, 1))
    assertEquals(asked.query(b.actor), DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(one, "Add 1 attack die"),
      DecisionOption.Button(three,
        "Add 3 attack dice, and discard Mountain Giant after the Campaign")),
      heading = Some("Mountain Giant: add 1 or 3 attack dice?")))

  test("an attacker who adds three discards it after the Campaign"):
    val b = attackerWithGiant
    val run = commit(rules(winningDice), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.actor, MountainGiant.decisionId, three)
    assert(picked.since(run).contains(
      ModifyDicePool(CampaignIds.attackPool, 3)))
    val done = picked.finishPlanWindows
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(lines(done), Vector(NoteText.Said("added",
      "Added 3 attack dice.", covers = false), discarded))

  test("an attacker who adds one keeps it"):
    val b = attackerWithGiant
    val done = commit(rules(winningDice), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .pick(b.actor, MountainGiant.decisionId, one).finishPlanWindows
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, 1)))
    assert(!Look(done.state).inRegionalDiscard(card))
    assertEquals(lines(done), Vector(NoteText.Said("added",
      "Added 1 attack die.", covers = false)))

  test("a defender removes three attack dice, and it is discarded"):
    val b = defenderWithGiant(secrets = 1)
    val done = commit(rules(winningDice), b, 4)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
      .pick(b.rival, MountainGiant.decisionId, three).finishPlanWindows
    assert(done.ops.contains(ModifyDicePool(CampaignIds.attackPool, -3)))
    assert(Look(done.state).inRegionalDiscard(card))
    assertEquals(lines(done), Vector(NoteText.Said("removed",
      "Removed 3 attack dice.", covers = false), discarded))

  test("it costs a secret, and without one it is shown unavailable"):
    val b = defenderWithGiant(secrets = 1)
    val option = commit(rules(winningDice), b, 4).options(b.actor)
      .find(_.ref == ref)
    assertEquals(option.collect {
      case DecisionOption.Priced(_, price) => price
    }, Some(OptionPrice(secrets = 1)))
    val poor = defenderWithGiant(secrets = 0)
    assertEquals(commit(rules(winningDice), poor, 4).withheld(poor.actor,
      ref), Some(OptionReason.Written("Needs 1 secret; you have 0")))
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.MountainGiantSuite"`
Expected: compile failure, `Not found: MountainGiant` and `value button is not a member`.

- [ ] **Step 4: Add the choice builder**

In `PlanAmounts.scala`, add:

```scala
  /** One of `choices`, each a button with its words, asked as a
    * `ChooseOne`. */
  def oneOf(decisionId: String, heading: String,
      choices: Vector[(Int, String)])(
      effects: Int => Vector[CampaignPlanEffect]): PlanAmount =
    PlanAmount(decisionId, DecisionQuery.ChooseOne(choices.map {
      case (amount, words) => DecisionOption.Button(button(amount), words)
    }, heading = Some(heading)), {
      case DecisionAnswer.ChooseOneAnswer(ref) => choices.collectFirst {
        case (amount, _) if button(amount) == ref => amount }
      case _ => None
    }, effects)

  /** The button that answers `amount` in a `oneOf` question. */
  def button(amount: Int): DecisionOptionRef.Button =
    DecisionOptionRef.Button(s"amount-$amount")
```

- [ ] **Step 5: Write Mountain Giant**

Delete `object MountainGiantCard` from `HoldingNomadDenizens.scala`. Create `src/main/scala/oathdigital/gameplay/powers/campaign/MountainGiant.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignProcedure, PlanAmounts}
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

object MountainGiantCard extends Denizen(DenizenId("155"), "Mountain Giant",
    Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.mountain-giant"),
    persistent = false, cost = Cost(secret = 1),
    text = "±1 [attack-die] or ±3 [attack-die] At end, discard.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Mountain Giant (card 155), a battle plan for either side: "±1
  * [attack-die] or ±3 [attack-die] At end, discard." The card leaves out
  * the second icon; it is an attack die (product owner).
  *
  * It costs a secret, placed onto the card. Its user then chooses one or
  * three: an attacker adds that many attack dice, a defender removes them.
  * The card is discarded through the standard discard once the Campaign
  * has resolved, only when three was chosen (FAQ). A bandit defender pays
  * nothing, so it never applies the plan.
  *
  * It writes "Added {n} attack dice." ("Removed" for a defender), and the
  * discard line when it is discarded.
  */
final case class MountainGiant private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = MountainGiantCard.id
  def id: PowerId = MountainGiant.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(MountainGiant.added,
    MountainGiant.removed, PlanDiscard.discarded)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    val side = context.side
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      MountainGiant.label(side), Vector(CampaignPlanCost.Secret(1)),
      Vector.empty, amount = Some(PlanAmounts.oneOf(MountainGiant.decisionId,
        MountainGiant.heading(side), Vector(1, MountainGiant.Three).map(
          dice => dice -> MountainGiant.choice(side, dice)))(dice => Vector(
        PlanDice.effect(side, dice),
        CampaignPlanEffect.Run(Vector(note(side, dice))))))))

  /** "Added {n} attack dice.", or "Removed" for a defender. */
  private def note(side: CampaignPlanSide, dice: Int): Note =
    val key = side match
      case CampaignPlanSide.Attacker => MountainGiant.added
      case CampaignPlanSide.Defender => MountainGiant.removed
    Note(id, _ => Some(key(PowerSourceRef.Card(cardId),
      NoteArg.Number(dice))))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use => Vector(Branch(
      (_, pending) =>
        if PowerAnswers.one(pending, MountainGiant.decisionId).contains(
            PlanAmounts.button(MountainGiant.Three)) then
          PlanDiscard.afterCampaign(catalog, id, use, cardId)
        else Vector.empty))))

object MountainGiant:
  val id: PowerId = MountainGiantCard.power.id
  /** The dice that discard the card after the Campaign. */
  val Three: Int = 3
  /** Under the Campaign's prefix, so a parked question is a Campaign
    * decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix +
    "mountain-giant"
  /** "Added {n} attack dice." */
  val added: NoteKey = NoteKey("added", Vector(NotePart.Text("Added "),
    NotePart.Arg(0), NotePart.Plural(0, " attack die.", " attack dice.")))
  /** "Removed {n} attack dice." */
  val removed: NoteKey = NoteKey("removed", Vector(
    NotePart.Text("Removed "), NotePart.Arg(0),
    NotePart.Plural(0, " attack die.", " attack dice.")))

  /** "Mountain Giant: add 1 or 3 attack dice" */
  def label(side: CampaignPlanSide): String =
    s"Mountain Giant: ${verb(side)} 1 or $Three attack dice"

  /** "Mountain Giant: add 1 or 3 attack dice?" */
  def heading(side: CampaignPlanSide): String = s"${label(side)}?"

  /** "Add 1 attack die"; "Add 3 attack dice, and discard Mountain Giant
    * after the Campaign". */
  def choice(side: CampaignPlanSide, dice: Int): String =
    val noun = if dice == 1 then "attack die" else "attack dice"
    val words = s"${verb(side).capitalize} $dice $noun"
    if dice == Three then
      s"$words, and discard Mountain Giant after the Campaign"
    else words

  private def verb(side: CampaignPlanSide): String = side match
    case CampaignPlanSide.Attacker => "add"
    case CampaignPlanSide.Defender => "remove"

  def forCatalog(catalog: ExecutableCatalog): MountainGiant =
    new MountainGiant(catalog)
```

- [ ] **Step 6: Register it**

In `NewFoundations.scala`, add `MountainGiantCard` to the `import oathdigital.gameplay.powers.campaign.{…}` list.

In `PlanRules.scala`, add after `Vector(ArcaneArmor) ++`:

```scala
      Vector(MountainGiant.forCatalog(catalog)) ++
```

and append to the doc comment's list, before ` and Herald`: `, Mountain Giant (an amount asked when applied, and a discard at the end on three)`.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.CampaignPlanWindowSuite oathdigital.catalog.* oathdigital.gameplay.cards.*"`
Expected: PASS.

Then run `./sbtw test`. If another suite fails because one of this slice's three cards now offers a plan where the suite used it as a plain card, swap in another card the same way as Step 1 (`PlanDriver.planlessCard` checks the choice), and list the suite in your report.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/campaign/PlanAmounts.scala src/main/scala/oathdigital/gameplay/powers/campaign/MountainGiant.scala src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala src/test/scala/oathdigital/gameplay/powers/campaign/MountainGiantSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/KindredWarriorsSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/SaltTheEarthSuite.scala
git commit -m "feat(batch7): Mountain Giant adds or removes one or three dice"
```

Add any suite Step 7 changed to the `git add` line.

---

### Task 6: Gates and roadmap

**Files:**
- Modify: `docs/ROADMAP.md`
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md` (Status)

- [ ] **Step 1: Run the gates**

Run, in order:

1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. The server count is the baseline plus 22; the frontend count is the baseline. If a count differs, find out why before going on.

- [ ] **Step 2: Mark the slice done**

In `docs/ROADMAP.md`, under "### Phase - Catalog batch 7 - Campaign dice", change `- [ ] Slice 3: amounts and fixed faces (Arcane Armor, Unstable Summon,` to `- [x] Slice 3: amounts and fixed faces (Arcane Armor, Unstable Summon,`.

In the design's **Status** paragraph, replace `Slices 1 and 2 are done and slice 3 is planned;` with `Slices 1, 2 and 3 are done;`. Re-wrap the paragraph to 78 characters.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md docs/superpowers/specs/2026-10-09-catalog-batch-7-design.md
git commit -m "docs(batch7): slice 3 done"
```
