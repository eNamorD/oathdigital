package oathdigital.model

/** Core operations occurring in a game of Oath.
  *
  * These values describe semantic game instructions. They do not
  * validate restrictions, choices, and consent.
  *
  * Oath's own vocabulary, sealed so that the serializer and the restriction
  * screener can match it exhaustively. The walker's generic control
  * vocabulary is not here: it lives under the unsealed [[Operation]] in
  * `Operation.scala`, and a control node is therefore not a
  * `Vector[CoreOperation]` member and never a recorded delta.
  *
  * Composites expose their constituent operations through `children`; read
  * the leaves with [[Operation.flatten]]. `PrimitiveOperation` is Oath's
  * sealed half of [[LeafOperation]].
  */
sealed trait CoreOperation extends Operation:
  def simultaneous: Boolean = false

sealed trait PrimitiveOperation extends CoreOperation, LeafOperation

sealed trait Piece extends Product with Serializable
object Piece:
  final case class Card(id: CardId) extends Piece
  final case class Banner(banner: oathdigital.model.Banner) extends Piece
  final case class Pawn(player: PlayerId) extends Piece

  sealed trait Counted extends Piece { def amount: Int }
  final case class Favor(amount: Int) extends Counted:
    require(amount > 0, "favor amount must be positive")
  final case class Secrets(amount: Int) extends Counted:
    require(amount > 0, "secret amount must be positive")
  final case class Warbands(kind: ForceKind, amount: Int) extends Counted:
    require(amount > 0, "warband amount must be positive")

sealed trait Location extends Product with Serializable
object Location:
  final case class Hand(player: PlayerId) extends Location
  /** Player board and everything held around it, including Advisers. */
  final case class PlayArea(player: PlayerId) extends Location
  final case class Site(site: SiteId) extends Location
  final case class OnCard(card: CardId) extends Location
  final case class OnBanner(banner: Banner) extends Location
  final case class FavorBank(suit: Suit) extends Location
  final case class WarbandBank(kind: ForceKind) extends Location
  final case class Deck(deck: CardDeck) extends Location
  final case class RegionalDiscard(region: Region) extends Location
  case object SharedBank extends Location
  case object SetAsideRelics extends Location
  case object Reliquary extends Location
  case object Atlas extends Location
  case object Dispossessed extends Location

  private[model] def ownedBy(location: Location,
      player: PlayerId): Boolean = location match
    case Hand(owner) => owner == player
    case PlayArea(owner) => owner == player
    case _ => false

enum SecretSide { case FaceUp, FaceDown }

enum StackPosition { case Unspecified, Top, Bottom }

/** A location, and where in it a piece is moved from or to. `side`, for
  * secrets on a player's board, is the side a move takes them from; with
  * none, the move takes either side as its destination allows. Moved
  * secrets keep their side (catalog batch 8, slice 5). */
final case class PositionedLocation(
    location: Location,
    position: StackPosition = StackPosition.Unspecified,
    side: Option[SecretSide] = None):
  require(side.isEmpty || location.isInstanceOf[Location.PlayArea],
    "only secrets on a board have a side to move from")

/** Changes a piece's location or role within one play area. `by` is the
  * player who moves it, where a rule names one (catalog batch 8, slice 11):
  * a restriction that holds against some players only reads it off the
  * operation tree. `None` also means nobody moves it, as for Empty
  * Grounds' discards, so no such restriction holds.
  */
final case class Move(
    piece: Piece,
    from: PositionedLocation,
    to: PositionedLocation,
    resultingOrientation: Option[Orientation] = None,
    by: Option[PlayerId] = None)
    extends PrimitiveOperation:
  require(from != to || (resultingOrientation.isDefined &&
      piece.isInstanceOf[Piece.Card] &&
      from.location == to.location &&
      from.location.isInstanceOf[Location.PlayArea]),
    "a move must change location, stack position, or card role within a play area")
  require(resultingOrientation.isEmpty || piece.isInstanceOf[Piece.Card],
    "only cards have an orientation")
  require(from.side.isEmpty || piece.isInstanceOf[Piece.Secrets],
    "only secrets have a side")
  require(to.side.isEmpty, "a move names the side it takes, not lands on")

