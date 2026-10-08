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

  /** p1's Act with p1's pawn at Ancient City and League Treaty there, ruled
    * by one of `ruler`'s warbands, or by one bandit when `ruler` is unset.
    * p2's pawn stands at Broken Peaks, which holds two secrets, so p2,
    * waking after p1's Rest, has wealth to take and the Wake waits. Add
    * favored cards with `withFavoredCard`.
    */
  def leagueTreatyAct(ruler: Option[PlayerId]): Table =
    val quiet = Table.start.pawn(Table.p1, treatySite)
      .pawn(Table.p2, "Broken Peaks").pawn(Table.p3, "Buried Giant")
    val ruled = ruler.fold(quiet.bandits(treatySite, 1))(
      quiet.warbandsAt(treatySite, _, 1))
    ruled.denizen(treatyCard, at = treatySite)
      .siteTokens("Broken Peaks", secrets = 2)

  extension (table: Table)
    /** `card` stands faceup at the Cradle site `at`, with `favor` favor on
      * it. */
    def withFavoredCard(card: String, at: String, favor: Int): Table =
      table.denizen(card, at = at).tokens(card, favor = favor)
