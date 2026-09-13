package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** Recover eligibility and scoring, shared by the walker's declared procedure
  * ([[oathdigital.gameplay.actions.recover.RecoverProcedure]]), the reviewed
  * Recover powers, and the application-layer projectors. Carries no walker or
  * legacy-command knowledge of its own -- just the rules every caller needs
  * to agree on.
  */
object RecoverRules {
  def difficulty(catalog: ExecutableCatalog, site: SiteId): Option[Int] =
    catalog.sites.find(_.id == site).flatMap(_.recoverDifficulty)

  def score(faces: Vector[DefenseDieFace]): Int = DefenseDieFace.score(faces)

}
