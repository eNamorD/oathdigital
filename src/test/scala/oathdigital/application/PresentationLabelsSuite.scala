package oathdigital.application

import oathdigital.application.gamelog.LogScripts
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class PresentationLabelsSuite extends munit.FunSuite:
  private val presentation = new GamePresentationProjector(catalog)

  private def woken: ReadyGame = LogScripts.woken.history.steps.last.after match
    case OathState.Ready(ready) => ready
    case other => fail(s"expected a ready game, got $other")

  test("a player's label is the one the Players strip shows"):
    val ready = woken
    val player = ready.game.current.players.head.player
    assertEquals(presentation.playerLabel(player),
      presentation.readyPlayers(ready).head.displayName)

  test("a site denizen is identified to everyone; a facedown adviser to its owner only"):
    val ready = woken
    val current = ready.game.current
    // A first game deals no denizen to a site, only homeland edifices, which
    // lie in the same denizen slots.
    val denizen: CardId = current.map.inPlay
      .flatMap(current.map.sites(_).denizens).collectFirst {
        case DenizenState(id, _, _) => id
        case EdificeState(id, _, _) => id }.get
    Vector(None, Some(current.players.head.player)).foreach { viewer =>
      assert(presentation.identifiesAt(ready, viewer, denizen)) }
    val owner = current.players.find(_.advisers.exists(
      presentation.adviserOrientation(_) == Orientation.FaceDown)).get
    val adviser = owner.advisers.find(
      presentation.adviserOrientation(_) == Orientation.FaceDown).get.id
    assert(presentation.identifiesAt(ready, Some(owner.player), adviser))
    val other = current.players.find(_.player != owner.player).get.player
    assert(!presentation.identifiesAt(ready, Some(other), adviser))
    assert(!presentation.identifiesAt(ready, None, adviser))

  test("a card in the world deck is identified to nobody"):
    val ready = woken
    val top = ready.game.current.commonCards.worldDeck.head
    ready.game.current.players.map(p => Some(p.player)).foreach { viewer =>
      assert(!presentation.identifiesAt(ready, viewer, top)) }

  test("a card's label is its printed name"):
    val ready = woken
    val denizen = catalog.denizens.head
    assertEquals(presentation.cardLabel(ready, DenizenId(denizen.id.value)),
      denizen.name)
