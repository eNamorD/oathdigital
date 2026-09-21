package oathdigital.gameplay

import oathdigital.gameplay.actions.{CardPlay, PlacementRules}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._

/** `PlacementRules.siteDiscardFirst`: a play to a site may first discard one
  * card of the site's card list, at any capacity. No real power sets it yet
  * (the People's Favor banner face does), so a test double does.
  */
class SiteDiscardFirstSuite extends munit.FunSuite {
  import PlacementFixture._

  private val powers = WalkerPowers(Vector(discardFirst))
  private val noReplacement = CardPlayProcedure.noReplacement.ref
  private def choices(ready: ReadyGame, actor: PlayerId, card: DenizenId,
      rules: PlacementRules): Option[CardPlay.Choice] =
    CardPlay.legalChoices(catalog, ready, actor, card,
      CardPlay.Origin.TemporaryHand, rules)
      .find(_.placement.isInstanceOf[SearchPlacement.Site])

  /** Chooses "play at site" and returns the walk parked on the discard. */
  private def toDiscardDecision(ready: ReadyGame, actor: PlayerId,
      card: DenizenId): (Operation, PendingTree) = {
    val tree = build(ready, actor, card)
    val placed = answer(ready, tree, park(ready, tree, powers), powers,
      decisionId(card, "place"), DecisionOptionRef.Button("site"), actor)
      .asInstanceOf[WalkerOutcome.Parked]
    (tree, placed.tree)
  }

  test("a site with room offers a discard only under the permission") {
    val Vector(card, kept, other) = plain(initialReady).take(3)
    val (ready, actor, siteId) = staged(card, Vector(denizen(kept), denizen(other)))
    val capacity = catalog.sites.find(_.id == siteId).get.capacity
    assert(capacity > 2, s"the pawn site must have room, capacity $capacity")
    val plainChoice = choices(ready, actor, card, PlacementRules.default).get
    assertEquals(plainChoice.replacements, Vector.empty)
    assert(!plainChoice.replacementOptional)
    val allowed = choices(ready, actor, card,
      PlacementRules.default.withSiteDiscardFirst).get
    assertEquals(allowed.replacements.toSet, Set[CardId](kept, other))
    assert(allowed.replacementOptional)
  }

  test("declining the optional discard plays the card and discards nothing") {
    val Vector(card, kept) = plain(initialReady).take(2)
    val (ready, actor, siteId) = staged(card, Vector(denizen(kept)))
    val (tree, asked) = toDiscardDecision(ready, actor, card)
    assertEquals(options(ready, tree, asked, powers),
      Vector(noReplacement, DecisionOptionRef.Denizen(kept)))
    val done = answer(ready, tree, asked, powers, decisionId(card, "replace"),
      noReplacement, actor).asInstanceOf[WalkerOutcome.Finished].treeless
    assertEquals(done.game.current.map.sites(siteId).denizens.map(_.id),
      Vector[CardId](kept, card))
    assertEquals(done.game.current.temporaryHands(actor), Vector.empty)
  }

  test("choosing a site card discards it with the standard returns") {
    val card = plain(initialReady).head
    val kept = plain(initialReady).find(id =>
      catalog.suitOf(id) != catalog.suitOf(card)).get
    val suit = catalog.suitOf(kept).get
    val (ready, actor, siteId) = staged(card, Vector(denizen(kept, Tokens(1, 1))))
    val (tree, asked) = toDiscardDecision(ready, actor, card)
    val done = answer(ready, tree, asked, powers, decisionId(card, "replace"),
      DecisionOptionRef.Denizen(kept), actor)
      .asInstanceOf[WalkerOutcome.Finished].treeless
    val region = CardPlay.nextRegion(done.game.current.map.regionOf(siteId).get)
    assertEquals(done.game.current.map.sites(siteId).denizens.map(_.id),
      Vector[CardId](card))
    assertEquals(done.game.current.commonCards.discard(region).last, kept)
    // The favor on the discarded card returns to its suit bank, its secret to
    // the player facedown.
    assertEquals(done.banks.favor.getOrElse(suit, 0),
      ready.banks.favor.getOrElse(suit, 0) + 1)
    assertEquals(actorOf(done).board.faceDownSecrets,
      actorOf(ready).board.faceDownSecrets + 1)
  }

