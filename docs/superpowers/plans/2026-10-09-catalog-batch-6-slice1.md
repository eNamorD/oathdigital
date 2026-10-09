# Catalog Batch 6, Slice 1 (Plan Restrictions) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Specialist (114), Code of Honor (103), Marsh Spirit (192), Beast Tamer (90) and True Names (41). Each stops a side of a Campaign from using some battle plans.

**Architecture:**

- **Option restrictions reach the plans an `OfferHost` gathers.** `WalkerPowerGather.applyWindow` runs the window's `OptionRestriction`s over each gathered offer before `expand`, keyed by the offer source's option ref.
  - A forbidden offer is withheld with the first forbidding restriction's note (`OptionReason.Noted`).
  - It is dropped when that restriction has no note.
- **`CampaignPlanOffer.withheld` becomes `Option[OptionReason]`.** Encirclement's reason becomes `OptionReason.Written`.
- **`CampaignPlanSource.ref`** names a source as a decision option. It lives in the model, so the walker needs no Campaign import. `CampaignPlans.refOf` delegates to it.
- **`BattlePlan.restrictions`** lets a plan's power forbid options at a window. Specialist and Code of Honor use it.
- **`CampaignPlanOffer.alone`** marks a plan used with no other (Code of Honor). A bandit defender that would apply such a plan applies it and nothing else (FAQ).
- **The three persistent rules** are plain `ContributingPower`s with an `OptionRestriction` at the plan windows: Marsh Spirit, Beast Tamer and True Names.
- **Registration.** All five register in a new `PlanRestrictions` object.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change: the plan decision already shows an unavailable option with its reason on hover.

**Spec:** Read both documents before starting:

- `docs/superpowers/specs/2026-10-09-catalog-batch-6-design.md`: "Goal and scope", "The bar", "Slice 1: Plan restrictions", "Seams used for the first time" and "Testing".
- `docs/superpowers/specs/2026-10-09-catalog-batch-6-rulings.md`: "Rules that apply to the whole batch" and "Slice 1: Plan restrictions".

Task 1 amends both with the rulings made at plan time, so read them after Task 1 too. The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

**Starts from:** `main` at `22c8b88d` or later.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails it.
  - Each file below lists the imports it needs.
  - A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker sources.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
  - The walker (`gameplay/walker`) does not import `gameplay/actions` outside `WalkerProcedureRegistry`.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Log lines.** No slice 1 power writes a Game Log line. A restriction's note is a hover reason only (travel destination design, ruling 10). A chosen plan is told by the plan window's own line.
- **Texts, exactly.** A hover reason reads as the Game Log words its note: the card's name, a colon, then the sentence.
  - Specialist, key `silenced`: "The defender cannot use battle plans."
  - Marsh Spirit, key `mired`: "Players who target its site cannot use battle plans."
  - Beast Tamer, key `tamed`: "No Beast or Nomad battle plans."
  - True Names, key `named`: "No battle plans matching {Blue}'s advisers." `{Blue}` is the holder, a `NoteArg.Player`.
  - Code of Honor, key `not-with-others`: "Cannot be used with other battle plans."
  - Code of Honor, key `no-others`: "No other battle plans."
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 34 server tests and no frontend test.
  - It rewrites no existing test.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

The product owner settled ruling 1 on 2026-10-09. Task 1 writes all of them into the spec.

1. **Bandits and Code of Honor.** A bandit defender that would apply Code of Honor applies it and no other plan. This follows the FAQ: "They will use Code of Honor and no others." A Code of Honor another power withholds is not applied, and then the bandits apply their other plans as usual. This is carried by `CampaignPlanOffer.alone` and read in `CampaignPlanChoice.banditPlans`. It is an approved change outside `gameplay/powers/`.

Also settled at plan time:

2. **Reason wording.** A withheld plan's reason is its note, which reads as the Game Log words it: "Specialist: The defender cannot use battle plans." The rulings appendix's lower-case reasons become these sentences.
3. **An offer its own rule withheld keeps that reason.** A restriction does not replace Encirclement's force rule, for example.
4. **No plan is asked about.** A restriction's waiver is not consulted at the plan windows. None of this slice's restrictions is waivable.
5. **A Raid targets no site,** so Marsh Spirit never applies to one.
6. **`CampaignPlanSource.ref`** moves the source's option ref into the model, so the walker can key offers without importing the Campaign package.

## File map

| File | Change | Task |
|---|---|---|
| `docs/superpowers/specs/2026-10-09-catalog-batch-6-design.md` | modify | 1 |
| `docs/superpowers/specs/2026-10-09-catalog-batch-6-rulings.md` | modify | 1 |
| `src/main/scala/oathdigital/model/CampaignTypes.scala` | modify | 2, 4 |
| `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlans.scala` | modify | 2 |
| `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanChoice.scala` | modify | 2, 4 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/Encirclement.scala` | modify | 2 |
| `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala` | modify | 2 |
| `src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala` | modify | 2 |
| `src/test/scala/oathdigital/gameplay/powers/campaign/PlanOfferRestrictionSuite.scala` | create | 2 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala` | modify | 3 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/Specialist.scala` | create | 3 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRestrictions.scala` | create | 3, 4, 5, 6, 7 |
| `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala` | modify | 3 |
| `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` | modify | 3, 4, 5, 6, 7 |
| `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala` | modify | 3, 4 |
| `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala` | modify | 5, 7 |
| `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala` | modify | 6 |
| `src/test/scala/oathdigital/gameplay/powers/NoteText.scala` | modify | 3 |
| `src/test/scala/oathdigital/gameplay/powers/campaign/SpecialistSuite.scala` | create | 3 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/SolePlan.scala` | create | 4 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/CodeOfHonor.scala` | create | 4 |
| `src/test/scala/oathdigital/gameplay/powers/campaign/CodeOfHonorSuite.scala` | create | 4 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/MarshSpirit.scala` | create | 5 |
| `src/test/scala/oathdigital/gameplay/powers/campaign/MarshSpiritSuite.scala` | create | 5 |
| `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala` | modify | 5, 6, 7 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/EnemyPlans.scala` | create | 6 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/BeastTamer.scala` | create | 6 |
| `src/test/scala/oathdigital/gameplay/powers/campaign/BeastTamerSuite.scala` | create | 6 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/TrueNames.scala` | create | 7 |
| `src/test/scala/oathdigital/gameplay/powers/campaign/TrueNamesSuite.scala` | create | 7 |
| `docs/ROADMAP.md` | modify | 8 |

### Moving a card out of holding

Tasks 3 to 7 each move a card object from `src/main/scala/oathdigital/catalog/holding/` into its power's file, as earlier batches did:

1. Delete the card's `object ...Card extends Denizen(...)` block from the holding file. The block is the `object` line, its `val power`, its `val powers` and the blank line after it.
2. Paste it unchanged at the top of the power's file. Each task shows the block.
3. In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add the card object to the `import oathdigital.gameplay.powers.campaign.{...}` list, wrapped at 80 columns. The `oathdigital.catalog.holding._` wildcard stops supplying it, so there is no ambiguity.

---

### Task 1: Write the plan-time rulings into the spec

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-6-design.md`
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-6-rulings.md`

- [ ] **Step 1: Amend "The bar" in the design**

Replace the bullet

```markdown
- **Option restrictions over offers** (slice 1). `WalkerPowerGather` applies
  a window's `OptionRestriction`s to the plans an `OfferHost` gathers, and
  `CampaignPlanOffer.withheld` becomes an `OptionReason`.
```

with

```markdown
- **Option restrictions over offers** (slice 1). `WalkerPowerGather` applies
  a window's `OptionRestriction`s to the plans an `OfferHost` gathers, and
  `CampaignPlanOffer.withheld` becomes an `OptionReason`. A plan source
  names its own option (`CampaignPlanSource.ref`), so the walker keys offers
  without importing the Campaign package.
- **Plans used alone** (slice 1, approved at plan time).
  `CampaignPlanOffer.alone` marks a plan its user may use with no other. A
  bandit defender that would apply one applies it and nothing else
  (`CampaignPlanChoice`).
```

- [ ] **Step 2: Add the plan-time notes to slice 1 of the design**

In the design, insert this section before `## Slice 2: Plan rewrites`:

