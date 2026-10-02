package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

object VowOfObedienceCard extends Denizen(DenizenId("121"), "Vow of Obedience", Suit.Order) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-obedience"),
    persistent = false, cost = Cost.free,
    text = "You **cannot** play Visions faceup. **REST:** Take [favor] " +
      "from any one favor bank.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Vow of Obedience (card 121): "You cannot play Visions faceup. REST: Take
  * [favor] from any one favor bank."
  *
  * The rule is a `Restriction` on the card-play hook. The walker's
  * restriction look-ahead then leaves a faceup copy's holder no faceup
  * placement for a Vision, the Conspiracy included, in Search and in
  * facedown-adviser play alike. The REST power is a [[PhasePower]], as Silver
  * Tongue's is. The catalog marks the power `persistent: false` because of
  * its REST, so the rule keeps the default automatic resolution rather than
  * reading one from the catalog.
  * When the look-ahead hides a faceup placement because of it, it writes
  * "{Red} cannot play a Vision faceup."
  */
case object VowOfObedience extends PhasePower with ContributingPower:
  val cardId: DenizenId = VowOfObedienceCard.id
  val id: PowerId = VowOfObedienceCard.power.id

  def choiceDecisionId(ready: ReadyGame, player: PlayerId): String =
    s"vow-of-obedience-${ready.game.current.tracks.round}-${player.value}"

  def timing: PowerTiming = PowerTiming.Rest
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] =
    Vector(NoteSupport.took, VisionPlay.noFaceup)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup -> Vector(Restriction((ctx, _) =>
      VisionPlay.pending(ctx).filter(_ => holds(ctx.state, ctx.activePlayer))
        .map(_ => VisionPlay.forbidden("Vow of Obedience")),
      VisionPlay.note(cardId))))

  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean =
    FavorBankChoice.stocked(ready).nonEmpty

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    val choice = VowOfObedience.choiceDecisionId(ready, player)
    Right(Sequence(Vector(
      Branch((state, _) => FavorBankChoice.take(state, player, 1, choice,
        "Vow of Obedience: take a favor from a bank")),
      Note(id, NoteSupport.tookNote(source, player), covers = true))))

  /** Whether `player` holds this card as a faceup adviser. */
  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player).exists(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    })
