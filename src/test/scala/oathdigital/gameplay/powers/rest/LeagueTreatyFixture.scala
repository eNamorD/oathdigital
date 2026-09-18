package oathdigital.gameplay.powers.rest

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** League Treaty arranged on a first-game Act state, shared with the
  * pending-walker invariant (Task 13).
  */
object LeagueTreatyFixture {
  val treatyCard = DenizenId("237")

  def act: ReadyGame = {
    val initial = initialReady
    initial.updateCurrent(_.copy(
      turn = initial.game.current.turn.copy(phase = Phase.Act)))
  }

  def suitOf(id: DenizenId): Suit = catalog.suitOf(id).get

  /** Places League Treaty and `favor` (suit -> amounts per card) on the
    * treaty site's region, with every placed card pulled out of the decks
    * and every other in-play card emptied of favor. The treaty site is ruled
    * by `ruler`'s warband when `ruler` is set, and by bandits otherwise.
    */
  def arranged(ruler: Option[PlayerId],
      favor: Vector[(Suit, Int)]): (ReadyGame, SiteId) = {
    val base = act
    val current = base.game.current
    val site = current.map.cradle.head
    val region = current.map.inPlay.filter(current.map.regionOf(_) ==
      current.map.regionOf(site))
    val deck = (current.commonCards.worldDeck ++
      current.commonCards.regionalDiscards.values.flatten)
      .collect { case id: DenizenId => id }
    val picks = favor.foldLeft(Vector.empty[(DenizenId, Int)]) {
      case (chosen, (suit, amount)) => chosen :+ (deck.find(id =>
        id != treatyCard && !chosen.exists(_._1 == id) && suitOf(id) == suit)
        .get -> amount)
    }
    val lineage = current.players.map(p => p.player -> p.lineage).toMap
    val placed = picks.zipWithIndex.groupBy { case (_, i) =>
      region(i % region.size) }.map { case (id, rows) => id -> rows.map {
        case ((card, amount), _) => DenizenState(card, Orientation.FaceUp,
          Tokens(amount, 0)) } }
    val sites = current.map.sites.map { case (id, state) =>
      val emptied = state.copy(denizens = state.denizens.map {
        case d: DenizenState => d.copy(tokens = d.tokens.copy(favor = 0))
        case e: EdificeState => e.copy(tokens = e.tokens.copy(favor = 0))
      })
      val treaty = Vector(DenizenState(treatyCard, Orientation.FaceUp,
        Tokens.empty)).filter(_ => id == site)
      id -> (if (id == site) emptied.copy(forces = ruler.fold[SiteForces](
          SiteForces.Occupied(ForceKind.Bandit, 1))(owner =>
          SiteForces.Occupied(ForceKind.Exile(lineage(owner)), 1)),
          denizens = treaty ++ placed.getOrElse(id, Vector.empty))
        else emptied.copy(denizens = emptied.denizens ++
          placed.getOrElse(id, Vector.empty)))
    }
    val removed = picks.map(_._1).toSet + treatyCard
    base.copy(game = base.game.copy(current = current.copy(
      map = current.map.copy(sites = sites),
      commonCards = current.commonCards.copy(
        worldDeck = current.commonCards.worldDeck.filterNot {
          case id: DenizenId => removed(id)
          case _ => false
        }, regionalDiscards = current.commonCards.regionalDiscards.map {
          case (region, cards) => region -> cards.filterNot {
            case id: DenizenId => removed(id)
            case _ => false
          }
        })))) -> site
  }
}