```markdown
### Plan-time rulings

- **Reasons read as the Game Log words them.** A withheld plan's reason is
  its power's note: the card's name, a colon, then the sentence, as
  "Specialist: The defender cannot use battle plans." No slice 1 power
  writes a Game Log line.
- **An offer its own rule withheld keeps that reason.** A restriction does
  not replace Encirclement's force rule.
- **No plan is asked about.** A restriction's waiver is not consulted at the
  plan windows. None of this slice's restrictions is waivable.
- **Bandits and Code of Honor.** A bandit defender that would apply Code of
  Honor applies it and no other plan (FAQ). A Code of Honor another power
  withholds is not applied, and the bandits apply their other plans as
  usual.
- **Where the restrictions live.** `BattlePlan.restrictions` lets a plan's
  power forbid options at a window (Specialist, Code of Honor). Code of
  Honor's pair of restrictions is `SolePlan`, which Peace Envoy reuses in
  slice 2. Beast Tamer and True Names share `EnemyPlans`: the faceup holder
  when they are the enemy of the window's side, and a plan's suit.
```

- [ ] **Step 3: Amend the rulings appendix**

In `docs/superpowers/specs/2026-10-09-catalog-batch-6-rulings.md`, add this bullet at the end of "Rules that apply to the whole batch":

```markdown
- **A reason reads as the Game Log words it:** the card's name, a colon, then
  the sentence. The reasons below are written that way.
```

Then, under "Slice 1: Plan restrictions", rewrite each quoted reason as its sentence: a capital after the colon and a full stop inside the closing quote. The six reasons become:

- "Specialist: The defender cannot use battle plans."
- "Marsh Spirit: Players who target its site cannot use battle plans."
- "Beast Tamer: No Beast or Nomad battle plans."
- "True Names: No battle plans matching {Blue}'s advisers."
- "Code of Honor: Cannot be used with other battle plans."
- "Code of Honor: No other battle plans."

After `Who defends does not matter. The defender's plans are not affected.` in the Marsh Spirit entry, add `A Raid targets no site, so it never applies to one.`

At the end of the Code of Honor entry, add:

```markdown
  A bandit defender that would apply it applies it and no other plan
  (FAQ). When another power withholds it, the bandits apply their other
  plans as usual.
```

Re-wrap the edited paragraphs to 76 columns, as the file is.

- [ ] **Step 4: Check the links and commit**

Run: `python3 scripts/check-markdown-links.py`
Expected: pass.

```bash
git add docs/superpowers/specs/2026-10-09-catalog-batch-6-design.md \
  docs/superpowers/specs/2026-10-09-catalog-batch-6-rulings.md
git commit -m "docs: record catalog batch 6 slice 1 plan-time rulings"
```

---

### Task 2: Option restrictions over offers

**Files:**
- Modify: `src/main/scala/oathdigital/model/CampaignTypes.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlans.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanChoice.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/Encirclement.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/campaign/PlanOfferRestrictionSuite.scala`

**Interfaces:**
- Produces:
  - `CampaignPlanSource#ref: DecisionOptionRef`
  - `CampaignPlanOffer.withheld: Option[OptionReason]`
  - `PlanDriver.Run.query/options/offered/withheld` take an optional last argument `catalogPowers: WalkerPowers`, defaulting to the production catalog.
- Consumes: nothing new.

- [ ] **Step 1: Let the plan driver read a decision built with a suite's own powers**

In `PlanDriver.scala`, `Run.query` rebuilds the parked decision with the production catalog, so a suite that runs `rulesWith` its own powers cannot read its options. Replace `query`, `options`, `offered` and `withheld` with:

```scala
    /** The parked decision's query, as `actor`'s Campaign builds it with
      * `catalogPowers`: the production catalog unless a suite runs its own.
      */
    def query(actor: PlayerId,
        catalogPowers: WalkerPowers = WalkerPowerCatalog.default(catalog))
        : DecisionQuery =
      val current = ready(state)
      val tree = CampaignProcedure.rebuild(catalog, current, actor, Vector.empty)
        .toOption.get
      // The run's own powers: a selected modifier folds only when chosen.
      ProcedureWalker.openDecisions(current, tree,
        current.game.current.walkerPending.get,
        WalkerPowers.selected(catalogPowers,
          current.game.current.walkerModifiers)).head.query

    /** The options the parked choice shows. */
    def options(actor: PlayerId,
        catalogPowers: WalkerPowers = WalkerPowerCatalog.default(catalog))
        : Vector[DecisionOption] = query(actor, catalogPowers) match
      case DecisionQuery.ChooseOne(options, _) => options
      case _ => Vector.empty

    /** The options the parked choice offers that may be chosen. */
    def offered(actor: PlayerId,
        catalogPowers: WalkerPowers = WalkerPowerCatalog.default(catalog))
        : Vector[DecisionOptionRef] =
      options(actor, catalogPowers).filter(_.available).map(_.ref)

    /** Why the parked choice shows `ref` without offering it. */
    def withheld(actor: PlayerId, ref: DecisionOptionRef,
        catalogPowers: WalkerPowers = WalkerPowerCatalog.default(catalog))
        : Option[OptionReason] =
      options(actor, catalogPowers).collectFirst {
        case shown: DecisionOption.Unavailable if shown.ref == ref =>
          shown.reason
      }
```

`offers` keeps calling `offered(actor)`. No import changes.

- [ ] **Step 2: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/PlanOfferRestrictionSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.CatalogNames

/** The plan window runs its option restrictions over the plans it is
  * offered (catalog batch 6, "Option restrictions over offers"): a forbidden
  * plan is withheld with the restriction's note, or dropped without one.
  */
class PlanOfferRestrictionSuite extends munit.FunSuite:
  private val longbows = CatalogNames.denizen("Longbows")
  private val longbowsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(longbows)
  private val encirclement = CatalogNames.denizen("Encirclement")
  private val encirclementRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(encirclement)
  private val title: DecisionOptionRef = DecisionOptionRef.Button("title")

  /** "Not this plan." */
  private val barred = NoteKey("barred",
    Vector(NotePart.Text("Not this plan.")))

  /** Forbids the defender's plan `forbidden`, noting why when `noted`. */
  private final case class ForbidsDefenderPlan(forbidden: DecisionOptionRef,
      noted: Boolean) extends ContributingPower:
    val id: PowerId = PowerId("test.forbids-defender-plan")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    override def noteKeys: Vector[NoteKey] = Vector(barred)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.CampaignDefenderBattlePlans -> Vector(OptionRestriction(
        (_, ref) => Option.when(ref == forbidden)(
          OathViolation.CampaignUnavailable("forbidden in this suite")),
        (_, ref) => Option.when(noted && ref == forbidden)(
          barred(PowerSourceRef.Card(longbows))))))

  /** The title's defense, Longbows and Encirclement, with `rule`. */
  private def plansWith(rule: ContributingPower): Vector[ContributingPower] =
    Vector(TitleDefensePlan.plan, Longbows, Encirclement, rule)

  /** The rival rules the origin with two warbands and the Oathkeeper title,
    * whose defense keeps their window asked, and holds Longbows faceup. */
  private def rivalDefendsWithLongbows: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    withAdviserFor(base, base.rival, longbows, Orientation.FaceUp)

  /** A Conquest of `b` with `powers`, at the defender's plan window. */
  private def defenderWindow(b: Board, powers: Vector[ContributingPower],
      force: Int = 3): Run =
    commit(rulesWith(powers, winningDice), b, force)
      .finishAttackerPlans(b.actor)

  test("a restriction with a note withholds the plan with that note"):
    val b = rivalDefendsWithLongbows
    val rule = ForbidsDefenderPlan(longbowsRef, noted = true)
    val powers = plansWith(rule)
    val run = defenderWindow(b, powers)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assertEquals(run.withheld(b.actor, longbowsRef, WalkerPowers(powers)),
      Some(OptionReason.Noted(rule.id,
        barred(PowerSourceRef.Card(longbows)))))
    assert(run.offered(b.actor, WalkerPowers(powers)).contains(title))

  test("a restriction without a note drops the plan"):
    val b = rivalDefendsWithLongbows
    val powers = plansWith(ForbidsDefenderPlan(longbowsRef, noted = false))
    val shown = defenderWindow(b, powers)
      .options(b.actor, WalkerPowers(powers)).map(_.ref)
    assert(!shown.contains(longbowsRef))
    assert(shown.contains(title))

  test("a forbidden plan cannot be chosen"):
    val b = rivalDefendsWithLongbows
    val run = defenderWindow(b,
      plansWith(ForbidsDefenderPlan(longbowsRef, noted = true)))
    assert(run.refused(b.rival, CampaignIds.defenderPlan,
      ChooseOneAnswer(longbowsRef)).nonEmpty)

  test("a plan its own rule withheld keeps that reason"):
    // Two warbands committed against the rival's two: the forces are equal.
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withAdviserFor(base, base.rival, encirclement,
      Orientation.FaceUp))(_.favor(base.rival, 1))
    val powers = plansWith(ForbidsDefenderPlan(encirclementRef, noted = true))
    val run = defenderWindow(b, powers, force = 2)
    assertEquals(run.withheld(b.actor, encirclementRef, WalkerPowers(powers)),
      Some(OptionReason.Written(Encirclement.SmallerForce)))

  test("a bandit defender does not apply a forbidden plan"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, longbows)
    val applied = ModifyDicePool(CampaignPlans.appliedMarker(longbowsRef), 1)
    def ops(powers: Vector[ContributingPower]): Vector[CoreOperation] =
      commit(rulesWith(powers, winningDice), b, 3).finishPlanWindows.ops
    assert(ops(Vector(Longbows)).contains(applied))
    assert(!ops(plansWith(ForbidsDefenderPlan(longbowsRef, noted = true)))
      .contains(applied))
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.PlanOfferRestrictionSuite"`
Expected: 4 failures. The first three find Longbows offered, and the last finds the bandits applying it. "a plan its own rule withheld keeps that reason" passes already; it guards rule 3.

