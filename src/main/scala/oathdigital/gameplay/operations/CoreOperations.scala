package oathdigital.gameplay.operations

import oathdigital.gameplay.{DiceSpec, OathViolation, ReadyGame}
import oathdigital.gameplay.powerresolver.PowerWindow
import oathdigital.model._

/** Core operations occurring in a game of Oath.
  *
  * These values describe semantic game instructions. They do not
  * validate restrictions, choices, and consent.
  *
  * Re-rooted under [[Operation]]: composites expose their constituent
  * operations through `children` (the vectors they previously exposed as
  * `primitives`, now widened to `Vector[Operation]`); read the leaves with
  * [[Operation.flatten]]. `PrimitiveOperation` stays a subtype so the
  * existing operation-batch vocabulary (`Vector[CoreOperation]`, policies,
  * executor) keeps accepting both leaves and composites.
  */
sealed trait CoreOperation extends Operation {
  def simultaneous: Boolean = false
}

sealed trait PrimitiveOperation extends CoreOperation {
  final override val children: Vector[Operation] = Vector(this)
}

sealed trait Piece extends Product with Serializable
object Piece {
  final case class Card(id: CardId) extends Piece
  final case class Banner(banner: oathdigital.model.Banner) extends Piece
  final case class Pawn(player: PlayerId) extends Piece

  sealed trait Counted extends Piece { def amount: Int }
  final case class Favor(amount: Int) extends Counted {
    require(amount > 0, "favor amount must be positive")
  }
  final case class Secrets(amount: Int) extends Counted {
    require(amount > 0, "secret amount must be positive")
  }
  final case class Warbands(kind: ForceKind, amount: Int) extends Counted {
    require(amount > 0, "warband amount must be positive")
  }
}

sealed trait Location extends Product with Serializable
object Location {
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

  private[operations] def ownedBy(location: Location,
      player: PlayerId): Boolean = location match {
    case Hand(owner) => owner == player
    case PlayArea(owner) => owner == player
    case _ => false
  }
}

sealed trait SecretSide extends Product with Serializable
object SecretSide {
  case object FaceUp extends SecretSide
  case object FaceDown extends SecretSide
}

sealed trait StackPosition extends Product with Serializable
object StackPosition {
  case object Unspecified extends StackPosition
  case object Top extends StackPosition
  case object Bottom extends StackPosition
}

final case class PositionedLocation(
    location: Location,
    position: StackPosition = StackPosition.Unspecified)

/** Changes a piece's location or role within one play area. */
final case class Move(
    piece: Piece,
    from: PositionedLocation,
    to: PositionedLocation,
    resultingOrientation: Option[Orientation] = None)
    extends PrimitiveOperation {
  require(from != to || (resultingOrientation.isDefined &&
      piece.isInstanceOf[Piece.Card] &&
      from.location == to.location &&
      from.location.isInstanceOf[Location.PlayArea]),
    "a move must change location, stack position, or card role within a play area")
  require(resultingOrientation.isEmpty || piece.isInstanceOf[Piece.Card],
    "only cards have an orientation")
}

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
    from: SecretSide, to: SecretSide) extends PrimitiveOperation {
  require(amount > 0, "secret amount must be positive")
  require(from != to, "secret flip must change side")
}

/** Changes a player's spendable Supply. Negative amounts spend (an
  * insufficient track is an OperationError.InsufficientSupply), positive
  * amounts gain up to SupplyTrack.Maximum. Rest's refresh-to-value stays a
  * non-delta write in the Rest phase.
  */
final case class AdjustSupply(player: PlayerId, amount: Int)
    extends PrimitiveOperation {
  require(amount != 0, "supply adjustment must be non-zero")
}

/** Moves favor or secrets to the shared bank. */
sealed trait Burn extends CoreOperation {
  def resource: Piece.Counted
  def from: PositionedLocation

