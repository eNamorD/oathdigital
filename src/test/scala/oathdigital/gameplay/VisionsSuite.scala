package oathdigital.gameplay

import oathdigital.gameplay.actions.{VisionCommand, VisionRules, Visions}
import oathdigital.model._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathEvent._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation.UnsupportedVisionRule

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
      phase = Phase.Act), commonCards = base.game.current.commonCards.copy(
      worldDeck = base.game.current.commonCards.worldDeck.filterNot(_ == card)))
    val ready = base.copy(game = base.game.copy(current = current))
    (ready, actor, ready.game.current.map.regionOf(actor.pawnSite.get).get)
  }

  test("revealing a true Vision costs no Supply and discards the replaced Vision") {
    val (base0, actor, origin) = actWith(VisionRules.Faith)
    val old = VisionRules.Conquest
    val base = base0.updateCurrent(_.copy(
      commonCards = base0.game.current.commonCards.copy(worldDeck =
        base0.game.current.commonCards.worldDeck.filterNot(_ == old)),
      players = base0.game.current.players.map(p => if (p.player == actor.player)
        p.copy(revealedVision = Some(VisionState(old, Orientation.FaceUp))) else p)))
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
    val base = base0.updateCurrent(_.copy(
      players = base0.game.current.players.map(p =>
        if (p.player == enemy.player) enemy else p)))
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
    // A direct (facedown-adviser) play boxes from advisers only; the actor's
    // temporary hand key is left untouched and empty under the always-key
    // invariant.
    assertEquals(after.game.current.temporaryHands.get(actor.player),
      Some(Vector.empty[WorldCardId]))
  }

  test("Conspiracy must choose an asset when eligible and resolves empty when none exist") {
    val (base0, actor, _) = actWith(VisionRules.Conspiracy)
    val enemy = base0.game.current.players.find(_.player != actor.player).get
      .copy(pawnSite = actor.pawnSite, relics = Vector(RelicState(
        RelicId("eligible"), Orientation.FaceDown, Tokens.empty)))
    val withTarget = base0.updateCurrent(_.copy(
      players = base0.game.current.players.map(p =>
        if (p.player == enemy.player) enemy else p)))
    assert(rules.handle(Ready(withTarget), VisionCommand.PlayConspiracy(
      actor.player, DecisionId("missing"), None)).isLeft)

    val none = base0.updateCurrent(_.copy(
      players = base0.game.current.players.map(p => if (p.player == actor.player) p
        else p.copy(pawnSite = None, relics = Vector.empty)), banners =
        base0.game.current.banners.copy(
          peoplesFavor = base0.game.current.banners.peoplesFavor.copy(holder = None),
          darkestSecret = base0.game.current.banners.darkestSecret.copy(holder = None))))
    val accepted = rules.handle(Ready(none), VisionCommand.PlayConspiracy(
      actor.player, DecisionId("empty"), None)).toOption.get
    assert(accepted.events.last.isInstanceOf[ConspiracyCompleted])
  }

  test("stale and tampered Conspiracy completions reject") {
    val (base0, actor, _) = actWith(VisionRules.Conspiracy)
    val enemy0 = base0.game.current.players.find(_.player != actor.player).get
    val enemy = enemy0.copy(pawnSite = actor.pawnSite)
    val banners = base0.game.current.banners.copy(peoplesFavor =
      base0.game.current.banners.peoplesFavor.copy(
        holder = Some(enemy.player), favor = 2))
    val base = base0.updateCurrent(_.copy(
      players = base0.game.current.players.map(p =>
        if (p.player == enemy.player) enemy else p),
      banners = banners))
    val target = Some(ConspiracyTarget.Banner(enemy.player, Banner.PeoplesFavor))
    val pending = PendingProcedure.Conspiracy(DecisionId("expected"), actor.player,
      VisionRules.Conspiracy, target)
    val state = base.updateCurrent(_.copy(
      pending = Some(pending)))
    // A completion for a different decision, target, or favor order is stale.
    assert(Visions.evolve(catalog, Ready(state), ConspiracyCompleted(actor.player,
      DecisionId("stale"), pending.source, pending.target,
      Vector.empty)).isLeft)
    assert(Visions.evolve(catalog, Ready(state), ConspiracyCompleted(actor.player,
      pending.decision, pending.source, None, Vector.empty)).isLeft)
    assert(Visions.evolve(catalog, Ready(state), ConspiracyCompleted(actor.player,
      pending.decision, pending.source, pending.target,
      Vector(Suit.Order))).isLeft)
    // A started Conspiracy whose recorded automatic favor return diverges from
    // the deterministic least-bank return is rejected.
    val bogus = ConspiracyStarted(actor.player, DecisionId("bogus"),
      VisionRules.Conspiracy, target, Vector(Suit.Beast))
    assert(Visions.evolve(catalog, Ready(base), bogus).isLeft)
  }

  test("Conspiracy taking the Darkest Secret burns every secret and takes the banner") {
    val (base0, actor, _) = actWith(VisionRules.Conspiracy)
    val enemy0 = base0.game.current.players.find(_.player != actor.player).get
    val enemy = enemy0.copy(pawnSite = actor.pawnSite)
    val banners = base0.game.current.banners.copy(darkestSecret =
      base0.game.current.banners.darkestSecret.copy(
        holder = Some(enemy.player), secrets = 3))
    val base = base0.updateCurrent(_.copy(
      players = base0.game.current.players.map(p =>
        if (p.player == enemy.player) enemy else p),
      banners = banners))
    val accepted = rules.handle(Ready(base), VisionCommand.PlayConspiracy(
      actor.player, DecisionId("conspiracy-ds"),
      Some(ConspiracyTargetRef.Banner(enemy.player, Banner.DarkestSecret))))
      .toOption.get
    assert(accepted.events.exists(_.isInstanceOf[ConspiracyCompleted]))
    val Ready(after) = accepted.state: @unchecked
    assertEquals(after.game.current.banners.darkestSecret.secrets, 0)
    assertEquals(after.game.current.banners.darkestSecret.holder, Some(actor.player))
    // No secret was placed on any site: the burn returns all three secrets to
    // the untracked SharedBank sink, leaving site tokens unchanged.
    def siteSecrets(game: ReadyGame): Int =
      game.game.current.map.sites.valuesIterator.map { site =>
        site.tokens.secrets + site.denizens.collect {
          case d: DenizenState => d.tokens.secrets
        }.sum
      }.sum
    assertEquals(siteSecrets(after), siteSecrets(base))
    assert(!after.game.current.players.find(_.player == actor.player).get
      .advisers.exists(_.id == VisionRules.Conspiracy))
  }

  private def withActorAdviser(base: ReadyGame, actor: PlayerState,
      handler: String): ReadyGame = {
    val definition = catalog.denizens.find(_.handlers.contains(handler)).get
    val powered = DenizenState(DenizenId(definition.id.value), Orientation.FaceUp,
      Tokens.empty)
    base.updateCurrent(_.copy(
      commonCards = base.game.current.commonCards.copy(worldDeck =
        base.game.current.commonCards.worldDeck.filterNot(
          _ == DenizenId(definition.id.value))),
      players = base.game.current.players.map(p => if (p.player == actor.player)
        p.copy(advisers = p.advisers :+ powered) else p)))
  }

  private def withEdifice(base: ReadyGame, siteId: SiteId, side: EdificeSide,
      handler: String): ReadyGame = {
    val definition = catalog.edifices.find(e => (side match {
      case EdificeSide.Intact => e.intact.handlers
      case EdificeSide.Ruined => e.ruined.handlers
    }).contains(handler)).get
    val state = EdificeState(EdificeId(definition.id.value), side, Tokens.empty)
    val site = base.game.current.map.sites(siteId)
    base.updateCurrent(_.copy(map =
      base.game.current.map.copy(sites = base.game.current.map.sites.updated(
        siteId, site.copy(denizens = site.denizens :+ state)))))
  }

  test("actor and enemy Vision restrictions reject with stable source identities") {
    val (base0, actor, _) = actWith(VisionRules.Faith)
    val vow = withActorAdviser(base0, actor, "denizen.vow-of-obedience")
    assertEquals(rules.handle(Ready(vow), VisionCommand.Reveal(
      actor.player, VisionRules.Faith)).left.toOption,
      Some(UnsupportedVisionRule(
        s"adviser:${actor.player.value}:denizen:121", "denizen.vow-of-obedience")))
    val projected = new oathdigital.application.GameProjector(catalog).project(
      "blocked-vision", oathdigital.application.LoadedGame(Ready(vow), 0),
      actor.player)
    assert(!projected.legalControls.contains("revealVision"))

    val enemy = base0.game.current.players.find(_.player != actor.player).get
    val siteId = actor.pawnSite.get
    val policeId = DenizenId(catalog.denizens.find(
      _.handlers.contains("denizen.secret-police")).get.id.value)
    val site = base0.game.current.map.sites(siteId).copy(
      forces = SiteForces.Occupied(ForceKind.Exile(enemy.lineage), 1),
      denizens = Vector(DenizenState(policeId, Orientation.FaceUp, Tokens.empty)))
    val police = base0.updateCurrent(_.copy(
      map = base0.game.current.map.copy(sites = base0.game.current.map.sites.updated(
        siteId, site))))
    assertEquals(rules.handle(Ready(police), VisionCommand.Reveal(
      actor.player, VisionRules.Faith)).left.toOption,
      Some(UnsupportedVisionRule(s"site-card:${siteId.value}:denizen:${policeId.value}",
        "denizen.secret-police")))

    val triggerId = DenizenId(catalog.denizens.find(
      _.handlers.contains("denizen.book-binders")).get.id.value)
    val trigger = base0.updateCurrent(_.copy(
      players = base0.game.current.players.map(p => if (p.player == enemy.player)
        p.copy(advisers = Vector(DenizenState(triggerId, Orientation.FaceUp,
          Tokens.empty))) else p)))
    assertEquals(rules.handle(Ready(trigger), VisionCommand.Reveal(
      actor.player, VisionRules.Faith)).left.toOption,
      Some(UnsupportedVisionRule(
        s"adviser:${enemy.player.value}:denizen:${triggerId.value}",
        "denizen.book-binders")))
  }

  test("both audited Vision edifice faces reject only in their relevant contexts") {
    val (base, actor, _) = actWith(VisionRules.Conquest)
    val remote = base.game.current.map.inPlay.find(!actor.pawnSite.contains(_)).get
    val intact = withEdifice(base, remote, EdificeSide.Intact, "edifice.e08.intact")
    assertEquals(rules.handle(Ready(intact), VisionCommand.Reveal(
      actor.player, VisionRules.Conquest)).left.toOption,
      Some(UnsupportedVisionRule(s"edifice:${remote.value}:E08", "edifice.e08.intact")))

    val local = actor.pawnSite.get
    val ruined = withEdifice(base, local, EdificeSide.Ruined, "edifice.e08.ruined")
    assertEquals(rules.handle(Ready(ruined), VisionCommand.Reveal(
      actor.player, VisionRules.Conquest)).left.toOption,
      Some(UnsupportedVisionRule(s"edifice:${local.value}:E08", "edifice.e08.ruined")))
  }

  test("the direct Reveal audit blocks a blocking power while unrelated powers do not") {
    val (base0, actor, _) = actWith(VisionRules.Sanctuary)
    val blocked = withActorAdviser(base0, actor, "denizen.vow-of-obedience")
    assert(rules.handle(Ready(blocked), VisionCommand.Reveal(
      actor.player, VisionRules.Sanctuary)).isLeft)

    val revelation = withActorAdviser(base0, actor, "denizen.revelation")
    assert(rules.handle(Ready(revelation), VisionCommand.Reveal(
      actor.player, VisionRules.Sanctuary)).isRight)
  }

  test("altered Foundations reject at the bounded Vision boundary") {
    val (base, actor, _) = actWith(VisionRules.Rebellion)
    val number = FoundationNumber.I
    val changed = base.updateCampaign(_.copy(
      foundations = base.game.campaign.foundations.updated(number,
        FoundationState(FoundationFace.Altered, Set(LegacyId("legacy:vision-change"))))))
    assertEquals(rules.handle(Ready(changed), VisionCommand.Reveal(
      actor.player, VisionRules.Rebellion)).left.toOption,
      Some(UnsupportedVisionRule("foundation:1",
        "foundation.altered-vision-rules")))
  }
}