/** Looks at a card's front without changing its orientation or location. */
final case class Peek(viewer: PlayerId, card: CardId,
    at: Location) extends PrimitiveOperation

/** Sets a card's physical orientation. */
final case class Flip(card: CardId, at: Location,
    orientation: Orientation) extends PrimitiveOperation

/** Flips secrets already in a player's play area. Moving secrets preserves
  * their current orientation unless followed by this operation.
  */
final case class FlipSecrets(player: PlayerId, amount: Int,
    from: SecretSide, to: SecretSide) extends PrimitiveOperation:
  require(amount > 0, "secret amount must be positive")
  require(from != to, "secret flip must change side")

/** Gains Supply up to SupplyTrack.Maximum. */
final case class GainSupply(player: PlayerId, amount: Int)
    extends PrimitiveOperation:
  require(amount > 0, "supply gain must be positive")

/** Spends Supply. Costs are required unless a power explicitly permits less. */
final case class SpendSupply(player: PlayerId, amount: Int,
    override val required: Boolean = true) extends PrimitiveOperation:
  require(amount > 0, "supply spend must be positive")

/** Moves favor or secrets to the shared bank. `by` is the player who burns
  * (catalog batch 8, "A burn's actor"): the actor of the procedure that
  * burns, each owner when an effect makes each player burn, the defeated
  * defender for a Raid's half-favor burn, and `None` when no player burns,
  * as in a setup rule. A cost's burnt portions are its payer's, so
  * `PayCost` holds no `Burn`.
  */
sealed trait Burn extends CoreOperation:
  def resource: Piece.Counted
  def from: PositionedLocation
  def by: Option[PlayerId]

  final lazy val move: Move = Move(resource, from,
    PositionedLocation(Location.SharedBank))
  final override lazy val children: Vector[Operation] = Vector(move)
object Burn:
  def favor(amount: Int, from: PositionedLocation,
      by: Option[PlayerId]): Burn =
    ResourceBurn(Piece.Favor(amount), from, by)

  def secrets(amount: Int, from: PositionedLocation,
      by: Option[PlayerId]): Burn =
    ResourceBurn(Piece.Secrets(amount), from, by)

  private final case class ResourceBurn(resource: Piece.Counted,
      from: PositionedLocation, by: Option[PlayerId]) extends Burn

sealed trait BuryableCard extends Product with Serializable:
  def id: CardId
  def deck: CardDeck
object BuryableCard:
  final case class Denizen(id: DenizenId) extends BuryableCard:
    override val deck: CardDeck = CardDeck.World
  final case class Relic(id: RelicId) extends BuryableCard:
    override val deck: CardDeck = CardDeck.Relic
  final case class Edifice(id: EdificeId) extends BuryableCard:
    override val deck: CardDeck = CardDeck.Edifice
  final case class Vision(id: VisionId) extends BuryableCard:
    override val deck: CardDeck = CardDeck.World

/** Adds a denizen, relic, or edifice to the bottom of its matching deck.
  * Bury explicitly ignores the locked restriction.
  */
final case class Bury(card: BuryableCard, from: PositionedLocation,
    override val required: Boolean = false)
    extends PrimitiveOperation:
  val to: PositionedLocation = PositionedLocation(
    Location.Deck(card.deck), StackPosition.Bottom)

object Bury:
  /** A bury with the returns a discard makes: favor to the suit's bank and
    * secrets to the acting player, facedown. `Bury` alone returns nothing.
    * The returns come first because a card must carry no resources when it
    * enters a deck.
    * `suit` is a fact about the card the caller supplies (the pipeline has no
    * catalog). It may be `None` only when `favor` is zero, as for a relic.
    */
  def standard(card: BuryableCard, from: PositionedLocation,
      suit: Option[Suit], favor: Int, secrets: Int,
      actingPlayer: PlayerId): Vector[CoreOperation] =
    require(favor == 0 || suit.isDefined, "buried favor needs its suit bank")
    Discard.returns(card.id, suit, favor, secrets, actingPlayer) :+
      Bury(card, from)

