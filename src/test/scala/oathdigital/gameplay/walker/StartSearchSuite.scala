package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  ModifierPicks, Transform}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** The dry run that walks a start past its modifier decision (walker-driven
  * game loop design, ruling 8; slice 4, plan-time ruling 1), over a
  * hand-built tree walked with stand-in modifiers. */
class StartSearchSuite extends munit.FunSuite:
  private val window = PowerWindow.SearchModifierSelection
  private val alpha = CatalogNames.denizen("Wrestlers")
  private val beta = CatalogNames.denizen("Mercenaries")
  private val gamma = CatalogNames.denizen("Errand Boy")
  private val go = DecisionOptionRef.Button("go")

  /** A stand-in modifier on `card`, pickable at the Search window, that
    * waives every cost node windowed `waives`. */
  private final case class Stand(name: String, card: DenizenId,
      waives: Option[PowerWindow] = None) extends ContributingPower:
    val id: PowerId = PowerId(s"stand.$name")
    val source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      waives.map(at => at -> Vector[Contribution](
        Transform((_, _) => Vector.empty))).toMap
    override def resolution: PowerResolution = PowerResolution.PlayerSelected
    override def selectionCard: Option[CardId] = Some(card)
    override def selectionWindows: Set[PowerWindow] = Set(window)

  /** One Supply spent at a node windowed `at`. */
  private def spending(at: PowerWindow): Operation =
    BuildOps((_, _) => Right(Vector(SpendSupply(p1, 1))), window = Some(at),
      required = true)

  private val ask: Operation = Decide("stand.go", p1,
    DecisionQuery.ChooseOne(Vector(DecisionOption.Button(go, "Go"))))

  /** Asks "Go", then spends one Supply at the Search cost window and one at
    * the Travel cost window. */
  private val rest: Operation = Sequence(Vector(ask,
    spending(PowerWindow.SearchCost), spending(PowerWindow.TravelCost)))

  private val tree: Operation =
    Sequence(Vector(ModifierChoice.loop(p1, window, rest, "Start"), rest))

  private def powers(stands: Stand*): WalkerPowers =
    WalkerPowers.selected(WalkerPowers(stands.toVector), Vector.empty)

  /** p1 at Ancient City with `supply` Supply, holding alpha, beta and gamma
    * faceup. */
  private def table(supply: Int): ReadyGame =
    Table.start.pawn(p1, "Ancient City").favor(p1, 3).supply(p1, supply)
      .adviser(p1, alpha).adviser(p1, beta).adviser(p1, gamma).ready

  private def started(supply: Int, stands: Stand*) =
    WalkerSimulation.started(tree, table(supply), powers(stands*))

  /** The decision the walk stands at once it is past the modifiers, and the
    * modifiers picked on the way. */
  private def reached(found: WalkerSimulation.Started, walking: WalkerPowers)
      (using munit.Location): (String, Vector[CardId]) = found.at match
    case Some((state, pending)) =>
      val decide = ProcedureWalker.parkedDecide(state, tree, pending, walking,
        probing = false)
      (decide.fold("none")(_.decisionId),
        ModifierPicks.cards(pending.answered))
    case None => fail("the walk finished")

  test("with nothing to pick, the walk is past the modifiers at once"):
    val found = started(2)
    assertEquals(found.map(reached(_, powers())),
      Right(("stand.go", Vector.empty)))

  test("Start with no pick comes first"):
    val walking = powers(Stand("a", alpha,
      waives = Some(PowerWindow.SearchCost)))
    val found = WalkerSimulation.started(tree, table(2), walking)
    assertEquals(found.map(reached(_, walking)),
      Right(("stand.go", Vector.empty)))

  test("a start that needs two of three modifiers is found"):
    val walking = powers(Stand("c", gamma),
      Stand("a", alpha, waives = Some(PowerWindow.SearchCost)),
      Stand("b", beta, waives = Some(PowerWindow.TravelCost)))
    val found = WalkerSimulation.started(tree, table(0), walking)
    val at = found.map(reached(_, walking))
    // The search may pick gamma on the way too; alpha and beta must be in.
    assertEquals(at.map(_._1), Right("stand.go"))
    assertEquals(at.map(found => Set[CardId](alpha, beta)
      .subsetOf(found._2.toSet)), Right(true))

  test("with no way through, the refusal is the reason Start shows"):
    val found = started(0, Stand("c", gamma),
      Stand("a", alpha, waives = Some(PowerWindow.SearchCost)))
    assertEquals(found.left.toOption, Some(OathViolation.StartUnavailable(
      ModifierChoice.cannotStart)))

  test("a start no route of which can finish is refused as the walk refuses"):
    // Picking gamma, the only modifier, starts the walk by itself, and "Go"
    // then spends Supply p1 does not have: the walk's own look-ahead leaves
    // it nothing to ask, so the search never gets past the modifiers.
    val found = started(0, Stand("c", gamma))
    assertEquals(found.left.toOption.collect {
      case OathViolation.CoreOperationRejected(code, _) => code
    }, Some("insufficient-supply"))

  test("what the walk spends before its first decision is reported"):
    val paying = Sequence(Vector(spending(PowerWindow.SearchCost), ask))
    val found = WalkerSimulation.started(paying, table(2), powers())
    assertEquals(found.map(_.operations.collect {
      case SpendSupply(_, amount, _) => amount
    }), Right(Vector(1)))
