# Catalog Batch 8, Slice 5 (Burns Taken) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Vow of Silence (74) and Vow of Division (86). When an enemy burns secrets or favor, the holder takes that many. Each card also stops its holder from challenging.

**Architecture:**

- **A burn's actor.** `Burn` gains `by: Option[PlayerId]`, the player who burns. Every builder of a `Burn` states it, and the codec records it. A cost's burnt portions are already the payer's, because `PayCost` names its player. A `None` is a burn no player makes, such as Bandit Market's setup burn, or a burn from a journal written before this slice.
- **The burn pass.** After a walker step, a new pass in the walker reads the burns the step made, then hands them to every power's new `ContributingPower.onBurns`. It works the way the adviser arrival pass hands arrivals to `onArrival`. A power's answer is a follow-up: operations and a line, run and journaled as their own step. Both passes share one `FollowUpStep` helper, which is moved out of `AdviserArrivalPass`.
  - **Batches:** the pass reads the operations the batch executed. These are every `Burn` with a burner, the burns inside a `Take`'s `leaving`, and each `PayCost`'s burnt portions.
  - **Split composites:** a `Burn` or `PayCost` in the tree is walked as its child moves, and a bare move has no burner. So `walkComposite` hands the pass the moves to the shared bank that such a composite recorded, once it is done. They are attributed to the `Burn`'s burner or to the payer.
- **The burn stands, and the holder takes as many.** The Vows do not rewrite the burn. Their follow-up is a `Take` of the same count from the shared bank to the holder.
  - A burn uses only faceup secrets.
  - A `Take` of secrets from a board holding both faces is ambiguous, but the shared bank gives faceup secrets.
  - The holder ends with exactly what the burn removed, and every existing burn rule keeps working unchanged.
- **The Challenge halves:**
  - Vow of Division is a `Restriction` at `ChallengeActionEligibility`, as Vow of Peace is at the Campaign's.
  - Vow of Silence is an `OptionRestriction` at `ChallengeBannerSelection`, as the Fortress's is.
- **Codec:** one optional field, `"by"` on `"burn"`. Old journals replay unchanged.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`, sections:
  - "Goal and scope";
  - "The bar" (the slice 5 and slice 11 bullets);
  - "Slice 5: Burns taken";
  - "Seams used for the first time";
  - "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: "Rules that apply to the whole batch" and the slice 5 entries.

Task 1 amends both with this plan's rulings. Read them again after Task 1.

**Starts from:** `main` with slice 4 (Keep) merged. Slice 4's plan is `docs/superpowers/plans/2026-10-12-catalog-batch-8-slice4.md`. It splits `CampaignRaid.transfer` into `takes` and `consequences`:

- the Darkest Secret's `Burn.secrets` moves into `takes`;
- the half-favor `Burn.favor` moves into `consequences`.

If slice 4 has not merged when you start, branch from `main` at `1f75bc24` or later. Edit the two `Burn` calls in `CampaignRaid.transfer` as Task 2 says. Expect these conflicts when this branch merges, and in each keep both sides:

- `CampaignRaid`;
- the design's Status line and the ROADMAP's Now line. Write in them what is true on `main`.

Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it.
  - Each new file below lists the imports it needs.
  - When code moves out of a file, remove any import that file no longer uses.
  - A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). `WalkerOperationCodec.scala` is at 796 before this plan, and Task 2 adds exactly 2 lines to it. Add no comment there.
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker sources.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **No tree changes.** No procedure tree gains, loses or moves a node. Parked games keep their addresses.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Names, exactly:**
  - `Burn.by: Option[PlayerId]`. The factories are `Burn.favor(amount, from, by)` and `Burn.secrets(amount, from, by)`, with `by` required and no default.
  - In `oathdigital.gameplay.powerresolver`, file `StepBurns.scala`:
    - `Burnt(resource: Piece.Counted, from: Location, by: PlayerId)`;
    - `StepBurns(actor: PlayerId, after: ReadyGame, burns: Vector[Burnt])`, with `StepBurns.in` and `StepBurns.moved`;
    - `BurnFollowUp(operations: Vector[CoreOperation], note: Note)`.
  - `ContributingPower.onBurns(burns: StepBurns): Option[BurnFollowUp]`.
  - In `oathdigital.gameplay.walker`, both `private[walker]`:
    - `FollowUpStep.recorded`;
    - `BurnPass.afterBatch` and `BurnPass.afterComposite`.
  - In `oathdigital.gameplay.powers.burn`, file `BurnVows.scala`:
    - `BurnVow` (sealed abstract class);
    - `VowOfSilence` and `VowOfDivision` (case objects);
    - `VowOfSilenceCard` and `VowOfDivisionCard`.
  - Power ids are unchanged: `denizen.vow-of-silence` and `denizen.vow-of-division`.
  - Note keys:
    - `BurnVow.taken` (name `"taken"`);
    - `BurnVow.takenBy` (name `"taken-by"`);
    - `VowOfSilence.forbidden` (name `"forbidden"`).
- **Texts, exactly:**
  - The take line on the holder's own turn: "Took {2 secrets} instead of the bank." and "Took {1 favor} instead of the bank." It covers the take's own detail line.
  - The take line off the holder's turn: "{Blue} took {2 secrets} instead of the bank."
  - The Vow of Silence banner option's hover reason: "Cannot challenge the {Darkest Secret}."
  - The Challenge's hover reason in the menu:
    - "Vow of Division prevents you from challenging";
    - "Vow of Silence prevents you from challenging the Darkest Secret", when the Darkest Secret is the only banner the holder could challenge.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 12 server tests and no frontend test.
  - It changes two existing expectations, each to include the new burner: `ConspiracyWhenPlayedSuite` and `BookBurningSuite`.
  - `PowerKindsCatalogSuite`'s list of persistent rules gains the two Vows.
  - Several suites only gain the new `by` argument where they build a `Burn` (Task 2).
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

These are written into the design and the rulings appendix in Task 1. Rulings 1 and 2 change what the design approved, and are flagged there for the product owner.

1. **The burn stands, and the holder takes as many from the shared bank.**
   - The design said a rewrite would replace an enemy's burn with a `Take`. Instead, the burn runs as it always has. Right after the step that burnt, the holder takes the same count from the shared bank, as a step of its own credited to the Vow.
   - The result is the same: the burner loses the tokens, and the holder gains them. Secrets arrive faceup, as the burnt ones were.
   - This keeps every burn rule as it is: only faceup secrets burn, and an amount is cut to what is there. It also avoids a `Take` of secrets from a board holding both faces, which the engine cannot resolve.
2. **A Raid's half-favor burn is the defeated defender's own burn.**
   - The batch rule made the burner the actor of the procedure, the attacker. That would make an attacker who holds Vow of Division its own burner, so they would take nothing from the defender they beat.
   - With the defender as the burner, the card reads as printed in every case:
     - an attacker or a bystander who holds it takes the defender's favor;
     - a defender who holds it keeps nothing, which is the card's "unless it was burned by a raid targeting you".
3. **Who burns, card by card.** The batch rule names the actor of the procedure, the payer of a cost, and each owner in an effect that makes each player burn.
   - **Raid, the Darkest Secret's secrets:** the attacker who takes the banner. The same holds for Conspiracy, where the burner is its player.
   - **Book Burning:** the attacker. A defender who holds Vow of Silence takes their burnt secrets back.
   - **Ancient Binding:** each player burns from their own board. A held card's secrets are its holder's burn. A card at a site has no holder, so the user burns its secrets.
   - **Revelation:** each player burns their own.
   - **Defame, Riots, Charlatan, Firebrand and Amber Flame:** the player who uses the card.
   - **Unstable Summon:** the attacker.
   - **Bandit Market:** no player burns. Its setup burn is from the favor banks.
   - **Costs:** the payer burns. This covers:
     - action costs;
     - battle plan costs, and Insect Swarm's added cost;
     - Curfew, Forced Labor, Way Station and Toll Roads paid to bandits;
     - Trade for secrets;
     - modifier costs.
4. **An enemy is every other player.** Every game is all-Exile, as in earlier batches. A player's own burns go to the bank.
5. **One take per step.** All of a step's enemy burns of the resource add up to one take and one line.
   - The line follows the burning step, so it comes before that step's own line, such as Book Burning's or Defame's.
   - The burning step's own line still reads only the burn.
6. **The holder holds the Vow faceup as an adviser.** The card is adviser-only. A facedown Vow does nothing.
7. **Vow of Division's Challenge half.**
   - Its holder cannot start a Challenge.
   - The menu shows Challenge unavailable, with the reason "Vow of Division prevents you from challenging".
8. **Vow of Silence's Challenge half.**
   - Its holder's banner choice shows the Darkest Secret unavailable, with the reason "Cannot challenge the Darkest Secret.".
   - When the Darkest Secret is the only banner they could challenge, the menu shows Challenge unavailable: "Vow of Silence prevents you from challenging the Darkest Secret".
9. **What the pass does not see.** The burns of an adviser arrival's follow-up start no burn pass, and none exist today. A follow-up's own operations start no second burn pass.
10. **Old journals replay unchanged.** A burn recorded without a burner decodes with `None`.

## File map

- `src/main/scala/oathdigital/model/CoreOperations.scala`: `Burn.by` and the factories.
- `src/main/scala/oathdigital/gameplay/operations/OperationResolution.scala`: the counted rebuild keeps `by`.
- `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`: `"by"` on `"burn"`.
- Every `Burn` builder, as listed in Task 2:
  - `SecretBurns`, `BookBurning`, `AncientBinding`, `Defame`, `Revelation`;
  - `ConspiracyWhenPlayed`, `Riots`, `Charlatan`, `GreatMarketRules`;
  - `Firebrand`, `AmberFlame`, `UnstableSummon`, `CampaignRaid`.
- Create `src/main/scala/oathdigital/gameplay/powerresolver/StepBurns.scala`.
- `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`: `onBurns`.
- Create `src/main/scala/oathdigital/gameplay/walker/FollowUpStep.scala` and `BurnPass.scala`.
- `src/main/scala/oathdigital/gameplay/walker/AdviserArrivalPass.scala`: uses `FollowUpStep`.
- `src/main/scala/oathdigital/gameplay/walker/WalkerSteps.scala`: `recordBatch` runs the burn pass.
- `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`: `walkComposite` runs it for a split `Burn` or `PayCost`.
- Create `src/main/scala/oathdigital/gameplay/powers/burn/BurnVows.scala`.
- `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala` and `HoldingDiscordDenizens.scala`: the two cards leave.
- `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`: imports the cards.
- `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`: registers the Vows.
- Tests:
  - create `src/test/scala/oathdigital/gameplay/walker/BurnPassSuite.scala`;
  - create `src/test/scala/oathdigital/gameplay/powers/burn/VowOfSilenceSuite.scala` and `VowOfDivisionSuite.scala`;
  - modify `WalkerOperationWireSuite`, `PowerKindsCatalogSuite`, and the suites Task 2 lists.
- Docs:
  - the design;
  - the rulings appendix;
  - `docs/ROADMAP.md`.

---

### Task 1: Write the plan-time rulings into the design and the rulings appendix

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`

