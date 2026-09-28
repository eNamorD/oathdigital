package oathdigital.application

/** Each site power's name and printed text (CR p. 31, NF p. 11), by the kind
  * its handler ends in: `site.riverbank.river` is a River. The six Homelands,
  * `homeland-<suit>`, share one text and are each named for their suit:
  * `homeland-beast` is the Beast Homeland. */
private[application] object SitePowerText:
  final case class Printed(label: String, text: String)

  def kindOf(handler: String): String =
    handler.split('.').lastOption.getOrElse(handler)

  def of(kind: String): Option[Printed] =
    if kind.startsWith("homeland-") then
      Some(Printed(s"${kind.stripPrefix("homeland-").capitalize} Homeland",
        homeland))
    else printed.get(kind)

  private val homeland = "There is a Homeland of each suit. When playing a " +
    "card of its Homeland suit to this site, you may discard a card from " +
    "the site first (even one of matching suit)."

  private val printed: Map[String, Printed] = Map(
    "plains" -> Printed("Plains", "This site has no power."),
    "coast" -> Printed("Coast", "Traveling from here to a Coast or Island " +
      "costs only 1 Supply and ignores other Travel modifiers (Mountain, " +
      "Pass, etc.)."),
    "island" -> Printed("Island", "This site has the Coast power. Traveling " +
      "to it costs 2 Supply more than its normal Travel cost unless you're " +
      "traveling from a Coast or Island."),
    "river" -> Printed("River", "WAKE: You may place your pawn at another " +
      "River. This is not a Travel action."),
    "mountain" -> Printed("Mountain", "Traveling to this site costs one more " +
      "Supply than its normal Travel cost. Do not add this Supply cost if " +
      "this site also has the Coast power and you're traveling to it from a " +
      "Coast."),
    "pass" -> Printed("Pass", "If your pawn is outside this region, you " +
      "cannot travel to other sites in this region or target other sites in " +
      "this region in campaigns, unless you have the consent of the Pass's " +
      "ruler. You can ignore this power if you rule the Pass. The bandits " +
      "never consent."),
    "enduring" -> Printed("Enduring", "Cards at this site are not discarded " +
      "in the Chronicle Phase during the Shape Empire step."))
