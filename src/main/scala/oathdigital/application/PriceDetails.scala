package oathdigital.application

import oathdigital.model.OptionPrice

/** The cost of an option, worded for display: what a player weighs when they
  * choose a battle plan. Every part is a line of its own, in a fixed order, and
  * a part that costs nothing is not mentioned.
  */
private[application] object PriceDetails {
  def of(price: OptionPrice): Vector[String] = Vector(
    line(price.favor, "favor", "favor", ""),
    line(price.secrets, "secret", "secrets", ""),
    line(price.favorBurnt, "favor", "favor", " burnt"),
    line(price.secretsBurnt, "secret", "secrets", " burnt"),
    if (price.warbands == 0) ""
    else s"Cost: sacrifice ${price.warbands} warband" +
      (if (price.warbands == 1) "" else "s")).filter(_.nonEmpty)

  private def line(count: Int, one: String, many: String, suffix: String)
      : String =
    if (count == 0) ""
    else s"Cost: $count ${if (count == 1) one else many}$suffix"
}