- [ ] **Step 1: Amend the design's dependency list.** In "Goal and scope", replace the bullet

```markdown
- Slice 11 needs the acting player that slice 5 gives a burn.
```

with

```markdown
- Slice 11 needs an acting player on an operation, as slice 5 gives one to
  a burn.
```

- [ ] **Step 2: Replace the slice 5 bullet in "The bar".** Replace the whole bullet that starts "- **A burn's actor** (slice 5)." with:

```markdown
- **A burn's actor and the burn pass** (slice 5, amended at plan time).
  Each `Burn` names the player who burns, as `by`:
  - the actor of the procedure that builds it;
  - each owner in an effect that makes each player burn;
  - the defeated defender, for a Raid's half-favor burn.

  A cost's burnt portions are its payer's. After a walker step burns, a
  burn pass hands the step's burns to every power's `onBurns`, as the
  adviser arrival pass hands arrivals to `onArrival`. A power's follow-up
  runs as its own step. The burn itself stands: Vow of Silence and Vow of
  Division take as many from the shared bank. The design first proposed
  rewriting the burn into a `Take`.
```

- [ ] **Step 3: Amend the slice 11 bullet in "The bar".** Replace

```markdown
- **An actor-aware operation restriction** (slice 11). An
  `OperationRestriction` learns the acting player, through the same actor as
  slice 5, so a lock can hold against enemies only.
```

with

```markdown
- **An actor-aware operation restriction** (slice 11). An
  `OperationRestriction` learns the acting player, so a lock can hold
  against enemies only. Slice 5 names only a burn's actor, on the `Burn`
  itself, so slice 11 settles how a restriction learns its own.
```

- [ ] **Step 4: Rewrite the slice 5 section.** Replace everything from the heading `## Slice 5: Burns taken` up to (not including) `## Slice 6: Operation triggers` with:

```markdown
## Slice 5: Burns taken

Each `Burn` names its burner. After every walker step, a burn pass hands
the step's burns, costs included, to the powers. Vow of Silence and Vow of
Division then take from the shared bank what their holder's enemies burnt
of secrets or favor. Each card's "cannot challenge" half refuses the
Challenge at the Challenge's own windows.

### Plan-time rulings

- **The burn stands, and the holder takes as many** (flagged for the
  product owner).
  - The burn runs as it always has. Right after the step that burnt, the
    holder takes the same count from the shared bank, as its own step.
    Secrets arrive faceup.
  - Every burn rule stays as it is: only faceup secrets burn, and an
    amount is cut to what is there.
- **A Raid's half-favor burn is the defeated defender's own** (flagged for
  the product owner).
  - An attacker or a bystander who holds Vow of Division takes it.
  - A defender who holds it keeps nothing, which is the card's "unless it
    was burned by a raid targeting you".
- **Who burns.**
  - The attacker burns the Darkest Secret's secrets when taking it in a
    Raid, and so does Conspiracy's player when they take it.
  - **Book Burning:** the attacker.
  - **Ancient Binding:** each player burns their own board. A held card's
    secrets are its holder's burn, and the user burns a site card's.
  - **Revelation:** each player.
  - **Defame, Riots, Charlatan, Firebrand and Amber Flame:** the user.
  - **Unstable Summon:** the attacker.
  - **Costs:** the payer.
  - **Bandit Market's setup burn:** no player.
- **One take per step.**
  - A step's enemy burns of the resource add up to one take and one line.
  - The line follows the burning step, before that step's own line.
  - "Took 2 secrets instead of the bank." on the holder's turn; "{Blue}
    took 2 secrets instead of the bank." off it. Each line covers the
    take's own detail line.
- **Faceup only.** The holder holds the Vow faceup as an adviser.
- **Vow of Division:** the Challenge is unavailable to its holder: "Vow of
  Division prevents you from challenging".
- **Vow of Silence.**
  - The holder's banner choice shows the Darkest Secret unavailable:
    "Cannot challenge the Darkest Secret."
  - When it is the only banner they could challenge, the Challenge is
    unavailable: "Vow of Silence prevents you from challenging the Darkest
    Secret".
- **Not seen by the pass:** an adviser arrival's follow-up, which burns
  nothing today, and a burn follow-up's own operations.
- **Old journals** replay unchanged: a burn recorded without a burner has
  none.
```

- [ ] **Step 5: Amend the rulings appendix.** In "Rules that apply to the whole batch", replace the "**Who burns.**" bullet with:

```markdown
- **Who burns.**
  - A burn's actor is the actor of the procedure that burns, or the payer
    when the burn is a cost.
  - In an effect that makes each player burn (Revelation, Ancient
    Binding), each owner is the actor of their own burn. A card's secrets
    are its holder's.
  - A Raid's half-favor burn is the defeated defender's own.
  - A player never counts as their own enemy.
```

In "Slice 5: Burns taken", replace the Vow of Silence sub-bullet

```markdown
  - Every burn of secrets by an enemy counts, cost or effect. The burn is
    replaced by a take: the tokens go to the holder instead of the bank.
```

with

```markdown
  - Every burn of secrets by an enemy counts, cost or effect. The burn
    stands, and right after it the holder takes as many secrets, faceup,
    from the shared bank (plan-time ruling).
```

Then replace the Vow of Division sub-bullet

```markdown
  - As Vow of Silence, for favor. A raid's burn of the holder's own favor,
    in a Raid targeting the holder, still goes to the bank.
```

with

```markdown
  - As Vow of Silence, for favor. A Raid's half-favor burn is the defeated
    defender's own (plan-time ruling), so an attacker or a bystander who
    holds the Vow takes it, and a defender who holds it does not.
```

