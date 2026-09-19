package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class VisionPlaySuite extends munit.FunSuite {
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
  private val conspiracy = VisionRules.Conspiracy
  private val faceup = DecisionAnswer.ChooseOneAnswer(
    DecisionOptionRef.Button("adviser-faceup"))

  private def placeId(card: VisionId) = s"cardplay.place.${card.kind}.${card.value}"

  private def acting: ReadyGame = {
    val state = initialReady
    state.updateCurrent(_.copy(
      turn = state.game.current.turn.copy(phase = Phase.Act)))
  }

  private def player(ready: ReadyGame, id: PlayerId): PlayerState =
    ready.game.current.players.find(_.player == id).get

  test("a Conspiracy kept faceup from Search parks on its target, takes it " +
      "and leaves the game") {
    val base = acting
    val current = base.game.current
    val actor = current.turn.activePlayer
    val site = player(base, actor).pawnSite
    val enemy = current.players.find(_.player != actor).get.player
    val deck = current.commonCards.worldDeck
    val initial = base.updateCurrent(_.copy(
      players = current.players.map(p =>
        if (p.player == enemy) p.copy(pawnSite = site) else p),
      banners = current.banners.copy(
        peoplesFavor = current.banners.peoplesFavor.copy(holder = Some(enemy)),
        darkestSecret = current.banners.darkestSecret.copy(holder = None)),
      commonCards = current.commonCards.copy(worldDeck =
        Vector(conspiracy) ++ deck.filterNot(_ == conspiracy))))
    val started = rules.startWalker(OathState.Ready(initial), ActionRef.Search,
      actor, startArgs = Vector(DecisionOptionRef.Button("search:world")))
      .toOption.get
    val parked = rules.resolveWalker(started.state, actor, placeId(conspiracy),
      faceup).toOption.get
    assert(parked.continue.isInstanceOf[OathContinue.AwaitingSearchDecision])
    val OathState.Ready(waiting) = parked.state: @unchecked
    assertEquals(waiting.game.current.temporaryHands(actor),
      Vector[WorldCardId](conspiracy))

    val projector = new GameProjector(catalog)
    val loaded = LoadedGame(parked.state, 11)
    val owner = projector.project("searched-conspiracy", loaded, actor)
    val hidden = projector.project("searched-conspiracy", loaded, enemy)
    assertEquals(owner.walkerDecision.map(_.decisionId),
      Some("cardplay.conspiracy.target"))
    assertEquals(owner.walkerDecision.flatMap(_.query)
      .map(_.options.map(option => (option.kind, option.id))),
      Some(Vector(("banner", "peoples-favor"))))
    assertEquals(hidden.walkerDecision, None)

    val resolved = rules.resolveWalker(parked.state, actor,
      "cardplay.conspiracy.target", DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Banner(Banner.PeoplesFavor))).toOption.get
    val OathState.Ready(after) = resolved.state: @unchecked
    assertEquals(after.game.current.banners.peoplesFavor.holder, Some(actor))
    assertEquals(after.game.current.temporaryHands(actor), Vector.empty)
    assertEquals(after.game.current.walkerPending, None)
    assert(!CardIndex.from(after.game).toOption.get.ids.contains(conspiracy))
  }
}
