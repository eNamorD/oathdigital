# Catalog Batch 3, Slice 1c (Battle Plans: After the Campaign) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement five battle-plan powers: Insect Swarm (184), Book Burning (22), Field Promotion (106), Tribute Spoils (239) and Military Parade (109), with the refactor P3.

**Architecture:**

- **P3.** Gleaming Armor's surcharge moves into a new trait, `PlanSurcharge`. The trait takes the added cost, the line and the title's payment as members. Gleaming Armor and Insect Swarm both extend it. `GleamingArmorSuite` passes unchanged.
- Book Burning, Field Promotion, Tribute Spoils and Military Parade are `BattlePlan`s (`src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`).
  - Each one acts once the Campaign has resolved, through a `later` step at `CampaignActionEligibility`, as Battle Honors does.
  - Their `plan` changes no dice.
- Tribute Spoils and Military Parade share a new helper, `FavorBySuit`. It turns a list of cards into one favor gain per suit bank.
- Registration:
  - Field Promotion, Tribute Spoils and Military Parade pay after the Campaign, as Battle Honors does. They register in `SimplePlans`.
  - Book Burning (a burn at the end) and Insect Swarm (a rule that taxes plans) register in `PlanRules`.
- No engine change: no new operation, window, query, option kind, `NoteArg` kind, protocol or frontend change.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change.

**Spec:** `docs/superpowers/specs/2026-09-29-catalog-batch-3-design.md` ("Powers-side refactors", "Slicing", "Log lines", "Testing", "Verify at plan time"), with the per-card rulings in `docs/superpowers/specs/2026-09-29-catalog-batch-3-rulings.md` ("Rules that apply to the whole batch", "Slice 1: battle plans" and "Slice 1c"). Read both before starting. The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build. Each file below lists the imports it needs.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`).
- Import rules:
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - No power name appears in walker sources.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree:
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- A note template starts with an argument or a capital letter (`PowerNoteCatalogSuite` checks this).
- Log lines, exactly (spec, "Log lines", "Slice 1: battle plans"):
  - Book Burning, key `burned`: "Book Burning: Burned {n secrets} from {Blue}'s board." `{n secrets}` is a `NoteArg.Amount` in `NoteUnit.Secret` of what was burnt. `{Blue}` is the defender.
  - Book Burning, key `none`: "Book Burning: {Blue} had no secret to burn." It is written when the attacker won and nothing was burnt.
  - Field Promotion, key `gained`, when at least one warband was gained: "Field Promotion: {Red} gained {n warbands}." This is `NoteSupport.gainedKey`, as Proving Grounds uses it.
  - Insect Swarm, key `taxed`, after the taxed plan: "Insect Swarm: {Red}'s battle plans cost {1} extra favor, burnt." `{1}` is a `NoteArg.Number`.
  - Tribute Spoils and Military Parade write no line. The generic gain lines show their favor, as for Battle Honors.
- Batch rulings that every task applies (rulings, "Rules that apply to the whole batch" and "Slice 1: battle plans"):
  - **Enemy.** For an attacker's plan, the defender, a player or bandits. For a defender's plan, the attacker (`PlanContext.enemy`).
  - **"If you're victorious".** Resolved at the end of the Campaign, as Battle Honors is. A plan that also names a Campaign kind is offered only in that kind.
  - **Suits.** Only faceup cards have a suit. Relics and banners have none. An edifice has its suit on both faces (`catalog.suitOf`).
  - **All-Exile.** An Empire or Imperial clause does nothing.
  - **Amounts are best effort.** A gain or burn resolves to what its source holds.
  - **Bandit defenders** apply their free plans without choosing, and never use a plan that costs. `CampaignPlanChoice` already does this.
- Baseline: `main` at `5dedf52f` passes 2234 server and 466 frontend tests. Record the count from your first full `./sbtw test` run in the worktree and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

## Rulings made at plan time

These settle what the spec and rulings leave open. Each one names what it costs if it is wrong.

1. **No registration pins.** As in slices 1a and 1b: `PowerKindsCatalogSuite` does not list battle plans, and per-card registration pins were pruned on 2026-09-27. Every suite below runs through `WalkerPowerCatalog.default`, so a power that is not registered is never offered or applied, and its suite fails. No card in this slice has a reviewed stub to retire.
2. **P3 is a trait.** `PlanSurcharge` declares `cardId` and `catalog` as abstract members, which the case class parameters implement. By default the title's plan pays the added cost as a `PayCost` from its user's board. `CampaignPlanApplication` pays the title's own burnt-only cost the same way. Gleaming Armor overrides this, because its cost is a placed secret, which the title turns facedown instead. If the trait is judged too wide, the fallback is two copies of the transform. That fallback is what the spec's "generalised" rules out.
3. **Insect Swarm's burn is a burn off turn too** (spec, "Verify at plan time"). The burnt favor goes to the shared bank whatever the turn:
   - `PayCost.children` moves `cost.favorBurnt` to `Location.SharedBank` in both branches.
   - `PayCostSettlement` asks for a matching bank only for a placed favor (`cost.favor > 0`).
   - `Costs.onCard` builds the `PayCost` from the user's play area.
   
   Task 1's first test pins this: the card's suit bank does not change.
4. **Book Burning flips and burns in one step** (spec, "Verify at plan time"). Its `BuildOps` returns `FlipSecrets(defender, k, FaceDown, FaceUp)` followed by `Burn.secrets(n, PlayArea(defender))`. `OperationPipeline.run` executes the batch's operations one after another, so the burn sees the secrets just turned faceup. Two constraints shape this:
   - A burn takes faceup secrets only (`OperationSecretPlanner`).
   - One composite holding both would fail, because one operation's moves are planned before its flips.
   
   Task 2 pins both the single recorded step and the result.
5. **Book Burning's `none` line** is written when the attacker won and nothing was burnt: the defender held one secret or none. A lost Raid writes nothing. A Raid's defender is always a player, because a Raid targets a co-located enemy pawn, so Book Burning has no bandit case.
6. **The end-of-Campaign reads.** A `later` step reads two things:
   - **State:** `use.ready`, the state the step's `Branch` is selected against, after the losses and the Raid's relocation.
   - **The Campaign:** `use.result`, its recorded `CampaignResult`, with `defender` and `targetSites`.
   
   So Tribute Spoils counts the cards at the targets as they stand at the end, and Military Parade counts the advisers the enemy holds then. Field Promotion and Book Burning read the board inside their `BuildOps`, at the same moment.
7. **Tribute Spoils counts faceup denizens and edifices on either face.** A facedown denizen has no suit and gives nothing. Relics are in `SiteState.relics`, which it never reads.
8. **Military Parade is always offered to a defender.** A defender's enemy is the attacker, a player. An attacker is offered it only against a player. Its gains go through `FavorBySuit.gains`, which moves a bandit defender's favor from each bank to the shared bank, as `BattleHonors.toBandits` does. Battle Honors itself is left unchanged.
9. **Generic lines** (spec, "Verify at plan time", "Generic lines"):
   - `DetailLines` writes "gained {n} favor from the {suit} bank" for `Gain.Favor`, so Tribute Spoils and Military Parade need no line.
   - It writes nothing for `Gain.Warbands`, so Field Promotion's note covers nothing (`covers = false`).
   - A favor move from a bank to the shared bank writes nothing, as for Battle Honors.
   - Book Burning's secret burn adds nothing to the Campaign losses line, which counts burnt favor only.

## File Structure

Production:

| File | Change | Responsibility |
|---|---|---|
| `gameplay/powers/campaign/PlanSurcharge.scala` | Create | P3: an added cost on every plan the holder's enemy chooses |
| `gameplay/powers/campaign/GleamingArmor.scala` | Modify | Extends `PlanSurcharge` |
| `gameplay/powers/campaign/InsectSwarm.scala` | Create | One more favor, burnt, on each enemy plan |
| `gameplay/powers/campaign/BookBurning.scala` | Create | A won Raid burns the defender's secrets but one |
| `gameplay/powers/campaign/FieldPromotion.scala` | Create | Three warbands if victorious |
| `gameplay/powers/campaign/FavorBySuit.scala` | Create | One favor gain per suit bank, for a list of cards |
| `gameplay/powers/campaign/TributeSpoils.scala` | Create | A favor per card at the targets if victorious in a Conquest |
| `gameplay/powers/campaign/MilitaryParade.scala` | Create | A favor per faceup adviser of the enemy if victorious |
| `gameplay/powers/campaign/PlanRules.scala` | Modify | Registers Insect Swarm and Book Burning |
| `gameplay/powers/campaign/SimplePlans.scala` | Modify | Registers Field Promotion, Tribute Spoils and Military Parade |

All production paths are under `src/main/scala/oathdigital/`.

Tests, under `src/test/scala/oathdigital/gameplay/powers/campaign/`:

| File | Change |
|---|---|
| `InsectSwarmSuite.scala` | Create (Task 1) |
| `BookBurningSuite.scala` | Create (Task 2) |
| `FieldPromotionSuite.scala` | Create (Task 3) |
| `TributeSpoilsSuite.scala`, `MilitaryParadeSuite.scala` | Create (Task 4) |

`GleamingArmorSuite.scala` must pass unchanged.

Docs: `docs/ROADMAP.md` (Task 5).

### How the suites drive a Campaign

Read `campaign/PlanDriver.scala` and `src/test/scala/oathdigital/gameplay/CampaignFixture.scala` before writing a suite. In short:

- `board(extras, warbands, supply)`: p1, the actor, stands at the origin (Ancient City, defense 2), which two bandits rule. `extras` further sites are bandit-ruled too. Every other site starts empty and holds no card. p2 (`b.other`) and p3 stand at the first site nobody rules.
- `againstPlayer(b)`: p2 rules the origin with two warbands and holds the Oath title, so p2 is offered the title's defense plan until they use it. That keeps p2's plan window open.
- `withEnemyAtOrigin(b)`: p2 joins the actor at the origin, so a Raid is legal. A won Raid then asks the attacker where the beaten pawn goes (`CampaignIds.relocation`) before the Campaign ends.
- `commit(rules(dice), b, force, targets, raid)` starts the Campaign and answers the kind, the targets and the force. `winning` dice are all swords and `losing` dice all hollow swords; defense dice are blank. Four `winning` dice beat two warbands or a Raid defender with three; four `losing` dice lose to them. Two `losing` dice lose to two bandits.
- `run.pick(who, CampaignIds.attackerPlan | defenderPlan, ref)` chooses a plan. `run.finish` finishes every plan window and answers 0 to a sacrifice or placement, until the Campaign ends or asks something else.
- `run.ops` is every recorded operation. `picked.since(run)` is only what the pick recorded.
- `Run.offered(actor)`, `Run.options(actor)` and `Run.offers(actor, who, id, ref)` rebuild the parked decision with the Campaign's actor, so their first argument is always the actor, even in the defender's window.
- A "not offered" check needs the window open, or it is vacuous. Hold a second plan that is offered (Battle Honors is free for either side), or assert the run skipped straight to `CampaignIds.sacrifice`.
- `inert(suit, n)` gives `n` denizen ids of `suit` whose powers do nothing.
- `ready(run.state).game.current.lastCampaignResult` is the finished Campaign's `CampaignResult`.
- Table steps reached through `on(b)(...)`: `favor(player, n)`, `secrets(player, faceUp, faceDown)`, `warbands(player, n)`, `denizen(card, at, facedown)`, `relicAt(card, at)`, `bankFavor(suit, n)`.

---

### Task 1: `PlanSurcharge` (P3), then Insect Swarm

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanSurcharge.scala`
- Modify (replace the whole file): `src/main/scala/oathdigital/gameplay/powers/campaign/GleamingArmor.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/InsectSwarm.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/InsectSwarmSuite.scala`

