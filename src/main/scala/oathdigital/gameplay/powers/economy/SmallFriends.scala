package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.model._

object SmallFriendsCard extends Denizen(DenizenId("177"), "Small Friends", Suit.Beast) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.small-friends"),
    persistent = false, cost = Cost.free,
    text = "Act as if your pawn is at any site with a [suit-beast] " +
      "card. _(You may use Trade modifiers there.)_")
  val powers: Vector[PrintedPower] = Vector(power)

/** Small Friends (card 177, adviser-only), a selected Trade modifier with
  * no cost: "Act as if your pawn is at any site with a beast card. (You may
  * use Trade modifiers there.)" It offers the sites in play, other than the
  * pawn site, that hold a card of the Beast suit ([[ActingSite]]).
  */
final case class SmallFriends private (catalog: ExecutableCatalog)
    extends ActingSite:
  val cardId: DenizenId = SmallFriendsCard.id
  def id: PowerId = SmallFriends.id
  val noSite: String = "No other site has a Beast card"
  val heading: String = "Small Friends: act as if at which site?"

  def sites(ready: ReadyGame, actor: PlayerId): Vector[SiteId] =
    ActingSite.otherSites(ready, actor)(card =>
      catalog.suitOf(card.id).contains(Suit.Beast))

object SmallFriends:
  val id: PowerId = SmallFriendsCard.power.id

  def forCatalog(catalog: ExecutableCatalog): SmallFriends =
    new SmallFriends(catalog)
