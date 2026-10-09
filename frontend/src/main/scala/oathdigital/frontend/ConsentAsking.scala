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

  /** "Ask Red for consent to ignore Narrow Pass?" */
  def question(value: GameProjection, ask: ConsentAskState): String =
    s"Ask ${listed(ask.granterPlayerIds.map(name(value, _)))} for consent " +
      s"to ignore ${listed(ask.powers)}?"

  /** What choosing an askable option of `decisionId` does: the
    * confirmation, whose Yes sends the request. Without control, nothing. */
  def asker(decisionId: String, value: GameProjection, canControl: Boolean,
      controls: TableControls): (DecisionOptionState, dom.html.Element) => Unit =
    (option, origin) =>
      if canControl then option.consent.foreach(ask =>
        CardInspection.openConfirm(question(value, ask), () =>
          controls.submitCommand(GameCommand.RequestConsent(decisionId,
            WalkerStartArgWire(option.kind, option.id))), origin))
