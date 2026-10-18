package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.InPlayCardResources
import oathdigital.gameplay.powers.SecretBurns
import oathdigital.model._

object AncientBindingCard extends Denizen(DenizenId("23"), "Ancient Binding", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.ancient-binding"),
    persistent = false, cost = Cost(secret = 1, secretBurnt = 1),
    text = "**ACTION:** Each player burns all [secret] from their board " +
      "and cards except their last [secret]. _(Keep only your " +
      "[secret] here.)_")
  val powers: Vector[PrintedPower] = Vector(power)

/** Ancient Binding (card 23), ACTION: place 1 secret on this card and burn
  * 1, then each player burns all secrets from their board and cards except
  * their last; the player keeps only the one on this card.
  *
  * Afterwards each player's board lists at most one secret
  * (`PlayerSecretSummary`). That count is a player's board, plus, for the
  * acting player, every secret on a card in play: during a turn only the
  * acting player places secrets on cards. So the player burns every secret
  * on their board and every secret on a card in play, at sites or in play
  * areas, except Ancient Binding's. Every other player burns all but one
  * secret from their board, keeping a faceup one if they have one, as Book
  * Burning does ([[SecretBurns]]). The Darkest Secret's secrets stay.
  * Everything burns in one batch, read live after the cost.
  *
  * One line counts every secret burnt, or says none was. The batch and its
  * line are chosen together in one `Branch`: an empty batch records no step,
  * and a line after it would read the cost's step instead.
  *
  * No generic line exists for a burn, so the line covers nothing.
  */
case object AncientBinding extends PaidAction(AncientBindingCard.power):
  private val notes = NoteSet(id)
  /** "Burned {n secrets}." */
  val burned: NoteKey = notes.key(NoteKey.Used, Vector(NotePart.Text("Burned "),
    NotePart.Arg(0), NotePart.Text(".")))
  /** "No secret was burned." */
  val nothing: NoteKey = notes.key("used.none", Vector(
    NotePart.Text("No secret was burned.")))
  override def noteKeys: Vector[NoteKey] = notes.declared

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match
    case DecisionOptionRef.Denizen(card) => Right(Sequence(Vector[Operation](
      Branch((live, _) =>
        if burns(live, player, card).isEmpty then
          Vector(Note(id, _ => PowerSourceRef.of(source).map(nothing(_))))
        else Vector(
          BuildOps((state, _) => Right(burns(state, player, card))),
          Note(id, burnedNote(_, source)))))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not a denizen source"))

  /** The player's board and every card in play, then every other board
    * down to one secret, in seat order. Each player burns their own
    * board. */
  private def burns(ready: ReadyGame, actor: PlayerId, binding: DenizenId)
      : Vector[CoreOperation] =
    ready.game.current.players.flatMap(held =>
      SecretBurns.keeping(held, if held.player == actor then 0 else 1,
        held.player)) ++
      onCards(ready, actor, binding)

  /** Every secret on a card in play but Ancient Binding: advisers and
    * relics in play areas, denizens, edifices and relics at sites. A held
    * card's secrets are its holder's burn; a card at a site has no holder,
    * so `actor` burns them. */
  private def onCards(ready: ReadyGame, actor: PlayerId,
      binding: DenizenId): Vector[CoreOperation] =
    val found = InPlayCardResources.discover(ready)
    val cards: Vector[(CardId, Int)] =
      found.denizens.map(card => (card.id: CardId) -> card.tokens.secrets) ++
        found.relics.map(relic => (relic.id: CardId) -> relic.tokens.secrets)
    cards.collect { case (card, secrets) if secrets > 0 && card != binding =>
      Burn.secrets(secrets, PositionedLocation(Location.OnCard(card)),
        Some(holderOf(ready, card).getOrElse(actor))) }

  /** The player holding `card` as an adviser or a relic, if any. */
  private def holderOf(ready: ReadyGame, card: CardId): Option[PlayerId] =
    ready.game.current.players.find(player =>
      player.advisers.exists(_.id == card) ||
        player.relics.exists(_.id == card)).map(_.player)

  /** The secrets that left boards and cards in the step before the note. */
  private def burnedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[LogNote] = for
    card <- PowerSourceRef.of(source)
    (before, after) <- states.previous
    count = held(before) - held(after)
    if count > 0
  yield burned(card, NoteArg.Amount(count, NoteUnit.Secret))

  /** Secrets on every board and on every card in play. */
  private def held(ready: ReadyGame): Int =
    ready.game.current.players.map(player =>
      player.board.faceUpSecrets + player.board.faceDownSecrets).sum +
      InPlayCardResources.discover(ready).secrets