- [ ] **Step 4: Name a plan source's option in the model**

In `src/main/scala/oathdigital/model/CampaignTypes.scala`, replace the `CampaignPlanSource` trait and object with:

```scala
sealed trait CampaignPlanSource extends Product with Serializable:
  def stableKey: String
  /** How a decision option names the source: its card, or the title's
    * button. */
  def ref: DecisionOptionRef
object CampaignPlanSource:
  final case class Adviser(playerId: PlayerId, id: DenizenId)
      extends CampaignPlanSource:
    def stableKey: String = s"adviser:${playerId.value}:denizen:${id.value}"
    def ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  final case class SiteCard(siteId: SiteId, id: DenizenId)
      extends CampaignPlanSource:
    def stableKey: String = s"site-card:${siteId.value}:denizen:${id.value}"
    def ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  final case class SiteEdifice(siteId: SiteId, id: EdificeId)
      extends CampaignPlanSource:
    def stableKey: String = s"site-edifice:${siteId.value}:edifice:${id.value}"
    def ref: DecisionOptionRef = DecisionOptionRef.Edifice(id)
  final case class Relic(playerId: PlayerId, id: RelicId)
      extends CampaignPlanSource:
    def stableKey: String = s"relic:${playerId.value}:${id.value}"
    def ref: DecisionOptionRef = DecisionOptionRef.Relic(id)
  final case class Title(playerId: PlayerId) extends CampaignPlanSource:
    def stableKey: String = s"title:${playerId.value}"
    def ref: DecisionOptionRef = DecisionOptionRef.Button("title")
```

In the `CampaignPlanOffer` doc comment, replace the paragraph

```scala
  * `withheld` is why the plan stands but cannot be used now, such as
  * Encirclement's force rule: the plan window shows it unavailable with that
  * reason, and no one applies it.
```

with

```scala
  * `withheld` is why the plan stands but cannot be used now: its own rule,
  * such as Encirclement's force rule, or another power's restriction, which
  * the walker writes in when it gathers the window. The plan window shows it
  * unavailable with that reason, and no one applies it.
```

and change the last parameter to `withheld: Option[OptionReason] = None)`.

- [ ] **Step 5: Delegate `CampaignPlans.refOf`**

In `CampaignPlans.scala`, replace the body of `refOf` so it reads:

```scala
  /** What a decision option names a source by: the card, or the title's button. */
  def refOf(source: CampaignPlanSource): DecisionOptionRef = source.ref
```

- [ ] **Step 6: Take the reason as it is in the plan window**

In `CampaignPlanChoice.userPass`, replace

```scala
    def unavailable(offered: OfferedPlan, reason: String): DecisionOption =
      DecisionOption.Unavailable(CampaignPlans.optionOf(offered),
        OptionReason.Written(reason))
```

with

```scala
    def unavailable(offered: OfferedPlan, reason: OptionReason)
        : DecisionOption =
      DecisionOption.Unavailable(CampaignPlans.optionOf(offered), reason)
```

and replace

```scala
          case Left(violation) =>
            unavailable(offered, PlanPrice.shortfall(pass.state, user,
              offered.offer, violation)))
```

with

```scala
          case Left(violation) =>
            unavailable(offered, OptionReason.Written(PlanPrice.shortfall(
              pass.state, user, offered.offer, violation))))
```

- [ ] **Step 7: Write Encirclement's reason as written**

In `Encirclement.scala`, replace

```scala
      else offer.copy(withheld = Some(Encirclement.SmallerForce))
```

with

```scala
      else offer.copy(withheld =
        Some(OptionReason.Written(Encirclement.SmallerForce)))
```

- [ ] **Step 8: Restrict the offers in the walker**

In `WalkerPowerGather.scala`, in `applyWindow`'s doc comment, replace

```scala
    * When the node is an [[OfferHost]], the offers the window gathered are
    * turned into its children after the transforms have run.
```

with

```scala
    * When the node is an [[OfferHost]], the offers the window gathered, with
    * its option restrictions run over them ([[restrictOffers]]), are turned
    * into its children after the transforms have run.
```

Replace the `OfferHost` case

```scala
          case host: OfferHost =>
            val offered = gathered.offers.flatMap { case (powerId, offer) =>
              offer.plan(ctxFor(byId(powerId))).map(OfferedPlan(powerId, _))
            }
```

with

```scala
          case host: OfferHost =>
            val offered = restrictOffers(
              gathered.offers.flatMap { case (powerId, offer) =>
                offer.plan(ctxFor(byId(powerId))).map(OfferedPlan(powerId, _))
              }, gathered.optionRestrictions, ctxFor, byId)
```

and keep the rest of the case as it is. Add this method after `optionRefs`:

```scala
  /** The offers an [[OfferHost]] gathered, with the window's option
    * restrictions run over each by its source's option (catalog batch 6,
    * "Option restrictions over offers"). A forbidden offer is withheld with
    * the first forbidding restriction's note, or dropped when that one has
    * none, as a forbidden `Decide` option is. An offer its own rule already
    * withheld keeps its reason. No plan is asked about, so a waiver is not
    * consulted.
    */
  private def restrictOffers(offers: Vector[OfferedPlan],
      restrictions: Vector[(PowerId, OptionRestriction)],
      ctxFor: ContributingPower => PowerCtx,
      byId: Map[PowerId, ContributingPower]): Vector[OfferedPlan] =
    if restrictions.isEmpty then offers
    else offers.flatMap { offered =>
      if offered.offer.withheld.nonEmpty then Vector(offered)
      else
        val ref = offered.offer.source.ref
        restrictions.iterator.flatMap { case (id, restriction) =>
          val ctx = ctxFor(byId(id))
          restriction.fn(ctx, ref).map(Forbidding(id, restriction, ctx, _))
        }.nextOption() match
          case None => Vector(offered)
          case Some(first) => first.restriction.note(first.ctx, ref).toVector
            .map(note => offered.copy(offer = offered.offer.copy(
              withheld = Some(OptionReason.Noted(first.power, note)))))
    }
```

The file already imports everything this uses.

- [ ] **Step 9: Run the suite and the Campaign suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.Campaign*"`
Expected: PASS, the new suite included.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/model/CampaignTypes.scala \
  src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlans.scala \
  src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanChoice.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/Encirclement.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/PlanOfferRestrictionSuite.scala
git commit -m "feat(walker): run option restrictions over battle plan offers"
```

---

### Task 3: Specialist

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/Specialist.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRestrictions.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/NoteText.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/campaign/SpecialistSuite.scala`

**Interfaces:**
- Consumes: Task 2's restrictions over offers.
- Produces:
  - `BattlePlan#restrictions: Map[PowerWindow, Vector[OptionRestriction]]`, empty by default.
  - `PlanRestrictions.forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower]`
  - `NoteText.sentence(keys: Vector[NoteKey], note: PowerNote): String`, now public.
  - `Specialist.silenced: NoteKey`

- [ ] **Step 1: Make the test sentence helper public**

