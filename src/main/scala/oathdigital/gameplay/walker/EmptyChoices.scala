package oathdigital.gameplay.walker

import oathdigital.model.{Decide, DecisionOption, DecisionOptionRef,
  DecisionQuery, OptionReason}

/** The Continue screen of a choose-one passed when empty (catalog batch 8,
  * slice 10, part 2b). When the search leaves it no option, the live walk
  * parks it anyway, so the player sees why nothing can be chosen: every
  * option shown unavailable with its reason, a Continue button that passes
  * it, and a heading that keeps the power's name: "{power}: nothing to
  * choose", or [[Heading]] when the question names none. An option the
  * search hid takes the reason
  * the walk found for it, or [[Reason]]. An ask still asks, so a grant
  * opens its option; after a refusal Continue remains.
  */
private[walker] object EmptyChoices:
  val Heading: String = "Nothing to choose"
  val Reason: OptionReason = OptionReason.Written("Cannot be chosen now")
  val ContinueOption: DecisionOption =
    DecisionOption.Button(Decide.Pass, "Continue")

  /** `decide` with every available option removed: the cut a search that
    * hid them all would make. */
  def nothingLeft(decide: Decide): Decide = decide.query match
    case one: DecisionQuery.ChooseOne => decide.copy(query = one.copy(
      options = one.options.filterNot(_.available)))
    case _ => decide

  /** The Continue screen's heading for a question headed `heading`: the
    * power's name before its colon, then "nothing to choose". */
  def headingFor(heading: Option[String]): String =
    heading.flatMap(text => text.indexOf(": ") match
      case -1 => None
      case at => Some(s"${text.take(at)}: nothing to choose"))
      .getOrElse(Heading)

  /** `decide` as its Continue screen: each available option shown
    * unavailable with its `hidden` reason or [[Reason]], each `asks` reason
    * over an option already unavailable, then Continue, under
    * [[headingFor]]. */
  def offered(decide: Decide, hidden: Map[DecisionOptionRef, OptionReason],
      asks: Map[DecisionOptionRef, OptionReason]): Decide =
    decide.query match
      case one: DecisionQuery.ChooseOne =>
        val shown = one.options.map(option =>
          if option.available then DecisionOption.Unavailable(option,
            hidden.getOrElse(option.ref, Reason))
          else option)
        WalkerSearch.reasoned(decide.copy(query = one.copy(
          options = shown :+ ContinueOption,
          heading = Some(headingFor(one.heading)))), asks)
      case _ => decide
