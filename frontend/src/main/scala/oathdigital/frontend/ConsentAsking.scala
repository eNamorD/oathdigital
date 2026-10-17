package oathdigital.frontend

import oathdigital.protocol.{GameIntent => GameCommand, WalkerStartArgWire}
import org.scalajs.dom

/** Asking for consent from the table (consent design, user story steps 1
  * and 2). The projection names players by id; this words their display
  * names. */
private[frontend] object ConsentAsking:
  def name(value: GameProjection, playerId: String): String =
    value.players.find(_.playerId == playerId).map(_.displayName)
      .getOrElse(playerId)

  /** "A", "A and B", "A, B and C". */
  def listed(words: Vector[String]): String = words match
    case Vector() => ""
    case Vector(only) => only
    case _ => s"${words.init.mkString(", ")} and ${words.last}"

  /** "Ask Red for consent to ignore Narrow Pass?", or with alternatives,
    * "Ask Blue or Red for consent to ignore Toll Roads?": the required
    * granters, then the alternatives joined by "or". A granter asked to pay
    * reads "Ask Blue to pay for Toll Roads?", after any ask to ignore. */
  def question(value: GameProjection, ask: ConsentAskState): String =
    val alternatives = ask.alternatives.map(alt => name(value,
      alt.granterPlayerId))
    val asked = ask.granterPlayerIds.map(name(value, _)) ++
      Option.when(alternatives.nonEmpty)(alternatives.mkString(" or "))
    val powers = (ask.powers ++ ask.alternatives.flatMap(_.powers)).distinct
    val ignoring = Option.when(asked.nonEmpty)(
      s"${listed(asked)} for consent to ignore ${listed(powers)}")
    // "Blue to pay for Toll Roads" (catalog batch 8, slice 15).
    val payers = ask.payers.map(payer => name(value, payer.granterPlayerId))
    val paying = Option.when(payers.nonEmpty)(s"${listed(payers)} to pay " +
      s"for ${listed(ask.payers.flatMap(_.powers).distinct)}")
    s"Ask ${listed(ignoring.toVector ++ paying)}?"

  /** One way to ask: its button, and the alternatives' granters it sends
    * (empty for every ask). */
  final case class AskChoice(label: String, granters: Vector[String])

  /** "Ask Blue", "Ask Red", then "Ask both", or "Ask all" past two: one
    * per alternative, each with the required granters too (catalog batch
    * 8, slice 15). Empty without alternatives. */
  def choices(value: GameProjection, ask: ConsentAskState)
      : Vector[AskChoice] =
    if ask.alternatives.isEmpty then Vector.empty
    else
      val required = ask.granterPlayerIds ++
        ask.payers.map(_.granterPlayerId)
      val one = ask.alternatives.map(alt => AskChoice(
        s"Ask ${listed((required :+ alt.granterPlayerId).map(name(value, _)))}",
        Vector(alt.granterPlayerId)))
      val all = AskChoice(if required.isEmpty && ask.alternatives.size == 2
        then "Ask both" else "Ask all", Vector.empty)
      if ask.alternatives.size > 1 then one :+ all else one

  /** What choosing an askable option of `decisionId` does: the
    * confirmation, whose Yes sends the request, or with alternatives a
    * choice of whom to ask. Without control, nothing. */
  def asker(decisionId: String, value: GameProjection, canControl: Boolean,
      controls: TableControls): (DecisionOptionState, dom.html.Element) => Unit =
    (option, origin) =>
      if canControl then option.consent.foreach { ask =>
        def send(granters: Vector[String]): Unit =
          controls.submitCommand(GameCommand.RequestConsent(decisionId,
            WalkerStartArgWire(option.kind, option.id), granters))
        choices(value, ask) match
          case Vector() => CardInspection.openConfirm(question(value, ask),
            () => send(Vector.empty), origin)
          case found => CardInspection.openChoices(question(value, ask),
            found.map(choice => choice.label -> (() =>
              send(choice.granters))), origin)
      }