  final lazy val move: Move = Move(resource, from,
    PositionedLocation(Location.SharedBank))
  final override lazy val children: Vector[Operation] = Vector(move)
}
object Burn {
  def favor(amount: Int, from: PositionedLocation): Burn =
    ResourceBurn(Piece.Favor(amount), from)

  def secrets(amount: Int, from: PositionedLocation): Burn =
    ResourceBurn(Piece.Secrets(amount), from)

  private final case class ResourceBurn(resource: Piece.Counted,
      from: PositionedLocation) extends Burn
}

sealed trait BuryableCard extends Product with Serializable {
  def id: CardId
  def deck: CardDeck
}
object BuryableCard {
  final case class Denizen(id: DenizenId) extends BuryableCard {
    override val deck: CardDeck = CardDeck.World
  }
  final case class Relic(id: RelicId) extends BuryableCard {
    override val deck: CardDeck = CardDeck.Relic
  }
  final case class Edifice(id: EdificeId) extends BuryableCard {
    override val deck: CardDeck = CardDeck.Edifice
  }
}

/** Adds a denizen, relic, or edifice to the bottom of its matching deck.
  * Bury explicitly ignores the locked restriction.
  */
final case class Bury(card: BuryableCard, from: PositionedLocation)
    extends PrimitiveOperation {
  val to: PositionedLocation = PositionedLocation(
    Location.Deck(card.deck), StackPosition.Bottom)
}

sealed trait Discard extends CoreOperation
object Discard {
  /** Discards a denizen facedown to the specified regional pile and returns
    * all resources on it.
    */
  final case class Denizen(card: DenizenId, from: PositionedLocation,
      to: Region, suit: Suit, favor: Int,
      secrets: Int, actingPlayer: PlayerId) extends Discard {
    require(favor >= 0, "discarded favor must be non-negative")
    require(secrets >= 0, "discarded secrets must be non-negative")

    override val children: Vector[Operation] =
      discardWorld(card, from, to) ++
        returnedResources(card, suit, favor, secrets, actingPlayer)
  }

  /** Visions carry no resources but otherwise use world-card discard rules. */
  final case class Vision(card: VisionId, from: PositionedLocation,
      to: Region) extends Discard {
    override val children: Vector[Operation] =
      discardWorld(card, from, to)
  }

  /** Only a ruined edifice can be discarded; intact edifices are locked. */
  final case class RuinedEdifice(card: EdificeId, from: PositionedLocation,
      suit: Suit, favor: Int, secrets: Int, actingPlayer: PlayerId)
      extends Discard {
    require(favor >= 0, "discarded favor must be non-negative")
    require(secrets >= 0, "discarded secrets must be non-negative")

    override val children: Vector[Operation] = Vector(Move(
      Piece.Card(card), from,
      PositionedLocation(Location.Deck(CardDeck.Edifice),
        StackPosition.Bottom))) ++
      returnedResources(card, suit, favor, secrets, actingPlayer)
  }

  /** Sets a relic aside until Chronicle and takes its secrets facedown. */
  final case class Relic(card: RelicId, from: PositionedLocation, secrets: Int,
      actingPlayer: PlayerId) extends Discard {
    require(secrets >= 0, "discarded secrets must be non-negative")

    override val children: Vector[Operation] = Vector(Move(
      Piece.Card(card), from,
      PositionedLocation(Location.SetAsideRelics))) ++
      returnedSecrets(card, secrets, actingPlayer)
  }

  private def discardWorld(card: WorldCardId, from: PositionedLocation,
      destination: Region): Vector[Operation] = Vector(Move(
    Piece.Card(card), from,
    PositionedLocation(Location.RegionalDiscard(destination),
      StackPosition.Top), resultingOrientation = Some(Orientation.FaceDown)))

  private def returnedResources(card: CardId, suit: Suit, favor: Int,
      secrets: Int, actingPlayer: PlayerId): Vector[Operation] =
    positiveMove(favor)(Piece.Favor.apply,
      PositionedLocation(Location.OnCard(card)),
      PositionedLocation(Location.FavorBank(suit))) ++
    returnedSecrets(card, secrets, actingPlayer)

