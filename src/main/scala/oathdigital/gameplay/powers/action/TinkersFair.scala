package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.powers.RelicDraws
import oathdigital.model._

object TinkersFairCard extends Denizen(DenizenId("13"), "Tinker's Fair", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.tinker-s-fair"),
    persistent = false, cost = Cost(favor = 3),
    text = "**ACTION:** Draw a relic from the relic deck.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Tinker's Fair (card 13, site-only), ACTION: place 3 favor on this card,
  * then draw a relic and take it facedown, as Dowsing Sticks does. An empty
  * relic deck pays the cost and does nothing else.
  */
case object TinkersFair extends PaidAction(TinkersFairCard.power):
  override def noteKeys: Vector[NoteKey] =
    Vector(RelicDraws.drew, RelicDraws.emptyDeck)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    Right(Sequence(RelicDraws.drawSteps(id, player, source)))