- [ ] **Step 6: Check the links and commit.**

Run: `python3 scripts/check-markdown-links.py`
Expected: PASS.

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md
git commit -m "docs(batch8): slice 5 plan-time rulings for the burn vows"
```

---

### Task 2: Every burn names its burner

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala` (the `Burn` trait and object)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationResolution.scala` (`counted`, the `Burn` case)
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` (encode and decode `"burn"`)
- Modify, one call each (Step 5): `gameplay/powers/SecretBurns.scala`, `gameplay/powers/campaign/BookBurning.scala`, `gameplay/powers/action/AncientBinding.scala`, `gameplay/powers/economy/Defame.scala`, `gameplay/powers/whenplayed/Revelation.scala`, `gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala`, `gameplay/powers/whenplayed/Riots.scala`, `gameplay/powers/whenplayed/Charlatan.scala`, `gameplay/powers/setup/GreatMarketRules.scala`, `gameplay/powers/action/Firebrand.scala`, `gameplay/powers/action/AmberFlame.scala`, `gameplay/powers/campaign/UnstableSummon.scala`, `gameplay/actions/campaign/CampaignRaid.scala` (all under `src/main/scala/oathdigital/`)
- Test: `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala`
- Modify (new argument only): the test suites in Step 6

**Interfaces:**
- Produces: `Burn.by: Option[PlayerId]`; `Burn.favor(amount: Int, from: PositionedLocation, by: Option[PlayerId]): Burn`; `Burn.secrets(amount: Int, from: PositionedLocation, by: Option[PlayerId]): Burn`.

Nothing reads `by` yet: the game plays exactly as before.

- [ ] **Step 1: Write the failing codec test.** In `WalkerOperationWireSuite`, after the test "a Take's leaving operations round trip, and a Take without them writes none", add:

```scala
  test("a burn's burner round trips, and a burn without one writes none"):
    val player = PlayerId("red")
    val burns = Vector[CoreOperation](
      Burn.favor(2, PositionedLocation(Location.PlayArea(player)),
        Some(player)),
      Burn.secrets(1,
        PositionedLocation(Location.OnBanner(Banner.DarkestSecret)), None))
    val events = burns.map(burn => recordedOperation("burn", burn))
    val encoded = encodedStreamThatRoundTrips("burn-by", events)
    val ops = ujson.read(encoded).arr.map(_("payload")("ops")(0))
    assertEquals(ops(0)("by").str, "red")
    assert(!ops(1).obj.contains("by"))
```

- [ ] **Step 2: Run it to see it fail.**

Run: `./sbtw "testOnly oathdigital.serialization.WalkerOperationWireSuite"`
Expected: FAIL to compile: `Burn.favor` takes two arguments.

- [ ] **Step 3: Give `Burn` its burner.** In `CoreOperations.scala`, replace the `Burn` trait's doc comment and the trait and object with:

```scala
/** Moves favor or secrets to the shared bank. `by` is the player who burns
  * (catalog batch 8, "A burn's actor"): the actor of the procedure that
  * burns, each owner when an effect makes each player burn, the defeated
  * defender for a Raid's half-favor burn, and `None` when no player burns,
  * as in a setup rule. A cost's burnt portions are its payer's, so
  * `PayCost` holds no `Burn`. A burn recorded before burns named their
  * burner has `None`.
  */
sealed trait Burn extends CoreOperation:
  def resource: Piece.Counted
  def from: PositionedLocation
  def by: Option[PlayerId]

  final lazy val move: Move = Move(resource, from,
    PositionedLocation(Location.SharedBank))
  final override lazy val children: Vector[Operation] = Vector(move)
object Burn:
  def favor(amount: Int, from: PositionedLocation,
      by: Option[PlayerId]): Burn =
    ResourceBurn(Piece.Favor(amount), from, by)

  def secrets(amount: Int, from: PositionedLocation,
      by: Option[PlayerId]): Burn =
    ResourceBurn(Piece.Secrets(amount), from, by)

  private final case class ResourceBurn(resource: Piece.Counted,
      from: PositionedLocation, by: Option[PlayerId]) extends Burn
```

In `OperationResolution.counted`, the `Burn` case rebuilds with the same burner:

```scala
          case Piece.Favor(count) => Burn.favor(count, value.from, value.by)
          case Piece.Secrets(count) =>
            Burn.secrets(count, value.from, value.by)
```

- [ ] **Step 4: Record the burner.** In `WalkerOperationCodec.scala`, replace the `Burn` encode case with these 4 lines:

```scala
      case burn: Burn => ujson.Obj.from(Vector[(String, ujson.Value)](
        "kind" -> "burn", "resource" -> encodePiece(burn.resource),
        "from" -> encodePositionedLocation(burn.from)) ++
        burn.by.map(player => "by" -> ujson.Str(player.value)))
```

In the `"burn"` decode case, bind the burner after `from` and pass it to both factories:

```scala
    case "burn" => for
      resource <- decodePiece(value("resource"), s"$path.resource")
      from <- decodePositionedLocation(value("from"), s"$path.from")
      by = value.obj.get("by").map(raw => PlayerId(raw.str))
      burn <- resource match
        case Piece.Favor(amount) => Right(Burn.favor(amount, from, by))
        case Piece.Secrets(amount) => Right(Burn.secrets(amount, from, by))
        case other => Left(InvalidValue(s"$path.resource",
          s"unsupported burn resource $other"))
    yield burn
```

Check: `wc -l` shows the file at 798 lines.

- [ ] **Step 5: Name the burner at every builder.** Make each change below. Where a helper gains a parameter, change its callers in the same file. Only `AncientBinding` gains a new helper.

`SecretBurns.keeping` takes the burner:

```scala
  /** Every secret on `held`'s board but `kept`, burnt by `by`. A board
    * holding `kept` or fewer loses nothing. A batch runs its operations in
    * order, so the burn sees the secrets just turned. */
  def keeping(held: PlayerState, kept: Int, by: PlayerId)
      : Vector[CoreOperation] =
    val burnt = held.board.faceUpSecrets + held.board.faceDownSecrets - kept
    val turned = math.min(held.board.faceDownSecrets, burnt)
    if burnt <= 0 then Vector.empty
    else Option.when[CoreOperation](turned > 0)(FlipSecrets(held.player,
      turned, SecretSide.FaceDown, SecretSide.FaceUp)).toVector :+
      Burn.secrets(burnt, PositionedLocation(Location.PlayArea(held.player)),
        Some(by))
```

`BookBurning`: the attacker burns. Its private `burn` gains `attacker: PlayerId` and calls `SecretBurns.keeping(_, 1, attacker)`. The `later` hook passes `use.actor`, which is the Campaign's attacker:

```scala
        BuildOps((ready, _) =>
          Right(BookBurning.burn(ready, defender, use.actor))),
```

`AncientBinding`: each player burns their own board. A held card's secrets are its holder's, and a site card's are the user's. Replace `burns` and `onCards`, and add `holderOf`:

```scala
  /** The player's board and every card in play, then every other board
    * down to one secret, in seat order. Each player burns their own
    * board. */
  private def burns(ready: ReadyGame, actor: PlayerId, binding: DenizenId)
      : Vector[CoreOperation] =
    ready.game.current.players.flatMap(held =>
      SecretBurns.keeping(held, if held.player == actor then 0 else 1,
        held.player)) ++
      onCards(ready, actor, binding)

  /** Every secret on a card in play but Ancient Binding: advisers and
    * relics in play areas, denizens, edifices and relics at sites. A held
    * card's secrets are its holder's burn; a card at a site has no holder,
    * so `actor` burns them. */
  private def onCards(ready: ReadyGame, actor: PlayerId,
      binding: DenizenId): Vector[CoreOperation] =
    val found = InPlayCardResources.discover(ready)
    val cards: Vector[(CardId, Int)] =
      found.denizens.map(card => (card.id: CardId) -> card.tokens.secrets) ++
        found.relics.map(relic => (relic.id: CardId) -> relic.tokens.secrets)
    cards.collect { case (card, secrets) if secrets > 0 && card != binding =>
      Burn.secrets(secrets, PositionedLocation(Location.OnCard(card)),
        Some(holderOf(ready, card).getOrElse(actor))) }

  /** The player holding `card` as an adviser or a relic, if any. */
  private def holderOf(ready: ReadyGame, card: CardId): Option[PlayerId] =
    ready.game.current.players.find(player =>
      player.advisers.exists(_.id == card) ||
        player.relics.exists(_.id == card)).map(_.player)
```

