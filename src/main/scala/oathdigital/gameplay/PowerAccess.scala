package oathdigital.gameplay

import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

/** The rulebook's access rule for using a card's power: cards at your site,
  * cards at sites you rule, and everything in your play area (relics,
  * banners, advisers and legacies). One definition, used by phase powers and
  * by every walker contribution's `applicable`.
  *
  * Denizens at sites are always faceup and relics at sites are always
  * facedown, so a site relic never grants access. Both faces of an edifice
  * count. Faceup advisers and relics only, unless the caller opts in to
  * facedown advisers (battle plans).
  *
  * The pre-walker resolver (`RuleSourceAccess`, used by `ReviewedPowerInspector`)
  * is a separate, older rule and is deliberately not changed here.
  */
private[gameplay] object PowerAccess {
  sealed trait Held extends Product with Serializable
  object Held {
    final case class AtSite(site: SiteId) extends Held
    case object InPlayArea extends Held
  }

  def pawnSite(ready: ReadyGame, actor: PlayerId): Option[SiteId] =
    ready.game.current.players.find(_.player == actor).flatMap(_.pawnSite)

  def ruledSites(ready: ReadyGame, actor: PlayerId): Set[SiteId] = {
    val current = ready.game.current
    current.map.inPlay.filter(id => current.map.sites.get(id).exists(state =>
      SiteRule.ruler(state.forces, current.players).toOption
        .contains(SiteRuler.Player(actor)))).toSet
  }

  private def usableSites(ready: ReadyGame, actor: PlayerId): Set[SiteId] =
    ruledSites(ready, actor) ++ pawnSite(ready, actor)

  def accessible(ref: RuleSourceRef, face: RuleSourceFace, ready: ReadyGame,
      actor: PlayerId, facedownAdviser: Boolean = false): Boolean = {
    lazy val sites = usableSites(ready, actor)
    ref match {
      case RuleSourceRef.Site(id) => pawnSite(ready, actor).contains(id)
      case RuleSourceRef.SiteCard(id, _) =>
        face == RuleSourceFace.FaceUp && sites(id)
      case RuleSourceRef.SiteRelic(_, _) => false
      case RuleSourceRef.Edifice(id, _) =>
        (face == RuleSourceFace.Intact || face == RuleSourceFace.Ruined) &&
          sites(id)
      case RuleSourceRef.Adviser(owner, _) => owner == actor &&
        (face == RuleSourceFace.FaceUp ||
          (facedownAdviser && face == RuleSourceFace.FaceDown))
      case RuleSourceRef.Relic(owner, _) =>
        owner == actor && face == RuleSourceFace.FaceUp
      case RuleSourceRef.Banner(key) => Banner.fromKey(key).exists(banner =>
        BannerRules.holder(ready.game.current, banner).contains(actor))
      case RuleSourceRef.Foundation(_) => true
      case RuleSourceRef.Legacy(lineage, _) =>
        ready.game.current.players.find(_.player == actor)
          .exists(_.lineage == lineage) && face == RuleSourceFace.Active
      case _ => false
    }
  }

  /** Where `card` is, if `actor` may use it. A site card or edifice at a
    * usable site is `AtSite`. A faceup adviser or relic in the actor's play
    * area is `InPlayArea`.
    */
  def locate(ready: ReadyGame, actor: PlayerId, card: CardId): Option[Held] = {
    val current = ready.game.current
    val atSite = usableSites(ready, actor).toVector.sortBy(_.value).collectFirst {
      case id if current.map.sites.get(id).exists(_.denizens.exists {
        case d: DenizenState => d.id == card && d.orientation == Orientation.FaceUp
        case e: EdificeState => e.id == card
      }) => Held.AtSite(id)
    }
    lazy val inPlay = current.players.find(_.player == actor).exists(player =>
      player.advisers.exists {
        case d: DenizenState => d.id == card && d.orientation == Orientation.FaceUp
        case _ => false
      } || player.relics.exists(relic =>
        relic.id == card && relic.orientation == Orientation.FaceUp))
    atSite.orElse(Option.when(inPlay)(Held.InPlayArea))
  }

  def siteOf(ready: ReadyGame, actor: PlayerId, card: CardId): Option[SiteId] =
    locate(ready, actor, card).flatMap {
      case Held.AtSite(site) => Some(site)
      case Held.InPlayArea => pawnSite(ready, actor)
    }
}
