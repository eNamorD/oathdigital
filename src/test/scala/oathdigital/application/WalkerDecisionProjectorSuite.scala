package oathdigital.application

import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.oathkeeper.{OathkeeperFixture, OathkeeperProcedure}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.walker.{WalkerPowers, WalkerProcedureRegistry}
import oathdigital.model.OathState.Ready
import oathdigital.model._
import oathdigital.protocol.projection.WalkerWaitingProjection

/** Batch-1 Task 3, ruling R18 (P4), second consulting call site.
  *
  * `WalkerProcedureRegistry.rollDecisionId` is a typed `Left` for an action
  * whose entry declares none (Forge), and `WalkerProcedureRegistrySuite` pins
  * that value. This suite proves the OTHER half -- that
  * [[WalkerDecisionProjector]] honours the rejection rather than projecting
  * a decision the client would then send back as a `ResolveWalker` id.
  * Without a test here, replacing the accessor with a sentinel string, or
  * recovering from the `Left` with a `getOrElse`, would leave every existing
  * suite green.
  *
  * Reaching that branch needs a `Roll` park under an action declaring no
  * roll decision id, and no production tree can produce one: Forge is the
  * only such action and its tree has no `Roll` node at all. So the tree --
  * and ONLY the tree -- is substituted, through
  * `WalkerDecisionProjector.TreeSource`, the projection-side twin of the
  * `walkerTree` seam `OathRulesWalkerPowerSuite` already uses for the same
  * reason. The roll decision id under test is still read from the
  * PRODUCTION registry entries, so the substitution cannot also supply the
  * answer.
  */
class WalkerDecisionProjectorSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)

  /** A one-node tree that parks on a `Roll` at path `Vector("0")`. */
  private val rollTree: Operation =
    Sequence(Roll(PoolKey("test.roll"), DiceSpec(DiceKind.Defense)))

  /** A ready game parked on `rollTree`'s single node, owned by `action`.
    *
    * `atSite` moves the acting player's pawn, which the disclosure tests
    * below need: `identifiesCard` names a site's facedown relics to a
    * viewer standing there, so proving both sides of that clause means
    * choosing where the pawn stands rather than taking whichever site the
    * setup fixture happened to pick.
    */
  private def parked(action: ActionRef, atSite: Option[SiteId] = None)
      : (ScopedProjectionContext, PlayerId) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val actor = base.game.current.turn.activePlayer
    val moved = atSite.fold(base.game.current.players)(site =>
      base.game.current.players.map(player =>
        if (player.player == actor) player.copy(pawnSite = Some(site))
        else player))
    val ready: ReadyGame = base.copy(game = base.game.copy(
      current = base.game.current.copy(
        players = moved,
        turn = base.game.current.turn.copy(phase = Phase.Act),
        walkerProcedure = Some(action),
        walkerPending = Some(PendingTree(Vector("0"), Vector.empty)))))
    (ScopedProjectionContext(ready, Some(actor)), actor)
  }

  /** The first site holding a facedown relic, so the disclosure tests can
    * stand the pawn where Recover's own option shape is observable. The
    * setup fixture's board carries no site denizens at all, which is why
    * Forge's faceup-denizen shape is proven end to end in
    * `GameApplicationServiceSuite` rather than here.
    */
  private lazy val facedownRelicSite: SiteId = {
    val Ready(base) = execute(setup)._1: @unchecked
    base.game.current.map.sites.collectFirst {
      case (siteId, site)
          if site.relics.exists(_.orientation == Orientation.FaceDown) =>
        siteId
    }.getOrElse(fail("the fixture board must hold a facedown site relic"))
  }

  /** Whatever the action, the tree is [[rollTree]] -- so the two calls
    * below differ in nothing but the `ActionRef` that parked.
    */
  private def projector = new WalkerDecisionProjector(catalog,
    new GamePresentationProjector(catalog), WalkerPowers.empty,
    (_, _, _, _, _) => Right(rollTree))

  test("a Roll park under an action declaring no roll decision id projects " +
      "nothing, rather than a projection naming a sentinel") {
    // Control: the same tree, the same park, the same projector -- under
    // Recover, whose entry DOES declare a roll decision id.
    val (recoverContext, _) = parked(ActionRef.Recover)
    val projected = projector.project(recoverContext).getOrElse(
      fail("Recover's Roll park must project a roll decision"))
    assertEquals(projected.action, ActionRef.Recover.key)
    assertEquals(projected.decisionId, RecoverProcedure.rollDecisionId)
    assertEquals(projected.kind, "roll")

    // Forge declares none, so there is nothing to project at all.
    val (forgeContext, _) = parked(ActionRef.Forge)
    assertEquals(projector.project(forgeContext), None)

    // ...and the reason is the accessor's typed rejection, not a rebuild
    // failure or an ownership mismatch: both were satisfied above.
    assertEquals(WalkerProcedureRegistry.rollDecisionId(ActionRef.Forge),
      Left(OathViolation.InvalidEventOrder(
        "walker procedure forge declares no roll decision id")))
  }

  /** The spec's presentation-failure rule (Task 4): an option whose identity
    * cannot be presented suppresses the ENTIRE decision projection rather
    * than emitting a half-described option a client would render as a blank
    * button and then submit.
    *
    * This needs a parked `Decide` naming a card that is nowhere in
    * authoritative state, and no production tree can build one -- both
    * Recover and Forge read their options live off `ready`, which is the
    * property that makes them safe. So the tree is substituted the same way
    * and for the same reason the roll test above substitutes one.
    */
  private def decideTree(options: Vector[DecisionOption],
      actor: PlayerId): Operation =
    Sequence(Decide("test.decide", actor, DecisionQuery.ChooseOne(options)))

  private def projectorFor(tree: Operation) =
    new WalkerDecisionProjector(catalog,
      new GamePresentationProjector(catalog), WalkerPowers.empty,
      (_, _, _, _, _) => Right(tree))

  test("a parked distribution projects its slots, bounds, suggestions and total") {
    val (context, actor) = parked(ActionRef.Recover)
    val tree = Sequence(Decide("test.distribute", actor, DecisionQuery.Distribute(
      Vector(DistributeSlot(DecisionOptionRef.FavorBank(Suit.Arcane), 0, 2, Some(2)),
        DistributeSlot(DecisionOptionRef.FavorBank(Suit.Nomad), 0, 6, Some(0))),
      total = 2, heading = Some("League Treaty"), confirmLabel = "Move favor")))
    val query = projectorFor(tree).project(context).flatMap(_.query)
      .getOrElse(fail("a parked distribution must project"))
    assertEquals(query.form, "distribute")
    assertEquals(query.options, Vector.empty)
    assertEquals(query.slots.map(s => (s.option.kind, s.option.id, s.minimum,
      s.maximum, s.suggested)), Vector(("favor-bank", "arcane", 0, 2, Some(2)),
      ("favor-bank", "nomad", 0, 6, Some(0))))
    assertEquals(query.total, Some(2))
    assertEquals(query.heading, Some("League Treaty"))
    assertEquals(query.confirmLabel, Some("Move favor"))
  }

  /** Whether the tree below projects at all, for the given options. */
  private def projects(context: ScopedProjectionContext, actor: PlayerId,
      options: Vector[DecisionOption]) =
    projectorFor(decideTree(options, actor)).project(context).flatMap(_.query)

  private def relicAtActorSite(context: ScopedProjectionContext,
      actor: PlayerId): RelicId = {
    val site = context.ready.game.current.players.find(_.player == actor)
      .flatMap(_.pawnSite).getOrElse(fail("the actor must have a pawn site"))
    context.ready.game.current.map.sites(site).relics.map(_.id).headOption
      .getOrElse(fail(s"the actor's site $site must hold a relic"))
  }

  test("a declared option whose id is absent from authoritative state " +
      "suppresses the whole decision projection") {
    val (context, actor) = parked(ActionRef.Recover,
      Some(facedownRelicSite))
    val ready = context.ready
    val present = relicAtActorSite(context, actor)
    val absent = RelicId("relic:not-on-this-board")
    assert(!CardIndex.from(ready.game).toOption.get.ids.contains(absent),
      "the fixture must not actually hold the absent relic")

    // Control: the same shape, the same park, with an option the board
    // really holds -- so the suppression below is the absent id and not the
    // substituted tree.
    val live = projects(context, actor, Vector(
      DecisionOption.Relic(DecisionOptionRef.Relic(present)))).getOrElse(
        fail("a present relic option must project"))
    assertEquals(live.options.map(_.id), Vector(present.value))

    // One unpresentable option among two takes the whole projection with
    // it: not a one-option query, and not a blank second option.
    assertEquals(projects(context, actor, Vector(
      DecisionOption.Relic(DecisionOptionRef.Relic(present)),
      DecisionOption.Relic(DecisionOptionRef.Relic(absent)))), None)
  }

  /** The other half of "cannot be presented": a card that IS in
    * authoritative state but that this viewer may not identify.
    *
    * A decision option cannot be redacted the way a board slot can -- its
    * reference is the card's identity and the client answers by sending it
    * back -- so `GamePresentationProjector.identifiesCard` deciding "no"
    * suppresses the decision rather than emitting a nameless option beside
    * a live id. Without this the projector's blanket `hidden = false` would
    * hand a decision's owner the name and rules text of any card a tree
    * happened to name, which is the disclosure rule the rest of the layer
    * spends `hiddenCard` avoiding.
    *
    * Recover and Forge are unaffected and their own suites prove it: the
    * relics Recover offers sit at the acting player's own site, and the
    * denizens Forge offers are faceup.
    */
  /** A temporary hand is the one player area whose cards carry no state,
    * so `orientationOf` reports no orientation for them. A disclosure rule
    * phrased as "not facedown" therefore reads that absence as public and
    * hands every drawn card to any viewer -- the same trap a deck card
    * falls into, in an area that otherwise looks like a board slot.
    * `identifiesCard` settles a hand on ownership alone, and these are its
    * two controls.
    *
    * The card is MOVED out of the world deck rather than copied into a
    * hand: `CardIndex.from` rejects a duplicate outright, which would leave
    * the projector with no index and suppress the decision for the wrong
    * reason, passing the non-owner case while proving nothing.
    */
  private def withHand(context: ScopedProjectionContext, holder: PlayerId)
      : (ScopedProjectionContext, WorldCardId) = {
    val current = context.ready.game.current
    val drawn = current.commonCards.worldDeck.headOption.getOrElse(
      fail("the fixture must leave a card on the world deck"))
    val moved = current.copy(
      commonCards = current.commonCards.copy(
        worldDeck = current.commonCards.worldDeck.drop(1)),
      temporaryHands = current.temporaryHands.updated(holder, Vector(drawn)))
    val ready = context.ready.copy(game = context.ready.game.copy(current = moved))
    assert(CardIndex.from(ready.game).isRight,
      "moving the card must leave the index consistent, not duplicated")
    (context.copy(ready = ready), drawn)
  }

  test("a temporary hand is identifiable only to the player holding it") {
    val (base, actor) = parked(ActionRef.Recover, Some(facedownRelicSite))
    val other = base.ready.game.current.players.map(_.player)
      .find(_ != actor).get

    // The decision's owner drew it: the option projects with its name.
    val (own, ownCard) = withHand(base, actor)
    val projected = projects(own, actor, Vector(
      DecisionOption.Denizen(DecisionOptionRef.Denizen(
        DenizenId(ownCard.value))))).getOrElse(
          fail("a card in the decision owner's own hand must project"))
    assertEquals(projected.options.map(_.id), Vector(ownCard.value))
    assert(projected.options.forall(_.card.exists(!_.hidden)))

    // Another player drew it: same card, same container, same absent
    // orientation -- and the whole decision is suppressed.
    val (others, othersCard) = withHand(base, other)
    assertEquals(projects(others, actor, Vector(
      DecisionOption.Denizen(DecisionOptionRef.Denizen(
        DenizenId(othersCard.value))))), None)
  }

  test("a card the decision's owner may not identify suppresses the " +
      "projection, even though the card is really in play") {
    val (context, actor) = parked(ActionRef.Recover,
      Some(facedownRelicSite))
    val current = context.ready.game.current
    val actorSite = current.players.find(_.player == actor)
      .flatMap(_.pawnSite).get

    // A facedown relic at a site the actor's pawn is not at, and which the
    // actor has never peeked at.
    val elsewhere = current.map.sites.collect {
      case (siteId, site) if siteId != actorSite => site.relics.collect {
        case relic if relic.orientation == Orientation.FaceDown => relic.id
      }
    }.flatten.headOption.getOrElse(
      fail("the fixture board must hold a facedown relic away from the actor"))
    assert(!context.ready.knowledge.siteRelics.getOrElse(actor, Map.empty)
      .values.flatten.toVector.contains(elsewhere))
    assertEquals(projects(context, actor, Vector(
      DecisionOption.Relic(DecisionOptionRef.Relic(elsewhere)))), None)

    // Another player's facedown adviser: present in state, owned by someone
    // else, and not covered by the actor's recorded knowledge.
    val othersAdviser = current.players.filter(_.player != actor)
      .flatMap(_.advisers).collectFirst {
        case adviser: DenizenState
            if adviser.orientation == Orientation.FaceDown => adviser.id
      }.getOrElse(fail("the fixture must deal another player a facedown adviser"))
    assertEquals(projects(context, actor, Vector(
      DecisionOption.Denizen(DecisionOptionRef.Denizen(othersAdviser)))), None)

    // A card still in a deck has no orientation to be facedown at all, so
    // it has to be rejected by its container rather than by its face.
    val inDeck = current.commonCards.relicDeck.headOption.getOrElse(
      fail("the fixture must leave a relic on the deck"))
    assertEquals(projects(context, actor, Vector(
      DecisionOption.Relic(DecisionOptionRef.Relic(inDeck)))), None)

    // Control, so the three suppressions above are the disclosure rule and
    // not the substituted tree: the actor's OWN facedown adviser projects,
    // by the ownership clause rather than the site clause the first test
    // exercises. The card details carry its real name, not a placeholder.
    val ownAdviser = current.players.find(_.player == actor).get.advisers
      .collectFirst { case adviser: DenizenState => adviser.id }
      .getOrElse(fail("the actor must hold an adviser"))
    val projected = projects(context, actor, Vector(
      DecisionOption.Denizen(DecisionOptionRef.Denizen(ownAdviser))))
      .getOrElse(fail("the actor's own adviser must project"))
    assertEquals(projected.options.map(_.id), Vector(ownAdviser.value))
    assert(projected.options.forall(_.card.exists(!_.hidden)))
    assert(projected.options.forall(_.label.nonEmpty))
  }

  /** Task 5b: panel copy is OPTIONAL, and the absent case has to project as
    * absent rather than as an invented default.
    *
    * Every production query declares its copy now, so the only way to park
    * on one that does not is the substituted tree above -- which is also
    * the shape any future action gets for free on the day it declares a
    * decision and says nothing about what to call it. The generic strings
    * a panel falls back to are the frontend's business
    * (`WalkerPanelSupport.decisionHeading`); nothing here supplies one.
    */
  test("a query declaring no panel copy projects both fields as absent") {
    val (context, actor) = parked(ActionRef.Recover, Some(facedownRelicSite))
    val present = relicAtActorSite(context, actor)
    val query = projects(context, actor, Vector(
      DecisionOption.Relic(DecisionOptionRef.Relic(present)))).getOrElse(
        fail("a present relic option must project"))
    assertEquals(query.heading, None)
    assertEquals(query.confirmLabel, None)
  }

  /** Task 5: a parked `Decide` owned by a player other than the active one.
    * `owner` is who [[WalkerDecisionProjector.project]] must show the
    * decision to and [[WalkerDecisionProjector.waiting]] must name; `active`
    * gets only the public waiting projection, same as any spectator.
    */
  private def parkedOffTurn: (ReadyGame, PlayerId, PlayerId,
      WalkerDecisionProjector) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val active = base.game.current.turn.activePlayer
    val owner = base.game.current.players.map(_.player).find(_ != active).get
    val tree: Operation = Sequence(Decide("test.off-turn", owner,
      DecisionQuery.ChooseOne(Vector(DecisionOption.Button(
        DecisionOptionRef.Button("ok"), "OK")), heading = Some("Answer"))))
    val ready = base.copy(game = base.game.copy(current =
      base.game.current.copy(
        turn = base.game.current.turn.copy(phase = Phase.Act),
        walkerProcedure = Some(ActionRef.Recover),
        walkerPending = Some(PendingTree(Vector("0"), Vector.empty)))))
    val projector = new WalkerDecisionProjector(catalog,
      new GamePresentationProjector(catalog), WalkerPowers.empty,
      (_, _, _, _, _) => Right(tree))
    (ready, active, owner, projector)
  }

  test("only the awaited player sees the decision; everyone else, the active " +
      "player and spectators included, sees who is being waited on") {
    val (ready, active, owner, projector) = parkedOffTurn
    def ctx(viewer: Option[PlayerId]) = ScopedProjectionContext(ready, viewer)
    assert(projector.project(ctx(Some(owner))).nonEmpty)
    assertEquals(projector.project(ctx(Some(active))), None)
    assertEquals(projector.waiting(ctx(Some(owner))), None)
    val waiting = Some(WalkerWaitingProjection(owner.value, Some("Answer")))
    assertEquals(projector.waiting(ctx(Some(active))), waiting)
    assertEquals(projector.waiting(ctx(None)), waiting)
  }

  /** Task 7: the production Oathkeeper tree parked on a tie. The tree comes
    * from the default `declaredTree`, so the registry entry, the awaited
    * player and the Player-option presentation are all the real ones.
    */
  private def parkedOathkeeperTie: (ReadyGame, PlayerId, PlayerId,
      Vector[PlayerId]) = {
    val base = OathkeeperFixture.base
    val active = base.game.current.turn.activePlayer
    val holder = OathkeeperFixture.players.find(_ != active).get
    val leaders = OathkeeperFixture.players.filterNot(_ == holder).take(2)
    val ruled = OathkeeperFixture.inPhase(OathkeeperFixture.ruled(base,
      leaders.map(Some(_)), holder = Some(holder)), Phase.Act)
    val ready = ruled.copy(game = ruled.game.copy(current =
      ruled.game.current.copy(
        walkerProcedure = Some(TriggeredProcedureRef.Oathkeeper),
        walkerPending = Some(PendingTree(Vector("0"), Vector.empty)))))
    (ready, active, holder, leaders)
  }

  test("a parked Oathkeeper tie shows the holder the tied leaders and names " +
      "the holder to everyone else") {
    val (ready, active, holder, leaders) = parkedOathkeeperTie
    def ctx(viewer: Option[PlayerId]) = ScopedProjectionContext(ready, viewer)
    val projector = new WalkerDecisionProjector(catalog,
      new GamePresentationProjector(catalog), WalkerPowers.empty)
    val query = projector.project(ctx(Some(holder))).flatMap(_.query)
    assertEquals(query.map(_.options.map(_.kind)),
      Some(Vector("player", "player")))
    assertEquals(query.map(_.options.map(_.id)), Some(leaders.map(_.value)))
    assertEquals(projector.project(ctx(Some(active))), None)
    val waiting = Some(WalkerWaitingProjection(holder.value,
      Some("Choose the Oathkeeper")))
    assertEquals(projector.waiting(ctx(Some(active))), waiting)
    assertEquals(projector.waiting(ctx(None)), waiting)
  }

  /** Ruling R-P1: a Player option naming nobody seated cannot be presented.
    * Only the tree is substituted, and within it only one candidate.
    */
  test("an Oathkeeper candidate who holds no seat suppresses the projection") {
    val (ready, _, holder, _) = parkedOathkeeperTie
    def ctx(viewer: Option[PlayerId]) = ScopedProjectionContext(ready, viewer)
    val unseated = PlayerId("unseated")
    assert(!ready.game.current.players.exists(_.player == unseated))
    val production = OathkeeperProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty)
      .getOrElse(fail("the tie must build a tree"))
    val substituted: Operation = production match {
      case Sequence(children, window) => Sequence(children.map {
        case decide: Decide => decide.query match {
          case query: DecisionQuery.ChooseOne => decide.copy(query = query.copy(
            options = query.options.updated(1,
              DecisionOption.Player(DecisionOptionRef.Player(unseated)))))
          case other => fail(s"expected a choose-one query, got $other")
        }
        case other => other
      }, window)
      case other => fail(s"expected a Sequence, got $other")
    }
    val broken = projectorFor(substituted)
    assertEquals(broken.project(ctx(Some(holder))), None)
    assertEquals(broken.waiting(ctx(None)), None)
  }
}
