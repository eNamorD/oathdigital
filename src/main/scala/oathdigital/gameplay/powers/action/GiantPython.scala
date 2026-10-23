package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower,
  SiteOnly}
import oathdigital.gameplay.{NoteSupport, SiteRulers}
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.gameplay.powers.banner.CardPowerBanners
import oathdigital.model._

object GiantPythonCard extends Denizen(DenizenId("186"), "Giant Python", Suit.Beast) with SiteOnly:
  val action = PrintedPower(PowerId("denizen.giant-python.action"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** If you hold the Darkest Secret, move Giant " +
      "Python to another site, and you may discard a card there " +
      "first.")
  val restriction = PrintedPower(PowerId("denizen.giant-python.restriction"),
    persistent = true, cost = Cost.free,
    text = "Other cards at this site **cannot** be used for " +
      "non-persistent powers or placing [favor] / [secret].")
  val powers: Vector[PrintedPower] = Vector(action, restriction)

/** Giant Python's ACTION (card 186, site-only): "If you hold the Darkest
  * Secret, move Giant Python to another site, and you may discard a card
  * there first."
  *
  * It is usable when its user holds the Darkest Secret, as Yew Staff lets
  * them act ([[CardPowerBanners.holdsDarkestSecret]]), and some slot may
  * be chosen. After the cost, it asks one question over every other
  * site's slots ([[SiteSlotMoves]]): the first empty slot where there is
  * room, and each card there to replace. At a full site only a Replace is
  * offered. Giant Python moves carrying its secrets; its restriction reads
  * where it stands, so it moves too (`GiantPythonRestriction`).
  *
  * Its line covers the generic discard and move lines: "Moved {card} to
  * {site}." or "Replaced {card} at {site}."
  */
final case class GiantPython private (catalog: ExecutableCatalog)
    extends PaidAction(GiantPythonCard.action):
  import GiantPython._

  private val slots = new SiteSlotMoves(catalog, GiantPythonCard.id,
    empty = true)
  override def noteKeys: Vector[NoteKey] = GiantPython.noteKeys
  override def narratedDecisions: Set[String] = Set(decisionId)

  override def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean =
    CardPowerBanners.holdsDarkestSecret(ready, player) &&
      slots.options(ready, player).exists(_.available)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => Vector(Decide(decisionId, player,
      DecisionQuery.ChooseOne(slots.options(live, player),
        heading = Some(heading)), passWhenEmpty = true))),
    BuildOps((live, pending) => move(live, player, pending),
      required = true),
    Note(this.id, movedNote(_, source), covers = true))))

  /** No answer means Continue was pressed, and Giant Python stays. */
  private def move(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => slots.chosen(ready, ref).toRight(
          OathViolation.InvalidEventOrder(
            s"${ref.wireId} is not a slot Giant Python can move to"))
        .flatMap(slots.operations(ready, actor, _))

  /** The chosen slot, read before the step, once Giant Python stands at
    * its site. */
  private def movedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[LogNote] =
    for
      card <- PowerSourceRef.of(source)
      ref <- NoteSupport.answer(states, decisionId)
      (before, _) <- states.previous
      chosen <- slots.chosen(before, ref)
      if SiteRulers.siteOf(states.now, GiantPythonCard.id)
        .contains(chosen.site)
    yield chosen.replaced.fold(moved(card,
      NoteArg.Card(GiantPythonCard.id), NoteArg.Site(chosen.site)))(
      gone => replaced(card, NoteArg.Card(gone._1.id),
        NoteArg.Site(chosen.site)))

object GiantPython:
  val id: PowerId = GiantPythonCard.action.id
  val decisionId: String = "power.giant-python.slot"
  val heading: String = "Giant Python: move to which slot?"
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Moved {card} to {site}." */
  val moved: NoteKey = notes.key(NoteKey.Used, Vector(NotePart.Text("Moved "),
    NotePart.Arg(0), NotePart.Text(" to "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "Replaced {card} at {site}." */
  val replaced: NoteKey = notes.key("used.replaced", Vector(
    NotePart.Text("Replaced "), NotePart.Arg(0), NotePart.Text(" at "),
    NotePart.Arg(1), NotePart.Text(".")))

  def forCatalog(catalog: ExecutableCatalog): GiantPython =
    new GiantPython(catalog)