**Interfaces:**
- Consumes: `CampaignPlanApplication` (`setup`, `side`, `user`, `source`), `CampaignPlans.cardOf`, `Costs.onCard`, `CatalogResolution.of`, `CatalogCards.denizen`.
- Produces: `trait PlanSurcharge extends ContributingPower` with abstract `cardId: DenizenId`, `catalog: ExecutableCatalog`, `cost: Cost`, `taxed: NoteKey`, `amount: Int`, `unpayable: OathViolation`, and an overridable `onTitle(ready: ReadyGame, user: PlayerId): Either[OathViolation, Vector[CoreOperation]]`. `InsectSwarm.id`, `InsectSwarm.taxed`, `InsectSwarm.Favor`.

- [ ] **Step 1: Create `PlanSurcharge`**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/PlanSurcharge.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.{CampaignPlanApplication, CampaignPlans}
import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.CatalogResolution
import oathdigital.model._

/** A persistent rule of a faceup adviser that adds a cost to every battle plan
  * its holder's enemy chooses (Gleaming Armor, Insect Swarm).
  *
  * While the holder is in a Campaign, as the attacker or as a player defender,
  * every plan the opposing side chooses costs `cost` more, paid like the plan's
  * own cost: onto the plan's source card, and settled at once outside its
  * user's turn. The title has no card, so its plan pays `onTitle` instead. The
  * added cost is part of the plan's application, so a plan the user cannot
  * afford with it is not offered, and the option's price includes it. Bandits
  * are enemies too, but they hold nothing and cannot pay, so while a holder
  * attacks, a bandit defender applies no plan at all.
  *
  * The rule is automatic, so it needs no selection. A facedown copy is not
  * active, and the card is adviser-only, so the holder is found among the
  * players' faceup advisers.
  *
  * Each taxed plan writes `taxed`, naming its user and `amount`, after the
  * plan's own effects, so it never comes before a decision the plan asks. The
  * log drops a line identical to one already posted in the same action, so two
  * taxed plans in one Campaign post one line.
  */
trait PlanSurcharge extends ContributingPower:
  /** The card whose faceup holder taxes the enemy's plans. */
  def cardId: DenizenId
  def catalog: ExecutableCatalog
  /** The added cost of a plan with a source card. */
  protected def cost: Cost
  /** The line each taxed plan writes: its user, then `amount`. */
  protected def taxed: NoteKey
  /** The number the line states. */
  protected def amount: Int
  /** Why a bandit's plan cannot pay the added cost. */
  protected def unpayable: OathViolation

  /** The added cost of the title's plan, which has no card. By default it is
    * paid from the user's board, as the title's own burnt cost is
    * (`CampaignPlanApplication`), which suits a cost with no placed portion. */
  protected def onTitle(ready: ReadyGame, user: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    Right(Vector[CoreOperation](PayCost(user, Location.PlayArea(user), cost)))

  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  override def noteKeys: Vector[NoteKey] = Vector(taxed)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignPlanApplication -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case application: CampaignPlanApplication =>
          surcharge(ctx, application).fold(children)(paid =>
            paid +: children :+ note(application))
        case _ => children
      })))

  /** Its line, naming the plan's user; a bandit plan has none and pays
    * nothing. */
  private def note(application: CampaignPlanApplication): Note =
    Note(id, _ => application.user.map(user => taxed(
      PowerSourceRef.Card(cardId), NoteArg.Player(user), NoteArg.Number(amount))))

  private def holder(ctx: PowerCtx): Option[PlayerId] =
    ctx.state.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

  /** The added cost, when the plan is the enemy's of a holder in this Campaign. */
  private def surcharge(ctx: PowerCtx, application: CampaignPlanApplication)
      : Option[Operation] = for
    holding <- holder(ctx)
    if enemy(application, holding)
  yield application.user.fold[Operation](
    BuildOps((_, _) => Left(unpayable)))(user =>
    BuildOps((ready, _) => CampaignPlans.cardOf(application.source) match {
      case Some(card) => Right(Vector[CoreOperation](Costs.onCard(user, card,
        cost, catalog, intoOccupied = true)))
      case None => onTitle(ready, user)
    }))

  private def enemy(application: CampaignPlanApplication, holding: PlayerId)
      : Boolean = application.side match
    case CampaignPlanSide.Defender => application.setup.actor == holding
    case CampaignPlanSide.Attacker =>
      application.setup.defender == CampaignDefender.Player(holding)
