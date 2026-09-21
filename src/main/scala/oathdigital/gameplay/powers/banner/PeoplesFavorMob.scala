package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, PowerCtx, Transform}
import oathdigital.model._

/** Mob (the Banner of the People's Favor, Mob face): when you play a card to a
  * site, you may first discard a card from the site's card list.
  *
  * A persistent rule of the banner's holder, automatic. It changes only the
  * rules the play is planned under, through the `SearchPlayAdviser` window, so
  * it composes with Silver Tongue's limit in either order. The card-play
  * planner offers the discard, at any capacity, and the generic discard rules
  * decide which cards may go (an intact edifice and a locked card may not).
  * A full site that would refuse the play accepts it with a discard.
  */
object PeoplesFavorMob extends ContributingPower {
  val id: PowerId = PowerId("banner.peoples-favor.mob")
  def source: RuleSourceRef = RuleSourceRef.Banner(Banner.PeoplesFavor.key)

  /** The player plays a card while holding the banner, Mob face up. */
  override def applicable(ctx: PowerCtx): Boolean = {
    val favor = ctx.state.game.current.banners.peoplesFavor
    favor.active == PeoplesFavorFace.Mob && favor.holder.contains(ctx.activePlayer)
  }

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case tree: CardPlayProcedure.PlacementTree =>
          tree.adjust(children)(_.withSiteDiscardFirst)
        case _ => children
      })))
}
