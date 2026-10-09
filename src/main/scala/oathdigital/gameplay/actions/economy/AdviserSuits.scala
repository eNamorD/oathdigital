package oathdigital.gameplay.actions.economy

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.model._

object MarriageCard extends Denizen(DenizenId("148"), "Marriage", Suit.Hearth) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.marriage"),
    persistent = true, cost = Cost.free,
    text = "Marriage counts as two [suit-hearth] advisers, but only " +
      "counts as one toward your adviser limit.")
  val powers: Vector[PrintedPower] = Vector(power)

/** A player's advisers counted by suit (catalog batch 5 rulings, "Counts as
  * applies everywhere"). A faceup Marriage counts as two advisers of its
  * suit, any other faceup adviser as one, and a facedown adviser has no
  * suit. A list of targets, or a check for having one adviser of a suit,
  * reads the cards themselves.
  *
  * It lives beside `MusterSource` because Muster and Trade read it, and
  * `gameplay/actions` never imports `gameplay/powers`. This is Marriage's
  * whole rule: its registered power adds nothing.
  */
object AdviserSuits:
  /** How many advisers of its suit `card` counts as, faceup. */
  def weight(card: CardId): Int = if card == MarriageCard.id then 2 else 1

  /** `player`'s faceup advisers of `suit`, each by its weight. */
  def count(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId,
      suit: Suit): Int =
    ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.advisers).collect {
        case DenizenState(id, Orientation.FaceUp, _)
            if catalog.suitOf(id).contains(suit) => weight(id)
      }.sum
