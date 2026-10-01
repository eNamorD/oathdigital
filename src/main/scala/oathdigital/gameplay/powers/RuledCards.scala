package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.SiteRulers
import oathdigital.model._

/** The cards a ruler rules (catalog batch 3 rulings, "Cards a player
  * rules"): a player's faceup advisers, and the denizens and edifices at the
  * sites the ruler rules. Bandits hold no advisers; they rule the cards at the
  * sites they rule, as `SiteRulers.rulerOfCard` treats them. Only a faceup
  * card has a suit, so a facedown adviser or denizen belongs to no suit, while
  * an edifice has its suit on either face.
  */
object RuledCards:
  /** The cards of `suit` that `ruler` rules: its advisers first, then the
    * cards at each ruled site in map order. */
  def of(catalog: ExecutableCatalog, ready: ReadyGame, ruler: SiteRuler,
      suit: Suit): Vector[CardId] =
    val current = ready.game.current
    val advisers: Vector[CardId] = ruler match
      case SiteRuler.Player(player) => current.players.find(_.player == player)
        .toVector.flatMap(_.advisers.collect {
          case DenizenState(held, Orientation.FaceUp, _) => held })
      case _ => Vector.empty
    val atSites: Vector[CardId] = current.map.inPlay
      .filter(site => SiteRulers.rulerOf(ready, site).contains(ruler))
      .flatMap(current.map.sites.get).flatMap(_.denizens).collect {
        case DenizenState(held, Orientation.FaceUp, _) => held
        case edifice: EdificeState => edifice.id
      }
    (advisers ++ atSites).filter(card => catalog.suitOf(card).contains(suit))
