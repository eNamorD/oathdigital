package oathdigital.application.gamelog

import oathdigital.application.{GameCommand, StartPayload}
import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powers.search.{Mushrooms, PaintedTrumpet,
  WineOfWelcome}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{Answers, Table}
import oathdigital.testkit.Table.p1
import LogScripts._

/** The Search line for the draws catalog batch 6 adds: from the bottom of
  * a pile, and from each discard pile (slice 3 plan-time ruling 1). */
class GameLogSearchDrawSuite extends munit.FunSuite:
  /** p1's depth-1 lines after a Search at `table` that picks `modifiers`,
    * then `source`, every other decision answered by default. */
  private def searchLines(name: String, table: Table,
      modifiers: Vector[PowerId], source: SearchSource)(
      using munit.Location): Vector[String] =
    val (service, act) = atTable(name, table.banditsRefilled)
    val pile: Answers = {
      case park if park.decisionId == SearchProcedure.sourceDecisionId =>
        ChooseOneAnswer(SearchSource.ref(source))
    }
    act.withAnswers(pile.orElse(ModifierPlay.answers(
      PowerWindow.SearchModifierSelection, modifiers)))
      .after(GameCommand.StartWalker(ActionRef.Search, StartPayload(p1)))
    texts(format(Script(name, service, p1), Some(p1)).filter(_.depth == 1))

  test("Wine of Welcome: drew from the bottom of the World Deck"):
    val lines = searchLines("wine-of-welcome", Table.start
      .relic(p1, "Wine of Welcome")
      .worldDeckBottom("Threatening Roar", "Fae Merchant", "Second Chance"),
      Vector(WineOfWelcome.id), SearchSource.WorldDeck)
    val drew = lineStarting(lines, "Drew ")
    assert(drew.contains(" from the bottom of the World Deck and kept "),
      drew)

  test("Mushrooms: drew one card from the bottom of the Cradle discard"):
    val lines = searchLines("mushrooms", Table.start
      .denizen("Mushrooms", "Ancient City")
      .discarded(Region.Cradle, "Threatening Roar", "Fae Merchant"),
      Vector(Mushrooms.id), SearchSource.RegionalDiscard(Region.Cradle))
    val drew = lineStarting(lines, "Drew ")
    assert(drew.startsWith(
      "Drew Threatening Roar from the bottom of the Cradle discard and " +
        "kept "), drew)

  test("Painted Trumpet: drew from each discard pile"):
    val lines = searchLines("painted-trumpet", Table.start
      .relic(p1, "Painted Trumpet")
      .discarded(Region.Cradle, "Threatening Roar")
      .discarded(Region.Provinces, "Fae Merchant"),
      Vector(PaintedTrumpet.id), SearchSource.RegionalDiscard(Region.Cradle))
    val drew = lineStarting(lines, "Drew ")
    assert(drew.contains(" from each discard pile and kept "), drew)
