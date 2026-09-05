package oathdigital.gameplay

/** Plan-time clamps for supply-capped effects. "As much as possible" is
  * resolved here, before an exact operation is built, so the executor never
  * needs a best-effort mode (engine redesign decision 3).
  */
object SupplyResource {
  def favor(available: Int, requested: Int): Int =
    math.min(math.max(0, available), math.max(0, requested))
}
