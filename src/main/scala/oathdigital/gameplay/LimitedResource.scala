package oathdigital.gameplay

/** Plan-time clamps for supply-capped effects. `clamp` bounds a
  * requested amount of a limited resource (for example favor held in a suit
  * bank) by what is actually available.
  */
object LimitedResource {
  def clamp(available: Int, requested: Int): Int =
    math.min(math.max(0, available), math.max(0, requested))
}
