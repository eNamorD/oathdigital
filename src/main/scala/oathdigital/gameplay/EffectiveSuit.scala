package oathdigital.gameplay

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.catalog.holding.SpitefulMirrorCard
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.model._

object MarriageCard extends Denizen(DenizenId("148"), "Marriage", Suit.Hearth) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.marriage"),
    persistent = true, cost = Cost.free,
    text = "Marriage counts as two [suit-hearth] advisers, but only " +
      "counts as one toward your adviser limit.")
  val powers: Vector[PrintedPower] = Vector(power)

object PledgeToDiscordCard extends Denizen(DenizenId("223"), "Pledge to Discord", Suit.Discord) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.pledge-to-discord"),
    persistent = true, cost = Cost.free,
    text = "Your advisers all have the [suit-discord] suit instead of " +
      "their printed suit. _Marriage counts as two [suit-discord]._")
  val powers: Vector[PrintedPower] = Vector(power)

/** A card's suit as the rules see it (catalog batch 8, slice 8).
  *
  * Pledge to Discord makes every faceup adviser of its holder Discord, while
  * Pledge itself is faceup among those advisers. A faceup Marriage counts
  * as two advisers of its suit, any other faceup adviser as one, and a
  * facedown adviser has no suit (catalog batch 5 rulings, "Counts as applies
  * everywhere"). Every reader of an adviser's suit reads `of`, including
  * the bank the favor on an adviser goes back to. Every reader of the card
  * a Trade or Muster draws on reads `traded`, which also sees Spiteful
  * Mirror.
  *
  * It lives in `gameplay`, below `actions` and `powers`, because the
  * operation pipeline reads `pledged` too. It names the cards its rules
  * read: Marriage and Pledge to Discord, whose registered powers add
  * nothing, and Spiteful Mirror, whose pick it reads.
  */
object EffectiveSuit:
  /** How many advisers of its suit `card` counts as, faceup. */
  def weight(card: CardId): Int = if card == MarriageCard.id then 2 else 1

  /** Whether `card` is a faceup adviser of a player who has Pledge to
    * Discord faceup. It needs no catalog: such a card is Discord whatever
    * it prints. */
  def pledged(ready: ReadyGame, card: CardId): Boolean =
    ready.game.current.players.exists(player =>
      faceUp(player, card) && faceUp(player, PledgeToDiscordCard.id))

  /** `card`'s suit: Discord when [[pledged]], otherwise its printed suit. */
  def of(catalog: ExecutableCatalog, ready: ReadyGame, card: CardId)
      : Option[Suit] =
    if pledged(ready, card) then Some(Suit.Discord)
    else catalog.suitOf(card)

  /** `player`'s faceup advisers of `suit`, each by its weight. */
  def count(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      suit: Suit): Int =
    ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.advisers).collect {
        case DenizenState(id, Orientation.FaceUp, _)
            if of(catalog, ready, id).contains(suit) => weight(id)
      }.sum

  /** Whether the walk's answers pick Spiteful Mirror, for a Trade or a
    * Muster. */
  def mirrored(answered: Vector[Answered]): Boolean =
    Vector(PowerWindow.TradeModifierSelection,
      PowerWindow.MusterModifierSelection).exists(window =>
      ModifierPicks.isPicked(answered, window, SpitefulMirrorCard.id))

  /** The suit of the card `ref` names, as the Trade or Muster that draws on
    * it reads it: Discord once Spiteful Mirror is picked, otherwise
    * [[of]]. `None` for a reference that names no card. */
  def traded(catalog: ExecutableCatalog, ready: ReadyGame,
      answered: Vector[Answered], ref: DecisionOptionRef): Option[Suit] =
    cardOf(ref).flatMap(card =>
      if mirrored(answered) then Some(Suit.Discord)
      else of(catalog, ready, card))

  private def cardOf(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Edifice(id) => Some(id)
    case _ => None

  private def faceUp(player: PlayerState, card: CardId): Boolean =
    player.advisers.exists {
      case DenizenState(held, Orientation.FaceUp, _) => held == card
      case _ => false
    }
