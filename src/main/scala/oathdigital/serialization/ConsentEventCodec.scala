package oathdigital.serialization

import oathdigital.model._
import oathdigital.model.OathEvent.{ConsentAnswered, ConsentPreferenceSet,
  ConsentPreferencesCleared, ConsentRequestWithdrawn, ConsentRequested}

/** The consent facts (consent design, "Events"). */
private[serialization] trait ConsentEventCodec { this: GameEventJsonSupport =>
  import GameEventWire._

  protected final val consentDiscriminator
      : PartialFunction[OathEvent, String] =
      case _: ConsentRequested => ConsentRequestedType
      case _: ConsentAnswered => ConsentAnsweredType
      case _: ConsentRequestWithdrawn => ConsentRequestWithdrawnType
      case _: ConsentPreferenceSet => ConsentPreferenceSetType
      case _: ConsentPreferencesCleared => ConsentPreferencesClearedType

  protected final val consentEncoder: PartialFunction[OathEvent, ujson.Value] =
      case ConsentRequested(request) => ujson.Obj(
        "requester" -> request.requester.value,
        "decisionId" -> request.decisionId,
        "target" -> DecisionAnswerCodec.encodeRef(request.target),
        "asks" -> ujson.Arr.from(request.asks.map(ask => ujson.Obj(
          "granter" -> ask.granter.value,
          "powers" -> stringArray(ask.powers.map(_.value))))))
      case ConsentAnswered(granter, granted) =>
        ujson.Obj("granter" -> granter.value, "granted" -> granted)
      case ConsentRequestWithdrawn(requester) =>
        ujson.Obj("requester" -> requester.value)
      case ConsentPreferenceSet(granter, power, requester, setting) =>
        ujson.Obj("granter" -> granter.value, "powerId" -> power.value,
          "requester" -> requester.value, "setting" -> setting.key)
      case ConsentPreferencesCleared(granter, power) =>
        ujson.Obj("granter" -> granter.value, "powerId" -> power.value)

  protected final def consentDecode(eventType: String, payload: ujson.Value,
      path: String): Option[Either[WireError, OathEvent]] =
    def player(name: String) = PlayerId(payload(name).str)
    val decoder: PartialFunction[String, Either[WireError, OathEvent]] =
        case ConsentRequestedType =>
          DecisionAnswerCodec.decodeRef(payload("target"), s"$path.target")
            .map(target => ConsentRequested(ConsentRequest(player("requester"),
              payload("decisionId").str, target,
              payload("asks").arr.toVector.map(ask => ConsentAsk(
                PlayerId(ask("granter").str),
                ask("powers").arr.toVector.map(power => PowerId(power.str))))
            )))
        case ConsentAnsweredType =>
          Right(ConsentAnswered(player("granter"), payload("granted").bool))
        case ConsentRequestWithdrawnType =>
          Right(ConsentRequestWithdrawn(player("requester")))
        case ConsentPreferenceSetType =>
          val key = payload("setting").str
          ConsentSetting.fromKey(key).toRight(WireError.InvalidValue(
            s"$path.setting", s"unknown consent setting '$key'"))
            .map(ConsentPreferenceSet(player("granter"),
              PowerId(payload("powerId").str), player("requester"), _))
        case ConsentPreferencesClearedType =>
          Right(ConsentPreferencesCleared(player("granter"),
            PowerId(payload("powerId").str)))
    decoder.lift(eventType)
}
