package oathdigital.gameplay

import oathdigital.protocol.projection.BoardTargetRefProjection

import oathdigital.gameplay.actions.EconomyCommand
import oathdigital.model._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.OathEvent.{Mustered, Traded}
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.OathViolation._

class EconomySuite extends munit.FunSuite {
  private val setup = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)
  private val economic = Set("73", "76", "40", "42", "176", "177", "193",
    "196", "81", "144", "6", "119", "120", "199", "102", "224",
    "229", "231", "238", "241", "248")

  private val plain = catalog.denizens.find(d => !economic(d.id.value)).get
  private val matching = catalog.denizens.find(d => d.suit == plain.suit &&
    d.id != plain.id && !economic(d.id.value)).get
  private val plainId = DenizenId(plain.id.value)
  private val plainTarget = EconomyTargetRef.Denizen(plainId)
  private val matchingId = DenizenId(matching.id.value)
  private val springDefinition = catalog.edifices.find(
    _.intact.handlers.contains("edifice.e26.intact")).get
  private val springId = EdificeId(springDefinition.id.value)

  private def act(tokens: Tokens = Tokens.empty, favor: Int = 4,
      secrets: Int = 2, supply: Int = 7, advisers: Vector[AdviserState] = Vector.empty,
      bank: Int = 5, boardWarbands: Int = 3): ReadyGame = {
    val Ready(initial) = execute(setup)._1: @unchecked
    val activeId = initial.game.current.turn.activePlayer
    val active = initial.game.current.players.find(_.player == activeId).get
    val siteId = active.pawnSite.get
    val site = initial.game.current.map.sites(siteId).copy(denizens = Vector(
      DenizenState(plainId, Orientation.FaceUp, tokens)))
    val inserted = advisers.map(_.id).toSet + plainId
    initial.copy(
      banks = initial.banks.copy(favor = initial.banks.favor.updated(
        Suit.all.find(_.key == plain.suit.value).get, bank)),
      game = initial.game.copy(current = initial.game.current.copy(
        turn = initial.game.current.turn.copy(phase = Phase.Act),
        commonCards = initial.game.current.commonCards.copy(worldDeck =
          initial.game.current.commonCards.worldDeck.filterNot(inserted)),
        map = initial.game.current.map.copy(sites =
          initial.game.current.map.sites.updated(siteId, site)),
        players = initial.game.current.players.map(p => if (p.player != activeId) p
          else p.copy(board = p.board.copy(favor = favor,
            faceUpSecrets = secrets, supply = SupplyTrack(supply),
            warbands = boardWarbands), advisers = advisers)))))
  }

  private def player(ready: ReadyGame) = ready.game.current.players.find(
    _.player == ready.game.current.turn.activePlayer).get

  private def spring(ready: ReadyGame, side: EdificeSide): ReadyGame = {
    val actor = player(ready)
    val siteId = actor.pawnSite.get
    val withoutSpring = ready.game.current.map.sites.map {
      case (id, site) => id -> site.copy(
        denizens = site.denizens.filterNot(_.id == springId))
    }
    ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      map = ready.game.current.map.copy(sites = withoutSpring.updated(
        siteId, ready.game.current.map.sites(siteId).copy(denizens = Vector(
          EdificeState(springId, side, Tokens.empty))))),
      commonCards = ready.game.current.commonCards.copy(edificeDeck =
        ready.game.current.commonCards.edificeDeck.filterNot(_ == springId)))))
  }

  test("Muster costs one Supply and favor and uses NF adviser yield") {
    val adviser = DenizenState(matchingId, Orientation.FaceUp, Tokens.empty)
    val ready = act(advisers = Vector(adviser))
    val actor = player(ready)
    val accepted = rules.handle(Ready(ready),
      EconomyCommand.Muster(actor.player, plainTarget)).toOption.get
    val Ready(after) = accepted.state: @unchecked
    assertEquals(accepted.events.head.asInstanceOf[Mustered].warbandsGained, 2)
    assertEquals(player(after).board.favor, 3)
    assertEquals(player(after).board.supply.supply, 6)
    assertEquals(player(after).board.warbands, 5)
    assertEquals(after.game.current.map.sites(actor.pawnSite.get).denizens.head.tokens,
      Tokens(1, 0))
  }

  test("Muster obeys the finite fourteen-warband supply") {
    val ready = act(boardWarbands = 14)
    val actor = player(ready)
    val accepted = rules.handle(Ready(ready),
      EconomyCommand.Muster(actor.player, plainTarget)).toOption.get
    assertEquals(accepted.events.head.asInstanceOf[Mustered].warbandsGained, 0)
  }

  test("Economy rejects a missing bounded warband supply") {
    val ready = act()
    val actor = player(ready)
    val malformed = ready.copy(banks = ready.banks.copy(warbandSupply =
      ready.banks.warbandSupply - ForceKind.Exile(actor.lineage)))

    assert(rules.handle(Ready(malformed), EconomyCommand.Muster(
      actor.player, plainTarget)).left.toOption.get
      .isInstanceOf[UnsupportedEconomyState])
  }

  test("Trade for favor moves secret and caps yield at the matching bank") {
    val adviser = DenizenState(matchingId, Orientation.FaceUp, Tokens.empty)
    val ready = act(advisers = Vector(adviser), bank = 1)
    val actor = player(ready)
    val accepted = rules.handle(Ready(ready), EconomyCommand.Trade(
      actor.player, plainTarget, TradeResource.Favor)).toOption.get
    val Ready(after) = accepted.state: @unchecked
    assertEquals(accepted.events.head.asInstanceOf[Traded].gained, 1)
    assertEquals(player(after).board.faceUpSecrets, 1)
    assertEquals(player(after).board.favor, 5)
    assertEquals(after.banks.favor(
      Suit.all.find(_.key == plain.suit.value).get), 0)
  }

  test("NF Trade for secrets places one favor burns one and yields matches") {
    val adviser = DenizenState(matchingId, Orientation.FaceUp, Tokens.empty)
    val ready = act(advisers = Vector(adviser))
    val actor = player(ready)
    val accepted = rules.handle(Ready(ready), EconomyCommand.Trade(
      actor.player, plainTarget, TradeResource.Secret)).toOption.get
    val Ready(after) = accepted.state: @unchecked
    assertEquals(accepted.events.head.asInstanceOf[Traded].gained, 1)
    assertEquals(player(after).board.favor, 2)
    assertEquals(player(after).board.faceUpSecrets, 3)
    assertEquals(after.game.current.map.sites(actor.pawnSite.get).denizens.head.tokens,
      Tokens(1, 0))
  }

  test("legality rejects occupied cards costs and tampered replay") {
    val occupied = act(tokens = Tokens(0, 1))
    val actor = player(occupied)
    assert(rules.handle(Ready(occupied), EconomyCommand.Muster(
      actor.player, plainTarget)).left.toOption.get.isInstanceOf[EconomyCardNotEmpty])
    val poor = act(favor = 1)
    assertEquals(rules.handle(Ready(poor), EconomyCommand.Trade(
      player(poor).player, plainTarget, TradeResource.Secret)).left.toOption.get,
      InsufficientFavor(2, 1))
    val ready = act()
    val p = player(ready)
    val suit = Suit.all.find(_.key == plain.suit.value).get
    assert(rules.evolve(Ready(ready), Mustered(p.player, p.pawnSite.get,
      plainTarget, suit, 1, 9)).left.toOption.get
      .isInstanceOf[EconomyOutcomeMismatch])
  }

  test("player-scoped legal controls are redacted") {
    val ready = act()
    val p = player(ready)
    val projector = new oathdigital.application.GameProjector(catalog)
    val own = projector.project("economy",
      oathdigital.application.LoadedGame(Ready(ready), 4), p.player)
    val other = ready.game.current.players.find(_.player != p.player).get
    val hidden = projector.project("economy",
      oathdigital.application.LoadedGame(Ready(ready), 4), other.player)
    assert(own.legalMusters.nonEmpty)
    assert(own.legalTrades.nonEmpty)
    assertEquals(own.boardTargetActions.map(_.actionKind).toSet,
      Set("travel", "campaign-conquest", "challenge", "muster", "trade-favor", "trade-secret"))
    val economy = own.boardTargetActions.filterNot(action =>
      action.actionKind == "travel" || action.actionKind == "campaign-conquest" ||
        action.actionKind == "challenge")
    assert(economy.flatMap(_.candidates).forall(_.target.isInstanceOf[
      BoardTargetRefProjection.SiteCard]))
    assert(economy.flatMap(_.candidates).forall(_.details.size == 2))
    assertEquals(hidden.legalMusters, Vector.empty)
    assertEquals(hidden.legalTrades, Vector.empty)
    assertEquals(hidden.boardTargetActions, Vector.empty)
  }

  test("ruined edifice is a legal typed target and replays its update") {
    val ready = spring(act(), EdificeSide.Ruined)
    val actor = player(ready)
    val target = EconomyTargetRef.Edifice(springId)
    val own = new oathdigital.application.GameProjector(catalog).project(
      "edifice", oathdigital.application.LoadedGame(Ready(ready), 7), actor.player)
    assertEquals(own.legalMusters.map(_.targetKind), Vector("edifice"))
    assertEquals(own.legalMusters.map(_.targetId), Vector(springId.value))
    assert(own.legalMusters.head.label.nonEmpty)
    val accepted = rules.handle(Ready(ready),
      EconomyCommand.Muster(actor.player, target)).toOption.get
    val event = accepted.events.head.asInstanceOf[Mustered]
    assertEquals(event.target, target)
    assertEquals(rules.evolve(Ready(ready), event), Right(accepted.state))
    val Ready(after) = accepted.state: @unchecked
    assertEquals(after.game.current.map.sites(actor.pawnSite.get).denizens.head.tokens,
      Tokens(1, 0))
  }

  test("unimplemented optional Economy handler does not block base Trade") {
    val ready = spring(act(), EdificeSide.Intact)
    val actor = player(ready)
    val result = rules.handle(Ready(ready), EconomyCommand.Trade(actor.player,
      EconomyTargetRef.Edifice(springId), TradeResource.Secret))
    assert(result.isRight)
    assert(result.toOption.get.events.exists(_.isInstanceOf[Traded]))
  }
}
