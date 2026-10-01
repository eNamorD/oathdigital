package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.model._

/** Riots (card 91), WHEN PLAYED: discard all denizens at sites in this
  * region. Burn the same number of favor from the People's Favor.
  *
  * The discard is [[RegionDiscard]]'s with every suit kept: every site
  * denizen and ruined edifice in the actor's region, as far as the generic
  * discard rules permit. Played to a site, Riots stands there, so it
  * discards and counts itself. The burn is one favor for each target no
  * site holds after the discard, from the People's Favor, whoever holds it,
  * as far as it holds favor. With nothing discarded nothing is burned, and
  * only the `none` line is written.
  */
final case class Riots private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends WhenPlayedPower:
  import Riots._
  def id: PowerId = Riots.id

  override def noteKeys: Vector[NoteKey] =
    Vector(RegionDiscard.discarded, burned, unburned, RegionDiscard.none)

  private val region = new RegionDiscard(catalog, _ => true)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(Branch((live, _) =>
      val found = region.targets(live, actor)
      Vector(
        BuildOps((ready, _) => region.discards(ready, actor)),
        Note(id, RegionDiscard.said(cardId, found), covers = true),
        BuildOps((ready, _) => Right(burn(ready, found))),
        Note(id, burnedNote(_, found)))))

  /** What the burn step took from the banner, or that it held none. Nothing
    * when nothing was discarded. */
  private def burnedNote(states: NoteStates, found: Vector[CardId])
      : Option[PowerNote] =
    val source = PowerSourceRef.Card(cardId)
    val banner = NoteArg.Banner(Banner.PeoplesFavor)
    val burnt = states.previous.fold(0)((before, after) =>
      favorOn(before) - favorOn(after))
    Option.when(RegionDiscard.gone(found, states.now).nonEmpty)(
      if burnt > 0 then burned(source, NoteArg.Amount(burnt, NoteUnit.Favor),
        banner)
      else unburned(source, banner))

object Riots:
  val id: PowerId = PowerId("denizen.riots")
  /** "Burned {n favor} from the {People's Favor}." */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Text("Burned "),
    NotePart.Arg(0), NotePart.Text(" from the "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "The {People's Favor} had no favor to burn." */
  val unburned: NoteKey = NoteKey("unburned", Vector(NotePart.Text("The "),
    NotePart.Arg(0), NotePart.Text(" had no favor to burn.")))

  def forCatalog(catalog: ExecutableCatalog): Option[Riots] =
    WhenPlayedPower.cardOf(catalog, id).map(new Riots(_, catalog))

  private def favorOn(ready: ReadyGame): Int =
    ready.game.current.banners.peoplesFavor.favor

  /** One favor for each of `found` that no site holds now. */
  private def burn(ready: ReadyGame, found: Vector[CardId])
      : Vector[CoreOperation] =
    val count = RegionDiscard.gone(found, ready).size
    if count == 0 then Vector.empty
    else Vector(Burn.favor(count,
      PositionedLocation(Location.OnBanner(Banner.PeoplesFavor))))
