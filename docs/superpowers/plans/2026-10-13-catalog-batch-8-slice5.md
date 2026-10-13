# Catalog Batch 8, Slice 5 (Burns Taken) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Vow of Silence (74) and Vow of Division (86). When an enemy burns secrets or favor, the holder takes them instead, and each card stops its holder from challenging.

**Architecture:**

- **A secret move names its side.** `PositionedLocation` gains `side: Option[SecretSide]`, for secrets on a player's board, and `Take` gains `sourceSide`. The secret planner moves exactly that side, and the secrets keep their side where they land.
  - A burn with no side still takes faceup secrets only.
  - Book Burning and Ancient Binding burn facedown secrets by naming the side, where they flipped them faceup first. Without a Vow, the end state is the same.
- **A burn's actor.** `Burn` gains `by: Option[PlayerId]`, the player who burns. Every builder of a `Burn` states it.
  - `None` is a burn no player makes: Bandit Market's setup burn.
  - A cost's burnt portions are already the payer's, because `PayCost` names its player.
- **Burns taken.** Before the walker runs a batch, `BurnTakes` asks each power's new `ContributingPower.takesBurn` about every burn in it. A power may claim a burn for a taker, and the burn is then replaced by a `Take` of the same tokens to that taker. So a restriction on a `Take` holds against it, and the count is cut as a burn's would be.
  - **A burn:** it becomes the `Take`. Its secrets keep the side the burn named, faceup by default.
  - **A cost:** its burnt portions leave the `PayCost` and become required `Take`s.
  - **A banner:** a burn inside a banner `Take`'s `leaving` is replaced in place.
  - **The taker's own tokens:** a burn of the taker's own board leaves the tokens where they are.
  - **The line:** after the batch runs, the claiming power writes one line per taker and burner, from what the `Take`s actually moved.
- **Burns in the tree.** A `Burn` or `PayCost` in the tree is normally walked as its child moves, and a bare move has no burner. So when a power claims one of its burns, `walkComposite` runs it whole as one batch instead.
- **The Challenge halves:**
  - Vow of Division is a `Restriction` at `ChallengeActionEligibility`, as Vow of Peace is at the Campaign's.
  - Vow of Silence is an `OptionRestriction` at `ChallengeBannerSelection`, as the Fortress's is.
- **Codec:**
  - `"burn"` always writes `"by"`, as `null` when no player burns.
  - A position and a `Take` write a side only when there is one.
  - Old journals are not supported: a recorded burn without `"by"` no longer decodes.

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

If slice 4 has not merged when you start, branch from `main` at `1f75bc24` or later, and edit the two `Burn` calls in `CampaignRaid.transfer` instead. When this branch merges, expect conflicts in these files, and in each keep both sides:

- `CampaignRaid`;
- `CampaignLines`, if a pattern there gains an argument;
- the design's Status line and the ROADMAP's Now line. Write what is true on `main`.

Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it.
  - Each new file below lists the imports it needs.
  - When code moves out of a file, remove any import that file no longer uses.
  - A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
  - `WalkerOperationCodec.scala` is at 796 before this plan. Task 2 moves its secret-side helpers into a new `PositionWire` trait first, so it ends near 794.
  - Check with `wc -l` after each codec edit.
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker sources.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **No tree changes.** No procedure tree gains, loses or moves a node. A `Burn` or `PayCost` that a power claims runs as one batch at its own address.
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
  - `PositionedLocation.side: Option[SecretSide]` (default `None`) and `Take.sourceSide: Option[SecretSide]` (default `None`, last parameter).
  - `Burn.by: Option[PlayerId]`. The factories are `Burn.favor(amount, from, by)` and `Burn.secrets(amount, from, by)`, with `by` required and no default.
  - In `oathdigital.gameplay.powerresolver`, file `BurnTake.scala`:
    - `Burnt(resource: Piece.Counted, from: PositionedLocation, by: PlayerId)`;
    - `BurnTake(taker: PlayerId, line: (PlayerId, Int, PlayerId) => PowerNote)`.
  - `ContributingPower.takesBurn(ready: ReadyGame, burnt: Burnt): Option[BurnTake]`.
  - In `oathdigital.gameplay.walker`, the `private[walker]` object `BurnTakes`, with `replace`, `takes`, `Swapped` and `Taken`.
  - In `oathdigital.serialization`, the `private[serialization]` trait `PositionWire`.
  - In `oathdigital.gameplay.powers.burn`, file `BurnVows.scala`:
    - `BurnVow` (sealed abstract class);
    - `VowOfSilence` and `VowOfDivision` (case objects);
    - `VowOfSilenceCard` and `VowOfDivisionCard`.
  - Power ids are unchanged: `denizen.vow-of-silence` and `denizen.vow-of-division`.
  - Note keys: each Vow's `taken` (name `"taken"`) and `takenBy` (name `"taken-by"`), and `VowOfSilence.forbidden` (name `"forbidden"`).