sealed trait Discard extends CoreOperation
object Discard:
  /** Discards a denizen facedown to the specified regional pile and returns
    * all resources on it. `actingPlayer` takes its secrets and discards it,
    * unless the discard is `actorless`: an effect no player performs, as
    * Empty Grounds'. Its secrets still go to `actingPlayer`, the active
    * player, since secrets reach a card only on a player's turn.
    */
  final case class Denizen(card: DenizenId, from: PositionedLocation,
      to: Region, suit: Suit, favor: Int,
      secrets: Int, actingPlayer: PlayerId,
      override val required: Boolean = false, actorless: Boolean = false)
      extends Discard:
    require(favor >= 0, "discarded favor must be non-negative")
    require(secrets >= 0, "discarded secrets must be non-negative")

    /** The player who discards it, if any. */
    def by: Option[PlayerId] = Option.unless(actorless)(actingPlayer)

    override val children: Vector[Operation] =
      discardWorld(card, from, to, by) ++
        returnedResources(card, suit, favor, secrets, actingPlayer)

  /** Visions carry no resources but otherwise use world-card discard rules. */
  final case class Vision(card: VisionId, from: PositionedLocation,
      to: Region, override val required: Boolean = false) extends Discard:
    override val children: Vector[Operation] =
      discardWorld(card, from, to, None)

  /** Only a ruined edifice can be discarded; intact edifices are locked.
    * `actingPlayer` and `actorless` are as for `Denizen`. */
  final case class RuinedEdifice(card: EdificeId, from: PositionedLocation,
      suit: Suit, favor: Int, secrets: Int, actingPlayer: PlayerId,
      override val required: Boolean = false, actorless: Boolean = false)
      extends Discard:
    require(favor >= 0, "discarded favor must be non-negative")
    require(secrets >= 0, "discarded secrets must be non-negative")

    /** The player who discards it, if any. */
    def by: Option[PlayerId] = Option.unless(actorless)(actingPlayer)

    override val children: Vector[Operation] = Vector(Move(
      Piece.Card(card), from,
      PositionedLocation(Location.Deck(CardDeck.Edifice),
        StackPosition.Bottom), by = by)) ++
      returnedResources(card, suit, favor, secrets, actingPlayer)

  /** Sets a relic aside until Chronicle and takes its secrets facedown. */
  final case class Relic(card: RelicId, from: PositionedLocation, secrets: Int,
      actingPlayer: PlayerId) extends Discard:
    require(secrets >= 0, "discarded secrets must be non-negative")

    override val children: Vector[Operation] = Vector(Move(
      Piece.Card(card), from,
      PositionedLocation(Location.SetAsideRelics),
      by = Some(actingPlayer))) ++
      returnedSecrets(card, secrets, actingPlayer)

  private def discardWorld(card: WorldCardId, from: PositionedLocation,
      destination: Region, by: Option[PlayerId]): Vector[Operation] =
    Vector(Move(Piece.Card(card), from,
      PositionedLocation(Location.RegionalDiscard(destination),
        StackPosition.Top), resultingOrientation = Some(Orientation.FaceDown),
      by = by))

  // The suit fallback is never reached with a positive favor: positiveMove
  // returns empty for zero and Bury.standard requires the suit.
  private[model] def returns(card: CardId, suit: Option[Suit], favor: Int,
      secrets: Int, actingPlayer: PlayerId): Vector[CoreOperation] =
    (positiveMove(favor)(Piece.Favor.apply,
      PositionedLocation(Location.OnCard(card)),
      PositionedLocation(Location.FavorBank(suit.getOrElse(Suit.Arcane)))) ++
      returnedSecrets(card, secrets, actingPlayer)).collect:
      case operation: CoreOperation => operation

  private def returnedResources(card: CardId, suit: Suit, favor: Int,
      secrets: Int, actingPlayer: PlayerId): Vector[Operation] =
    positiveMove(favor)(Piece.Favor.apply,
      PositionedLocation(Location.OnCard(card)),
      PositionedLocation(Location.FavorBank(suit))) ++
    returnedSecrets(card, secrets, actingPlayer)

  private def returnedSecrets(card: CardId, amount: Int,
      actingPlayer: PlayerId): Vector[Operation] =
    if amount == 0 then Vector.empty
    else Vector(
      Move(Piece.Secrets(amount), PositionedLocation(Location.OnCard(card)),
        PositionedLocation(Location.PlayArea(actingPlayer))),
      FlipSecrets(actingPlayer, amount, SecretSide.FaceUp,
        SecretSide.FaceDown))

  private def positiveMove(amount: Int)(piece: Int => Piece,
      from: PositionedLocation,
      to: PositionedLocation): Vector[Operation] =
    if amount == 0 then Vector.empty
    else Vector(Move(piece(amount), from, to))

