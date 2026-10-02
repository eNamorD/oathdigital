package oathdigital.gameplay.powers.rest

import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** Silver Tongue staged in the Rest phase, shared by its own suite, the
  * projection suite and the pending-walker invariant (Task 13).
  */
object SilverTongueFixture:
  val tongue: DenizenId = CatalogNames.denizen("Silver Tongue")

  /** p1's Rest at Ancient City, holding Silver Tongue as the only adviser.
    * Ancient City shows `siteCards` faceup. Every bank in `stocked` holds 3
    * favor and every other bank none. Broken Peaks holds two secrets, so
    * p2, waking next, has wealth to take and the Wake waits. No site holds a
    * bandit, as on the quiet table, so the action boundary visibly refills
    * the sites with capacity.
    */
  def silverTongueRest(siteCards: Vector[String], stocked: Set[Suit])
      : ReadyGame =
    val resting = Table.start.turn(p1, Phase.Rest).pawn(p1, "Ancient City")
      .adviser(p1, tongue).siteTokens("Broken Peaks", secrets = 2)
    val shown = siteCards.foldLeft(resting)(_.denizen(_, at = "Ancient City"))
    Suit.all.foldLeft(shown)((table, suit) =>
      table.bankFavor(suit, if stocked(suit) then 3 else 0)).ready