- **Texts, exactly:**
  - The take line on the holder's own turn: "Took {Red}'s 2 burned secrets." or "Took {Red}'s 1 burned favor." `{Red}` is the burner.
  - The take line off the holder's turn: "{Blue} took {Red}'s 2 burned secrets."
  - "secret" is singular for a count of 1. "favor" never changes.
  - The Vow of Silence banner option's hover reason: "Cannot challenge the {Darkest Secret}."
  - The Challenge's hover reason in the menu:
    - "Vow of Division prevents you from challenging";
    - "Vow of Silence prevents you from challenging the Darkest Secret", when the Darkest Secret is the only banner the holder could challenge.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 15 server tests and no frontend test.
  - It changes three existing expectations, each to the new shapes:
    - `ConspiracyWhenPlayedSuite` (the burn's burner);
    - `BookBurningSuite` (a facedown burn by name instead of a flip, and the burn's burner);
    - `PowerKindsCatalogSuite` (the two Vows join its persistent rules).
  - Several suites only gain the new `by` argument where they build a `Burn`, or a `_` where they match a `PositionedLocation` or a `Take`.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

These are written into the design and the rulings appendix in Task 1.

1. **A taken burn becomes a `Take`.**
   - Before the burn runs, it is replaced by a `Take` of the same tokens to the holder.
   - A restriction on a `Take` holds against it.
   - The count is cut to what is there, as a burn's is.
2. **Secrets keep their side.**
   - A burn from a board takes faceup secrets, unless it names the facedown side. The taken secrets land on the same side they left.
   - Book Burning and Ancient Binding burn a board's facedown secrets by naming that side. They no longer flip them faceup first. Without a Vow, the end state is unchanged: the secrets kept are faceup when any faceup one remains.
3. **The holder's own tokens stay.** When an enemy burns tokens from the holder's own board, nothing moves, and the Vow's line still says what it took. Examples: Defame naming the holder, or Book Burning against a defender who holds Vow of Silence.

   The burning card's own line reads the board's change, so it reports nothing burnt. Book Burning, for one, then says "{Red} had no secret to burn."
4. **Costs.** A cost's burnt portion leaves its `PayCost` and becomes a `Take` that must be paid in full. The rest of the cost is paid as before.
5. **Banners.** A burn inside a banner `Take`'s `leaving` (Raid, Conspiracy) is replaced there. The secrets' `Take` rides inside the banner's, and is checked with it.
6. **A Raid's half-favor burn is the defeated defender's own.** With the attacker as the burner, an attacker who held Vow of Division would take nothing from the defender they beat. With the defender as the burner, the card reads as printed in every case:
   - an attacker or a bystander who holds it takes the defender's favor;
   - a defender who holds it does not, which is the card's "unless it was burned by a raid targeting you".
7. **Who burns, card by card.**
   - **Raid:** the attacker burns the Darkest Secret's secrets as they take it.
   - **Conspiracy:** its player burns the Darkest Secret's secrets as they take it.
   - **Book Burning:** the attacker.
   - **Ancient Binding:** each player burns their own board. A held card's secrets are its holder's burn, and the user burns the secrets on a card at a site.
   - **Revelation:** each player.
   - **Defame, Riots, Charlatan, Firebrand and Amber Flame:** the user.
   - **Unstable Summon:** the attacker.
   - **Costs:** the payer.
   - **Bandit Market's setup burn:** no player.
8. **An enemy is every other player.** Every game is all-Exile, as in earlier batches. A player's own burns go to the bank. When two powers claim the same burn, the first in the order contributions chain takes it.
9. **Lines.** One line per taker and burner per step.
   - "Took {Red}'s 2 burned secrets." on the holder's turn, and "{Blue} took {Red}'s 2 burned secrets." off it.
   - The line follows the burning step, so it comes before that step's own line.
   - A Raid's closing line no longer counts favor that a Vow took. The Vow's line says it.
10. **The holder holds the Vow faceup as an adviser.** The card is adviser-only. A facedown Vow does nothing.
11. **Vow of Division's Challenge half.**
    - Its holder cannot start a Challenge.
    - The menu shows Challenge unavailable, with the reason "Vow of Division prevents you from challenging".
12. **Vow of Silence's Challenge half.**
    - Its holder's banner choice shows the Darkest Secret unavailable, with the reason "Cannot challenge the Darkest Secret.".
    - When the Darkest Secret is the only banner they could challenge, the menu shows Challenge unavailable: "Vow of Silence prevents you from challenging the Darkest Secret".
13. **What is not replaced:**
    - an adviser arrival follow-up's burns (none exist today);
    - a modifier menu's dry run of its costs. It only asks whether the player can pay, and a Vow changes nothing there.
14. **No old journals.** A recorded burn always carries its burner, and a journal without it is not read.

## File map

- `src/main/scala/oathdigital/model/CoreOperations.scala`: `PositionedLocation.side`, `Move`'s side checks, `Take.sourceSide`, `Burn.by`.
- `src/main/scala/oathdigital/gameplay/operations/OperationSecretPlanner.scala`: a named side is moved exactly.
- `src/main/scala/oathdigital/gameplay/operations/OperationResolution.scala`: the counted rebuild keeps `by`.
- Create `src/main/scala/oathdigital/serialization/PositionWire.scala`. Modify `WalkerOperationCodec.scala`: the side, `sourceSide` and `by`.
- `src/main/scala/oathdigital/gameplay/powers/SecretBurns.scala`: facedown secrets burnt by name.
- Every `Burn` builder, as listed in Task 3:
  - `BookBurning`, `AncientBinding`, `Defame`, `Revelation`;
  - `ConspiracyWhenPlayed`, `Riots`, `Charlatan`, `GreatMarketRules`;
  - `Firebrand`, `AmberFlame`, `UnstableSummon`, `CampaignRaid`.
- Every pattern on `PositionedLocation(_, _)` or `Take(...)`, which gains a `_` (the compiler lists them).
- Create `src/main/scala/oathdigital/gameplay/powerresolver/BurnTake.scala`. Modify `ContributingPower.scala`: `takesBurn`.
- Create `src/main/scala/oathdigital/gameplay/walker/BurnTakes.scala`. Modify:
  - `WalkerSteps.scala`: `recordBatch` replaces the claimed burns;
  - `ProcedureWalker.scala`: `walkComposite` runs a claimed `Burn` or `PayCost` whole.
- Create `src/main/scala/oathdigital/gameplay/powers/burn/BurnVows.scala`. Modify:
  - the two holding files, which lose their cards;
  - `NewFoundations.scala`;
  - `WalkerPowerCatalog.scala`.
- Tests:
  - create `src/test/scala/oathdigital/gameplay/walker/BurnTakesSuite.scala`;
  - create `src/test/scala/oathdigital/gameplay/powers/burn/VowOfSilenceSuite.scala` and `VowOfDivisionSuite.scala`;
  - modify `OperationExecutorSuite`, `WalkerOperationWireSuite`, `PowerKindsCatalogSuite`, and the suites Tasks 2 and 3 list.
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
- **A burn's actor, and burns taken** (slice 5, detailed at plan time).
  Each `Burn` names the player who burns, as `by`:
  - the actor of the procedure that builds it;
  - each owner in an effect that makes each player burn;
  - the defeated defender, for a Raid's half-favor burn.

  A cost's burnt portions are its payer's. Before the walker runs a batch,
  each burn a power claims becomes a `Take` of the same tokens to its
  taker, so a restriction on a `Take` holds against it. A `Burn` or
  `PayCost` in the tree that a power claims runs whole as one batch.
- **A secret move's side** (slice 5, added at plan time). A secret move
  from a board may name the side it takes (`PositionedLocation.side`,
  `Take.sourceSide`), and the secrets keep their side where they land.
  Book Burning and Ancient Binding burn facedown secrets by naming that
  side, instead of flipping them faceup first.
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

Each `Burn` names its burner. Before a batch runs, Vow of Silence and Vow of
Division claim the secrets or favor that their holder's enemies burn, costs
included, and each claimed burn becomes a `Take` to the holder. Each card's
"cannot challenge" half refuses the Challenge at the Challenge's own
windows.

### Plan-time rulings

- **A taken burn becomes a `Take`**, so a restriction on a `Take` holds
  against it. The count is cut to what is there, as a burn's is.
- **Secrets keep their side.** A burn from a board takes faceup secrets
  unless it names the facedown side. Taken secrets land on the side they
  left. Book Burning and Ancient Binding burn facedown secrets by naming
  that side.
- **The holder's own tokens stay.** When an enemy burns from the holder's
  board, nothing moves, and the Vow's line still says what it took. The
  burning card's own line then reports nothing burnt.
- **Costs:** a cost's burnt portion becomes a `Take` that must be paid in
  full.
- **Banners:** a burn inside a banner's `Take` is replaced there and rides
  with it.
- **A Raid's half-favor burn is the defeated defender's own.**
  - An attacker or a bystander who holds Vow of Division takes it.
  - A defender who holds it does not, which is the card's "unless it was
    burned by a raid targeting you".
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
- **An enemy is every other player.** When two powers claim one burn, the
  first in the contribution order takes it.
- **Lines.**
  - One line per taker and burner per step, right after it: "Took {Red}'s
    2 burned secrets." on the holder's turn, "{Blue} took {Red}'s 2 burned
    secrets." off it.
  - A Raid's closing line no longer counts favor a Vow took.
- **Faceup only.** The holder holds the Vow faceup as an adviser.
- **Vow of Division:** the Challenge is unavailable to its holder: "Vow of
  Division prevents you from challenging".
- **Vow of Silence.**
  - The holder's banner choice shows the Darkest Secret unavailable:
    "Cannot challenge the Darkest Secret."
  - When it is the only banner they could challenge, the Challenge is
    unavailable: "Vow of Silence prevents you from challenging the Darkest
    Secret".
- **Not replaced:** an adviser arrival follow-up's burns, and a modifier
  menu's dry run of its costs.
- **No old journals:** a recorded burn always carries its burner.
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
  - Every burn of secrets by an enemy counts, cost or effect. The burn is
    replaced by a `Take`: the tokens go to the holder instead of the bank,
    and a facedown secret stays facedown. Secrets an enemy burns from the
    holder's own board stay there.
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

### Task 2: A secret move names its side

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala` (`PositionedLocation`, `Move`, `Take`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationSecretPlanner.scala` (`planSecretSource`)
- Create: `src/main/scala/oathdigital/serialization/PositionWire.scala`
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/SecretBurns.scala`
- Modify: every pattern the compiler flags (Step 6)
- Test: `src/test/scala/oathdigital/gameplay/OperationExecutorSuite.scala`
- Test: `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala`

**Interfaces:**
- Produces:
  - `PositionedLocation(location, position = StackPosition.Unspecified, side: Option[SecretSide] = None)`;
  - `Take(piece, player, from, to, sourcePosition = StackPosition.Unspecified, required = false, leaving = Vector.empty, sourceSide: Option[SecretSide] = None)`.

`Burn` still has no burner in this task, so `SecretBurns` keeps calling `Burn.secrets(amount, from)`.

- [ ] **Step 1: Write the failing tests.** In `OperationExecutorSuite`, after "secret burn consumes only faceup secrets and never facedown", add:

```scala
  test("a secret move naming its side takes only that side, and the " +
      "secrets keep it"):
    val mixed =
      Table.from(ready).secrets(playerId, faceUp = 1, faceDown = 2).unchecked
    val blueBefore = Look(mixed).player(blueId).board
    val taken = accepted(executor.execute(mixed, Take(Piece.Secrets(2),
      blueId, Location.PlayArea(playerId), Location.PlayArea(blueId),
      sourceSide = Some(SecretSide.FaceDown))))
    val red = Look(taken).player(playerId).board
    val blue = Look(taken).player(blueId).board
    assertEquals(red.faceUpSecrets -> red.faceDownSecrets, 1 -> 0)
    assertEquals(blue.faceUpSecrets -> blue.faceDownSecrets,
      blueBefore.faceUpSecrets -> (blueBefore.faceDownSecrets + 2))
    val burnt = accepted(executor.execute(mixed, Burn.secrets(2,
      PositionedLocation(Location.PlayArea(playerId),
        side = Some(SecretSide.FaceDown)))))
    val left = Look(burnt).player(playerId).board
    assertEquals(left.faceUpSecrets -> left.faceDownSecrets, 1 -> 0)
    assertEquals(executor.execute(mixed, Take(Piece.Secrets(2), blueId,
      Location.PlayArea(playerId), Location.PlayArea(blueId),
      sourceSide = Some(SecretSide.FaceUp))).left.toOption,
      Some(OperationError.InsufficientPieces(Piece.Secrets(2),
        Location.PlayArea(playerId), 1)))
```

In `WalkerOperationWireSuite`, after "a Take's leaving operations round trip, and a Take without them writes none", add:

```scala
  test("a secret move's side round trips, and a move without one writes " +
      "none"):
    val red = PlayerId("red")
    val blue = PlayerId("blue")
    val moves = Vector[CoreOperation](
      Move(Piece.Secrets(2), PositionedLocation(Location.PlayArea(red),
        side = Some(SecretSide.FaceDown)),
        PositionedLocation(Location.PlayArea(blue))),
      Take(Piece.Secrets(1), blue, Location.PlayArea(red),
        Location.PlayArea(blue), sourceSide = Some(SecretSide.FaceUp)),
      Move(Piece.Favor(1), PositionedLocation(Location.PlayArea(red)),
        PositionedLocation(Location.PlayArea(blue))))
    val events = moves.map(move => recordedOperation("side", move))
    val encoded = encodedStreamThatRoundTrips("secret-side", events)
    val ops = ujson.read(encoded).arr.map(_("payload")("ops")(0))
    assertEquals(ops(0)("from")("side").str, "face-down")
    assertEquals(ops(1)("sourceSide").str, "face-up")
    assert(!ops(2)("from").obj.contains("side"))
```

- [ ] **Step 2: Run them to see them fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationExecutorSuite oathdigital.serialization.WalkerOperationWireSuite"`
Expected: FAIL to compile: `side` and `sourceSide` are not parameters.

- [ ] **Step 3: Give a position its side.** In `CoreOperations.scala`, replace `PositionedLocation` with:

```scala
/** A location, and where in it a piece is moved from or to. `side`, for
  * secrets on a player's board, is the side a move takes them from; with
  * none, the move takes either side as its destination allows. Moved
  * secrets keep their side (catalog batch 8, slice 5). */
final case class PositionedLocation(
    location: Location,
    position: StackPosition = StackPosition.Unspecified,
    side: Option[SecretSide] = None):
  require(side.isEmpty || location.isInstanceOf[Location.PlayArea],
    "only secrets on a board have a side to move from")
```

In `Move`, add after the existing `require`s:

```scala
  require(from.side.isEmpty || piece.isInstanceOf[Piece.Secrets],
    "only secrets have a side")
  require(to.side.isEmpty, "a move names the side it takes, not lands on")
```

In `Take`, add the last parameter `sourceSide: Option[SecretSide] = None`, and build its move from it:

```scala
  val move: Move = Move(piece, PositionedLocation(from, sourcePosition,
    sourceSide), PositionedLocation(to))
```

Add one sentence to `Take`'s doc comment: "`sourceSide` names the side of the secrets it takes from a board."

- [ ] **Step 4: Move a named side exactly.** In `OperationSecretPlanner.planSecretSource`, replace the `else` branch for a source other than the shared bank (from `else secrets(ready, source).flatMap { inventory =>` to the end of the method) with:

```scala
    else secrets(ready, source).flatMap { inventory =>
      // A move naming a side takes exactly that side. Without one, only
      // faceup secrets can be burned, so a SharedBank (burn) destination is
      // a mandatory-faceup move just like a card or site destination, and a
      // move to a board takes either side.
      val empty = Vector.empty[(Move, Int)]
      val (faceUpOnly, faceDownOnly, flexible) =
        indexed.foldLeft((empty, empty, empty)):
          case ((up, down, free), entry) =>
            (entry._1.from.side, entry._1.to.location) match
              case (Some(SecretSide.FaceUp), _) => (up :+ entry, down, free)
              case (Some(SecretSide.FaceDown), _) => (up, down :+ entry, free)
              case (None, _: Location.PlayArea) => (up, down, free :+ entry)
              case (None, _) => (up :+ entry, down, free)
      val mandatoryFaceUp = secretAmount(faceUpOnly)
      val mandatoryFaceDown = secretAmount(faceDownOnly)
      val flexibleTotal = secretAmount(flexible)
      val total = mandatoryFaceUp + mandatoryFaceDown + flexibleTotal
      val availableFaceUp = inventory.faceUp.toLong
      val availableFaceDown = inventory.faceDown.toLong
      val availableTotal = availableFaceUp + availableFaceDown

      if mandatoryFaceUp > availableFaceUp then
        Left(InsufficientPieces(
          Piece.Secrets(clampPositive(mandatoryFaceUp)),
          source,
          clampNonNegative(availableFaceUp)
        ))
      else if mandatoryFaceDown > availableFaceDown then
        Left(InsufficientPieces(
          Piece.Secrets(clampPositive(mandatoryFaceDown)),
          source,
          clampNonNegative(availableFaceDown)
        ))
      else if total > availableTotal then
        Left(InsufficientPieces(
          Piece.Secrets(clampPositive(total)),
          source,
          clampNonNegative(availableTotal)
        ))
      else
        val remainingFaceUp = availableFaceUp - mandatoryFaceUp
        val remainingFaceDown = availableFaceDown - mandatoryFaceDown
        val minimumFlexibleFaceUp = math.max(
          0L, flexibleTotal - remainingFaceDown)
        val maximumFlexibleFaceUp = math.min(
          flexibleTotal, remainingFaceUp)
        uniqueFlexibleSecretSplits(
          source,
          flexible,
          flexibleTotal,
          minimumFlexibleFaceUp,
          maximumFlexibleFaceUp
        ).map { flexibleSplits =>
          val faceUp = faceUpOnly.map { case (move, index) =>
            val amount = move.piece.asInstanceOf[Piece.Secrets].amount
            (index, move, SecretSplit(amount, 0))
          }
          val faceDown = faceDownOnly.map { case (move, index) =>
            val amount = move.piece.asInstanceOf[Piece.Secrets].amount
            (index, move, SecretSplit(0, amount))
          }
          faceUp ++ faceDown ++ flexibleSplits
        }
    }
```

Add `SecretSide` to the file's `oathdigital.model` import.

- [ ] **Step 5: Record a side.** Create `PositionWire.scala`. Its first two methods are moved out of `WalkerOperationCodec`, and the codec deletes its private copies:

```scala
package oathdigital.serialization

import oathdigital.model.SecretSide

/** Wire spellings for the side of a secret move (catalog batch 8, slice 5),
  * split out of `WalkerOperationCodec` to keep it under the 800-line bound.
  * A side is written only when a move names one. */
private[serialization] trait PositionWire:
  import WireError._

  protected final def encodeSecretSide(value: SecretSide): String =
    value match
      case SecretSide.FaceUp => "face-up"
      case SecretSide.FaceDown => "face-down"

  protected final def decodeSecretSide(value: String,
      path: String): Either[WireError, SecretSide] = value match
    case "face-up" => Right(SecretSide.FaceUp)
    case "face-down" => Right(SecretSide.FaceDown)
    case other => Left(InvalidValue(path, s"unknown secret side '$other'"))

  /** `key` with `side`, when there is one. */
  protected final def sideField(key: String, side: Option[SecretSide])
      : Option[(String, ujson.Value)] =
    side.map(value => key -> ujson.Str(encodeSecretSide(value)))

  /** The side under `key`, `None` when there is none. */
  protected final def decodeSide(value: ujson.Value, key: String,
      path: String): Either[WireError, Option[SecretSide]] =
    value.obj.get(key).fold[Either[WireError, Option[SecretSide]]](
      Right(None))(raw => decodeSecretSide(raw.str, s"$path.$key")
      .map(Some(_)))
```

In `WalkerOperationCodec.scala`:
- Mix the new trait in: `extends CampaignResultCodec with FollowUpWire with OathkeeperWire with PositionWire:`, wrapped to stay under 80 characters.
- Delete the private `encodeSecretSide` and `decodeSecretSide`.
- Replace the position helpers:

```scala
  private def encodePositionedLocation(value: PositionedLocation): ujson.Value =
    ujson.Obj.from(Vector[(String, ujson.Value)](
      "location" -> encodeLocation(value.location),
      "position" -> encodeStackPosition(value.position)) ++
      sideField("side", value.side))

  private def decodePositionedLocation(value: ujson.Value,
      path: String): Either[WireError, PositionedLocation] = for
    location <- decodeLocation(value("location"), s"$path.location")
    position <- decodeStackPosition(value("position").str, s"$path.position")
    side <- decodeSide(value, "side", path)
  yield PositionedLocation(location, position, side)
```

- In the `Take` encode case, bind the new last field and write it:

```scala
      case Take(piece, player, from, to, sourcePosition, _, leaving,
          sourceSide) =>
```

  End its `ujson.Obj.from(...)` with `++ optional ++ sideField("sourceSide", sourceSide))`.

- In the `"take"` decode case, add `sourceSide <- decodeSide(value, "sourceSide", path)` after `leaving`, and pass `sourceSide = sourceSide` to `Take`.

Check: `wc -l` shows the file under 800 lines.

- [ ] **Step 6: Fix every pattern the compiler flags.** Compile with `./sbtw "Test/compile"`.
  - Each pattern `PositionedLocation(a, b)` becomes `PositionedLocation(a, b, _)`.
  - Each pattern `Take(...)` with seven arguments gains an eighth `_`.

  The known sites are:
  - `PricedOptions`, `CampaignLines`, `DetailLines` and `ActionLines` in `src/main`;
  - `LostTongue`;
  - `ChallengeProcedureSuite` and the `Move` patterns in the test suites.

  Change nothing else in them.

- [ ] **Step 7: Burn facedown secrets by name.** Replace `SecretBurns`'s object doc comment and `keeping` with:

```scala
/** A board's secrets burnt down to the number its player keeps, for Book
  * Burning and Ancient Binding. A burn takes faceup secrets unless it names
  * the facedown side, so the facedown ones are burnt by name, first: the
  * secrets kept are faceup when any faceup one remains.
  */
object SecretBurns:
  /** Every secret on `held`'s board but `kept`. A board holding `kept` or
    * fewer loses nothing. */
  def keeping(held: PlayerState, kept: Int): Vector[CoreOperation] =
    val burnt = held.board.faceUpSecrets + held.board.faceDownSecrets - kept
    val faceDown = math.min(held.board.faceDownSecrets, burnt)
    def side(value: SecretSide) = PositionedLocation(
      Location.PlayArea(held.player), side = Some(value))
    if burnt <= 0 then Vector.empty
    else Vector(
      Option.when(faceDown > 0)(Burn.secrets(faceDown,
        side(SecretSide.FaceDown))),
      Option.when(burnt > faceDown)(Burn.secrets(burnt - faceDown,
        side(SecretSide.FaceUp)))).flatten
```

In `BookBurningSuite`, the test that checks "one left stays facedown" collected the step holding the `FlipSecrets`. Collect the step holding a burn of secrets instead, and expect one facedown burn:

```scala
    val burnt = done.events.collect {
      case step: WalkerStepRecorded if step.ops.exists {
          case burn: Burn => burn.resource.isInstanceOf[Piece.Secrets]
          case _ => false } => step.ops }
    assertEquals(burnt, Vector(Vector[CoreOperation](
      Burn.secrets(2, PositionedLocation(Location.PlayArea(b.rival),
        side = Some(SecretSide.FaceDown))))))
```

Keep the test's state assertions as they are. They still hold: 0 faceup and 1 facedown.

- [ ] **Step 8: Run the tests.**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationExecutorSuite oathdigital.serialization.* oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.powers.action.* oathdigital.application.gamelog.*"`
Expected: PASS, including the two new tests.

- [ ] **Step 9: Commit.** Stage every file Steps 3 to 7 changed, by name: `git status --short` lists them. Then:

```bash
git commit -m "feat(batch8): a secret move may name the side it takes"
```

---

### Task 3: Every burn names its burner

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala` (`Burn`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationResolution.scala` (`counted`, the `Burn` case)
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala` (`"burn"`)
- Modify, one call each (Step 5), all under `src/main/scala/oathdigital/`:
  - `gameplay/powers/SecretBurns.scala` and `gameplay/powers/campaign/BookBurning.scala`;
  - `gameplay/powers/action/AncientBinding.scala`, `Firebrand.scala` and `AmberFlame.scala`;
  - `gameplay/powers/economy/Defame.scala`;
  - `gameplay/powers/whenplayed/Revelation.scala`, `ConspiracyWhenPlayed.scala`, `Riots.scala` and `Charlatan.scala`;
  - `gameplay/powers/setup/GreatMarketRules.scala`;
  - `gameplay/powers/campaign/UnstableSummon.scala`;
  - `gameplay/actions/campaign/CampaignRaid.scala`.
- Test: `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala`
- Modify (new argument only): the test suites in Step 6

**Interfaces:**
- Produces:
  - `Burn.by: Option[PlayerId]`;
  - `Burn.favor(amount: Int, from: PositionedLocation, by: Option[PlayerId]): Burn`;
  - `Burn.secrets(amount: Int, from: PositionedLocation, by: Option[PlayerId]): Burn`;
  - `SecretBurns.keeping(held: PlayerState, kept: Int, by: PlayerId)`.

Nothing reads `by` yet, so the game plays exactly as before.

- [ ] **Step 1: Write the failing codec test.** In `WalkerOperationWireSuite`, after the side test, add:

```scala
  test("a burn's burner round trips, and a burn no player makes writes " +
      "null"):
    val red = PlayerId("red")
    val burns = Vector[CoreOperation](
      Burn.favor(2, PositionedLocation(Location.PlayArea(red)), Some(red)),
      Burn.favor(1, PositionedLocation(Location.FavorBank(Suit.Order)),
        None))
    val events = burns.map(burn => recordedOperation("burn", burn))
    val encoded = encodedStreamThatRoundTrips("burn-by", events)
    val ops = ujson.read(encoded).arr.map(_("payload")("ops")(0))
    assertEquals(ops(0)("by").str, "red")
    assertEquals(ops(1)("by"), ujson.Null)
```

- [ ] **Step 2: Run it to see it fail.**

Run: `./sbtw "testOnly oathdigital.serialization.WalkerOperationWireSuite"`
Expected: FAIL to compile: `Burn.favor` takes two arguments.

- [ ] **Step 3: Give `Burn` its burner.** In `CoreOperations.scala`, replace the `Burn` doc comment, trait and object with:

```scala
/** Moves favor or secrets to the shared bank. `by` is the player who burns
  * (catalog batch 8, "A burn's actor"): the actor of the procedure that
  * burns, each owner when an effect makes each player burn, the defeated
  * defender for a Raid's half-favor burn, and `None` when no player burns,
  * as in a setup rule. A cost's burnt portions are its payer's, so
  * `PayCost` holds no `Burn`.
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

- [ ] **Step 4: Record the burner.** In `WalkerOperationCodec.scala`, the `Burn` encode case always writes `"by"`:

```scala
      case burn: Burn => ujson.Obj("kind" -> "burn",
        "resource" -> encodePiece(burn.resource),
        "from" -> encodePositionedLocation(burn.from),
        "by" -> burn.by.fold[ujson.Value](ujson.Null)(by => ujson.Str(by.value)))
```

The `"burn"` decode case reads it. Old journals are not supported, so its absence is an error:

```scala
    case "burn" => for
      resource <- decodePiece(value("resource"), s"$path.resource")
      from <- decodePositionedLocation(value("from"), s"$path.from")
      by = value("by").strOpt.map(PlayerId(_))
      burn <- resource match
        case Piece.Favor(amount) => Right(Burn.favor(amount, from, by))
        case Piece.Secrets(amount) => Right(Burn.secrets(amount, from, by))
        case other => Left(InvalidValue(s"$path.resource",
          s"unsupported burn resource $other"))
    yield burn
```

Check: `wc -l` shows the file under 800 lines.

- [ ] **Step 5: Name the burner at every builder.** Make each change below. Where a helper gains a parameter, change its callers in the same file. Only `AncientBinding` gains a new helper.

`SecretBurns.keeping` takes the burner and passes `Some(by)` to both of its burns:

```scala
  /** Every secret on `held`'s board but `kept`, burnt by `by`. A board
    * holding `kept` or fewer loses nothing. */
  def keeping(held: PlayerState, kept: Int, by: PlayerId)
      : Vector[CoreOperation] =
```

```scala
      Option.when(faceDown > 0)(Burn.secrets(faceDown,
        side(SecretSide.FaceDown), Some(by))),
      Option.when(burnt > faceDown)(Burn.secrets(burnt - faceDown,
        side(SecretSide.FaceUp), Some(by)))).flatten
```

`BookBurning`: the attacker burns. Its private `burn` gains `attacker: PlayerId` and calls `SecretBurns.keeping(_, 1, attacker)`. The `later` hook passes `use.actor`, the Campaign's attacker:

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

`Defame`: the trader burns. `offer` passes `actor` to `burn`, and the burn names it:

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

`Riots`: the card's player burns. `effect` passes its `actor` to `burn`:

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

`CampaignRaid`: the attacker burns the Darkest Secret's secrets as they take it, and the defeated defender burns half their own favor. The first edit is in `takes` and the second in `consequences`. If slice 4 has not merged, both are in `transfer`.

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

  - `src/test/scala/oathdigital/serialization/WalkerOperationWireSuite.scala`: the burns near lines 276 and 396.
  - `src/test/scala/oathdigital/gameplay/OperationExecutorSuite.scala`: every burn, including the one Task 2 added.
  - `src/test/scala/oathdigital/gameplay/CoreOperationsSuite.scala`: 2 calls.
  - `src/test/scala/oathdigital/gameplay/OperationResolutionSuite.scala`: both burns in `Burn.favor(3, toArea) -> Burn.favor(1, toArea)`.
  - `src/test/scala/oathdigital/gameplay/powers/campaign/UnstableSummonSuite.scala`: only `.move` is compared, so `None` is fine.
  - `src/test/scala/oathdigital/application/gamelog/GameLogRareLinesSuite.scala`: 1 call.
  - `src/test/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayedSuite.scala`: the expected `leaving` burn is p1's, `Some(p1)`.
  - `src/test/scala/oathdigital/gameplay/powers/campaign/BookBurningSuite.scala`: the expected facedown burn from Task 2 is the attacker's, `Some(b.actor)`.

- [ ] **Step 7: Run the affected suites.**

Run: `./sbtw "testOnly oathdigital.serialization.* oathdigital.gameplay.OperationExecutorSuite oathdigital.gameplay.CoreOperationsSuite oathdigital.gameplay.OperationResolutionSuite oathdigital.gameplay.powers.* oathdigital.gameplay.CampaignRaidSuite oathdigital.application.gamelog.*"`
Expected: PASS, including the new codec test.

- [ ] **Step 8: Commit.** Stage every file Steps 1 to 6 changed, by name: `git status --short` lists them. Then:

```bash
git commit -m "feat(batch8): every burn names its burner"
```

---

### Task 4: Burns taken

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powerresolver/BurnTake.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala` (after `onArrival`)
- Create: `src/main/scala/oathdigital/gameplay/walker/BurnTakes.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSteps.scala` (`recordBatch`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (`walkComposite`)
- Test: `src/test/scala/oathdigital/gameplay/walker/BurnTakesSuite.scala`

**Interfaces:**
- Consumes: `Burn.by` (Task 3), `PositionedLocation.side` and `Take.sourceSide` (Task 2).
- Produces:
  - `Burnt(resource, from, by)`;
  - `BurnTake(taker, line)`, where `line` takes the burner, the count taken and the active player;
  - `ContributingPower.takesBurn(ready: ReadyGame, burnt: Burnt): Option[BurnTake]`, default `None`.

- [ ] **Step 1: Write the failing tests.** Create `BurnTakesSuite.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{BurnTake, Burnt,
  ContributingPower, Contribution}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class BurnTakesSuite extends munit.FunSuite:
  /** A test power: claims for p1 every burn another player makes. Its line
    * names the burner and the count. */
  private object TakesOthersBurnsForP1 extends ContributingPower:
    val id: PowerId = PowerId("test.takes-others-burns")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def takesBurn(ready: ReadyGame, burnt: Burnt)
        : Option[BurnTake] =
      Option.when(burnt.by != p1)(BurnTake(p1, (burner, amount, _) =>
        line(burner, amount)))

  private def line(burner: PlayerId, amount: Int): PowerNote =
    PowerNote(PowerSourceRef.Site(SiteId("test")), "took",
      Vector(NoteArg.Player(burner), NoteArg.Number(amount)))

  private val powers = WalkerPowers(Vector(TakesOthersBurnsForP1))
  /** p1 with no favor; p2 with 3 favor, 1 faceup and 2 facedown secrets. */
  private val start: ReadyGame = Table.start.favor(p1, 0).favor(p2, 3)
    .secrets(p2, faceUp = 1, faceDown = 2).ready
  private val p2Board = PositionedLocation(Location.PlayArea(p2))

  /** The end state and the events of walking `tree` from `start`. */
  private def walked(tree: Operation)(using munit.Location)
      : (ReadyGame, Vector[OathEvent]) =
    accepted(ProcedureWalker.advance(start, tree, None, powers)) match
      case WalkerOutcome.Finished(end, events) => (end, events)
      case other => fail(s"the walk did not finish: $other")

  /** Each recorded step's operations. */
  private def steps(events: Vector[OathEvent]): Vector[Vector[CoreOperation]] =
    events.collect { case step: WalkerStepRecorded => step.ops }

  /** The test power's lines. */
  private def lines(events: Vector[OathEvent]): Vector[PowerNote] =
    events.collect { case PowerNoted(TakesOthersBurnsForP1.id, note, _) =>
      note }

  test("a batch's burn another player makes becomes a Take to the taker, " +
      "cut to what is there, with the taker's line"):
    val (end, events) = walked(BuildOps((_, _) => Right(Vector[CoreOperation](
      Burn.favor(5, p2Board, Some(p2))))))
    assertEquals(Look(end).favor(p2), 0)
    assertEquals(Look(end).favor(p1), 3)
    assertEquals(steps(events), Vector(Vector[CoreOperation](Take(
      Piece.Favor(3), p1, Location.PlayArea(p2), Location.PlayArea(p1)))))
    assertEquals(lines(events), Vector(line(p2, 3)))

  test("a cost or a burn in the tree runs whole: the cost's take is " +
      "required, and facedown secrets stay facedown"):
    val (paid, paidEvents) = walked(Sequence(Vector[Operation](
      PayCost(p2, Location.SharedBank, Cost(favorBurnt = 2)))))
    assertEquals(Look(paid).favor(p1), 2)
    assertEquals(steps(paidEvents), Vector(Vector[CoreOperation](
      PayCost(p2, Location.SharedBank, Cost.free),
      Take(Piece.Favor(2), p1, Location.PlayArea(p2), Location.PlayArea(p1),
        required = true))))
    val faceDownBefore = Look(start).faceDownSecrets(p1)
    val (taken, takenEvents) = walked(Sequence(Vector[Operation](
      Burn.secrets(2, PositionedLocation(Location.PlayArea(p2),
        side = Some(SecretSide.FaceDown)), Some(p2)))))
    assertEquals(Look(taken).faceDownSecrets(p1), faceDownBefore + 2)
    assertEquals(Look(taken).faceDownSecrets(p2), 0)
    assertEquals(Look(taken).faceUpSecrets(p2), 1)
    assertEquals(lines(takenEvents), Vector(line(p2, 2)))

  test("a burn of the taker's own board leaves it there, and a player's " +
      "own burn goes to the bank"):
    val keep = Table.from(start).favor(p1, 2).unchecked
    val (kept, keptEvents) = accepted(ProcedureWalker.advance(keep,
      BuildOps((_, _) => Right(Vector[CoreOperation](Burn.favor(2,
        PositionedLocation(Location.PlayArea(p1)), Some(p2))))), None,
      powers)) match
      case WalkerOutcome.Finished(end, events) => (end, events)
      case other => fail(s"the walk did not finish: $other")
    assertEquals(Look(kept).favor(p1), 2)
    assertEquals(steps(keptEvents), Vector.empty)
    assertEquals(lines(keptEvents), Vector(line(p2, 2)))
    val (own, ownEvents) = walked(BuildOps((_, _) => Right(
      Vector[CoreOperation](Burn.favor(2, p2Board, Some(p1))))))
    assertEquals(Look(own).favor(p2), 1)
    assertEquals(Look(own).favor(p1), 0)
    assertEquals(lines(ownEvents), Vector.empty)
```

- [ ] **Step 2: Run them to see them fail.**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.BurnTakesSuite"`
Expected: FAIL to compile: `BurnTake`, `Burnt` and `takesBurn` are not found.

- [ ] **Step 3: Add the claim.** Create `BurnTake.scala`:

```scala
package oathdigital.gameplay.powerresolver

import oathdigital.model.{Piece, PlayerId, PositionedLocation, PowerNote}

/** Favor or secrets a player burns: how many, where from, and who burns
  * them (catalog batch 8, "A burn's actor"). A cost's burnt portion is its
  * payer's, from their board. */
final case class Burnt(resource: Piece.Counted, from: PositionedLocation,
    by: PlayerId)

/** A power's claim on a burn: `taker` takes the tokens instead of the bank.
  * `line` words what was taken, given the burner, the count taken and the
  * active player. */
final case class BurnTake(taker: PlayerId,
    line: (PlayerId, Int, PlayerId) => PowerNote)
```

In `ContributingPower`, after `onArrival`, add:

```scala
  /** Who takes `burnt` instead of the bank, when this power claims it
    * (catalog batch 8, "A burn's actor"). None by default. */
  def takesBurn(ready: ReadyGame, burnt: Burnt): Option[BurnTake] = None
```

- [ ] **Step 4: Replace the claimed burns.** Create `BurnTakes.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{BurnTake, Burnt,
  ContributingPower}
import oathdigital.model.{Burn, CoreOperation, Location, PayCost, Piece,
  PlayerId, PositionedLocation, PowerId, ReadyGame, SecretSide, Take}

/** The burns that powers take (catalog batch 8, "A burn's actor"). Before a
  * batch runs, each burn a power claims is replaced by a `Take` of the same
  * tokens to its taker, so a restriction on a `Take` holds against it and
  * the count is cut as a burn's would be. A secret keeps its side: a burn
  * from a board takes faceup secrets unless it names the facedown side. A
  * burn of the taker's own board leaves the tokens where they are. A cost's
  * burnt portions leave its `PayCost` as `Take`s that must be paid in full.
  * A burn inside a `Take`'s leaving is replaced there. The first power to
  * claim a burn, in the order contributions chain, takes it. The pass names
  * no card.
  */
private[walker] object BurnTakes:
  /** A burn `power` claimed. */
  final case class Taken(power: PowerId, burnt: Burnt, take: BurnTake)

  /** A batch with its claimed burns replaced, and what was claimed. */
  final case class Swapped(operations: Vector[CoreOperation],
      taken: Vector[Taken]):
    /** One line per power, taker and burner, for what `executed` took. A
      * burn of the taker's own board counts in full: nothing moved. */
    def lines(executed: Vector[CoreOperation], actor: PlayerId)
        : Vector[PowerNoted] =
      val moved = takesIn(executed)
      taken.map(group).distinct.flatMap { key =>
        val claims = taken.filter(group(_) == key)
        val amount = claims.map(kept).sum + claims.filterNot(own)
          .map(source).distinct.map(took(moved, key._2, _)).sum
        Option.when(amount > 0)(PowerNoted(key._1,
          claims.head.take.line(key._3, amount, actor), covers = false))
      }

  /** `operations` with every burn a power claims replaced. */
  def replace(ready: ReadyGame, powers: Vector[ContributingPower],
      operations: Vector[CoreOperation]): Swapped =
    swapAll(ready, powers.sortBy(ContributingPower.sortKey), operations)

  /** Whether a power claims a burn of `operation`. */
  def takes(ready: ReadyGame, powers: Vector[ContributingPower],
      operation: CoreOperation): Boolean =
    replace(ready, powers, Vector(operation)).taken.nonEmpty

  private def swapAll(ready: ReadyGame, powers: Vector[ContributingPower],
      operations: Vector[CoreOperation]): Swapped =
    operations.foldLeft(Swapped(Vector.empty, Vector.empty)) {
      (done, operation) =>
        val next = swap(ready, powers, operation)
        Swapped(done.operations ++ next.operations, done.taken ++ next.taken)
    }

  private def swap(ready: ReadyGame, powers: Vector[ContributingPower],
      operation: CoreOperation): Swapped = operation match
    case burn: Burn =>
      burn.by.flatMap(by =>
        claim(ready, powers, Burnt(burn.resource, burn.from, by))) match
        case Some(taken) => Swapped(replacement(taken, required = false),
          Vector(taken))
        case None => Swapped(Vector(burn), Vector.empty)
    case pay: PayCost =>
      val board = PositionedLocation(Location.PlayArea(pay.player))
      def claimed(amount: Int, piece: Int => Piece.Counted) =
        Option.when(amount > 0)(Burnt(piece(amount), board, pay.player))
          .flatMap(claim(ready, powers, _))
      val favor = claimed(pay.cost.favorBurnt, Piece.Favor(_))
      val secrets = claimed(pay.cost.secretBurnt, Piece.Secrets(_))
      if favor.isEmpty && secrets.isEmpty then
        Swapped(Vector(pay), Vector.empty)
      else
        val cost = pay.cost.copy(
          favorBurnt = if favor.isDefined then 0 else pay.cost.favorBurnt,
          secretBurnt = if secrets.isDefined then 0 else pay.cost.secretBurnt)
        val taken = favor.toVector ++ secrets.toVector
        Swapped(pay.copy(cost = cost) +:
          taken.flatMap(replacement(_, required = true)), taken)
    case take: Take if take.leaving.nonEmpty =>
      val leaving = swapAll(ready, powers, take.leaving)
      Swapped(Vector(take.copy(leaving = leaving.operations)), leaving.taken)
    case other => Swapped(Vector(other), Vector.empty)

  /** The first power's claim on `burnt`. */
  private def claim(ready: ReadyGame, powers: Vector[ContributingPower],
      burnt: Burnt): Option[Taken] =
    powers.iterator.flatMap(power =>
      power.takesBurn(ready, burnt).map(Taken(power.id, burnt, _)))
      .nextOption()

  /** The `Take` in place of a claimed burn, or nothing when the tokens are
    * on the taker's own board. */
  private def replacement(taken: Taken, required: Boolean)
      : Vector[CoreOperation] =
    if own(taken) then Vector.empty
    else
      val from = taken.burnt.from
      Vector(Take(taken.burnt.resource, taken.take.taker, from.location,
        Location.PlayArea(taken.take.taker), from.position,
        required = required, sourceSide = side(taken.burnt)))

  /** The side a burn of secrets from a board takes: the one it names, or
    * faceup. */
  private def side(burnt: Burnt): Option[SecretSide] = burnt match
    case Burnt(_: Piece.Secrets, PositionedLocation(_: Location.PlayArea,
        _, named), _) => named.orElse(Some(SecretSide.FaceUp))
    case _ => None

  private def own(taken: Taken): Boolean =
    taken.burnt.from.location == Location.PlayArea(taken.take.taker)

  /** What a claim on the taker's own board counts. */
  private def kept(taken: Taken): Int =
    if own(taken) then taken.burnt.resource.amount else 0

  private def group(taken: Taken): (PowerId, PlayerId, PlayerId) =
    (taken.power, taken.take.taker, taken.burnt.by)

  /** Where a claim's take comes from: location, side and kind. */
  private def source(taken: Taken): (Location, Option[SecretSide], Boolean) =
    (taken.burnt.from.location, side(taken.burnt),
      taken.burnt.resource.isInstanceOf[Piece.Secrets])

  /** Every `Take` in `operations`, inside a `Take`'s leaving too. */
  private def takesIn(operations: Vector[CoreOperation]): Vector[Take] =
    operations.flatMap {
      case take: Take => take +: takesIn(take.leaving)
      case _ => Vector.empty
    }

  /** The count the takes in `moved` took for `taker` from `from`. */
  private def took(moved: Vector[Take], taker: PlayerId,
      from: (Location, Option[SecretSide], Boolean)): Int =
    moved.collect {
      case Take(piece: Piece.Counted, `taker`, location, _, _, _, _, named)
          if location == from._1 && named == from._2 &&
            piece.isInstanceOf[Piece.Secrets] == from._3 => piece.amount
    }.sum
```

- [ ] **Step 5: Replace before a batch runs.** In `WalkerSteps.recordBatch`, replace the claimed burns before the pipeline, run nothing when nothing is left, and journal the lines right after the step:

```scala
    val swapped = BurnTakes.replace(ctx.state, ctx.powers.powers, ops)
    val ran =
      if swapped.operations.isEmpty then
        Right(OperationRun(ctx.state, Vector.empty, Vector.empty))
      else OperationPipeline.run(ctx.state, swapped.operations,
        OperationPolicy.Permissive, ctx.powers.operationRestrictions,
        requireAll)(Right(_))
    ran.flatMap { updated =>
      val nodeId = if path.isEmpty then label else path.mkString(".")
      val step = Option.when(updated.executed.nonEmpty)(WalkerStepRecorded(
        nodeId = nodeId,
        payload = WalkerStepPayload.DeltaRecorded(
          DeltaMeaning.of(updated.executed, label)),
        ops = updated.executed,
        contributions = contributions))
      val events = ctx.events ++ step ++
        swapped.lines(updated.executed, ctx.activePlayer)
      AdviserArrivalPass.after(ctx.state, ctx.copy(state = updated.state,
        events = events, previous = Some((ctx.state, updated.state))),
        nodeId)
    }
```

Import `OperationRun` from `oathdigital.gameplay.operations`. Extend `recordBatch`'s doc comment: "Each burn a power claims is first replaced by a `Take` ([[BurnTakes]]), and the claims' lines follow the step."

- [ ] **Step 6: Run a claimed burn or cost in the tree whole.** In `ProcedureWalker.walkComposite`, add a case before the existing body, which moves into the `case _` arm unchanged:

```scala
    else composite match
      // A burn or a cost a power claims runs whole as one batch, where
      // `BurnTakes` replaces it: split, its moves would lose their burner.
      case core @ (_: Burn | _: PayCost) if cursor.isEmpty &&
          BurnTakes.takes(ctx.state, ctx.powers.powers, core) =>
        WalkerSteps.runBuildOps(BuildOps((_, _) =>
          Right(Vector[CoreOperation](core)),
          required = core.required || hooks.strict), ctx, path,
          hooks.inherited)
      case _ =>
        // the existing body: `required`, `runs` and `walkFolded`
```

Add `Burn` and `PayCost` to `ProcedureWalker`'s `oathdigital.model` import.

- [ ] **Step 7: Run the tests.**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.* oathdigital.gameplay.CampaignRaidSuite oathdigital.gameplay.powers.action.* oathdigital.gameplay.powers.campaign.*"`
Expected: PASS. No production power claims a burn yet, so every existing suite reads as before.

- [ ] **Step 8: Commit.**

```bash
git add src/main/scala/oathdigital/gameplay/powerresolver/BurnTake.scala src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala src/main/scala/oathdigital/gameplay/walker/BurnTakes.scala src/main/scala/oathdigital/gameplay/walker/WalkerSteps.scala src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/test/scala/oathdigital/gameplay/walker/BurnTakesSuite.scala
git commit -m "feat(batch8): a burn a power claims becomes a Take to its taker"
```

---

### Task 5: Vow of Silence and Vow of Division

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
- Consumes: `Burnt`, `BurnTake` and `ContributingPower.takesBurn` (Task 4).
- Produces: `VowOfSilence`, `VowOfDivision`, `BurnVow.holderOf(ready): Option[PlayerId]`, each Vow's `taken` and `takenBy`, and `VowOfSilence.forbidden`.

- [ ] **Step 1: Move the cards.** Cut `VowOfSilenceCard` out of `HoldingArcaneDenizens.scala` and `VowOfDivisionCard` out of `HoldingDiscordDenizens.scala`.
  - Vow of Silence was the only `Locked` card in `HoldingArcaneDenizens.scala`, so drop `Locked` from that file's `oathdigital.catalog` import.
  - `HoldingDiscordDenizens.scala` still uses `Locked` and `AdviserOnly`, so its imports stay.

In `NewFoundations.scala`, add `import oathdigital.gameplay.powers.burn.{VowOfDivisionCard, VowOfSilenceCard}`. The `holding._` wildcard no longer supplies them.

- [ ] **Step 2: Write the failing tests.** Create `VowOfSilenceSuite.scala`:

```scala
package oathdigital.gameplay.powers.burn

import oathdigital.gameplay.CampaignFixture.{on, raidBoard, rules,
  withAdviser, withAdviserFor}
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
  private val bookBurning = CatalogNames.denizen("Book Burning")
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
      s"${p2.value} took ${p1.value}'s 3 burned secrets.", covers = false)))
    assertEquals(ActPowerHarness.replayed(game, ready0, done.events), end)
    assertWireRoundTrips(done.events)

  test("the holder's own burnt cost goes to the bank"):
    val ready0 = demonTailWithVowHeldBy(p1)
    val done = accepted(ActPowerHarness.use(game, ready0, DemonTail.id,
      DecisionOptionRef.Relic(tail)))
    assertEquals(Look(Outcomes.ready(done.state)).faceUpSecrets(p1), 0)
    assertEquals(said(done.events), Vector.empty)

  test("a defender holding it takes the Darkest Secret's secrets the " +
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
      s"${b.rival.value} took ${b.actor.value}'s 2 burned secrets.",
      covers = false)))

  test("a bystander holding it takes the secrets Book Burning burns, and " +
      "the facedown ones stay facedown"):
    val raid = raidBoard()
    val staged = on(raid.board)(_.secrets(raid.board.rival, faceUp = 1,
      faceDown = 2))
    val b = withAdviserFor(withAdviser(staged, bookBurning,
      Orientation.FaceUp), staged.bystander, vow, Orientation.FaceUp)
    val faceDownBefore = Look(b.ready).faceDownSecrets(b.bystander)
    val run = commit(rules(winningDice), b, 4,
      targets = Vector(DecisionOptionRef.Relic(raid.relic)), raid = true)
      .pick(b.actor, CampaignIds.attackerPlan,
        DecisionOptionRef.Denizen(bookBurning))
      .finishPlanWindows
    assertEquals(run.winner, Some(true))
    val elsewhere = Outcomes.ready(run.state).game.current.map.inPlay
      .find(_ != b.origin).get
    val done = run.pick(b.actor, CampaignIds.relocation,
      DecisionOptionRef.Site(elsewhere))
    // Book Burning keeps one secret: the 2 facedown ones burn.
    assertEquals(Look(done.state).faceUpSecrets(b.rival), 1)
    assertEquals(Look(done.state).faceDownSecrets(b.rival), 0)
    assertEquals(Look(done.state).faceDownSecrets(b.bystander),
      faceDownBefore + 2)
    assertEquals(said(done.events), Vector(NoteText.Said("taken-by",
      s"${b.bystander.value} took ${b.actor.value}'s 2 burned secrets.",
      covers = false)))

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
      s"Took ${b.rival.value}'s 2 burned favor.", covers = false)))
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
  * It claims every burn of its resource by a player other than the holder,
  * and the walker replaces the burn with a `Take` to the holder
  * (`BurnTakes`). Every other player is an enemy, and the holder's own
  * burns go to the bank. Its Challenge half is each Vow's own.
  */
sealed abstract class BurnVow extends ContributingPower:
  def cardId: DenizenId
  /** "Took {Red}'s 2 burned secrets." */
  def taken: NoteKey
  /** "{Blue} took {Red}'s 2 burned secrets." */
  def takenBy: NoteKey
  /** Whether `resource` is the one it takes. */
  protected def takes(resource: Piece.Counted): Boolean

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  /** The player holding the card faceup, if any. */
  final def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

  final override def takesBurn(ready: ReadyGame, burnt: Burnt)
      : Option[BurnTake] =
    holderOf(ready).filter(holder =>
      holder != burnt.by && takes(burnt.resource)).map(holder =>
      BurnTake(holder, line(holder)))

  /** What `holder` took from `burner`, naming the holder unless it is
    * their turn. */
  private def line(holder: PlayerId)(burner: PlayerId, amount: Int,
      actor: PlayerId): PowerNote =
    val card = PowerSourceRef.Card(cardId)
    if holder == actor then
      taken(card, NoteArg.Player(burner), NoteArg.Number(amount))
    else takenBy(card, NoteArg.Player(holder), NoteArg.Player(burner),
      NoteArg.Number(amount))

object BurnVow:
  /** "Took {Red}'s {2} burned {secrets}." */
  def taken(one: String, many: String): NoteKey = NoteKey("taken", Vector(
    NotePart.Text("Took "), NotePart.Arg(0), NotePart.Text("'s "),
    NotePart.Arg(1), NotePart.Text(" burned "),
    NotePart.Plural(1, one, many), NotePart.Text(".")))

  /** "{Blue} took {Red}'s {2} burned {secrets}." */
  def takenBy(one: String, many: String): NoteKey = NoteKey("taken-by",
    Vector(NotePart.Arg(0), NotePart.Text(" took "), NotePart.Arg(1),
      NotePart.Text("'s "), NotePart.Arg(2), NotePart.Text(" burned "),
      NotePart.Plural(2, one, many), NotePart.Text(".")))

/** Vow of Silence (74): "You cannot challenge the Darkest Secret. Whenever
  * an enemy burns [secret-burnt], you take those secrets."
  *
  * Its holder's banner choice keeps the Darkest Secret, unavailable, with
  * the reason; when it is the only banner they could challenge, the
  * Challenge cannot start. */
case object VowOfSilence extends BurnVow:
  val cardId: DenizenId = VowOfSilenceCard.id
  val id: PowerId = VowOfSilenceCard.power.id
  val taken: NoteKey = BurnVow.taken("secret", "secrets")
  val takenBy: NoteKey = BurnVow.takenBy("secret", "secrets")
  protected def takes(resource: Piece.Counted): Boolean =
    resource.isInstanceOf[Piece.Secrets]

  /** "Cannot challenge the {Darkest Secret}.": the banner's hover
    * reason. */
  val forbidden: NoteKey = NoteKey("forbidden", Vector(
    NotePart.Text("Cannot challenge the "), NotePart.Arg(0),
    NotePart.Text(".")))

  override def noteKeys: Vector[NoteKey] = Vector(taken, takenBy, forbidden)

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
  val taken: NoteKey = BurnVow.taken("favor", "favor")
  val takenBy: NoteKey = BurnVow.takenBy("favor", "favor")
  protected def takes(resource: Piece.Counted): Boolean =
    resource.isInstanceOf[Piece.Favor]

  override def noteKeys: Vector[NoteKey] = Vector(taken, takenBy)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ChallengeActionEligibility -> Vector(Restriction((ctx, _) =>
      Option.when(holderOf(ctx.state).contains(ctx.activePlayer))(
        OathViolation.StartUnavailable(
          "Vow of Division prevents you from challenging")))))
```

The card objects keep their long first lines, as in the holding files they came from.

- [ ] **Step 5: Register the Vows.** In `WalkerPowerCatalog.default`:
  - Add `Vector(VowOfSilence, VowOfDivision) ++` right after `Vector(VowOfPeaceContribution) ++`.
  - Import them with `import oathdigital.gameplay.powers.burn.{VowOfDivision, VowOfSilence}`.
  - Add this to the doc comment, after the Vow of Peace sentence: "Vow of Silence and Vow of Division claim enemies' burns whenever the walker runs a batch, and are inert at the Challenge until a Challenge walks its windows."

- [ ] **Step 6: Run the tests.**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.burn.* oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.powers.PowerNoteCatalogSuite oathdigital.gameplay.powers.PowerImplementationStatusSuite oathdigital.gameplay.powers.targeting.*"`
Expected: PASS.

Stop and report, without changing the tests, in either of these cases:
- a menu test sees a reason other than the Vow's. That would mean a Challenge's start refusal does not reach the menu's hover reason, which this plan assumed.
- the Darkest Secret is missing from the banner options, rather than unavailable.

- [ ] **Step 7: Commit.**

```bash
git add src/main/scala/oathdigital/gameplay/powers/burn/BurnVows.scala src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/test/scala/oathdigital/gameplay/powers/burn/VowOfSilenceSuite.scala src/test/scala/oathdigital/gameplay/powers/burn/VowOfDivisionSuite.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(batch8): Vow of Silence and Vow of Division take enemies' burns"
```

---

### Task 6: Gates and docs

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` (Status line)
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Run the gates.**

Run, in order:

1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass.

- The server count is your baseline plus 15.
- The frontend count is unchanged.

- [ ] **Step 2: Check the client.**

Run: `grep -rn '"burn"\|"side"\|sourceSide\|Vow of Silence\|Vow of Division' frontend/src/main`
Expected: nothing reads a recorded operation's fields. The client shows the Vows' lines and hover reasons through the generic note and option rendering. Report anything else you find instead of changing it.

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
