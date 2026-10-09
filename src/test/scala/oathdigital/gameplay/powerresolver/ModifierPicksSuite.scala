package oathdigital.gameplay.powerresolver

import oathdigital.model._
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Table.p1

/** The modifier decision's vocabulary: its id per window, its Start answer,
  * and the picks read back out of the answers (modifier walker design,
  * plan-time rulings 1 and 6). */
class ModifierPicksSuite extends munit.FunSuite:
  private val tents = CatalogNames.denizen("Tents")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val search = ModifierPicks.decisionId(
    PowerWindow.SearchModifierSelection)

  /** A stand-in on `card`, pickable at `windows`: player-selected unless
    * `automatic`. */
  private final case class Pickable(name: String, card: CardId,
      windows: Set[PowerWindow], automatic: Boolean = false)
      extends ContributingPower:
    val id: PowerId = PowerId(s"probe.$name")
    val source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    val contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def resolution: PowerResolution =
      if automatic then PowerResolution.Automatic
      else PowerResolution.PlayerSelected
    override def selectionCard: Option[CardId] = Some(card)
    override def selectionWindows: Set[PowerWindow] = windows

  private val atSearch = Pickable("search", tents,
    Set(PowerWindow.SearchModifierSelection))
  private val atTravel = Pickable("travel", tents,
    Set(PowerWindow.TravelModifierSelection))
  private val second = Pickable("second", wrestlers,
    Set(PowerWindow.SearchModifierSelection))
  private val pool = Vector(atTravel, atSearch, second)

  private def picked(card: DenizenId, id: String = search): Answered =
    Answered(id, DecisionAnswer.ChooseOneAnswer(
      DecisionOptionRef.Denizen(card)), p1)
  private val started =
    Answered(search, DecisionAnswer.ChooseOneAnswer(ModifierPicks.start), p1)

  test("the decision is named for its action"):
    assertEquals(search, "search.modifiers")
    assertEquals(ModifierPicks.decisionId(
      PowerWindow.TravelModifierSelection), "travel.modifiers")
    assert(ModifierPicks.isDecision(search))
    assert(!ModifierPicks.isDecision("search.source"))

  test("the picks are the powers the answers name, in pick order, at the " +
      "answered window"):
    val answered = Vector(picked(wrestlers), picked(tents), started)
    assertEquals(ModifierPicks.picks(pool, answered),
      Vector(second.id, atSearch.id))
    assertEquals(ModifierPicks.cards(answered),
      Vector[CardId](wrestlers, tents))

  test("an automatic power on a picked card is not a pick"):
    val automatic = Pickable("auto", tents,
      Set(PowerWindow.SearchModifierSelection), automatic = true)
    assertEquals(ModifierPicks.picks(Vector(automatic), Vector(picked(tents))),
      Vector.empty)

  test("Start ends the decision; settled means never asked, started, or " +
      "moved on"):
    val source = Answered("search.source", DecisionAnswer.ChooseOneAnswer(
      DecisionOptionRef.Button("world")), p1)
    assert(!ModifierPicks.started(Vector(picked(tents)), search))
    assert(ModifierPicks.started(Vector(picked(tents), started), search))
    assert(ModifierPicks.settled(Vector.empty))
    assert(!ModifierPicks.settled(Vector(picked(tents))))
    assert(ModifierPicks.settled(Vector(picked(tents), started)))
    assert(ModifierPicks.settled(Vector(picked(tents), source)))

  test("a card is picked at the window its answer names"):
    val answered = Vector(picked(tents))
    assert(ModifierPicks.isPicked(answered,
      PowerWindow.SearchModifierSelection, tents))
    assert(!ModifierPicks.isPicked(answered,
      PowerWindow.TravelModifierSelection, tents))
    assertEquals(ModifierPicks.powerFor(pool,
      PowerWindow.TravelModifierSelection, DecisionOptionRef.Denizen(tents)),
      Some(atTravel))