  test("a full site with no matching homeland accepts the play only with a " +
      "discard, and only under the permission") {
    val cards = plain(initialReady)
    val card = cards.head
    val (_, _, probeSite) = staged(card, Vector.empty)
    val capacity = catalog.sites.find(_.id == probeSite).get.capacity
    val fillers = cards.tail.filter(id =>
      catalog.suitOf(id) != catalog.suitOf(card)).take(capacity)
    assertEquals(fillers.size, capacity)
    val (ready, actor, siteId) = staged(card, fillers.map(denizen(_)))
    assertEquals(choices(ready, actor, card, PlacementRules.default), None)
    val site = choices(ready, actor, card,
      PlacementRules.default.withSiteDiscardFirst).get
    assertEquals(site.replacements.toSet, fillers.toSet[CardId])
    assert(!site.replacementOptional)
    val (tree, asked) = toDiscardDecision(ready, actor, card)
    assert(!options(ready, tree, asked, powers).contains(noReplacement))
    val done = answer(ready, tree, asked, powers, decisionId(card, "replace"),
      DecisionOptionRef.Denizen(fillers.head), actor)
      .asInstanceOf[WalkerOutcome.Finished].treeless
    assertEquals(done.game.current.map.sites(siteId).denizens.size, capacity)
    assert(done.game.current.map.sites(siteId).denizens.exists(_.id == card))
  }

  test("an intact edifice is never offered for the discard: it is locked") {
    val Vector(card, kept) = plain(initialReady).take(2)
    val hall = EdificeId("E16")
    val (built, actor, siteId) = staged(card, Vector(denizen(kept),
      EdificeState(hall, EdificeSide.Intact, Tokens.empty)))
    val ready = ruledByActor(built, siteId)
    val site = choices(ready, actor, card,
      PlacementRules.default.withSiteDiscardFirst).get
    assertEquals(site.replacements, Vector[CardId](kept))
  }

  test("a ruined edifice may be discarded, and it goes back to the edifice deck") {
    val Vector(card, kept) = plain(initialReady).take(2)
    val hall = EdificeId("E16")
    val ruined = DecisionOptionRef.Button("replace:edifice:E16")
    val (built, actor, siteId) = staged(card, Vector(denizen(kept),
      EdificeState(hall, EdificeSide.Ruined, Tokens.empty)))
    val ready = ruledByActor(built, siteId)
    val (tree, asked) = toDiscardDecision(ready, actor, card)
    assertEquals(options(ready, tree, asked, powers).toSet,
      Set[DecisionOptionRef](noReplacement, DecisionOptionRef.Denizen(kept),
        ruined))
    val done = answer(ready, tree, asked, powers, decisionId(card, "replace"),
      ruined, actor).asInstanceOf[WalkerOutcome.Finished].treeless
    assertEquals(done.game.current.map.sites(siteId).denizens.map(_.id),
      Vector[CardId](kept, card))
    assertEquals(done.game.current.commonCards.edificeDeck.last, hall)
  }

  test("the permission composes with an adviser limit through the walker") {
    val Vector(card, kept, first, second) = plain(initialReady).take(4)
    val (built, actor, _) = staged(card, Vector(denizen(kept)))
    val held = Vector(first, second)
    val current = built.game.current
    val ready = built.updateCurrent(_.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(held.contains)),
      players = current.players.map(p => if (p.player == actor)
        p.copy(advisers = held.map(id => DenizenState(id,
          Orientation.FaceDown, Tokens.empty))) else p)))
    val both = WalkerPowers(Vector(discardFirst, limitTwo))
    val tree = build(ready, actor, card)
    val parked = park(ready, tree, both)
    // A limit of two with two advisers held: a faceup adviser needs a discard.
    val faceup = answer(ready, tree, parked, both, decisionId(card, "place"),
      DecisionOptionRef.Button("adviser-faceup"), actor)
      .asInstanceOf[WalkerOutcome.Parked]
    assertEquals(options(ready, tree, faceup.tree, both).toSet,
      Set[DecisionOptionRef](DecisionOptionRef.Denizen(first),
        DecisionOptionRef.Denizen(second)))
    // And the site still offers the optional discard.
    val site = answer(ready, tree, parked, both, decisionId(card, "place"),
      DecisionOptionRef.Button("site"), actor).asInstanceOf[WalkerOutcome.Parked]
    assertEquals(options(ready, tree, site.tree, both).head, noReplacement)
  }

  test("without the permission a site with room asks no discard") {
    val Vector(card, kept) = plain(initialReady).take(2)
    val (ready, actor, siteId) = staged(card, Vector(denizen(kept)))
    val none = WalkerPowers.empty
    val tree = build(ready, actor, card)
    val done = answer(ready, tree, park(ready, tree, none), none,
      decisionId(card, "place"), DecisionOptionRef.Button("site"), actor)
      .asInstanceOf[WalkerOutcome.Finished].treeless
    assertEquals(done.game.current.map.sites(siteId).denizens.map(_.id),
      Vector[CardId](kept, card))
  }
}
