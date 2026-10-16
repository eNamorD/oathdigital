# Catalog Batch 8, Slice 10, Part 2 (Placement Bans, Automatic Powers, Forest Council and City Wall) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement 194 Forest Council and 252 City Wall.

- Forest Council: "Enemies of Forest Council's ruler **cannot** place [favor] / [secret] on [suit-beast] cards or use their non-persistent powers."
- City Wall: "While at this site, enemies of City Wall's ruler **cannot** place [favor] / [secret] on cards or use non-persistent powers."

**Architecture:**

- **Part 1 is a prerequisite.** This plan builds on these pieces from it:
  - `PowerUse`, with its `places` flag;
  - `PowerUseRestriction`, with its `waiver`;
  - `PowerUseRules`;
  - `RulerBans`;
  - the phase menu, modifier picker, plan and reroll paths.
- **Each card is one walker power with two restrictions:**
  - a `PowerUseRestriction`, which refuses uses;
  - an operation restriction, which refuses placements.
- **The use restriction refuses two kinds of use:**
  - **A non-persistent use.**
  - **A use whose cost places on its card** (`PowerUse.places`), on the user's own turn. This catches a persistent power with a placed cost, such as Jinx.
- **The operation restriction refuses two operations:**
  - a `PayCost` onto a card, on the payer's own turn;
  - a `Give` of favor or secrets onto a card.
- **One grant covers both restrictions.** The placement refusal's waiver covers `ConsentKey(power, placer, "power.use", card)`. That is the key a power-use grant is remembered under. So asking to use a card covers using its power and placing its cost on it, for the turn.
- **Two engine changes make placements askable:**
  - **Wrapping.** `OperationRestrictions.active` now wraps the walker powers' operation restrictions in `ConsentedRestriction`, as it already wraps the printed ones. A remembered grant then lifts them on every step.
  - **The search keeps the waiver.** A refused operation now rejects with `OathViolation.WaivableOperationRejected`, which carries its waiver.
  - The walker uses this when its search hides an option of a choose-one. If only waivable operation refusals stop that option's path, and granting them opens it, the walker shows the option askable (`walker/OperationAsks.scala`). This covers a Muster or Trade source card. It also covers the Hall of Ministers on any choose-one.
- **Refused automatic powers do not apply.** The rulings are in the [automatic power consent design](../specs/2026-10-15-automatic-power-consent-design.md), rulings 1 and 3.
  - An automatic power is judged where its effect runs, not where its window folds. Each wrapped power appends its effect inside a new `AutomaticUse(user, power, card, effect)` node. The walker resolves the node when it reaches it: the effect when `PowerUseRules` finds no refusal, nothing otherwise.
  - Wrapped: Tyrant, Hospitality, every When Played power through `WhenPlayedPower`, Conspiracy's When Played, and Vow of Beastkin's and Vow of Wandering's extra warbands, which become their own gain after the Muster's.
  - Keep replaces a Raid's consequences, so it cannot be wrapped. It states its use, and `ContributionCollector.gather` takes a `refused` test that judges it where its window folds. A refused power keeps only its `Offer` and `OfferReroll` contributions.
  - Never refused, since they are not uses: Initiation Rite (a cost change), the edifice setup rules (Setup), Vow of Beastkin's source limit (a restriction) and the lasting limits.
  - Hunger's forced Wake keeps part 1's check. Roadmap item 10b wraps it.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`, sections:
  - "The bar" (the slice 10 bullets);
  - "Slice 10: Power-use restrictions" and its "Plan-time rulings";
  - "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: the slice 10 entries.
- `docs/superpowers/specs/2026-10-08-consent-system-design.md`, "Rulings" 1 to 8 and slice 3 (the operation path).
- `docs/superpowers/plans/2026-10-14-catalog-batch-8-slice10-part1.md`, for the names this plan consumes.

**Starts from:** `main` with slice 10 part 1 merged. Check that `src/main/scala/oathdigital/gameplay/powers/poweruse/SpellBreaker.scala` exists and that `PowerUse` has a `places` field. Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it. Each new file below lists the imports it needs. A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). Check with `wc -l`. `ProcedureWalker.scala` is about 745 lines, so its new helper is one short method that delegates to `OperationAsks`.
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - The engine (`gameplay/walker`, `gameplay/operations`) never names a power. `BackendArchitectureSuite` scans the engine's sources for every power's name, lowercased. So no file there may contain "forestcouncil", "citywall" or any other power name.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **Tree addresses.** No procedure tree gains, loses or moves a node, except in Task 3: it appends one `AutomaticUse` node holding each wrapped power's effect, so positions inside that effect move one level down.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
  - Run `ExitWorktree` with `keep` before merging into `main`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Test helper names** say what they build. A doc comment does not excuse a vague name.
- **Log lines.** Neither card writes a line. A refused power is not used, and a refused placement does not happen.
- **Note keys** are declared through a `NoteSet` (`model/PowerNotes.scala`), as plain `val`s after the set, never `lazy`. `src/test/resources/notes/declared-keys.txt` snapshots every declared key. Regenerate it with `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"` and check the diff adds only the keys named in the task.
- **Hover reasons, exactly.** The projector prefixes a power note with its card's name and a colon.
  - "Forest Council: Cannot use non-persistent powers of Beast cards."
  - "Forest Council: Cannot place favor or secrets on Beast cards."
  - "City Wall: Cannot use non-persistent powers while at its site."
  - "City Wall: Cannot place favor or secrets on cards while at its site."
  - An askable reason ends " without ruler's consent." instead of ".".
- **Names, exactly:**
  - `OathViolation.WaivableOperationRejected(code: String, detail: String, waiver: OperationWaiver)`.
  - File `src/main/scala/oathdigital/gameplay/walker/OperationAsks.scala`: `private[walker] object OperationAsks` with `def of`.
  - `ContributionCollector.gather(window, index, ctxFor, refused: ContributingPower => Boolean = _ => false)`.
  - `final case class AutomaticUse(user: PlayerId, power: PowerId, card: CardId, effect: (ReadyGame, PendingTree) => Vector[Operation])` in `model/Operation.scala`.
  - File `src/main/scala/oathdigital/gameplay/walker/AutomaticUses.scala`: `private[walker] object AutomaticUses` with `def children`.
  - `PowerUse.automatic(user: PlayerId, power: PowerId, card: CardId): PowerUse`.
  - `PowerUseRules.cardRef(card: CardId): DecisionOptionRef`.
  - File `src/main/scala/oathdigital/gameplay/powers/poweruse/Placements.scala`: `private[powers] object Placements` with `onto` and `byUse`.
  - File `src/main/scala/oathdigital/gameplay/powers/poweruse/ForestCouncil.scala`: `ForestCouncilCard` (moved unchanged from `HoldingBeastDenizens.scala`), `final case class ForestCouncil` and `object ForestCouncil` with `forCatalog`.
  - File `src/main/scala/oathdigital/gameplay/powers/poweruse/CityWall.scala`: `CityWallCard` (moved unchanged from `HoldingOrderDenizens.scala`) and `case object CityWall`.

---

### Task 1: Docs

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`

- [ ] **Step 1: Add part 2's rulings to the design**

In the design, under "### Plan-time rulings" in "## Slice 10: Power-use restrictions", after the bullet "**Placing favor or secrets on a card**", add:

```markdown
- **A use that places** (part 2). A use whose cost places favor or secrets
  on its card is a placement too, made by its user on their own turn. So a
  persistent power with a placed cost, such as Jinx, is refused where
  placing is.
- **One grant per card** (part 2). A placement refusal is remembered under
  the same key as a power-use refusal: the restriction, the placer,
  `"power.use"` and the card. Asking to use a card covers using its power
  and placing on it, for the turn.
- **Where a placement is askable** (part 2). A placement is askable on a
  choose-one whose option leads to it, such as a Muster's or a Trade's
  source card. The walker's search finds it: the option's path meets only
  waivable operation refusals, and granting them opens it. The Hall of
  Ministers is askable the same way. A placement that no choose-one offers
  is refused without a question: Forge's partition, Whispering Leaves' and
  Dissent's gifts.
- **An unexplained placement refusal** (part 2). Under bandits, a source
  card whose placement is refused is left out of its question, not shown
  with a reason. The walker cannot word an operation refusal that nobody
  may waive. This is a known gap.
- **When an automatic power is judged** (part 2). An automatic power is
  judged where its effect runs, inside an `AutomaticUse` node, against the
  state when the walk reaches it. Tyrant's kill runs after the pawn moves,
  so City Wall stops it when its holder travels to City Wall's site, not
  from it. Keep, which replaces a Raid's consequences, is judged where its
  window folds.
- **What an automatic use is** (part 2). An automatic power is used when
  it makes something happen. Initiation Rite changes a cost, and Vow of
  Beastkin's source limit is a restriction, so neither is refused; the
  Vow's extra warband is. The edifice setup rules run during Setup, where
  no player uses a power, so none is refused.
