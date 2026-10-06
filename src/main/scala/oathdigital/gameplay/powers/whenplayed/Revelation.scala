package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.phases.rest.FinishRestProcedure
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object RevelationCard extends Denizen(DenizenId("63"), "Revelation", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.revelation"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED:** each player, following turn order, may " +
      "burn any number of [favor] to gain an equal number of " +
      "[secret].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Revelation (card 63), WHEN PLAYED: each player, following turn order, may
  * burn any number of favor to gain an equal number of secrets.
  *
  * Turn order starts with the first player (`FinishRestProcedure.turnOrder`),
  * wherever the actor sits. Each player who holds favor is asked, as the
  * owner of their own question, for an amount from 0 to their favor; a
  * player with none is not asked. The burn and the gain are one step, so
  * each player answers after the players before them have burned. Every
  * player keeps the same place in the tree whether or not they are asked,
  * so a later player's question resumes where it parked.
  *
  * One line per player who burned covers that step's Gain line, and one
  * names each player who was asked and burned none. With nobody asked, one
  * line says nobody had favor.
  */
case object Revelation extends WhenPlayedPower:
  val cardId: DenizenId = RevelationCard.id
  val id: PowerId = RevelationCard.power.id
  val decisionId: String = "cardplay.revelation.burn"
  /** "{Blue} burned {n favor} and gained {n secrets}." */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Arg(0),
    NotePart.Text(" burned "), NotePart.Arg(1), NotePart.Text(" and gained "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "{Blue} did not burn any favor." */
  val declined: NoteKey = NoteKey("declined", Vector(NotePart.Arg(0),
    NotePart.Text(" did not burn any favor.")))
  /** "Nobody had favor to burn." */
  val none: NoteKey = NoteKey("none", Vector(
    NotePart.Text("Nobody had favor to burn.")))

  override def noteKeys: Vector[NoteKey] = Vector(burned, declined, none)

  private def cardSource: PowerSourceRef = PowerSourceRef.Card(cardId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    FinishRestProcedure.turnOrder(ctx.state).flatMap(turn) :+
      Note(id, noneNote)

  /** One player's place: their question while they hold favor, then what
    * their answer does. */
  private def turn(player: PlayerId): Vector[Operation] = Vector(
    Branch((live, _) => ask(live, player)),
    Branch((_, pending) => settle(pending.answered, player)))

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] =
    NoteSupport.board(ready, player).map(_.favor).filter(_ > 0).toVector
      .map(favor => Decide(decisionId, player, DecisionQuery.ChooseAmount(0,
        favor, Some("Revelation: burn any favor to gain as many secrets"),
        "Burn favor")))

  /** The burn and gain `player` answered, with its line; the line that they
    * burned none; or nothing when they were not asked. */
  private def settle(answered: Vector[Answered], player: PlayerId)
      : Vector[Operation] = burnt(answered, player) match
    case Some(0) => Vector(Note(id, _ =>
      Some(declined(cardSource, NoteArg.Player(player)))))
    case Some(amount) => Vector(
      BuildOps((_, _) => Right(Vector(
        Burn.favor(amount, PositionedLocation(Location.PlayArea(player))),
        Gain.Secrets(player, amount)))),
      Note(id, burnedNote(_, player), covers = true))
    case None => Vector.empty

  /** The amount `player` answered, if they were asked. */
  private def burnt(answered: Vector[Answered], player: PlayerId)
      : Option[Int] = answered.collectFirst {
    case Answered(`decisionId`, DecisionAnswer.ChooseAmountAnswer(amount),
        `player`) => amount }

  /** The favor `player` lost and the secrets they gained in the step
    * before. */
  private def burnedNote(states: NoteStates, player: PlayerId)
      : Option[PowerNote] = for
    step <- states.previous
    lost = -NoteSupport.favor(step, player)
    if lost > 0
  yield burned(cardSource, NoteArg.Player(player),
    NoteArg.Amount(lost, NoteUnit.Favor),
    NoteArg.Amount(NoteSupport.secrets(step, player), NoteUnit.Secret))

  /** "Nobody had favor to burn." when nobody was asked. */
  private def noneNote(states: NoteStates): Option[PowerNote] =
    Option.when(!states.answered.exists(_.decisionId == decisionId))(
      none(cardSource))
