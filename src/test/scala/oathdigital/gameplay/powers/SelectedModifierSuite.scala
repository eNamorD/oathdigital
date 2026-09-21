package oathdigital.gameplay.powers

import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** The kit every selected modifier stands on: it is offered only for its own
  * action, only when the player may use its card and can pay for it, and it
  * applies inside the walk by asking only about the node it is hooked on.
  */
class SelectedModifierSuite extends munit.FunSuite {
  import PowerFixture._

  private val card = DenizenId("29")

  /** A test double on a catalog modifier id, so its resolution is the catalog's. */
  private final case class Probe(cardId: CardId, override val cost: Cost,
      actions: Set[MajorActionType], idValue: String = "denizen.tents")
      extends SelectedModifier {
    def catalog = oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
    def id: PowerId = PowerId(idValue)
    def effects: Map[PowerWindow, Vector[Contribution]] = Map.empty
  }

  private def ctx(ready: ReadyGame, power: SelectedModifier,
      window: PowerWindow): PowerCtx = PowerCtx(ready, actor, power.source,
    window, Vector.empty, Sequence(Vector.empty, Some(window)))

  private def selectable(ready: ReadyGame, power: SelectedModifier,
      window: PowerWindow = PowerWindow.TravelModifierSelection): Boolean =
    power.applicable(ctx(ready, power, window))

  private val travel: Set[MajorActionType] = Set(MajorActionType.Travel)
  private def free = Probe(card, Cost.free, travel)
  private def atHomeWith(cost: Int = 1) = withBoard(atHome(base, card))(
    _.copy(favor = cost))

  test("its resolution is read from the catalog") {
    assertEquals(free.resolution, PowerResolution.PlayerSelected)
    assertEquals(Probe(card, Cost.free, travel, "denizen.toll-roads").resolution,
      PowerResolution.Automatic)
  }

  test("it is offered for its own action and no other") {
    val ready = atHomeWith()
    assert(selectable(ready, free))
    assert(!selectable(ready, free, PowerWindow.SearchModifierSelection))
    assert(!selectable(ready, free, PowerWindow.MusterModifierSelection))
  }

  test("it is not offered when the player may not use its card") {
    assert(!selectable(base, free))
    val faceDown = withBoard(asAdviser(base, card, Orientation.FaceDown))(
      _.copy(favor = 1))
    assert(!selectable(faceDown, free))
    assert(selectable(withBoard(asAdviser(base, card))(_.copy(favor = 1)), free))
  }

  test("a cost must be payable: enough favor, and an empty card") {
    val priced = Probe(card, Cost(favor = 1), travel)
    assert(selectable(atHomeWith(1), priced))
    assert(!selectable(atHomeWith(0), priced))
    val occupied = atHomeWith(1).updateCurrent(c => c.copy(map = c.map.copy(
      sites = c.map.sites.updated(home(base), c.map.sites(home(base)).copy(
        denizens = c.map.sites(home(base)).denizens.map {
          case d: DenizenState if d.id == card => d.copy(tokens = Tokens(1, 0))
          case other => other
        })))))
    assert(!selectable(occupied, priced))
  }

  test("inside the walk it asks only about its own node") {
    val priced = Probe(card, Cost(favor = 1), travel)
    // No card and no favor, yet the fold at a walk window still applies it,
    // because a walk window must not depend on what the action spends.
    assert(priced.applicable(ctx(base, priced, PowerWindow.TravelCost)))
  }

  test("a cost is paid at the root of the action's tree, and only for a costed " +
      "modifier") {
    val priced = Probe(card, Cost(favor = 1), travel)
    val windows = priced.contributions.keySet
    assertEquals(windows, Set[PowerWindow](PowerWindow.TravelActionEligibility))
    assertEquals(free.contributions, Map.empty[PowerWindow, Vector[Contribution]])
  }

  test("at the action's eligibility window a selected modifier always applies") {
    val priced = Probe(card, Cost(favor = 1), travel)
    assert(priced.applicable(ctx(base, priced,
      PowerWindow.TravelActionEligibility)))
  }

  test("the payment is what selecting it pays, for the combined check") {
    val priced = Probe(card, Cost(favor = 1), travel)
    val ready = atHomeWith(1)
    assertEquals(priced.selectionPayments(ready, actor).size, 1)
    assertEquals(free.selectionPayments(ready, actor), Vector.empty)
  }

  test("a payment and an effect at the same window keep the payment first") {
    val both = new SelectedModifier {
      def catalog = oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
      def cardId: CardId = card
      def id: PowerId = PowerId("denizen.tents")
      def actions: Set[MajorActionType] = travel
      override def cost: Cost = Cost(favor = 1)
      def effects: Map[PowerWindow, Vector[Contribution]] = Map(
        PowerWindow.TravelActionEligibility ->
          Vector(oathdigital.gameplay.powerresolver.Transform((_, ops) => ops)))
    }
    assertEquals(both.contributions(PowerWindow.TravelActionEligibility).size, 2)
  }

  test("the catalog cards helper finds a power's card, or nothing") {
    assertEquals(CatalogCards.denizen(catalog, PowerId("denizen.tents")),
      Some(card))
    assertEquals(CatalogCards.relic(catalog, PowerId("relic.dragonskin-drum")),
      Some(RelicId("R20")))
    assertEquals(CatalogCards.edifice(catalog, PowerId("edifice.e28.ruined")),
      Some(EdificeId("E28")))
    assertEquals(CatalogCards.denizen(catalog, PowerId("denizen.nobody")), None)
  }
}
