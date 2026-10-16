package oathdigital.gameplay

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.cardplay.WildCry
import oathdigital.gameplay.powers.search.NewsFromAfar
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Outcomes, ParkedNode, PowerRules,
  Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Search and facedown-adviser play ask for their modifiers inside the walk
  * (modifier walker design, slice 1). */
class SearchModifierChoiceSuite extends munit.FunSuite:
  private val rules = PowerRules.withWalkerPowers
  private val walkerPowers = WalkerPowerCatalog.default(catalog)
  private val search = ModifierPicks.decisionId(
    PowerWindow.SearchModifierSelection)
  private val news = CatalogNames.denizen("News from Afar")

  /** p1 at Ancient City with `supply` Supply and `favor` favor, News from
    * Afar faceup. */
  private def withNews(supply: Int, favor: Int): ReadyGame =
    Table.start.pawn(p1, "Ancient City").supply(p1, supply).favor(p1, favor)
      .adviser(p1, news).ready

  private def started(ready: ReadyGame): OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.Search, p1))

  /** The decision the walk is parked on, rebuilt as the projector does. */
  private def parked(state: OathState)(using munit.Location): Decide =
    ParkedNode.of(state, catalog, walkerPowers) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide
      case other => fail(s"expected a parked decision, got $other")

  private def options(transition: OathTransition)(using munit.Location)
      : Vector[DecisionOption] = parked(transition.state).query match
    case one: DecisionQuery.ChooseOne => one.options
    case other => fail(s"expected a choose-one, got $other")

  private def newsOption: DecisionOption =
    DecisionOption.Denizen(DecisionOptionRef.Denizen(news))

  test("a Search starts on the modifier decision when a modifier can be " +
      "picked"):
    val begun = started(withNews(supply = 5, favor = 2))
    assertEquals(parked(begun.state).decisionId, search)
    assertEquals(options(begun), Vector(
      DecisionOption.Button(ModifierPicks.start, "Start Search"),
      DecisionOption.Priced(newsOption, OptionPrice(favor = 2))))

  test("with no modifier to pick, a Search starts on its source"):
    val begun = started(Table.start.pawn(p1, "Ancient City").ready)
    assertEquals(parked(begun.state).decisionId,
      SearchProcedure.sourceDecisionId)

  test("Start says why the Search cannot go on, until a pick makes it free"):
    val begun = started(withNews(supply = 0, favor = 2))
    assertEquals(options(begun).head, DecisionOption.Unavailable(
      DecisionOption.Button(ModifierPicks.start, "Start Search"),
      OptionReason.Written("Needs 2 Supply; you have 0")))
    val picked = accepted(rules.resolveWalker(begun.state, p1, search,
      ModifierPlay.pick(NewsFromAfar.id)))
    // The favor is paid at the pick, onto the card.
    assertEquals(Outcomes.ready(picked.state).game.current.players
      .find(_.player == p1).map(_.board.favor), Some(0))
    assertEquals(parked(picked.state).decisionId,
      SearchProcedure.sourceDecisionId)

  test("a picked modifier pays once, at the pick"):
    val ready = withNews(supply = 5, favor = 3)
    val searched = accepted(SearchPlay.searched(rules, Ready(ready), p1,
      modifiers = Vector(NewsFromAfar.id)))
    assertEquals(Outcomes.ready(searched.state).game.current.players
      .find(_.player == p1).map(_.board.favor), Some(1))

  test("a facedown-adviser play offers the Search modifiers first"):
    val wildCry = CatalogNames.denizen("Wild Cry")
    val ready = Table.start.pawn(p1, "Ancient City")
      .denizen(wildCry, at = "Ancient City")
      .adviser(p1, "Errand Boy", facedown = true).ready
    val begun = accepted(rules.startWalker(Ready(ready),
      ActionRef.PlayFacedownAdviser, p1))
    assertEquals(parked(begun.state).decisionId, search)
    assertEquals(options(begun).head,
      DecisionOption.Button(ModifierPicks.start, "Play facedown adviser"))
    val picked = accepted(ModifierPlay.started(rules, Ready(ready),
      ActionRef.PlayFacedownAdviser, p1, PowerWindow.SearchModifierSelection,
      Vector(WildCry.id)))
    assertEquals(parked(picked.state).decisionId,
      CardPlayProcedure.facedownAdviserDecisionId)
