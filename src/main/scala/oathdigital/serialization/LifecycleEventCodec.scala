package oathdigital.serialization

import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.gameplay.OathEvent._

private[serialization] trait LifecycleEventCodec { this: GameEventJsonSupport =>
  import GameEventWire._
  import WireError._

  protected final val lifecycleDiscriminator: PartialFunction[OathEvent, String] = {
      case _: FirstGameStarted => FirstGameStartedType
      case _: GamePawnPlaced => PawnPlacedType
      case _: StartingAdviserChosen => AdviserChosenType
      case FirstGameCompleted => FirstGameCompletedType
      case _: WealthTaken => TakeWealthType
      case _: WakeEnded => WakeEndedType
      case _: RestStarted => RestStartedType
      case _: LeagueTreatyDecisionStarted => LeagueTreatyDecisionStartedType
      case _: LeagueTreatyResolved => LeagueTreatyResolvedType
      case _: LeagueTreatyDeclined => LeagueTreatyDeclinedType
      case _: RestCompleted => RestCompletedType
      case _: IgnoredRulesRecorded => IgnoredRulesRecordedType
  }

  protected final val lifecycleEncoder: PartialFunction[OathEvent, ujson.Value] = {
      case FirstGameStarted(plan) => encodePlan(plan)
      case GamePawnPlaced(playerId, siteId) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "siteId" -> siteId.value
        )
      case StartingAdviserChosen(playerId, adviserId) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "adviserId" -> adviserId.value
        )
      case FirstGameCompleted => ujson.Obj()
      case WealthTaken(playerId, siteId, resource) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "siteId" -> siteId.value,
          "resource" -> (resource match {
            case WakeResource.Favor => "favor"
            case WakeResource.Secret => "secret"
          })
        )
      case WakeEnded(playerId) =>
        ujson.Obj("playerId" -> playerId.value)
      case RestStarted(playerId) => ujson.Obj("playerId" -> playerId.value)
      case value: LeagueTreatyDecisionStarted => ujson.Obj(
        "restActorPlayerId" -> value.restActor.value,
        "decisionId" -> value.decision.value,
        "powerId" -> value.powerId.value,
        "source" -> encodeTreatySource(value.source),
        "decisionOwnerPlayerId" -> value.decisionOwner.value,
        "remaining" -> ujson.Arr.from(value.remaining.map(encodeRestInvocation)),
        "eligibleSources" -> ujson.Arr.from(
          value.eligibleSources.map(encodeFavorSource)),
        "legalBanks" -> stringArray(value.legalBanks.map(_.key)))
      case value: LeagueTreatyResolved => ujson.Obj(
        "restActorPlayerId" -> value.restActor.value,
        "decisionId" -> value.decision.value,
        "powerId" -> value.powerId.value,
        "source" -> encodeTreatySource(value.source),
        "decisionOwnerPlayerId" -> value.decisionOwner.value,
        "allocations" -> ujson.Arr.from(value.allocations.map(allocation =>
          ujson.Obj("source" -> encodeFavorSource(allocation.source),
            "amount" -> allocation.amount))),
        "destinationBank" -> value.destinationBank.key)
      case value: LeagueTreatyDeclined => ujson.Obj(
        "restActorPlayerId" -> value.restActor.value,
        "decisionId" -> value.decision.value,
        "powerId" -> value.powerId.value,
        "source" -> encodeTreatySource(value.source),
        "decisionOwnerPlayerId" -> value.decisionOwner.value)
      case RestCompleted(playerId, favor, secrets, supply, active, round, limited) =>
        ujson.Obj(
          "playerId" -> playerId.value,
          "returnedFavor" -> ujson.Obj.from(favor.toVector.sortBy(_._1.key)
            .map { case (suit, amount) => suit.key -> ujson.Num(amount) }),
          "returnedSecrets" -> secrets,
          "refreshedSupply" -> supply,
          "postRestActivePlayerId" -> active.value,
          "completedRound" -> round,
          "usurperLimited" -> limited
        )
      case IgnoredRulesRecorded(player, action, diagnostics) => ujson.Obj(
        "playerId" -> player.value,
        "action" -> action.key,
        "diagnostics" -> ujson.Arr.from(diagnostics.map(d => ujson.Obj(
          "source" -> d.source.stableKey,
          "handlerId" -> d.handlerId,
          "timing" -> d.timing.key,
          "reason" -> d.reason))))
  }

  protected final def lifecycleDecode(eventType: String, payload: ujson.Value,
      path: String, envelopeCatalog: CatalogRef): Option[Either[WireError, OathEvent]] = {
    val decoder: PartialFunction[String, Either[WireError, OathEvent]] = {
        case FirstGameStartedType =>
          decodePlan(payload, path).flatMap { plan =>
            if (plan.catalog == envelopeCatalog)
              Right(FirstGameStarted(plan))
            else
              Left(
                CatalogMismatch(
                  s"$path.catalog",
                  envelopeCatalog,
                  plan.catalog
                )
              )
          }
        case PawnPlacedType =>
          Right(
            GamePawnPlaced(
              PlayerId(payload("playerId").str),
              SiteId(payload("siteId").str)
            )
          )
        case AdviserChosenType =>
          Right(
            StartingAdviserChosen(
              PlayerId(payload("playerId").str),
              DenizenId(payload("adviserId").str)
            )
          )
        case FirstGameCompletedType => Right(FirstGameCompleted)
        case TakeWealthType =>
          val resource = payload("resource").str match {
            case "favor" => Right(WakeResource.Favor)
            case "secret" => Right(WakeResource.Secret)
            case other => Left(InvalidValue(
              s"$path.resource",
              s"unknown wealth resource '$other'"
            ))
          }
          resource.map(WealthTaken(
            PlayerId(payload("playerId").str),
            SiteId(payload("siteId").str),
            _
          ))
        case WakeEndedType =>
          Right(WakeEnded(PlayerId(payload("playerId").str)))
        case RestStartedType =>
          Right(RestStarted(PlayerId(payload("playerId").str)))
        case LeagueTreatyDecisionStartedType => for {
          source <- decodeTreatySource(payload("source"), s"$path.source")
          remaining <- traverse(payload("remaining").arr.toVector)(value =>
            decodeRestInvocation(value, s"$path.remaining"))
          eligible <- traverse(payload("eligibleSources").arr.toVector)(value =>
            decodeFavorSource(value, s"$path.eligibleSources"))
          banks <- traverse(payload("legalBanks").arr.toVector)(value =>
            decodeSuit(value.str, s"$path.legalBanks"))
        } yield LeagueTreatyDecisionStarted(
          PlayerId(payload("restActorPlayerId").str),
          DecisionId(payload("decisionId").str), PowerId(payload("powerId").str),
          source, PlayerId(payload("decisionOwnerPlayerId").str), remaining,
          eligible, banks)
        case LeagueTreatyResolvedType => for {
          source <- decodeTreatySource(payload("source"), s"$path.source")
          allocations <- traverse(payload("allocations").arr.toVector) { value =>
            for {
              source <- decodeFavorSource(value("source"),
                s"$path.allocations.source")
              amount <- safeIntField(value.obj, "amount", s"$path.allocations")
            } yield FavorAllocation(source, amount)
          }
          bank <- decodeSuit(payload("destinationBank").str,
            s"$path.destinationBank")
        } yield LeagueTreatyResolved(
          PlayerId(payload("restActorPlayerId").str),
          DecisionId(payload("decisionId").str), PowerId(payload("powerId").str),
          source, PlayerId(payload("decisionOwnerPlayerId").str), allocations, bank)
        case LeagueTreatyDeclinedType => decodeTreatySource(payload("source"),
          s"$path.source").map(source => LeagueTreatyDeclined(
          PlayerId(payload("restActorPlayerId").str),
          DecisionId(payload("decisionId").str), PowerId(payload("powerId").str),
          source, PlayerId(payload("decisionOwnerPlayerId").str)))
        case RestCompletedType =>
          val favorObject = payload("returnedFavor").obj
          val favor = favorObject.toVector.map { case (key, value) =>
            Suit.all.find(_.key == key).toRight(InvalidValue(
              s"$path.returnedFavor.$key", "unknown suit")).flatMap { suit =>
              safeInt(value, s"$path.returnedFavor.$key").map(suit -> _)
            }
          }
          for {
            entries <- favor.foldLeft[Either[WireError, Vector[(Suit, Int)]]](
              Right(Vector.empty)) {
              case (Right(acc), Right(entry)) => Right(acc :+ entry)
              case (Left(error), _) => Left(error)
              case (_, Left(error)) => Left(error)
            }
            returnedSecrets <- safeIntField(payload.obj, "returnedSecrets", path)
            refreshedSupply <- safeIntField(payload.obj, "refreshedSupply", path)
            completedRound <- safeIntField(payload.obj, "completedRound", path)
            limited = payload("usurperLimited").bool
          } yield RestCompleted(
            PlayerId(payload("playerId").str), entries.toMap,
            returnedSecrets, refreshedSupply,
            PlayerId(payload("postRestActivePlayerId").str), completedRound, limited)
        case IgnoredRulesRecordedType => for {
          action <- MajorActionKind.fromKey(payload("action").str).toRight(
            InvalidValue(s"$path.action", "unknown major action"))
          diagnostics <- payload("diagnostics").arr.toVector.foldLeft[
            Either[WireError, Vector[IgnoredRuleDiagnostic]]](Right(Vector.empty)) {
            case (Right(acc), value) => for {
              source <- RuleSourceRef.parse(value("source").str).toRight(
                InvalidValue(s"$path.diagnostics.source", "unknown rule source"))
              timing <- Vector(RuleTiming.Start, RuleTiming.Persistent,
                RuleTiming.Trigger, RuleTiming.BattlePlan, RuleTiming.Inherent)
                .find(_.key == value("timing").str).toRight(
                  InvalidValue(s"$path.diagnostics.timing", "unknown timing"))
            } yield acc :+ IgnoredRuleDiagnostic(source, value("handlerId").str,
              action, timing, value("reason").str)
            case (left @ Left(_), _) => left
          }
        } yield IgnoredRulesRecorded(PlayerId(payload("playerId").str), action,
          diagnostics)
    }
    decoder.lift(eventType)
  }

  private def encodeTreatySource(value: SiteDenizenTarget) = ujson.Obj(
    "siteId" -> value.siteId.value, "denizenId" -> value.denizenId.value)

  private def decodeTreatySource(value: ujson.Value, path: String) =
    Right(SiteDenizenTarget(SiteId(value("siteId").str),
      DenizenId(value("denizenId").str)))

  private def encodeRestInvocation(value: RestPowerInvocationRef) = ujson.Obj(
    "powerId" -> value.powerId.value,
    "source" -> encodeTreatySource(value.source),
    "decisionOwnerPlayerId" -> value.decisionOwner.value)

  private def decodeRestInvocation(value: ujson.Value, path: String) =
    decodeTreatySource(value("source"), s"$path.source").map(source =>
      RestPowerInvocationRef(PowerId(value("powerId").str), source,
        PlayerId(value("decisionOwnerPlayerId").str)))

  private def encodeFavorSource(value: SiteFavorSource): ujson.Value = value match {
    case SiteFavorSource.Denizen(site, id) => ujson.Obj(
      "kind" -> "denizen", "siteId" -> site.value, "cardId" -> id.value)
    case SiteFavorSource.Edifice(site, id) => ujson.Obj(
      "kind" -> "edifice", "siteId" -> site.value, "cardId" -> id.value)
    case SiteFavorSource.Relic(site, slot) => ujson.Obj(
      "kind" -> "relic-slot", "siteId" -> site.value, "slot" -> slot)
  }

  private def decodeFavorSource(value: ujson.Value, path: String)
      : Either[WireError, SiteFavorSource] = value("kind").str match {
    case "denizen" => Right(SiteFavorSource.Denizen(
      SiteId(value("siteId").str), DenizenId(value("cardId").str)))
    case "edifice" => Right(SiteFavorSource.Edifice(
      SiteId(value("siteId").str), EdificeId(value("cardId").str)))
    case "relic-slot" => safeIntField(value.obj, "slot", path).map(slot =>
      SiteFavorSource.Relic(SiteId(value("siteId").str), slot))
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown Rest favor source '$other'"))
  }

}
