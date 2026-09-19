package oathdigital.gameplay.actions

import oathdigital.model._

object VisionRules {
  val Conquest = VisionId("vision:vision-of-conquest")
  val Sanctuary = VisionId("vision:vision-of-sanctuary")
  val Rebellion = VisionId("vision:vision-of-rebellion")
  val Faith = VisionId("vision:vision-of-faith")
  val Conspiracy = VisionId("vision:conspiracy")

  val goals: Map[VisionId, OathkeeperGoal] = Map(
    Conquest -> OathkeeperGoal.Supremacy,
    Sanctuary -> OathkeeperGoal.Protection,
    Rebellion -> OathkeeperGoal.ThePeople,
    Faith -> OathkeeperGoal.Devotion)

  def trueGoal(id: VisionId): Option[OathkeeperGoal] = goals.get(id)
}
