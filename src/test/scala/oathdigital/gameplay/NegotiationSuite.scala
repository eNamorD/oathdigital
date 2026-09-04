package oathdigital.gameplay

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.actions.{Negotiation, NegotiationCommand}
import oathdigital.model._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState.Ready
import oathdigital.catalog.CatalogPower

class NegotiationSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

  private def ready(): (ReadyGame, Vector[PlayerState], SiteId, RelicId, RelicId) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val siteId = base.game.current.map.inPlay.find(id =>
      base.game.current.map.sites(id).relics.nonEmpty).get
    val siteRelic = base.game.current.map.sites(siteId).relics.head.id
    val relics = base.game.current.map.sites.iterator
      .filterNot(_._1 == siteId)
      .flatMap(_._2.relics)
      .map(_.id)
      .take(3)
      .toVector
    val players = base.game.current.players.zipWithIndex.map { case (p, index) =>
      p.copy(pawnSite = Some(siteId), board = p.board.copy(favor = 5),
        relics = Vector(RelicState(relics(index), Orientation.FaceDown,
          if (index == 0) Tokens(0, 1) else Tokens.empty)))
    }
    val current = base.game.current.copy(players = players,
      map = base.game.current.map.copy(sites = base.game.current.map.sites.map {
        case (`siteId`, value) => siteId -> value
        case (id, value) => id -> value.copy(relics = Vector.empty)
      }), turn = base.game.current.turn.copy(activePlayer = players.head.player,
        phase = Phase.Act), pending = None)
    val ready = base.copy(game = base.game.copy(current = current), knowledge =
      base.knowledge.copy(siteRelics = Map(players.head.player ->
        Map(siteId -> Vector(siteRelic)))))
    (ready, players, siteId, relics.head, relics(1))
  }

  private def begin(base: ReadyGame, participants: Vector[PlayerId]) =
    Negotiation.handle(catalog, Ready(base), NegotiationCommand.Begin(
      base.game.current.turn.activePlayer, DecisionId("deal"), participants)).toOption.get

  test("bilateral directed unequal favor and stateful relic transfers are atomic") {
    val (base, players, _, actorRelic, _) = ready()
    val started = begin(base, Vector(players(1).player))
    val actorTerms = NegotiationTerms(Vector(NegotiationTransfer(players(1).player,
      3, Vector(actorRelic))))
    val revised = Negotiation.handle(catalog, started.state,
      NegotiationCommand.ReplaceTerms(players.head.player, DecisionId("deal"), actorTerms))
      .toOption.get
    val acceptedOther = Negotiation.handle(catalog, revised.state,
      NegotiationCommand.Accept(players(1).player, DecisionId("deal"))).toOption.get
    val completed = rules.handle(acceptedOther.state,
      NegotiationCommand.Accept(players.head.player, DecisionId("deal"))).toOption.get
    val Ready(after) = completed.state: @unchecked
    val actor = after.game.current.players.find(_.player == players.head.player).get
    val recipient = after.game.current.players.find(_.player == players(1).player).get
    assertEquals(actor.board.favor, 2)
    assertEquals(recipient.board.favor, 8)
    assertEquals(after.game.current.players.map(_.board.favor).sum,
      base.game.current.players.map(_.board.favor).sum)
    assertEquals(after.game.current.players.flatMap(_.relics).map(_.id).toSet,
      base.game.current.players.flatMap(_.relics).map(_.id).toSet)
    assertEquals(recipient.relics.find(_.id == actorRelic).get.tokens, Tokens(0, 1))
    assertEquals(after.game.current.pending, None)
  }

  test("three-player terms are participant-authored and any replacement resets consent") {
    val (base, players, _, _, otherRelic) = ready()
    val started = begin(base, Vector(players(2).player, players(1).player))
    val pending = started.state.asInstanceOf[Ready].value.game.current.pending.get
      .asInstanceOf[PendingProcedure.Negotiation]
    assertEquals(pending.participants, Vector(players.head.player,
      players(1).player, players(2).player))
    val terms = NegotiationTerms(Vector(NegotiationTransfer(players.head.player,
      1, Vector(otherRelic))))
    val changed = Negotiation.handle(catalog, started.state,
      NegotiationCommand.ReplaceTerms(players(1).player, DecisionId("deal"), terms))
      .toOption.get
    val accepted = Negotiation.handle(catalog, changed.state,
      NegotiationCommand.Accept(players.head.player, DecisionId("deal"))).toOption.get
    val revised = Negotiation.handle(catalog, accepted.state,
      NegotiationCommand.ReplaceTerms(players(1).player, DecisionId("deal"), terms.copy(
        transfers = Vector(NegotiationTransfer(players.head.player, 2, Vector(otherRelic))))))
      .toOption.get
    assertEquals(revised.state.asInstanceOf[Ready].value.game.current.pending.get
      .asInstanceOf[PendingProcedure.Negotiation].accepted, Set.empty[PlayerId])
    assert(Negotiation.handle(catalog, revised.state,
      NegotiationCommand.ReplaceTerms(players(2).player, DecisionId("deal"), terms)).isLeft)
  }

  test("disclosure-only deal grants selective durable hidden knowledge") {
    val (base, players, site, _, _) = ready()
    val adviser = players.head.advisers.head.id.asInstanceOf[WorldCardId]
    val siteRelic = base.knowledge.siteRelics(players.head.player)(site).head
    val started = begin(base, Vector(players(1).player, players(2).player))
    val terms = NegotiationTerms(disclosures = Vector(
      NegotiationDisclosure(players(1).player,
        NegotiationDisclosureRef.Adviser(players.head.player, adviser)),
      NegotiationDisclosure(players(1).player,
        NegotiationDisclosureRef.SiteRelic(site, siteRelic))))
    val changed = Negotiation.handle(catalog, started.state,
      NegotiationCommand.ReplaceTerms(players.head.player, DecisionId("deal"), terms))
      .toOption.get
    val projector = new oathdigital.application.GameProjector(catalog)
    val loaded = oathdigital.application.LoadedGame(changed.state, 2)
    assert(projector.project("deal", loaded, players.head.player).negotiation.get
      .disclosures.forall(_.card.nonEmpty))
    assert(projector.project("deal", loaded, players(1).player).negotiation.get
      .disclosures.forall(_.card.isEmpty))
    assertEquals(projector.project("deal", loaded, players(2).player).negotiation.get
      .disclosures.map(_.card), Vector(None, None))
    assertEquals(projector.projectPublic("deal", loaded).negotiation, None)
    assert(projector.projectPublic("deal", loaded).negotiationWaiting)
    val one = Negotiation.handle(catalog, changed.state,
      NegotiationCommand.Accept(players.head.player, DecisionId("deal"))).toOption.get
    val two = Negotiation.handle(catalog, one.state,
      NegotiationCommand.Accept(players(1).player, DecisionId("deal"))).toOption.get
    val done = rules.handle(two.state,
      NegotiationCommand.Accept(players(2).player, DecisionId("deal"))).toOption.get
    val Ready(after) = done.state: @unchecked
    assert(after.knowledge.advisers(players(1).player).contains(adviser))
    assert(after.knowledge.siteRelics(players(1).player)(site).contains(siteRelic))
    assert(!after.knowledge.advisers.getOrElse(players(2).player, Vector.empty)
      .contains(adviser))
  }

  test("projection gives every participant only participant Negotiation controls") {
    val (base, players, _, _, _) = ready()
    val started = begin(base, Vector(players(1).player))
    val changed = Negotiation.handle(catalog, started.state,
      NegotiationCommand.ReplaceTerms(players.head.player, DecisionId("deal"),
        NegotiationTerms(Vector(NegotiationTransfer(players(1).player, 1,
          Vector.empty))))).toOption.get
    val projector = new oathdigital.application.GameProjector(catalog)
    val loaded = oathdigital.application.LoadedGame(changed.state, 2)

    val active = projector.project("deal", loaded, players.head.player)
    val offTurn = projector.project("deal", loaded, players(1).player)
    val nonparticipant = projector.project("deal", loaded, players(2).player)
    Vector(active, offTurn).foreach { view =>
      assert(view.negotiation.nonEmpty)
      assert(view.legalControls.contains("replaceNegotiationTerms"))
      assert(view.legalControls.contains("acceptNegotiation"))
      assert(view.legalControls.contains("declineNegotiation"))
      assert(!view.negotiationWaiting)
    }
    assertEquals(nonparticipant.negotiation, None)
    assertEquals(nonparticipant.legalControls, Vector.empty)
    assert(nonparticipant.negotiationWaiting)
  }

  test("information-for-assets deal supports held-relic disclosure") {
    val (base, players, _, _, otherRelic) = ready()
    val started = begin(base, Vector(players(1).player))
    val actorTerms = NegotiationTerms(Vector(NegotiationTransfer(
      players(1).player, 2, Vector.empty)))
    val otherTerms = NegotiationTerms(disclosures = Vector(
      NegotiationDisclosure(players.head.player,
        NegotiationDisclosureRef.HeldRelic(players(1).player, otherRelic))))
    val actorChanged = Negotiation.handle(catalog, started.state,
      NegotiationCommand.ReplaceTerms(players.head.player, DecisionId("deal"), actorTerms))
      .toOption.get
    val changed = Negotiation.handle(catalog, actorChanged.state,
      NegotiationCommand.ReplaceTerms(players(1).player, DecisionId("deal"), otherTerms))
      .toOption.get
    val one = Negotiation.handle(catalog, changed.state,
      NegotiationCommand.Accept(players.head.player, DecisionId("deal"))).toOption.get
    val done = rules.handle(one.state,
      NegotiationCommand.Accept(players(1).player, DecisionId("deal"))).toOption.get
    val Ready(after) = done.state: @unchecked
    assert(after.knowledge.heldRelics(players.head.player).contains(otherRelic))
    assertEquals(after.game.current.players.find(_.player == players.head.player).get
      .board.favor, 3)
  }

  test("decline applies nothing and stale or tampered completion is rejected") {
    val (base, players, site, _, _) = ready()
    val emptySite = base.game.current.map.inPlay.find(_ != site).get
    val boundaryBase = base.copy(game = base.game.copy(current = base.game.current.copy(
      map = base.game.current.map.copy(sites = base.game.current.map.sites.updated(
        emptySite, base.game.current.map.sites(emptySite).copy(forces = SiteForces.Empty))))))
    val started = begin(boundaryBase, Vector(players(1).player))
    val decline = rules.handle(started.state,
      NegotiationCommand.Decline(players(1).player, DecisionId("deal")))
    assert(decline.isRight, decline.left.toOption.toString)
    val declined = decline.toOption.get
    assertEquals(declined.state.asInstanceOf[Ready].value.game.current.pending, None)
    assertEquals(declined.state.asInstanceOf[Ready].value.game.current.players,
      boundaryBase.game.current.players)
    assert(declined.events.exists(_.isInstanceOf[BanditsRefilled]),
      "decline must complete the action boundary and run bandit refill")
    assert(Negotiation.evolve(catalog, started.state, NegotiationCompleted(
      players.head.player, DecisionId("deal"), Vector(players.head.player,
        players(1).player), Map.empty)).isLeft)
    assert(Negotiation.handle(catalog, started.state,
      NegotiationCommand.Accept(players(2).player, DecisionId("deal"))).isLeft)
  }

  test("changed catalog handler inventory blocks Negotiation explicitly") {
    val (base, players, _, _, _) = ready()
    val first = catalog.relics.head
    val changed = catalog.copy(relics = first.copy(powers = first.powers :+
      CatalogPower("relic.future-negotiation", persistent = false, "Future power.")) +:
      catalog.relics.tail)
    assert(Negotiation.handle(changed, Ready(base), NegotiationCommand.Begin(
      players.head.player, DecisionId("changed"), Vector(players(1).player)))
      .left.toOption.exists(_.isInstanceOf[
        oathdigital.gameplay.OathViolation.UnsupportedNegotiationCatalogInventory]))
  }

  test("accessible Negotiation edifices and held relics block with stable sources") {
    val (base, players, site, _, _) = ready()
    val edifice = EdificeId("E21")
    val edificeSite = base.game.current.map.sites(site).copy(denizens =
      Vector(EdificeState(edifice, EdificeSide.Intact, Tokens.empty)))
    val withEdifice = base.copy(game = base.game.copy(current = base.game.current.copy(
      map = base.game.current.map.copy(sites = base.game.current.map.sites.updated(site,
        edificeSite)))))
    assertEquals(Negotiation.handle(catalog, Ready(withEdifice), NegotiationCommand.Begin(
      players.head.player, DecisionId("edifice"), Vector(players(1).player))).left.toOption,
      Some(oathdigital.gameplay.OathViolation.UnsupportedNegotiationRule(
        s"edifice:${site.value}:E21", "edifice.e21.intact")))

    val scepter = RelicId("grand-scepter")
    val withRelic = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == players(1).player)
        p.copy(relics = p.relics :+ RelicState(scepter, Orientation.FaceUp, Tokens.empty))
      else p))))
    assertEquals(Negotiation.handle(catalog, Ready(withRelic), NegotiationCommand.Begin(
      players.head.player, DecisionId("relic"), Vector(players(1).player))).left.toOption,
      Some(oathdigital.gameplay.OathViolation.UnsupportedNegotiationRule(
        s"relic:${players(1).player.value}:grand-scepter", "relic.the-grand-scepter.negotiation")))
  }

  test("active Negotiation legacy blocks while separate Negotiation actions do not") {
    val (base, players, _, _, _) = ready()
    val highPriest = LegacyId("L21")
    val lineage = base.game.campaign.lineages(players(1).lineage)
    val campaign = base.game.campaign.copy(lineages = base.game.campaign.lineages.updated(
      lineage.id, lineage.copy(legacies = Vector(LegacyState(highPriest, active = true)))))
    val modified = base.copy(game = base.game.copy(campaign = campaign))
    assertEquals(Negotiation.handle(catalog, Ready(modified), NegotiationCommand.Begin(
      players.head.player, DecisionId("legacy"), Vector(players(1).player))).left.toOption,
      Some(oathdigital.gameplay.OathViolation.UnsupportedNegotiationRule(
        s"legacy:${lineage.id.value}:L21", "legacy.high-priest")))

    val whisperingStone = RelicId("R34")
    val separateAction = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == players(1).player)
        p.copy(relics = p.relics :+ RelicState(
          whisperingStone, Orientation.FaceUp, Tokens.empty)) else p))))
    assert(Negotiation.handle(catalog, Ready(separateAction), NegotiationCommand.Begin(
      players.head.player, DecisionId("separate-action"),
      Vector(players(1).player))).isRight)
  }

  test("projection derives accept legality and preserves public faceup relic identity") {
    val (base, players, _, actorRelic, _) = ready()
    val faceup = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == players.head.player)
        p.copy(relics = p.relics.map(_.copy(orientation = Orientation.FaceUp))) else p))))
    val started = begin(faceup, Vector(players(1).player))
    val projector = new oathdigital.application.GameProjector(catalog)
    def projection(state: OathState, viewer: PlayerId) = projector.project(
      "deal", oathdigital.application.LoadedGame(state, 2), viewer)
    assert(!projection(started.state, players.head.player).legalControls
      .contains("acceptNegotiation"))
    val terms = NegotiationTerms(Vector(NegotiationTransfer(
      players(1).player, 1, Vector(actorRelic))))
    val changed = Negotiation.handle(catalog, started.state,
      NegotiationCommand.ReplaceTerms(players.head.player, DecisionId("deal"), terms))
      .toOption.get
    val recipient = projection(changed.state, players(1).player)
    assert(recipient.legalControls.contains("acceptNegotiation"))
    assertEquals(recipient.negotiation.get.transfers.head.relics.map(_.cardId),
      Vector(actorRelic.value))
    val accepted = Negotiation.handle(catalog, changed.state,
      NegotiationCommand.Accept(players(1).player, DecisionId("deal"))).toOption.get
    assert(!projection(accepted.state, players(1).player).legalControls
      .contains("acceptNegotiation"))

    val Ready(changedReady) = changed.state: @unchecked
    val pending = changedReady.game.current.pending.get
      .asInstanceOf[PendingProcedure.Negotiation]
    val staleTerms = pending.terms.updated(players.head.player,
      NegotiationTerms(Vector(NegotiationTransfer(
        players(1).player, 99, Vector.empty))))
    val invalidPending = pending.copy(terms = staleTerms)
    val invalid = changedReady.copy(game = changedReady.game.copy(current =
      changedReady.game.current.copy(pending = Some(invalidPending))))
    assert(!projection(Ready(invalid), players.head.player).legalControls
      .contains("acceptNegotiation"))
  }

  test("accessible When Negotiating handler blocks with stable source identity") {
    val (base, players, _, _, _) = ready()
    val powered = DenizenId(catalog.denizens.find(
      _.handlers.contains("denizen.council-arbiter")).get.id.value)
    val modified = base.copy(game = base.game.copy(current = base.game.current.copy(
      players = base.game.current.players.map(p => if (p.player == players(1).player)
        p.copy(advisers = Vector(DenizenState(powered, Orientation.FaceUp, Tokens.empty)))
      else p))))
    val failure = Negotiation.handle(catalog, Ready(modified), NegotiationCommand.Begin(
      players.head.player, DecisionId("powered"), Vector(players(1).player)))
      .left.toOption.get
    assertEquals(failure, oathdigital.gameplay.OathViolation.UnsupportedNegotiationRule(
      s"adviser:${players(1).player.value}:denizen:${powered.value}",
      "denizen.council-arbiter"))
  }

  test("valid setup history replays through negotiation completion") {
    val (setupState, setupEvents) = execute(setup)
    val Ready(original) = setupState: @unchecked
    val actor = original.game.current.turn.activePlayer
    val other = original.game.current.players.find(_.player != actor).get.player
    val destination = original.game.current.players.find(_.player == other).get.pawnSite.get
    val act = rules.handle(setupState,
      oathdigital.gameplay.phases.WakeCommand.EndWake(actor)).toOption.get
    val traveled = rules.handle(act.state,
      oathdigital.gameplay.actions.TravelCommand.Travel(actor, destination)).toOption.get
    val adviser = traveled.state.asInstanceOf[Ready].value.game.current.players
      .find(_.player == actor).get.advisers.head.id.asInstanceOf[WorldCardId]
    val start = Negotiation.handle(catalog, traveled.state, NegotiationCommand.Begin(
      actor, DecisionId("replay-deal"), Vector(other))).toOption.get
    val terms = NegotiationTerms(disclosures = Vector(NegotiationDisclosure(other,
      NegotiationDisclosureRef.Adviser(actor, adviser))))
    val change = Negotiation.handle(catalog, start.state,
      NegotiationCommand.ReplaceTerms(actor, DecisionId("replay-deal"), terms)).toOption.get
    val a = Negotiation.handle(catalog, change.state,
      NegotiationCommand.Accept(actor, DecisionId("replay-deal"))).toOption.get
    val done = rules.handle(a.state,
      NegotiationCommand.Accept(other, DecisionId("replay-deal"))).toOption.get
    val events = setupEvents ++ act.events ++ traveled.events ++ start.events ++
      change.events ++ a.events ++ done.events
    val replayed = new EventReplayEngine(rules).replay(events.zipWithIndex.map {
      case (event, index) => RecordedEvent(index.toLong, event)
    }).toOption.get
    assertEquals(replayed, done.state)
  }
}
