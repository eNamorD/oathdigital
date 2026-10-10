package oathdigital.application.gamelog

import oathdigital.application.{GameCommand, StartPayload}
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
      .after(GameCommand.StartWalker(ActionRef.Search, StartPayload(p1)))
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
    assert(lines(drew).contains(" from the Cradle discard and kept "),
      lines(drew))

  test("Spinning Bee with no match: drew the pile and kept none"):
    val lines = searchLines("spinning-bee-none", besideBee,
      Vector(SpinningBee.id), declaring(Suit.Nomad))
    val drew = lineStarting(lines, "Drew ")
    assert(drew.endsWith(" from the Cradle discard and kept none"), drew)

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
    val drew = lineStarting(lines, "Drew ")
    assert(drew.endsWith(" and kept Threatening Roar and Fae Merchant"),
      drew)
    // By default the first goes to the discard pile, its first option.
    // Land Warden then leaves the second only the site.
    assert(lines.contains(
      "Discarded Threatening Roar to the Provinces discard"), lines)
    assert(lines.contains("Played Fae Merchant to Ancient City"), lines)
