package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver.{Contribution, ModifierPicks,
  PowerCtx}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** The kit every selected modifier stands on: it is offered only for its own
  * action, only when the player may use its card and can pay for it, and it
  * applies inside the walk by asking only about the node it is hooked on.
  */
class SelectedModifierSuite extends munit.FunSuite:
  import PowerFixture._

  private val card = CatalogNames.denizen("Tents")

  /** Stub of SelectedModifier: on a catalog modifier id (the Tents id unless
    * `idValue` says another), so its resolution is the catalog's; the card,
    * cost and actions are the ones passed in, its effects are empty, and
    * `appliesAt` answers `applies`. */
  private final case class Probe(cardId: CardId, override val cost: Cost,
      actions: Set[MajorActionType], idValue: String = "denizen.tents",
      applies: Boolean = true)
      extends SelectedModifier:
    def catalog = oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
    def id: PowerId = PowerId(idValue)
    def effects: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def appliesAt(ctx: PowerCtx): Boolean = applies

  private def ctx(ready: ReadyGame, power: SelectedModifier,
      window: PowerWindow): PowerCtx = PowerCtx(ready, actor, power.source,
    window, Vector.empty, Sequence(Vector.empty, Some(window)))

  private def selectable(ready: ReadyGame, power: SelectedModifier,
      window: PowerWindow = PowerWindow.TravelModifierSelection): Boolean =
    power.applicable(ctx(ready, power, window))

  private val travel: Set[MajorActionType] = Set(MajorActionType.Travel)
  private def free = Probe(card, Cost.free, travel)
  /** p1's Act at Ancient City with Tents at the site and `favor` favor. */
  private def tentsAtAncientCity(favor: Int = 1): Table =
    Table.start.pawn(p1, "Ancient City").denizen(card, at = "Ancient City")
      .favor(p1, favor)

  test("it is offered for its own action and no other"):
    val ready = tentsAtAncientCity().ready
    assert(selectable(ready, free))
    assert(!selectable(ready, free, PowerWindow.SearchModifierSelection))
    assert(!selectable(ready, free, PowerWindow.MusterModifierSelection))

  test("it is not offered when the player may not use its card"):
    assert(!selectable(quietTable, free))
    val faceDown = Table.from(asAdviser(quietTable, card, Orientation.FaceDown))
      .favor(actor, 1).unchecked
    assert(!selectable(faceDown, free))
    assert(selectable(Table.from(asAdviser(quietTable, card)).favor(actor, 1)
      .unchecked, free))

  test("a cost must be payable: enough favor, and an empty card"):
    val priced = Probe(card, Cost(favor = 1), travel)
    assert(selectable(tentsAtAncientCity(1).ready, priced))
    assert(!selectable(tentsAtAncientCity(0).ready, priced))
    val occupied = tentsAtAncientCity(1).tokens(card, favor = 1).ready
    assert(!selectable(occupied, priced))

  test("inside the walk it asks only about its own node"):
    val priced = Probe(card, Cost(favor = 1), travel)
    // No card and no favor, yet the fold at a walk window still applies it,
    // because a walk window must not depend on what the action spends.
    val broke = Table.start.favor(p1, 0).ready
    assert(priced.applicable(ctx(broke, priced, PowerWindow.TravelCost)))

  test("a cost is paid at the root of the action's tree, and only for a costed " +
      "modifier"):
    val priced = Probe(card, Cost(favor = 1), travel)
    val windows = priced.contributions.keySet
    assertEquals(windows, Set[PowerWindow](PowerWindow.TravelActionEligibility))
    assertEquals(free.contributions, Map.empty[PowerWindow, Vector[Contribution]])
    val both = Probe(card, Cost(favor = 1),
      Set(MajorActionType.Travel, MajorActionType.Search))
    assertEquals(both.contributions.keySet,
      Set[PowerWindow](PowerWindow.TravelActionEligibility))

  test("at the action's eligibility window a selected modifier always applies"):
    // A probe that applies at no node still applies at the eligibility
    // window, where its payment is made, and nowhere else.
    val priced = Probe(card, Cost(favor = 1), travel, applies = false)
    assert(priced.applicable(ctx(quietTable, priced,
      PowerWindow.TravelActionEligibility)))
    assert(!priced.applicable(ctx(quietTable, priced, PowerWindow.TravelCost)))

  test("the payment is what selecting it pays, for the combined check"):
    val priced = Probe(card, Cost(favor = 1), travel)
    val ready = tentsAtAncientCity(1).ready
    assertEquals(priced.selectionPayments(ready, actor), Vector(
      oathdigital.gameplay.operations.Costs.onCard(actor, card, Cost(favor = 1),
        catalog)))
    assertEquals(free.selectionPayments(ready, actor), Vector.empty)

  test("a payment and an effect at the same window keep the payment first"):
    // Stub of SelectedModifier: the Tents id, a cost of one favor and one
    // Transform that changes nothing.
    val both = new SelectedModifier:
      def catalog = oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
      def cardId: CardId = card
      def id: PowerId = PowerId("denizen.tents")
      def actions: Set[MajorActionType] = travel
      override def cost: Cost = Cost(favor = 1)
      def effects: Map[PowerWindow, Vector[Contribution]] = Map(
        PowerWindow.TravelActionEligibility ->
          Vector(oathdigital.gameplay.powerresolver.Transform((_, ops) => ops)))
    val transforms = both.contributions(
      PowerWindow.TravelActionEligibility).collect {
        case oathdigital.gameplay.powerresolver.Transform(fn) => fn }
    assertEquals(transforms.size, 2)
    val (first, second) = (transforms(0), transforms(1))
    val at = ctx(quietTable, both, PowerWindow.TravelActionEligibility)
    assertEquals(first(at, Vector.empty), Vector[Operation](
      oathdigital.gameplay.operations.Costs.onCard(actor, card, Cost(favor = 1),
        catalog)))
    assertEquals(second(at, Vector.empty), Vector.empty[Operation])

  test("its option names its card, at its actions' selection windows"):
    val both = Probe(card, Cost.free,
      Set(MajorActionType.Travel, MajorActionType.Search))
    assertEquals(both.selectionCard, Some(card))
    assertEquals(both.selectionWindows, Set[PowerWindow](
      PowerWindow.TravelModifierSelection,
      PowerWindow.SearchModifierSelection))

  test("once picked it applies at its selection window, though it could not " +
      "be paid for again"):
    val priced = Probe(card, Cost(favor = 1), travel)
    // The card now holds the favor the pick paid, so a second payment would
    // be refused; the pick still applies, for its option restrictions.
    val paid = tentsAtAncientCity(0).tokens(card, favor = 1).ready
    val answered = Vector(Answered(
      ModifierPicks.decisionId(PowerWindow.TravelModifierSelection),
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(card)), p1))
    val window = PowerWindow.TravelModifierSelection
    assert(!priced.applicable(ctx(paid, priced, window)))
    assert(priced.applicable(ctx(paid, priced, window)
      .copy(answered = answered)))
