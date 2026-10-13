package oathdigital.serialization

import oathdigital.model._

/** Wire spelling of the two Oathkeeper operations, `set-oathkeeper` (the
  * title's holder) and `set-oathkeeper-goal` (the campaign's goal). Split out
  * of `WalkerOperationCodec` for headroom.
  */
private[serialization] trait OathkeeperWire:
  this: GameEventJsonSupport =>
  import WireError._

  protected final def encodeSetOathkeeper(operation: SetOathkeeper)
      : ujson.Value = ujson.Obj("kind" -> "set-oathkeeper",
    "holderPlayerId" -> operation.holder.fold[ujson.Value](ujson.Null)(p =>
      ujson.Str(p.value)))

  protected final def decodeSetOathkeeper(value: ujson.Value): SetOathkeeper =
    SetOathkeeper(value("holderPlayerId") match
      case ujson.Null => None
      case other => Some(PlayerId(other.str)))

  protected final def encodeSetOathkeeperGoal(operation: SetOathkeeperGoal)
      : ujson.Value = ujson.Obj("kind" -> "set-oathkeeper-goal",
    "goal" -> operation.goal.key)

  protected final def decodeSetOathkeeperGoal(value: ujson.Value,
      path: String): Either[WireError, SetOathkeeperGoal] =
    val key = value("goal").str
    OathkeeperGoal.all.find(_.key == key).toRight(
      InvalidValue(s"$path.goal", s"unknown Oathkeeper goal '$key'"))
      .map(SetOathkeeperGoal.apply)
