package oathdigital.model

final case class Tokens(favor: Int, secrets: Int) {
  require(favor >= 0, "favor must be non-negative")
  require(secrets >= 0, "secrets must be non-negative")

  def isEmpty: Boolean = favor == 0 && secrets == 0
}

object Tokens {
  val empty: Tokens = Tokens(0, 0)
}

/** Remaining spendable Supply, not a persisted marker coordinate. */
final case class Supply(remaining: Int) {
  require(remaining >= 0, "Supply must be non-negative")
  require(
    remaining <= Supply.Maximum,
    s"Supply must not exceed ${Supply.Maximum}"
  )
}

object Supply {
  val Maximum: Int = 7
  val empty: Supply = Supply(0)
  val full: Supply = Supply(Maximum)
}

final case class InclusiveIntRange(minimum: Int, maximum: Int) {
  require(minimum >= 0, "range minimum must be non-negative")
  require(maximum >= minimum, "range maximum must not be below its minimum")

  def contains(value: Int): Boolean =
    value >= minimum && value <= maximum

  def overlaps(other: InclusiveIntRange): Boolean =
    contains(other.minimum) || other.contains(minimum)
}

final case class SupplyRefreshBand(
    warbandsInBank: InclusiveIntRange,
    baseSupply: Supply
)

/**
 * Board-specific Supply refresh data.
 *
 * At Rest, find the matching warband band, then add unspent Supply and cap at
 * `maximum`. Citizens bypass this table and copy the Chancellor's result.
 */
final case class SupplyRules(
    maximum: Supply,
    refreshBands: Vector[SupplyRefreshBand]
) {
  require(refreshBands.nonEmpty, "Supply refresh bands must not be empty")
  require(
    refreshBands.forall(_.baseSupply.remaining <= maximum.remaining),
    "base Supply must not exceed the board maximum"
  )
  require(
    refreshBands.indices.forall { left =>
      refreshBands.indices.forall { right =>
        left == right ||
        !refreshBands(left).warbandsInBank.overlaps(
          refreshBands(right).warbandsInBank
        )
      }
    },
    "Supply refresh bands must not overlap"
  )

  def baseSupplyFor(warbandsInBank: Int): Option[Supply] = {
    require(warbandsInBank >= 0, "warbands in bank must be non-negative")
    refreshBands
      .find(_.warbandsInBank.contains(warbandsInBank))
      .map(_.baseSupply)
  }

  def refresh(warbandsInBank: Int, unspentSupply: Supply): Option[Supply] =
    baseSupplyFor(warbandsInBank).map { base =>
      Supply(
        math.min(
          maximum.remaining,
          base.remaining + unspentSupply.remaining
        )
      )
    }
}
