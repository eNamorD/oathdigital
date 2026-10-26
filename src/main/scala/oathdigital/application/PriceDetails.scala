package oathdigital.application

import oathdigital.model.OptionPrice

/** The cost of an option, worded for display: what a player weighs when they
  * choose a battle plan. Every part is a line of its own, in a fixed order, and
  * a part that costs nothing is not mentioned.
  */
private[application] object PriceDetails:
  def of(price: OptionPrice): Vector[String] = Vector(
    line(price.favor, "favor", "favor", ""),
    line(price.secrets, "secret", "secrets", ""),
    if price.secretsX then "Cost: X secrets" else "",
    line(price.favorBurnt, "favor", "favor", " burnt"),
    line(price.secretsBurnt, "secret", "secrets", " burnt"),
    if price.warbands == 0 then ""
    else s"Cost: sacrifice ${price.warbands} warband" +
      (if price.warbands == 1 then "" else "s")).filter(_.nonEmpty)

  private def line(count: Int, one: String, many: String, suffix: String)
      : String =
    if count == 0 then ""
    else s"Cost: $count ${if count == 1 then one else many}$suffix"

  /** What a pick paid, worded for its row: "1 favor", "2 secrets burnt",
    * "1 warband sacrificed", or "Free" when it paid nothing. */
  def paid(price: OptionPrice): Vector[String] =
    val parts = paidParts(price)
    if parts.isEmpty then Vector("Free") else parts

  /** What several picks paid together: "1 favor, 1 secret", or "Nothing". */
  def paidInAll(prices: Vector[OptionPrice]): String =
    val parts = paidParts(prices.foldLeft(OptionPrice())((total, price) =>
      OptionPrice(total.favor + price.favor, total.secrets + price.secrets,
        total.favorBurnt + price.favorBurnt,
        total.secretsBurnt + price.secretsBurnt,
        total.warbands + price.warbands)))
    if parts.isEmpty then "Nothing" else parts.mkString(", ")

  private def paidParts(price: OptionPrice): Vector[String] = Vector(
    amount(price.favor, "favor", "favor", ""),
    amount(price.secrets, "secret", "secrets", ""),
    amount(price.favorBurnt, "favor", "favor", " burnt"),
    amount(price.secretsBurnt, "secret", "secrets", " burnt"),
    amount(price.warbands, "warband", "warbands", " sacrificed"))
    .filter(_.nonEmpty)

  private def amount(count: Int, one: String, many: String, suffix: String)
      : String =
    if count == 0 then "" else s"$count ${if count == 1 then one else many}$suffix"