In `src/test/scala/oathdigital/gameplay/powers/NoteText.scala`, replace

```scala
  private def sentence(keys: Vector[NoteKey], note: PowerNote): String =
```

with

```scala
  /** `note` as the plain sentence its key in `keys` words. */
  def sentence(keys: Vector[NoteKey], note: PowerNote): String =
```

- [ ] **Step 2: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/SpecialistSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Specialist: two favor placed so that the defender cannot use battle
  * plans. */
class SpecialistSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Specialist")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val longbows = CatalogNames.denizen("Longbows")
  private val longbowsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(longbows)

  /** `b`, with the actor holding Specialist faceup and two favor. */
  private def withSpecialist(b: Board): Board =
    on(withAdviser(b, card, Orientation.FaceUp))(_.favor(b.actor, 2))

  /** The rival rules the origin with two warbands and the Oathkeeper title,
    * whose defense keeps their window asked, and holds Longbows faceup. The
    * actor holds Specialist faceup and two favor. */
  private def specialistAgainstLongbows: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    withSpecialist(withAdviserFor(base, base.rival, longbows,
      Orientation.FaceUp))

  test("the attacker places two favor, and the defender is not asked"):
    val b = specialistAgainstLongbows
    val picked = commit(rules(winningDice), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(Look(picked.state).adviserTokens(b.actor, card), Tokens(2, 0))
    assert(!awaits(picked, b.rival, CampaignIds.defenderPlan))
    assert(!picked.finishPlanWindows.ops.contains(
      ModifyDicePool(CampaignIds.attackPool, -1)))
    assertEquals(said(Specialist, picked.events), Vector.empty)

  test("the defender is asked when the attacker does not choose it"):
    val b = specialistAgainstLongbows
    val run = commit(rules(winningDice), b, 3).finishAttackerPlans(b.actor)
    assert(run.offers(b.actor, b.rival, CampaignIds.defenderPlan,
      longbowsRef))

  test("a bandit defender applies no plan"):
    // Longbows stands at the origin, which the bandits rule.
    val base = board(warbands = 5, supply = 7)
    val b = withSpecialist(withSiteCard(base, base.origin, longbows))
    val run = commit(rules(winningDice), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assert(!run.ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(longbowsRef), 1)))

  test("it is the attacker's plan only"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.favor(base.rival, 2))
    val run = commit(rules(winningDice), b, 3).finishAttackerPlans(b.actor)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assert(!run.options(b.actor).map(_.ref).contains(ref))

  test("its reason reads as ruled"):
    assertEquals(NoteText.sentence(Specialist.noteKeys,
      Specialist.silenced(PowerSourceRef.Card(card))),
      "The defender cannot use battle plans.")
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.SpecialistSuite"`
Expected: compilation failure, `Not found: Specialist`.

- [ ] **Step 4: Let a battle plan restrict options**

In `BattlePlan.scala`:

1. Change the import to `import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Offer, OptionRestriction, Transform}`, wrapped at 80 columns.
2. In the trait's doc comment, after the `cardRef` bullet, add:

```scala
  *  - `restrictions` are the options the plan's power forbids at a window,
  *    whether or not the plan was chosen. A restriction that holds only once
  *    the plan is used reads the answered plan picks (Specialist).
```

3. After `wrapping`, add:

```scala
  /** The options this plan's power forbids at a window. */
  def restrictions: Map[PowerWindow, Vector[OptionRestriction]] = Map.empty
```

4. In `contributions`, replace the final expression

```scala
    (offers.keySet ++ afterwards.keySet ++ around.keySet).map(window =>
      window -> (offers.getOrElse(window, Vector.empty) ++
        afterwards.getOrElse(window, Vector.empty) ++
        around.getOrElse(window, Vector.empty))).toMap
```

with

```scala
    (offers.keySet ++ afterwards.keySet ++ around.keySet ++
      restrictions.keySet).map(window =>
      window -> (offers.getOrElse(window, Vector.empty) ++
        afterwards.getOrElse(window, Vector.empty) ++
        around.getOrElse(window, Vector.empty) ++
        restrictions.getOrElse(window, Vector.empty))).toMap
```

- [ ] **Step 5: Write Specialist**

Delete `SpecialistCard` from `HoldingOrderDenizens.scala` (see "Moving a card out of holding"). Create `src/main/scala/oathdigital/gameplay/powers/campaign/Specialist.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignAnswers, CampaignIds}
import oathdigital.gameplay.powerresolver.{OptionRestriction, PowerCtx}
import oathdigital.model._

object SpecialistCard extends Denizen(DenizenId("114"), "Specialist", Suit.Order):
  val power = PrintedPower(PowerId("denizen.specialist"),
    persistent = false, cost = Cost(favor = 2),
    text = "The defender **cannot** use battle plans.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Specialist (card 114), an attacker's battle plan: "[favor][favor] The
  * defender cannot use battle plans."
  *
  * Two favor are placed onto the card. Once it is chosen, every plan of the
  * defender, the title's included, is withheld with "Specialist: The
  * defender cannot use battle plans.", so the defender is not asked, and a
  * bandit defender applies nothing. The restriction reads the attacker's
  * answered plan picks, so it holds only in a Campaign that chose it.
  */
case object Specialist extends BattlePlan:
  val cardId: DenizenId = SpecialistCard.id
  val id: PowerId = SpecialistCard.power.id
  val Favor: Int = 2
  /** "The defender cannot use battle plans." */
  val silenced: NoteKey = NoteKey("silenced",
    Vector(NotePart.Text("The defender cannot use battle plans.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = Vector(Specialist.silenced)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Specialist: the defender cannot use battle plans",
      Vector(CampaignPlanCost.Favor(Specialist.Favor)), Vector.empty))

  override def restrictions: Map[PowerWindow, Vector[OptionRestriction]] =
    Map(PowerWindow.CampaignDefenderBattlePlans -> Vector(OptionRestriction(
      (ctx, _) => Option.when(chosen(ctx))(OathViolation.CampaignUnavailable(
        "Specialist keeps the defender from using battle plans")),
      (ctx, _) => Option.when(chosen(ctx))(
        Specialist.silenced(PowerSourceRef.Card(cardId))))))

  /** Whether the attacker chose this plan in this Campaign. */
  private def chosen(ctx: PowerCtx): Boolean = CampaignAnswers.picks(
    PendingTree(Vector.empty, ctx.answered), CampaignIds.attackerPlan)
    .contains(cardRef)
```

Add `SpecialistCard` to the campaign import in `NewFoundations.scala`.

- [ ] **Step 6: Register it**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRestrictions.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans and rules that restrict which plans a side of a
  * Campaign may choose (catalog batch 6, slice 1), registered together:
  * Specialist. Each names its card, so all are always present.
  */
object PlanRestrictions:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector(Specialist)
```

In `WalkerPowerCatalog.scala`, add `PlanRestrictions` to the `oathdigital.gameplay.powers.campaign` import, and add `PlanRestrictions.forCatalog(catalog) ++` on the line after `SimplePlans.forCatalog(catalog) ++`.

`catalog` is unused until Task 6. The build does not warn on unused parameters.

- [ ] **Step 7: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/Specialist.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRestrictions.scala \
  src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/NoteText.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/SpecialistSuite.scala
git commit -m "feat(powers): Specialist keeps the defender from using battle plans"
```

---

### Task 4: Code of Honor

**Files:**
- Modify: `src/main/scala/oathdigital/model/CampaignTypes.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanChoice.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/SolePlan.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/CodeOfHonor.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRestrictions.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/campaign/CodeOfHonorSuite.scala`

**Interfaces:**
- Consumes: `BattlePlan#restrictions` (Task 3), `NoteText.sentence` (Task 3).
- Produces:
  - `CampaignPlanOffer.alone: Boolean`, false by default.
  - `SolePlan.restrictions(plan: DecisionOptionRef, sides: Set[CampaignPlanSide], source: PowerSourceRef, notWithOthers: NoteKey, noOthers: NoteKey): Map[PowerWindow, Vector[OptionRestriction]]`. Slice 2's Peace Envoy reuses it.
  - `CodeOfHonor.notWithOthers: NoteKey`, `CodeOfHonor.noOthers: NoteKey`

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/CodeOfHonorSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** Code of Honor: two attack dice added or removed, used with no other
  * battle plan. */
class CodeOfHonorSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Code of Honor")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val longbows = CatalogNames.denizen("Longbows")
  private val longbowsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(longbows)
  private val outriders = CatalogNames.denizen("Outriders")
  private val source = PowerSourceRef.Card(card)

  /** The actor holds Code of Honor, Longbows and Outriders faceup: three
    * free attacker plans. Two bandits rule the origin. */
  private def threeFreeAttackerPlans: Board =
    Vector(card, longbows, outriders).foldLeft(
      board(warbands = 5, supply = 7))((b, id) =>
      withAdviser(b, id, Orientation.FaceUp))

  test("an attacker adds two attack dice and may then choose no other plan"):
    val b = threeFreeAttackerPlans
    val run = commit(rules(winningDice), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(
      ModifyDicePool(CampaignIds.attackPool, 2)))
    // Longbows and Outriders are withheld, so nothing is left to ask.
    assert(!awaits(picked, b.actor, CampaignIds.attackerPlan))
    assert(!picked.finishPlanWindows.ops.contains(
      ModifyDicePool(CampaignIds.attackPool, 1)))
    assertEquals(said(CodeOfHonor, picked.events), Vector.empty)

  test("it is withheld once its user has chosen another plan"):
    val b = threeFreeAttackerPlans
    val picked = commit(rules(winningDice), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, longbowsRef)
    assert(picked.offers(b.actor, b.actor, CampaignIds.attackerPlan,
      DecisionOptionRef.Denizen(outriders)))
    assertEquals(picked.withheld(b.actor, ref), Some(OptionReason.Noted(
      CodeOfHonor.id, CodeOfHonor.notWithOthers(source))))

  test("a defender removes two attack dice"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withAdviserFor(base, base.rival, card, Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 3).finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))

  test("a bandit defender applies it and no other plan"):
    // Both stand at the origin, which the bandits rule; both are free.
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(withSiteCard(base, base.origin, card), base.origin,
      longbows)
    val ops = commit(rules(winningDice), b, 3).finishPlanWindows.ops
    assert(ops.contains(ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)))
    assert(!ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(longbowsRef), 1)))

  test("its reasons read as ruled"):
    assertEquals(NoteText.sentence(CodeOfHonor.noteKeys,
      CodeOfHonor.notWithOthers(source)),
      "Cannot be used with other battle plans.")
    assertEquals(NoteText.sentence(CodeOfHonor.noteKeys,
      CodeOfHonor.noOthers(source)), "No other battle plans.")
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.CodeOfHonorSuite"`
Expected: compilation failure, `Not found: CodeOfHonor`.

- [ ] **Step 3: Mark a plan used alone**

In `CampaignTypes.scala`, add this paragraph at the end of the `CampaignPlanOffer` doc comment, before `*/`:

```scala
  *
  * `alone` marks a plan its user may use with no other (Code of Honor). A
  * player's window withholds the rest through the power's own restrictions.
  * A bandit defender, which chooses nothing, applies such a plan and no
  * other when it would apply it (FAQ).
```

and change the last parameter line to

```scala
    withheld: Option[OptionReason] = None, alone: Boolean = false)
```

In `CampaignPlanChoice.scala`, replace `banditPlans` with:

```scala
  /** Bandits pay nothing, so a plan that costs is never applied, and neither is
    * one a power makes unpayable for them (a surcharge in secrets, which bandits
    * do not hold). A plan used alone (Code of Honor) is applied with no other
    * when it would be applied.
    */
  private def banditPlans(setup: CampaignSetup, offers: Vector[OfferedPlan],
      pass: OfferHost.Pass): Vector[Operation] =
    val applied = offers
      .filter(offered => offered.offer.costs.isEmpty &&
        offered.offer.withheld.isEmpty)
      .map(applicationOf(setup, _))
      .filter(application => pass.applies(application).isRight)
    applied.find(_.offered.offer.alone).fold(applied)(Vector(_))
```

- [ ] **Step 4: Write the shared restrictions of a plan used alone**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/SolePlan.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.actions.campaign.{CampaignAnswers, CampaignIds}
import oathdigital.gameplay.powerresolver.{OptionRestriction, PowerCtx}
import oathdigital.model._

/** The restrictions of a battle plan its user may use with no other (Code
  * of Honor; Peace Envoy in slice 2), at the window of each side in
  * `sides`. The plan is withheld once its user has chosen another plan in
  * the window, with `notWithOthers`. Once it is chosen, the user's other
  * plans are withheld, with `noOthers`. Both read the side's answered plan
  * picks. A bandit defender chooses nothing; its offer's `alone` flag covers
  * it.
  */
private[campaign] object SolePlan:
  def restrictions(plan: DecisionOptionRef, sides: Set[CampaignPlanSide],
      source: PowerSourceRef, notWithOthers: NoteKey, noOthers: NoteKey)
      : Map[PowerWindow, Vector[OptionRestriction]] =
    sides.toVector.map { side =>
      def blocked(ctx: PowerCtx, ref: DecisionOptionRef): Option[NoteKey] =
        val picks = CampaignAnswers.picks(
          PendingTree(Vector.empty, ctx.answered),
          CampaignIds.planDecision(side))
        if ref == plan then Option.when(picks.exists(_ != plan))(notWithOthers)
        else Option.when(picks.contains(plan))(noOthers)
      BattlePlan.windowOf(side) -> Vector(OptionRestriction(
        (ctx, ref) => blocked(ctx, ref).map(_ =>
          OathViolation.CampaignUnavailable(
            "a battle plan used alone was combined with another")),
        (ctx, ref) => blocked(ctx, ref).map(key => key(source))))
    }.toMap
```

- [ ] **Step 5: Write Code of Honor**

Delete `CodeOfHonorCard` from `HoldingOrderDenizens.scala`. Create `src/main/scala/oathdigital/gameplay/powers/campaign/CodeOfHonor.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.powerresolver.OptionRestriction
import oathdigital.model._

