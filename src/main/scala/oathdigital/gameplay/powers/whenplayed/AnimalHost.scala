package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts}
import oathdigital.model._

object AnimalHostCard extends Denizen(DenizenId("190"), "Animal Host", Suit.Beast):
  val power = PrintedPower(PowerId("denizen.animal-host"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** gain warbands equal to the total number of " +
      "[suit-beast] cards _(including Animal Host)_ at any sites " +
      "_(regardless of rule)_.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Animal Host (card 190), WHEN PLAYED: gain warbands equal to the total
  * number of Beast cards (including Animal Host) at any sites (regardless of
  * rule).
  *
  * The count is every faceup Beast denizen and every Beast edifice, on
  * either face, at every site in play, read live. Played to a site, Animal
  * Host is one of them; played as an adviser it is not at a site, so it does
  * not count itself (rulings, "Cards counting themselves"). The gain is best
  * effort, so it takes what the bank holds. A gain of 0 writes the `none`
  * line.
  */
final case class AnimalHost private (catalog: ExecutableCatalog)
    extends WhenPlayedPower:
  val cardId: DenizenId = AnimalHostCard.id
  import AnimalHost._
  def id: PowerId = AnimalHost.id

  override def noteKeys: Vector[NoteKey] = Vector(gained, none)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(BuildOps((ready, _) => gain(ready, actor)),
      Note(id, gainedNote(_, actor)))

  private def gain(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    val count = beasts(ready)
    if count == 0 then Right(Vector.empty)
    else PlayerFacts.forceKind(ready, actor).map(kind =>
      Vector(Gain.Warbands(actor, kind, count)))

  /** The faceup Beast denizens and the Beast edifices at every site in
    * play. */
  private def beasts(ready: ReadyGame): Int =
    val map = ready.game.current.map
    map.inPlay.flatMap(map.sites.get).flatMap(_.denizens).count {
      case DenizenState(card, Orientation.FaceUp, _) =>
        catalog.suitOf(card).contains(Suit.Beast)
      case edifice: EdificeState =>
        catalog.suitOf(edifice.id).contains(Suit.Beast)
      case _ => false
    }

  /** The warbands the gain step added to the actor's board. */
  private def gainedNote(states: NoteStates, actor: PlayerId)
      : Option[PowerNote] =
    val source = PowerSourceRef.Card(cardId)
    val count = states.previous.fold(0)(NoteSupport.warbands(_, actor))
    Some(if count > 0 then gained(source, NoteArg.Player(actor),
      NoteArg.Amount(count, NoteUnit.Warband))
    else none(source, NoteArg.Player(actor)))

object AnimalHost:
  val id: PowerId = AnimalHostCard.power.id
  /** "{Red} gained {n warbands}." */
  val gained: NoteKey = NoteSupport.gainedKey("gained")
  /** "{Red} gained no warbands." */
  val none: NoteKey = NoteKey("none", Vector(NotePart.Arg(0),
    NotePart.Text(" gained no warbands.")))

  def forCatalog(catalog: ExecutableCatalog): AnimalHost =
    new AnimalHost(catalog)
