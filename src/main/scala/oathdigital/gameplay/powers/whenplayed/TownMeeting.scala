package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.NoteSupport
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.RuledCards
import oathdigital.gameplay.powers.action.FavorSplit
import oathdigital.model._

object TownMeetingCard extends Denizen(DenizenId("236"), "Town Meeting", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.town-meeting"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** gain [favor] from any favor bank for each " +
      "[suit-hearth] card you rule. _This includes your advisers, " +
      "even Town Meeting._")
  val powers: Vector[PrintedPower] = Vector(power)

/** Town Meeting (card 236), WHEN PLAYED: gain favor from any favor bank for
  * each Hearth card you rule. This includes your advisers, even Town
  * Meeting.
  *
  * X is counted as Fabled Feast counts it. The favor comes from any banks,
  * split as Alchemist splits ([[FavorSplit]]): the actor is asked only when
  * two or more banks hold favor and they hold more than X in all. The
  * rulings settle that the favor may come from any combination of banks,
  * unlike Fabled Feast's one bank. Its line tells the whole gain; each
  * bank's Gain line stays.
  */
final case class TownMeeting private (catalog: ExecutableCatalog)
    extends WhenPlayedPower:
  val cardId: DenizenId = TownMeetingCard.id
  import TownMeeting._
  def id: PowerId = TownMeeting.id

  override def noteKeys: Vector[NoteKey] = TownMeeting.noteKeys

  private val banks = new FavorSplit(decisionId, Suit.all, "Take favor")

  def effect(ctx: PowerCtx): Vector[Operation] =
    val source = PowerSourceRef.Card(cardId)
    Vector(Branch((live, _) =>
      val count = RuledCards.count(catalog, live, SiteRuler.Player(ctx.actor),
        Suit.Hearth)
      if count == 0 then Vector(Note(id, _ =>
        Some(none(source, NoteArg.Player(ctx.actor)))))
      else if banks.stocked(live).isEmpty then
        Vector(Note(id, _ => Some(empty(source))))
      else banks.ask(live, ctx.actor, count,
          s"Town Meeting: take $count favor from any banks") ++ Vector(
        BuildOps((ready, pending) => banks.split(ready, pending, count).map(
          _.map { case (suit, n) => Gain.Favor(ctx.actor, suit, n) })),
        Note(id, NoteSupport.gainedNote(gained, source, ctx.actor,
          NoteUnit.Favor, NoteSupport.favor)))))

object TownMeeting:
  val id: PowerId = TownMeetingCard.power.id
  val decisionId: String = "cardplay.town-meeting.banks"
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "{Red} gained {n favor}." */
  val gained: NoteKey = NoteSupport.gainedKey(notes, "gained")
  /** "Every favor bank was empty." */
  val empty: NoteKey = notes.key("empty", Vector(
    NotePart.Text("Every favor bank was empty.")))
  /** "{Red} ruled no Hearth card." */
  val none: NoteKey = notes.key("none", Vector(NotePart.Arg(0),
    NotePart.Text(" ruled no Hearth card.")))

  def forCatalog(catalog: ExecutableCatalog): TownMeeting =
    new TownMeeting(catalog)