`Defame`: the trader burns. `offer` passes `actor` to `burn`:

```scala
        BuildOps((_, answered) => burn(actor, answered)),
```

```scala
  private def burn(actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case Some(`skip`) => Right(Vector.empty)
      case Some(DecisionOptionRef.Player(target)) => Right(Vector(
        Burn.favor(Burned, PositionedLocation(Location.PlayArea(target)),
          Some(actor))))
```

The other cases of `Defame.burn` stay as they are.

`Revelation.settle`: each player burns their own:

```scala
        Burn.favor(amount, PositionedLocation(Location.PlayArea(player)),
          Some(player)),
```

`ConspiracyWhenPlayed.take`: its player, `actor`, burns the Darkest Secret's secrets:

```scala
              Option.when(secrets > 0)(Burn.secrets(secrets,
                PositionedLocation(Location.OnBanner(held)), Some(actor)))
                .toVector
```

`Riots`: the card's player burns. `effect` passes its `actor`:

```scala
        BuildOps((ready, _) => Right(burn(ready, found, actor))),
```

```scala
  private def burn(ready: ReadyGame, found: Vector[CardId], actor: PlayerId)
      : Vector[CoreOperation] =
    val count = RegionDiscard.gone(found, ready).size
    if count == 0 then Vector.empty
    else Vector(Burn.favor(count,
      PositionedLocation(Location.OnBanner(Banner.PeoplesFavor)),
      Some(actor)))
```

`Charlatan`: the card's player burns:

```scala
  private def burn(ready: ReadyGame, actor: PlayerId): Vector[CoreOperation] =
    val extra = secretsOn(ready) - Kept
    if extra <= 0 then Vector.empty
    else Vector(Burn.secrets(extra,
      PositionedLocation(Location.OnBanner(Banner.DarkestSecret)),
      Some(actor)))
```

```scala
  def effect(ctx: PowerCtx): Vector[Operation] = Vector(
    BuildOps((ready, _) => Right(burn(ready, ctx.activePlayer))),
    Note(id, burnedNote))
```

`GreatMarketRules` (Bandit Market): no player burns:

```scala
      Burn.favor(1, PositionedLocation(Location.FavorBank(bank)), None))
```

`Firebrand`: the user burns. `build` passes `player` to `effect`:

```scala
    BuildOps((live, pending) => Right(effect(live, pending, player))),
```

```scala
  private def effect(ready: ReadyGame, pending: PendingTree,
      player: PlayerId): Vector[CoreOperation] = chosen(ready, pending) match
    case Some(DecisionOptionRef.FavorBank(suit)) => Vector(Move(
      Piece.Favor(Moved), PositionedLocation(Location.FavorBank(suit)),
      banner))
    case Some(`burn`) => Vector(Burn.favor(Moved, banner, Some(player)))
    case _ => Vector.empty
```

`AmberFlame.burn`: the user, `actor`, burns:

```scala
        case Banner.PeoplesFavor => Burn.favor(Burned, from, Some(actor))
        case Banner.DarkestSecret => Burn.secrets(Burned, from, Some(actor))
```

`UnstableSummon`: an attacker's plan, so the Campaign's actor burns. `plan` passes `summon(context.setup.actor)` to `PlanAmounts.range`, and `summon` takes the attacker first:

```scala
      else offer.copy(amount = Some(PlanAmounts.range(decisionId, 1,
        secrets, heading, confirm)(summon(context.setup.actor))))
```

```scala
  /** X secrets the attacker burns from the banner, X attack dice, and the
    * line. */
  private def summon(attacker: PlayerId)(count: Int)
      : Vector[CampaignPlanEffect] = Vector(
    CampaignPlanEffect.Run(Vector(Burn.secrets(count,
      PositionedLocation(Location.OnBanner(Banner.DarkestSecret)),
      Some(attacker)))),
```

The rest of `summon` stays as it is. Add `PlayerId` to `UnstableSummon`'s imports if it imports model names one by one.

`CampaignRaid`: the attacker burns the Darkest Secret's secrets as they take it, and the defeated defender burns half their own favor. The first edit is in `takes`, the second in `consequences` (both are in `transfer` if slice 4 has not merged):

```scala
            Option.when(secrets > 0)(Burn.secrets(secrets,
              PositionedLocation(Location.OnBanner(banner)),
              Some(attacker))).toVector
```

```scala
        Burn.favor(defender.board.favor / 2,
          PositionedLocation(Location.PlayArea(defenderId)),
          Some(defenderId))).toVector
```

- [ ] **Step 6: Give the test suites' burns their burner.** Each of these builds a `Burn`. Pass `None` unless the line says otherwise:

  - `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala`: lines near 276 and 396.
  - `src/test/scala/oathdigital/gameplay/OperationExecutorSuite.scala`: 5 calls.
  - `src/test/scala/oathdigital/gameplay/CoreOperationsSuite.scala`: 2 calls.
  - `src/test/scala/oathdigital/gameplay/OperationResolutionSuite.scala`: both burns in `Burn.favor(3, toArea) -> Burn.favor(1, toArea)`.
  - `src/test/scala/oathdigital/gameplay/powers/campaign/UnstableSummonSuite.scala`: only `.move` is compared, so `None` is fine.
  - `src/test/scala/oathdigital/application/gamelog/GameLogRareLinesSuite.scala`: 1 call.
  - `src/test/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayedSuite.scala`: the expected `leaving` burn is p1's, `Some(p1)`.
  - `src/test/scala/oathdigital/gameplay/powers/campaign/BookBurningSuite.scala`: the expected burn is the attacker's, `Some(b.actor)`.

- [ ] **Step 7: Run the affected suites.**

Run: `./sbtw "testOnly oathdigital.serialization.WalkerOperationWireSuite oathdigital.gameplay.OperationExecutorSuite oathdigital.gameplay.CoreOperationsSuite oathdigital.gameplay.OperationResolutionSuite oathdigital.gameplay.powers.* oathdigital.gameplay.CampaignRaidSuite oathdigital.application.gamelog.*"`
Expected: PASS, including the new codec test.

- [ ] **Step 8: Commit.**

```bash
git add src/main/scala/oathdigital/model/CoreOperations.scala src/main/scala/oathdigital/gameplay/operations/OperationResolution.scala src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala src/main/scala/oathdigital/gameplay/powers/SecretBurns.scala src/main/scala/oathdigital/gameplay/powers/campaign/BookBurning.scala src/main/scala/oathdigital/gameplay/powers/action/AncientBinding.scala src/main/scala/oathdigital/gameplay/powers/economy/Defame.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/Revelation.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/Riots.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/Charlatan.scala src/main/scala/oathdigital/gameplay/powers/setup/GreatMarketRules.scala src/main/scala/oathdigital/gameplay/powers/action/Firebrand.scala src/main/scala/oathdigital/gameplay/powers/action/AmberFlame.scala src/main/scala/oathdigital/gameplay/powers/campaign/UnstableSummon.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignRaid.scala src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala src/test/scala/oathdigital/gameplay/OperationExecutorSuite.scala src/test/scala/oathdigital/gameplay/CoreOperationsSuite.scala src/test/scala/oathdigital/gameplay/OperationResolutionSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/UnstableSummonSuite.scala src/test/scala/oathdigital/application/gamelog/GameLogRareLinesSuite.scala src/test/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayedSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/BookBurningSuite.scala
git commit -m "feat(batch8): every burn names its burner"
```

---