```

- [ ] **Step 2: Rewrite Gleaming Armor on it**

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/campaign/GleamingArmor.scala` with:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Gleaming Armor (card 66), a persistent rule of a faceup adviser: "Your enemy's
  * battle plans have an added cost of [secret]."
  *
  * A `PlanSurcharge`: every plan the holder's enemy chooses costs one more
  * secret, placed onto the plan's source card like any plan's cost. The title
  * has no card, so the added cost of the title's plan is turning one of its
  * user's faceup secrets facedown. Bandits hold no secrets and cannot pay it.
  *
  * Each taxed plan writes "{Red}'s battle plans cost 1 extra secret."
  */
final case class GleamingArmor private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends PlanSurcharge:
  def id: PowerId = GleamingArmor.id
  protected def cost: Cost = Cost(secret = GleamingArmor.Secret)
  protected def taxed: NoteKey = GleamingArmor.taxed
  protected def amount: Int = GleamingArmor.Secret
  protected def unpayable: OathViolation =
    OathViolation.InsufficientSecrets(GleamingArmor.Secret, 0)

  // Turning a secret facedown does nothing without one, so the cost of the
  // title's plan is checked here rather than left to a best-effort flip.
  override protected def onTitle(ready: ReadyGame, user: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    val faceUp = ready.game.current.players.find(_.player == user)
      .fold(0)(_.board.faceUpSecrets)
    if faceUp >= GleamingArmor.Secret then Right(Vector[CoreOperation](
      FlipSecrets(user, GleamingArmor.Secret, SecretSide.FaceUp,
        SecretSide.FaceDown)))
    else Left(OathViolation.InsufficientSecrets(GleamingArmor.Secret, faceUp))

object GleamingArmor:
  val id: PowerId = PowerId("denizen.gleaming-armor")
  /** The added cost, in secrets. */
  val Secret: Int = 1
  /** "{Red}'s battle plans cost {1} extra secret." */
  val taxed: NoteKey = NoteKey("taxed", Vector(NotePart.Arg(0),
    NotePart.Text("'s battle plans cost "), NotePart.Arg(1),
    NotePart.Plural(1, " extra secret.", " extra secrets.")))

  def forCatalog(catalog: ExecutableCatalog): Option[GleamingArmor] =
    CatalogCards.denizen(catalog, id).map(new GleamingArmor(_, catalog))
```

- [ ] **Step 3: Run Gleaming Armor's suite, unchanged**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.GleamingArmorSuite"`
Expected: PASS, all 11 tests, with no change to the suite.

- [ ] **Step 4: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/InsectSwarmSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Insect Swarm: while its faceup holder is in a Campaign, every plan the enemy
  * chooses costs one more favor, burnt from the plan's user's board, whatever
  * the turn. A plan the enemy cannot afford with it is not offered, and the
  * option states the price with it.
  */
class InsectSwarmSuite extends munit.FunSuite:
  private val swarm = cardWith("denizen.insect-swarm")
  private val armor = cardWith("denizen.gleaming-armor")
  private val watchdog = cardWith("denizen.watchdog")
  private val honors = cardWith("denizen.battle-honors")
  private val provisions = cardWith("denizen.extra-provisions")
  private val titleRef: DecisionOptionRef = DecisionOptionRef.Button("title")

  private def ref(card: String): DecisionOptionRef =
    DecisionOptionRef.Denizen(DenizenId(card))

  private def price(option: DecisionOption): OptionPrice = option match
    case DecisionOption.Priced(_, price) => price
    case _ => OptionPrice()

  private def favor(state: OathState, who: PlayerId): Int =
    player(state, who).board.favor

  private def bank(state: OathState, suit: Suit): Int =
    ready(state).banks.favor.getOrElse(suit, 0)

  private def tokensOn(state: OathState, who: PlayerId, card: String)
      : Option[Tokens] = player(state, who).advisers.collectFirst {
    case held: DenizenState if held.id.value == card => held.tokens }

  // ---- the attacker holds it: the defender's plans cost more ---------------

  /** The attacker holds Insect Swarm. The defender holds the title, a Watchdog,
    * which costs nothing of itself, and `defenderFavor` favor.
    */
  private def attackerHolds(defenderFavor: Int): Board =
    val base = againstPlayer(board())
    on(withAdviserFor(withAdviser(base, swarm, Orientation.FaceUp),
      base.other, watchdog, Orientation.FaceUp))(_.favor(base.other, defenderFavor))

  test("a defender's plan costs one more favor, burnt even though it is paid off turn"):
    val b = attackerHolds(1)
    val suit = catalog.suitOf(DenizenId(watchdog)).get
    val run = commit(rules(losing), b, 4)
    val option = run.options(b.actor).find(_.ref == ref(watchdog)).get
    assertEquals(price(option), OptionPrice(favorBurnt = 1))
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref(watchdog))
    assertEquals(favor(picked.state, b.other), 0)
    // Burnt to the shared bank: not settled into the card's suit bank, and
    // nothing rests on the card.
    assertEquals(bank(picked.state, suit), bank(run.state, suit))
    assertEquals(tokensOn(picked.state, b.other, watchdog), Some(Tokens.empty))

  test("the title's plan burns a favor from its user's board, since it has no card"):
    val b = attackerHolds(1)
    val run = commit(rules(losing), b, 4)
    val title = run.options(b.actor).find(_.ref == titleRef).get
    assertEquals(price(title), OptionPrice(favorBurnt = 1))
    val picked = run.pick(b.other, CampaignIds.defenderPlan, titleRef)
    assertEquals(favor(picked.state, b.other), 0)

  test("a defender with no favor is offered no plan"):
    val b = attackerHolds(0)
    assert(awaits(commit(rules(losing), b, 4), b.actor, CampaignIds.sacrifice))

  test("a plan whose own cost and the added favor cannot both be paid is not offered"):
    val base = attackerHolds(1)
    // Extra Provisions places a favor of its own, so it needs two.
    val b = withAdviserFor(base, base.other, provisions, Orientation.FaceUp)
    val run = commit(rules(losing), b, 4)
    assert(awaits(run, b.other, CampaignIds.defenderPlan))
    assert(run.offered(b.actor).contains(titleRef))
    assert(!run.offered(b.actor).contains(ref(provisions)))

  test("a facedown Insect Swarm is not active"):
    val base = againstPlayer(board())
    val b = on(withAdviserFor(withAdviser(base, swarm, Orientation.FaceDown),
      base.other, watchdog, Orientation.FaceUp))(_.favor(base.other, 0))
    assertEquals(commit(rules(losing), b, 4).options(b.actor).map(price),
      Vector.fill(3)(OptionPrice()))

  test("bandits are enemies too: a bandit defender is taxed, cannot pay, and applies no plan"):
    val base = board()
    val free = withSiteCard(base, base.origin, watchdog)
    val taxed = withAdviser(free, swarm, Orientation.FaceUp)
    // Every pool change but the attacker's: the printed defense, Watchdog's die
    // and the record that the bandit applied it.
    def defenseChanges(run: Run): Int = run.ops.count:
      case ModifyDicePool(pool, _, _) => pool != CampaignIds.attackPool
      case _ => false
    val plain = commit(rules(losing), free, 2)
    val swarmed = commit(rules(losing), taxed, 2)
    assert(awaits(plain, free.actor, CampaignIds.sacrifice))
    assert(awaits(swarmed, taxed.actor, CampaignIds.sacrifice))
    assertEquals(defenseChanges(plain) - defenseChanges(swarmed), 2)
    assertEquals(swarmed.ops.count(_.isInstanceOf[PayCost]), 0)

  // ---- the defender holds it: the attacker's plans cost more ---------------

  /** The other player holds Insect Swarm. The attacker holds Battle Honors,
    * which costs nothing of itself, and `attackerFavor` favor.
    */
  private def defenderHolds(attackerFavor: Int): Board =
    val base = againstPlayer(board())
    on(withAdviser(withAdviserFor(base, base.other, swarm, Orientation.FaceUp),
      honors, Orientation.FaceUp))(_.favor(base.actor, attackerFavor))

  test("an attacker's plan costs one more favor, burnt from the attacker's board"):
    val b = defenderHolds(1)
    val run = commit(rules(winning), b, 4)
    val option = run.options(b.actor).find(_.ref == ref(honors)).get
    assertEquals(price(option), OptionPrice(favorBurnt = 1))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref(honors))
    assertEquals(favor(picked.state, b.actor), 0)
    assertEquals(tokensOn(picked.state, b.actor, honors), Some(Tokens.empty))

  test("with Gleaming Armor on the same side, both added costs apply"):
    val base = defenderHolds(1)
    val b = on(withAdviserFor(base, base.other, armor, Orientation.FaceUp))(
      _.secrets(base.actor, faceUp = 1))
    val run = commit(rules(winning), b, 4)
    val option = run.options(b.actor).find(_.ref == ref(honors)).get
    assertEquals(price(option), OptionPrice(secrets = 1, favorBurnt = 1))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref(honors))
    assertEquals(favor(picked.state, b.actor), 0)
    assertEquals(player(picked.state, b.actor).board.faceUpSecrets, 0)
    // Gleaming Armor's secret is placed onto the card; the favor is burnt.
    assertEquals(tokensOn(picked.state, b.actor, honors), Some(Tokens(0, 1)))

  test("the holder's own plans are not taxed"):
    val base = againstPlayer(board())
    val b = on(withAdviserFor(withAdviserFor(base, base.other, swarm,
      Orientation.FaceUp), base.other, watchdog, Orientation.FaceUp))(
      _.favor(base.other, 0))
    assertEquals(commit(rules(losing), b, 4).options(b.actor).map(price),
      Vector.fill(3)(OptionPrice()))

  // ---- Lines ----

  private val power = InsectSwarm.forCatalog(catalog).get
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("a taxed plan writes the Swarm's line, naming the plan's user"):
    val b = attackerHolds(1)
    val run = commit(rules(losing), b, 4)
    // Pricing the offered plans writes nothing: only a plan applied does.
    assertEquals(said(run.events), Vector.empty)
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref(watchdog))
    assertEquals(said(picked.events), Vector(NoteText.Said("taxed",
      s"${b.other.value}'s battle plans cost 1 extra favor, burnt.",
      covers = false)))
