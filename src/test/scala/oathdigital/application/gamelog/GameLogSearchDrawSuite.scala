package oathdigital.application.gamelog

import oathdigital.testkit.MenuPlay
import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powers.search.{Mushrooms, PaintedTrumpet,
  WineOfWelcome}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{Answers, Table}
import oathdigital.testkit.Table.p1
import LogScripts._

/** The lines for the draws catalog batch 6 adds: from the bottom of a
  * pile, and from each discard pile. Each is told by the power that
  * reshapes the draw, before the draw, because the Search's own line names
  * the pile and cannot know what a power did to the draw operation (slice 3
  * plan-time ruling 5). The Search's line follows, naming the pile it was
  * priced at. */
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
      .after(MenuPlay.start(p1, ActionRef.Search))
    texts(format(Script(name, service, p1), Some(p1)).filter(_.depth == 1))

  /** That `line` comes before the Search's own "Drew …", and what that
    * line reads. */
  private def before(lines: Vector[String], line: String)(
      using munit.Location): String =
    val own = lines.indexWhere(_.startsWith("Drew "))
    assert(own >= 0, lines)
    assertEquals(lines.indexOf(line), own - 1, lines)
    lines(own)

  test("Wine of Welcome: its line says the draw came from the bottom"):
    val lines = searchLines("wine-of-welcome", Table.start
      .relic(p1, "Wine of Welcome")
      .worldDeckBottom("Threatening Roar", "Fae Merchant", "Second Chance"),
      Vector(WineOfWelcome.id), SearchSource.WorldDeck)
    val drew = before(lines,
      "Wine of Welcome: Drawing from the bottom of the World Deck")
    assert(drew.endsWith(" from the World Deck"), drew)

  test("Mushrooms: its line says the card came from the bottom"):
    val lines = searchLines("mushrooms", Table.start
      .denizen("Mushrooms", "Ancient City")
      .discarded(Region.Cradle, "Threatening Roar", "Fae Merchant"),
      Vector(Mushrooms.id), SearchSource.RegionalDiscard(Region.Cradle))
    val drew = before(lines, "Mushrooms: Drawing one card from the bottom " +
      "of the Cradle discard")
    assertEquals(drew, "Drew Threatening Roar from the Cradle discard")

  test("Painted Trumpet: its line says the cards came from every pile"):
    val lines = searchLines("painted-trumpet", Table.start
      .relic(p1, "Painted Trumpet")
      .discarded(Region.Cradle, "Threatening Roar")
      .discarded(Region.Provinces, "Fae Merchant"),
      Vector(PaintedTrumpet.id), SearchSource.RegionalDiscard(Region.Cradle))
    val drew = before(lines,
      "Painted Trumpet: Drawing the top card of each discard pile instead")
    assert(drew.endsWith(" from the Cradle discard"), drew)
