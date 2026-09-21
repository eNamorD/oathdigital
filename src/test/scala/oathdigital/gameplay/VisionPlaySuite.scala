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

  test("Reveal is a facedown Vision played faceup: it replaces the revealed " +
      "Vision and costs no Supply") {
    val base = acting
    val current = base.game.current
    val actor = current.turn.activePlayer
    val old = VisionRules.Conquest
    val revealed = VisionRules.Faith
    val staged = base.updateCurrent(_.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(id =>
          id == old || id == revealed)),
      players = current.players.map(p => if (p.player == actor) p.copy(
        advisers = p.advisers :+ VisionState(revealed, Orientation.FaceDown),
        revealedVision = Some(VisionState(old, Orientation.FaceUp))) else p)))
    val before = player(staged, actor)
    val origin = staged.game.current.map.regionOf(before.pawnSite.get).get
    val destination = origin match {
      case Region.Cradle => Region.Provinces
      case Region.Provinces => Region.Hinterland
      case Region.Hinterland => Region.Cradle
    }
    val started = rules.startWalker(OathState.Ready(staged),
      ActionRef.PlayFacedownAdviser, actor,
      startArgs = Vector(DecisionOptionRef.Vision(revealed))).toOption.get
    val done = rules.resolveWalker(started.state, actor, placeId(revealed),
      faceup).toOption.get
    val OathState.Ready(after) = done.state: @unchecked
    val updated = player(after, actor)
    assertEquals(updated.board.supply, before.board.supply)
    assertEquals(updated.revealedVision.map(_.id), Some(revealed))
    assert(!updated.advisers.exists(_.id == revealed))
    assert(after.game.current.commonCards.discard(destination).contains(old))
    assertEquals(after.game.current.walkerPending, None)
  }

  test("a Conspiracy played from a facedown adviser through the service " +
      "with nothing to take is boxed") {
    val base = acting
    val current = base.game.current
    val actor = current.turn.activePlayer
    val staged = base.updateCurrent(_.copy(
      players = current.players.map(p =>
        if (p.player == actor) p.copy(advisers = p.advisers :+
          VisionState(conspiracy, Orientation.FaceDown))
        else p.copy(pawnSite = None)),
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(_ == conspiracy))))
    val started = rules.startWalker(OathState.Ready(staged),
      ActionRef.PlayFacedownAdviser, actor,
      startArgs = Vector(DecisionOptionRef.Vision(conspiracy))).toOption.get
    val done = rules.resolveWalker(started.state, actor, placeId(conspiracy),
      faceup).toOption.get
    val OathState.Ready(after) = done.state: @unchecked
    assert(!player(after, actor).advisers.exists(_.id == conspiracy))
    assertEquals(after.game.current.walkerPending, None)
    assert(!CardIndex.from(after.game).toOption.get.ids.contains(conspiracy))
  }
}
