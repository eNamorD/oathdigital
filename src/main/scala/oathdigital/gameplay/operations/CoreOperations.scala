package oathdigital.gameplay.operations

import oathdigital.model._

/** Core operations occurring in a game of Oath.
  *
  * These values describe semantic game instructions. They do not
  * validate restrictions, choices, and consent.
  */
sealed trait CoreOperation extends Product with Serializable {
  def primitives: Vector[PrimitiveOperation]
  def simultaneous: Boolean = false
}

sealed trait PrimitiveOperation extends CoreOperation {
  final override def primitives: Vector[PrimitiveOperation] = Vector(this)
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

sealed trait CardDeck extends Product with Serializable
object CardDeck {
  case object World extends CardDeck
  case object Relic extends CardDeck
  case object Edifice extends CardDeck
  case object Legacy extends CardDeck
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

/** Moves favor or secrets to the shared bank. */
sealed trait Burn extends CoreOperation {
  def resource: Piece.Counted
  def from: PositionedLocation

  final lazy val move: Move = Move(resource, from,
    PositionedLocation(Location.SharedBank))
  final override lazy val primitives: Vector[PrimitiveOperation] = Vector(move)
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

    override val primitives: Vector[PrimitiveOperation] =
      discardWorld(card, from, to) ++
        returnedResources(card, suit, favor, secrets, actingPlayer)
  }

  /** Visions carry no resources but otherwise use world-card discard rules. */
  final case class Vision(card: VisionId, from: PositionedLocation,
      to: Region) extends Discard {
    override val primitives: Vector[PrimitiveOperation] =
      discardWorld(card, from, to)
  }

  /** Only a ruined edifice can be discarded; intact edifices are locked. */
  final case class RuinedEdifice(card: EdificeId, from: PositionedLocation,
      suit: Suit, favor: Int, secrets: Int, actingPlayer: PlayerId)
      extends Discard {
    require(favor >= 0, "discarded favor must be non-negative")
    require(secrets >= 0, "discarded secrets must be non-negative")

    override val primitives: Vector[PrimitiveOperation] = Vector(Move(
      Piece.Card(card), from,
      PositionedLocation(Location.Deck(CardDeck.Edifice),
        StackPosition.Bottom))) ++
      returnedResources(card, suit, favor, secrets, actingPlayer)
  }

  /** Sets a relic aside until Chronicle and takes its secrets facedown. */
  final case class Relic(card: RelicId, from: PositionedLocation, secrets: Int,
      actingPlayer: PlayerId) extends Discard {
    require(secrets >= 0, "discarded secrets must be non-negative")

    override val primitives: Vector[PrimitiveOperation] = Vector(Move(
      Piece.Card(card), from,
      PositionedLocation(Location.SetAsideRelics))) ++
      returnedSecrets(card, secrets, actingPlayer)
  }

  private def discardWorld(card: WorldCardId, from: PositionedLocation,
      destination: Region): Vector[PrimitiveOperation] = Vector(Move(
    Piece.Card(card), from,
    PositionedLocation(Location.RegionalDiscard(destination),
      StackPosition.Top), resultingOrientation = Some(Orientation.FaceDown)))

  private def returnedResources(card: CardId, suit: Suit, favor: Int,
      secrets: Int, actingPlayer: PlayerId): Vector[PrimitiveOperation] =
    positiveMove(favor)(Piece.Favor.apply,
      PositionedLocation(Location.OnCard(card)),
      PositionedLocation(Location.FavorBank(suit))) ++
    returnedSecrets(card, secrets, actingPlayer)

  private def returnedSecrets(card: CardId, amount: Int,
      actingPlayer: PlayerId): Vector[PrimitiveOperation] =
    if (amount == 0) Vector.empty
    else Vector(
      Move(Piece.Secrets(amount), PositionedLocation(Location.OnCard(card)),
        PositionedLocation(Location.PlayArea(actingPlayer))),
      FlipSecrets(actingPlayer, amount, SecretSide.FaceUp,
        SecretSide.FaceDown))

  private def positiveMove(amount: Int)(piece: Int => Piece,
      from: PositionedLocation,
      to: PositionedLocation): Vector[PrimitiveOperation] =
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
  override val primitives: Vector[PrimitiveOperation] =
    takes.flatMap(_.primitives)
}

/** Gives pieces in both directions. */
final case class Exchange(give: Give, receive: Give) extends CoreOperation {
  require(give.giver != receive.giver,
    "exchange requires two different giving players")

  override val primitives: Vector[PrimitiveOperation] =
    give.primitives ++ receive.primitives
}

sealed trait Gain extends CoreOperation
object Gain {
  final case class Favor(player: PlayerId, suit: Suit, amount: Int)
      extends Gain {
    override val primitives: Vector[PrimitiveOperation] = Vector(Move(
      Piece.Favor(amount),
      PositionedLocation(Location.FavorBank(suit)),
      PositionedLocation(Location.PlayArea(player))))
  }

  final case class Secrets(player: PlayerId, amount: Int) extends Gain {
    override val primitives: Vector[PrimitiveOperation] = Vector(Move(
      Piece.Secrets(amount),
      PositionedLocation(Location.SharedBank),
      PositionedLocation(Location.PlayArea(player))))
  }

  final case class Warbands(player: PlayerId, kind: ForceKind, amount: Int)
      extends Gain {
    override val primitives: Vector[PrimitiveOperation] = Vector(Move(
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
  override val primitives: Vector[PrimitiveOperation] = Vector(move)
}

/** Returns warbands to their matching bank. Imperial warbands use the
  * Chancellor's bank; bandits use the shared bandit bank.
  */
final case class Kill(warbands: Piece.Warbands,
    from: PositionedLocation) extends CoreOperation {
  override val primitives: Vector[PrimitiveOperation] = Vector(Move(
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

  override val primitives: Vector[PrimitiveOperation] = Vector(Move(
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

  override val primitives: Vector[PrimitiveOperation] = Vector(
    Move(removed, at,
      PositionedLocation(Location.WarbandBank(removed.kind))),
    Move(replacements,
      PositionedLocation(Location.WarbandBank(replacements.kind)), at))
  override val simultaneous: Boolean = true
}

/** Flips a card faceup without triggering When Played powers. */
final case class Reveal(card: CardId, at: Location)
    extends CoreOperation {
  override val primitives: Vector[PrimitiveOperation] =
    Vector(Flip(card, at, Orientation.FaceUp))
}

/** Kills a warband that belongs to the player issuing the instruction. */
final case class Sacrifice(player: PlayerId,
    warbands: Piece.Warbands, from: PositionedLocation)
    extends CoreOperation {
  private val kill = Kill(warbands, from)
  override val primitives: Vector[PrimitiveOperation] = kill.primitives
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
  override val primitives: Vector[PrimitiveOperation] = Vector(first, second)
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
  override val primitives: Vector[PrimitiveOperation] = Vector(move)
}
