package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.MarriageCard
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution}
import oathdigital.model._

/** Marriage (card 148, adviser-only, locked): "Marriage counts as two Hearth
  * advisers, but only counts as one toward your adviser limit."
  *
  * The rule is `AdviserSuits.weight`, which every count of a player's
  * advisers by suit reads, and the limit counts every adviser once. This
  * power hooks no window. It is registered so the card counts as
  * implemented. It writes no line.
  */
case object Marriage extends ContributingPower:
  val id: PowerId = MarriageCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
