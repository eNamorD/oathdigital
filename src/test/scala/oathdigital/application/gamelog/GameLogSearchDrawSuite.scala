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
    // The bottom three, named, so this still fails if the draw is not
    // reshaped and only the note fires.
    assertEquals(drew, "Drew Second Chance, Fae Merchant and Threatening " +
      "Roar from the World Deck")

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
    // One card from each pile, so this still fails if the draw is not
    // reshaped; the Search's line names the pile it was priced at.
    assertEquals(drew, "Drew Threatening Roar and Fae Merchant from the " +
      "Cradle discard")

  // Wine of Welcome rewrites a world-deck draw only, so a Search of a
  // discard pile under it draws the pile's top as usual: its line must not
  // claim a bottom draw. The pile stays choosable while Wine is picked,
  // warned (`WineOfWelcomeSuite`), so this is reachable every time.
  test("Wine of Welcome: no line when the Search took a discard pile"):
    val lines = searchLines("wine-of-welcome-discard", Table.start
      .relic(p1, "Wine of Welcome")
      .discarded(Region.Cradle, "Threatening Roar", "Fae Merchant"),
      Vector(WineOfWelcome.id), SearchSource.RegionalDiscard(Region.Cradle))
    assert(!lines.exists(_.contains("Drawing from the bottom")), lines)
    assert(lineStarting(lines, "Drew ").endsWith(" from the Cradle discard"),
      lines)
