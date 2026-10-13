package oathdigital.serialization

import oathdigital.model.SecretSide

/** Wire spellings for the side of a secret move (catalog batch 8, slice 5),
  * split out of `WalkerOperationCodec` to keep it under the 800-line bound.
  * A side is written only when a move names one. */
private[serialization] trait PositionWire:
  import WireError._

  protected final def encodeSecretSide(value: SecretSide): String =
    value match
      case SecretSide.FaceUp => "face-up"
      case SecretSide.FaceDown => "face-down"

  protected final def decodeSecretSide(value: String,
      path: String): Either[WireError, SecretSide] = value match
    case "face-up" => Right(SecretSide.FaceUp)
    case "face-down" => Right(SecretSide.FaceDown)
    case other => Left(InvalidValue(path, s"unknown secret side '$other'"))

  /** `key` with `side`, when there is one. */
  protected final def sideField(key: String, side: Option[SecretSide])
      : Option[(String, ujson.Value)] =
    side.map(value => key -> ujson.Str(encodeSecretSide(value)))

  /** The side under `key`, `None` when there is none. */
  protected final def decodeSide(value: ujson.Value, key: String,
      path: String): Either[WireError, Option[SecretSide]] =
    value.obj.get(key).fold[Either[WireError, Option[SecretSide]]](
      Right(None))(raw => decodeSecretSide(raw.str, s"$path.$key")
      .map(Some(_)))
