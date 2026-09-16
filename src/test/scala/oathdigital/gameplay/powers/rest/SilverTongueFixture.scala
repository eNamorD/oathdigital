package oathdigital.gameplay.powers.rest

import oathdigital.gameplay._
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** Silver Tongue arranged in the Rest phase, shared by its own suite, the
  * projection suite and the pending-walker invariant (Task 13).
  */
object SilverTongueFixture {
  val tongue: DenizenId = DenizenId("92")

  def suitOf(id: DenizenId): Suit = Suit.all.find(suit => catalog
    .denizens.find(_.id.value == id.value).exists(_.suit.value == suit.key)).get

  /** The Rest phase, with Silver Tongue as the active player's only adviser.
    * The pawn site shows one faceup denizen per suit in `siteSuits`, every
    * bank in `stocked` holds 3 favor and every other bank is empty. A site
    * with capacity is emptied so the action boundary visibly refills it.
    */
  def arranged(siteSuits: Vector[Suit], stocked: Set[Suit])
      : (ReadyGame, PlayerId) = {
    val Ready(ready) = execute(new FirstGameSetupRules(catalog))._1: @unchecked
    val current = ready.game.current
    val actor = current.turn.activePlayer
    val pawn = current.players.find(_.player == actor).get.pawnSite.get
    val deck = catalog.denizens.map(d => DenizenId(d.id.value))
    val cards = siteSuits.map(suit =>
      deck.find(id => id != tongue && suitOf(id) == suit).get)
    val empty = current.map.inPlay.find(id => id != pawn &&
      catalog.sites.find(_.id == id).exists(_.capacity > 0)).get
    val removed = cards.toSet + tongue
    val clearedSites = current.map.sites.map { case (id, site) => id ->
      site.copy(denizens = site.denizens.filter {
        case DenizenState(card, _, _) => !removed(card)
        case _ => true
      })
    }
    val sites = clearedSites
      .updated(pawn, clearedSites(pawn).copy(denizens = cards.map(
        DenizenState(_, Orientation.FaceUp, Tokens.empty))))
      .updated(empty, clearedSites(empty).copy(forces = SiteForces.Empty))
    val state = ready.copy(
      banks = ready.banks.copy(favor = Suit.all.map(suit =>
        suit -> (if (stocked(suit)) 3 else 0)).toMap),
      game = ready.game.copy(current = current.copy(
        turn = TurnState(actor, Phase.Rest, Set.empty),
        map = current.map.copy(sites = sites),
        players = current.players.map(p => if (p.player != actor)
          p.copy(advisers = p.advisers.filterNot {
            case DenizenState(card, _, _) => removed(card)
            case _ => false
          })
        else p.copy(advisers = Vector(DenizenState(tongue, Orientation.FaceUp,
          Tokens.empty)))),
        commonCards = current.commonCards.copy(worldDeck =
          current.commonCards.worldDeck.filterNot {
            case id: DenizenId => removed(id)
            case _ => false
          }, regionalDiscards = current.commonCards.regionalDiscards.view
            .mapValues(_.filterNot {
              case id: DenizenId => removed(id)
              case _ => false
            }).toMap))))
    (state, actor)
  }
}