/** Takes known cards from a prompted draw source, in the order drawn: from
  * the top, or from the bottom when `position` says so (a Search drawn
  * bottom up, catalog batch 6). The destination is intentionally supplied
  * by the prompting rule.
  */
final case class Draw(player: PlayerId, cards: Vector[CardId],
    source: Location, destination: Location,
    position: StackPosition = StackPosition.Top)
    extends CoreOperation:
  override val required: Boolean = true
  require(cards.nonEmpty, "draw must contain at least one card")
  require(position != StackPosition.Unspecified,
    "a draw takes from the top or the bottom")

  val takes: Vector[Take] = cards.map(card => Take(Piece.Card(card),
    player, source, destination, sourcePosition = position))
  override val children: Vector[Operation] =
    takes.flatMap(_.children)

/** Gives pieces in both directions. */
final case class Exchange(give: Give, receive: Give) extends CoreOperation:
  override val required: Boolean = true
  require(give.giver != receive.giver,
    "exchange requires two different giving players")

  override val children: Vector[Operation] =
    give.children ++ receive.children

sealed trait Gain extends CoreOperation
object Gain:
  final case class Favor(player: PlayerId, suit: Suit, amount: Int)
      extends Gain:
    override val children: Vector[Operation] = Vector(Move(
      Piece.Favor(amount),
      PositionedLocation(Location.FavorBank(suit)),
      PositionedLocation(Location.PlayArea(player))))

  final case class Secrets(player: PlayerId, amount: Int) extends Gain:
    override val children: Vector[Operation] = Vector(Move(
      Piece.Secrets(amount),
      PositionedLocation(Location.SharedBank),
      PositionedLocation(Location.PlayArea(player))))

  final case class Warbands(player: PlayerId, kind: ForceKind, amount: Int)
      extends Gain:
    override val children: Vector[Operation] = Vector(Move(
      Piece.Warbands(kind, amount),
      PositionedLocation(Location.WarbandBank(kind)),
      PositionedLocation(Location.PlayArea(player))))

/** Moves a piece out of the giver's custody to the prompted destination.
  * Restrictions on Take do not prevent a Give operation.
  */
final case class Give(piece: Piece, giver: PlayerId,
    from: Location, to: Location,
    override val required: Boolean = false)
    extends CoreOperation:
  require(Location.ownedBy(from, giver),
    "give source must belong to the giving player")
  val move: Move = Move(piece, PositionedLocation(from),
    PositionedLocation(to), by = Some(giver))
  override val children: Vector[Operation] = Vector(move)

/** A typed payment: `favor`/`secret` are placed at a destination card,
  * `favorBurnt`/`secretBurnt` leave play to the shared bank. All fields are
  * non-negative; an all-zero cost is legal and represents a free payment.
  */
final case class Cost(favor: Int = 0, secret: Int = 0, favorBurnt: Int = 0,
    secretBurnt: Int = 0):
  require(favor >= 0 && secret >= 0 && favorBurnt >= 0 && secretBurnt >= 0,
    "costs must be non-negative")

object Cost:
  val free: Cost = Cost(0, 0, 0, 0)

