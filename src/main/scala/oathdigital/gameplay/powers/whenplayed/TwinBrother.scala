package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.operations.OperationRestrictions
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Twin Brother (card 170, adviser-only), WHEN PLAYED: you may swap this
  * card with a faceup nomad adviser of another player.
  *
  * It fires only when played faceup, and card play places the card before
  * the hook, so it is the actor's faceup adviser when this runs. The
  * candidates are every other player's faceup nomad denizen advisers that
  * are not locked, read live. With none, nothing is asked. Otherwise the
  * actor picks one or keeps Twin Brother. The `Swap` exchanges the two cards
  * between the play areas. Each keeps its orientation and the tokens on it,
  * so both stay faceup and carry their favor and secrets.
  *
  * The effect is one `Branch`, so the node count this power adds to the
  * card-played window never depends on live state. Nothing between the
  * question and the swap changes the candidates, so the `Branch` selects the
  * same children on resume.
  *
  * Its locked filter goes when the restriction search hides refused options
  * (global operation restrictions, slice 2).
  */
final case class TwinBrother private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends WhenPlayedPower:
  import TwinBrother._
  def id: PowerId = TwinBrother.id

  override def noteKeys: Vector[NoteKey] = Vector(swapped)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(Branch((live, _) =>
      val found = candidates(live, actor)
      if found.isEmpty then Vector.empty
      else Vector(
        Decide(decisionId, actor, DecisionQuery.ChooseOne(
          found.map(c => DecisionOption.Denizen(DecisionOptionRef.Denizen(c.card))) :+
            DecisionOption.Button(keep, "Keep Twin Brother"),
          heading = Some("Twin Brother: swap it for another player's faceup " +
            "nomad adviser?"))),
        BuildOps((ready, pending) => swap(ready, actor, pending)),
        Note(id, swappedNote(actor)))))

  private def candidates(ready: ReadyGame, actor: PlayerId): Vector[Candidate] =
    for
      held <- ready.game.current.players if held.player != actor
      card <- held.advisers.collect {
        case DenizenState(id, Orientation.FaceUp, _) => id }
      definition <- catalog.denizen(card).toVector
      if definition.suit == Suit.Nomad &&
        !OperationRestrictions.isLocked(catalog, ready, card)
    yield Candidate(held.player, card)

  private def swap(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    pending.answered.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
    } match
      case Some(`keep`) => Right(Vector.empty)
      case Some(DecisionOptionRef.Denizen(target)) =>
        candidates(ready, actor).find(_.card == target).toRight(OathViolation
          .InvalidEventOrder(s"${target.value} is not a nomad adviser Twin " +
            "Brother can swap with")).map(chosen => Vector(Swap(cardId,
          PositionedLocation(Location.PlayArea(actor)), chosen.card,
          PositionedLocation(Location.PlayArea(chosen.owner)))))
      case Some(other) => Left(OathViolation.InvalidEventOrder(
        s"${other.wireId} is not a Twin Brother choice"))
      case None => Left(OathViolation.InvalidEventOrder(
        "no Twin Brother choice is recorded"))

  /** Written only when the actor now holds the chosen card. */
  private def swappedNote(actor: PlayerId)(states: NoteStates)
      : Option[PowerNote] = for
    case DecisionOptionRef.Denizen(target) <-
      NoteSupport.answer(states, decisionId)
    (before, after) <- states.previous
    owner <- before.game.current.players
      .find(_.advisers.exists(_.id == target)).map(_.player)
    if after.game.current.players.find(_.player == actor)
      .exists(_.advisers.exists(_.id == target))
  yield swapped(PowerSourceRef.Card(cardId), NoteArg.Player(actor),
    NoteArg.Player(owner), NoteArg.Card(target))

object TwinBrother:
  val id: PowerId = PowerId("denizen.twin-brother")
  val decisionId: String = "cardplay.twin-brother.swap"
  val keep: DecisionOptionRef.Button = DecisionOptionRef.Button("keep")
  /** "{Red} swapped it for {Blue}'s {card}." */
  val swapped: NoteKey = NoteKey("swapped", Vector(NotePart.Arg(0),
    NotePart.Text(" swapped it for "), NotePart.Arg(1), NotePart.Text("'s "),
    NotePart.Arg(2), NotePart.Text(".")))

  private final case class Candidate(owner: PlayerId, card: DenizenId)

  def forCatalog(catalog: ExecutableCatalog): Option[TwinBrother] =
    WhenPlayedPower.cardOf(catalog, id).map(new TwinBrother(_, catalog))
