package oathdigital.gameplay

import oathdigital.gameplay.actions.{CardPlay, VisionRules}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.operations.{BeginConspiracy, Decide, Discard, Play,
  OperationPipeline, OperationPolicy}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers, WalkerStepRecorded}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class CardPlayProcedureSuite extends munit.FunSuite {
  private val setupRules = new oathdigital.gameplay.setup.FirstGameSetupRules(catalog)

  private def handState: (ReadyGame, PlayerId, DenizenId) = {
    val OathState.Ready(base) = execute(setupRules)._1: @unchecked
    val actor = base.game.current.turn.activePlayer
    val card = base.game.current.commonCards.worldDeck.collectFirst {
      case id: DenizenId => id
    }.get
    val current = base.game.current
    val changed = current.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(_ == card)),
      temporaryHands = current.temporaryHands.updated(actor, Vector(card)))
    (base.copy(game = base.game.copy(current = changed)), actor, card)
  }

  test("CardPlay exposes legal placement choices in decision order") {
    val (ready, actor, card) = handState
    val choices = CardPlay.legalChoices(catalog, ready, actor, card,
      CardPlay.Origin.TemporaryHand, 3, 3)
    val expected = Vector[SearchPlacement](SearchPlacement.Discard,
      SearchPlacement.Site(None),
      SearchPlacement.Adviser(Orientation.FaceUp, None),
      SearchPlacement.Adviser(Orientation.FaceDown, None))
    assertEquals(choices.map(_.placement), expected)
  }

  test("temporary-hand card builds a reusable placement decision") {
    val (ready, actor, card) = handState
    val tree = CardPlayProcedure.build(catalog, ready, actor, card,
      CardPlayProcedure.Origin.TemporaryHand).toOption.get
    val decision = tree.children.head.asInstanceOf[Decide]
    assert(decision.query.asInstanceOf[DecisionQuery.ChooseOne]
      .options.exists(_.ref == DecisionOptionRef.Button("discard")))
  }

  test("card absent from temporary hand cannot build card play") {
    val (ready, actor, card) = handState
    val absent = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(temporaryHands = Map.empty)))
    assert(CardPlayProcedure.build(catalog, absent, actor, card,
      CardPlayProcedure.Origin.TemporaryHand).isLeft)
  }

  test("full adviser area offers placement then a discardable replacement") {
    val (base, actor, card) = handState
    val current = base.game.current
    val extras = current.commonCards.worldDeck.collect {
      case id: DenizenId if id != card => id
    }.take(3)
    val player = current.players.find(_.player == actor).get
    val added = extras.take(3 - player.advisers.size)
    val full = base.copy(game = base.game.copy(current = current.copy(
      players = current.players.map(p => if (p.player == actor)
        p.copy(advisers = p.advisers ++ added.map(id =>
          DenizenState(id, Orientation.FaceDown, Tokens.empty))) else p),
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(added.contains)))))
    val tree = CardPlayProcedure.build(catalog, full, actor, card,
      CardPlayProcedure.Origin.TemporaryHand).toOption.get
    val decision = tree.children.head.asInstanceOf[Decide]
    assert(decision.query.asInstanceOf[DecisionQuery.ChooseOne].options.exists(
      _.ref == DecisionOptionRef.Button("adviser-faceup")))
    val parked = ProcedureWalker.advance(full, tree, None, WalkerPowers.empty)
      .toOption.get.asInstanceOf[WalkerOutcome.Parked].tree
    val replacementPark = ProcedureWalker.resolve(full, tree, parked,
      Answered(decision.decisionId, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Button("adviser-faceup")), actor), WalkerPowers.empty)
      .toOption.get.asInstanceOf[WalkerOutcome.Parked].tree
    val replacement = ProcedureWalker.parkedDecide(full, tree,
      replacementPark, WalkerPowers.empty).get
    val faceup = CardPlay.legalChoices(catalog, full, actor, card,
      CardPlay.Origin.TemporaryHand, 3, 3).find(_.placement ==
      SearchPlacement.Adviser(Orientation.FaceUp, None)).get
    assertEquals(replacement.query.asInstanceOf[DecisionQuery.ChooseOne]
      .options.map(_.ref), faceup.replacements.map {
        case id: DenizenId => DecisionOptionRef.Denizen(id)
        case id: VisionId => DecisionOptionRef.Vision(id)
        case id => DecisionOptionRef.Button(s"replace:${id.kind}:${id.value}")
      })
    val chosen = DecisionOptionRef.Denizen(added.head)
    assert(replacement.query.asInstanceOf[DecisionQuery.ChooseOne].options
      .exists(_.ref == chosen))
    val finished = ProcedureWalker.resolve(full, tree, replacementPark,
      Answered(replacement.decisionId,
        DecisionAnswer.ChooseOneAnswer(chosen), actor), WalkerPowers.empty)
      .toOption.get.asInstanceOf[WalkerOutcome.Finished]
    val advisers = finished.treeless.game.current.players.find(
      _.player == actor).get.advisers
    assertEquals(advisers.size, 3)
    assert(advisers.exists(_.id == card))
    assert(!advisers.exists(_.id == added.head))
  }

  test("selected site placement moves kept card and empties temporary hand") {
    val (ready, actor, card) = handState
    val planned = CardPlay.plannedOperations(catalog, ready, actor, card,
      SearchPlacement.Site(None), CardPlay.Origin.TemporaryHand).toOption.get
    assert(planned.exists { case value: Play => value.required; case _ => false })
    val tree = CardPlayProcedure.build(catalog, ready, actor, card,
      CardPlayProcedure.Origin.TemporaryHand).toOption.get
    val parked = ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty)
      .toOption.get.asInstanceOf[WalkerOutcome.Parked].tree
    val decision = tree.children.head.asInstanceOf[Decide]
    val result = ProcedureWalker.resolve(ready, tree, parked,
      Answered(decision.decisionId,
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("site")), actor),
      WalkerPowers.empty).toOption.get.asInstanceOf[WalkerOutcome.Finished]
    val site = ready.game.current.players.find(_.player == actor).get.pawnSite.get
    assert(result.treeless.game.current.map.sites(site).denizens.exists(_.id == card))
    assertEquals(result.treeless.game.current.temporaryHands(actor), Vector.empty)
  }

  test("selected temporary-hand discard is a required semantic discard") {
    val (ready, actor, card) = handState
    val planned = CardPlay.plannedOperations(catalog, ready, actor, card,
      SearchPlacement.Discard, CardPlay.Origin.TemporaryHand).toOption.get
    assert(planned.exists {
      case value: Discard.Denizen => value.card == card && value.required
      case _ => false
    })
  }

  test("locked full adviser area offers no adviser placement") {
    val (base, actor, card) = handState
    val current = base.game.current
    val locked = catalog.denizens.filter(
      _.restrictions == oathdigital.catalog.CardRestrictions.LockedAdviserOnly)
      .map(d => DenizenId(d.id.value)).filterNot(_ == card).take(3)
    val full = base.copy(game = base.game.copy(current = current.copy(
      players = current.players.map(p => if (p.player == actor)
        p.copy(advisers = locked.map(id =>
          DenizenState(id, Orientation.FaceDown, Tokens.empty))) else p),
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(locked.contains)))))
    val tree = CardPlayProcedure.build(catalog, full, actor, card,
      CardPlayProcedure.Origin.TemporaryHand).toOption.get
    val query = tree.children.head.asInstanceOf[Decide].query
      .asInstanceOf[DecisionQuery.ChooseOne]
    assert(!query.options.exists(_.ref == DecisionOptionRef.Button(
      "adviser-faceup")))
    assert(!CardPlay.legalChoices(catalog, full, actor, card,
      CardPlay.Origin.TemporaryHand, 3, 3).exists(_.placement ==
      SearchPlacement.Adviser(Orientation.FaceUp, None)))
  }

  test("faceup Conspiracy from Search hands off to existing pending procedure") {
    val (base, actor, _) = handState
    val current = base.game.current
    val vision = VisionRules.Conspiracy
    val ready = base.copy(game = base.game.copy(current = current.copy(
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(_ == vision)),
      temporaryHands = current.temporaryHands.updated(actor, Vector(vision)))))
    val tree = CardPlayProcedure.build(catalog, ready, actor, vision,
      CardPlayProcedure.Origin.TemporaryHand).toOption.get
    val query = tree.children.head.asInstanceOf[Decide].query
      .asInstanceOf[DecisionQuery.ChooseOne]
    assert(query.options.exists(_.ref == DecisionOptionRef.Button(
      "adviser-faceup")))
    val parked = ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty)
      .toOption.get.asInstanceOf[WalkerOutcome.Parked].tree
    val decision = tree.children.head.asInstanceOf[Decide]
    val finished = ProcedureWalker.resolve(ready, tree, parked,
      Answered(decision.decisionId, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Button("adviser-faceup")), actor),
      WalkerPowers.empty).toOption.get.asInstanceOf[WalkerOutcome.Finished]
    assert(finished.treeless.game.current.pending.exists(
      _.isInstanceOf[PendingProcedure.Conspiracy]))
    assertEquals(finished.treeless.game.current.temporaryHands(actor),
      Vector(vision))
    val ops = finished.events.collect { case step: WalkerStepRecorded =>
      step.ops }.flatten
    assert(ops.exists(_.isInstanceOf[BeginConspiracy]))
    val replayed = OperationPipeline.run(ready, ops,
      OperationPolicy.Permissive)(Right(_)).toOption.get.state
    assertEquals(replayed, finished.treeless)
  }

  test("full site without Homeland permission offers no site placement") {
    val (base, actor, card) = handState
    val current = base.game.current
    val siteId = current.players.find(_.player == actor).get.pawnSite.get
    val capacity = catalog.sites.find(_.id == siteId).get.capacity
    val fillers = current.commonCards.worldDeck.collect {
      case id: DenizenId if id != card => id
    }.take(capacity)
    val site = current.map.sites(siteId).copy(denizens = fillers.map(id =>
      DenizenState(id, Orientation.FaceUp, Tokens.empty)))
    val full = base.copy(game = base.game.copy(current = current.copy(
      map = current.map.copy(sites = current.map.sites.updated(siteId, site)),
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(fillers.contains)))))
    val query = CardPlayProcedure.build(catalog, full, actor, card,
      CardPlayProcedure.Origin.TemporaryHand).toOption.get.children.head
      .asInstanceOf[Decide].query.asInstanceOf[DecisionQuery.ChooseOne]
    assert(!query.options.exists(_.ref == DecisionOptionRef.Button("site")))
  }

  test("Search Vision can replace an existing revealed Vision") {
    val (base, actor, _) = handState
    val incoming = VisionRules.Faith
    val old = VisionRules.Conquest
    val current = base.game.current
    val changed = current.copy(
      players = current.players.map(p => if (p.player == actor)
        p.copy(revealedVision = Some(VisionState(old, Orientation.FaceUp))) else p),
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(id => id == incoming || id == old)),
      temporaryHands = current.temporaryHands.updated(actor, Vector(incoming)))
    val ready = base.copy(game = base.game.copy(current = changed))
    val query = CardPlayProcedure.build(catalog, ready, actor, incoming,
      CardPlayProcedure.Origin.TemporaryHand).toOption.get.children.head
      .asInstanceOf[Decide].query.asInstanceOf[DecisionQuery.ChooseOne]
    assert(query.options.exists(_.ref == DecisionOptionRef.Button(
      "adviser-faceup")))
  }

  test("facedown adviser starts the shared walker placement tree") {
    val OathState.Ready(setup) = execute(setupRules)._1: @unchecked
    val actor = setup.game.current.turn.activePlayer
    val adviser = setup.game.current.players.find(_.player == actor).get
      .advisers.collectFirst {
        case DenizenState(id, Orientation.FaceDown, _) => id
      }.get
    val ready = setup.copy(game = setup.game.copy(current =
      setup.game.current.copy(turn = setup.game.current.turn.copy(
        phase = Phase.Act))))
    val rules = new OathRules(catalog)
    val started = rules.startWalker(OathState.Ready(ready),
      ActionRef.PlayFacedownAdviser, actor,
      startArgs = Vector(DecisionOptionRef.Denizen(adviser)))
    assert(started.isRight)
    assert(started.toOption.get.continue
      .isInstanceOf[OathContinue.AwaitingSearchDecision])
    val parked = started.toOption.get
    val other = ready.game.current.players.find(_.player != actor).get.player
    val projector = new oathdigital.application.GameProjector(catalog)
    val loaded = oathdigital.application.LoadedGame(parked.state, 10)
    val owner = projector.project("facedown-walker", loaded, actor)
    val hidden = projector.project("facedown-walker", loaded, other)
    assert(owner.walkerDecision.nonEmpty)
    assertEquals(hidden.walkerDecision, None)
    assert(hidden.walkerWaiting.nonEmpty)
  }
}