```

- [ ] **Step 5: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.InsectSwarmSuite"`
Expected: FAIL to compile, with "Not found: InsectSwarm".

- [ ] **Step 6: Create Insect Swarm**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/InsectSwarm.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Insect Swarm (card 184), a persistent rule of a faceup adviser: "Your
  * enemy's battle plans each have an added cost of [favor-burnt]."
  *
  * A `PlanSurcharge`: every plan the holder's enemy chooses costs one more
  * favor, burnt. A burnt cost never rests on the card, so it goes to the shared
  * bank even when paid outside its user's turn, and the title's plan burns it
  * from its user's board. Bandits hold no favor and cannot pay it. With
  * Gleaming Armor on the same side, both added costs apply.
  *
  * Each taxed plan writes "{Red}'s battle plans cost 1 extra favor, burnt."
  */
final case class InsectSwarm private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends PlanSurcharge:
  def id: PowerId = InsectSwarm.id
  protected def cost: Cost = Cost(favorBurnt = InsectSwarm.Favor)
  protected def taxed: NoteKey = InsectSwarm.taxed
  protected def amount: Int = InsectSwarm.Favor
  protected def unpayable: OathViolation =
    OathViolation.InsufficientFavor(InsectSwarm.Favor, 0)

object InsectSwarm:
  val id: PowerId = PowerId("denizen.insect-swarm")
  /** The added cost, in favor burnt. */
  val Favor: Int = 1
  /** "{Red}'s battle plans cost {1} extra favor, burnt." */
  val taxed: NoteKey = NoteKey("taxed", Vector(NotePart.Arg(0),
    NotePart.Text("'s battle plans cost "), NotePart.Arg(1),
    NotePart.Text(" extra favor, burnt.")))

  def forCatalog(catalog: ExecutableCatalog): Option[InsectSwarm] =
    CatalogCards.denizen(catalog, id).map(new InsectSwarm(_, catalog))
```

- [ ] **Step 7: Register it**

In `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`, add the line after `GleamingArmor.forCatalog(catalog).toVector ++`:

```scala
      InsectSwarm.forCatalog(catalog).toVector ++
```

and in the doc comment replace "and the rule that taxes them" with "and the rules that tax them", and "Gleaming Armor (an added cost on the enemy's plans)" with "Gleaming Armor and Insect Swarm (an added cost on the enemy's plans)".

- [ ] **Step 8: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS. 10 new tests in `InsectSwarmSuite`, and `GleamingArmorSuite` and every other campaign plan suite still pass.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/PlanSurcharge.scala src/main/scala/oathdigital/gameplay/powers/campaign/GleamingArmor.scala src/main/scala/oathdigital/gameplay/powers/campaign/InsectSwarm.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala src/test/scala/oathdigital/gameplay/powers/campaign/InsectSwarmSuite.scala
git commit -m "feat(powers): add Insect Swarm and share the plan surcharge with Gleaming Armor"
```

---

