package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

object ThreateningRoarCard extends Denizen(DenizenId("179"), "Threatening Roar", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.threatening-roar"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** discard all [suit-nomad] and [suit-beast] " +
      "cards at sites in your region.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Threatening Roar (card 179), WHEN PLAYED: discard all Nomad and Beast
  * cards at sites in your region.
  *
  * The discard is [[RegionDiscard]]'s, as Dazzle's is, with Nomad and Beast
  * kept. Played to a site, it is a Beast card at a site in the region, so it
  * discards itself.
  */
final case class ThreateningRoar private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends WhenPlayedPower:
  def id: PowerId = ThreateningRoar.id

  override def noteKeys: Vector[NoteKey] =
    Vector(RegionDiscard.discarded, RegionDiscard.none)

  private val region = new RegionDiscard(catalog, ThreateningRoar.suits)

  def effect(ctx: PowerCtx): Vector[Operation] =
    region.effect(id, cardId, ctx.activePlayer)

object ThreateningRoar:
  val id: PowerId = PowerId("denizen.threatening-roar")
  private val suits: Set[Suit] = Set(Suit.Nomad, Suit.Beast)

  def forCatalog(catalog: ExecutableCatalog): Option[ThreateningRoar] =
    WhenPlayedPower.cardOf(catalog, id).map(new ThreateningRoar(_, catalog))
