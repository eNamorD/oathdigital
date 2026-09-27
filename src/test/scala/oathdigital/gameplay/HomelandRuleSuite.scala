package oathdigital.gameplay

import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** The Homeland site power (CR p. 31): "When playing a card of its Homeland
  * suit to this site, you may discard a card from the site first (even one of
  * matching suit)." The site's handler names the suit, and the discard is
  * offered whether or not the site is full.
  */
class HomelandRuleSuite extends munit.FunSuite:
  import PlacementFixture._

  private val (homeSite, homeSuit) = homeland
  private val capacity = catalog.site(homeSite).get.capacity

  private def ofSuit(matching: Boolean): Vector[DenizenId] =
    plain(initialReady).filter(id =>
      catalog.suitOf(id).contains(homeSuit) == matching)

  private def siteChoice(ready: ReadyGame, actor: PlayerId, card: DenizenId) =
    CardPlay.legalChoices(catalog, ready, actor, card,
      CardPlay.Origin.TemporaryHand)
      .find(_.placement.isInstanceOf[SearchPlacement.Site])

  /** `card` played at the Homeland holding `cards`, which the actor rules. */
  private def at(card: DenizenId, cards: Vector[DenizenId]) =
    val (ready, actor, site) = staged(card, cards.map(denizen(_)), Some(homeSite))
    (ruledByActor(ready, site), actor)

  test("the Homeland suit is read from the site's handler"):
    assertEquals(CardPlay.homelandSuit(catalog, SiteId("site:deep-woods")),
      Some(Suit.Beast))
    assertEquals(CardPlay.homelandSuit(catalog, SiteId("site:ancient-city")), None)

  test("a matching Homeland with room offers an optional discard"):
    val card = ofSuit(true).head
    val kept = ofSuit(false).head
    assert(capacity > 1, s"the Homeland must have room, capacity $capacity")
    val (ready, actor) = at(card, Vector(kept))
    val choice = siteChoice(ready, actor, card).get
    assertEquals(choice.replacements, Vector[CardId](kept))
    assert(choice.replacementOptional)

  test("a card of another suit is offered no discard at a Homeland with room"):
    val Vector(card, kept) = ofSuit(false).take(2)
    val (ready, actor) = at(card, Vector(kept))
    val choice = siteChoice(ready, actor, card).get
    assertEquals(choice.replacements, Vector.empty)
    assert(!choice.replacementOptional)

  test("a full matching Homeland requires a discard, even of a matching card"):
    val card = ofSuit(true).head
    val fillers = (ofSuit(true).tail.take(1) ++ ofSuit(false)).take(capacity)
    assertEquals(fillers.size, capacity)
    val (ready, actor) = at(card, fillers)
    val choice = siteChoice(ready, actor, card).get
    assertEquals(choice.replacements.toSet, fillers.toSet[CardId])
    assert(!choice.replacementOptional)

  test("a full Homeland refuses a card of another suit"):
    val card = ofSuit(false).head
    val fillers = ofSuit(false).tail.take(capacity)
    assertEquals(fillers.size, capacity)
    val (ready, actor) = at(card, fillers)
    assertEquals(siteChoice(ready, actor, card), None)

  test("an edifice of the card's suit does not make a site a Homeland"):
    val hall = EdificeId("E16")
    val hallSuit = catalog.suitOf(hall).get
    val cards = plain(initialReady)
    val card = cards.find(catalog.suitOf(_).contains(hallSuit)).get
    val (_, _, pawnSite) = staged(card, Vector.empty)
    assertEquals(CardPlay.homelandSuit(catalog, pawnSite), None)
    val fillers = cards.filter(_ != card)
      .take(catalog.site(pawnSite).get.capacity - 1)
    val (built, actor, site) = staged(card, fillers.map(denizen(_)) :+
      EdificeState(hall, EdificeSide.Intact, Tokens.empty))
    assertEquals(siteChoice(ruledByActor(built, site), actor, card), None)