### Task 2: Book Burning

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/BookBurning.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/BookBurningSuite.scala`

**Interfaces:**
- Consumes: `BattlePlan`, `PlanContext.denizen/setup`, `PlanUse.won/result`, `NoteSupport.secrets(step, player): Int`, `FlipSecrets`, `Burn.secrets(amount, from)`.
- Produces: `BookBurning.id`, `BookBurning.burned: NoteKey`, `BookBurning.none: NoteKey`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/BookBurningSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.{WalkerDice, WalkerStepRecorded}
import oathdigital.model._

/** Book Burning: a free attacker's plan, offered only in a Raid. If the attacker
  * wins, every secret on the defender's board is burnt but one, the facedown
  * ones first, turned faceup to be burnt.
  */
class BookBurningSuite extends munit.FunSuite:
  private val card = cardWith("denizen.book-burning")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))
  private val honors = cardWith("denizen.battle-honors")
  private val honorsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(DenizenId(honors))

  /** The other player stands at the origin with three warbands on their board
    * and the given secrets. The attacker holds Book Burning and four warbands.
    */
  private def raid(faceUp: Int, faceDown: Int): Board =
    val base = withEnemyAtOrigin(board(warbands = 4))
    withAdviser(on(base)(_.warbands(base.other, 3)
      .secrets(base.other, faceUp = faceUp, faceDown = faceDown)),
      card, Orientation.FaceUp)

  /** A Raid with Book Burning chosen, played to its end. A beaten defender's
    * pawn goes to the first site offered. */
  private def burned(b: Board, dice: WalkerDice): Run =
    val run = commit(rules(dice), b, 4, raid = true)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    if awaits(run, b.actor, CampaignIds.relocation) then
      run.pick(b.actor, CampaignIds.relocation, run.offered(b.actor).head).finish
    else run

  private def secrets(run: Run, who: PlayerId): (Int, Int) =
    val held = player(run.state, who).board
    held.faceUpSecrets -> held.faceDownSecrets

  private def winner(run: Run): Option[Boolean] =
    ready(run.state).game.current.lastCampaignResult.map(_.attackerWins)

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    BookBurning.id, Vector(BookBurning.burned, BookBurning.none), run.events)

  test("a won Raid burns every secret but one, the facedown ones first, and says so"):
    val b = raid(faceUp = 2, faceDown = 2)
    val done = burned(b, winning)
    assertEquals(winner(done), Some(true))
    assertEquals(secrets(done, b.other), (1, 0))
    assertEquals(lines(done), Vector(NoteText.Said("burned",
      s"Burned 3 secrets from ${b.other.value}'s board.", covers = false)))

  test("facedown secrets are turned faceup and burnt in one step, and the one left stays facedown"):
    val b = raid(faceUp = 0, faceDown = 3)
    val done = burned(b, winning)
    assertEquals(secrets(done, b.other), (0, 1))
    val flipped = done.events.collect {
      case step: WalkerStepRecorded
          if step.ops.exists(_.isInstanceOf[FlipSecrets]) => step.ops }
    assertEquals(flipped, Vector(Vector[CoreOperation](
      FlipSecrets(b.other, 2, SecretSide.FaceDown, SecretSide.FaceUp),
      Burn.secrets(2, PositionedLocation(Location.PlayArea(b.other))))))

  test("a defender with one secret loses nothing, and the line says there was none to burn"):
    val b = raid(faceUp = 1, faceDown = 0)
    val done = burned(b, winning)
    assertEquals(winner(done), Some(true))
    assertEquals(secrets(done, b.other), (1, 0))
    assertEquals(lines(done), Vector(NoteText.Said("none",
      s"${b.other.value} had no secret to burn.", covers = false)))

  test("a lost Raid burns nothing and writes nothing"):
    val b = raid(faceUp = 2, faceDown = 2)
    val done = burned(b, losing)
    assertEquals(winner(done), Some(false))
    assertEquals(secrets(done, b.other), (2, 2))
    assertEquals(lines(done), Vector.empty)

  test("choosing it costs nothing and changes no dice"):
    val b = raid(faceUp = 2, faceDown = 0)
    val run = commit(rules(winning), b, 4, raid = true)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(!picked.since(run).exists(op => op.isInstanceOf[PayCost] ||
      op.isInstanceOf[ModifyDicePool]))

  test("it is not offered in a Conquest"):
    val b = withAdviser(withAdviser(board(), card, Orientation.FaceUp), honors,
      Orientation.FaceUp)
    val run = commit(rules(winning), b, 4)
    assert(run.offers(b.actor, b.actor, CampaignIds.attackerPlan, honorsRef))
    assert(!run.offered(b.actor).contains(ref))
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.BookBurningSuite"`
Expected: FAIL to compile, with "Not found: BookBurning".

- [ ] **Step 3: Create Book Burning**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/BookBurning.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport}
import oathdigital.model._

/** Book Burning (card 22), an attacker's battle plan: "If you're victorious in
  * a raid, burn all [secret] on the defender's board except their last
  * [secret]."
  *
  * It is free, and offered only in a Raid. A Raid's defender is always a player,
  * whose pawn the attacker reached. Once the Campaign has resolved, if the
  * attacker won, every secret on the defender's board is burnt except one. Only
  * a faceup secret can be burnt, so the facedown ones go first: they are turned
  * faceup and burnt in the same step. The defender keeps one secret, faceup if
  * any remains. A defender with one secret or none loses nothing.
  *
  * When the attacker won it writes "Burned {n secrets} from {Blue}'s board.",
  * or "{Blue} had no secret to burn." when nothing was burnt.
  */
final case class BookBurning private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = BookBurning.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] =
    Vector(BookBurning.burned, BookBurning.none)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.setup.kind == CampaignKind.Raid)
      .map(source => CampaignPlanOffer(source,
        "Book Burning: burn the defender's secrets but one if victorious",
        Vector.empty, Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if !use.won.contains(true) then Vector.empty
      else use.result.map(_.defender).collect {
        case CampaignDefender.Player(defender) => defender
      }.toVector.flatMap(defender => Vector(
        BuildOps((ready, _) => Right(BookBurning.burn(ready, defender))),
        Note(id, states => states.previous.map { step =>
          val burnt = -NoteSupport.secrets(step, defender)
          if burnt > 0 then BookBurning.burned(PowerSourceRef.Card(cardId),
            NoteArg.Amount(burnt, NoteUnit.Secret), NoteArg.Player(defender))
          else BookBurning.none(PowerSourceRef.Card(cardId),
            NoteArg.Player(defender))
        })))))

object BookBurning:
  val id: PowerId = PowerId("denizen.book-burning")

  /** "Burned {n secrets} from {Blue}'s board." */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Text("Burned "),
    NotePart.Arg(0), NotePart.Text(" from "), NotePart.Arg(1),
    NotePart.Text("'s board.")))

  /** "{Blue} had no secret to burn." */
  val none: NoteKey = NoteKey("none", Vector(NotePart.Arg(0),
    NotePart.Text(" had no secret to burn.")))

  /** Every secret on `defender`'s board but one, the facedown ones turned
    * faceup first. A batch runs its operations in order, so the burn sees the
    * secrets just turned. */
  private def burn(ready: ReadyGame, defender: PlayerId): Vector[CoreOperation] =
    ready.game.current.players.find(_.player == defender).toVector.flatMap {
      held =>
        val burnt = held.board.faceUpSecrets + held.board.faceDownSecrets - 1
        val turned = math.min(held.board.faceDownSecrets, burnt)
        if burnt <= 0 then Vector.empty
        else Option.when[CoreOperation](turned > 0)(FlipSecrets(defender,
          turned, SecretSide.FaceDown, SecretSide.FaceUp)).toVector :+
          Burn.secrets(burnt, PositionedLocation(Location.PlayArea(defender)))
    }

  def forCatalog(catalog: ExecutableCatalog): Option[BookBurning] =
    CatalogCards.denizen(catalog, id).map(new BookBurning(_))
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`, add the line after `Hospital.forCatalog(catalog).toVector`, moving the `++` so the chain stays valid:

```scala
      Hospital.forCatalog(catalog).toVector ++
      BookBurning.forCatalog(catalog).toVector
```

In the doc comment, replace "and Hospital (killed warbands saved until the end)" with "Hospital (killed warbands saved until the end) and Book Burning (secrets burnt at the end)".

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS. 6 new tests in `BookBurningSuite`, and every existing campaign plan suite still passes.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/BookBurning.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala src/test/scala/oathdigital/gameplay/powers/campaign/BookBurningSuite.scala
git commit -m "feat(powers): add Book Burning"
```

---

