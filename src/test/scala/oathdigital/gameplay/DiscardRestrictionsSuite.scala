package oathdigital.gameplay

import oathdigital.catalog.CardRestrictions
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.operations.DiscardRestrictions
import oathdigital.gameplay.powers.CardStaging
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** The discard restrictions: a locked denizen, an intact edifice and a
  * modifier selected for the running action cannot be discarded, whichever
  * path discards them, and a card that can be discarded is unaffected.
  */
class DiscardRestrictionsSuite extends munit.FunSuite {
  import PlacementFixture._

  private val restrictions = new DiscardRestrictions(catalog,
    actorOf(initialReady).player)
  private val lockedCard = DenizenId(catalog.denizens.find(
    _.restrictions == CardRestrictions.LockedAdviserOnly).get.id.value)
  private val hall = EdificeId("E16")
  private val wildCry = DenizenId("189")
  private val actor = actorOf(initialReady).player

  private def discard(card: DenizenId, from: Location): Discard.Denizen =
    Discard.Denizen(card, PositionedLocation(from), Region.Provinces,
      catalog.suitOf(card).get, 0, 0, actor)

  private def refused(ready: ReadyGame, operation: CoreOperation): Boolean =
    restrictions.reason(ready, operation).nonEmpty

  private def holding(ready: ReadyGame, card: DenizenId,
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    CardStaging.without(ready, card).updateCurrent(c => c.copy(players =
      c.players.map(p => if (p.player == actor) p.copy(advisers = p.advisers :+
        DenizenState(card, orientation, Tokens.empty)) else p)))

  private def selecting(ready: ReadyGame, powers: PowerId*): ReadyGame =
    ready.updateCurrent(_.copy(walkerModifiers = powers.toVector))

  test("a locked adviser cannot be discarded from play, and reports why") {
    val ready = holding(initialReady, lockedCard)
    val reason = restrictions.reason(ready,
      discard(lockedCard, Location.PlayArea(actor))).get
    assertEquals(reason.code, "locked")
    assertEquals(reason.kind, OperationReasonKind.Impossible)
  }

  test("a locked adviser is locked only while it is faceup") {
    val facedown = holding(initialReady, lockedCard, Orientation.FaceDown)
    assert(!refused(facedown, discard(lockedCard, Location.PlayArea(actor))))
  }

  test("a locked card drawn by a Search can still be discarded from the hand") {
    assert(!refused(initialReady, discard(lockedCard, Location.Hand(actor))))
  }

  test("an ordinary adviser can be discarded") {
    val card = plain(initialReady).head
    assert(!refused(holding(initialReady, card),
      discard(card, Location.PlayArea(actor))))
  }

  private def edificeAt(side: EdificeSide): (ReadyGame, SiteId) = {
    val card = plain(initialReady).head
    val (ready, _, site) = staged(card,
      Vector(EdificeState(hall, side, Tokens.empty)))
    (ruledByActor(ready, site), site)
  }

  private def edificeDiscard(site: SiteId) = Discard.RuinedEdifice(hall,
    PositionedLocation(Location.Site(site)), catalog.suitOf(hall).get, 0, 0,
    actor)

  test("an intact edifice is locked, and a ruined one is not") {
    val (intact, site) = edificeAt(EdificeSide.Intact)
    assertEquals(restrictions.reason(intact, edificeDiscard(site)).map(_.code),
      Some("locked"))
    val (ruined, ruinedSite) = edificeAt(EdificeSide.Ruined)
    assert(!refused(ruined, edificeDiscard(ruinedSite)))
  }

  test("a modifier selected for the running action cannot be discarded") {
    val ready = holding(initialReady, wildCry)
    val operation = discard(wildCry, Location.PlayArea(actor))
    assert(!refused(ready, operation))
    val active = selecting(ready, PowerId("denizen.wild-cry"))
    assertEquals(restrictions.reason(active, operation).map(_.code),
      Some("active-modifier"))
    // A different modifier selected leaves the card free.
    assert(!refused(selecting(ready, PowerId("denizen.tents")), operation))
  }

  test("a relic modifier selected for the running action cannot be discarded") {
    val relic = Discard.Relic(RelicId("R20"),
      PositionedLocation(Location.PlayArea(actor)), 0, actor)
    assert(!refused(initialReady, relic))
    assert(refused(selecting(initialReady,
      PowerId("relic.dragonskin-drum")), relic))
  }

  // ---- The card-play path ----

  private def siteChoice(ready: ReadyGame, card: DenizenId): CardPlay.Choice =
    CardPlay.legalChoices(catalog, ready, actor, card,
      CardPlay.Origin.TemporaryHand)
      .find(_.placement.isInstanceOf[SearchPlacement.Site]).get

  /** A full site whose Homeland is the Hall, so the played card may replace one
    * of its cards, and the actor rules it.
    */
  private def fullHomeland(side: EdificeSide)
      : (ReadyGame, DenizenId, Vector[DenizenId]) = {
    val hallSuit = catalog.suitOf(hall).get
    val cards = plain(initialReady)
    val card = cards.find(catalog.suitOf(_).contains(hallSuit)).get
    val (probe, _, site) = staged(card, Vector.empty)
    val capacity = catalog.sites.find(_.id == site).get.capacity
    val fillers = cards.filter(_ != card).take(capacity - 1)
    val (ready, _, _) = staged(card, fillers.map(denizen(_)) :+
      EdificeState(hall, side, Tokens.empty))
    (ruledByActor(ready, site), card, fillers)
  }

  test("a replacement can never be an intact edifice") {
    val (ready, card, fillers) = fullHomeland(EdificeSide.Intact)
    assertEquals(siteChoice(ready, card).replacements.toSet,
      fillers.toSet[CardId])
  }

  test("a ruined edifice can be the replacement, and is discarded, not buried") {
    val (ready, card, fillers) = fullHomeland(EdificeSide.Ruined)
    val choice = siteChoice(ready, card)
    assertEquals(choice.replacements.toSet, fillers.toSet[CardId] + hall)
    val planned = CardPlay.plannedOperations(catalog, ready, actor, card,
      SearchPlacement.Site(Some(hall)), CardPlay.Origin.TemporaryHand)
      .toOption.get
    assert(planned.exists(_.isInstanceOf[Discard.RuinedEdifice]))
    assert(!planned.exists(_.isInstanceOf[Bury]))
  }

  test("a locked adviser is not offered as the replacement of a faceup adviser") {
    val card = plain(initialReady).head
    val extras = plain(initialReady).filter(_ != card).take(2)
    val (staged1, _, _) = staged(card, Vector.empty)
    val current = staged1.game.current
    val full = staged1.updateCurrent(_.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(id =>
          extras.contains(id) || id == lockedCard)),
      players = current.players.map(p => if (p.player == actor)
        p.copy(advisers = (extras :+ lockedCard).map(id => DenizenState(id,
          Orientation.FaceDown, Tokens.empty))) else p)))
    val faceup = CardPlay.legalChoices(catalog, full, actor, card,
      CardPlay.Origin.TemporaryHand).find(_.placement ==
      SearchPlacement.Adviser(Orientation.FaceUp, None)).get
    assertEquals(faceup.replacements.toSet, extras.toSet[CardId])
  }
}