  private def returnedSecrets(card: CardId, amount: Int,
      actingPlayer: PlayerId): Vector[Operation] =
    if (amount == 0) Vector.empty
    else Vector(
      Move(Piece.Secrets(amount), PositionedLocation(Location.OnCard(card)),
        PositionedLocation(Location.PlayArea(actingPlayer))),
      FlipSecrets(actingPlayer, amount, SecretSide.FaceUp,
        SecretSide.FaceDown))

  private def positiveMove(amount: Int)(piece: Int => Piece,
      from: PositionedLocation,
      to: PositionedLocation): Vector[Operation] =
    if (amount == 0) Vector.empty
    else Vector(Move(piece(amount), from, to))
}

/** Takes known cards from the top of a prompted draw source, in top-first
  * order. The destination is intentionally supplied by the prompting rule.
  */
final case class Draw(player: PlayerId, cards: Vector[CardId],
    source: Location, destination: Location)
    extends CoreOperation {
  require(cards.nonEmpty, "draw must contain at least one card")

  val takes: Vector[Take] = cards.map(card => Take(Piece.Card(card),
    player, source, destination, sourcePosition = StackPosition.Top))
  override val children: Vector[Operation] =
    takes.flatMap(_.children)
}

/** Gives pieces in both directions. */
final case class Exchange(give: Give, receive: Give) extends CoreOperation {
  require(give.giver != receive.giver,
    "exchange requires two different giving players")

  override val children: Vector[Operation] =
    give.children ++ receive.children
}

sealed trait Gain extends CoreOperation
object Gain {
  final case class Favor(player: PlayerId, suit: Suit, amount: Int)
      extends Gain {
    override val children: Vector[Operation] = Vector(Move(
      Piece.Favor(amount),
      PositionedLocation(Location.FavorBank(suit)),
      PositionedLocation(Location.PlayArea(player))))
  }

  final case class Secrets(player: PlayerId, amount: Int) extends Gain {
    override val children: Vector[Operation] = Vector(Move(
      Piece.Secrets(amount),
      PositionedLocation(Location.SharedBank),
      PositionedLocation(Location.PlayArea(player))))
  }

  final case class Warbands(player: PlayerId, kind: ForceKind, amount: Int)
      extends Gain {
    override val children: Vector[Operation] = Vector(Move(
      Piece.Warbands(kind, amount),
      PositionedLocation(Location.WarbandBank(kind)),
      PositionedLocation(Location.PlayArea(player))))
  }
}

/** Moves a piece out of the giver's custody to the prompted destination.
  * Restrictions on Take do not prevent a Give operation.
  */
final case class Give(piece: Piece, giver: PlayerId,
    from: Location, to: Location)
    extends CoreOperation {
  require(Location.ownedBy(from, giver),
    "give source must belong to the giving player")
  val move: Move = Move(piece, PositionedLocation(from), PositionedLocation(to))
  override val children: Vector[Operation] = Vector(move)
}

/** A typed payment: `favor`/`secret` are placed at a destination card,
  * `favorBurnt`/`secretBurnt` leave play to the shared bank. All fields are
  * non-negative; an all-zero cost is legal and represents a free payment.
  */
final case class Cost(favor: Int = 0, secret: Int = 0, favorBurnt: Int = 0,
    secretBurnt: Int = 0) {
  require(favor >= 0 && secret >= 0 && favorBurnt >= 0 && secretBurnt >= 0,
    "costs must be non-negative")
}

object Cost {
  val free: Cost = Cost(0, 0, 0, 0)
}

/** Pays a typed cost. The placed portions (`favor`/`secret`) move from the
  * player's play area to `placedAt`; the burnt portions leave play to the
  * shared bank. An all-zero cost is an inert no-op.
  */
