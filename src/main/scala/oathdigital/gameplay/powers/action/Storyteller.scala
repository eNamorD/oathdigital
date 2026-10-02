package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.model._

object StorytellerCard extends Denizen(DenizenId("52"), "Storyteller", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.storyteller"),
    persistent = false, cost = Cost(favor = 1),
    text = "**ACTION:** Place [secret] from the shared bank on the " +
      "Darkest Secret.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Storyteller (card 52), ACTION: place 1 favor on this card, then place 1
  * secret from the shared bank on the Darkest Secret.
  *
  * The secret moves onto the banner wherever it is, held by a player or by
  * nobody. The shared bank's secrets are unbounded, so the move always
  * happens. Its line reads what the banner gained.
  */
case object Storyteller extends PaidAction("denizen.storyteller",
    Cost(favor = 1)):
  val Placed: Int = 1
  /** "{Red} placed {1 secret} on the {Darkest Secret}." */
  val placed: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" placed "), NotePart.Arg(1), NotePart.Text(" on the "),
    NotePart.Arg(2), NotePart.Text(".")))
  override def noteKeys: Vector[NoteKey] = Vector(placed)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Move(Piece.Secrets(Placed), PositionedLocation(Location.SharedBank),
      PositionedLocation(Location.OnBanner(Banner.DarkestSecret))),
    Note(id, placedNote(_, player, source)))))

  /** What the Darkest Secret gained in the step before the note. */
  private def placedNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    (before, after) <- states.previous
    gained = BannerRules.resources(after.game.current, Banner.DarkestSecret) -
      BannerRules.resources(before.game.current, Banner.DarkestSecret)
    if gained > 0
  yield placed(card, NoteArg.Player(player),
    NoteArg.Amount(gained, NoteUnit.Secret),
    NoteArg.Banner(Banner.DarkestSecret))
