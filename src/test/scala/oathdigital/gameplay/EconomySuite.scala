package oathdigital.gameplay

import oathdigital.gameplay.actions.EconomyCommand
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.FirstGameSetupFixture._
import oathdigital.setup.OathEvent.{Mustered, Traded}
import oathdigital.setup.OathState.Ready
import oathdigital.setup.OathViolation._

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
    initial.copy(
      support = initial.support.copy(favorBanks = initial.support.favorBanks.updated(
        Suit.all.find(_.key == plain.suit.value).get, bank)),
      game = initial.game.copy(current = initial.game.current.copy(
        turn = initial.game.current.turn.copy(phase = Phase.Act),
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
    ready.copy(game = ready.game.copy(current = ready.game.current.copy(
      map = ready.game.current.map.copy(sites = ready.game.current.map.sites.updated(
        siteId, ready.game.current.map.sites(siteId).copy(denizens = Vector(
          EdificeState(springId, side, Tokens.empty))))))))
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
    assertEquals(after.support.favorBanks(
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
    assertEquals(hidden.legalMusters, Vector.empty)
    assertEquals(hidden.legalTrades, Vector.empty)
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

  test("intact Hallowed Spring Economy handler rejects base Trade explicitly") {
    val ready = spring(act(), EdificeSide.Intact)
    val actor = player(ready)
    val result = rules.handle(Ready(ready), EconomyCommand.Trade(actor.player,
      EconomyTargetRef.Edifice(springId), TradeResource.Secret))
    assertEquals(result.left.toOption.get, UnsupportedEconomyState(
      "unsupported relevant Economy handler edifice.e26.intact"))
  }
}