### Task 3: Field Promotion

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/FieldPromotion.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/FieldPromotionSuite.scala`

**Interfaces:**
- Consumes: `BattlePlan`, `PlanContext.denizen`, `PlanUse.won/user`, `PlayerFacts.forceKind(ready, player): Either[OathViolation, ForceKind]`, `NoteSupport.gainedKey`, `NoteSupport.gainedNote`, `NoteSupport.warbands`.
- Produces: `FieldPromotion.id`, `FieldPromotion.gained: NoteKey`, `FieldPromotion.Warbands`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/FieldPromotionSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.{NoteText, PlayerFacts}
import oathdigital.gameplay.powers.PowerFixture.warbandBank
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Field Promotion: a plan for either side, a favor placed, that gains three
  * warbands if its user wins, or what their supply holds. */
class FieldPromotionSuite extends munit.FunSuite:
  private val card = cardWith("denizen.field-promotion")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val honors = cardWith("denizen.battle-honors")
  private val honorsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(DenizenId(honors))

  private def kind(b: Board, who: PlayerId): ForceKind =
    PlayerFacts.forceKind(b.ready, who).toOption.get

  private def winner(run: Run): Option[Boolean] =
    ready(run.state).game.current.lastCampaignResult.map(_.attackerWins)

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    FieldPromotion.id, Vector(FieldPromotion.gained), run.events)

  private def gained(who: PlayerId, count: Int): NoteText.Said =
    NoteText.Said("gained", s"${who.value} gained $count " +
      (if count == 1 then "warband." else "warbands."), covers = false)

  /** The attacker holds Field Promotion and a favor. */
  private def attacking: Board =
    val base = board()
    on(withAdviser(base, card, Orientation.FaceUp))(_.favor(base.actor, 1))

  test("an attacker that wins places a favor on it and gains three warbands, and says so"):
    val b = attacking
    val run = commit(rules(winning), b, 4)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(player(picked.state, b.actor).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens },
      Some(Tokens(1, 0)))
    val done = picked.finish
    assertEquals(winner(done), Some(true))
    assert(done.ops.contains(Gain.Warbands(b.actor, kind(b, b.actor), 3)))
    assertEquals(lines(done), Vector(gained(b.actor, 3)))

  test("an attacker that loses gains nothing and writes nothing"):
    val b = attacking
    val done = commit(rules(losing), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(false))
    assert(!done.ops.exists(_.isInstanceOf[Gain.Warbands]))
    assertEquals(lines(done), Vector.empty)

  test("a defender that wins pays off turn and gains three warbands"):
    val base = againstPlayer(board())
    val b = on(withAdviserFor(base, base.other, card, Orientation.FaceUp))(
      _.favor(base.other, 1))
    val picked = commit(rules(losing), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    // Paid off turn, the favor goes to the bank at once, not onto the card.
    assertEquals(player(picked.state, b.other).board.favor, 0)
    val done = picked.finish
    assertEquals(winner(done), Some(false))
    assertEquals(lines(done), Vector(gained(b.other, 3)))

  test("a short supply gives what it holds"):
    val base = attacking
    val force = kind(base, base.actor)
    // Every warband of the actor's kind but one is on their board.
    val b = replacePlayer(base, base.actor)(p => p.copy(board = p.board.copy(
      warbands = p.board.warbands + warbandBank(base.ready, force) - 1)))
    assertEquals(warbandBank(b.ready, force), 1)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(true))
    assertEquals(warbandBank(ready(done.state), force), 0)
    assertEquals(lines(done), Vector(gained(b.actor, 1)))

  test("an attacker with no favor is not offered it"):
    val base = board()
    val b = on(withAdviser(withAdviser(base, card, Orientation.FaceUp), honors,
      Orientation.FaceUp))(_.favor(base.actor, 0))
    val run = commit(rules(winning), b, 4)
    assert(run.offers(b.actor, b.actor, CampaignIds.attackerPlan, honorsRef))
    assert(!run.offered(b.actor).contains(ref))

  test("a bandit defender never uses it, since it costs a favor"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    val run = commit(rules(losing), b, 2)
    assert(awaits(run, b.actor, CampaignIds.sacrifice))
    assert(!ready(run.state).game.current.rollPools.contains(
      CampaignPlans.appliedMarker(ref)))
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.FieldPromotionSuite"`
Expected: FAIL to compile, with "Not found: FieldPromotion".

- [ ] **Step 3: Create Field Promotion**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/FieldPromotion.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport, PlayerFacts}
import oathdigital.model._

/** Field Promotion (card 106), a battle plan for either side: "[favor] If
  * you're victorious, gain three warbands."
  *
  * A favor is placed onto the card. Once the Campaign has resolved, its user
  * gains three warbands if they won, or what their supply holds. Bandits pay
  * nothing, so a bandit defender never applies it.
  *
  * When a warband was gained it writes "{Red} gained {n warbands}."
  */
final case class FieldPromotion private (cardId: DenizenId) extends BattlePlan:
  def id: PowerId = FieldPromotion.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(FieldPromotion.gained)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Field Promotion: gain 3 warbands if victorious",
      Vector(CampaignPlanCost.Favor(FieldPromotion.Favor)), Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if !use.won.contains(true) then Vector.empty
      else use.user.toVector.flatMap(user => Vector(
        BuildOps((ready, _) => PlayerFacts.forceKind(ready, user).map(kind =>
          Vector[CoreOperation](Gain.Warbands(user, kind,
            FieldPromotion.Warbands)))),
        Note(id, NoteSupport.gainedNote(FieldPromotion.gained,
          PowerSourceRef.Card(cardId), user, NoteUnit.Warband,
          NoteSupport.warbands))))))

object FieldPromotion:
  val id: PowerId = PowerId("denizen.field-promotion")
  /** The favor placed to choose it. */
  val Favor: Int = 1
  val Warbands: Int = 3
  /** "{Red} gained {n warbands}." */
  val gained: NoteKey = NoteSupport.gainedKey("gained")

  def forCatalog(catalog: ExecutableCatalog): Option[FieldPromotion] =
    CatalogCards.denizen(catalog, id).map(new FieldPromotion(_))
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala`, change the last line of the chain:

```scala
      BattleAxes.forCatalog(catalog).toVector ++
      FieldPromotion.forCatalog(catalog).toVector
```

In the doc comment, replace "Encirclement, Disgraced Captain and Battle Axes" with "Encirclement, Disgraced Captain, Battle Axes and Field Promotion".

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS. 6 new tests in `FieldPromotionSuite`, and every existing campaign plan suite still passes.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/FieldPromotion.scala src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala src/test/scala/oathdigital/gameplay/powers/campaign/FieldPromotionSuite.scala
git commit -m "feat(powers): add Field Promotion"
```

---