```

- [ ] **Step 2: Add a line to the rulings appendix**

In the rulings appendix, in the "## Slice 10" section, after the "**For every card:**" bullet, add:

```markdown
- **Placements in practice** (part 2). A use whose cost places on its card
  counts as a placement. One grant covers both a card's power and placing
  on it, for the turn. A placement is askable where a choose-one offers it.
  Elsewhere it is refused without a question.
```

- [ ] **Step 3: Check the links and commit**

Run: `python3 scripts/check-markdown-links.py`
Expected: it passes.

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md
git commit -m "docs(batch8): record slice 10 part 2's rulings"
```

---

### Task 2: Askable operation refusals

**Files:**
- Modify: `src/main/scala/oathdigital/model/GameViolation.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationResolution.scala` (`rejection`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala` (`active`)
- Create: `src/main/scala/oathdigital/gameplay/walker/OperationAsks.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (`narrowAt`, `reach`)
- Test: `src/test/scala/oathdigital/gameplay/walker/OperationAsksSuite.scala`

**Interfaces:**
- Produces:
  - `OathViolation.WaivableOperationRejected(code: String, detail: String, waiver: OperationWaiver)`. A required operation that a waivable restriction refuses now rejects with this, not with `CoreOperationRejected`.
  - A walker power's `operationRestrictions` are lifted by a remembered grant, as the printed ones are.
  - At a choose-one, an option the search hides is shown `Unavailable(option, OptionReason.Askable(..., covers))` when two things hold. First, only waivable operation refusals stop its path. Second, granting every refusal it meets opens the path. `covers` holds each refusal's key.

- [ ] **Step 1: Write the failing engine tests**

Create `src/test/scala/oathdigital/gameplay/walker/OperationAsksSuite.scala`. It musters with a test power that forbids placing favor on Alchemist. The board is `MusterProcedureSuite`'s.

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** The walker's search shows an option askable when only waivable
  * operation refusals stop its path and a grant would open it (catalog
  * batch 8, slice 10, part 2). */
class OperationAsksSuite extends munit.FunSuite:
  private val alchemist = CatalogNames.denizen("Alchemist")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val ancientCity = CatalogNames.site("Ancient City")
  private val alchemistRef = DecisionOptionRef.Denizen(alchemist)
  private val wrestlersRef = DecisionOptionRef.Denizen(wrestlers)
  private val ban = PowerId("test.no-favor-on-alchemist")
  private val covers = ConsentKey(ban, p1, "power.use", alchemistRef)
  private val notes = NoteSet(ban)
  private val asked = notes.key("asked",
    Vector(NotePart.Text("Ask first.")))

  /** A power whose operation restriction refuses any payment onto
    * Alchemist, waivable by `granter` under `covers`. */
  private def favorOnAlchemistBannedBy(granter: Option[PlayerId])
      : ContributingPower = new ContributingPower:
    def id: PowerId = ban
    def source: RuleSourceRef = RuleSourceRef.GameRule(ban.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def operationRestrictions: Vector[OperationRestriction] =
      Vector(new OperationRestriction:
        override def reason(ready: ReadyGame, operation: CoreOperation)
            : Option[OperationReason] = operation match
          case pay: PayCost if pay.placedAt == Location.OnCard(alchemist) =>
            Some(OperationReason("test-ban", "no favor on Alchemist",
              OperationReasonKind.Impossible, Some(OperationWaiver(ban,
                granter, covers, asked(PowerSourceRef.Card(alchemist))))))
          case _ => None)

  /** p1 at Ancient City with Alchemist and, when `beside`, Wrestlers to
    * muster from; 4 favor, 7 Supply and 3 warbands on the board. */
  private def musterAtAncientCity(beside: Boolean = true): ReadyGame =
    val table = Table.start.pawn(p1, at = ancientCity)
      .denizen(alchemist, at = ancientCity)
      .favor(p1, 4).supply(p1, 7).boardWarbands(p1, 3)
    (if beside then table.denizen(wrestlers, at = ancientCity) else table)
      .ready

  private def granted(ready: ReadyGame): ReadyGame =
    ready.copy(consent = ConsentState(
      answers = Map(covers -> ConsentAnswer.Granted),
      turn = Some(ConsentTurn.of(ready))))

  /** The Muster tree with `power`, parked on its source decision. */
  private final case class ParkedSource(tree: Operation,
      pending: PendingTree, powers: WalkerPowers)

  private def parkedSource(ready: ReadyGame, power: ContributingPower)
      : ParkedSource =
    val powers = WalkerPowers(Vector(power))
    val tree = accepted(MusterProcedure.build(catalog, ready, p1))
    ParkedSource(tree, Outcomes.parked(
      ProcedureWalker.advance(ready, tree, None, powers)).tree, powers)

  private def sourceOptions(ready: ReadyGame, power: ContributingPower)(
      using munit.Location): Vector[DecisionOption] =
    val walk = parkedSource(ready, power)
    present(ProcedureWalker.parkedDecide(ready, walk.tree, walk.pending,
      walk.powers), "the source decision").query match
      case one: DecisionQuery.ChooseOne => one.options
      case other => fail(s"expected a choose-one, got $other")

  test("a card only a waivable refusal stops is askable of its granter"):
    assertEquals(sourceOptions(musterAtAncientCity(),
      favorOnAlchemistBannedBy(Some(p2))), Vector(
      DecisionOption.Unavailable(DecisionOption.Denizen(alchemistRef),
        OptionReason.Askable(ban, asked(PowerSourceRef.Card(alchemist)),
          Vector(ConsentAsk(p2, Vector(ban))), Vector(covers))),
      DecisionOption.Denizen(wrestlersRef)))

  test("with nobody to grant it, the card is left out"):
    assertEquals(sourceOptions(musterAtAncientCity(),
      favorOnAlchemistBannedBy(None)),
      Vector(DecisionOption.Denizen(wrestlersRef)))

  test("a decision whose every option is askable still parks"):
    assertEquals(sourceOptions(musterAtAncientCity(beside = false),
      favorOnAlchemistBannedBy(Some(p2))).map(_.askable), Vector(true))

  test("a grant remembered this turn opens the card, and the Muster " +
      "places its favor there"):
    val ready = granted(musterAtAncientCity())
    val power = favorOnAlchemistBannedBy(Some(p2))
    assert(sourceOptions(ready, power).contains(
      DecisionOption.Denizen(alchemistRef)))
    val walk = parkedSource(ready, power)
    val done = Outcomes.finished(ProcedureWalker.resolve(ready, walk.tree,
      walk.pending, Answered(MusterProcedure.decisionId,
        DecisionAnswer.ChooseOneAnswer(alchemistRef), p1), walk.powers))
      .treeless
    assertEquals(Look(done).tokensOn(alchemist), Tokens(1, 0))
```

If `Outcomes.parked`, `Outcomes.finished` or `ProcedureWalker.resolve` take other arguments on `main`, use them as `MusterProcedureSuite.parked` and `.muster` do.

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.OperationAsksSuite"`
Expected: compilation passes. The askable tests fail, because Alchemist is left out. The grant test fails, because the walker power's restriction is not lifted.

- [ ] **Step 3: Keep the waiver on the rejection**

In `GameViolation.scala`, after `CoreOperationRejected`, add:

```scala
  /** A required operation a waivable restriction refused (consent design,
    * slice 3). `waiver` names who may lift it and the key a grant is
    * remembered under, so the walker's search can ask about the option
    * whose path it met (catalog batch 8, slice 10). */
  final case class WaivableOperationRejected(code: String, detail: String,
      waiver: OperationWaiver) extends OathViolation
```

In `OperationResolution.scala`, change `rejection` to:

```scala
  private def rejection(reason: OperationReason): OathViolation =
    reason.waiver.fold[OathViolation](OathViolation.CoreOperationRejected(
      reason.code, reason.detail))(waiver =>
      OathViolation.WaivableOperationRejected(reason.code, reason.detail,
        waiver))
```

- [ ] **Step 4: Lift the walker powers' restrictions on a grant**

In `OperationRestrictions.active`, wrap `powerRestrictions` as the printed ones are wrapped:

```scala
    printed.map(ConsentedRestriction(_)) ++ catalog
      .filter(_ => modifiers.nonEmpty || picked.nonEmpty)
      .map(ActiveModifier(_, modifiers, picked)).toVector ++
      powerRestrictions.map(ConsentedRestriction(_))
```

Update the class comment's last paragraph to say "each printed restriction and each power's" instead of "each printed restriction". A restriction without a waiver passes through `ConsentedRestriction` unchanged. `OperationConsent.together` already handles every `ConsentedRestriction` in the list.

- [ ] **Step 5: Create `OperationAsks.scala`**

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.OperationConsent
import oathdigital.model._

/** The asks of a choose-one's options that the search hid because a
  * waivable operation refusal rejects their path (catalog batch 8, slice
  * 10): a Muster's source card that Forest Council forbids placing on, or
  * a card the Hall of Ministers protects. An option is askable when every
  * refusal its path meets is waivable by a player other than the
  * decision's owner, none was refused this turn (consent design, ruling
  * 8), and granting them all opens the path. Each round grants what the
  * last refusal covers and tries again. The ask covers every refusal's
  * key, so a grant lifts each of them on the operation path.
  */
private[walker] object OperationAsks:
  /** Enough rounds for every waivable restriction one path can meet. */
  private val Rounds = 4

  def of(decide: Decide, state: ReadyGame, refs: Vector[DecisionOptionRef],
      verdict: ReadyGame => DecisionAnswer => Either[OathViolation, Unit])
      : Map[DecisionOptionRef, OptionReason] =
    if !decide.query.isInstanceOf[DecisionQuery.ChooseOne] then Map.empty
    else refs.flatMap(ref =>
      ask(decide, state, ref, verdict).map(ref -> _)).toMap

  private def ask(decide: Decide, state: ReadyGame, ref: DecisionOptionRef,
      verdict: ReadyGame => DecisionAnswer => Either[OathViolation, Unit])
      : Option[OptionReason] =
    val turn = ConsentTurn.of(state)
    val answer = DecisionAnswer.ChooseOneAnswer(ref)
    @annotation.tailrec
    def met(found: Vector[OperationReason], round: Int)
        : Vector[OperationReason] =
      val granted = state.copy(consent = state.consent.granting(
        found.flatMap(_.waiver).map(_.covers), turn))
      verdict(granted)(answer) match
        case Right(()) => found
        case Left(OathViolation.WaivableOperationRejected(code, detail,
            waiver)) if round < Rounds &&
            !found.exists(_.waiver.contains(waiver)) =>
          met(found :+ OperationReason(code, detail,
            OperationReasonKind.Impossible, Some(waiver)), round + 1)
        case Left(_) => Vector.empty
    val found = met(Vector.empty, 0)
    if found.isEmpty then None
    else OperationConsent.ask(state, decide.owner, found)
```

`OperationConsent.ask` returns an `OptionReason.Askable` whose `covers` are the refusals' keys, so the consent flow remembers the grant under them. If `ask` returns `Option[OptionReason.Askable]`, the result widens to `Option[OptionReason]` as written.

- [ ] **Step 6: Read the asks in `ProcedureWalker`**

Add, after `asksAt`:

```scala
  /** The asks of `refs`, options of `decide` the search hid, that only
    * waivable operation refusals stop (catalog batch 8, slice 10). */
  private def operationAsksAt(decide: Decide, ctx: WalkCtx,
      path: Vector[String], refs: Vector[DecisionOptionRef])
      : Map[DecisionOptionRef, OptionReason] =
    OperationAsks.of(decide, ctx.state, refs, state =>
      verdict(decide, ctx.copy(state = state), path))
```

In `narrowAt`, read them on both branches:

```scala
    else WalkerSearch.narrow(decide, verdict(decide, ctx, path)) match
      case Left(refused) =>
        val asks = asksAt(decide, ctx, path) ++ operationAsksAt(decide, ctx,
          path, WalkerSearch.choosable(decide))
        if asks.isEmpty then Left(refused)
        else Right(Some(WalkerSearch.reasoned(WalkerSearch.narrowed(decide,
          _ => false, asks.get).getOrElse(decide), asks)))
      case Right(narrowed) => Right(narrowed.map { cut =>
        val kept = WalkerSearch.choosable(cut).toSet
        val hidden = WalkerSearch.choosable(decide).filterNot(kept)
        val reasons = operationAsksAt(decide, ctx, path, hidden) ++
          WalkerPowerGather.lookAheadNoted(ctx.root, decide, cut,
            ctx.state, ctx.activePlayer, ctx.powers, ctx.answered,
            ctx.procedure, grantable(decide, ctx, path)).toMap
        WalkerSearch.reasoned(WalkerSearch.explained(decide, cut, reasons),
          asksAt(decide, ctx, path))
      })
```

A tree restriction's reason, from `lookAheadNoted`, wins over an operation ask for the same option. A grant could not open that option anyway.

In `reach`, a decision no answer leads on from is still a stop when an operation ask exists:

```scala
      case Left(refused) =>
        if asksAt(decide, ctx, path).nonEmpty || operationAsksAt(decide, ctx,
            path, WalkerSearch.choosable(decide)).nonEmpty then
          Right(Stopped(ctx))
        else Left(refused)
```

Update the doc comments of `narrowAt` and `reach`. Each should say that an option only waivable operation refusals hide is askable too.

- [ ] **Step 7: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.* oathdigital.gameplay.operations.* oathdigital.gameplay.MusterProcedureSuite oathdigital.gameplay.OperationRestrictionsSuite oathdigital.application.HallOfMinistersConsentServiceSuite"`
Expected: all pass. `OperationAsksSuite` has 4 tests.

A test that matched the Hall of Ministers' rejection as `CoreOperationRejected("discard-immune", _)` now sees `WaivableOperationRejected`. Update that match to the new case. A test that expected a Hall-protected option of some choose-one to be left out now finds it askable. Update the expectation, and say so in the task report.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/model/GameViolation.scala src/main/scala/oathdigital/gameplay/operations/OperationResolution.scala src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala src/main/scala/oathdigital/gameplay/walker/OperationAsks.scala src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/test/scala/oathdigital/gameplay/walker/OperationAsksSuite.scala
git commit -m "feat(batch8): an option only waivable operation refusals stop is askable"
```

Also stage any test you updated in Step 7, by its path.

---

### Task 3: Refused automatic powers

An automatic power is judged where its effect runs, not where its window
folds (automatic power consent design, ruling 3). Tyrant's kill is
gathered at `TravelCost` before the pawn moves; judged there, City Wall
would read the origin, and a resume after the move (parked at
Hospitality's bank question) would read the destination and move the
parked position. So each wrapped power appends its effect inside an
`AutomaticUse` node, which the walker judges when it reaches it. Keep
replaces a Raid's consequences rather than appending, so it cannot be
wrapped; the collector judges it at fold.

**Files:**
- Modify: `src/main/scala/oathdigital/model/Operation.scala` (add `AutomaticUse`)
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributionCollector.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/PowerUse.scala` (add `automatic`)
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/OperationRewrite.scala`
- Create: `src/main/scala/oathdigital/gameplay/walker/AutomaticUses.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPower.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/Tyrant.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/Hospitality.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/Keep.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/ExtraWarbands.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/VowOfBeastkin.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/VowOfWandering.scala` (`VowOfWanderingMuster`)
- Test: `src/test/scala/oathdigital/gameplay/ContributionCollectorSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/walker/AutomaticUseSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/poweruse/AutomaticPowerUseSuite.scala`

**Interfaces:**
- Consumes: part 1's `ContributingPower.use`, `WalkerPowers.useRestrictions` and `PowerUseRules.refusals`.
- Produces:
  - `AutomaticUse(user: PlayerId, power: PowerId, card: CardId, effect: (ReadyGame, PendingTree) => Vector[Operation])`, a composite with no static children, like `Branch`. The engine judges it without naming the power.
  - `AutomaticUses.children(use, state, pending, powers)`: `use.effect(state, pending)` when `PowerUseRules.refusals(state, PowerUse.automatic(use.user, use.power, use.card), powers.useRestrictions)` is empty, else nothing.
  - `ContributionCollector.gather(window: PowerWindow, index: PowerWindowIndex, ctxFor: ContributingPower => PowerCtx, refused: ContributingPower => Boolean = _ => false): GatheredContributions`. A refused applicable power keeps only its `Offer` and `OfferReroll` contributions. When it has none, it is not gathered and is not in `order`. Only Keep states a use at a window in this part.
  - `PowerUse.automatic(user, power, card)`: a non-persistent use that costs nothing.

- [ ] **Step 1: Write the failing collector tests**

Add to `ContributionCollectorSuite`:

```scala
  test("a refused power keeps only its offers and rerolls"):
    val offer = Offer(_ => None)
    val reroll = OfferReroll(_ => None)
    val refusedPower = fixturePower("power.refused", "a", Set(window),
      Vector(Transform((_, ops) => ops), offer, reroll))
    val gathered = ContributionCollector.gather(window,
      PowerWindowIndex.of(Vector(refusedPower)), ctxFor(_, window),
      refused = _.id == refusedPower.id)
    assertEquals(gathered.transforms, Vector.empty)
    assertEquals(gathered.offers, Vector(refusedPower.id -> offer))
    assertEquals(gathered.rerolls, Vector(refusedPower.id -> reroll))
    assertEquals(gathered.order, Vector(refusedPower.id))

  test("a refused power with nothing to offer is not gathered"):
    val refusedPower = fixturePower("power.refused", "a", Set(window),
      Vector(Transform((_, ops) => ops)))
    val gathered = ContributionCollector.gather(window,
      PowerWindowIndex.of(Vector(refusedPower)), ctxFor(_, window),
      refused = _ => true)
    assertEquals(gathered.order, Vector.empty[PowerId])
    assertEquals(gathered.transforms, Vector.empty)
```

- [ ] **Step 2: Write the failing node tests**

Create `src/test/scala/oathdigital/gameplay/walker/AutomaticUseSuite.scala`. Model it on `BurnTakesSuite`: walk a tree with `ProcedureWalker.advance(from, tree, None, powers)`. Its test power declares one `PowerUseRestriction` (part 1's trait) through `powerUseRestrictions`, refusing every use of `PowerId("test.automatic")` while p1 holds no favor, with no waiver. Name it for what it does, for example `RefusesTestPowerWithoutFavor`. Tests:

- "an allowed automatic use runs its effect": `AutomaticUse(p1, PowerId("test.automatic"), <a denizen id>, (_, _) => Vector(<a 1-favor gain for p1>))` walked with p1 holding favor gains it.
- "a refused automatic use runs nothing": the same tree with p1 at 0 favor finishes with nothing gained.
- "an automatic use is judged where the walk reaches it": `Sequence(<a step that gives p1 1 favor>, AutomaticUse(...))` from 0 favor runs the effect, because the restriction reads the state after the first step.
- "a park inside an automatic use resumes at the same decision": the effect is a `Decide` followed by a gain; the walk parks at the decision, and answering it resumes at the gain. The parked position names the `AutomaticUse`'s index, then the decision's index inside it.

- [ ] **Step 3: Write the failing use tests**

Create `src/test/scala/oathdigital/gameplay/powers/poweruse/AutomaticPowerUseSuite.scala`:

```scala
package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.powerresolver.{ContributingPower, PowerCtx,
  PowerUse}
import oathdigital.gameplay.powers.campaign.KeepRule
import oathdigital.gameplay.powers.economy.InitiationRite
import oathdigital.gameplay.powers.travel.Tyrant
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.p1

/** Keep states its use at its windows; the wrapped automatic powers state
  * theirs on their `AutomaticUse` node instead; a cost change and a
  * lasting limit state none (catalog batch 8, slice 10, part 2). */
class AutomaticPowerUseSuite extends munit.FunSuite:
  private val ready = Table.start.turn(p1, Phase.Act).ready

  private def ctxAt(power: ContributingPower, window: PowerWindow)
      : PowerCtx = PowerCtx(ready, p1, power.source, window, Vector.empty,
    Sequence(Vector.empty, Some(window)))

  test("an automatic use is non-persistent and costs nothing"):
    val use = PowerUse.automatic(p1, Tyrant.id, Tyrant.cardId)
    assert(!use.persistent && !use.costsSecret && !use.places)

  test("Keep is used by nobody without a Raid"):
    assertEquals(KeepRule.use(ctxAt(KeepRule,
      PowerWindow.CampaignRaidRelocation)), None)

  test("a wrapped power states no use at its window; its node does"):
    assertEquals(Tyrant.use(ctxAt(Tyrant, PowerWindow.TravelCost)), None)

  test("Initiation Rite, a cost change, is never used"):
    assertEquals(InitiationRite.use(ctxAt(InitiationRite,
      PowerWindow.MusterCost)), None)
```

Each wrapped power's node is tested in its own suite in Step 9: Tyrant's kill, Hospitality's gain, a When Played effect (Threatening Roar), Conspiracy's seizure, and Vow of Beastkin's and Vow of Wandering's extra warbands are each wrapped in an `AutomaticUse` naming the power, its card and its user.

- [ ] **Step 4: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.ContributionCollectorSuite oathdigital.gameplay.walker.AutomaticUseSuite oathdigital.gameplay.powers.poweruse.AutomaticPowerUseSuite"`
Expected: compilation fails. `gather` takes no `refused`, and `AutomaticUse` and `PowerUse.automatic` do not exist.

- [ ] **Step 5: Leave out a refused power in the collector**

In `ContributionCollector.gather`, add the parameter `refused: ContributingPower => Boolean = _ => false`. After step 2, add:

```scala
    // Step 2b: use -- a power a power-use restriction refuses here keeps
    // only its offers and rerolls, which its host withholds with the
    // reason (catalog batch 8, slice 10). With none left it is not
    // gathered.
    val used = applicable.flatMap(hooked =>
      if !refused(hooked.power) then Some(hooked)
      else
        val kept = hooked.contributions.filter {
          case _: Offer | _: OfferReroll => true
          case _ => false
        }
        Option.when(kept.nonEmpty)(hooked.copy(contributions = kept)))
```

Read `used` instead of `applicable` in step 3 and after it. Update the object comment's step list to name step 2b.

In `WalkerPowerGather`, add:

```scala
  /** Whether `power`, automatic, is refused where `ctx` gathers it
    * (catalog batch 8, slice 10). Only a power that states its use at a
    * window is judged here: Keep. A wrapped power is judged at its
    * `AutomaticUse` node, and a player-selected power at its pick. */
  private def deniedUse(powers: WalkerPowers)(power: ContributingPower,
      ctx: PowerCtx): Boolean =
    powers.useRestrictions.nonEmpty &&
      power.resolution == PowerResolution.Automatic &&
      power.use(ctx).exists(use => PowerUseRules.refusals(ctx.state, use,
        powers.useRestrictions).nonEmpty)
```

Pass `refused = power => deniedUse(powers)(power, ctxFor(power))` to all four `ContributionCollector.gather` calls: in `applyWindow`, `optionAsks`, and the two in `attributed`. At each call site, use the `ctxFor` already in scope and the `WalkerPowers` it gathers from. In `attributed`, that is `powers`, and the context function is `ctxFor(window, path, operation)`. Add the imports `oathdigital.gameplay.operations.PowerUseRules` and `PowerResolution`.

If `WalkerPowerGather.scala` passes 800 lines, move `deniedUse` into part 1's `walker/PowerUseJudge.scala` (create it if part 1 did not) as `private[walker] def deniedUse`.

- [ ] **Step 6: Add `PowerUse.automatic`**

In `object PowerUse`, add:

```scala
  /** An automatic rule's use (catalog batch 8, slice 10, part 2): printed
    * non-persistent, costing nothing. Each one's catalog flag is pinned by
    * `PowerKindsCatalogSuite`. */
  def automatic(user: PlayerId, power: PowerId, card: CardId): PowerUse =
    PowerUse(user, power, Some(card), persistent = false,
      costsSecret = false)
```

- [ ] **Step 7: Add the `AutomaticUse` node and judge it in the walker**

In `model/Operation.scala`, next to `Branch`:

```scala
/** An automatic power's effect, judged where the walk reaches it (catalog
  * batch 8, slice 10): the walker runs `effect` when no power-use
  * restriction refuses `user` using `power` from `card`, and nothing
  * otherwise. Like a `Branch`, it has no static children: a resume cursor
  * addresses the vector the walker resolved. The engine judges it without
  * naming the power. */
final case class AutomaticUse(user: PlayerId, power: PowerId, card: CardId,
    effect: (ReadyGame, PendingTree) => Vector[Operation]) extends Operation:
  override val children: Vector[Operation] = Vector.empty
```

Create `walker/AutomaticUses.scala`:

```scala
package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.powerresolver.PowerUse
import oathdigital.model.{AutomaticUse, Operation, PendingTree, ReadyGame}

/** How the walker resolves an [[AutomaticUse]] (catalog batch 8, slice
  * 10): its effect, unless a power-use restriction refuses it in `state`.
  * Every walker path that resolves a `Branch` resolves this node the same
  * way, so a resume addresses the same vector the walk did. */
private[walker] object AutomaticUses:
  def children(use: AutomaticUse, state: ReadyGame, pending: PendingTree,
      powers: WalkerPowers): Vector[Operation] =
    val refused = PowerUseRules.refusals(state,
      PowerUse.automatic(use.user, use.power, use.card),
      powers.useRestrictions)
    if refused.isEmpty then use.effect(state, pending) else Vector.empty
```

Wire it wherever the walker resolves a `Branch`:

- `ProcedureWalker`'s dispatch: next to `case branch: Branch => walkBranch(...)`, add `case use: AutomaticUse =>` walking `AutomaticUses.children(use, ctx.state, PendingTree(at = path, answered = ctx.answered), <the walk's powers>)` with `walkFolded(None, use, ..., ctx, path, cursor, resume, hooks)`, as `walkBranch` does. Keep it one short case; `ProcedureWalker.scala` is near 800 lines.
- `WalkerPowerGather.foldedChildrenAt`: a `case use: AutomaticUse =>` returning `(AutomaticUses.children(use, state, pending.copy(at = path), powers), gathered)`.
- `WalkerPowerGather.attributed`'s `windowsIn`: descend into an `AutomaticUse` as into a `Branch`.
- `OperationRewrite`: wherever it handles a `Branch` (`case branch: Branch =>`), handle an `AutomaticUse` the same way, rewriting what its `effect` returns and keeping its other fields.

Search for every other `case _: Branch` and `case branch: Branch` under `src/main/scala` and give `AutomaticUse` the same treatment where it resolves children.

- [ ] **Step 8: Wrap each automatic power's effect**

Add the import `oathdigital.model.AutomaticUse` where needed (most powers import `oathdigital.model._`). The user is the player the power already acts for.

| Power | Change |
| --- | --- |
| `WhenPlayedPower` | `children :+ AutomaticUse(ctx.activePlayer, id, cardId, (_, _) => effect(ctx))` in place of `children ++ effect(ctx)`. |
| `ConspiracyWhenPlayed` | Its three appended operations (the target `Decide`s, the `BuildOps` and the `Note`) go inside one `AutomaticUse(ctx.activePlayer, id, <the Conspiracy's card id>, ...)` appended after `children`. |
| `Tyrant` | `operations :+ AutomaticUse(route.player, id, cardId, (_, _) => Vector(kill(route.destination), Note(id, killedNote(_, route.destination))))`. |
| `Hospitality` | `operations :+ AutomaticUse(route.player, id, HospitalityCard.id, (live, _) => welcome(live, route.player, route.destination))`, replacing its `Branch`. |
| `VowOfBeastkin`, `VowOfWanderingMuster` | Append through `ExtraWarbands.appended` (below) instead of `ExtraWarbands.added`. |

Each wrapped power keeps the default `use` (`None`).

In `ExtraWarbands`, replace `added` with `appended(ctx, power, card, extra, operations)`: `operations :+ AutomaticUse(ctx.activePlayer, power, card, (_, _) => Vector(Gain.Warbands(ctx.activePlayer, <the Muster gain's force kind>, extra), Note(power, line(...), covers = true)))`. The vow's gain is its own step right after the Muster's gain, so its line counts what that step added (at most `extra`) and covers the step's generic gain line. Read the force kind the way the Muster's own `Gain.Warbands` does. Update the object's comment: each vow's warbands are their own gain, so under two vows each line tells its own gain. An empty bank still writes nothing.

`KeepRule` states its use at its windows: `override def use(ctx: PowerCtx): Option[PowerUse] = kept(ctx.state).map((defender, _) => PowerUse.automatic(defender, id, cardId))`.

Not refused, so not wrapped and stating no use: Initiation Rite (a cost change), Great Forge, Broken Forge, Proving Grounds and the other edifice setup rules (never refused during Setup), and every lasting limit. Hunger's forced Wake keeps part 1's check in `ForcedWakeProcedure.due`; roadmap item 10b wraps it.

- [ ] **Step 9: Pin each wrap in its power's suite**

In each wrapped power's existing suite, add a test that its window's fold appends one `AutomaticUse` naming the power, its card and its user (the traveller for Tyrant and Hospitality, the card player for When Played and Conspiracy, the musterer for the two vows). Update any test that asserts a parked position inside a wrapped effect: the position gains the `AutomaticUse`'s index.

- [ ] **Step 10: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.ContributionCollectorSuite oathdigital.gameplay.ContributionIgnoresSuite oathdigital.gameplay.walker.* oathdigital.gameplay.powers.poweruse.* oathdigital.gameplay.powers.whenplayed.* oathdigital.gameplay.powers.travel.* oathdigital.gameplay.powers.economy.* oathdigital.gameplay.powers.setup.* oathdigital.gameplay.powers.campaign.*"`
Expected: all pass. Nothing in the catalog refuses these powers yet: Spell Breaker reaches none of them, since none costs a secret.

- [ ] **Step 11: Commit**

```bash
git add src/main/scala/oathdigital/model/Operation.scala src/main/scala/oathdigital/gameplay/powerresolver/ContributionCollector.scala src/main/scala/oathdigital/gameplay/powerresolver/PowerUse.scala src/main/scala/oathdigital/gameplay/powerresolver/OperationRewrite.scala src/main/scala/oathdigital/gameplay/walker/AutomaticUses.scala src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPower.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala src/main/scala/oathdigital/gameplay/powers/travel/Tyrant.scala src/main/scala/oathdigital/gameplay/powers/travel/Hospitality.scala src/main/scala/oathdigital/gameplay/powers/campaign/Keep.scala src/main/scala/oathdigital/gameplay/powers/economy/ExtraWarbands.scala src/main/scala/oathdigital/gameplay/powers/economy/VowOfBeastkin.scala src/main/scala/oathdigital/gameplay/powers/economy/VowOfWandering.scala src/test/scala/oathdigital/gameplay/ContributionCollectorSuite.scala src/test/scala/oathdigital/gameplay/walker/AutomaticUseSuite.scala src/test/scala/oathdigital/gameplay/powers/poweruse/AutomaticPowerUseSuite.scala
```

Also stage each power suite Step 9 changed, by explicit path.

```bash
git commit -m "feat(batch8): a refused automatic power does not apply"
```

---

### Task 4: Forest Council

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/poweruse/Placements.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/poweruse/ForestCouncil.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/poweruse/RulerBans.scala` (add `reason`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/PowerUseRules.scala` (add `cardRef`)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala` (remove `ForestCouncilCard`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Modify: `src/test/resources/notes/declared-keys.txt` (regenerated)
- Test: `src/test/scala/oathdigital/gameplay/powers/poweruse/ForestCouncilSuite.scala`

**Interfaces:**
- Consumes:
  - from part 1: `RulerBans.ruler`, `.enemy`, `.refusal`, `.waiver`; `PowerUseRules.useKey` and `.target`;
  - from Task 2: the askable search;
  - from Task 3: the collector's refusal.
- Produces:
  - `Placements.onto(ready: ReadyGame, operation: CoreOperation): Option[(PlayerId, CardId)]`: the placer and the card when `operation` places favor or secrets on a card. That is a `PayCost` onto a card, paid on the payer's own turn, or a `Give` of favor or secrets onto a card.
  - `Placements.byUse(ready: ReadyGame, use: PowerUse): Option[CardId]`: the card when `use.places` and its user is the active player.
  - `RulerBans.reason(power: PowerId, card: DenizenId, ruler: SiteRuler, requester: PlayerId, covers: ConsentKey, asked: NoteKey, code: String): OperationReason`.
  - `PowerUseRules.cardRef(card: CardId): DecisionOptionRef`.
  - `ForestCouncil.id`, and note keys `usePlain`, `useAsked`, `placingPlain`, `placingAsked`.

- [ ] **Step 1: Write the failing restriction and path tests**

Create `src/test/scala/oathdigital/gameplay/powers/poweruse/ForestCouncilSuite.scala`.

The boards are:
- Forest Council stands at Deep Woods.
- Roving Terror (Beast, ACTION, one secret) and Wrestlers (Order) stand at Ancient City.
- Errand Boy (Beast) stands at Ancient City to muster from.

```scala
package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powerresolver.PowerUse
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.economy.VowOfBeastkin
import oathdigital.gameplay.powers.whenplayed.{ThreateningRoar,
  WhenPlayedHarness}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Forest Council (card 194): "Enemies of Forest Council's ruler cannot
  * place [favor] / [secret] on [suit-beast] cards or use their
  * non-persistent powers." (catalog batch 8, slice 10, part 2). */
class ForestCouncilSuite extends munit.FunSuite:
  private val council = CatalogNames.denizen("Forest Council")
  private val woods = CatalogNames.site("Deep Woods")
  private val home = CatalogNames.site("Ancient City")
  private val terror = CatalogNames.denizen("Roving Terror")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val errandBoyRef = DecisionOptionRef.Denizen(errandBoy)
  private val power = ForestCouncil.forCatalog(catalog)
  private val source = PowerSourceRef.Card(council)

  /** p1's Act at Ancient City with 4 favor and 7 Supply, beside Errand Boy
    * and Wrestlers, with Forest Council at Deep Woods, which `ruled` gives
    * its ruler: p2 by default. */
  private def councilAtDeepWoods(
      ruled: Table => Table = _.warbandsAt(woods, p2, 1)): ReadyGame =
    ruled(Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(errandBoy, at = home).denizen(wrestlers, at = home)
      .favor(p1, 4).supply(p1, 7).denizen(council, at = woods)).ready

  private def useOf(card: DenizenId, persistent: Boolean): PowerUse =
    PowerUse(p1, PowerId("test.power"), Some(card), persistent,
      costsSecret = false)

  test("it refuses an enemy a Beast card's non-persistent power, waivable " +
      "by the ruler"):
    val refusal = power.refusal(councilAtDeepWoods(),
      useOf(terror, persistent = false))
    assertEquals(refusal.map(_.note), Some(ForestCouncil.usePlain(source)))
    assertEquals(refusal.flatMap(_.reason.waiver).flatMap(_.granter),
      Some(p2))

  test("it refuses neither a Beast card's persistent power that places " +
      "nothing, nor another suit's non-persistent power, nor its ruler"):
    val ready = councilAtDeepWoods()
    assertEquals(power.refusal(ready, useOf(terror, persistent = true)),
      None)
    assertEquals(power.refusal(ready, useOf(wrestlers, persistent = false)),
      None)
    assertEquals(power.refusal(councilAtDeepWoods(_.warbandsAt(woods, p1,
      1)), useOf(terror, persistent = false)), None)

  test("a persistent power whose cost places on a Beast card is refused " +
      "on its user's turn, with the placing reason"):
    val placing = useOf(terror, persistent = true).copy(places = true)
    assertEquals(power.refusal(councilAtDeepWoods(), placing).map(_.note),
      Some(ForestCouncil.placingPlain(source)))
    val offTurn = councilAtDeepWoods().updateCurrent(current =>
      current.copy(turn = current.turn.copy(activePlayer = p2)))
    assertEquals(power.refusal(offTurn, placing), None)

  test("it refuses a payment onto a Beast card on the payer's turn, and " +
      "not off it"):
    val ready = councilAtDeepWoods()
    val pay = PayCost(p1, Location.OnCard(errandBoy), Cost(favor = 1))
    val ban = present(power.operationRestrictions.headOption, "the ban")
    assertEquals(ban.reason(ready, pay).flatMap(_.waiver).map(_.covers),
      Some(ConsentKey(ForestCouncil.id, p1, "power.use", errandBoyRef)))
    assertEquals(ban.reason(ready, pay.copy(placedAt =
      Location.OnCard(wrestlers))), None)
    assertEquals(ban.reason(ready, pay.copy(player = p2)), None)

  test("a Muster from a Beast card is askable of the ruler, beside a card " +
      "of another suit"):
    val options = musterSources(councilAtDeepWoods())
    assertEquals(options.find(_.ref == errandBoyRef).map(_.askable),
      Some(true))
    assert(options.exists(option =>
      option.ref == DecisionOptionRef.Denizen(wrestlers) && option.available))

  test("an enemy's Beast When Played power does not fire"):
    // Threatening Roar, played as p1's adviser, would discard Errand Boy,
    // a Beast card at p1's site. Forest Council stands outside p1's
    // region, where the Roar does not reach.
    import WhenPlayedHarness.{awaySite, homeSite, p1AtHomeSite}
    val roar = WhenPlayedHarness.registered[ThreateningRoar]
    val ready = p1AtHomeSite.adviser(p1, roar.cardId)
      .denizen(errandBoy, at = homeSite).denizen(council, at = awaySite)
      .warbandsAt(awaySite, p2, 1).ready
    val done = Outcomes.finished(WhenPlayedHarness.playBeside(ready, roar,
      roar.cardId, power))
    assert(Look(done.treeless).cardsAt(homeSite).contains(errandBoy))

  test("an enemy's Vow of Beastkin adds no warband, and its source limit " +
      "still holds"):
    // The Vow's extra warband is a use, and Forest Council refuses it. Its
    // source limit is a restriction, not a use, so it holds: Tyrant, an
    // Order adviser, makes Wrestlers (Order) a legal source. Wrestlers is
    // not a Beast card, so Forest Council lets p1 place favor on it.
    val vow = CatalogNames.denizen("Vow of Beastkin")
    val tyrant = CatalogNames.denizen("Tyrant")
    def board(withCouncil: Boolean): ReadyGame =
      val base = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
        .denizen(wrestlers, at = home).adviser(p1, vow).adviser(p1, tyrant)
        .favor(p1, 4).supply(p1, 7).boardWarbands(p1, 3)
      (if withCouncil then base.denizen(council, at = woods)
        .warbandsAt(woods, p2, 1) else base).ready
    def mustered(ready: ReadyGame): Int =
      Look(musteredFrom(ready, DecisionOptionRef.Denizen(wrestlers)))
        .boardWarbands(p1)
    assertEquals(mustered(board(withCouncil = true)),
      mustered(board(withCouncil = false)) - VowOfBeastkin.Warbands)

  /** Every production walker power, as a command with no modifiers picked
    * gets them. */
  private val everyPower: WalkerPowers =
    WalkerPowers.selected(WalkerPowerCatalog.default(catalog), Vector.empty)

  /** The Muster tree, walked with every production power to its source
    * decision. */
  private final case class ParkedMuster(tree: Operation,
      pending: PendingTree)

  private def parkedMuster(ready: ReadyGame): ParkedMuster =
    val tree = accepted(MusterProcedure.build(catalog, ready, p1))
    ParkedMuster(tree, Outcomes.parked(ProcedureWalker.advance(ready, tree,
      None, everyPower)).tree)

  /** The game after p1 musters from `ref`. */
  private def musteredFrom(ready: ReadyGame, ref: DecisionOptionRef)
      : ReadyGame =
    val walk = parkedMuster(ready)
    Outcomes.finished(ProcedureWalker.resolve(ready, walk.tree,
      walk.pending, Answered(MusterProcedure.decisionId,
        DecisionAnswer.ChooseOneAnswer(ref), p1), everyPower)).treeless

  /** The Muster source decision's options, walked with every production
    * walker power. */
  private def musterSources(ready: ReadyGame)(using munit.Location)
      : Vector[DecisionOption] =
    val walk = parkedMuster(ready)
    present(ProcedureWalker.parkedDecide(ready, walk.tree, walk.pending,
      everyPower), "the source decision").query match
      case one: DecisionQuery.ChooseOne => one.options
      case other => fail(s"expected a choose-one, got $other")
```

`homeSite`, `awaySite` and `p1AtHomeSite` are `WhenPlayedHarness`'s, as `ThreateningRoarSuite` uses them. If the modifier loop parks before the source decision with every production power, park past it: `MusterModifierChoiceSuite` shows how to answer the start. `MusterProcedureSuite.parked` and `.muster` show the walker calls, if their arguments differ on `main`.

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.poweruse.ForestCouncilSuite"`
Expected: compilation fails, because `ForestCouncil` does not exist.

- [ ] **Step 3: Create `Placements.scala`**

```scala
package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.powerresolver.PowerUse
import oathdigital.model._

/** Placing favor or secrets on a card (catalog batch 8, slice 10): a cost
  * paid onto it on the payer's own turn, or a gift of favor or secrets onto
  * it. Off its payer's turn a payment settles to the bank and places
  * nothing (`PayCostSettlement`). */
private[powers] object Placements:
  /** The placer and the card, when `operation` places on a card. */
  def onto(ready: ReadyGame, operation: CoreOperation)
      : Option[(PlayerId, CardId)] = operation match
    case pay: PayCost if pay.cost.favor > 0 || pay.cost.secret > 0 =>
      (pay.placedAt, pay.player) match
        case (Location.OnCard(card), player)
            if player == ready.game.current.turn.activePlayer =>
          Some(player -> card)
        case _ => None
    case give: Give => (give.piece, give.to) match
      case (_: Piece.Favor | _: Piece.Secrets, Location.OnCard(card)) =>
        Some(give.giver -> card)
      case _ => None
    case _ => None

  /** The card `use`'s cost places on, when it places on its user's turn. */
  def byUse(ready: ReadyGame, use: PowerUse): Option[CardId] =
    use.card.filter(_ =>
      use.places && use.user == ready.game.current.turn.activePlayer)
```

If `Piece.Favor` and `Piece.Secrets` are named differently on `main`, use the pieces `PayCost.placedFavor` moves.

- [ ] **Step 4: Add `RulerBans.reason` and `PowerUseRules.cardRef`**

In `RulerBans`, split the `OperationReason` out of `refusal`:

```scala
  /** `power`'s refusal of `requester` on the operation path, waivable by a
    * player ruler under `covers`. */
  def reason(power: PowerId, card: DenizenId, ruler: SiteRuler,
      requester: PlayerId, covers: ConsentKey, asked: NoteKey, code: String)
      : OperationReason =
    val granter = ruler match
      case SiteRuler.Player(player) => Some(player)
      case _ => None
    OperationReason(code, s"${power.value} refuses ${requester.value}",
      OperationReasonKind.Impossible, granter.map(player =>
        OperationWaiver(power, Some(player), covers,
          asked(PowerSourceRef.Card(card)))))
```

`refusal` becomes `PowerUseRefusal(power, plain(PowerSourceRef.Card(card)), reason(power, card, ruler, requester, covers, asked, code))`.

In `PowerUseRules`, add `cardRef` and make `target` use it:

```scala
  /** The option reference of `card`, the target of a grant's key. */
  def cardRef(card: CardId): DecisionOptionRef = card match
    case id: DenizenId => DecisionOptionRef.Denizen(id)
    case id: RelicId => DecisionOptionRef.Relic(id)
    case id: EdificeId => DecisionOptionRef.Edifice(id)
    case other => DecisionOptionRef.Button(other.value)

  def target(use: PowerUse): DecisionOptionRef =
    use.card.fold(DecisionOptionRef.Button(use.power.value))(cardRef)
```

Keep the match on whatever id classes part 1's `target` matched. If `CardId` has no other member, drop the last case.

- [ ] **Step 5: Create `ForestCouncil.scala`**

Move `ForestCouncilCard` here unchanged and remove it from `HoldingBeastDenizens.scala`. Drop any import there that only it used.

```scala
package oathdigital.gameplay.powers.poweruse

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower,
  SiteOnly}
import oathdigital.gameplay.EffectiveSuit
import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  PowerUse, PowerUseRefusal, PowerUseRestriction, Waiver}
import oathdigital.model._

object ForestCouncilCard extends Denizen(DenizenId("194"), "Forest Council", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.forest-council"),
    persistent = true, cost = Cost.free,
    text = "Enemies of Forest Council's ruler **cannot** place [favor] " +
      "/ [secret] on [suit-beast] cards or use their " +
      "non-persistent powers.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Forest Council (card 194, site-only): "Enemies of Forest Council's
  * ruler cannot place [favor] / [secret] on [suit-beast] cards or use their
  * non-persistent powers."
  *
  * A Beast card is a faceup adviser of effective suit Beast, or a site card
  * printed Beast (`EffectiveSuit.of`). The enemies are its site's ruler's
  * ([[RulerBans]]). It refuses an enemy's non-persistent use of a Beast
  * card, a use whose cost places on one ([[Placements.byUse]]), and a
  * placement on one ([[Placements.onto]]). A player ruler may consent
  * outside a Campaign. One grant, under the card's use key, covers both.
  * It writes no line.
  */
final case class ForestCouncil private (catalog: ExecutableCatalog)
    extends ContributingPower with PowerUseRestriction:
  import ForestCouncil._

  def id: PowerId = ForestCouncil.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
  override def noteKeys: Vector[NoteKey] = ForestCouncil.noteKeys
  override def powerUseRestrictions: Vector[PowerUseRestriction] =
    Vector(this)
  override def operationRestrictions: Vector[OperationRestriction] =
    Vector(placing)
  override def waiver: Option[Waiver] =
    Some(RulerBans.waiver(ForestCouncilCard.id, useAsked))

  private def beast(ready: ReadyGame, card: CardId): Boolean =
    EffectiveSuit.of(catalog, ready, card).contains(Suit.Beast)

  private def covers(player: PlayerId, card: CardId): ConsentKey =
    ConsentKey(id, player, PowerUseRules.useKey, PowerUseRules.cardRef(card))

  /** The plain and asked reasons for `use`: the use's own when it is
    * non-persistent, the placing ones when its cost places now. */
  private def reasons(ready: ReadyGame, use: PowerUse)
      : Option[(NoteKey, NoteKey)] =
    if !use.persistent then Some(usePlain -> useAsked)
    else Placements.byUse(ready, use).map(_ => placingPlain -> placingAsked)

  def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal] =
    for
      ruler <- RulerBans.ruler(ready, ForestCouncilCard.id)
      if RulerBans.enemy(ruler, use.user)
      card <- use.card
      if beast(ready, card)
      keys <- reasons(ready, use)
    yield RulerBans.refusal(id, ForestCouncilCard.id, ruler, use.user,
      covers(use.user, card), keys._1, keys._2, "forest-council")

  private object placing extends OperationRestriction:
    def reason(ready: ReadyGame, operation: CoreOperation)
        : Option[OperationReason] =
      for
        placed <- Placements.onto(ready, operation)
        if beast(ready, placed._2)
        ruler <- RulerBans.ruler(ready, ForestCouncilCard.id)
        if RulerBans.enemy(ruler, placed._1)
      yield RulerBans.reason(id, ForestCouncilCard.id, ruler, placed._1,
        covers(placed._1, placed._2), placingAsked,
        "forest-council-placing")

object ForestCouncil:
  val id: PowerId = ForestCouncilCard.power.id
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Cannot use non-persistent powers of Beast cards." */
  val usePlain: NoteKey = notes.key("no-beast-powers", Vector(NotePart.Text(
    "Cannot use non-persistent powers of Beast cards.")))
  /** "... without ruler's consent." */
  val useAsked: NoteKey = notes.key("no-beast-powers-consent", Vector(
    NotePart.Text("Cannot use non-persistent powers of Beast cards " +
      "without ruler's consent.")))
  /** "Cannot place favor or secrets on Beast cards." */
  val placingPlain: NoteKey = notes.key("no-beast-placing", Vector(
    NotePart.Text("Cannot place favor or secrets on Beast cards.")))
  /** "... without ruler's consent." */
  val placingAsked: NoteKey = notes.key("no-beast-placing-consent", Vector(
    NotePart.Text("Cannot place favor or secrets on Beast cards without " +
      "ruler's consent.")))

  def forCatalog(catalog: ExecutableCatalog): ForestCouncil =
    new ForestCouncil(catalog)
```

- [ ] **Step 6: Register it**

- In `WalkerPowerCatalog.scala`, add `Vector(ForestCouncil.forCatalog(catalog)) ++` after `Vector(SpellBreaker) ++`.
- `NewFoundations.scala` already imports `powers.poweruse._` (part 1). `ForestCouncilCard` keeps its place in the card list.
- In `PowerKindsCatalogSuite.scala`, add `"denizen.forest-council"` to `persistentRules`.
- Regenerate the note-key snapshot. The diff must add exactly four lines for `denizen.forest-council`: `no-beast-powers`, `no-beast-powers-consent`, `no-beast-placing` and `no-beast-placing-consent`.

`ConsentPowers` lists Forest Council through its `waiver` (part 1, Task 3).

- [ ] **Step 7: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.poweruse.* oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.BackendArchitectureSuite oathdigital.gameplay.powers.DeclaredNoteKeysSuite oathdigital.gameplay.MusterProcedureSuite oathdigital.gameplay.TradeProcedureSuite oathdigital.gameplay.powers.whenplayed.* oathdigital.gameplay.powers.economy.*"`
Expected: all pass. ForestCouncilSuite has 7 tests.

A suite whose board puts Forest Council beside a Beast card it uses may now see a refusal. That is the card working. Move Forest Council to a site the suite does not rule against p1, and say so in the task report.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/poweruse/Placements.scala src/main/scala/oathdigital/gameplay/powers/poweruse/ForestCouncil.scala src/main/scala/oathdigital/gameplay/powers/poweruse/RulerBans.scala src/main/scala/oathdigital/gameplay/operations/PowerUseRules.scala src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala src/test/resources/notes/declared-keys.txt src/test/scala/oathdigital/gameplay/powers/poweruse/ForestCouncilSuite.scala
git commit -m "feat(batch8): Forest Council bans its ruler's enemies from Beast cards"
```

---

### Task 5: City Wall

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/poweruse/CityWall.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala` (remove `CityWallCard`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Modify: `src/test/resources/notes/declared-keys.txt` (regenerated)
- Test: `src/test/scala/oathdigital/gameplay/powers/poweruse/CityWallSuite.scala`

**Interfaces:**
- Consumes: Task 4's `Placements`, `RulerBans.reason` and `PowerUseRules.cardRef`.
- Produces: `CityWall.id`, and note keys `usePlain`, `useAsked`, `placingPlain` and `placingAsked`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/poweruse/CityWallSuite.scala`.
- City Wall stands at Ancient City, p1's pawn site. p2 rules it by default.
- The restriction tests mirror Forest Council's. The path tests use the boards named below.

```scala
package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.powerresolver.PowerUse
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** City Wall (card 252): "While at this site, enemies of City Wall's ruler
  * cannot place [favor] / [secret] on cards or use non-persistent powers."
  * (catalog batch 8, slice 10, part 2). */
class CityWallSuite extends munit.FunSuite:
  private val wall = CatalogNames.denizen("City Wall")
  private val home = CatalogNames.site("Ancient City")
  private val woods = CatalogNames.site("Deep Woods")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val source = PowerSourceRef.Card(wall)

  /** p1's Act with City Wall at Ancient City, which `ruled` gives its
    * ruler (p2 by default), and p1's pawn at `pawnAt`. */
  private def wallAtAncientCity(pawnAt: SiteId = home,
      ruled: Table => Table = _.warbandsAt(home, p2, 1)): ReadyGame =
    ruled(Table.start.turn(p1, Phase.Act).pawn(p1, at = pawnAt)
      .denizen(wall, at = home).denizen(wrestlers, at = woods)).ready

  private def nonPersistentUse: PowerUse = PowerUse(p1,
    PowerId("test.power"), Some(wrestlers), persistent = false,
    costsSecret = false)

  test("it refuses an enemy at its site any non-persistent power, waivable " +
      "by the ruler"):
    val refusal = CityWall.refusal(wallAtAncientCity(), nonPersistentUse)
    assertEquals(refusal.map(_.note), Some(CityWall.usePlain(source)))
    assertEquals(refusal.flatMap(_.reason.waiver).flatMap(_.granter),
      Some(p2))

  test("away from its site, or as its ruler, nothing is refused"):
    assertEquals(CityWall.refusal(wallAtAncientCity(pawnAt = woods),
      nonPersistentUse), None)
    assertEquals(CityWall.refusal(wallAtAncientCity(ruled =
      _.warbandsAt(home, p1, 1)), nonPersistentUse), None)

  test("it refuses an enemy at its site a payment onto any card"):
    val ban = CityWall.operationRestrictions.head
    val pay = PayCost(p1, Location.OnCard(wrestlers), Cost(favor = 1))
    assertEquals(ban.reason(wallAtAncientCity(), pay).flatMap(_.waiver)
      .map(_.covers), Some(ConsentKey(CityWall.id, p1, "power.use",
      DecisionOptionRef.Denizen(wrestlers))))
    assertEquals(ban.reason(wallAtAncientCity(pawnAt = woods), pay), None)
```

Then add these path tests. Each starts from the suite of the card it uses, with City Wall at the acting player's site and ruled by the other player.

1. **The phase menu.** Copy `SpellBreakerPhaseSuite`'s board, with City Wall in place of Spell Breaker at p1's site, Ancient City. Roving Terror's option is askable, and `ask.power == CityWall.id`.
2. **Muster.** p1 musters at City Wall's site, from Wrestlers, with 4 favor and 7 Supply. The only source is askable, and the decision still parks. Read it as `ForestCouncilSuite.musterSources` does.
3. **Jinx's reroll.** Use `SpellBreakerOfferSuite`'s Recover board, with City Wall at Fair Isle ruled by p2 in place of Spell Breaker. Jinx is persistent, but its cost places a secret on it, so its reroll is askable. Its reason is `CityWall.placingAsked`.
4. **A battle plan.** Use `SpellBreakerOfferSuite`'s Fire Talkers board, with City Wall at the attacker's origin, ruled by the rival. The board helper there is `rivalRulesOriginAndHoldsOathkeeper`. Fire Talkers is withheld with `Some(OptionReason.Noted(CityWall.id, CityWall.usePlain(source)))`, since nobody is asked inside a Campaign.
5. **Tyrant.** Use `TyrantSuite`'s travel board. p1 holds Tyrant and travels to City Wall's site, ruled by p2, where p2 has a warband. No warband dies: Tyrant is judged after the pawn moves. A second test travels from City Wall's site to a site with a warband, and that warband dies.
6. **Hunger's forced Wake.** Use `HungerSuite`'s first forced board, with City Wall at p1's site, ruled by p2. p1's Wake does not force Hunger: `ForcedWakeProcedure.due` is empty for p1.

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.poweruse.CityWallSuite"`
Expected: compilation fails, because `CityWall` does not exist.

- [ ] **Step 3: Create `CityWall.scala`**

Move `CityWallCard` here unchanged and remove it from `HoldingOrderDenizens.scala`.

```scala
package oathdigital.gameplay.powers.poweruse

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.{PowerAccess, SiteRulers}
import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  PowerUse, PowerUseRefusal, PowerUseRestriction, Waiver}
import oathdigital.model._

object CityWallCard extends Denizen(DenizenId("252"), "City Wall", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.city-wall"),
    persistent = true, cost = Cost.free,
    text = "While at this site, enemies of City Wall's ruler **cannot** " +
      "place [favor] / [secret] on cards or use non-persistent " +
      "powers.")
  val powers: Vector[PrintedPower] = Vector(power)

/** City Wall (card 252, site-only): "While at this site, enemies of City
  * Wall's ruler cannot place [favor] / [secret] on cards or use
  * non-persistent powers."
  *
  * An enemy of its site's ruler ([[RulerBans]]) whose pawn stands at that
  * site cannot use a non-persistent power from any card, a use whose cost
  * places on its card ([[Placements.byUse]]), or place on any card
  * ([[Placements.onto]]). The pawn is the physical one: an as-if site does
  * not count. A player ruler may consent outside a Campaign. It writes no
  * line.
  */
case object CityWall extends ContributingPower with PowerUseRestriction:
  val id: PowerId = CityWallCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
  private val notes = NoteSet(id)
  override def noteKeys: Vector[NoteKey] = notes.declared
  /** "Cannot use non-persistent powers while at its site." */
  val usePlain: NoteKey = notes.key("no-powers-here", Vector(NotePart.Text(
    "Cannot use non-persistent powers while at its site.")))
  /** "... without ruler's consent." */
  val useAsked: NoteKey = notes.key("no-powers-here-consent", Vector(
    NotePart.Text("Cannot use non-persistent powers while at its site " +
      "without ruler's consent.")))
  /** "Cannot place favor or secrets on cards while at its site." */
  val placingPlain: NoteKey = notes.key("no-placing-here", Vector(
    NotePart.Text("Cannot place favor or secrets on cards while at its " +
      "site.")))
  /** "... without ruler's consent." */
  val placingAsked: NoteKey = notes.key("no-placing-here-consent", Vector(
    NotePart.Text("Cannot place favor or secrets on cards while at its " +
      "site without ruler's consent.")))

  override def powerUseRestrictions: Vector[PowerUseRestriction] =
    Vector(this)
  override def operationRestrictions: Vector[OperationRestriction] =
    Vector(placing)
  override val waiver: Option[Waiver] =
    Some(RulerBans.waiver(CityWallCard.id, useAsked))

  /** City Wall's ruler, when `player` is their enemy and stands at its
    * site. */
  private def walledIn(ready: ReadyGame, player: PlayerId)
      : Option[SiteRuler] =
    for
      site <- SiteRulers.siteOf(ready, CityWallCard.id)
      if PowerAccess.pawnSite(ready, player).contains(site)
      ruler <- RulerBans.ruler(ready, CityWallCard.id)
      if RulerBans.enemy(ruler, player)
    yield ruler

  /** The plain and asked reasons for `use`: the use's own when it is
    * non-persistent, the placing ones when its cost places now. */
  private def reasons(ready: ReadyGame, use: PowerUse)
      : Option[(NoteKey, NoteKey)] =
    if !use.persistent then Some(usePlain -> useAsked)
    else Placements.byUse(ready, use).map(_ => placingPlain -> placingAsked)

  def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal] =
    for
      ruler <- walledIn(ready, use.user)
      keys <- reasons(ready, use)
    yield RulerBans.refusal(id, CityWallCard.id, ruler, use.user,
      ConsentKey(id, use.user, PowerUseRules.useKey,
        PowerUseRules.target(use)), keys._1, keys._2, "city-wall")

  private object placing extends OperationRestriction:
    def reason(ready: ReadyGame, operation: CoreOperation)
        : Option[OperationReason] =
      Placements.onto(ready, operation).flatMap(placed =>
        walledIn(ready, placed._1).map(ruler => RulerBans.reason(id,
          CityWallCard.id, ruler, placed._1, ConsentKey(id, placed._1,
            PowerUseRules.useKey, PowerUseRules.cardRef(placed._2)),
          placingAsked, "city-wall-placing")))
```

`notes` must be declared before the keys, and `waiver` after `useAsked`, as plain `val`s.

City Wall refuses a banner or site power too, which has no card: part 1 counts those as non-persistent. Its grant key then names the power's button (`PowerUseRules.target`).

- [ ] **Step 4: Register it**

- In `WalkerPowerCatalog.scala`, add `Vector(CityWall) ++` after Forest Council.
- In `PowerKindsCatalogSuite.scala`, add `"denizen.city-wall"` to `persistentRules`.
- Regenerate the note-key snapshot. The diff must add exactly four lines for `denizen.city-wall`.

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.poweruse.* oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.BackendArchitectureSuite oathdigital.gameplay.powers.DeclaredNoteKeysSuite oathdigital.gameplay.powers.travel.* oathdigital.gameplay.powers.wake.* oathdigital.gameplay.powers.dice.* oathdigital.gameplay.powers.campaign.*"`
Expected: all pass. CityWallSuite has 9 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/poweruse/CityWall.scala src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala src/test/resources/notes/declared-keys.txt src/test/scala/oathdigital/gameplay/powers/poweruse/CityWallSuite.scala
git commit -m "feat(batch8): City Wall bans its ruler's enemies at its site"
```

---

### Task 6: Gates and status

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` (Status line)
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Run the full gate**

Run: `./sbtw "test" "frontend/test"`
Expected: everything passes. The server count rises by the tests this plan adds: 4 + 2 + 4 + 7 + 9 = 26, plus any the reviews ask for.

Run: `./sbtw "frontend/fullOptJS"`
Expected: it links.

Run: `python3 scripts/check-architecture.py` and `python3 scripts/check-markdown-links.py`.
Expected: both pass.

- [ ] **Step 2: Record the status**

- In the design's Status line, write "Slices 1 to 9 are done, and slice 10's first two parts (Spell Breaker, Forest Council, City Wall)."
- In the ROADMAP's item 10, change the part note to "Parts 1 and 2 (Spell Breaker, Forest Council, City Wall) are done; part 3 (Giant Python) is next."

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/ROADMAP.md
git commit -m "docs(batch8): slice 10 part 2 done"
```