/** Pays a typed cost. The placed portions (`favor`/`secret`) move from the
  * player's play area to `placedAt`; the burnt portions leave play to the
  * shared bank. An all-zero cost is an inert no-op.
  *
  * A cost is placed only onto an empty card, unless `intoOccupied` (battle
  * plans set it). `matchingBank` is the suit bank of the card the cost is paid
  * onto, supplied by the caller because the pipeline has no catalog; it is
  * used only to settle an off-turn payment.
  *
  * Off turn (`offTurn`, set only by the pipeline) the placed portions settle
  * immediately instead of resting on the card: favor goes to `matchingBank`
  * and the payer's secrets flip facedown.
  */
final case class PayCost(player: PlayerId, placedAt: Location, cost: Cost,
    intoOccupied: Boolean = false, matchingBank: Option[Suit] = None,
    offTurn: Boolean = false) extends CoreOperation:
  require(!offTurn || cost.favor == 0 || matchingBank.isDefined,
    "an off-turn placed favor needs its matching bank")
  override val required: Boolean = true
  override val children: Vector[Operation] =
    placedFavor ++ placedSecrets ++
      favorMove(cost.favorBurnt, Location.SharedBank) ++
      secretMove(cost.secretBurnt, Location.SharedBank)

  // The cost.favor > 0 guard matters: matchingBank.get is evaluated eagerly
  // as an argument, and matchingBank may be None for a zero favor.
  private def placedFavor: Vector[Operation] =
    if offTurn && cost.favor > 0 then
      favorMove(cost.favor, Location.FavorBank(matchingBank.get))
    else favorMove(cost.favor, placedAt)

  private def placedSecrets: Vector[Operation] =
    if cost.secret == 0 then Vector.empty
    else if offTurn then Vector(FlipSecrets(player, cost.secret,
      SecretSide.FaceUp, SecretSide.FaceDown))
    else secretMove(cost.secret, placedAt)

  private def favorMove(amount: Int, to: Location): Vector[Operation] =
    if amount == 0 then Vector.empty
    else Vector(Move(Piece.Favor(amount),
      PositionedLocation(Location.PlayArea(player)), PositionedLocation(to)))

  private def secretMove(amount: Int, to: Location): Vector[Operation] =
    if amount == 0 then Vector.empty
    else Vector(Move(Piece.Secrets(amount),
      PositionedLocation(Location.PlayArea(player)), PositionedLocation(to)))

/** Returns warbands to their matching bank. Imperial warbands use the
  * Chancellor's bank; bandits use the shared bandit bank.
  */
final case class Kill(warbands: Piece.Warbands,
    from: PositionedLocation) extends CoreOperation:
  override val children: Vector[Operation] = Vector(Move(
    warbands, from,
    PositionedLocation(Location.WarbandBank(warbands.kind))))

/** Places a prompted card at a site or in a player's play area, including
  * their Advisers.
  */
final case class Play(card: CardId, from: PositionedLocation,
    destination: Location, orientation: Orientation,
    override val required: Boolean = false)
    extends CoreOperation:
  require(destination match {
    case _: Location.Site | _: Location.PlayArea => true
    case _ => false
  }, "play destination must be a site or a play area")

  override val children: Vector[Operation] = Vector(Move(
    Piece.Card(card), from, PositionedLocation(destination),
    resultingOrientation = Some(orientation)))

/** Swaps warbands for a new color. This is distinct from kill and sacrifice. */
final case class Replace(removed: Piece.Warbands,
    replacements: Piece.Warbands, at: PositionedLocation,
    override val required: Boolean = false)
    extends CoreOperation:
  require(removed.amount == replacements.amount,
    "replace must exchange equal numbers of warbands")
  require(removed.kind != replacements.kind,
    "replacement warbands must have a new color")

  override val children: Vector[Operation] = Vector(
    Move(removed, at,
      PositionedLocation(Location.WarbandBank(removed.kind))),
    Move(replacements,
      PositionedLocation(Location.WarbandBank(replacements.kind)), at))
  override val simultaneous: Boolean = true

/** Flips a card faceup without triggering When Played powers. */
final case class Reveal(card: CardId, at: Location)
    extends CoreOperation:
  override val children: Vector[Operation] =
    Vector(Flip(card, at, Orientation.FaceUp))