object CodeOfHonorCard extends Denizen(DenizenId("103"), "Code of Honor", Suit.Order):
  val power = PrintedPower(PowerId("denizen.code-of-honor"),
    persistent = false, cost = Cost.free,
    text = "±2 [attack-die] but you **cannot** use other battle plans.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Code of Honor (card 103), a battle plan for either side: "±2 [attack-die]
  * but you cannot use other battle plans."
  *
  * It is free. An attacker adds two attack dice; a defender removes two. It
  * is withheld once its user has chosen another plan in the window, with
  * "Code of Honor: Cannot be used with other battle plans.". Once it is
  * chosen, the user's other plans are withheld, with "Code of Honor: No
  * other battle plans." (`SolePlan`). A bandit defender that would apply it
  * applies it and no other plan (FAQ).
  */
case object CodeOfHonor extends BattlePlan:
  val cardId: DenizenId = CodeOfHonorCard.id
  val id: PowerId = CodeOfHonorCard.power.id
  val Dice: Int = 2
  /** "Cannot be used with other battle plans." */
  val notWithOthers: NoteKey = NoteKey("not-with-others",
    Vector(NotePart.Text("Cannot be used with other battle plans.")))
  /** "No other battle plans." */
  val noOthers: NoteKey = NoteKey("no-others",
    Vector(NotePart.Text("No other battle plans.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] =
    Vector(CodeOfHonor.notWithOthers, CodeOfHonor.noOthers)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      PlanDice.label("Code of Honor", context.side, CodeOfHonor.Dice),
      Vector.empty, Vector(PlanDice.effect(context.side, CodeOfHonor.Dice)),
      alone = true))

  override def restrictions: Map[PowerWindow, Vector[OptionRestriction]] =
    SolePlan.restrictions(cardRef, sides, PowerSourceRef.Card(cardId),
      CodeOfHonor.notWithOthers, CodeOfHonor.noOthers)
```

Add `CodeOfHonorCard` to the campaign import in `NewFoundations.scala`. In `PlanRestrictions.scala`, change the registered vector to `Vector(Specialist, CodeOfHonor)` and the doc comment's list to `Specialist and Code of Honor`.

- [ ] **Step 6: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.Campaign*"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/model/CampaignTypes.scala \
  src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlanChoice.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/SolePlan.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/CodeOfHonor.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRestrictions.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/CodeOfHonorSuite.scala
git commit -m "feat(powers): Code of Honor is used with no other battle plan"
```

---

### Task 5: Marsh Spirit

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/MarshSpirit.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRestrictions.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/campaign/MarshSpiritSuite.scala`

**Interfaces:**
- Consumes: Task 2's restrictions over offers; `NoteText.sentence` (Task 3).
- Produces: `MarshSpirit.mired: NoteKey`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/MarshSpiritSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** Marsh Spirit: players who target its site cannot use battle plans. */
class MarshSpiritSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Marsh Spirit")
  private val longbows = CatalogNames.denizen("Longbows")
  private val longbowsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(longbows)
  private val outriders = CatalogNames.denizen("Outriders")

  /** `b`, with the actor holding Longbows faceup and Marsh Spirit standing
    * at `site`. */
  private def longbowsWithMarshAt(b: Board, site: SiteId): Board =
    withSiteCard(withAdviser(b, longbows, Orientation.FaceUp), site, card)

  test("an attacker who targets its site is not asked for a plan"):
    val base = board(warbands = 5, supply = 7)
    val b = longbowsWithMarshAt(base, base.origin)
    val run = commit(rules(winningDice), b, 3)
    assert(!awaits(run, b.actor, CampaignIds.attackerPlan))
    val ended = run.finishPlanWindows
    assert(!ended.ops.contains(ModifyDicePool(CampaignIds.attackPool, 1)))
    assertEquals(said(MarshSpirit, ended.events), Vector.empty)

  test("a site that is not targeted withholds nothing"):
    val base = board(extras = 1, warbands = 5, supply = 7)
    val b = longbowsWithMarshAt(base, base.extras.head)
    assert(commit(rules(winningDice), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, longbowsRef))

  test("an optional target counts"):
    val base = board(extras = 1, warbands = 5, supply = 7)
    val extra = base.extras.head
    val b = longbowsWithMarshAt(base, extra)
    val run = commit(rules(winningDice), b, 3,
      targets = Vector(DecisionOptionRef.Site(extra)))
    assert(!awaits(run, b.actor, CampaignIds.attackerPlan))

  test("the defender's plans are not affected"):
    // The rival rules the origin, where Marsh Spirit stands, and holds the
    // title and Longbows; the actor holds Outriders.
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withSiteCard(withAdviserFor(withAdviser(base, outriders,
      Orientation.FaceUp), base.rival, longbows, Orientation.FaceUp),
      base.origin, card)
    val run = commit(rules(winningDice), b, 3)
    assert(!awaits(run, b.actor, CampaignIds.attackerPlan))
    assert(run.offers(b.actor, b.rival, CampaignIds.defenderPlan,
      longbowsRef))

  test("a Raid targets no site"):
    val base = withEnemyAtOrigin(board(warbands = 5, supply = 7))
    val b = longbowsWithMarshAt(on(base)(_.boardWarbands(base.rival, 1)),
      base.origin)
    assert(commit(rules(winningDice), b, 2, raid = true).offers(b.actor,
      b.actor, CampaignIds.attackerPlan, longbowsRef))

  test("its reason reads as ruled"):
    assertEquals(NoteText.sentence(MarshSpirit.noteKeys,
      MarshSpirit.mired(PowerSourceRef.Card(card))),
      "Players who target its site cannot use battle plans.")
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.MarshSpiritSuite"`
Expected: compilation failure, `Not found: MarshSpirit`.

- [ ] **Step 3: Write Marsh Spirit**

Delete `MarshSpiritCard` from `HoldingBeastDenizens.scala`. Create `src/main/scala/oathdigital/gameplay/powers/campaign/MarshSpirit.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction, PowerCtx}
import oathdigital.model._

object MarshSpiritCard extends Denizen(DenizenId("192"), "Marsh Spirit", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.marsh-spirit"),
    persistent = true, cost = Cost.free,
    text = "Players who target this site **cannot** use battle plans.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Marsh Spirit (card 192, site-only), a persistent site rule: "Players who
  * target this site cannot use battle plans."
  *
  * While the site it stands faceup at is among a Campaign's targets, every
  * attacker plan is withheld with "Marsh Spirit: Players who target its site
  * cannot use battle plans.", so the attacker is not asked. Who defends does
  * not matter, and the defender's plans are not affected. A Raid targets no
  * site, so it never applies to one.
  */
case object MarshSpirit extends ContributingPower:
  val cardId: DenizenId = MarshSpiritCard.id
  val id: PowerId = MarshSpiritCard.power.id
  /** "Players who target its site cannot use battle plans." */
  val mired: NoteKey = NoteKey("mired", Vector(NotePart.Text(
    "Players who target its site cannot use battle plans.")))

  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] = Vector(MarshSpirit.mired)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignAttackerBattlePlans -> Vector(OptionRestriction(
      (ctx, _) => Option.when(targeted(ctx))(OathViolation.CampaignUnavailable(
        "Marsh Spirit keeps players who target its site from battle plans")),
      (ctx, _) => Option.when(targeted(ctx))(
        MarshSpirit.mired(PowerSourceRef.Card(cardId))))))

  /** Whether the Campaign the window `ctx` is gathered for targets the site
    * this card stands faceup at. */
  private def targeted(ctx: PowerCtx): Boolean = PlanContext.of(ctx)
    .exists(context => siteOf(ctx.state).exists(context.targets))

  private def siteOf(ready: ReadyGame): Option[SiteId] =
    ready.game.current.map.sites.collectFirst {
      case (site, state) if state.denizens.exists {
        case card: DenizenState =>
          card.id == cardId && card.orientation == Orientation.FaceUp
        case _ => false
      } => site
    }
```

Add `MarshSpiritCard` to the campaign import in `NewFoundations.scala`. In `PlanRestrictions.scala`, register `Vector(Specialist, CodeOfHonor, MarshSpirit)` and name all three in the doc comment. In `PowerKindsCatalogSuite.scala`, add `"denizen.marsh-spirit"` to the end of `persistentRules`.

- [ ] **Step 4: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.MarshSpiritSuite oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/MarshSpirit.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRestrictions.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/MarshSpiritSuite.scala
git commit -m "feat(powers): Marsh Spirit keeps players who target its site from battle plans"
```

---

### Task 6: Beast Tamer

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/EnemyPlans.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/BeastTamer.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRestrictions.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/campaign/BeastTamerSuite.scala`

**Interfaces:**
- Consumes: Task 2's restrictions over offers; `NoteText.sentence` (Task 3).
- Produces:
  - `EnemyPlans.holderAgainst(ctx: PowerCtx, card: DenizenId): Option[PlayerId]`
  - `EnemyPlans.suitOf(catalog: ExecutableCatalog, ref: DecisionOptionRef): Option[Suit]`
  - `BeastTamer.forCatalog(catalog): BeastTamer`, `BeastTamer.id`, `BeastTamer.tamed: NoteKey`

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/BeastTamerSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** Beast Tamer: its holder's enemy cannot use Beast or Nomad battle plans. */
class BeastTamerSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Beast Tamer")
  private val warTortoise = CatalogNames.denizen("War Tortoise")
  private val warTortoiseRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(warTortoise)
  private val horseArchers = CatalogNames.denizen("Horse Archers")
  private val horseArchersRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(horseArchers)
  private val longbows = CatalogNames.denizen("Longbows")
  private val longbowsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(longbows)
  private val title: DecisionOptionRef = DecisionOptionRef.Button("title")
  private val tamed = Some(OptionReason.Noted(BeastTamer.id,
    BeastTamer.tamed(PowerSourceRef.Card(card))))

  /** The rival rules the origin with two warbands and the Oathkeeper title,
    * and holds War Tortoise (Beast), Horse Archers (Nomad) and Longbows
    * (Order) faceup, and a favor. */
  private def rivalWithBeastNomadAndOrderPlans: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(Vector(warTortoise, horseArchers, longbows).foldLeft(base)((b, id) =>
      withAdviserFor(b, base.rival, id, Orientation.FaceUp)))(
      _.favor(base.rival, 1))

  /** A Conquest of `b`, at the defender's plan window. */
  private def defenderWindow(b: Board): Run =
    commit(rules(winningDice), b, 3).finishAttackerPlans(b.actor)

  test("the defender's Beast and Nomad plans are withheld"):
    val b = withAdviser(rivalWithBeastNomadAndOrderPlans, card,
      Orientation.FaceUp)
    val run = defenderWindow(b)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assertEquals(run.withheld(b.actor, warTortoiseRef), tamed)
    assertEquals(run.withheld(b.actor, horseArchersRef), tamed)
    assert(run.offered(b.actor).contains(longbowsRef))
    assert(run.offered(b.actor).contains(title))

  test("a facedown Beast Tamer withholds nothing"):
    val b = withAdviser(rivalWithBeastNomadAndOrderPlans, card,
      Orientation.FaceDown)
    assert(defenderWindow(b).offered(b.actor).contains(warTortoiseRef))

  test("a bystander's Beast Tamer withholds nothing"):
    val base = rivalWithBeastNomadAndOrderPlans
    val b = withAdviserFor(base, base.bystander, card, Orientation.FaceUp)
    assert(defenderWindow(b).offered(b.actor).contains(horseArchersRef))

  test("against a defending holder, the attacker's plans are withheld"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withAdviser(withAdviser(withAdviserFor(base, base.rival, card,
      Orientation.FaceUp), horseArchers, Orientation.FaceUp), longbows,
      Orientation.FaceUp)
    val run = commit(rules(winningDice), b, 3)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    assertEquals(run.withheld(b.actor, horseArchersRef), tamed)
    assert(run.offered(b.actor).contains(longbowsRef))

  test("the holder's own plans are not withheld"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = withAdviser(withAdviser(base, card, Orientation.FaceUp),
      horseArchers, Orientation.FaceUp)
    assert(commit(rules(winningDice), b, 3).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, horseArchersRef))

  test("a bandit defender does not apply a Nomad plan"):
    // Horse Archers is free and stands at the origin, which bandits rule.
    val base = board(warbands = 5, supply = 7)
    val b = withAdviser(withSiteCard(base, base.origin, horseArchers), card,
      Orientation.FaceUp)
    val ops = commit(rules(winningDice), b, 3).finishPlanWindows.ops
    assert(!ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(horseArchersRef), 1)))

  test("its reason reads as ruled"):
    assertEquals(NoteText.sentence(BeastTamer.noteKeys,
      BeastTamer.tamed(PowerSourceRef.Card(card))),
      "No Beast or Nomad battle plans.")
```

`BeastTamer.noteKeys` is the companion's, which Step 3 defines.

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.BeastTamerSuite"`
Expected: compilation failure, `Not found: BeastTamer`.

- [ ] **Step 3: Write the shared helper and Beast Tamer**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/EnemyPlans.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

/** What the adviser rules against an enemy's battle plans share (Beast
  * Tamer, True Names): the faceup holder, when they are the enemy of the
  * side whose plan window is being gathered, and a plan's suit.
  */
private[campaign] object EnemyPlans:
  /** The player holding `card` faceup as an adviser, when the window `ctx`
    * is gathered for is their enemy's: a defending holder at the attacker's
    * window, an attacking holder at the defender's. */
  def holderAgainst(ctx: PowerCtx, card: DenizenId): Option[PlayerId] =
    PlanContext.of(ctx).flatMap(context =>
      faceUpHolder(ctx.state, card).filter(holder =>
        context.enemy == CampaignDefender.Player(holder)))

  private def faceUpHolder(ready: ReadyGame, card: DenizenId)
      : Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(held, Orientation.FaceUp, _) => held == card
      case _ => false
    }).map(_.player)

  /** The suit of the plan named by `ref`: its source card's. A relic and
    * the title have none. */
  def suitOf(catalog: ExecutableCatalog, ref: DecisionOptionRef)
      : Option[Suit] = ref match
    case DecisionOptionRef.Denizen(id) => catalog.suitOf(id)
    case DecisionOptionRef.Edifice(id) => catalog.suitOf(id)
    case _ => None
