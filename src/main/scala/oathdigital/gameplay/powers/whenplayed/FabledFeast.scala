package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers, RuledCards}
import oathdigital.model._

/** Fabled Feast (card 136), WHEN PLAYED: take X favor equal to the number of
  * Hearth cards you rule (including Fabled Feast) from any one favor bank.
  *
  * X is read live with `RuledCards.of`, so Fabled Feast counts itself as a
  * faceup adviser or at a site the actor rules, and not at a site the actor
  * does not rule. The actor chooses one bank that holds favor; with only one
  * it is taken unasked. The gain is best effort, so a short bank gives what
  * it holds. Its line names the bank, so the choice is narrated, and it
  * covers the Gain line.
  */
final case class FabledFeast private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends WhenPlayedPower:
  import FabledFeast._
  def id: PowerId = FabledFeast.id

  override def noteKeys: Vector[NoteKey] = Vector(took, empty, none)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    val source = PowerSourceRef.Card(cardId)
    Vector(Branch((live, _) =>
      val count = RuledCards.of(catalog, live, SiteRuler.Player(actor),
        Suit.Hearth).size
      val banks = stocked(live)
      if count == 0 then Vector(Note(id, _ =>
        Some(none(source, NoteArg.Player(actor)))))
      else if banks.isEmpty then Vector(Note(id, _ => Some(empty(source))))
      else ask(actor, banks, count) ++ Vector(
        BuildOps((ready, pending) => take(ready, actor, pending, count)),
        Note(id, NoteSupport.gainedFromNote(took, source, actor),
          covers = true))))

object FabledFeast:
  val id: PowerId = PowerId("denizen.fabled-feast")
  val decisionId: String = "cardplay.fabled-feast.bank"
  /** "{Red} took {n favor} from {the Hearth bank}." */
  val took: NoteKey = NoteKey("took", Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1), NotePart.Text(" from "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "Every favor bank was empty." */
  val empty: NoteKey = NoteKey("empty", Vector(
    NotePart.Text("Every favor bank was empty.")))
  /** "{Red} ruled no Hearth card." */
  val none: NoteKey = NoteKey("none", Vector(NotePart.Arg(0),
    NotePart.Text(" ruled no Hearth card.")))

  def forCatalog(catalog: ExecutableCatalog): Option[FabledFeast] =
    WhenPlayedPower.cardOf(catalog, id).map(new FabledFeast(_, catalog))

  /** The banks that hold favor, in suit order. */
  private def stocked(ready: ReadyGame): Vector[Suit] =
    Suit.all.filter(suit => ready.banks.favor.getOrElse(suit, 0) > 0)

  /** The question, when two or more banks hold favor. */
  private def ask(actor: PlayerId, banks: Vector[Suit], count: Int)
      : Vector[Operation] =
    if banks.size < 2 then Vector.empty
    else Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      banks.map(suit => DecisionOption.FavorBank(
        DecisionOptionRef.FavorBank(suit))),
      heading = Some(s"Fabled Feast: take $count favor from one bank"))))

  /** The gain from the only stocked bank, or from the bank answered. */
  private def take(ready: ReadyGame, actor: PlayerId, pending: PendingTree,
      count: Int): Either[OathViolation, Vector[CoreOperation]] =
    val banks = stocked(ready)
    val bank = banks match
      case Vector(only) => Right(only)
      case _ => PowerAnswers.one(pending, decisionId)
        .toRight(PowerAnswers.missing(decisionId)).flatMap {
          case DecisionOptionRef.FavorBank(suit) if banks.contains(suit) =>
            Right(suit)
          case other => Left(OathViolation.InvalidEventOrder(
            s"${other.wireId} is not a bank that holds favor"))
        }
    bank.map(suit => Vector(Gain.Favor(actor, suit, count)))
