package oathdigital.gameplay

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.{CardPlay, VisionRules}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers,
  WalkerProcedureRegistry}
import oathdigital.model._
import oathdigital.model.OathState.Ready

class VisionPlaySuite extends munit.FunSuite {
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
  private val conspiracy = VisionRules.Conspiracy
  private val newVision = VisionRules.Faith
  private val existingVision = VisionRules.Conquest
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

  private def readyOf(state: OathState): ReadyGame = state match {
    case Ready(ready) => ready
    case other => fail(s"expected a ready state, got $other")
  }

  /** A `ReadyGame` with `newVision` first in the world deck and Search
    * started, so the walk parks on `newVision`'s own `cardplay.place.*`
    * decision with nothing else drawn. `revealed`, if given, is the actor's
    * revealed Vision before the play.
    */
  private def visionInTemporaryHand(revealed: Option[VisionId] = None)
      : OathState = {
    val base = acting
    val current = base.game.current
    val actor = current.turn.activePlayer
    val deck = current.commonCards.worldDeck
    val initial = base.updateCurrent(_.copy(
      players = current.players.map(p => if (p.player == actor) p.copy(
        revealedVision = revealed.map(id =>
          VisionState(id, Orientation.FaceUp))) else p),
      commonCards = current.commonCards.copy(worldDeck =
        Vector(newVision) ++ deck.filterNot(id =>
          id == newVision || revealed.contains(id)))))
    rules.startWalker(Ready(initial), ActionRef.Search, actor,
      startArgs = Vector(DecisionOptionRef.Button("search:world")))
      .toOption.get.state
  }

  /** The `Decide` node the walk is parked on, rebuilt the same way the
    * projector reads a parked position. */
  private def parkedPlacement(state: OathState): Decide = {
    val ready = readyOf(state)
    val current = ready.game.current
    val procedure = current.walkerProcedure.get
    val pending = current.walkerPending.get
    val tree = WalkerProcedureRegistry.rebuild(procedure, catalog, ready,
      current.turn.activePlayer, current.walkerStartArgs).toOption.get
    ProcedureWalker.parkedDecide(ready, tree, pending, WalkerPowers.empty).get
  }

  private def answerPlacementResult(state: OathState, ref: DecisionOptionRef)
      : Either[OathViolation, OathState] = {
    val actor = readyOf(state).game.current.turn.activePlayer
    rules.resolveWalker(state, actor, placeId(newVision),
      DecisionAnswer.ChooseOneAnswer(ref)).map(_.state)
  }

  private def answerPlacement(state: OathState, ref: DecisionOptionRef)
      : OathState = answerPlacementResult(state, ref).toOption.get

  private def walkerIsFinished(state: OathState): Boolean =
    readyOf(state).game.current.walkerPending.isEmpty

  private def revealedVisionOf(state: OathState): Option[VisionState] = {
    val ready = readyOf(state)
    player(ready, ready.game.current.turn.activePlayer).revealedVision
  }

  private def nextRegionDiscards(state: OathState): Vector[WorldCardId] = {
    val ready = readyOf(state)
    val current = ready.game.current
    val actor = player(ready, current.turn.activePlayer)
    actor.pawnSite.flatMap(current.map.regionOf).map(CardPlay.nextRegion)
      .map(current.commonCards.discard).getOrElse(Vector.empty)
  }

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

  test("a Vision's placement offers Discard and both adviser plays, " +
      "never a site") {
    val decision = parkedPlacement(visionInTemporaryHand())
    val offered = decision.query match {
      case DecisionQuery.ChooseOne(options, _) => options.map(_.ref).toSet
      case other => fail(s"expected a choose-one, got $other")
    }
    assertEquals(offered, Set[DecisionOptionRef](
      DecisionOptionRef.Button("discard"),
      DecisionOptionRef.Button("adviser-faceup"),
      DecisionOptionRef.Button("adviser-facedown")))
  }

  test("playing a Vision over a revealed one asks no discard question") {
    val state = visionInTemporaryHand(revealed = Some(existingVision))
    val after = answerPlacement(state, DecisionOptionRef.Button("adviser-faceup"))
    assertEquals(walkerIsFinished(after), true)
    assertEquals(revealedVisionOf(after).map(_.id), Some(newVision))
    assert(nextRegionDiscards(after).contains(existingVision))
  }

  test("a Vision answer naming a site is rejected -- it is not declared") {
    val state = visionInTemporaryHand()
    assert(answerPlacementResult(state, DecisionOptionRef.Button("site")).isLeft)
  }
}