```

Delete `BeastTamerCard` from `HoldingDiscordDenizens.scala`. Create `src/main/scala/oathdigital/gameplay/powers/campaign/BeastTamer.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction, PowerCtx}
import oathdigital.model._

object BeastTamerCard extends Denizen(DenizenId("90"), "Beast Tamer", Suit.Discord) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.beast-tamer"),
    persistent = true, cost = Cost.free,
    text = "Enemies cannot use [suit-beast] or [suit-nomad] battle " +
      "plans against you.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Beast Tamer (card 90, adviser-only), a persistent rule of a faceup
  * adviser: "Enemies cannot use [beast] or [nomad] battle plans against
  * you."
  *
  * While its holder is the attacker or the defender, the enemy's Beast and
  * Nomad plans, bandits' included, are withheld with "Beast Tamer: No Beast
  * or Nomad battle plans.". A plan's suit is its source card's
  * (`EnemyPlans.suitOf`); a relic and the title have none.
  */
final case class BeastTamer private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = BeastTamerCard.id
  def id: PowerId = BeastTamer.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] = BeastTamer.noteKeys

  private val barring: OptionRestriction = OptionRestriction(
    (ctx, ref) => Option.when(bars(ctx, ref))(
      OathViolation.CampaignUnavailable(
        "Beast Tamer keeps its holder's enemy from Beast and Nomad plans")),
    (ctx, ref) => Option.when(bars(ctx, ref))(
      BeastTamer.tamed(PowerSourceRef.Card(cardId))))

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignAttackerBattlePlans -> Vector(barring),
    PowerWindow.CampaignDefenderBattlePlans -> Vector(barring))

  /** Whether `ref` is a Beast or Nomad plan of the holder's enemy. */
  private def bars(ctx: PowerCtx, ref: DecisionOptionRef): Boolean =
    EnemyPlans.holderAgainst(ctx, cardId).nonEmpty &&
      EnemyPlans.suitOf(catalog, ref).exists(BeastTamer.Suits)

object BeastTamer:
  val id: PowerId = BeastTamerCard.power.id
  /** The suits of the plans it withholds. */
  val Suits: Set[Suit] = Set(Suit.Beast, Suit.Nomad)
  /** "No Beast or Nomad battle plans." */
  val tamed: NoteKey = NoteKey("tamed",
    Vector(NotePart.Text("No Beast or Nomad battle plans.")))
  val noteKeys: Vector[NoteKey] = Vector(tamed)

  def forCatalog(catalog: ExecutableCatalog): BeastTamer =
    new BeastTamer(catalog)
```

Add `BeastTamerCard` to the campaign import in `NewFoundations.scala`. In `PlanRestrictions.scala`, register `Vector(Specialist, CodeOfHonor, MarshSpirit, BeastTamer.forCatalog(catalog))` and name all four in the doc comment. In `PowerKindsCatalogSuite.scala`, add `"denizen.beast-tamer"` to the end of `persistentRules`.

- [ ] **Step 4: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.BeastTamerSuite oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/EnemyPlans.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/BeastTamer.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRestrictions.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/BeastTamerSuite.scala
git commit -m "feat(powers): Beast Tamer keeps enemies from Beast and Nomad battle plans"
```

---

