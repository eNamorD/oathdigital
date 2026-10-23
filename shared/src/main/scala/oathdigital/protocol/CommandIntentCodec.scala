package oathdigital.protocol

private[protocol] object CommandIntentCodec:
  import GameIntent._
  import CommandJsonSupport._

  def encode(intent: GameIntent): ujson.Obj = intent match
    case Undo => tagged("undo")
    case Redo => tagged("redo")
    case ResolveWalker(id, payload) => tagged("resolveWalker", "decisionId" -> id,
      "payload" -> CommandNestedCodecs.encodeDecisionAnswerWire(payload))
    case RequestConsent(id, target, granters) => tagged("requestConsent",
      Vector[(String, ujson.Value)]("decisionId" -> id,
        "target" -> CommandNestedCodecs.encodeStartArgWire(target)) ++
        Option.when(granters.nonEmpty)(
          "granters" -> ujson.Arr.from(granters.map(ujson.Str(_))))*)
    case AnswerConsent(choice) => tagged("answerConsent", "choice" -> choice)
    case SetConsentPreference(power, requester, setting) =>
      tagged("setConsentPreference", "powerId" -> power,
        "requesterId" -> requester, "setting" -> setting)

  def decode(value: ujson.Value, path: String): Either[ProtocolDecodeFailure, GameIntent] =
    obj(value, path).flatMap { value =>
      rejectActorFields(value, path).flatMap(_ => string(value, "type", path).flatMap(
        CommandIntentDecoders.decode(_, value, path)))
    }

  private def tagged(kind: String, values: (String, ujson.Value)*): ujson.Obj =
    ujson.Obj.from(("type" -> ujson.Str(kind)) +: values)
