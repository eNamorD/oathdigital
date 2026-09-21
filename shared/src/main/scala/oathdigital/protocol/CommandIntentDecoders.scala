package oathdigital.protocol

private[protocol] object CommandIntentDecoders {
  import CommandJsonSupport._
  import GameIntent._
  import ProtocolDecodeFailure._

  def decode(kind: String, value: ujson.Obj, path: String)
      : Either[ProtocolDecodeFailure, GameIntent] = kind match {
    case "placePawn" => one(value, path, "siteId")(PlacePawn)
    case "endWake" => empty(value, path, EndWake)
    case "beginRest" => empty(value, path, BeginRest)
    case "finishRest" => empty(value, path, FinishRest)
    case "usePower" => for {
      _ <- exact(value, Set("type", "powerId", "source"), path)
      power <- string(value, "powerId", path)
      source <- field(value, "source", path).flatMap(raw =>
        CommandNestedCodecs.decodeStartArgsWire(ujson.Arr(raw), s"$path.source"))
        .flatMap {
          case Vector(one) => Right(one)
          case _ => Left(InvalidValue(s"$path.source",
            "expected one power source"))
        }
    } yield UsePower(power, source)
    case "peekSiteRelics" => empty(value, path, PeekSiteRelics)
    case "revealOwnedRelic" => one(value, path, "relicId")(RevealOwnedRelic)
    case "moveWarbands" => for {
      _ <- exact(value, Set("type", "toSite", "amount"), path)
      toSite <- field(value, "toSite", path).flatMap(boolean(_, s"$path.toSite"))
      amount <- field(value, "amount", path).flatMap(integer(_, s"$path.amount"))
    } yield MoveWarbands(toSite, amount)
    case "resolveCardDecision" => for {
      _ <- exact(value, Set("type", "decisionId", "resolution"), path)
      id <- string(value, "decisionId", path)
      resolution <- field(value, "resolution", path).flatMap(CommandNestedCodecs.decodeDecision(_, s"$path.resolution"))
    } yield ResolveCardDecision(id, resolution)
    case "startWalker" => for {
      _ <- exact(value, Set("type", "action", "modifiers", "startArgs"), path)
      action <- string(value, "action", path)
      modifiers <- field(value, "modifiers", path).flatMap(strings(_, s"$path.modifiers"))
      // Optional and empty by default: every walker procedure but Travel
      // selects nothing before it starts.
      startArgs <- value.value.get("startArgs") match {
        case None => Right(Vector.empty[WalkerStartArgWire])
        case Some(args) => CommandNestedCodecs.decodeStartArgsWire(args,
          s"$path.startArgs")
      }
    } yield StartWalker(action, modifiers, startArgs)
    case "rollWalker" => one(value, path, "pool")(RollWalker)
    case "resolveWalker" => for {
      _ <- exact(value, Set("type", "decisionId", "payload"), path)
      id <- string(value, "decisionId", path)
      payload <- field(value, "payload", path).flatMap(
        CommandNestedCodecs.decodeDecisionAnswerWire(_, s"$path.payload"))
    } yield ResolveWalker(id, payload)
    case other => Left(InvalidValue(s"$path.type", s"unknown intent type '$other'"))
  }

  private def empty(value: ujson.Obj, path: String, result: GameIntent) = exact(value, Set("type"), path).map(_ => result)
  private def one(value: ujson.Obj, path: String, name: String)(f: String => GameIntent) =
    exact(value, Set("type", name), path).flatMap(_ => string(value, name, path)).map(f)
}