final case class PayCost(player: PlayerId, placedAt: Location, cost: Cost)
    extends CoreOperation {
  override val children: Vector[Operation] =
    favorMove(cost.favor, placedAt) ++
      secretMove(cost.secret, placedAt) ++
      favorMove(cost.favorBurnt, Location.SharedBank) ++
      secretMove(cost.secretBurnt, Location.SharedBank)

  private def favorMove(amount: Int, to: Location): Vector[Operation] =
    if (amount == 0) Vector.empty
    else Vector(Move(Piece.Favor(amount),
      PositionedLocation(Location.PlayArea(player)), PositionedLocation(to)))

  private def secretMove(amount: Int, to: Location): Vector[Operation] =
    if (amount == 0) Vector.empty
    else Vector(Move(Piece.Secrets(amount),
      PositionedLocation(Location.PlayArea(player)), PositionedLocation(to)))
}

/** Returns warbands to their matching bank. Imperial warbands use the
  * Chancellor's bank; bandits use the shared bandit bank.
  */
final case class Kill(warbands: Piece.Warbands,
    from: PositionedLocation) extends CoreOperation {
  override val children: Vector[Operation] = Vector(Move(
    warbands, from,
    PositionedLocation(Location.WarbandBank(warbands.kind))))
}

/** Places a prompted card at a site or in a player's play area, including
  * their Advisers.
  */
final case class Play(card: CardId, from: PositionedLocation,
    destination: Location, orientation: Orientation)
    extends CoreOperation {
  require(destination match {
    case _: Location.Site | _: Location.PlayArea => true
    case _ => false
  }, "play destination must be a site or a play area")

  override val children: Vector[Operation] = Vector(Move(
    Piece.Card(card), from, PositionedLocation(destination),
    resultingOrientation = Some(orientation)))
}

/** Swaps warbands for a new color. This is distinct from kill and sacrifice. */
final case class Replace(removed: Piece.Warbands,
    replacements: Piece.Warbands, at: PositionedLocation)
    extends CoreOperation {
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
}

/** Flips a card faceup without triggering When Played powers. */
final case class Reveal(card: CardId, at: Location)
    extends CoreOperation {
  override val children: Vector[Operation] =
    Vector(Flip(card, at, Orientation.FaceUp))
}

/** Kills a warband that belongs to the player issuing the instruction. */
final case class Sacrifice(player: PlayerId,
    warbands: Piece.Warbands, from: PositionedLocation)
    extends CoreOperation {
  private val kill = Kill(warbands, from)
  override val children: Vector[Operation] = kill.children
}

/** Swaps two cards by moving each one to the other's location. */
final case class Swap(firstCard: CardId, firstLocation: PositionedLocation,
    secondCard: CardId, secondLocation: PositionedLocation)
    extends CoreOperation {
  require(firstCard != secondCard, "swap requires two different cards")
  require(firstLocation != secondLocation,
    "swap requires two different locations")

  val first: Move =
    Move(Piece.Card(firstCard), firstLocation, secondLocation)
  val second: Move =
    Move(Piece.Card(secondCard), secondLocation, firstLocation)
  override val children: Vector[Operation] = Vector(first, second)
  override val simultaneous: Boolean = true
}

/** Moves a piece into the prompted player's custody. */
final case class Take(piece: Piece, player: PlayerId,
    from: Location, to: Location,
    sourcePosition: StackPosition = StackPosition.Unspecified)
    extends CoreOperation {
  require(Location.ownedBy(to, player),
    "take destination must belong to the taking player")
  val move: Move = Move(piece, PositionedLocation(from, sourcePosition),
    PositionedLocation(to))
  override val children: Vector[Operation] = Vector(move)
}

// ---------------------------------------------------------------------------
// Walker leaves and composites.
//
// These cases MUST live in this file: CoreOperation/PrimitiveOperation are
// sealed and Scala 2.13 requires sealed subclasses in the same source file
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

/** Parks a walker at a roll of `dice` drawn from `pool`; pool count comes from
  * state, faces ride the next command.
  */
