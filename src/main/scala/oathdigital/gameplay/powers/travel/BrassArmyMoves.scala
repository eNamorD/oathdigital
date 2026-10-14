package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powerresolver.{Contribution, ExecutedBatch,
  ExecutedTrigger}
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.gameplay.powers.campaign.BrassArmyCard
import oathdigital.model._

/** Brass Army (relic R25)'s restriction: "Whenever your pawn moves (travel
  * or place), bury this or flip a secret you have facedown." (catalog batch
  * 8 rulings, slice 6).
  *
  * A trigger on executed operations. After any walker batch that moves the
  * pawn of the player holding the relic faceup, on or off their turn, it
  * asks them right away, before the rest of the action: bury the relic, or
  * flip one faceup secret facedown. With no faceup secret the relic is
  * buried and nothing is asked. A placement that leaves the pawn where it
  * stands moves nothing, so it asks nothing.
  *
  * Its lines name the holder when it is not their turn: "{Red} buried it
  * after a pawn move." or "{Red} flipped a secret facedown.". The bury's
  * line covers the bury's own detail line.
  */
case object BrassArmyMoves extends ExecutedTrigger:
  val cardId: RelicId = BrassArmyCard.id
  val id: PowerId = BrassArmyCard.restriction.id
  val decisionId: String = "power.brass-army.moved"
  val bury: DecisionOptionRef.Button =
    DecisionOptionRef.Button("brass-army.bury")
  val flip: DecisionOptionRef.Button =
    DecisionOptionRef.Button("brass-army.flip")

  private val notes = NoteSet(id)
  /** "Buried after a pawn move." */
  val buried: NoteKey = notes.key("buried",
    Vector(NotePart.Text("Buried after a pawn move.")))
  /** "{Red} buried it after a pawn move." */
  val buriedBy: NoteKey = notes.key("buried-by", Vector(NotePart.Arg(0),
    NotePart.Text(" buried it after a pawn move.")))
  /** "Flipped a secret facedown." */
  val flipped: NoteKey = notes.key("flipped",
    Vector(NotePart.Text("Flipped a secret facedown.")))
  /** "{Red} flipped a secret facedown." */
  val flippedBy: NoteKey = notes.key("flipped-by", Vector(NotePart.Arg(0),
    NotePart.Text(" flipped a secret facedown.")))

  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
  override def noteKeys: Vector[NoteKey] = notes.declared
  override def narratedDecisions: Set[String] = Set(decisionId)

  def onExecuted(batch: ExecutedBatch): Option[Operation] =
    holderOf(batch.now).filter(moved(batch.executed, _)).map { holder =>
      val faceUp = batch.now.game.current.players
        .find(_.player == holder).fold(0)(_.board.faceUpSecrets)
      if faceUp == 0 then Sequence(buryIt(batch.actor, holder))
      else Sequence(question(holder), Branch((_, pending) =>
        if PowerAnswers.one(pending, decisionId).contains(flip) then
          flipOne(batch.actor, holder)
        else buryIt(batch.actor, holder)))
    }

  private def question(holder: PlayerId): Decide = Decide(decisionId,
    holder, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(bury, "Bury Brass Army"),
      DecisionOption.Button(flip, "Flip a secret facedown")),
      heading = Some("Brass Army: your pawn moved")))

  private def buryIt(actor: PlayerId, holder: PlayerId): Vector[Operation] =
    Vector(
      BuildOps((_, _) => Right(Vector[CoreOperation](Bury(
        BuryableCard.Relic(cardId),
        PositionedLocation(Location.PlayArea(holder)))))),
      Note(id, _ => Some(line(buried, buriedBy, actor, holder)),
        covers = true))

  private def flipOne(actor: PlayerId, holder: PlayerId)
      : Vector[Operation] = Vector(
    BuildOps((_, _) => Right(Vector[CoreOperation](FlipSecrets(holder, 1,
      SecretSide.FaceUp, SecretSide.FaceDown)))),
    Note(id, _ => Some(line(flipped, flippedBy, actor, holder))))

  /** `own` on the holder's turn, else `named`, naming them. */
  private def line(own: NoteKey, named: NoteKey, actor: PlayerId,
      holder: PlayerId): PowerNote =
    val card = PowerSourceRef.Card(cardId)
    if holder == actor then own(card) else named(card, NoteArg.Player(holder))

  /** The player holding the relic faceup, if any. */
  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.relics.exists {
      case RelicState(`cardId`, Orientation.FaceUp, _) => true
      case _ => false
    }).map(_.player)

  private def moved(executed: Vector[CoreOperation], holder: PlayerId)
      : Boolean = executed.exists {
    case Move(Piece.Pawn(player), _, _, _) => player == holder
    case _ => false
  }