### Task 3: The burn pass

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powerresolver/StepBurns.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala` (after `onArrival`)
- Create: `src/main/scala/oathdigital/gameplay/walker/FollowUpStep.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/AdviserArrivalPass.scala`
- Create: `src/main/scala/oathdigital/gameplay/walker/BurnPass.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSteps.scala` (`recordBatch`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (`walkComposite`)
- Test: `src/test/scala/oathdigital/gameplay/walker/BurnPassSuite.scala`

**Interfaces:**
- Consumes: `Burn.by` (Task 2).
- Produces: `Burnt`, `StepBurns(actor, after, burns)`, `StepBurns.in(executed: Vector[CoreOperation]): Vector[Burnt]`, `StepBurns.moved(operation: CoreOperation, by: PlayerId): Option[Burnt]`, `BurnFollowUp(operations, note)`, `ContributingPower.onBurns(burns: StepBurns): Option[BurnFollowUp]` (default `None`).

- [ ] **Step 1: Write the failing tests.** Create `BurnPassSuite.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{BurnFollowUp, Burnt,
  ContributingPower, Contribution, StepBurns}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class BurnPassSuite extends munit.FunSuite:
  /** A test power: takes for p1, from the shared bank, all the favor any
    * other player burnt in a step, with no line. */
  private object TakesOthersBurntFavorForP1 extends ContributingPower:
    val id: PowerId = PowerId("test.takes-others-burnt-favor")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def onBurns(burns: StepBurns): Option[BurnFollowUp] =
      val favor = burns.burns.collect {
        case Burnt(Piece.Favor(amount), _, by) if by != p1 => amount }.sum
      Option.when(favor > 0)(BurnFollowUp(Vector(Take(Piece.Favor(favor),
        p1, Location.SharedBank, Location.PlayArea(p1))),
        Note(id, _ => None)))

  private val powers = WalkerPowers(Vector(TakesOthersBurntFavorForP1))
  /** p1 with no favor, p2 with 3. */
  private val start: ReadyGame = Table.start.favor(p1, 0).favor(p2, 3).ready
  private val p2Board = PositionedLocation(Location.PlayArea(p2))

  /** The end state and the events of walking `tree` from `start`. */
  private def walked(tree: Operation)(using munit.Location)
      : (ReadyGame, Vector[OathEvent]) =
    accepted(ProcedureWalker.advance(start, tree, None, powers)) match
      case WalkerOutcome.Finished(end, events) => (end, events)
      case other => fail(s"the walk did not finish: $other")

  /** Each recorded step's operations, with the powers credited with it. */
  private def steps(events: Vector[OathEvent])
      : Vector[(Vector[CoreOperation], Vector[PowerId])] =
    events.collect { case step: WalkerStepRecorded =>
      (step.ops, step.contributions) }

  /** The test power's step after p2 burnt 2 favor. */
  private val takenForP1: (Vector[CoreOperation], Vector[PowerId]) =
    (Vector(Take(Piece.Favor(2), p1, Location.SharedBank,
      Location.PlayArea(p1))), Vector(TakesOthersBurntFavorForP1.id))

  test("StepBurns reads named burns, a cost's burnt portions and a Take's " +
      "leaving, and skips a burn no player made"):
    val onDarkestSecret =
      PositionedLocation(Location.OnBanner(Banner.DarkestSecret))
    val read = StepBurns.in(Vector(
      Burn.favor(2, p2Board, Some(p2)),
      Burn.favor(1, p2Board, None),
      PayCost(p1, Location.SharedBank, Cost(favorBurnt = 1, secretBurnt = 2)),
      Take(Piece.Banner(Banner.DarkestSecret), p1, Location.PlayArea(p2),
        Location.PlayArea(p1),
        leaving = Vector(Burn.secrets(3, onDarkestSecret, Some(p1))))))
    assertEquals(read, Vector(
      Burnt(Piece.Favor(2), Location.PlayArea(p2), p2),
      Burnt(Piece.Favor(1), Location.PlayArea(p1), p1),
      Burnt(Piece.Secrets(2), Location.PlayArea(p1), p1),
      Burnt(Piece.Secrets(3), Location.OnBanner(Banner.DarkestSecret), p1)))

  test("a batch that burns is followed by the taking power's own step"):
    val burn = Burn.favor(2, p2Board, Some(p2))
    val (end, events) =
      walked(BuildOps((_, _) => Right(Vector[CoreOperation](burn))))
    assertEquals(Look(end).favor(p2), 1)
    assertEquals(Look(end).favor(p1), 2)
    assertEquals(steps(events).map(_._1),
      Vector(Vector[CoreOperation](burn), takenForP1._1))
    assertEquals(steps(events).last, takenForP1)

  test("a cost or a burn walked as its moves is read once it is done, and " +
      "a player's own burn is left alone"):
    val (paid, paidEvents) = walked(Sequence(Vector[Operation](
      PayCost(p2, Location.SharedBank, Cost(favorBurnt = 2)))))
    assertEquals(Look(paid).favor(p1), 2)
    assertEquals(steps(paidEvents).last, takenForP1)
    val (burnt, burntEvents) = walked(Sequence(Vector[Operation](
      Burn.favor(2, p2Board, Some(p2)))))
    assertEquals(Look(burnt).favor(p1), 2)
    assertEquals(steps(burntEvents).last, takenForP1)
    val (own, ownEvents) = walked(Sequence(Vector[Operation](
      Burn.favor(2, p2Board, Some(p1)))))
    assertEquals(Look(own).favor(p1), 0)
    assertEquals(steps(ownEvents).size, 1)
```

- [ ] **Step 2: Run them to see them fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.BurnPassSuite"`
Expected: FAIL to compile: `StepBurns`, `Burnt` and `BurnFollowUp` are not found.

- [ ] **Step 3: Add what a burn rule reads.** Create `StepBurns.scala`:

```scala
package oathdigital.gameplay.powerresolver

import oathdigital.model.{Burn, CoreOperation, Location, Move, Note,
  PayCost, Piece, PlayerId, PositionedLocation, PrimitiveOperation,
  ReadyGame}

/** Favor or secrets a walker step burnt: how many, where from, and the
  * player who burnt them (catalog batch 8, "A burn's actor"). */
final case class Burnt(resource: Piece.Counted, from: Location,
    by: PlayerId)

/** What a burn rule reads: the active player, the state after the step,
  * and the step's burns, in batch order. `after` already holds the
  * follow-ups of the rules asked before. */
final case class StepBurns(actor: PlayerId, after: ReadyGame,
    burns: Vector[Burnt])

object StepBurns:
  /** The burns `executed` ran: each `Burn` that names its burner, inside a
    * composite such as a `Take`'s leaving too, and each cost's burnt
    * portions, which are its payer's. A burn no player made is none. */
  def in(executed: Vector[CoreOperation]): Vector[Burnt] =
    executed.flatMap {
      case burn: Burn =>
        burn.by.toVector.map(Burnt(burn.resource, burn.from.location, _))
      case pay: PayCost =>
        val from = Location.PlayArea(pay.player)
        Vector(
          Option.when(pay.cost.favorBurnt > 0)(Burnt(
            Piece.Favor(pay.cost.favorBurnt), from, pay.player)),
          Option.when(pay.cost.secretBurnt > 0)(Burnt(
            Piece.Secrets(pay.cost.secretBurnt), from, pay.player))).flatten
      case _: PrimitiveOperation => Vector.empty
      case composite => in(composite.children.collect {
        case core: CoreOperation => core })
    }

  /** `operation` as `by`'s burn, when it moves favor or secrets to the
    * shared bank. */
  def moved(operation: CoreOperation, by: PlayerId): Option[Burnt] =
    operation match
      case Move(resource: Piece.Favor, from,
          PositionedLocation(Location.SharedBank, _), _) =>
        Some(Burnt(resource, from.location, by))
      case Move(resource: Piece.Secrets, from,
          PositionedLocation(Location.SharedBank, _), _) =>
        Some(Burnt(resource, from.location, by))
      case _ => None

/** A burn rule's answer: the operations to run after the step, as one
  * required batch, and the line journaled after them. */
final case class BurnFollowUp(operations: Vector[CoreOperation], note: Note)
```

In `ContributingPower`, after `onArrival`, add:

```scala
  /** The follow-up this power adds after a walker step that burnt favor or
    * secrets (catalog batch 8, "A burn's actor"). None by default. */
  def onBurns(burns: StepBurns): Option[BurnFollowUp] = None
```

- [ ] **Step 4: Share the follow-up step.** Create `FollowUpStep.scala`. It is `AdviserArrivalPass.followed`, moved and widened to take the operations and the note:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy,
  OperationRun}
import oathdigital.model.{CoreOperation, Note, NoteStates, OathViolation,
  PowerId}

/** One power's follow-up after a walker step: the adviser arrival pass's
  * and the burn pass's. Its operations run as one required batch, recorded
  * as their own step at the step's node and credited to the power, and its
  * line is journaled right after them. A follow-up with no operations
  * records no step and journals only its line. The walk's `previous` stays
  * as it was. */
