package oathdigital.gameplay.powers.rest

import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}

/** League Treaty staged on p1's Act, shared with the pending-walker
  * invariant (Task 13).
  */
object LeagueTreatyFixture:
  val treatyCard: DenizenId = CatalogNames.denizen("League Treaty")
  /** The site League Treaty stands at in these tests, in the Cradle with
    * Broken Peaks. */
  val treatySite: SiteId = CatalogNames.site("Ancient City")

  /** p1's Act with League Treaty at Ancient City, ruled by one of `ruler`'s
    * warbands, or by one bandit when `ruler` is unset. Each `(card, site,
    * favor)` of `favored` stands faceup at that Cradle site with that much
    * favor on it. Broken Peaks holds two secrets, so p2, waking after p1's
    * Rest, has wealth to take and the Wake waits.
    */
  def leagueTreatyAct(ruler: Option[PlayerId],
    favored: (String, String, Int)*): Table =
    val ruled = ruler.fold(Table.start.bandits(treatySite, 1))(
      Table.start.warbandsAt(treatySite, _, 1))
    favored.foldLeft(ruled.denizen(treatyCard, at = treatySite)
      .siteTokens("Broken Peaks", secrets = 2)) {
      case (table, (card, site, favor)) =>
        table.denizen(card, at = site).tokens(card, favor = favor) }