final case class Roll(pool: PoolKey, dice: DiceSpec)
    extends PrimitiveOperation

/** Edits a recorded roll outcome for `pool` (power-authored skulls/score
  * changes). Left `None` fields unchanged.
  */
final case class ModifyRollOutcome(pool: PoolKey, skulls: Option[Int],
    score: Option[Int]) extends PrimitiveOperation

/** Removes `pool` from the rollPools state map. */
final case class ClearDicePool(pool: PoolKey) extends PrimitiveOperation

/** Parks a walker until `owner` resolves the decision `query` states.
  *
  * `query` is the single source of both halves of the contract: the walker
  * accepts exactly the answers it declares (via `DecisionQueries`), and the
  * projector offers exactly the options it declares. There is deliberately
  * no `validate` closure beside it — a legality fact that is not expressible
  * as an option set is a fact the client could never have been shown, so it
  * belongs in how the tree BUILDS the query, not in a second check the
  * projector cannot read. An option that authoritative state no longer
  * supports is simply absent from the query the next command rebuilds.
  *
  * `owner` is a concrete player rather than a resolver: the tree is rebuilt
  * against live state on every command, so whatever would have been computed
  * at resume time can be computed at build time instead.
  *
  * `window` makes the decision hookable: the walker folds the gathered
  * transforms over `Vector(this)` before walking it, so a power may insert
  * operations around the decision, or replace its query.
  */
final case class Decide(decisionId: String, owner: PlayerId,
    query: DecisionQuery,
    override val window: Option[PowerWindow] = None)
    extends PrimitiveOperation

/** A leaf whose concrete deltas are decided AT WALK TIME: the walker calls
  * `build(state, pending)` when it reaches the node and executes whatever
  * `CoreOperation`s come back, recording them in the node's
  * `WalkerStepRecorded.ops`.
  *
  * The closure lives in the action tree, which is derived per command and
  * never persisted, so it may close over anything reachable at build time
  * (e.g. a chosen relic id from an earlier answered decision). A `build` that
  * returns `Vector.empty` runs nothing and records no step (nothing ran).
  * Flatten sees a leaf: `Operation.flatten(BuildOps(...))` is itself.
  */
final case class BuildOps(build: (ReadyGame, PendingTree) =>
    Either[OathViolation, Vector[CoreOperation]],
    override val window: Option[PowerWindow] = None)
    extends PrimitiveOperation

/** Re-executes `body` until `guard` is false. The guard runs only at command
  * time (the walker's job); `Operation.flatten` always descends into `body`,
  * and replay applies recorded ops, never re-guarding.
  */
final case class Repeat(guard: (ReadyGame, PendingTree) => Boolean,
    body: Operation) extends CoreOperation {
  override val children: Vector[Operation] = Vector(body)
}

/** A composite whose children are chosen AT WALK TIME: the walker evaluates
  * `select(state, pending)` when it reaches the node and walks the returned
  * operations in order.
  *
  * `children` is statically empty because the selection is dynamic, so
  * `Operation.flatten(Branch(...))` is empty and a Branch must never sit on
  * an action root that other code flattens — the walker resolves it and
  * replay applies recorded ops, never re-selecting. Selected children are
  * addressed by child index inside the branch, exactly like static children.
  */
final case class Branch(select: (ReadyGame, PendingTree) => Vector[Operation])
    extends CoreOperation {
  override val children: Vector[Operation] = Vector.empty
}

/** Runs `children` in order. The walker's sequence composite; `Repeat`,
  * `Sequence`, and `Branch` are the composites the walker consumes this slice.
  */
final case class Sequence(override val children: Vector[Operation],
    override val window: Option[PowerWindow] = None) extends CoreOperation

object Sequence {
  /** Vararg builder so action trees read `Sequence(a, b)`
   *  instead of wrapping a vector by hand.
   */
  def apply(first: Operation, rest: Operation*): Sequence =
    new Sequence(first +: rest.toVector)
}
