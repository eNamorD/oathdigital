package oathdigital.gameplay.powers.search

import oathdigital.gameplay.{PlacementFixture, SearchPlay}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powers.{SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, ParkedNode, PlacementPicks,
  Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Land Warden (card 130): for 1 favor, a Search may keep two cards,
  * played in the order kept. When the first does not go to a site, the
  * second may go only to one (catalog batch 6, slice 4). */
class LandWardenSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val walkerPowers = WalkerPowerCatalog.default(catalog)
  private val landWarden = CatalogNames.denizen("Land Warden")
  private val modifiers = Vector(LandWarden.id)
  /** Three Beast cards with no power of their own, on top of the world
    * deck: playing one has no effect on the others. */
  private val drawn = SearchFixture.anyPlainDenizensOf(Suit.Beast).take(3)

  /** p1 at Ancient City (room for three, empty) with the start's 1 favor,
    * 5 Supply and the world deck topped by `drawn`, holding Land Warden
    * faceup unless `warden` is false. */
  private def atAncientCity(warden: Boolean = true): Table =
    val table = Table.start.pawn(p1, "Ancient City").supply(p1, 5)
      .worldDeckTop(drawn*)
    if warden then table.adviser(p1, landWarden) else table

  /** p1 searches the world deck with `picked`. */
  private def searched(table: Table, picked: Vector[PowerId] = modifiers)(
      using munit.Location): OathTransition =
    accepted(SearchPlay.searched(rules, Ready(table.ready), p1,
      SearchSource.WorldDeck, picked))

  /** Keeps `first`, then `second`, and discards the third drawn card. */
  private def keepingBoth(from: OathTransition, first: DenizenId,
      second: DenizenId): Either[OathViolation, OathTransition] =
    def placed(card: DenizenId, key: String) =
      DecisionPlacement(PlacementPicks.optionNaming(card), key)
    rules.resolveWalker(from.state, p1, SearchProcedure.cardDecisionId,
      DecisionAnswer.PartitionAnswer(Vector(
        placed(first, SearchProcedure.keepKey),
        placed(second, SearchProcedure.keepKey)) ++
        drawn.filterNot(Set(first, second)).map(
          placed(_, SearchProcedure.discardKey))))

  /** The decision `state` is parked on, as the projector rebuilds it. */
  private def parkedDecide(state: OathState)(using munit.Location): Decide =
    ParkedNode.of(state, catalog, walkerPowers) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide
      case other => fail(s"expected a parked decision, got $other")

  /** The second card's placement options once the first was played
    * `way`, at a table that `table` states. */
  private def secondCardOptions(table: Table, way: PlayedAs)(
      using munit.Location): (OathTransition, Vector[DecisionOption]) =
    val kept = accepted(keepingBoth(searched(table), drawn(1), drawn(0)))
    val first = accepted(SearchFixture.place(kept, drawn(1), way))
    (first, PlacementPicks.parkedOptions(first.state, walkerPowers))

  test("picked, it places 1 favor on itself and the Search may keep one " +
      "or two cards"):
    val at = searched(atAncientCity())
    assertEquals(Look(SearchFixture.after(at)).tokensOn(landWarden),
      Tokens(1, 0))
    val shown = parkedDecide(at.state).query match
      case partition: DecisionQuery.Partition =>
        Some((partition.sections, partition.heading))
      case _ => None
    assertEquals(shown, Some((Vector(
      DecisionSection(SearchProcedure.keepKey, "Keep", 1, Some(2)),
      DecisionSection(SearchProcedure.discardKey, "Discard", 0)),
      Some(LandWarden.heading))))
    assertEquals(LandWarden.id, PowerId("denizen.land-warden"))

  test("the kept cards are played in the order kept; after a play to a " +
      "site, the second goes anywhere"):
    val kept = accepted(keepingBoth(searched(atAncientCity()), drawn(1),
      drawn(0)))
    assertEquals(parkedDecide(kept.state).decisionId,
      PlacementFixture.cardPlayDecisionId(drawn(1), "place"))
    val (first, options) = secondCardOptions(atAncientCity(),
      PlayedAs.AtSite)
    assertEquals(parkedDecide(first.state).decisionId,
      PlacementFixture.cardPlayDecisionId(drawn(0), "place"))
    assert(options.forall(_.available), options)
    val second = accepted(SearchFixture.place(first, drawn(0),
      PlayedAs.Faceup))
    val after = Look(SearchFixture.after(second))
    assertEquals(after.cardsAt("Ancient City"), Vector(drawn(1)))
    assert(after.advisers(p1).contains(drawn(0)), after.advisers(p1))
    assert(after.inRegionalDiscard(drawn(2)))

  test("when the first did not go to a site, the second may go only to " +
      "one"):
    val (_, options) = secondCardOptions(atAncientCity(), PlayedAs.Faceup)
    assertEquals(PlacementPicks.offered(options), Vector(PlayedAs.AtSite))
    val refused = options.filterNot(option =>
      CardPlayProcedure.playedAs(option.ref).contains(PlayedAs.AtSite))
    assert(refused.nonEmpty, options)
    refused.foreach {
      case DecisionOption.Unavailable(_, reason) =>
        assertEquals(reason, OptionReason.Written(LandWarden.toSite))
      case other => fail(s"expected an unavailable option, got $other")
    }

  test("when the first did not go to a site and no site has room, the " +
      "second is discarded"):
    val full = atAncientCity().denizen("Rain Boots", at = "Ancient City")
      .denizen("Ancient Binding", at = "Ancient City")
      .denizen("Wrestlers", at = "Ancient City")
    val (first, options) = secondCardOptions(full, PlayedAs.Faceup)
    assertEquals(PlacementPicks.offered(options), Vector(PlayedAs.Discarded))
    val second = accepted(SearchFixture.place(first, drawn(0),
      PlayedAs.Discarded))
    assert(Look(SearchFixture.after(second)).inRegionalDiscard(drawn(0)))

  test("without Land Warden, keeping two is refused"):
    assert(keepingBoth(searched(atAncientCity(warden = false),
      picked = Vector.empty), drawn(0), drawn(1)).isLeft)
