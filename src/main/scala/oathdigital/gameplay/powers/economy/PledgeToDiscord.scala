package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.PledgeToDiscordCard
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution}
import oathdigital.model._

/** Pledge to Discord (card 223, adviser-only, locked): "Your advisers all
  * have the [suit-discord] suit instead of their printed suit. Marriage
  * counts as two [suit-discord]."
  *
  * The rule is `EffectiveSuit.of`, which every reader of an adviser's suit
  * reads. This power hooks no window. It is registered so the card counts
  * as implemented. It writes no line.
  */
case object PledgeToDiscord extends ContributingPower:
  val id: PowerId = PledgeToDiscordCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
