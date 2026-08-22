package oathdigital.gameplay

import oathdigital.gameplay.actions.{SearchCommand, VisionCommand, VisionRules, Visions}
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.FirstGameSetupFixture._
import oathdigital.setup.OathEvent._
import oathdigital.setup.OathState.Ready

class VisionsSuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)

  private def actWith(card: VisionId): (ReadyGame, PlayerState, Region) = {
    val Ready(base) = execute(setup)._1: @unchecked
    val active = base.game.current.turn.activePlayer
    val actor0 = base.game.current.players.find(_.player == active).get
    val actor = actor0.copy(advisers = Vector(
      VisionState(card, Orientation.FaceDown)))
    val current = base.game.current.copy(players = base.game.current.players.map(p =>
      if (p.player == active) actor else p), turn = base.game.current.turn.copy(
      phase = Phase.Act))
    val ready = base.copy(game = base.game.copy(current = current))
    (ready, actor, ready.game.current.map.regionOf(actor.pawnSite.get).get)
  }

  test("revealing a true Vision costs no Supply and discards the replaced Vision") {
    val (base0, actor, origin) = actWith(VisionRules.Faith)
    val old = VisionRules.Conquest
    val base = base0.copy(game = base0.game.copy(current = base0.game.current.copy(
      players = base0.game.current.players.map(p => if (p.player == actor.player)
        p.copy(revealedVision = Some(VisionState(old, Orientation.FaceUp))) else p))))
    val before = actor.board.supply
    val accepted = rules.handle(Ready(base),
      VisionCommand.Reveal(actor.player, VisionRules.Faith)).toOption.get
    val event = accepted.events.collectFirst { case e: VisionRevealed => e }.get
    val destination = origin match {
      case Region.Cradle => Region.Provinces
      case Region.Provinces => Region.Hinterland
      case Region.Hinterland => Region.Cradle
    }
    assertEquals(event.replaced, Some(old))
    assertEquals(event.destination, destination)
    val Ready(after) = accepted.state: @unchecked
    val updated = after.game.current.players.find(_.player == actor.player).get
    assertEquals(updated.board.supply, before)
    assertEquals(updated.revealedVision.map(_.id), Some(VisionRules.Faith))
    assert(after.game.current.commonCards.discard(destination).contains(old))
  }

  test("Conspiracy takes an opaque relic slot and is boxed") {
    val (base0, actor, _) = actWith(VisionRules.Conspiracy)
    val enemy0 = base0.game.current.players.find(_.player != actor.player).get
    val relic = RelicState(RelicId("conspiracy-relic"), Orientation.FaceDown,
      Tokens.empty)
    val enemy = enemy0.copy(pawnSite = actor.pawnSite, relics = Vector(relic))
    val base = base0.copy(game = base0.game.copy(current = base0.game.current.copy(
      players = base0.game.current.players.map(p =>
        if (p.player == enemy.player) enemy else p))))
    val legal = Visions.legalTargetRefs(base, actor.player)
    assertEquals(legal, Vector(ConspiracyTargetRef.RelicSlot(enemy.player, 0)))
    val accepted = rules.handle(Ready(base), VisionCommand.PlayConspiracy(
      actor.player, DecisionId("conspiracy-test"), legal.headOption)).toOption.get
    assert(accepted.events.exists(_.isInstanceOf[ConspiracyStarted]))
    assert(accepted.events.exists(_.isInstanceOf[ConspiracyCompleted]))
    val Ready(after) = accepted.state: @unchecked
    val updatedActor = after.game.current.players.find(_.player == actor.player).get
    val updatedEnemy = after.game.current.players.find(_.player == enemy.player).get
    assert(updatedActor.relics.exists(_.id == relic.id))
    assert(!updatedActor.advisers.exists(_.id == VisionRules.Conspiracy))
    assert(!updatedEnemy.relics.exists(_.id == relic.id))
    assertEquals(after.game.current.pending, None)
  }

  test("Conspiracy must choose an asset when eligible and resolves empty when none exist") {
    val (base0, actor, _) = actWith(VisionRules.Conspiracy)
    val enemy = base0.game.current.players.find(_.player != actor.player).get
      .copy(pawnSite = actor.pawnSite, relics = Vector(RelicState(
        RelicId("eligible"), Orientation.FaceDown, Tokens.empty)))
    val withTarget = base0.copy(game = base0.game.copy(current = base0.game.current.copy(
      players = base0.game.current.players.map(p =>
        if (p.player == enemy.player) enemy else p))))
    assert(rules.handle(Ready(withTarget), VisionCommand.PlayConspiracy(
      actor.player, DecisionId("missing"), None)).isLeft)

    val none = base0.copy(game = base0.game.copy(current = base0.game.current.copy(
      players = base0.game.current.players.map(p => if (p.player == actor.player) p
        else p.copy(pawnSite = None, relics = Vector.empty)), banners =
        base0.game.current.banners.copy(
          peoplesFavor = base0.game.current.banners.peoplesFavor.copy(holder = None),
          darkestSecret = base0.game.current.banners.darkestSecret.copy(holder = None)))))
    val accepted = rules.handle(Ready(none), VisionCommand.PlayConspiracy(
      actor.player, DecisionId("empty"), None)).toOption.get
    assert(accepted.events.last.isInstanceOf[ConspiracyCompleted])
  }

  test("stale and tampered Conspiracy choices reject") {
    val (base, actor, _) = actWith(VisionRules.Conspiracy)
    val pending = PendingProcedure.Conspiracy(DecisionId("expected"), actor.player,
      VisionRules.Conspiracy, Some(ConspiracyTarget.Banner(actor.player,
        Banner.DarkestSecret)), 1)
    val state = base.copy(game = base.game.copy(current = base.game.current.copy(
      pending = Some(pending))))
    assert(rules.handle(Ready(state), VisionCommand.ChooseSecretSite(actor.player,
      DecisionId("stale"), state.game.current.map.inPlay.head)).isLeft)
    assert(Visions.evolve(catalog, Ready(state), ConspiracyCompleted(actor.player,
      pending.decision, pending.source, pending.target, Vector.empty,
      Vector.empty)).isLeft)
  }

  test("a Conspiracy kept faceup from Search immediately enters its target procedure") {
    val (base0, actor, _) = actWith(VisionRules.Faith)
    val enemy0 = base0.game.current.players.find(_.player != actor.player).get
    val enemy = enemy0.copy(pawnSite = actor.pawnSite)
    val players = base0.game.current.players.map(p =>
        if (p.player == actor.player) p.copy(advisers = Vector.empty)
        else if (p.player == enemy.player) enemy else p)
    val banners = base0.game.current.banners.copy(peoplesFavor =
      base0.game.current.banners.peoplesFavor.copy(holder = Some(enemy.player)))
    val current = base0.game.current.copy(players = players,
      commonCards = base0.game.current.commonCards.copy(
        worldDeck = Vector(VisionRules.Conspiracy)), banners = banners)
    val base = base0.copy(game = base0.game.copy(current = current))
    val decision = DecisionId("searched-conspiracy")
    val started = rules.handle(Ready(base), SearchCommand.Start(actor.player,
      decision, SearchSource.WorldDeck, Vector(VisionRules.Conspiracy))).toOption.get
    val completed = rules.handle(started.state, SearchCommand.Complete(actor.player,
      decision, VisionRules.Conspiracy, Vector.empty,
      SearchPlacement.Adviser(Orientation.FaceUp, None))).toOption.get
    val Ready(awaiting) = completed.state: @unchecked
    assert(awaiting.game.current.pending.exists {
      case p: PendingProcedure.Conspiracy => p.awaitingTarget && p.decision == decision
      case _ => false
    })
    val resolved = rules.handle(completed.state, VisionCommand.PlayConspiracy(
      actor.player, decision, Some(ConspiracyTargetRef.Banner(enemy.player,
        Banner.PeoplesFavor))))
      .toOption.get
    val Ready(after) = resolved.state: @unchecked
    assertEquals(after.game.current.banners.peoplesFavor.holder, Some(actor.player))
    assertEquals(after.game.current.pending, None)
  }
}
