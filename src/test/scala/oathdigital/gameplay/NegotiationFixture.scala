package oathdigital.gameplay

import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** The board the Negotiation suites share: every player at one site holding 5
  * favor and one facedown relic each, the first player (the actor) in the Act
  * phase knowing one relic that lies at that site.
  */
object NegotiationFixture {
  final case class Board(ready: ReadyGame, players: Vector[PlayerState],
      site: SiteId, actorRelic: RelicId, otherRelic: RelicId,
      siteRelic: RelicId) {
    def actor: PlayerId = players.head.player
    def second: PlayerId = players(1).player
    def third: PlayerId = players(2).player
  }

  private val setup = new FirstGameSetupRules(catalog)

  def board(): Board = {
    val Ready(base) = execute(setup)._1: @unchecked
    val siteId = base.game.current.map.inPlay.find(id =>
      base.game.current.map.sites(id).relics.nonEmpty).get
    val siteRelic = base.game.current.map.sites(siteId).relics.head.id
    val relics = base.game.current.map.sites.iterator
      .filterNot(_._1 == siteId).flatMap(_._2.relics).map(_.id).take(3).toVector
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
        phase = Phase.Act))
    val ready = base.copy(game = base.game.copy(current = current), knowledge =
      base.knowledge.copy(siteRelics = Map(players.head.player ->
        Map(siteId -> Vector(siteRelic)))))
    Board(ready, players, siteId, relics.head, relics(1), siteRelic)
  }

  private def relocate(board: Board, who: Set[PlayerId]): Board = {
    val elsewhere = board.ready.game.current.map.inPlay.find(_ != board.site).get
    board.copy(ready = board.ready.updateCurrent(current => current.copy(
      players = current.players.map(p =>
        if (who(p.player)) p.copy(pawnSite = Some(elsewhere)) else p))))
  }

  /** The third player moves away, leaving the actor exactly one candidate. */
  def withThirdElsewhere(board: Board): Board = relocate(board, Set(board.third))

  /** Every other player moves away, leaving the actor no candidate. */
  def isolated(board: Board): Board =
    relocate(board, Set(board.second, board.third))

  def player(ready: ReadyGame, id: PlayerId): PlayerState =
    ready.game.current.players.find(_.player == id).get
}
