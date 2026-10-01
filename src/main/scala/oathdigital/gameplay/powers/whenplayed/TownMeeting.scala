package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{NoteSupport, RuledCards}
import oathdigital.gameplay.powers.action.FavorSplit
import oathdigital.model._

/** Town Meeting (card 236), WHEN PLAYED: gain favor from any favor bank for
  * each Hearth card you rule. This includes your advisers, even Town
  * Meeting.
  *
  * X is counted as Fabled Feast counts it. The favor comes from any banks,
  * split as Alchemist splits ([[FavorSplit]]): the actor is asked only when
  * two or more banks hold favor and they hold more than X in all. The
  * rulings leave open whether the text means one bank for all; the split is
  * used until that is settled. Its line tells the whole gain; each bank's
  * Gain line stays.
  */
final case class TownMeeting private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends WhenPlayedPower:
  import TownMeeting._
  def id: PowerId = TownMeeting.id

  override def noteKeys: Vector[NoteKey] = Vector(gained, empty, none)

  private val banks = new FavorSplit(decisionId, Suit.all, "Take favor")

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    val source = PowerSourceRef.Card(cardId)
    Vector(Branch((live, _) =>
      val count = RuledCards.of(catalog, live, SiteRuler.Player(actor),
        Suit.Hearth).size
      if count == 0 then Vector(Note(id, _ =>
        Some(none(source, NoteArg.Player(actor)))))
      else if banks.stocked(live).isEmpty then
        Vector(Note(id, _ => Some(empty(source))))
      else banks.ask(live, actor, count,
          s"Town Meeting: take $count favor from any banks") ++ Vector(
        BuildOps((ready, pending) => banks.split(ready, pending, count).map(
          _.map { case (suit, n) => Gain.Favor(actor, suit, n) })),
        Note(id, NoteSupport.gainedNote(gained, source, actor,
          NoteUnit.Favor, NoteSupport.favor)))))

object TownMeeting:
  val id: PowerId = PowerId("denizen.town-meeting")
  val decisionId: String = "cardplay.town-meeting.banks"
  /** "{Red} gained {n favor}." */
  val gained: NoteKey = NoteSupport.gainedKey("gained")
  /** "Every favor bank was empty." */
  val empty: NoteKey = NoteKey("empty", Vector(
    NotePart.Text("Every favor bank was empty.")))
  /** "{Red} ruled no Hearth card." */
  val none: NoteKey = NoteKey("none", Vector(NotePart.Arg(0),
    NotePart.Text(" ruled no Hearth card.")))

  def forCatalog(catalog: ExecutableCatalog): Option[TownMeeting] =
    WhenPlayedPower.cardOf(catalog, id).map(new TownMeeting(_, catalog))
