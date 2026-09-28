package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** Family Heirloom (card 133), WHEN PLAYED: draw a relic. Take it or put it
  * on the bottom of the relic deck.
  *
  * A relic cannot wait in a temporary hand (`temporaryHands` holds world
  * cards), so the draw puts it facedown in the player's play area, where
  * only they can identify it, and "bottom" buries it again. The three steps
  * sit in a once-guarded `Repeat`: the guard runs only at pass boundaries,
  * so the draw emptying the relic deck cannot make the walker lose the
  * parked decision on resume.
  *
  * One line tells the draw and the choice, "{player} drew {relic} and kept
  * it." or "... and put it on the bottom of the relic deck.", in place of the
  * choice's "Chose" line and the burial's line.
  */
final case class FamilyHeirloom private (cardId: DenizenId)
    extends WhenPlayedPower:
  import FamilyHeirloom._
  def id: PowerId = FamilyHeirloom.id
  override def noteKeys: Vector[NoteKey] = Vector(kept, returned)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(Repeat(
      (ready, pending) => !asked(pending) &&
        ready.game.current.commonCards.relicDeck.nonEmpty,
      Sequence(Vector[Operation](
        BuildOps((ready, _) => draw(ready, actor)),
        Decide(decisionId, actor, DecisionQuery.ChooseOne(Vector(
          DecisionOption.Button(keep, "Take the relic"),
          DecisionOption.Button(bottom,
            "Put it on the bottom of the relic deck")),
          heading = Some("Family Heirloom: take the relic you drew, or put it " +
            "on the bottom of the relic deck"))),
        BuildOps((ready, pending) => settle(ready, actor, pending)),
        Note(id, note(_, actor), covers = true)))))

  /** The relic the choice was about: the last one the player held before
    * the step that settled it, as `settle` reads it. */
  private def note(states: NoteStates, actor: PlayerId): Option[PowerNote] =
    for
      (before, _) <- states.previous
      held <- PlayerFacts.player(before, actor).toOption
      relic <- held.relics.lastOption
      choice <- states.answered.collectFirst {
        case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(
            DecisionOptionRef.Button(button)), _) => button
      }
      key <- Vector(kept, returned).find(_.name == choice)
    yield key(PowerSourceRef.Card(cardId), NoteArg.Player(actor),
      NoteArg.Card(relic.id))

object FamilyHeirloom:
  val id: PowerId = PowerId("denizen.family-heirloom")
  val decisionId: String = "cardplay.family-heirloom.keep"
  val keep: DecisionOptionRef.Button = DecisionOptionRef.Button("keep")
  val bottom: DecisionOptionRef.Button = DecisionOptionRef.Button("bottom")
  /** A note key is named for the choice it tells. */
  val kept: NoteKey = NoteKey(keep.key, Vector(NotePart.Arg(0),
    NotePart.Text(" drew "), NotePart.Arg(1), NotePart.Text(" and kept it.")))
  val returned: NoteKey = NoteKey(bottom.key, Vector(NotePart.Arg(0),
    NotePart.Text(" drew "), NotePart.Arg(1),
    NotePart.Text(" and put it on the bottom of the relic deck.")))

  def forCatalog(catalog: ExecutableCatalog): Option[FamilyHeirloom] =
    WhenPlayedPower.cardOf(catalog, id).map(new FamilyHeirloom(_))

  private def asked(pending: PendingTree): Boolean =
    pending.answered.exists(_.decisionId == decisionId)

  private def draw(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    ready.game.current.commonCards.relicDeck.headOption.toRight(
      OathViolation.RecoverUnavailable("relic deck is empty")).map(relic =>
      Vector(Play(relic, PositionedLocation(Location.Deck(CardDeck.Relic),
        StackPosition.Top), Location.PlayArea(actor), Orientation.FaceDown)))

  private def settle(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    pending.answered.collectFirst:
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) => ref
    match
      case Some(`bottom`) =>
        for
          held <- PlayerFacts.player(ready, actor)
          drawn <- held.relics.lastOption.toRight(OathViolation.InvalidEventOrder(
            "no drawn relic to put back"))
        yield Vector(Bury(BuryableCard.Relic(drawn.id),
          PositionedLocation(Location.PlayArea(actor))))
      case Some(`keep`) => Right(Vector.empty)
      case _ => Left(OathViolation.InvalidEventOrder(
        "no Family Heirloom choice is recorded"))
