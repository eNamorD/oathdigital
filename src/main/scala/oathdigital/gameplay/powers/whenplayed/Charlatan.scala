package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

object CharlatanCard extends Denizen(DenizenId("79"), "Charlatan", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.charlatan"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** burn all [secret] but one from the Darkest " +
      "Secret.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Charlatan (card 79), WHEN PLAYED: burn all secrets but one from the
  * Darkest Secret.
  *
  * The banner's secrets are read live. When it holds more than 1, all but 1
  * burn, whoever holds it, or nobody. Otherwise nothing burns and the line
  * says so.
  */
final case class Charlatan private (cardId: DenizenId)
    extends WhenPlayedPower:
  import Charlatan._
  def id: PowerId = Charlatan.id

  override def noteKeys: Vector[NoteKey] = Vector(burned, none)

  def effect(ctx: PowerCtx): Vector[Operation] = Vector(
    BuildOps((ready, _) => Right(burn(ready))),
    Note(id, burnedNote))

  /** What the burn step took from the banner. */
  private def burnedNote(states: NoteStates): Option[PowerNote] =
    val source = PowerSourceRef.Card(cardId)
    val banner = NoteArg.Banner(Banner.DarkestSecret)
    val burnt = states.previous.fold(0)((before, after) =>
      secretsOn(before) - secretsOn(after))
    Some(if burnt > 0 then burned(source,
      NoteArg.Amount(burnt, NoteUnit.Secret), banner)
    else none(source, banner))

object Charlatan:
  val id: PowerId = PowerId("denizen.charlatan")
  /** The secrets the Darkest Secret keeps. */
  val Kept: Int = 1
  /** "Burned {n secrets} from the {Darkest Secret}." */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Text("Burned "),
    NotePart.Arg(0), NotePart.Text(" from the "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "The {Darkest Secret} had no secret to burn." */
  val none: NoteKey = NoteKey("none", Vector(NotePart.Text("The "),
    NotePart.Arg(0), NotePart.Text(" had no secret to burn.")))

  def forCatalog(catalog: ExecutableCatalog): Option[Charlatan] =
    WhenPlayedPower.cardOf(catalog, id).map(new Charlatan(_))

  private def secretsOn(ready: ReadyGame): Int =
    ready.game.current.banners.darkestSecret.secrets

  private def burn(ready: ReadyGame): Vector[CoreOperation] =
    val extra = secretsOn(ready) - Kept
    if extra <= 0 then Vector.empty
    else Vector(Burn.secrets(extra,
      PositionedLocation(Location.OnBanner(Banner.DarkestSecret))))
