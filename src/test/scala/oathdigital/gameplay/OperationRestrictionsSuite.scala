package oathdigital.gameplay

import oathdigital.gameplay.operations.{GrandScepter, LockedCards,
  OperationPipeline, OperationPolicy, OperationRestrictions}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** The global operation restrictions (global operation restrictions design,
  * "Rules"): a locked card cannot be moved or flipped while it shows its lock,
  * a modifier selected for the running action cannot be discarded, and the
  * Hall of Ministers protects its ruler's sites from enemy discards.
  */
class OperationRestrictionsSuite extends munit.FunSuite:
  private val set = OperationRestrictions.forCatalog(catalog)
  /** Locked and adviser-only. */
  private val lockedCard = CatalogNames.denizen("Sealing Ward")
  /** The Order edifice Hall of Ministers, locked while intact. */
  private val hall = CatalogNames.edifice("Hall of Ministers")
  private val wildCry = CatalogNames.denizen("Wild Cry")
  /** A plain Beast card, unrestricted. */
  private val plainCard = CatalogNames.denizen("Errand Boy")
  private val drum = CatalogNames.relic("Dragonskin Drum")
  private val scepter = RelicId("grand-scepter")

  private def refusal(ready: ReadyGame, operation: CoreOperation,
      modifiers: PowerId*): Option[String] =
    set.active(Vector.empty, modifiers.toVector)
      .flatMap(_.reason(ready, operation)).headOption.map(_.code)

  private def discard(card: DenizenId, from: Location,
      actor: PlayerId = p1): Discard.Denizen =
    Discard.Denizen(card, PositionedLocation(from), Region.Provinces,
      catalog.suitOf(card).get, 0, 0, actor)

  /** p1 holds `card`, p2 holds the plain card. */
  private def holding(card: DenizenId, facedown: Boolean = false): ReadyGame =
    Table.start.adviser(p1, card, facedown = facedown)
      .adviser(p2, plainCard).ready

  /** Every way to move or flip p1's `card`: a discard, a Move, a Flip, a
    * Swap with p2's plain card, a Take by p2 and a Give to p2. */
  private def movesOf(card: DenizenId): Vector[CoreOperation] =
    val mine = PositionedLocation(Location.PlayArea(p1))
    val theirs = PositionedLocation(Location.PlayArea(p2))
    Vector(
      discard(card, Location.PlayArea(p1)),
      Move(Piece.Card(card), mine, theirs),
      Flip(card, Location.PlayArea(p1), Orientation.FaceDown),
      Swap(card, mine, plainCard, theirs),
      Take(Piece.Card(card), p2, Location.PlayArea(p1), Location.PlayArea(p2)),
      Give(Piece.Card(card), p1, Location.PlayArea(p1), Location.PlayArea(p2)))

  test("a faceup locked adviser refuses every move and flip of itself"):
    val ready = holding(lockedCard)
    assertEquals(movesOf(lockedCard).map(refusal(ready, _)),
      Vector.fill(6)(Some("locked")))

  test("the lock refuses as Impossible, so an optional operation is skipped"):
    val reason = set.active(Vector.empty, Vector.empty).flatMap(
      _.reason(holding(lockedCard), movesOf(lockedCard).head)).head
    assertEquals(reason.kind, OperationReasonKind.Impossible)

  test("a facedown locked adviser has no restrictions"):
    val ready = holding(lockedCard, facedown = true)
    assertEquals(movesOf(lockedCard).map(refusal(ready, _)),
      Vector.fill(6)(None))

  test("Bury ignores locked"):
    val bury = Bury(BuryableCard.Denizen(lockedCard),
      PositionedLocation(Location.PlayArea(p1)))
    assertEquals(refusal(holding(lockedCard), bury), None)

  test("a locked card drawn by a Search can still be discarded from the hand"):
    val drawn = Table.start.hand(p1, lockedCard).ready
    assertEquals(refusal(drawn, discard(lockedCard, Location.Hand(p1))), None)

  test("an ordinary adviser can be discarded"):
    val ready = Table.start.adviser(p1, plainCard).ready
    assertEquals(refusal(ready, discard(plainCard, Location.PlayArea(p1))), None)

  /** The Hall on `side` at p1's site, which p1 rules. */
  private def edificeAt(side: EdificeSide): (ReadyGame, SiteId) =
    val home = Table.homeOf(p1)
    (Table.start.edifice(hall, side, at = home).warbandsAt(home, p1, 1).ready,
      home)

  test("an intact edifice is locked, and a ruined one is not"):
    def edificeOps(site: SiteId): Vector[CoreOperation] = Vector(
      Discard.RuinedEdifice(hall, PositionedLocation(Location.Site(site)),
        catalog.suitOf(hall).get, 0, 0, p1),
      Take(Piece.Card(hall), p1, Location.Site(site), Location.PlayArea(p1)))
    val (intact, site) = edificeAt(EdificeSide.Intact)
    assertEquals(edificeOps(site).map(refusal(intact, _)),
      Vector.fill(2)(Some("locked")))
    val (ruined, ruinedSite) = edificeAt(EdificeSide.Ruined)
    assertEquals(edificeOps(ruinedSite).map(refusal(ruined, _)),
      Vector.fill(2)(None))

  test("a modifier selected for the running action cannot be discarded"):
    val ready = Table.start.adviser(p1, wildCry).ready
    val operation = discard(wildCry, Location.PlayArea(p1))
    assertEquals(refusal(ready, operation), None)
    assertEquals(refusal(ready, operation, PowerId("denizen.wild-cry")),
      Some("active-modifier"))
    // A different modifier selected leaves the card free.
    assertEquals(refusal(ready, operation, PowerId("denizen.tents")), None)

  test("a relic modifier selected for the running action cannot be discarded"):
    val relic = Discard.Relic(drum,
      PositionedLocation(Location.PlayArea(p1)), 0, p1)
    val ready = Table.start.relic(p1, drum).ready
    assertEquals(refusal(ready, relic), None)
    assertEquals(refusal(ready, relic, PowerId("relic.dragonskin-drum")),
      Some("active-modifier"))

  test("the Hall of Ministers refuses an enemy's discard at its ruler's sites, " +
      "relics included, and names the discard's own acting player"):
    val home = Table.homeOf(p1)
    val ready = Table.start.edifice(hall, EdificeSide.Intact, at = home)
      .warbandsAt(home, p1, 1).denizen(plainCard, at = home)
      .relicAt(drum, at = home).ready
    val byEnemy = discard(plainCard, Location.Site(home), actor = p2)
    val relicByEnemy = Discard.Relic(drum,
      PositionedLocation(Location.Site(home)), 0, p2)
    assertEquals(refusal(ready, byEnemy), Some("discard-immune"))
    assertEquals(refusal(ready, relicByEnemy), Some("discard-immune"))
    assertEquals(refusal(ready, byEnemy.copy(actingPlayer = p1)), None)

  test("the Grand Scepter refuses a discard, a return to the relic deck and " +
      "a Bury, and allows a Take and a Give"):
    val ready = Table.start.relic(p1, scepter).ready
    val mine = PositionedLocation(Location.PlayArea(p1))
    val removals = Vector[CoreOperation](
      Discard.Relic(scepter, mine, 0, p1),
      Move(Piece.Card(scepter), mine, PositionedLocation(
        Location.Deck(CardDeck.Relic), StackPosition.Bottom)),
      Bury(BuryableCard.Relic(scepter), mine))
    assertEquals(removals.map(refusal(ready, _)),
      Vector.fill(3)(Some("grand-scepter")))
    val passes = Vector[CoreOperation](
      Take(Piece.Card(scepter), p2, Location.PlayArea(p1),
        Location.PlayArea(p2)),
      Give(Piece.Card(scepter), p1, Location.PlayArea(p1),
        Location.PlayArea(p2)))
    assertEquals(passes.map(refusal(ready, _)), Vector.fill(2)(None))

  test("the catalog prints one Grand Scepter restriction, for its scepter"):
    assertEquals(set.printed.collect { case value: GrandScepter => value.relic },
      Vector(scepter))

  test("the catalog prints a lock for every lock-icon card and only those"):
    val locked = set.printed.collect { case value: LockedCards => value.cards }
      .flatten.toSet
    assert(locked.contains(lockedCard))
    assert(locked.contains(hall))
    assert(!locked.contains(plainCard))

  test("no catalog holds only the powers' restrictions"):
    assertEquals(OperationRestrictions.none.active(Vector.empty,
      Vector(PowerId("denizen.wild-cry"))), Vector.empty)

  test("a refused Take skips its leaving operations with it, so the banner " +
      "stays whole"):
    val ready = Table.start.peoplesFavor(Some(p2), favor = 2).ready
    val take = Take(Piece.Banner(Banner.PeoplesFavor), p1,
      Location.PlayArea(p2), Location.PlayArea(p1), leaving = Vector(Move(
        Piece.Favor(2), PositionedLocation(Location.OnBanner(Banner.PeoplesFavor)),
        PositionedLocation(Location.FavorBank(Suit.Order)))))
    val noTake = new OperationRestriction:
      def reason(ready: ReadyGame, operation: CoreOperation)
          : Option[OperationReason] = operation match
        case _: Take => Some(OperationReason("test.no-take", "no take",
          OperationReasonKind.Impossible))
        case _ => None
    def run(restrictions: Vector[OperationRestriction]) = OperationPipeline.run(
      ready, Vector(take), OperationPolicy.Permissive, restrictions)(Right(_))
      .toOption.get
    val order = (state: ReadyGame) => state.banks.favor.getOrElse(Suit.Order, 0)
    val refused = run(Vector(noTake))
    assertEquals(refused.executed, Vector.empty)
    assertEquals(refused.state.game.current.banners.peoplesFavor.holder, Some(p2))
    assertEquals(refused.state.game.current.banners.peoplesFavor.favor, 2)
    assertEquals(order(refused.state), order(ready))
    val taken = run(Vector.empty)
    assertEquals(taken.state.game.current.banners.peoplesFavor.holder, Some(p1))
    assertEquals(taken.state.game.current.banners.peoplesFavor.favor, 0)
    assertEquals(order(taken.state), order(ready) + 2)
