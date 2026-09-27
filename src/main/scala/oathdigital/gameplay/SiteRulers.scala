package oathdigital.gameplay

import oathdigital.model._

/** Who rules a site, and where a rule card stands, for the powers whose reach
  * is "the sites this card's ruler rules": Toll Roads, Grasping Vines and
  * Secret Police. Whether a player is an enemy of a ruler is
  * `SiteRule.enemies(ruler, SiteRuler.Player(player))`, the model's single
  * definition.
  */
private[gameplay] object SiteRulers:

  /** The site holding `card` faceup. */
  def siteOf(ready: ReadyGame, card: DenizenId): Option[SiteId] =
    ready.game.current.map.sites.collectFirst:
      case (id, site) if site.denizens.exists {
        case DenizenState(`card`, Orientation.FaceUp, _) => true
        case _ => false
      } => id

  def rulerOf(ready: ReadyGame, site: SiteId): Option[SiteRuler] =
    ready.game.current.map.sites.get(site).flatMap(state =>
      SiteRule.ruler(state.forces, ready.game.current.players).toOption)

  /** The ruler of the site holding `card`, when that is a player or bandits.
    * An Empire ruler is not supported.
    */
  def rulerOfCard(ready: ReadyGame, card: DenizenId): Option[SiteRuler] =
    siteOf(ready, card).flatMap(rulerOf(ready, _)).filter:
      case SiteRuler.Player(_) | SiteRuler.Bandits => true
      case _ => false
