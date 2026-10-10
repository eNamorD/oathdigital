package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, EconomyActions, Look, Outcomes,
  Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Trade asks for its modifiers inside the walk, after its resource
  * (modifier walker design, slice 3). */
class TradeModifierChoiceSuite extends munit.FunSuite:
  import EconomyActions.parkedDecision
  import SearchFixture.rules

  private val birdsong = CatalogNames.denizen("Birdsong")
  /** A plain Beast denizen to trade with: it has no Muster or Trade power. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val home = "Ancient City"
  private val modifierDecisionId =
    ModifierPicks.decisionId(PowerWindow.TradeModifierSelection)
  private val startTrade: DecisionOption =
    DecisionOption.Button(ModifierPicks.start, "Start Trade")

  /** p1 at Ancient City beside Errand Boy, with 4 favor, 2 faceup secrets
    * and `supply` Supply, holding Birdsong faceup when `withBirdsong`. */
  private def besideErrandBoy(supply: Int, withBirdsong: Boolean = true)
      : ReadyGame =
    val table = Table.start.pawn(p1, at = home).denizen(errandBoy, at = home)
      .favor(p1, 4).secrets(p1, faceUp = 2).supply(p1, supply)
    (if withBirdsong then table.adviser(p1, birdsong) else table).ready

  private def started(ready: ReadyGame, resource: String): OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.Trade, p1,
      startArgs = Vector(DecisionOptionRef.Button(resource))))

  private def options(state: OathState)(using munit.Location)
      : Vector[DecisionOption] = parkedDecision(state).query match
    case DecisionQuery.ChooseOne(listed, _, _) => listed
    case other => fail(s"expected a choose-one, got $other")

  test("a Trade starts on the modifier decision, and Start keeps its " +
      "resource"):
    val begun = started(besideErrandBoy(supply = 7), "secret")
    assertEquals(parkedDecision(begun.state).decisionId, modifierDecisionId)
    assertEquals(options(begun.state), Vector(startTrade,
      DecisionOption.Denizen(DecisionOptionRef.Denizen(birdsong))))
    val going = accepted(rules.resolveWalker(begun.state, p1,
      modifierDecisionId, ModifierPlay.start))
    val source = parkedDecision(going.state)
    assertEquals(source.decisionId, TradeProcedure.decisionId)
    assertEquals(source.query match
      case DecisionQuery.ChooseOne(_, heading, _) => heading
      case _ => None,
      Some("Choose a card to Trade for secrets"))

  test("with no modifier to pick, a Trade starts on its source"):
    val begun = started(besideErrandBoy(supply = 7, withBirdsong = false),
      "favor")
    assertEquals(parkedDecision(begun.state).decisionId,
      TradeProcedure.decisionId)

  test("Start says why the Trade cannot go on, until a pick waives the " +
      "Supply"):
    val begun = started(besideErrandBoy(supply = 0), "favor")
    assertEquals(options(begun.state).head, DecisionOption.Unavailable(
      startTrade, OptionReason.Written("Needs 1 Supply; you have 0")))
    val picked = accepted(rules.resolveWalker(begun.state, p1,
      modifierDecisionId, ModifierPlay.pick(Birdsong.id)))
    val done = accepted(rules.resolveWalker(picked.state, p1,
      TradeProcedure.decisionId, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Denizen(errandBoy))))
    // Trade for favor pays one secret onto the card.
    assertEquals(Look(Outcomes.ready(done.state)).tokensOn(errandBoy),
      Tokens(0, 1))
