package oathdigital.gameplay

import oathdigital.gameplay.operations.{GrandScepter, HallOfMinisters,
  LockedCards, OperationConsent, OperationPipeline, OperationPolicy,
  OperationRestrictions, RestrictionIcons}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

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

  /** The Hall's site, where p1 stands. */
  private val hallSite = CatalogNames.site("Ancient City")

  /** The Hall on `side` at Ancient City, which p1 rules. */
  private def edificeAt(side: EdificeSide): ReadyGame =
    Table.start.pawn(p1, at = hallSite).edifice(hall, side, at = hallSite)
      .warbandsAt(hallSite, p1, 1).ready

  test("an intact edifice is locked, and a ruined one is not"):
    val edificeOps: Vector[CoreOperation] = Vector(
      Discard.RuinedEdifice(hall, PositionedLocation(Location.Site(hallSite)),
        catalog.suitOf(hall).get, 0, 0, p1),
      Take(Piece.Card(hall), p1, Location.Site(hallSite),
        Location.PlayArea(p1)))
    val intact = edificeAt(EdificeSide.Intact)
    assertEquals(edificeOps.map(refusal(intact, _)),
      Vector.fill(2)(Some("locked")))
    val ruined = edificeAt(EdificeSide.Ruined)
    assertEquals(edificeOps.map(refusal(ruined, _)),
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
    val ready = Table.start.pawn(p1, at = hallSite)
      .edifice(hall, EdificeSide.Intact, at = hallSite)
      .warbandsAt(hallSite, p1, 1).denizen(plainCard, at = hallSite)
      .relicAt(drum, at = hallSite).ready
    val byEnemy = discard(plainCard, Location.Site(hallSite), actor = p2)
    val relicByEnemy = Discard.Relic(drum,
      PositionedLocation(Location.Site(hallSite)), 0, p2)
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

  test("the Grand Scepter and the Hall of Ministers hold whichever cards a " +
      "catalog lists"):
    val bare = OperationRestrictions.forCatalog(
      catalog.copy(relics = Vector.empty, edifices = Vector.empty))
    assertEquals(bare.printed.filterNot(restriction =>
      restriction.isInstanceOf[LockedCards] ||
        restriction.isInstanceOf[RestrictionIcons]),
      Vector[OperationRestriction](HallOfMinisters, GrandScepter))
    assertEquals(GrandScepter.relic, scepter)

  test("the catalog prints a lock for Sealing Ward and Hall of Ministers but " +
      "not for Errand Boy"):
    val locked = set.printed.collect { case value: LockedCards => value.cards }
      .flatten.toSet
    assert(locked.contains(lockedCard))
    assert(locked.contains(hall))
    assert(!locked.contains(plainCard))

  test("the empty restriction set refuses nothing, even for a selected " +
      "modifier"):
    assertEquals(OperationRestrictions.none.active(Vector.empty,
      Vector(PowerId("denizen.wild-cry"))), Vector.empty)

  test("a refused Take skips its leaving operations with it, so the banner " +
      "stays whole"):
    val ready = Table.start.peoplesFavor(Some(p2), favor = 2).ready
    val take = Take(Piece.Banner(Banner.PeoplesFavor), p1,
      Location.PlayArea(p2), Location.PlayArea(p1), leaving = Vector(Move(
        Piece.Favor(2), PositionedLocation(Location.OnBanner(Banner.PeoplesFavor)),
        PositionedLocation(Location.FavorBank(Suit.Order)))))
    /** Stub of OperationRestriction: refuses every Take. */
    val noTake = new OperationRestriction:
      def reason(ready: ReadyGame, operation: CoreOperation)
          : Option[OperationReason] = operation match
        case _: Take => Some(OperationReason("test.no-take", "no take",
          OperationReasonKind.Impossible))
        case _ => None
    def run(restrictions: Vector[OperationRestriction]) =
      accepted(OperationPipeline.run(ready, Vector(take),
        OperationPolicy.Permissive, restrictions)(Right(_)))
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

  test("a selected power that discards its own card is left out of the " +
      "active-modifier rule, and the others stay protected"):
    val mountedLibrary = CatalogNames.denizen("Mounted Library")
    val cropRotation = CatalogNames.denizen("Crop Rotation")
    val ready = Table.start.adviser(p1, cropRotation)
      .adviser(p1, mountedLibrary).ready
    val selected = oathdigital.gameplay.walker.WalkerPowers.selected(
      oathdigital.gameplay.powers.WalkerPowerCatalog.default(catalog),
      Vector(oathdigital.gameplay.powers.economy.MountedLibrary.id,
        oathdigital.gameplay.powers.search.CropRotation.id))
    def refused(card: DenizenId): Option[String] = selected
      .operationRestrictions.flatMap(_.reason(ready,
        discard(card, Location.PlayArea(p1)))).headOption.map(_.code)
    assertEquals(refused(cropRotation), Some("active-modifier"))
    assertEquals(refused(mountedLibrary), None)

  /** p1 rules Ancient City, where the intact Hall and the plain card stand. */
  private def hallRuledByP1: ReadyGame = Table.start.pawn(p1, at = hallSite)
    .edifice(hall, EdificeSide.Intact, at = hallSite)
    .warbandsAt(hallSite, p1, 1).denizen(plainCard, at = hallSite).ready

  private val coversPlainCard = ConsentKey(HallOfMinisters.id, p2,
    HallOfMinisters.discardKey, DecisionOptionRef.Denizen(plainCard))

  private def waiverOf(ready: ReadyGame, operation: CoreOperation)
      : Option[OperationWaiver] =
    set.active(Vector.empty, Vector.empty).flatMap(_.reason(ready, operation))
      .headOption.flatMap(_.waiver)

  test("the Hall's refusal names its ruler as the one who may consent, and " +
      "the card a grant covers"):
    val byEnemy = discard(plainCard, Location.Site(hallSite), actor = p2)
    assertEquals(waiverOf(hallRuledByP1, byEnemy), Some(OperationWaiver(
      HallOfMinisters.id, Some(p1), coversPlainCard,
      HallOfMinisters.noDiscardConsent(HallOfMinisters.source))))

  test("under bandit rule the Hall's refusal names nobody to ask"):
    val banditHall = Table.start.edifice(hall, EdificeSide.Intact,
      at = hallSite).bandits(hallSite, 2).denizen(plainCard, at = hallSite)
      .ready
    val byEnemy = discard(plainCard, Location.Site(hallSite), actor = p2)
    assertEquals(waiverOf(banditHall, byEnemy).map(_.granter), Some(None))

  test("a grant remembered for the card lifts the Hall's refusal; a grant " +
      "for another card does not"):
    val ready = hallRuledByP1
    val granted = ready.copy(consent = ConsentState(
      answers = Map(coversPlainCard -> ConsentAnswer.Granted),
      turn = Some(ConsentTurn.of(ready))))
    val byEnemy = discard(plainCard, Location.Site(hallSite), actor = p2)
    assertEquals(refusal(granted, byEnemy), None)
    assertEquals(refusal(granted, byEnemy.copy(actingPlayer = p3)),
      Some("discard-immune"))

  test("only waivable refusals make an option askable, of other players, " +
      "with what they cover"):
    val byEnemy = discard(plainCard, Location.Site(hallSite), actor = p2)
    val restrictions = set.active(Vector.empty, Vector.empty)
    val refusals = OperationConsent.refusals(hallRuledByP1, Vector(byEnemy),
      restrictions)
    assertEquals(OperationConsent.ask(hallRuledByP1, p2, refusals),
      Some(OptionReason.Askable(HallOfMinisters.id,
        HallOfMinisters.noDiscardConsent(HallOfMinisters.source),
        Vector(ConsentAsk(p1, Vector(HallOfMinisters.id))),
        Vector(coversPlainCard))))
    val locked = OperationReason("locked", "locked",
      OperationReasonKind.Impossible)
    assertEquals(OperationConsent.ask(hallRuledByP1, p2, refusals :+ locked),
      None)
    val refused = hallRuledByP1.copy(consent = ConsentState(
      answers = Map(coversPlainCard -> ConsentAnswer.Refused),
      turn = Some(ConsentTurn.of(hallRuledByP1))))
    assertEquals(OperationConsent.ask(refused, p2, refusals), None)

  test("a grant for one card's discard lifts the Hall for every discard of " +
      "the same batch, which happen at the same time, and for no other " +
      "batch"):
    val wrestlers = CatalogNames.denizen("Wrestlers")
    val ready = Table.from(hallRuledByP1).denizen(wrestlers, at = hallSite)
      .ready
    val granted = ready.copy(consent = ConsentState(
      answers = Map(coversPlainCard -> ConsentAnswer.Granted),
      turn = Some(ConsentTurn.of(ready))))
    def byEnemy(card: DenizenId): CoreOperation =
      discard(card, Location.Site(hallSite), actor = p2).copy(required = true)
    val restrictions = set.active(Vector.empty, Vector.empty)
    def run(operations: Vector[CoreOperation]) = OperationPipeline.run(granted,
      operations, OperationPolicy.Permissive, restrictions)(Right(_))
    assert(run(Vector(byEnemy(plainCard), byEnemy(wrestlers))).isRight)
    assert(run(Vector(byEnemy(wrestlers))).isLeft)
    assertEquals(OperationConsent.refusals(granted,
      Vector(byEnemy(plainCard), byEnemy(wrestlers)), restrictions),
      Vector.empty)
