package oathdigital.gameplay.powers.banner

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution}
import oathdigital.model._

object YewStaffCard extends Relic(RelicId("R40"), "Yew Staff", value = 60, defense = 2):
  val power = PrintedPower(PowerId("relic.yew-staff"),
    persistent = true, cost = Cost.free,
    text = "When using card powers, you may act as if you hold the " +
      "Darkest Secret.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Yew Staff (relic R40): "When using card powers, you may act as if you
  * hold the Darkest Secret."
  *
  * While its holder has it faceup, every card power they use that asks
  * whether they hold the Darkest Secret finds that they do
  * (`CardPowerBanners.holdsDarkestSecret`). It applies automatically and
  * writes no line. This power hooks no window. It is registered so the
  * card counts as implemented.
  */
case object YewStaff extends ContributingPower
    with CardPowerBanners.DarkestSecretOverride:
  val id: PowerId = YewStaffCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty

  def actsAsDarkestSecretHolder(ready: ReadyGame, player: PlayerId)
      : Boolean =
    ready.game.current.players.find(_.player == player)
      .exists(_.relics.exists {
        case RelicState(card, Orientation.FaceUp, _) =>
          card == YewStaffCard.id
        case _ => false
      })
