package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.powerresolver.{ContributingPower, PhasePower}
import oathdigital.model.PowerId

/** The powers printed on the banner faces, registered through this one object
  * by [[oathdigital.gameplay.powers.PhasePowerCatalog]], like
  * [[oathdigital.gameplay.powers.action.MovementPowers]] and the other groups.
  *
  * A banner face has no catalog entry, so the name and text a player sees for
  * each of its phase powers are declared here.
  */
object BannerFacePowers {
  val phasePowers: Vector[PhasePower] =
    Vector[PhasePower](WanderingFlameMove, WanderingFlamePlace)

  /** The banner faces' rules that change how the engine plans an action. */
  val contributions: Vector[ContributingPower] =
    Vector[ContributingPower](PeoplesFavorMob)

  private val texts: Vector[(PowerId, String, String)] = Vector(
    (WanderingFlameMove.id, "Wandering Flame: move",
      "ACTION: Place your pawn at any other site with a secret on it."),
    (WanderingFlamePlace.id, "Wandering Flame: place a secret",
      "ACTION: Move a secret from your board to the site your pawn is at."))

  /** The name and the text of a banner face's phase power. */
  def printed(id: PowerId): Option[(String, String)] =
    texts.collectFirst { case (`id`, name, text) => (name, text) }
}
