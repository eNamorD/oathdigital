package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** Reads and rewrites the cost node of a Travel that several powers share. */
private[travel] object TravelPayments {

  /** The cost node without the traveller's Supply payment. `SpendSupply` cannot
    * be zero, so a free Travel has no payment at all.
    */
  def withoutSupply(operations: Vector[Operation], traveller: PlayerId)
      : Vector[Operation] = operations.filter {
    case SpendSupply(player, _, _) => player != traveller
    case _ => true
  }

  def region(ready: ReadyGame, site: SiteId): Option[Region] =
    ready.game.current.map.regionOf(site)

  /** Whether `site` holds a faceup denizen or an edifice, intact or ruined,
    * of `suit`.
    */
  def holdsSuit(ready: ReadyGame, site: SiteId, suit: Suit,
      suitOf: CardId => Option[Suit]): Boolean =
    ready.game.current.map.sites.get(site).exists(_.denizens.exists {
      case card: DenizenState => suitOf(card.id).contains(suit)
      case card: EdificeState => suitOf(card.id).contains(suit)
    })

  /** The warband of the traveller's own kind, if it has one. */
  def ownWarband(ready: ReadyGame, traveller: PlayerId, amount: Int)
      : Option[Piece.Warbands] = PlayerFacts.forceKind(ready, traveller)
    .toOption.map(kind => Piece.Warbands(kind, amount))
}

/** Who rules the site a rule card stands at, for Toll Roads and Grasping Vines. */
private[travel] object TravelRulers {

  /** The site holding `card` faceup. */
  def siteOf(ready: ReadyGame, card: DenizenId): Option[SiteId] =
    ready.game.current.map.sites.collectFirst {
      case (id, site) if site.denizens.exists {
        case DenizenState(`card`, Orientation.FaceUp, _) => true
        case _ => false
      } => id
    }

  def rulerOf(ready: ReadyGame, site: SiteId): Option[SiteRuler] =
    ready.game.current.map.sites.get(site).flatMap(state =>
      SiteRule.ruler(state.forces, ready.game.current.players).toOption)

  /** The ruler of the site holding `card`, when that is a player or bandits.
    * An Empire ruler is not supported.
    */
  def rulerOfCard(ready: ReadyGame, card: DenizenId): Option[SiteRuler] =
    siteOf(ready, card).flatMap(rulerOf(ready, _)).filter {
      case SiteRuler.Player(_) | SiteRuler.Bandits => true
      case _ => false
    }

  /** Whether `traveller` is an enemy of `ruler`: every player but the ruler
    * itself, and every player when bandits rule.
    */
  def isEnemy(ruler: SiteRuler, traveller: PlayerId): Boolean = ruler match {
    case SiteRuler.Player(owner) => owner != traveller
    case SiteRuler.Bandits => true
    case _ => false
  }
}