### Task 4: `FavorBySuit`, then Tribute Spoils and Military Parade

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/FavorBySuit.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/TributeSpoils.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/MilitaryParade.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/TributeSpoilsSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/campaign/MilitaryParadeSuite.scala`

**Interfaces:**
- Consumes: `BattlePlan`, `PlanContext.denizen/setup/enemy`, `PlanUse.won/user/result/side/actor/ready`, `CampaignResult.targetSites/defender`, `ExecutableCatalog.suitOf(id: CardId): Option[Suit]`, `Suit.all: Vector[Suit]`.
- Produces:
  - `FavorBySuit.counts(catalog: ExecutableCatalog, cards: Vector[CardId]): Vector[(Suit, Int)]`
  - `FavorBySuit.gains(user: Option[PlayerId], counts: Vector[(Suit, Int)]): Vector[Operation]`
  - `TributeSpoils.id`, `MilitaryParade.id`

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/TributeSpoilsSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Tribute Spoils: a plan for either side, a favor placed, offered only in a
  * Conquest. If its user wins, they gain a favor for each denizen and edifice
  * at the targets, from that card's suit bank. */
class TributeSpoilsSuite extends munit.FunSuite:
  private val card = cardWith("denizen.tribute-spoils")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))
  private val honors = cardWith("denizen.battle-honors")
  private val honorsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(DenizenId(honors))
  private val orders = inert(Suit.Order, 3)
  private val beastEdifice =
    catalog.edifices.find(_.suit == Suit.Beast).get.id.value
  private val relic = catalog.relics.head.id.value

  private def favor(state: OathState, who: PlayerId): Int =
    player(state, who).board.favor

  private def bank(state: OathState, suit: Suit): Int =
    ready(state).banks.favor.getOrElse(suit, 0)

  private def winner(run: Run): Option[Boolean] =
    ready(run.state).game.current.lastCampaignResult.map(_.attackerWins)

  /** The attacker holds Tribute Spoils and a favor. The origin, which two
    * bandits rule, holds two faceup order denizens, a facedown order denizen
    * and a ruined beast edifice. */
  private def attacking: Board =
    val base = board()
    val cards = withEdifice(withSiteCard(withSiteCard(
      withAdviser(base, card, Orientation.FaceUp), base.origin, orders(0)),
      base.origin, orders(1)), base.origin, beastEdifice, EdificeSide.Ruined)
    on(cards)(_.denizen(DenizenId(orders(2)), at = base.origin, facedown = true)
      .favor(base.actor, 1))

  test("an attacker that wins a Conquest gains a favor per card at the targets, from that card's bank"):
    val b = attacking
    val before = OathState.Ready(b.ready)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(true))
    // The favor placed on the card, then two from the Order bank and one from
    // the Beast bank: the facedown denizen has no suit and gives nothing, and
    // the ruined edifice keeps its suit.
    assertEquals(favor(done.state, b.actor), 1 - 1 + 3)
    assertEquals(bank(done.state, Suit.Order), bank(before, Suit.Order) - 2)
    assertEquals(bank(done.state, Suit.Beast), bank(before, Suit.Beast) - 1)

  test("an attacker that loses gains nothing"):
    val b = attacking
    val before = OathState.Ready(b.ready)
    val done = commit(rules(losing), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(false))
    assertEquals(favor(done.state, b.actor), 0)
    assertEquals(bank(done.state, Suit.Order), bank(before, Suit.Order))

  test("a defender that wins gains for the cards at its own targeted site, and a relic counts nothing"):
    val base = againstPlayer(board())
    val b = on(withSiteCard(withAdviserFor(base, base.other, card,
      Orientation.FaceUp), base.origin, orders(0)))(
      _.relicAt(relic, base.origin).favor(base.other, 1))
    val before = OathState.Ready(b.ready)
    val done = commit(rules(losing), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assertEquals(winner(done), Some(false))
    // Paid off turn, the placed favor went to the Nomad bank; one favor came
    // back from the Order bank.
    assertEquals(favor(done.state, b.other), 1)
    assertEquals(bank(done.state, Suit.Order), bank(before, Suit.Order) - 1)

  test("it is not offered in a Raid"):
    val base = withEnemyAtOrigin(board(warbands = 4))
    val b = on(withAdviser(withAdviser(base, card, Orientation.FaceUp), honors,
      Orientation.FaceUp))(_.warbands(base.other, 3).favor(base.actor, 1))
    val run = commit(rules(winning), b, 4, raid = true)
    assert(run.offers(b.actor, b.actor, CampaignIds.attackerPlan, honorsRef))
    assert(!run.offered(b.actor).contains(ref))
```

Create `src/test/scala/oathdigital/gameplay/powers/campaign/MilitaryParadeSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._

/** Military Parade: a free plan for either side. If its user wins, they gain a
  * favor for each faceup adviser the enemy holds then, from that adviser's
  * suit bank. Bandits hold no advisers, so an attacker is not offered it
  * against them. */
class MilitaryParadeSuite extends munit.FunSuite:
  private val card = cardWith("denizen.military-parade")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(DenizenId(card))
  private val honors = cardWith("denizen.battle-honors")
  private val honorsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(DenizenId(honors))
  private val orders = inert(Suit.Order, 2)
  private val beast = inert(Suit.Beast, 1).head
  private val arcane = inert(Suit.Arcane, 1).head

  private def favor(state: OathState, who: PlayerId): Int =
    player(state, who).board.favor

  private def bank(state: OathState, suit: Suit): Int =
    ready(state).banks.favor.getOrElse(suit, 0)

  private def winner(run: Run): Option[Boolean] =
    ready(run.state).game.current.lastCampaignResult.map(_.attackerWins)

  /** The attacker holds Military Parade. The other player rules the origin and
    * holds two order advisers and a beast adviser faceup, and an arcane
    * adviser facedown. */
  private def attacking: Board =
    val base = againstPlayer(board())
    val held = Vector(orders(0) -> Orientation.FaceUp,
      orders(1) -> Orientation.FaceUp, beast -> Orientation.FaceUp,
      arcane -> Orientation.FaceDown).foldLeft(base) {
      case (b, (adviser, face)) => withAdviserFor(b, b.other, adviser, face) }
    withAdviser(held, card, Orientation.FaceUp)

  test("an attacker that wins gains a favor per faceup adviser of the enemy, from each adviser's bank"):
    val b = attacking
    val before = OathState.Ready(b.ready)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(true))
    // The facedown arcane adviser has no suit and gives nothing.
    assertEquals(favor(done.state, b.actor), favor(before, b.actor) + 3)
    assertEquals(bank(done.state, Suit.Order), bank(before, Suit.Order) - 2)
    assertEquals(bank(done.state, Suit.Beast), bank(before, Suit.Beast) - 1)
    assertEquals(bank(done.state, Suit.Arcane), bank(before, Suit.Arcane))

  test("an attacker that loses gains nothing"):
    val b = attacking
    val before = OathState.Ready(b.ready)
    val done = commit(rules(losing), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(winner(done), Some(false))
    assertEquals(favor(done.state, b.actor), favor(before, b.actor))

  test("a defender that wins gains for the attacker's faceup advisers"):
    val base = againstPlayer(board())
    val b = withAdviser(withAdviserFor(base, base.other, card,
      Orientation.FaceUp), orders(0), Orientation.FaceUp)
    val before = OathState.Ready(b.ready)
    val done = commit(rules(losing), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref).finish
    assertEquals(winner(done), Some(false))
    assertEquals(favor(done.state, b.other), favor(before, b.other) + 1)
    assertEquals(bank(done.state, Suit.Order), bank(before, Suit.Order) - 1)

  test("an attacker is not offered it against bandits"):
    val b = withAdviser(withAdviser(board(), card, Orientation.FaceUp), honors,
      Orientation.FaceUp)
    val run = commit(rules(winning), b, 4)
    assert(run.offers(b.actor, b.actor, CampaignIds.attackerPlan, honorsRef))
    assert(!run.offered(b.actor).contains(ref))

  test("a bandit defender that wins moves the favor from the banks to the shared bank, without choosing"):
    val base = board()
    val b = withAdviser(withSiteCard(base, base.origin, card), orders(0),
      Orientation.FaceUp)
    val before = OathState.Ready(b.ready)
    val run = commit(rules(losing), b, 2)
    // It applied the free plan by itself, so nothing was asked of the attacker.
    assert(awaits(run, b.actor, CampaignIds.sacrifice))
    val done = run.finish
    assertEquals(winner(done), Some(false))
    assertEquals(bank(done.state, Suit.Order), bank(before, Suit.Order) - 1)
    assertEquals(favor(done.state, b.actor), favor(before, b.actor))

  test("the gain is best-effort: an Order bank with one favor gives one"):
    val b = on(attacking)(_.bankFavor(Suit.Order, 1))
    val before = OathState.Ready(b.ready)
    val done = commit(rules(winning), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(bank(done.state, Suit.Order), 0)
    assertEquals(favor(done.state, b.actor), favor(before, b.actor) + 2)
```

