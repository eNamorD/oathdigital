package oathdigital.protocol

private[protocol] object CommandIntentCodec {
  import GameIntent._
  import CommandJsonSupport._

  def encode(intent: GameIntent): ujson.Obj = intent match {
    case EndWake => tagged("endWake")
    case BeginRest => tagged("beginRest")
    case FinishRest => tagged("finishRest")
    case UsePower(power, source) => tagged("usePower", "powerId" -> power,
      "source" -> CommandNestedCodecs.encodeStartArgWire(source))
    case PeekSiteRelics => tagged("peekSiteRelics")
    case RevealOwnedRelic(id) => tagged("revealOwnedRelic", "relicId" -> id)
    case MoveWarbands(toSite, amount) => tagged("moveWarbands", "toSite" -> toSite, "amount" -> amount)
    case StartWalker(action, modifiers, startArgs) =>
      tagged("startWalker", "action" -> action,
        "modifiers" -> ujson.Arr.from(modifiers.map(ujson.Str(_))),
        "startArgs" -> ujson.Arr.from(startArgs.map(
          CommandNestedCodecs.encodeStartArgWire)))
    case RollWalker(pool) => tagged("rollWalker", "pool" -> pool)
    case ResolveWalker(id, payload) => tagged("resolveWalker", "decisionId" -> id,
      "payload" -> CommandNestedCodecs.encodeDecisionAnswerWire(payload))
  }

  def decode(value: ujson.Value, path: String): Either[ProtocolDecodeFailure, GameIntent] =
    obj(value, path).flatMap { value =>
      rejectActorFields(value, path).flatMap(_ => string(value, "type", path).flatMap(
        CommandIntentDecoders.decode(_, value, path)))
    }

  private def tagged(kind: String, values: (String, ujson.Value)*): ujson.Obj =
    ujson.Obj.from(("type" -> ujson.Str(kind)) +: values)
}
