package oathdigital.gameplay.powers.search

import oathdigital.testkit.MenuPlay
import oathdigital.application.{GameCommand, GameProjector, LoadedGame,
  TreeDecision}
import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.walker.{FollowUpOffer, FollowUpSkipped}
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.protocol.projection.DecisionQueryProjection
import oathdigital.testkit.{CatalogNames, Situation, Table}
import oathdigital.testkit.Table.p1

/** Hunting Party (card 122): after a Search of the world deck, a Campaign is
  * owed, spending no Supply; a discard pile warns and stays choosable
  * (follow-up actions design, ruling 10). */
class HuntingPartySuite extends munit.FunSuite:
  private val hunting = CatalogNames.denizen("Hunting Party")
  private val modifiers = Vector(HuntingParty.id)
  private val owed = QueuedProcedure(p1, ActionRef.Campaign, Vector.empty,
      Some(PowerSourceRef.Card(hunting)), QueueKind.Offer)
  private val region = Region.Cradle // Ancient City's region
  /** Plain denizens for the world deck and the Cradle discard. */
  private val deckTop = Vector("Wrestlers", "Errand Boy", "Mercenaries")
    .map(CatalogNames.denizen(_))
  private val discardPile = Vector("Code of Honor", "Messenger", "Shield Wall")
    .map(CatalogNames.denizen(_))

  /** p1 in Act at Ancient City holding Hunting Party faceup, with 5 Supply,
    * the world deck topped by three plain cards and the Cradle discard
    * holding three more. */
  private def board: Table = Table.start
    .worldDeckTop(deckTop*).discarded(region, discardPile*)
    .pawn(p1, "Ancient City").supply(p1, 5).adviser(p1, hunting)

  /** The offer the driver makes after a whole Search of `pile` with
    * `selected`, read from the Skip that answers it; every other later
    * decision is answered by default. */
  private def offeredAfter(pile: SearchSource, selected: Vector[PowerId])
      : Option[QueuedProcedure] =
    val source: oathdigital.testkit.Answers = {
      case park if park.decisionId == SearchProcedure.sourceDecisionId =>
        ChooseOneAnswer(SearchSource.ref(pile))
    }
    val skipOffer: oathdigital.testkit.Answers = {
      case park if park.decisionId == FollowUpOffer.decisionId =>
        ChooseOneAnswer(FollowUpOffer.skip)
    }
    val after = board.situation(Situation.productionRules(catalog))
      .withAnswers(source.orElse(skipOffer).orElse(ModifierPlay.answers(
        PowerWindow.SearchModifierSelection, selected)))
      .after(MenuPlay.start(p1, ActionRef.Search))
    after.journal.collectFirst { case FollowUpSkipped(entry) => entry }

  test("a Search of the world deck offers a Campaign"):
    assertEquals(offeredAfter(SearchSource.WorldDeck, modifiers), Some(owed))

  test("a Search of a discard pile offers nothing"):
    assertEquals(offeredAfter(SearchSource.RegionalDiscard(region), modifiers),
      None)

  test("without the selection nothing is offered"):
    assertEquals(offeredAfter(SearchSource.WorldDeck, Vector.empty), None)

  test("while it is selected the discard pile warns and stays choosable"):
    val rules = Situation.productionRules(catalog)
    // Hunting Party is the only modifier, so once it is picked the Search
    // starts by itself and parks on its source.
    val parked = board.situation(rules)
      .parkedAfter(MenuPlay.start(p1, ActionRef.Search))
      .parkedAfter(GameCommand.ResolveWalker(p1, TreeDecision(
        ModifierPicks.decisionId(PowerWindow.SearchModifierSelection),
        ModifierPlay.pick(HuntingParty.id))))
    val shown = new GameProjector(catalog).project("hunting",
      LoadedGame(parked.state, 1), p1)
    val options = shown.walkerDecision.flatMap(_.query).collect {
      case one: DecisionQueryProjection.ChooseOne => one.options
    }.getOrElse(Vector.empty)
    assertEquals(options.find(_.kind == "discard-pile").map(o =>
      (o.state, o.reason)),
      Some(("warning", Some(HuntingParty.discardWarning))))
    assertEquals(options.find(_.kind == "deck").map(_.state),
      Some("available"))