private[walker] object FollowUpStep:
  def recorded(ctx: WalkCtx, power: PowerId,
      operations: Vector[CoreOperation], note: Note, nodeId: String,
      label: String): Either[OathViolation, WalkCtx] =
    val ran =
      if operations.isEmpty then
        Right(OperationRun(ctx.state, Vector.empty, Vector.empty))
      else OperationPipeline.run(ctx.state, operations,
        OperationPolicy.Permissive, ctx.powers.operationRestrictions,
        requireAll = true)(Right(_))
    ran.map { updated =>
      val step = Option.when(updated.executed.nonEmpty)(WalkerStepRecorded(
        nodeId = nodeId,
        payload = WalkerStepPayload.DeltaRecorded(
          DeltaMeaning.of(updated.executed, label)),
        ops = updated.executed,
        contributions = Vector(power)))
      val line = note.build(NoteStates(updated.state,
        Some((ctx.state, updated.state)), ctx.answered)).map(built =>
        PowerNoted(note.power, built, note.covers))
      ctx.copy(state = updated.state, events = ctx.events ++ step ++ line)
    }
```

In `AdviserArrivalPass`:
- Delete `followed`.
- Call the shared step in its place:

  ```scala
              .fold[Either[OathViolation, WalkCtx]](Right(ctx))(followUp =>
                FollowUpStep.recorded(ctx, power.id, followUp.operations,
                  followUp.note, nodeId, label)))
  ```

- Drop the imports it no longer uses: `OperationPipeline`, `OperationPolicy`, `OperationRun`, `ArrivalFollowUp`, `NoteStates` and `PowerId`.
- Keep the class doc comment's description of a follow-up step as it is.

- [ ] **Step 5: Add the burn pass.** Create `BurnPass.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{Burnt, ContributingPower,
  StepBurns}
import oathdigital.model.{Burn, CoreOperation, OathViolation, Operation,
  PayCost}

/** The burn pass (catalog batch 8, "A burn's actor"). After a walker step
  * burns favor or secrets, its burns go to every power's `onBurns`, in the
  * order contributions chain, and each follow-up runs as its own step
  * ([[FollowUpStep]]). A step's burns are read from the batch it executed,
  * or, for a `Burn` or a `PayCost` the walk split into its moves, from those
  * moves once the composite is done. A follow-up's own burns start no
  * second pass, and an adviser arrival's follow-up starts none. The pass
  * names no card.
  */
private[walker] object BurnPass:
  /** `ran`, the walk after a step that executed `executed`, with the
    * follow-ups of its burns run. */
  def afterBatch(ran: WalkCtx, executed: Vector[CoreOperation],
      nodeId: String): Either[OathViolation, WalkCtx] =
    follow(ran, StepBurns.in(executed), nodeId)

  /** `ran`, the walk after `composite` was walked as its moves from `from`,
    * with the follow-ups of its burns run. Only a `Burn` and a `PayCost`
    * burn: each of their moves of favor or secrets to the shared bank is
    * the `Burn`'s burner's, or the payer's. */
  def afterComposite(composite: Operation, from: WalkCtx, ran: WalkCtx,
      nodeId: String): Either[OathViolation, WalkCtx] =
    val burner = composite match
      case burn: Burn => burn.by
      case pay: PayCost => Some(pay.player)
      case _ => None
    burner.fold[Either[OathViolation, WalkCtx]](Right(ran))(by =>
      follow(ran, ran.events.drop(from.events.size).collect {
        case step: WalkerStepRecorded => step.ops
      }.flatten.flatMap(StepBurns.moved(_, by)), nodeId))

  private def follow(ran: WalkCtx, burns: Vector[Burnt], nodeId: String)
      : Either[OathViolation, WalkCtx] =
    if burns.isEmpty then Right(ran)
    else ran.powers.powers.sortBy(ContributingPower.sortKey)
      .foldLeft[Either[OathViolation, WalkCtx]](Right(ran)) {
        (walked, power) => walked.flatMap(ctx =>
          power.onBurns(StepBurns(ctx.activePlayer, ctx.state, burns))
            .fold[Either[OathViolation, WalkCtx]](Right(ctx))(followUp =>
              FollowUpStep.recorded(ctx, power.id, followUp.operations,
                followUp.note, nodeId, label)))
      }

  /** The fallback name of a follow-up's step. */
  private val label = "BurnTaken"
```

- [ ] **Step 6: Run the pass after a batch.** In `WalkerSteps.recordBatch`, run the burn pass on the recorded step before the adviser arrival pass:

```scala
      BurnPass.afterBatch(ctx.copy(state = updated.state, events = events,
          previous = Some((ctx.state, updated.state))), updated.executed,
          nodeId)
        .flatMap(AdviserArrivalPass.after(ctx.state, _, nodeId))
```

Extend its doc comment's last sentence to say: "The burn pass then runs the follow-ups of the batch's burns (`BurnPass`), and the adviser arrival pass those of any adviser that arrived in the batch (`AdviserArrivalPass`)."

- [ ] **Step 7: Run the pass after a split `Burn` or `PayCost`.** In `ProcedureWalker.walkComposite`, chain the pass onto the walk of the folded children:

```scala
        else walkFolded(composite.window, composite, composite.children, ctx,
          path, cursor, resume,
          if required then hooks.copy(strict = true) else hooks)
          .flatMap(burnt(composite, ctx, path))
```

Add after `walkComposite`:

```scala
  /** A `Burn` or a `PayCost` walked as its moves hands its burns to the
    * burn pass once it is done, as a batch's step does for its own. */
  private def burnt(composite: Operation, from: WalkCtx,
      path: Vector[String])(step: Step): Either[OathViolation, Step] =
    step match
      case Done(ran) => BurnPass.afterComposite(composite, from, ran,
        if path.isEmpty then WalkerSteps.leafLabel(composite)
        else path.mkString(".")).map(Done(_))
      case other => Right(other)
```

- [ ] **Step 8: Run the tests.**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.* oathdigital.gameplay.CampaignRaidSuite oathdigital.gameplay.powers.cardplay.* oathdigital.gameplay.powers.action.*"`
Expected: PASS. No production power answers `onBurns` yet, so every existing suite reads as before.

- [ ] **Step 9: Commit.**

```bash
git add src/main/scala/oathdigital/gameplay/powerresolver/StepBurns.scala src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala src/main/scala/oathdigital/gameplay/walker/FollowUpStep.scala src/main/scala/oathdigital/gameplay/walker/AdviserArrivalPass.scala src/main/scala/oathdigital/gameplay/walker/BurnPass.scala src/main/scala/oathdigital/gameplay/walker/WalkerSteps.scala src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/test/scala/oathdigital/gameplay/walker/BurnPassSuite.scala
git commit -m "feat(batch8): a burn pass runs powers' follow-ups after a step burns"
```

---