/** Kills a warband that belongs to the player issuing the instruction. */
final case class Sacrifice(player: PlayerId,
    warbands: Piece.Warbands, from: PositionedLocation)
    extends CoreOperation:
  private val kill = Kill(warbands, from)
  override val children: Vector[Operation] = kill.children

/** Swaps two cards by moving each one to the other's location. `by` is the
  * player who swaps them. */
final case class Swap(firstCard: CardId, firstLocation: PositionedLocation,
    secondCard: CardId, secondLocation: PositionedLocation,
    by: Option[PlayerId] = None)
    extends CoreOperation:
  require(firstCard != secondCard, "swap requires two different cards")
  require(firstLocation != secondLocation,
    "swap requires two different locations")

  val first: Move =
    Move(Piece.Card(firstCard), firstLocation, secondLocation, by = by)
  val second: Move =
    Move(Piece.Card(secondCard), secondLocation, firstLocation, by = by)
  override val children: Vector[Operation] = Vector(first, second)
  override val simultaneous: Boolean = true

/** Moves a piece into the prompted player's custody. A required `Take`
  * rejects its batch, or fails the search's path, when a restriction refuses
  * it.
  *
  * `leaving` clears what does not travel with the piece before it moves: a
  * banner's favor returned to the banks, its secrets burnt. Those operations
  * are the `Take`'s first children, so a restriction that refuses the `Take`
  * refuses them with it, and the banner stays whole.
  *
  * `sourceSide` names the side of the secrets it takes from a board.
  */
final case class Take(piece: Piece, player: PlayerId,
    from: Location, to: Location,
    sourcePosition: StackPosition = StackPosition.Unspecified,
    override val required: Boolean = false,
    leaving: Vector[CoreOperation] = Vector.empty,
    sourceSide: Option[SecretSide] = None)
    extends CoreOperation:
  require(Location.ownedBy(to, player),
    "take destination must belong to the taking player")
  val move: Move = Move(piece, PositionedLocation(from, sourcePosition,
    sourceSide), PositionedLocation(to), by = Some(player))
  override val children: Vector[Operation] = leaving :+ move

// ---------------------------------------------------------------------------
// Walker leaves and composites.
//
// These cases MUST live in this file: CoreOperation/PrimitiveOperation are
// sealed and Scala requires sealed subclasses in the same source file
// (Task 1 review ruling / plan amendment). They extend PrimitiveOperation, so
// `children = Vector(this)` — each is a leaf.
//
// Dice pools: pool counts live in state (CurrentGameState.rollPools:
// Map[PoolKey, DicePoolState]); these leaves mutate/consume them. A `Roll`
// only records that a roll must happen — the application layer pre-rolls the
// faces at the command boundary (the engine never rolls). Powers/windows are
// not wired in this slice, so leaves keep the default `window = None`; Roll
// and Decide are the future window-hook candidates and may override `window`
// once power windows land.
// ---------------------------------------------------------------------------

/** Adds `delta` dice to a named pool's count in state. `window` makes the
  * node hookable exactly like `Decide`.
  */
final case class ModifyDicePool(pool: PoolKey, delta: Int,
    override val window: Option[PowerWindow] = None)
    extends PrimitiveOperation

/** Records one use-limited power instance as used for the current turn, by
  * adding `power` to `TurnState.usedPowers` (batch-1 Task 7).
  *
  * The write side of a use limit has to be an operation rather than a state
  * callback beside one, because replay applies recorded operations and
  * nothing else: a limit written any other way would be absent from a
  * reloaded game and the same site could be used again. The read side is a
  * power's own restriction, so nothing here knows which power this is.
  *
  * Adding a `PowerUseRef` the turn already holds is a no-op rather than a
  * rejection, matching the set semantics of the field it writes. Whether a
  * second use is legal at all is a question for whoever declared the limit,
  * asked before the walk; this operation only records the answer.
  */
final case class RecordPowerUse(power: PowerUseRef) extends PrimitiveOperation