- [ ] **Step 2: Run the suites to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.TributeSpoilsSuite oathdigital.gameplay.powers.campaign.MilitaryParadeSuite"`
Expected: FAIL. Both suites compile, because they name no new production symbol. Every test that picks the plan fails with an `IllegalStateException` ending "was refused: ...", thrown by `Run.answer`, because the plan is not offered yet. The two "not offered" tests pass already; they pin the conditions once the plans exist.

- [ ] **Step 3: Create `FavorBySuit`**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/FavorBySuit.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** The favor a plan gains one of per card, from each card's suit bank (Tribute
  * Spoils, Military Parade). Only a faceup card has a suit, except an edifice,
  * which has its suit on either face, so the callers pass only such cards. The
  * gains are best effort: a bank with less favor gives what it holds.
  */
object FavorBySuit:
  /** How many of `cards` belong to each suit, in suit order, leaving out the
    * suits with none. A card whose suit the catalog does not know counts
    * nothing. */
  def counts(catalog: ExecutableCatalog, cards: Vector[CardId])
      : Vector[(Suit, Int)] =
    val suits = cards.flatMap(card => catalog.suitOf(card))
    Suit.all.map(suit => suit -> suits.count(_ == suit)).filter(_._2 > 0)

  /** One gain per suit for `user`. A bandit defender has no board, so its favor
    * moves from the bank to the shared bank, as Battle Honors' does. */
  def gains(user: Option[PlayerId], counts: Vector[(Suit, Int)])
      : Vector[Operation] = counts.map { case (suit, count) =>
    user.fold[Operation](Move(Piece.Favor(count),
      PositionedLocation(Location.FavorBank(suit)),
      PositionedLocation(Location.SharedBank)))(Gain.Favor(_, suit, count))
  }
```

- [ ] **Step 4: Create Tribute Spoils**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/TributeSpoils.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Tribute Spoils (card 239), a battle plan for either side: "[favor] If you're
  * victorious in a conquest, take [favor] for each card at targeted sites from
  * the matching favor bank."
  *
  * A favor is placed onto the card. It is offered only in a Conquest. Once the
  * Campaign has resolved, its user gains one favor if they won for each
  * denizen and edifice at the targeted sites as they stand then, from that
  * card's suit bank (`FavorBySuit`). A facedown denizen has no suit and gives
  * nothing, an edifice gives on either face, and relics count nothing. Bandits
  * pay nothing, so a bandit defender never applies it. The gains write the
  * generic gain lines, so it writes no line of its own.
  */
final case class TributeSpoils private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = TributeSpoils.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.setup.kind == CampaignKind.Conquest)
      .map(source => CampaignPlanOffer(source,
        "Tribute Spoils: gain favor for each card at the targets if victorious",
        Vector(CampaignPlanCost.Favor(TributeSpoils.Favor)), Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if !use.won.contains(true) then Vector.empty
      else use.result.toVector.flatMap(result => FavorBySuit.gains(use.user,
        FavorBySuit.counts(catalog,
          TributeSpoils.cardsAt(use.ready, result.targetSites))))))

object TributeSpoils:
  val id: PowerId = PowerId("denizen.tribute-spoils")
  /** The favor placed to choose it. */
  val Favor: Int = 1

  /** The faceup denizens and the edifices at `sites`. */
  private def cardsAt(ready: ReadyGame, sites: Vector[SiteId]): Vector[CardId] =
    sites.flatMap(ready.game.current.map.sites.get).flatMap(_.denizens).collect {
      case DenizenState(held, Orientation.FaceUp, _) => held
      case edifice: EdificeState => edifice.id
    }

  def forCatalog(catalog: ExecutableCatalog): Option[TributeSpoils] =
    CatalogCards.denizen(catalog, id).map(new TributeSpoils(_, catalog))
```

- [ ] **Step 5: Create Military Parade**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/MilitaryParade.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

/** Military Parade (card 109), a battle plan for either side: "If you're
  * victorious, gain [favor] from the favor banks matching each adviser of your
  * enemy (including Imperial Allies)."
  *
  * It is free. Once the Campaign has resolved, its user gains one favor if they
  * won for each faceup adviser the enemy holds then, from that adviser's suit
  * bank (`FavorBySuit`). A facedown adviser has no suit and gives nothing.
  * Every game is all-Exile, so the Imperial Allies clause does nothing. Bandits
  * hold no advisers, so an attacker is not offered it against bandits. A
  * bandit defender applies it by itself, since it is free, and when the
  * bandits win the favor moves from the banks to the shared bank. The gains
  * write the generic gain lines, so it writes no line of its own.
  */
final case class MilitaryParade private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends BattlePlan:
  def id: PowerId = MilitaryParade.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.enemy != CampaignDefender.Bandits)
      .map(source => CampaignPlanOffer(source,
        "Military Parade: gain favor for the enemy's advisers if victorious",
        Vector.empty, Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      if !use.won.contains(true) then Vector.empty
      else MilitaryParade.enemy(use).toVector.flatMap(enemy =>
        FavorBySuit.gains(use.user, FavorBySuit.counts(catalog,
          MilitaryParade.advisers(use.ready, enemy))))))

object MilitaryParade:
  val id: PowerId = PowerId("denizen.military-parade")

  /** The enemy of the plan's user: the attacker for a defender, and the
    * defending player for an attacker. */
  private def enemy(use: PlanUse): Option[PlayerId] = use.side match
    case CampaignPlanSide.Defender => Some(use.actor)
    case CampaignPlanSide.Attacker => use.result.map(_.defender).collect {
      case CampaignDefender.Player(player) => player }

  /** `player`'s faceup advisers. */
  private def advisers(ready: ReadyGame, player: PlayerId): Vector[CardId] =
    ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.advisers.collect {
        case DenizenState(held, Orientation.FaceUp, _) => held })

  def forCatalog(catalog: ExecutableCatalog): Option[MilitaryParade] =
    CatalogCards.denizen(catalog, id).map(new MilitaryParade(_, catalog))
```

- [ ] **Step 6: Register them**

In `src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala`, change the last line of the chain:

```scala
      FieldPromotion.forCatalog(catalog).toVector ++
      TributeSpoils.forCatalog(catalog).toVector ++
      MilitaryParade.forCatalog(catalog).toVector
```

In the doc comment, replace "Battle Axes and Field Promotion" with "Battle Axes, Field Promotion, Tribute Spoils and Military Parade".

- [ ] **Step 7: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: PASS. 10 new tests (4 in `TributeSpoilsSuite`, 6 in `MilitaryParadeSuite`), and every existing campaign plan suite still passes.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/FavorBySuit.scala src/main/scala/oathdigital/gameplay/powers/campaign/TributeSpoils.scala src/main/scala/oathdigital/gameplay/powers/campaign/MilitaryParade.scala src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala src/test/scala/oathdigital/gameplay/powers/campaign/TributeSpoilsSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/MilitaryParadeSuite.scala
git commit -m "feat(powers): add Tribute Spoils and Military Parade"
```

---

### Task 5: Gates and roadmap

**Files:**
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Record the slice in `docs/ROADMAP.md`**

In the "Phase - Catalog batch 3" section, replace:

```markdown
Axes, Great Crusade, Pledge of Defense, The Great Levy, Rain Boots and
Garrison Armory. Slices 1c to 4 remain.
```

with:

```markdown
Axes, Great Crusade, Pledge of Defense, The Great Levy, Rain Boots and
Garrison Armory. Slice 1c is done: the battle plans Book Burning, Tribute
Spoils, Field Promotion and Military Parade, and the plan surcharge Insect
Swarm. Slices 2 to 4 remain.
```

- [ ] **Step 2: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: every server and frontend test passes. The server count is the baseline plus 32 (10 in Task 1, 6 in Task 2, 6 in Task 3, 10 in Task 4). From a baseline of 2234, that is 2266. The frontend count is unchanged at 466.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: record catalog batch 3 slice 1c"
```
