package oathdigital.application.gamelog

import oathdigital.testkit.MenuPlay
import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powers.search.{LandWarden, SpinningBee}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseOneAnswer, PartitionAnswer}
import oathdigital.testkit.{Answers, CatalogNames, Table}
import oathdigital.testkit.Table.p1
import LogScripts._

/** The lines of the Searches slice 4 adds: Spinning Bee's own line, a
  * Search that keeps none, and one that keeps two (catalog batch 6, slice
  * 4 plan-time rulings 2 and 10). */
class GameLogSearchKeepSuite extends munit.FunSuite:
  /** p1's depth-1 lines after a Search at `table` that picks `modifiers`,
    * with `answers` over the defaults. */
  private def searchLines(name: String, table: Table,
      modifiers: Vector[PowerId], answers: Answers)(
      using munit.Location): Vector[String] =
    val (service, act) = atTable(name, table.banditsRefilled)
    act.withAnswers(answers.orElse(ModifierPlay.answers(
      PowerWindow.SearchModifierSelection, modifiers)))
      .after(MenuPlay.start(p1, ActionRef.Search))
    texts(format(Script(name, service, p1), Some(p1)).filter(_.depth == 1))

  private def declaring(suit: Suit): Answers = {
    case park if park.decisionId == SpinningBee.suitDecisionId =>
      ChooseOneAnswer(DecisionOptionRef.FavorBank(suit))
  }

  /** p1 at Ancient City holding Spinning Bee faceup. The Cradle pile holds
    * Homesteaders (Hearth) under Wrestlers (Order) under Defame
    * (Discord). */
  private val besideBee: Table = Table.start.pawn(p1, "Ancient City")
    .adviser(p1, "Spinning Bee")
    .discarded(Region.Cradle, "Homesteaders", "Wrestlers", "Defame")

  test("Spinning Bee: its line names the pile and the suit, before the " +
      "Search line"):
    val lines = searchLines("spinning-bee", besideBee,
      Vector(SpinningBee.id), declaring(Suit.Order))
    val bee = lines.indexOf(
      "Spinning Bee: Drawing from the Cradle discard until Order card is drawn")
    val drew = lines.indexWhere(_.startsWith("Drew "))
    assert(bee >= 0 && drew > bee, lines)
    assert(lines(drew).endsWith(" from the Cradle discard"), lines(drew))
    assert(lines.exists(_.startsWith("Kept ")), lines)
    // The suit is told by Bee's line, not as a favor bank pick.
    assert(!lines.exists(_.contains(" bank")), lines)

  // This power discards the whole hand inside the draw's own window, so by
  // the time the Search's draw line runs there is nothing in the hand and
  // neither of the Search's own lines is written: Bee's own second line is
  // the only thing that says what happened (slice 3 plan-time ruling 5).
  test("Spinning Bee with no match: its line names what it discarded"):
    val lines = searchLines("spinning-bee-none", besideBee,
      Vector(SpinningBee.id), declaring(Suit.Nomad))
    assert(lines.exists(line => line.startsWith("Spinning Bee: Drew ") &&
      line.endsWith(", and discarded them all")), lines)
    assert(!lines.exists(_.startsWith("Drew ")), lines)
    assert(!lines.exists(_.startsWith("Kept ")), lines)

  test("Land Warden: kept two, then a line for each play"):
    val drawn = Vector("Threatening Roar", "Fae Merchant", "Second Chance")
      .map(CatalogNames.denizen(_))
    def placed(card: DenizenId, key: String) =
      DecisionPlacement(DecisionOptionRef.Denizen(card), key)
    val keepFirstTwo: Answers = {
      case park if park.decisionId == SearchProcedure.cardDecisionId =>
        PartitionAnswer(Vector(placed(drawn(0), SearchProcedure.keepKey),
          placed(drawn(1), SearchProcedure.keepKey),
          placed(drawn(2), SearchProcedure.discardKey)))
    }
    val lines = searchLines("land-warden", Table.start
      .pawn(p1, "Ancient City").adviser(p1, "Land Warden")
      .worldDeckTop(drawn*), Vector(LandWarden.id), keepFirstTwo)
    assert(lines.contains("Kept Threatening Roar and Fae Merchant"), lines)
    // By default the first goes to the discard pile, its first option.
    // Land Warden then leaves the second only the site.
    assert(lines.contains(
      "Discarded Threatening Roar to the Provinces discard"), lines)
    assert(lines.contains("Played Fae Merchant to Ancient City"), lines)