/** Moves the turn into `phase` (batch-1 Task 7).
  *
  * A phase change is a state write like any other, so the procedure that
  * performs one declares it as an operation and replay restores it from the
  * journal -- the same argument [[RecordPowerUse]] makes. Ending Wake is the
  * only procedure that declares it today.
  *
  * Which phase may follow which is NOT stated here. The phase order is a rule
  * about the turn, and the turn's procedures are where it is written; an
  * operation that encoded the order would state the same rule a second time,
  * in the vocabulary every future power can reach for. The one thing applying
  * this does reject is entering the phase the turn is already in
  * (`OperationError.PhaseAlreadyEntered`), which is a doubled or reordered
  * journal rather than a rule about order.
  */
final case class EnterPhase(phase: Phase) extends PrimitiveOperation

/** Sets who holds the Oathkeeper title: a player, or the shared bank (`None`).
  *
  * The holder and the side change together: any change of holder puts the
  * title back on its Oathkeeper side, as every title change in the rules does.
  * Which holder the title should go to is not decided here; that is
  * `OathkeeperRules.outcome`. The one thing applying this rejects is leaving
  * the holder as it is (`OperationError.OathkeeperUnchanged`), which is a
  * doubled or reordered journal rather than a rule about the title.
  */
final case class SetOathkeeper(holder: Option[PlayerId]) extends PrimitiveOperation

/** Sets the campaign's Oathkeeper goal (catalog batch 8, slice 2).
  *
  * Which goal a power may choose is the power's rule. The title follows at
  * the next action boundary, where `OathkeeperRules.outcome` reads the goal.
  * The one thing applying this rejects is leaving the goal as it is
  * (`OperationError.OathkeeperGoalUnchanged`), which is a doubled or
  * reordered journal, as for [[SetOathkeeper]].
  */
final case class SetOathkeeperGoal(goal: OathkeeperGoal)
    extends PrimitiveOperation

/** Records the result of the Campaign just fought as the public
  * `lastCampaignResult`, replacing the previous one.
  */
final case class RecordCampaignResult(result: CampaignResult)
    extends PrimitiveOperation

/** Puts `entry` on the queue (walker-driven game loop design, "The
  * queue"): at the back, or in front when `atFront`. A source power emits
  * it inside the origin action's tree. */
final case class QueueProcedure(entry: QueuedProcedure,
    atFront: Boolean = false) extends PrimitiveOperation

/** Hands the turn to `player` in `phase`, clearing every power use recorded
  * against the turn that ended.
  *
  * Two procedures declare it: Finish Rest hands the turn to the next player,
  * and Setup's last step hands the first turn to the first player. As with
  * [[EnterPhase]], which player follows whom is not stated here: that is the
  * turn order Finish Rest reads. What applying this does reject is a player
  * who is not seated, and a phase no turn begins in. A turn begins in Wake,
  * or in RoundEnd when the last player has rested.
  */
final case class BeginTurn(player: PlayerId, phase: Phase)
    extends PrimitiveOperation

/** Advances Visions Drawn by one after a world-deck Vision is drawn. */
case object AdvanceVisionsDrawn extends PrimitiveOperation:
  override val required: Boolean = true

/** Reorders a pile Search draws from: the world deck, or one region's
  * discard pile. `order` is the pile's new contents in stored order (the
  * world deck top first, a discard pile top last).
  *
  * A tree declares a shuffle with no order, as a leaf of its own (a
  * `Branch` may return one). The walker fills the order from its random
  * source before running it, so the journal records the order and replay
  * applies it as it ran, as it applies an automatic roll's faces. A
  * `BuildOps` batch is run as it is built, so an unordered shuffle inside one
  * is never filled. Applying a shuffle with no order, or with an order that
  * is not exactly the pile's cards, is an error.
  */
final case class Shuffle(pile: SearchSource,
    order: Option[Vector[WorldCardId]] = None) extends PrimitiveOperation:
  override val required: Boolean = true

/** Rolls `dice` drawn from `pool`; the pool count comes from state. `Parked`
  * (the default) parks the walker until the faces ride the next command;
  * `Automatic` takes them from the walker's dice source and keeps walking.
  * A `window` lets a power hook the roll.
  */
final case class Roll(pool: PoolKey, dice: DiceSpec,
    mode: RollMode = RollMode.Parked,
    override val window: Option[PowerWindow] = None)
    extends PrimitiveOperation

