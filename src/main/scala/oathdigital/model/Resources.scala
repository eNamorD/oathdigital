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
final case class SupplyTrack(supply: Int) {
  require(supply >= 0, "Supply must be non-negative")
  require(
    supply <= SupplyTrack.Maximum,
    s"Supply must not exceed ${SupplyTrack.Maximum}"
  )
}

object SupplyTrack {
  val Maximum: Int = 7
  val empty: SupplyTrack = SupplyTrack(0)
  val full: SupplyTrack = SupplyTrack(Maximum)
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
    baseSupply: Int
) {
  require(baseSupply >= 0, "base Supply must be non-negative")
}

/**
 * Board-specific Supply refresh data.
 *
 * At Rest, find the matching warband bank, then add unspent Supply and cap at
 * `maximum`. Citizens bypass this table and copy the Chancellor's result.
 */
final case class SupplyRules(
    maximum: Int,
    refreshBands: Vector[SupplyRefreshBand]
) {
  require(maximum >= 0, "maximum Supply must be non-negative")
  require(
    maximum <= SupplyTrack.Maximum,
    s"maximum Supply must not exceed ${SupplyTrack.Maximum}"
  )
  require(refreshBands.nonEmpty, "Supply refresh bands must not be empty")
  require(
    refreshBands.forall(_.baseSupply <= maximum),
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

  def baseSupplyFor(warbandsInBank: Int): Option[Int] = {
    require(warbandsInBank >= 0, "warbands in bank must be non-negative")
    refreshBands
      .find(_.warbandsInBank.contains(warbandsInBank))
      .map(_.baseSupply)
  }

  def refresh(warbandsInBank: Int, unspentSupply: Int): Option[SupplyTrack] = {
    require(unspentSupply >= 0, "unspent Supply must be non-negative")
    baseSupplyFor(warbandsInBank).map { baseSupply =>
      SupplyTrack(
        math.min(
          maximum,
          baseSupply + unspentSupply
        )
      )
    }
  }
}

/** The six suits. `all` is the rules order (Discord, Arcane, Order, Hearth,
  * Beast, Nomad); some powers resolve suit by suit in that order, so never
  * sort by `key` where order matters. */
sealed trait Suit extends Product with Serializable {
  def key: String
}
object Suit {
  case object Discord extends Suit {
    override val key: String = "discord"
  }
  case object Arcane extends Suit {
    override val key: String = "arcane"
  }
  case object Order extends Suit {
    override val key: String = "order"
  }
  case object Hearth extends Suit {
    override val key: String = "hearth"
  }
  case object Beast extends Suit {
    override val key: String = "beast"
  }
  case object Nomad extends Suit {
    override val key: String = "nomad"
  }

  val all: Vector[Suit] =
    Vector(Discord, Arcane, Order, Hearth, Beast, Nomad)

  /** Safe parse for untrusted (catalog or wire) input. */
  def fromKey(key: String): Option[Suit] = all.find(_.key == key)
}