### Task 4: Vow of Silence and Vow of Division

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/burn/BurnVows.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala` (remove `VowOfSilenceCard`)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala` (remove `VowOfDivisionCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import the cards)
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala` (register the Vows)
- Test: `src/test/scala/oathdigital/gameplay/powers/burn/VowOfSilenceSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/burn/VowOfDivisionSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala` (`persistentRules`)

**Interfaces:**
- Consumes: `StepBurns`, `Burnt`, `BurnFollowUp` and `ContributingPower.onBurns` (Task 3).
- Produces: `VowOfSilence`, `VowOfDivision`, `BurnVow.holderOf(ready): Option[PlayerId]`, `BurnVow.taken`, `BurnVow.takenBy`, `VowOfSilence.forbidden`.

- [ ] **Step 1: Move the cards.** Cut `VowOfSilenceCard` out of `HoldingArcaneDenizens.scala` and `VowOfDivisionCard` out of `HoldingDiscordDenizens.scala`.
  - Vow of Silence was the only `Locked` card in `HoldingArcaneDenizens.scala`, so drop `Locked` from that file's `oathdigital.catalog` import.
  - `HoldingDiscordDenizens.scala` still uses `Locked` and `AdviserOnly`, so its imports stay.

In `NewFoundations.scala`, add `import oathdigital.gameplay.powers.burn.{VowOfDivisionCard, VowOfSilenceCard}`. The `holding._` wildcard no longer supplies them.

- [ ] **Step 2: Write the failing tests.** Create `VowOfSilenceSuite.scala`:

```scala
package oathdigital.gameplay.powers.burn

import oathdigital.gameplay.CampaignFixture.{raidBoard, rules,
  withAdviserFor}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.action.DemonTail
import oathdigital.gameplay.powers.campaign.PlanDriver.{awaits, commit,
  winningDice}
import oathdigital.gameplay.powers.targeting.TargetingFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, MenuPlay,
  Outcomes, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class VowOfSilenceSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Silence")
  private val tail = CatalogNames.relic("Demon Tail")
  private val game = ActPowerHarness.rulesWithPrintedRestrictions()

  /** The lines Vow of Silence journaled in `events`. */
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(VowOfSilence.id, VowOfSilence.noteKeys, events)

  /** p1's Act holding Demon Tail (burn 3 secrets: gain 2 Supply) with 3
    * faceup secrets and 2 Supply, and `holder` holding Vow of Silence
    * faceup. */
  private def demonTailWithVowHeldBy(holder: PlayerId): ReadyGame =
    Table.start.turn(p1, Phase.Act).relic(p1, tail).supply(p1, 2)
      .secrets(p1, 3).adviser(holder, vow).ready

  /** p1 at Ancient City, holding Vow of Silence, can challenge p2 there for
    * the People's Favor (2 favor) or the Darkest Secret (1 secret). */
  private val challengeForEither: Table = TargetingFixture.challengeTable
    .darkestSecret(Some(p2), secrets = 1).adviser(p1, vow)

  test("an enemy's burnt cost goes to the holder, faceup, with its line"):
    val ready0 = demonTailWithVowHeldBy(p2)
    val done = accepted(ActPowerHarness.use(game, ready0, DemonTail.id,
      DecisionOptionRef.Relic(tail)))
    val end = Outcomes.ready(done.state)
    assertEquals(Look(end).faceUpSecrets(p1), 0)
    assertEquals(Look(end).faceUpSecrets(p2),
      Look(ready0).faceUpSecrets(p2) + 3)
    assertEquals(said(done.events), Vector(NoteText.Said("taken-by",
      s"${p2.value} took 3 secrets instead of the bank.", covers = true)))
    assertEquals(ActPowerHarness.replayed(game, ready0, done.events), end)
    assertWireRoundTrips(done.events)

  test("the holder's own burnt cost goes to the bank"):
    val ready0 = demonTailWithVowHeldBy(p1)
    val done = accepted(ActPowerHarness.use(game, ready0, DemonTail.id,
      DecisionOptionRef.Relic(tail)))
    assertEquals(Look(Outcomes.ready(done.state)).faceUpSecrets(p1), 0)
    assertEquals(said(done.events), Vector.empty)

  test("a defender holding it takes back the Darkest Secret's secrets the " +
      "attacker burns as they take it"):
    val raid = raidBoard()
    val b = withAdviserFor(raid.board, raid.board.rival, vow,
      Orientation.FaceUp)
    val before = Look(b.ready).faceUpSecrets(b.rival)
    val run = commit(rules(winningDice), b, 4, targets = Vector(
      DecisionOptionRef.Banner(Banner.DarkestSecret)), raid = true)
      .finishPlanWindows
    assertEquals(run.winner, Some(true))
    assert(awaits(run, b.actor, CampaignIds.relocation))
    val after = Outcomes.ready(run.state).game.current
    assertEquals(BannerRules.holder(after, Banner.DarkestSecret),
      Some(b.actor))
    assertEquals(after.banners.darkestSecret.secrets, 0)
    assertEquals(Look(run.state).faceUpSecrets(b.rival), before + 2)
    assertEquals(said(run.events), Vector(NoteText.Said("taken-by",
      s"${b.rival.value} took 2 secrets instead of the bank.",
      covers = true)))

  test("its holder sees the Darkest Secret unavailable, with the reason, " +
      "and may still challenge for the People's Favor"):
    val started = accepted(TargetingFixture.start(challengeForEither.ready,
      ActionRef.Challenge, p1))
    val banners = MenuPlay.options(started.state, catalog)
    assertEquals(banners.find(_.ref ==
      DecisionOptionRef.Banner(Banner.PeoplesFavor)).map(_.available),
      Some(true))
    assertEquals(banners.collectFirst {
      case DecisionOption.Unavailable(option, reason)
          if option.ref == DecisionOptionRef.Banner(Banner.DarkestSecret) =>
        reason
    }, Some(OptionReason.Noted(VowOfSilence.id, VowOfSilence.forbidden(
      PowerSourceRef.Card(vow), NoteArg.Banner(Banner.DarkestSecret)))))

  test("a holder who could challenge only for the Darkest Secret cannot " +
      "start a Challenge, and the menu says why"):
    // With no favor, the People's Favor is out of reach.
    val ready0 = challengeForEither.favor(p1, 0).ready
    MenuPlay.menuOptionFor(catalog, ready0, ActionRef.Challenge) match
      case DecisionOption.Unavailable(_, reason) => assertEquals(reason,
        OptionReason.Written("Vow of Silence prevents you from " +
          "challenging the Darkest Secret"))
      case other => fail(s"the Challenge is offered: $other")
```

Create `VowOfDivisionSuite.scala`:

```scala
package oathdigital.gameplay.powers.burn

import oathdigital.gameplay.CampaignFixture.{raidBoard, rules,
  withAdviserFor}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver.{awaits, commit,
  winningDice}
import oathdigital.gameplay.powers.targeting.TargetingFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, MenuPlay,
  Outcomes}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}

class VowOfDivisionSuite extends munit.FunSuite:
  private val vow = CatalogNames.denizen("Vow of Division")

  /** The lines Vow of Division journaled in `events`. */
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(VowOfDivision.id, VowOfDivision.noteKeys, events)

  test("an attacker holding it takes the favor a defeated defender burns"):
    val raid = raidBoard()
    val b = withAdviserFor(raid.board, raid.board.actor, vow,
      Orientation.FaceUp)
    val before = Look(b.ready).favor(b.actor)
    val run = commit(rules(winningDice), b, 4,
      targets = Vector(DecisionOptionRef.Relic(raid.relic)), raid = true)
      .finishPlanWindows
    assertEquals(run.winner, Some(true))
    assert(awaits(run, b.actor, CampaignIds.relocation))
    assertEquals(Look(run.state).favor(b.rival), 5 - 5 / 2)
    assertEquals(Look(run.state).favor(b.actor), before + 5 / 2)
    assertEquals(said(run.events), Vector(NoteText.Said("taken",
      "Took 2 favor instead of the bank.", covers = true)))
    assertEquals(ActPowerHarness.replayed(run.game, b.ready, run.events),
      Outcomes.ready(run.state))
    assertWireRoundTrips(run.events)

  test("a defender holding it still loses half their favor to the bank: " +
      "a Raid's burn is their own"):
    val raid = raidBoard()
    val b = withAdviserFor(raid.board, raid.board.rival, vow,
      Orientation.FaceUp)
    val before = Look(b.ready).favor(b.actor)
    val run = commit(rules(winningDice), b, 4,
      targets = Vector(DecisionOptionRef.Relic(raid.relic)), raid = true)
      .finishPlanWindows
    assertEquals(run.winner, Some(true))
    assertEquals(Look(run.state).favor(b.rival), 5 - 5 / 2)
    assertEquals(Look(run.state).favor(b.actor), before)
    assertEquals(said(run.events), Vector.empty)

  test("its holder cannot take the Challenge action, and the menu says " +
      "why; another player's Vow does not stop them"):
    val held = TargetingFixture.challengeTable.adviser(p1, vow).ready
    MenuPlay.menuOptionFor(catalog, held, ActionRef.Challenge) match
      case DecisionOption.Unavailable(_, reason) => assertEquals(reason,
        OptionReason.Written("Vow of Division prevents you from challenging"))
      case other => fail(s"the Challenge is offered: $other")
    val elsewhere = TargetingFixture.challengeTable.adviser(p2, vow).ready
    assert(MenuPlay.availableInMenu(catalog, elsewhere, ActionRef.Challenge))
```

In `PowerKindsCatalogSuite`, add `"denizen.vow-of-silence"` and `"denizen.vow-of-division"` to the end of `persistentRules`. Both cards print `persistent = true`, so the existing test "resolution follows the flag" covers them.

- [ ] **Step 3: Run them to see them fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.burn.* oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: FAIL to compile: `VowOfSilence` and `VowOfDivision` are not found.

- [ ] **Step 4: Write the Vows.** Create `BurnVows.scala`:

```scala
package oathdigital.gameplay.powers.burn

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

object VowOfSilenceCard extends Denizen(DenizenId("74"), "Vow of Silence", Suit.Arcane) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-silence"),
    persistent = true, cost = Cost.free,
    text = "You **cannot** challenge the Darkest Secret. Whenever an " +
      "enemy burns [secret-burnt], you take those secrets.")
  val powers: Vector[PrintedPower] = Vector(power)

object VowOfDivisionCard extends Denizen(DenizenId("86"), "Vow of Division", Suit.Discord) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-division"),
    persistent = true, cost = Cost.free,
    text = "You **cannot** take the Challenge action. Whenever an enemy " +
      "burns [favor-burnt], you take that favor unless it was " +
      "burned by a raid targeting you.")
  val powers: Vector[PrintedPower] = Vector(power)

/** A vow that takes what its holder's enemies burn of one resource
  * (catalog batch 8, slice 5), and bars a Challenge. The holder is the
  * player who holds the card faceup as an adviser; the card is
  * adviser-only, so no other place counts.
  *
  * Its taking half answers the burn pass. After a step in which players
  * other than the holder burnt the resource, the holder takes as many from
  * the shared bank, faceup for secrets, as one step and one line. The burn
  * itself stands. Every other player is an enemy, and the holder's own
  * burns go to the bank. Its Challenge half is each Vow's own.
  */
sealed abstract class BurnVow extends ContributingPower:
  def cardId: DenizenId
  /** The resource it takes, as a line counts it. */
  protected def unit: NoteUnit
  /** `amount` of the resource it takes. */
  protected def piece(amount: Int): Piece.Counted
  /** How much of the resource it takes `burnt` holds: none of another. */
  protected def amountOf(burnt: Piece.Counted): Int

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  /** The player holding the card faceup, if any. */
  final def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

  final override def onBurns(burns: StepBurns): Option[BurnFollowUp] = for
    holder <- holderOf(burns.after)
    amount = burns.burns.filter(_.by != holder)
      .map(burnt => amountOf(burnt.resource)).sum
    if amount > 0
  yield BurnFollowUp(Vector(Take(piece(amount), holder, Location.SharedBank,
      Location.PlayArea(holder))),
    Note(id, _ => Some(line(holder, burns.actor, amount)), covers = true))

  /** The take, naming the holder unless it is their turn. */
  private def line(holder: PlayerId, actor: PlayerId, amount: Int)
      : PowerNote =
    val card = PowerSourceRef.Card(cardId)
    val taken = NoteArg.Amount(amount, unit)
    if holder == actor then BurnVow.taken(card, taken)
    else BurnVow.takenBy(card, NoteArg.Player(holder), taken)

object BurnVow:
  /** "Took {2 secrets} instead of the bank." */
  val taken: NoteKey = NoteKey("taken", Vector(NotePart.Text("Took "),
    NotePart.Arg(0), NotePart.Text(" instead of the bank.")))
  /** "{Blue} took {2 secrets} instead of the bank." */
  val takenBy: NoteKey = NoteKey("taken-by", Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1),
    NotePart.Text(" instead of the bank.")))

/** Vow of Silence (74): "You cannot challenge the Darkest Secret. Whenever
  * an enemy burns [secret-burnt], you take those secrets."
  *
  * Its holder's banner choice keeps the Darkest Secret, unavailable, with
  * the reason; when it is the only banner they could challenge, the
  * Challenge cannot start. */
case object VowOfSilence extends BurnVow:
  val cardId: DenizenId = VowOfSilenceCard.id
  val id: PowerId = VowOfSilenceCard.power.id
  protected def unit: NoteUnit = NoteUnit.Secret
  protected def piece(amount: Int): Piece.Counted = Piece.Secrets(amount)
  protected def amountOf(burnt: Piece.Counted): Int = burnt match
    case Piece.Secrets(amount) => amount
    case _ => 0

  /** "Cannot challenge the {Darkest Secret}.": the banner's hover
    * reason. */
  val forbidden: NoteKey = NoteKey("forbidden", Vector(
    NotePart.Text("Cannot challenge the "), NotePart.Arg(0),
    NotePart.Text(".")))

  override def noteKeys: Vector[NoteKey] =
    Vector(BurnVow.taken, BurnVow.takenBy, forbidden)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ChallengeBannerSelection ->
      Vector(OptionRestriction(guard, reason)))

  private def guard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = ref match
    case DecisionOptionRef.Banner(Banner.DarkestSecret)
        if holderOf(ctx.state).contains(ctx.activePlayer) =>
      Some(OathViolation.StartUnavailable(
        "Vow of Silence prevents you from challenging the Darkest Secret"))
    case _ => None

  private def reason(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[PowerNote] = ref match
    case DecisionOptionRef.Banner(Banner.DarkestSecret) =>
      Some(forbidden(PowerSourceRef.Card(cardId),
        NoteArg.Banner(Banner.DarkestSecret)))
    case _ => None

/** Vow of Division (86): "You cannot take the Challenge action. Whenever an
  * enemy burns [favor-burnt], you take that favor unless it was burned by a
  * raid targeting you."
  *
  * A Raid's half-favor burn is the defeated defender's own, so the
  * exception needs no rule of its own: a defender holding the Vow is not
  * their own enemy. Its holder's Challenge is refused at the Challenge's
  * root, as Vow of Peace refuses a Campaign. */
case object VowOfDivision extends BurnVow:
  val cardId: DenizenId = VowOfDivisionCard.id
  val id: PowerId = VowOfDivisionCard.power.id
  protected def unit: NoteUnit = NoteUnit.Favor
  protected def piece(amount: Int): Piece.Counted = Piece.Favor(amount)
  protected def amountOf(burnt: Piece.Counted): Int = burnt match
    case Piece.Favor(amount) => amount
    case _ => 0

  override def noteKeys: Vector[NoteKey] =
    Vector(BurnVow.taken, BurnVow.takenBy)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ChallengeActionEligibility -> Vector(Restriction((ctx, _) =>
      Option.when(holderOf(ctx.state).contains(ctx.activePlayer))(
        OathViolation.StartUnavailable(
          "Vow of Division prevents you from challenging")))))
```

The card objects keep their long first lines, as in the holding files they came from.

- [ ] **Step 5: Register the Vows.** In `WalkerPowerCatalog.default`, add `Vector(VowOfSilence, VowOfDivision) ++` right after `Vector(VowOfPeaceContribution) ++`. Import them with `import oathdigital.gameplay.powers.burn.{VowOfDivision, VowOfSilence}`. Add to the doc comment, after the Vow of Peace sentence: "Vow of Silence and Vow of Division take enemies' burns through the walker's burn pass, and are inert at the Challenge until a Challenge walks its windows."

- [ ] **Step 6: Run the tests.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.burn.* oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.gameplay.powers.PowerImplementationStatusSuite oathdigital.gameplay.powers.targeting.*"`
Expected: PASS.

Stop and report, without changing the tests, if either menu test sees a reason other than the Vow's. That would mean a Challenge's start refusal does not reach the menu's hover reason, which is a seam this plan assumed. Also stop and report if the Darkest Secret is missing from the banner options, rather than unavailable.

- [ ] **Step 7: Commit.**

```bash
git add src/main/scala/oathdigital/gameplay/powers/burn/BurnVows.scala src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/test/scala/oathdigital/gameplay/powers/burn/VowOfSilenceSuite.scala src/test/scala/oathdigital/gameplay/powers/burn/VowOfDivisionSuite.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(batch8): Vow of Silence and Vow of Division take enemies' burns"
```

---

### Task 5: Gates and docs

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` (Status line)
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Run the gates.**

Run, in order:

1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass.

- The server count is your baseline plus 12.
- The frontend count is unchanged.

- [ ] **Step 2: Check the client.**

Run: `grep -rn '"burn"\|Vow of Silence\|Vow of Division' frontend/src/main`
Expected: nothing reads a recorded burn's fields. The client shows the Vows' lines and hover reasons through the generic note and option rendering. Report anything else you find instead of changing it.

- [ ] **Step 3: Update the docs.**
  - In the design, change the Status line to "Slices 1 to 5 are done."
  - In `docs/ROADMAP.md`:
    - check the slice 5 box: `- [x] **5. Burns taken:** Vow of Silence, Vow of Division.`;
    - set the Now line to "**Phase - Catalog batch 8** is under way: slices 1 to 5 are done, and slice 6 is next."

Run: `python3 scripts/check-markdown-links.py`
Expected: PASS.

- [ ] **Step 4: Commit.**

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/ROADMAP.md
git commit -m "docs(batch8): slice 5 done"
```
