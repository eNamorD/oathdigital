package oathdigital.gameplay.phases.rest

import oathdigital.model.PlayerId

trait WarExhaustionRandomPort {
  def choose(candidates: Vector[PlayerId]): PlayerId
}
object WarExhaustionRandomPort {
  val random: WarExhaustionRandomPort = new WarExhaustionRandomPort {
    private val rng = new scala.util.Random()
    def choose(candidates: Vector[PlayerId]): PlayerId =
      candidates(rng.nextInt(candidates.size))
  }
}