/** Edits a recorded roll outcome for `pool` (power-authored skulls/score
  * changes). Left `None` fields unchanged.
  */
final case class ModifyRollOutcome(pool: PoolKey, skulls: Option[Int],
    score: Option[Int]) extends PrimitiveOperation

/** Rerolls the dice at `indices` of `pool`'s recorded outcome: the faces
  * at those indices are replaced by `faces`, in order, and the skulls and
  * the score are worked out again (catalog batch 7, slice 1).
  *
  * A tree declares it with no faces. The walker fills them from its dice
  * source before running it, so the journal records them and replay applies
  * them as they ran, as it applies a `Shuffle`'s order. A `BuildOps` batch
  * is run as it is built, so a reroll inside one is never filled. Applying
  * a reroll with no faces is an error.
  */
final case class Reroll(pool: PoolKey, indices: Vector[Int],
    faces: Option[Vector[DieFace]] = None) extends PrimitiveOperation:
  override val required: Boolean = true

/** Sets dice of `pool` to `faces` instead of rolling them (catalog batch 7,
  * slice 3: Arcane Armor). The faces join the pool's recorded outcome,
  * which starts with no throw counted when the pool has not rolled, and the
  * pool holds as many dice fewer, so the roll that follows rolls only the
  * rest. The set dice are no part of that roll's throw, so no reroll
  * changes them. Setting no dice, more dice than the pool holds, or faces
  * of another kind than the outcome's is an error.
  */
final case class SetDieFaces(pool: PoolKey, faces: Vector[DieFace])
    extends PrimitiveOperation:
  override val required: Boolean = true

/** The name [[Defer]] holds ops under, until a later step of the same
  * action takes them (operation rewrite design, "Deferral"). Named after
  * the power that owns it. */
final case class DeferralKey(value: String)

/** Holds `ops` under `key` in `CurrentGameState.walkerDeferred`, after any
  * held there already, without running them. A later step of the same
  * action runs them, replaces them or drops them, and takes them with
  * [[TakeDeferred]]; the walker drops whatever is left when the action
  * ends. They are held as the journal records them, so a game reloaded from
  * its events holds the same ops. It writes no Game Log line.
  *
  * It is a leaf, so [[Operation.flatten]] stops here and never descends
  * into `ops`. That is what holding them means: nothing under a `Defer`
  * runs, is screened, or counts as a hidden outcome until a later step
  * returns those ops as operations of its own (hidden outcome depth
  * decision).
  */
final case class Defer(key: DeferralKey, ops: Vector[CoreOperation])
    extends PrimitiveOperation

/** Drops what [[Defer]] holds under `key`. Taking a key that holds nothing
  * changes nothing. It writes no Game Log line. */
final case class TakeDeferred(key: DeferralKey) extends PrimitiveOperation

/** Records that the walk's actor acts as if their pawn were at `site` for
  * the rest of the walk, in `CurrentGameState.walkerAsIfSite` (catalog
  * batch 8, item 13c). The walker clears it when the walk ends. The journal
  * records it, so a game reloaded from its events holds the same site. It
  * moves no pawn and writes no Game Log line.
  */
final case class ActAsIf(site: SiteId) extends PrimitiveOperation

/** Semantic window for a card played faceup, to a site or as a faceup
  * adviser. Powers supply its children; no hook is a delta.
  */
final case class CardPlayedFaceup(card: WorldCardId, resultingSource: RuleSourceRef)
    extends Operation:
  override val window: Option[PowerWindow] = Some(PowerWindow.ActionCardPlayedFaceup)
  override val children: Vector[Operation] = Vector.empty

/** Semantic window for a card (a denizen or a Vision) placed facedown as an
  * adviser by `player`. A discard emits neither hook. Powers supply its
  * children; no hook is a delta.
  */
final case class CardPlayedFacedown(card: WorldCardId, player: PlayerId)
    extends Operation:
  override val window: Option[PowerWindow] = Some(PowerWindow.ActionCardPlayedFacedown)
  override val children: Vector[Operation] = Vector.empty

