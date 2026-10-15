package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower,
  SiteOnly}
import oathdigital.gameplay.phases.RestCleanup
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

object ARoundOfAleCard extends Denizen(DenizenId("129"), "A Round of Ale", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.a-round-of-ale"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** return all [favor] and [secret], and flip " +
      "your [secret], as in the Rest Phase.")
  val powers: Vector[PrintedPower] = Vector(power)

/** A Round of Ale (card 129, site-only), WHEN PLAYED: return all favor and
  * secrets, and flip your secrets, as in the Rest Phase.
  *
  * It runs the Rest cleanup ([[RestCleanup]]) with the actor as the resting
  * player, in one step: favor on every card in play to its suit's bank,
  * secrets on cards to the actor, and the actor's facedown secrets flipped
  * faceup. The step declares no Rest favor window, so League Treaty does not
  * redirect the favor. Cards cleared of tokens may be used again; powers
  * used this turn stay used. Its line comes first, and the cleanup writes
  * no generic line.
  */
final case class ARoundOfAle private (catalog: ExecutableCatalog)
    extends WhenPlayedPower:
  val cardId: DenizenId = ARoundOfAleCard.id
  import ARoundOfAle._
  def id: PowerId = ARoundOfAle.id

  override def noteKeys: Vector[NoteKey] = ARoundOfAle.noteKeys

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(
      Note(id, _ => Some(cleanup(PowerSourceRef.Card(cardId)))),
      BuildOps((ready, _) => RestCleanup.operations(catalog, ready, actor)))

object ARoundOfAle:
  val id: PowerId = ARoundOfAleCard.power.id
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Triggering resource cleanup." */
  val cleanup: NoteKey = notes.key("cleanup", Vector(
    NotePart.Text("Triggering resource cleanup.")))

  def forCatalog(catalog: ExecutableCatalog): ARoundOfAle =
    new ARoundOfAle(catalog)
