package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object RovingTerrorCard extends Denizen(DenizenId("46"), "Roving Terror", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.roving-terror"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Discard a denizen card at any other site and " +
      "move this card there.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Roving Terror (card 46, site-only), ACTION: place 1 secret on this card,
  * then discard a denizen card at any other site and move this card there.
  *
  * After the cost it asks [[SiteSlotMoves]]' question with Replace options
  * only, read live: each faceup denizen and ruined edifice at every other
  * site in play, in map then slot order, answered by its site slot. An
  * intact edifice is locked, so it is never offered. The chosen card gets
  * the standard discard: favor to its suit's bank, secrets to the player
  * facedown. Roving Terror then moves to that site carrying its secret, and
  * takes the site's last slot, since a row has no gaps. The discard and the
  * move are one required batch, so a card the Hall of Ministers protects is
  * shown with its reason, or askable of the Hall's ruler (consent slice 3).
  * With no slot that may be chosen the question shows Continue beside any
  * askable one. With no candidate the cost stays paid and one line says so.
  *
  * The line covers the generic discard line.
  */
final case class RovingTerror private (catalog: ExecutableCatalog)
    extends PaidAction(RovingTerrorCard.power):
  import RovingTerror._

  private val slots = new SiteSlotMoves(catalog, RovingTerrorCard.id,
    empty = false)
  override def noteKeys: Vector[NoteKey] = RovingTerror.noteKeys
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => replace(live, player, pending),
      required = true),
    Note(this.id, replacedNote(_, source), covers = true))))

  /** The question over the slots, each judged for the actor's discard.
    * With none that may be chosen it is the Continue screen, where an
    * askable slot still asks (catalog batch 8, slice 10, part 2b). */
  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      slots.options(ready, actor), heading = Some(heading)),
      passWhenEmpty = true))

  /** No answer means no card could be replaced. */
  private def replace(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => slots.chosen(ready, ref).toRight(
          OathViolation.InvalidEventOrder(
            s"${ref.wireId} is not a slot Roving Terror can move to"))
        .flatMap(slots.operations(ready, actor, _))

  /** The chosen slot, read before the step, once Roving Terror stands at its
    * site. */
  private def replacedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] =
    PowerSourceRef.of(source).map(card => (for
      ref <- NoteSupport.answer(states, decisionId)
      (before, _) <- states.previous
      move <- slots.chosen(before, ref)
      replacedCard <- move.replaced
      if SiteRulers.siteOf(states.now, RovingTerrorCard.id)
        .contains(move.site)
    yield replaced(card, NoteArg.Card(replacedCard._1.id),
      NoteArg.Site(move.site))).getOrElse(nothing(card)))

object RovingTerror:
  val id: PowerId = RovingTerrorCard.power.id
  val decisionId: String = "power.roving-terror.card"
  val heading: String = "Roving Terror: move to which slot?"
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Replaced {card} at {site}." */
  val replaced: NoteKey = notes.key(NoteKey.Used, Vector(
    NotePart.Text("Replaced "), NotePart.Arg(0), NotePart.Text(" at "),
    NotePart.Arg(1), NotePart.Text(".")))
  /** "No card could be replaced." */
  val nothing: NoteKey = notes.key("used.none", Vector(
    NotePart.Text("No card could be replaced.")))

  def forCatalog(catalog: ExecutableCatalog): RovingTerror =
    new RovingTerror(catalog)