### Task 7: True Names

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/TrueNames.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRestrictions.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/campaign/TrueNamesSuite.scala`

**Interfaces:**
- Consumes: `EnemyPlans` (Task 6), `CodeOfHonor` (Task 4), `NoteText.sentence` (Task 3).
- Produces: `TrueNames.forCatalog(catalog): TrueNames`, `TrueNames.id`, `TrueNames.named: NoteKey`, `TrueNames.noteKeys`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/TrueNamesSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** True Names: its holder's enemy cannot use battle plans matching any of
  * the holder's faceup advisers. */
class TrueNamesSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("True Names")
  private val warTortoise = CatalogNames.denizen("War Tortoise")
  private val warTortoiseRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(warTortoise)
  private val longbows = CatalogNames.denizen("Longbows")
  private val longbowsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(longbows)
  private val outriders = CatalogNames.denizen("Outriders")
  private val outridersRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(outriders)
  private val encirclement = CatalogNames.denizen("Encirclement")
  private val encirclementRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(encirclement)
  private val horseArchers = CatalogNames.denizen("Horse Archers")
  private val horseArchersRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(horseArchers)
  private val codeOfHonor = CatalogNames.denizen("Code of Honor")
  private val codeOfHonorRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(codeOfHonor)
  private val title: DecisionOptionRef = DecisionOptionRef.Button("title")

  /** The rival rules the origin with two warbands and the Oathkeeper title,
    * and holds War Tortoise (Beast) and Longbows (Order) faceup, and a
    * favor. The actor holds True Names (Beast) faceup and Outriders (Order)
    * facedown. */
  private def trueNamesAgainstBeastAndOrderPlans: Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val rival = on(Vector(warTortoise, longbows).foldLeft(base)((b, id) =>
      withAdviserFor(b, base.rival, id, Orientation.FaceUp)))(
      _.favor(base.rival, 1))
    withAdviser(withAdviser(rival, card, Orientation.FaceUp), outriders,
      Orientation.FaceDown)

  test("the enemy cannot use plans matching the holder's faceup advisers"):
    val b = trueNamesAgainstBeastAndOrderPlans
    val run = commit(rules(winningDice), b, 3).finishAttackerPlans(b.actor)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assertEquals(run.withheld(b.actor, warTortoiseRef),
      Some(OptionReason.Noted(TrueNames.id, TrueNames.named(
        PowerSourceRef.Card(card), NoteArg.Player(b.actor)))))
    assert(run.offered(b.actor).contains(title))

  test("a facedown adviser's suit does not count"):
    // Outriders, an Order card, is facedown, so Longbows is offered.
    val b = trueNamesAgainstBeastAndOrderPlans
    val run = commit(rules(winningDice), b, 3).finishAttackerPlans(b.actor)
    assert(run.offered(b.actor).contains(longbowsRef))

  test("an adviser turned faceup during plan selection counts from the " +
      "next pass"):
    // Choosing the facedown Outriders turns it faceup.
    val b = trueNamesAgainstBeastAndOrderPlans
    val run = commit(rules(winningDice), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, outridersRef)
      .finishAttackerPlans(b.actor)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assert(run.withheld(b.actor, longbowsRef).nonEmpty)

  test("a plan chosen before an adviser turns faceup stays chosen"):
    // The rival defends holding True Names faceup and Encirclement (Order)
    // facedown; the actor chooses Longbows (Order) while the rival shows no
    // Order card, and then the rival's Encirclement is turned faceup.
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withAdviser(withAdviserFor(withAdviserFor(base, base.rival,
      card, Orientation.FaceUp), base.rival, encirclement,
      Orientation.FaceDown), longbows, Orientation.FaceUp))(
      _.favor(base.rival, 1))
    // One warband against the rival's two: Encirclement is usable.
    val chosen = commit(rules(winningDice), b, 1)
      .pick(b.actor, CampaignIds.attackerPlan, longbowsRef)
      .finishAttackerPlans(b.actor)
    val revealed = chosen.pick(b.rival, CampaignIds.defenderPlan,
      encirclementRef)
    val ended = revealed.finishPlanWindows
    assert(ended.ops.contains(ModifyDicePool(CampaignIds.attackPool, 1)))
    assert(ended.ops.contains(ModifyDicePool(CampaignIds.attackPool, -2)))
    assert(ended.winner.nonEmpty)

  test("bandits whose Code of Honor is withheld apply their other plans"):
    // The actor shows True Names (Beast) and Outriders (Order) faceup, so
    // the bandits' Code of Honor (Order) is withheld and their Horse Archers
    // (Nomad) is not.
    val base = board(warbands = 5, supply = 7)
    val sites = withSiteCard(withSiteCard(base, base.origin, codeOfHonor),
      base.origin, horseArchers)
    val b = withAdviser(withAdviser(sites, card, Orientation.FaceUp),
      outriders, Orientation.FaceUp)
    val ops = commit(rules(winningDice), b, 3)
      .finishAttackerPlans(b.actor).finishPlanWindows.ops
    assert(ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(horseArchersRef), 1)))
    assert(!ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(codeOfHonorRef), 1)))

  test("its reason reads as ruled"):
    val b = trueNamesAgainstBeastAndOrderPlans
    assertEquals(NoteText.sentence(TrueNames.noteKeys, TrueNames.named(
      PowerSourceRef.Card(card), NoteArg.Player(b.actor))),
      s"No battle plans matching ${b.actor.value}'s advisers.")
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.TrueNamesSuite"`
Expected: compilation failure, `Not found: TrueNames`.

- [ ] **Step 3: Write True Names**

Delete `TrueNamesCard` from `HoldingBeastDenizens.scala`. Create `src/main/scala/oathdigital/gameplay/powers/campaign/TrueNames.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction, PowerCtx}
import oathdigital.model._

object TrueNamesCard extends Denizen(DenizenId("41"), "True Names", Suit.Beast) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.true-names"),
    persistent = true, cost = Cost.free,
    text = "Your enemy **cannot** use battle plans that match any of " +
      "your advisers against you.")
  val powers: Vector[PrintedPower] = Vector(power)

/** True Names (card 41, adviser-only), a persistent rule of a faceup
  * adviser: "Your enemy cannot use battle plans that match any of your
  * advisers against you."
  *
  * While its holder is the attacker or the defender, every enemy plan whose
  * suit matches one of the holder's faceup advisers, True Names included,
  * is withheld with "True Names: No battle plans matching {Blue}'s
  * advisers.". Facedown advisers do not count, because their suits are
  * hidden. The suits are read at every pass of the window, so an adviser
  * turned faceup during plan selection counts from the next pass, and a
  * plan chosen before that stays chosen.
  */
final case class TrueNames private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = TrueNamesCard.id
  def id: PowerId = TrueNames.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] = TrueNames.noteKeys

  private val naming: OptionRestriction = OptionRestriction(
    (ctx, ref) => matched(ctx, ref).map(_ =>
      OathViolation.CampaignUnavailable(
        "True Names keeps its holder's enemy from matching plans")),
    (ctx, ref) => matched(ctx, ref).map(holder => TrueNames.named(
      PowerSourceRef.Card(cardId), NoteArg.Player(holder))))

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignAttackerBattlePlans -> Vector(naming),
    PowerWindow.CampaignDefenderBattlePlans -> Vector(naming))

  /** The holder, when `ref` is a plan of their enemy whose suit matches one
    * of the holder's faceup advisers. */
  private def matched(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[PlayerId] =
    EnemyPlans.holderAgainst(ctx, cardId).filter(holder =>
      EnemyPlans.suitOf(catalog, ref).exists(suits(ctx.state, holder)))

  /** The suits of `holder`'s faceup advisers. */
  private def suits(ready: ReadyGame, holder: PlayerId): Set[Suit] =
    ready.game.current.players.find(_.player == holder).toVector
      .flatMap(_.advisers.collect {
        case DenizenState(held, Orientation.FaceUp, _) =>
          catalog.suitOf(held)
      }.flatten).toSet

object TrueNames:
  val id: PowerId = TrueNamesCard.power.id
  /** "No battle plans matching {Blue}'s advisers." */
  val named: NoteKey = NoteKey("named", Vector(
    NotePart.Text("No battle plans matching "), NotePart.Arg(0),
    NotePart.Text("'s advisers.")))
  val noteKeys: Vector[NoteKey] = Vector(named)

  def forCatalog(catalog: ExecutableCatalog): TrueNames =
    new TrueNames(catalog)
```

Add `TrueNamesCard` to the campaign import in `NewFoundations.scala`. In `PlanRestrictions.scala`, register `Vector(Specialist, CodeOfHonor, MarshSpirit, BeastTamer.forCatalog(catalog), TrueNames.forCatalog(catalog))` and name all five in the doc comment. In `PowerKindsCatalogSuite.scala`, add `"denizen.true-names"` to the end of `persistentRules`.

- [ ] **Step 4: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.TrueNamesSuite oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: PASS.

If "a plan chosen before an adviser turns faceup stays chosen" fails because the resumed walk refuses the attacker's earlier Longbows answer, stop and report it. The walk is meant to rebuild a chosen plan from its offer whatever the offer's reason (`CampaignPlanChoice`, the second `Branch` of a pass), so a refusal means the earlier pass's decision is re-checked against today's restrictions. That needs a ruling, not a workaround.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/TrueNames.scala \
  src/main/scala/oathdigital/gameplay/powers/campaign/PlanRestrictions.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/campaign/TrueNamesSuite.scala
git commit -m "feat(powers): True Names keeps enemies from plans matching its holder's advisers"
```

---

### Task 8: Record the slice and run the gates

**Files:**
- Modify: `docs/ROADMAP.md` ("Now" paragraph; Phase - Catalog batch 6)

- [ ] **Step 1: Run the gates**

Run, in order:
1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. The server count is the baseline plus 34; the frontend count is unchanged.

- [ ] **Step 2: Update the roadmap**

In `docs/ROADMAP.md`:

- Delete the bullet `- [ ] Slice 1, plan restrictions: Specialist, Marsh Spirit, Beast Tamer, True Names, Code of Honor.` (two lines) under "### Phase - Catalog batch 6".
- In "## Now", append to the paragraph: `**Phase - Catalog batch 6** is in progress: slice 1 is done.`

Run `python3 scripts/check-markdown-links.py` again. Expected: pass.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: record catalog batch 6 slice 1"
```
