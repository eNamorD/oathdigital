package oathdigital.protocol

private[protocol] object CommandIntentCodec:
  import GameIntent._
  import CommandJsonSupport._

  def encode(intent: GameIntent): ujson.Obj = intent match
    case BeginRest => tagged("beginRest")
    case Undo => tagged("undo")
    case Redo => tagged("redo")
    case UsePower(power, source) => tagged("usePower", "powerId" -> power,
      "source" -> CommandNestedCodecs.encodeStartArgWire(source))
    case StartWalker(action, startArgs) =>
      tagged("startWalker", "action" -> action,
        "startArgs" -> ujson.Arr.from(startArgs.map(
          CommandNestedCodecs.encodeStartArgWire)))
    case RollWalker(pool) => tagged("rollWalker", "pool" -> pool)
    case ResolveWalker(id, payload) => tagged("resolveWalker", "decisionId" -> id,
      "payload" -> CommandNestedCodecs.encodeDecisionAnswerWire(payload))
    case RequestConsent(id, target) => tagged("requestConsent",
      "decisionId" -> id,
      "target" -> CommandNestedCodecs.encodeStartArgWire(target))
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
