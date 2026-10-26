package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  ModifierPicks, OfferReason, OptionRestriction, PowerCtx, Transform}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Outcomes, RecordedOps, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** The modifier decision on its own: a hand-built tree whose first decision
  * is `ModifierChoice.loop`, walked with stand-in modifiers (modifier walker
  * design, rulings 1 to 6, and plan-time rulings 2 to 4). */
class ModifierChoiceSuite extends munit.FunSuite:
  private val window = PowerWindow.SearchModifierSelection
  private val decisionId = ModifierPicks.decisionId(window)
  private val alpha = CatalogNames.denizen("Wrestlers")
  private val beta = CatalogNames.denizen("Mercenaries")
  private val gamma = CatalogNames.denizen("Errand Boy")
  /** A card p1 does not hold. */
  private val outside = CatalogNames.denizen("Homesteaders")
  private val go = DecisionOptionRef.Button("go")

  /** A stand-in modifier on `card`, pickable at the Search window. */
  private final case class Stand(name: String, card: DenizenId,
      cost: Cost = Cost.free,
      reason: PowerCtx => Option[OfferReason] = _ => None,
      effects: Map[PowerWindow, Vector[Contribution]] = Map.empty)
      extends ContributingPower:
    val id: PowerId = PowerId(s"stand.$name")
    val source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = effects
    override def resolution: PowerResolution = PowerResolution.PlayerSelected
    override def selectionCard: Option[CardId] = Some(card)
    override def selectionWindows: Set[PowerWindow] = Set(window)
    override def offerReason(ctx: PowerCtx): Option[OfferReason] = reason(ctx)
    override def selectionPayments(ready: ReadyGame, actor: PlayerId)
        : Vector[CoreOperation] =
      if cost == Cost.free then Vector.empty
      else Vector(Costs.onCard(actor, card, cost, catalog))

  /** Asks "Go", then spends `supply` Supply at the Search cost window. */
  private def rest(supply: Int): Operation = Sequence(Vector(
    Decide("stand.go", p1, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(go, "Go")))),
    BuildOps((_, _) => Right(Vector(SpendSupply(p1, supply))),
      window = Some(PowerWindow.SearchCost), required = true)))

  private def tree(supply: Int = 1): Operation =
    val after = rest(supply)
    Sequence(Vector(ModifierChoice.loop(p1, window, after, "Start"), after))

  private def powers(stands: Stand*): WalkerPowers =
    WalkerPowers.selected(WalkerPowers(stands.toVector), Vector.empty)

  /** p1 at Ancient City with `favor` favor and `supply` Supply, holding
    * alpha, beta and gamma faceup. */
  private def table(favor: Int = 3, supply: Int = 5): ReadyGame =
    Table.start.pawn(p1, "Ancient City").favor(p1, favor)
      .supply(p1, supply).adviser(p1, alpha).adviser(p1, beta)
      .adviser(p1, gamma).ready

  /** Where a walk parked, with the state its steps left, as replay leaves
    * it. */
  private final case class At(ready: ReadyGame, pending: PendingTree,
      events: Vector[OathEvent])

  private def parked(from: ReadyGame, before: PendingTree,
      outcome: WalkerOutcome)(using munit.Location): At = outcome match
    case WalkerOutcome.Parked(next, events) =>
      val start: OathState = OathState.Ready(from.updateCurrent(
        _.copy(walkerPending = Some(before))))
      val after = events.collect { case step: WalkerStepRecorded => step }
        .foldLeft(start)((state, step) =>
          accepted(ProcedureWalker.applyRecorded(state, step)))
      At(Outcomes.ready(after).updateCurrent(
        _.copy(walkerPending = Some(next))), next, events)
    case other => fail(s"expected a park, got $other")

  private def begin(ready: ReadyGame, on: Operation, walking: WalkerPowers)
      (using munit.Location): At =
    parked(ready, PendingTree(Vector.empty, Vector.empty),
      accepted(ProcedureWalker.advance(ready, on, None, walking)))

  private def answer(at: At, on: Operation, walking: WalkerPowers,
      id: String, ref: DecisionOptionRef)(using munit.Location): At =
    parked(at.ready, at.pending, accepted(ProcedureWalker.resolve(at.ready,
      on, at.pending, Answered(id, DecisionAnswer.ChooseOneAnswer(ref), p1),
      walking)))

  private def decide(at: At, on: Operation, walking: WalkerPowers)
      (using munit.Location): Decide =
    ProcedureWalker.parkedDecide(at.ready, on, at.pending, walking)
      .getOrElse(fail("not parked on a decision"))

  private def options(at: At, on: Operation, walking: WalkerPowers)
      (using munit.Location): Vector[DecisionOption] =
    decide(at, on, walking).query match
      case one: DecisionQuery.ChooseOne => one.options
      case other => fail(s"expected a choose-one, got $other")

  private def card(id: DenizenId): DecisionOption =
    DecisionOption.Denizen(DecisionOptionRef.Denizen(id))
  private val startWithNoModifiers =
    DecisionOption.Button(ModifierPicks.start, "Start with no modifiers")
  private val startWithOneModifier =
    DecisionOption.Button(ModifierPicks.start, "Start with 1 modifier")
  private def unavailable(option: DecisionOption, reason: String) =
    DecisionOption.Unavailable(option, OptionReason.Written(reason))

  test("with nothing to pick, nothing is asked"):
    val on = tree()
    val walked = powers(Stand("away", outside))
    assertEquals(decide(begin(table(), on, walked), on, walked).decisionId,
      "stand.go")

  test("it lists Start first, then each modifier the player may use"):
    val on = tree()
    val walked = powers(Stand("a", alpha), Stand("b", beta))
    val at = begin(table(), on, walked)
    assertEquals(decide(at, on, walked).decisionId, decisionId)
    assertEquals(decide(at, on, walked).query, DecisionQuery.ChooseOne(
      Vector(startWithNoModifiers, card(alpha), card(beta)),
      heading = Some(ModifierChoice.heading)))

  test("Start says how many modifiers the action starts with"):
    assertEquals(Vector(0, 1, 2).map(ModifierChoice.startWith("Start Muster",
      _)), Vector("Start Muster with no modifiers",
      "Start Muster with 1 modifier", "Start Muster with 2 modifiers"))

  test("a modifier is priced from its payment, or unavailable with the " +
      "shortfall"):
    val on = tree()
    val walked = powers(Stand("a", alpha, Cost(favor = 2)),
      Stand("b", beta, Cost(favor = 4)))
    assertEquals(options(begin(table(favor = 3), on, walked), on, walked),
      Vector(startWithNoModifiers,
        DecisionOption.Priced(card(alpha), OptionPrice(favor = 2)),
        unavailable(card(beta), "Needs 4 favor; you have 3")))

  test("a modifier nobody can pay for is not asked about"):
    val on = tree()
    val walked = powers(Stand("b", beta, Cost(favor = 4)))
    assertEquals(decide(begin(table(favor = 3), on, walked), on, walked)
      .decisionId, "stand.go")

  test("a pick pays at once, and the next pass prices what is left after " +
      "it"):
    val on = tree()
    val walked = powers(Stand("a", alpha, Cost(favor = 2)),
      Stand("b", beta, Cost(favor = 2)))
    val first = begin(table(favor = 3), on, walked)
    val second = answer(first, on, walked, decisionId,
      DecisionOptionRef.Denizen(alpha))
    assert(RecordedOps.all(second.events).exists {
        case PayCost(_, Location.OnCard(paid), _, _, _, _) => paid == alpha
        case _ => false
      })
    assertEquals(options(second, on, walked), Vector(startWithOneModifier,
      unavailable(card(beta), "Needs 2 favor; you have 1")))

  test("a pick's record holds what it paid, and the walk's end clears it"):
    val on = tree()
    val walked = powers(Stand("a", alpha, Cost(favor = 2)),
      Stand("b", beta, Cost(favor = 2)))
    val picked = answer(begin(table(favor = 3), on, walked), on, walked,
      decisionId, DecisionOptionRef.Denizen(alpha))
    assertEquals(picked.ready.game.current.walkerPicks, Vector(PlayedPick(
      decisionId, DecisionOptionRef.Denizen(alpha), None,
      OptionPrice(favor = 2), open = false)))
    val begun = answer(picked, on, walked, decisionId, ModifierPicks.start)
    accepted(ProcedureWalker.resolve(begun.ready, on, begun.pending,
      Answered("stand.go", DecisionAnswer.ChooseOneAnswer(go), p1),
      walked)) match
      case WalkerOutcome.Finished(after, _) =>
        assertEquals(after.game.current.walkerPicks, Vector.empty)
      case other => fail(s"expected the walk to finish, got $other")

  test("a modifier's own reason makes it unavailable, or warns, from the " +
      "picks"):
    def after(ref: CardId)(ctx: PowerCtx): Boolean =
      ModifierPicks.isPicked(ctx.answered, window, ref)
    val on = tree()
    val walked = powers(Stand("a", alpha),
      Stand("b", beta, reason = ctx => Option.when(after(alpha)(ctx))(
        OfferReason.Unavailable("Not with Wrestlers"))),
      Stand("c", gamma, reason = ctx => Option.when(after(alpha)(ctx))(
        OfferReason.Warned("Wasted with Wrestlers"))))
    val picked = answer(begin(table(), on, walked), on, walked, decisionId,
      DecisionOptionRef.Denizen(alpha))
    assertEquals(options(picked, on, walked), Vector(startWithOneModifier,
      unavailable(card(beta), "Not with Wrestlers"),
      DecisionOption.Warned(card(gamma),
        OptionReason.Written("Wasted with Wrestlers"))))

  test("a picked modifier's option restriction acts on the options left"):
    val forbids = OptionRestriction(
      (_, ref) => Option.when(ref == DecisionOptionRef.Denizen(beta))(
        OathViolation.InvalidEventOrder("not with alpha")),
      note = (_, _) =>
        Some(LogNote(Some(PowerSourceRef.Card(alpha)), "barred",
        Vector.empty)))
    val restricting = Stand("a", alpha,
      effects = Map(window -> Vector(forbids)))
    val on = tree()
    val walked = powers(restricting, Stand("b", beta))
    val picked = answer(begin(table(), on, walked), on, walked, decisionId,
      DecisionOptionRef.Denizen(alpha))
    assertEquals(options(picked, on, walked), Vector(startWithOneModifier,
      DecisionOption.Unavailable(card(beta), OptionReason.Noted(
        restricting.id, LogNote(Some(PowerSourceRef.Card(alpha)), "barred",
          Vector.empty)))))

  test("Start says why the action cannot go on, and a pick that makes it " +
      "possible opens it"):
    val waive = Transform((_, _) => Vector.empty)
    val waiver = Stand("a", alpha,
      effects = Map(PowerWindow.SearchCost -> Vector(waive)))
    val on = tree(supply = 2)
    val walked = powers(waiver, Stand("b", beta))
    val first = begin(table(supply = 0), on, walked)
    assertEquals(options(first, on, walked).head,
      unavailable(startWithNoModifiers, ModifierChoice.cannotStart))
    val picked = answer(first, on, walked, decisionId,
      DecisionOptionRef.Denizen(alpha))
    assertEquals(options(picked, on, walked).head, startWithOneModifier)
    val begun = answer(picked, on, walked, decisionId, ModifierPicks.start)
    assertEquals(decide(begun, on, walked).decisionId, "stand.go")
    val done = accepted(ProcedureWalker.resolve(begun.ready, on,
      begun.pending, Answered("stand.go",
        DecisionAnswer.ChooseOneAnswer(go), p1), walked))
    assert(done.isInstanceOf[WalkerOutcome.Finished], done)

  test("once every modifier is picked, the action starts by itself"):
    val on = tree()
    val walked = powers(Stand("a", alpha))
    val picked = answer(begin(table(), on, walked), on, walked, decisionId,
      DecisionOptionRef.Denizen(alpha))
    assertEquals(decide(picked, on, walked).decisionId, "stand.go")
