package oathdigital.server

import scala.util.control.NonFatal

import oathdigital.application.{
  BootstrapParticipant,
  CardDecisionResolution,
  GameCommand,
  FirstGameBootstrapConfig,
  GameProjection
}
import oathdigital.model._
import oathdigital.serialization.GameEventWire
import oathdigital.setup.{PlayerColor, TradeResource, WakeResource}

final case class GameCommandRequest(
    expectedNextSequence: Long,
    command: GameCommand
)
final case class FirstGameBootstrapRequest(
    expectedNextSequence: Long,
    config: FirstGameBootstrapConfig
)

final case class HttpInputError(path: String, message: String)

object GameHttpWire {
  def decodeBootstrap(
      json: String
  ): Either[HttpInputError, FirstGameBootstrapRequest] =
    try {
      for {
        root <- objectValue(ujson.read(json), "$")
        expectedValue <- field(root, "expectedNextSequence", "$")
        expected <- safeSequence(expectedValue, "$.expectedNextSequence")
        participantsValue <- field(root, "participants", "$")
        participantValues <- arrayValue(
          participantsValue,
          "$.participants"
        )
        participants <- traverse(participantValues.zipWithIndex) {
          case (value, index) =>
            val path = s"$$.participants[$index]"
            for {
              obj <- objectValue(value, path)
              player <- stringField(obj, "playerId", path)
              lineage <- stringField(obj, "lineageId", path)
              color <- stringField(obj, "color", path)
            } yield BootstrapParticipant(
              PlayerId(player),
              LineageId(lineage),
              PlayerColor(color)
            )
        }
        firstPlayer <- stringField(root, "firstPlayer", "$")
      } yield FirstGameBootstrapRequest(
        expected,
        FirstGameBootstrapConfig(participants, PlayerId(firstPlayer))
      )
    } catch {
      case NonFatal(error) =>
        Left(HttpInputError(
          "$",
          Option(error.getMessage).getOrElse("malformed JSON")
        ))
    }

  def decodeCommand(json: String): Either[HttpInputError,
    GameCommandRequest] =
    try {
      for {
        root <- objectValue(ujson.read(json), "$")
        expectedValue <- field(root, "expectedNextSequence", "$")
        expected <- safeSequence(expectedValue, "$.expectedNextSequence")
        commandValue <- field(root, "command", "$")
        commandObject <- objectValue(commandValue, "$.command")
        command <- decodeCommandObject(commandObject, "$.command")
      } yield GameCommandRequest(expected, command)
    } catch {
      case NonFatal(error) =>
        Left(HttpInputError(
          "$",
          Option(error.getMessage).getOrElse("malformed JSON")
        ))
    }

  def encodeProjection(projection: GameProjection): String =
    ujson.write(
      ujson.Obj(
        "gameId" -> projection.gameId,
        "nextSequence" -> ujson.Num(projection.nextSequence.toDouble),
        "phase" -> projection.phase,
        "activeParticipantId" -> projection.activeParticipantId
          .fold[ujson.Value](ujson.Null)(ujson.Str(_)),
        "players" -> ujson.Arr.from(projection.players.map { player =>
          ujson.Obj(
            "playerId" -> player.playerId,
            "displayName" -> player.displayName,
            "role" -> player.role,
            "colorToken" -> player.colorToken
          )
        }),
        "world" -> ujson.Arr.from(projection.world.map { region =>
          ujson.Obj(
            "regionId" -> region.regionId,
            "discardCount" -> region.discardCount,
            "discardTopCardKind" -> region.discardTopCardKind.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
            "sites" -> ujson.Arr.from(region.sites.map { site =>
              ujson.Obj(
                "siteId" -> site.siteId,
                "label" -> site.label,
                "looseFavor" -> site.looseFavor,
                "looseSecrets" -> site.looseSecrets,
                "denizenCapacity" -> site.denizenCapacity,
                "relicCapacity" -> site.relicCapacity,
                "defense" -> site.defense,
                "recoverDifficulty" -> site.recoverDifficulty.fold[ujson.Value](ujson.Null)(ujson.Num(_)),
                "forgeCost" -> site.forgeCost.fold[ujson.Value](ujson.Null)(cost => ujson.Obj(
                  "favor" -> cost.favor, "secrets" -> cost.secrets)),
                "powers" -> ujson.Arr.from(site.powers.map(power => ujson.Obj(
                  "kind" -> power.kind, "label" -> power.label,
                  "description" -> power.description.fold[ujson.Value](ujson.Null)(ujson.Str(_))))),
                "forces" -> site.forces.fold[ujson.Value](ujson.Null)(forces =>
                  ujson.Obj(
                    "forceKind" -> forces.forceKind,
                    "count" -> forces.count,
                    "rulerKind" -> forces.rulerKind,
                    "rulerPlayerId" -> forces.rulerPlayerId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
                    "label" -> forces.label,
                    "colorToken" -> forces.colorToken
                  )),
                "denizens" -> ujson.Arr.from(site.denizens.map { denizen =>
                  ujson.Obj(
                    "denizenId" -> denizen.cardId,
                    "label" -> denizen.label,
                    "details" -> denizen.details.fold[ujson.Value](ujson.Null)(encodeCardDetails)
                  )
                }),
                "relics" -> ujson.Obj(
                  "facedownCount" -> site.relics.facedownCount
                )
              )
            })
          )
        }),
        "pawnLocations" -> ujson.Arr.from(
          projection.pawnLocations.map { pawn =>
            ujson.Obj(
              "playerId" -> pawn.playerId,
              "siteId" -> pawn.siteId
            )
          }
        ),
        "legalControls" -> ujson.Arr.from(
          projection.legalControls.map(ujson.Str(_))
        ),
        "ready" -> projection.ready,
        "completed" -> projection.completed,
        "oathkeeper" -> projection.oathkeeper.fold[ujson.Value](ujson.Null) { oath =>
          ujson.Obj("goal" -> oath.goal,
            "holderPlayerId" -> oath.holderPlayerId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
            "side" -> oath.side, "usurperLimited" -> oath.usurperLimited,
            "winnerPlayerId" -> oath.winnerPlayerId.fold[ujson.Value](ujson.Null)(ujson.Str(_)))
        },
        "worldDeckCount" -> projection.worldDeckCount,
        "worldDeckTopCardKind" -> projection.worldDeckTopCardKind.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
        "activePlayerResources" -> projection.activePlayerResources.fold[
          ujson.Value](ujson.Null)(resources => ujson.Obj(
            "favor" -> resources.favor,
            "faceUpSecrets" -> resources.faceUpSecrets,
            "faceDownSecrets" -> resources.faceDownSecrets,
            "supply" -> resources.supply
          )),
        "currentSiteResources" -> projection.currentSiteResources.fold[
          ujson.Value](ujson.Null)(resources => ujson.Obj(
            "siteId" -> resources.siteId,
            "favor" -> resources.favor,
            "secrets" -> resources.secrets
          )),
        "actionSelectionOpen" -> projection.actionSelectionOpen,
        "actionFamilies" -> ujson.Arr.from(
          projection.actionFamilies.map(ujson.Str(_))),
        "legalTravelDestinations" -> ujson.Arr.from(
          projection.legalTravelDestinations.map { destination =>
            ujson.Obj(
              "siteId" -> destination.siteId,
              "supplyCost" -> destination.supplyCost
            )
          }),
        "legalSearchSources" -> ujson.Arr.from(
          projection.legalSearchSources.map { source => ujson.Obj(
            "kind" -> source.kind,
            "region" -> source.region.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
            "supplyCost" -> source.supplyCost
          )}),
        "legalMusters" -> ujson.Arr.from(projection.legalMusters.map { option =>
          ujson.Obj("target" -> ujson.Obj("kind" -> option.targetKind,
            "id" -> option.targetId), "label" -> option.label, "suit" -> option.suit,
            "supplyCost" -> option.supplyCost,
            "warbandsGained" -> option.warbandsGained)
        }),
        "legalTrades" -> ujson.Arr.from(projection.legalTrades.map { option =>
          ujson.Obj("target" -> ujson.Obj("kind" -> option.targetKind,
            "id" -> option.targetId), "label" -> option.label, "suit" -> option.suit,
            "resource" -> option.resource, "supplyCost" -> option.supplyCost,
            "gained" -> option.gained)
        }),
        "boardTargetActions" -> ujson.Arr.from(
          projection.boardTargetActions.map { action => ujson.Obj(
            "actionKind" -> action.actionKind,
            "prompt" -> action.prompt,
            "minimum" -> action.minimum,
            "maximum" -> action.maximum,
            "autoActivate" -> action.autoActivate,
            "candidates" -> ujson.Arr.from(action.candidates.map { candidate =>
              ujson.Obj("target" -> encodeBoardTarget(candidate.target),
                "label" -> candidate.label,
                "details" -> ujson.Arr.from(
                  candidate.details.map(ujson.Str(_))))
            }))
          }),
        "pendingCardDecision" -> projection.pendingCardDecision.fold[ujson.Value](ujson.Null) {
          decision => ujson.Obj(
            "decisionId" -> decision.decisionId,
            "kind" -> decision.kind,
            "actorPlayerId" -> decision.actorPlayerId,
            "prompt" -> decision.prompt,
            "instructions" -> ujson.Arr.from(decision.instructions.map(ujson.Str(_))),
            "cards" -> ujson.Arr.from(decision.cards.map(encodeCardDetails)),
            "keepMinimum" -> decision.keepMinimum,
            "keepMaximum" -> decision.keepMaximum,
            "orderingRequired" -> decision.orderingRequired,
            "resolutionsByCard" -> ujson.Obj.from(decision.resolutionsByCard.map {
              case (cardId, resolutions) => cardId -> ujson.Arr.from(resolutions.map { resolution =>
                ujson.Obj("kind" -> resolution.kind,
                  "orientation" -> resolution.orientation.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
                  "replacementRequired" -> resolution.replacementRequired,
                  "replacementTargets" -> ujson.Arr.from(
                    resolution.replacementTargets.map(encodeCardDetails)))
              })
            })
          )
        },
        "recover" -> projection.recover.fold[ujson.Value](ujson.Null) { recover =>
          ujson.Obj("decisionId" -> recover.decisionId,
            "dice" -> ujson.Arr.from(recover.dice.map(ujson.Str(_))),
            "shields" -> recover.shields, "difficulty" -> recover.difficulty,
            "supplySpent" -> recover.supplySpent,
            "supplyRemaining" -> recover.supplyRemaining,
            "canAddDice" -> recover.canAddDice, "canStop" -> recover.canStop)
        },
        "playerBoards" -> ujson.Arr.from(projection.playerBoards.map { board => ujson.Obj(
          "playerId" -> board.playerId, "warbands" -> board.warbands,
          "favor" -> board.favor, "faceUpSecrets" -> board.faceUpSecrets,
          "faceDownSecrets" -> board.faceDownSecrets, "supply" -> board.supply,
          "pawnSiteId" -> board.pawnSiteId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
          "advisers" -> ujson.Arr.from(board.advisers.map(encodeCardDetails)),
          "relics" -> ujson.Arr.from(board.relics.map(encodeCardDetails)),
          "revealedVision" -> board.revealedVision.fold[ujson.Value](ujson.Null)(encodeCardDetails)
        )})
      )
    )

  private def encodeCardDetails(card: oathdigital.application.CardDetailsProjection): ujson.Obj =
    ujson.Obj("cardId" -> card.cardId, "cardKind" -> card.cardKind,
      "name" -> card.name, "suit" -> card.suit.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
      "restrictions" -> card.restrictions.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
      "rulesText" -> card.rulesText.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
      "orientation" -> card.orientation.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
      "side" -> card.side.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
      "favor" -> card.favor, "secrets" -> card.secrets,
      "relicValue" -> card.relicValue.fold[ujson.Value](ujson.Null)(ujson.Num(_)),
      "defense" -> card.defense.fold[ujson.Value](ujson.Null)(ujson.Num(_)),
      "hidden" -> card.hidden)

  private def encodeBoardTarget(
      target: oathdigital.application.BoardTargetRefProjection
  ): ujson.Obj = target match {
    case oathdigital.application.BoardTargetRefProjection.Site(siteId) =>
      ujson.Obj("kind" -> "site", "siteId" -> siteId)
    case oathdigital.application.BoardTargetRefProjection.SiteCard(
        siteId, cardKind, cardId) =>
      ujson.Obj("kind" -> "site-card", "siteId" -> siteId,
        "cardKind" -> cardKind, "cardId" -> cardId)
    case oathdigital.application.BoardTargetRefProjection.PlayerAdviser(
        playerId, cardId) =>
      ujson.Obj("kind" -> "player-adviser", "playerId" -> playerId,
        "cardId" -> cardId)
    case oathdigital.application.BoardTargetRefProjection.PlayerRelic(
        playerId, relicId) =>
      ujson.Obj("kind" -> "player-relic", "playerId" -> playerId,
        "relicId" -> relicId)
  }

  def encodeError(code: String, message: String): String =
    ujson.write(ujson.Obj("error" -> code, "message" -> message))

  private def decodeCommandObject(
      obj: ujson.Obj,
      path: String
  ): Either[HttpInputError, GameCommand] =
    stringField(obj, "type", path).flatMap {
      case "begin" =>
        Left(HttpInputError(
          s"$path.type",
          "begin is not accepted here; use the development bootstrap endpoint"
        ))
      case "placePawn" =>
        for {
          player <- stringField(obj, "playerId", path)
          site <- stringField(obj, "siteId", path)
        } yield GameCommand.PlacePawn(PlayerId(player), SiteId(site))
      case "chooseAdviser" =>
        Left(HttpInputError(s"$path.type", "use resolveCardDecision"))
      case "takeWealth" =>
        for {
          player <- stringField(obj, "playerId", path)
          resourceName <- stringField(obj, "resource", path)
          resource <- resourceName match {
            case "favor" => Right(WakeResource.Favor)
            case "secret" => Right(WakeResource.Secret)
            case other => Left(HttpInputError(
              s"$path.resource",
              s"unknown wealth resource '$other'"
            ))
          }
        } yield GameCommand.TakeWealth(PlayerId(player), resource)
      case "endWake" =>
        stringField(obj, "playerId", path).map(player =>
          GameCommand.EndWake(PlayerId(player)))
      case "beginRest" =>
        stringField(obj, "playerId", path).map(player =>
          GameCommand.BeginRest(PlayerId(player)))
      case "finishRest" =>
        stringField(obj, "playerId", path).map(player =>
          GameCommand.FinishRest(PlayerId(player)))
      case "travel" =>
        for {
          player <- stringField(obj, "playerId", path)
          destination <- stringField(obj, "destinationSiteId", path)
        } yield GameCommand.Travel(
          PlayerId(player), SiteId(destination))
      case "muster" =>
        for {
          _ <- exactFields(obj, Set("type", "playerId", "target"), path)
          player <- stringField(obj, "playerId", path)
          targetValue <- field(obj, "target", path)
          target <- decodeEconomyTarget(targetValue, s"$path.target")
        } yield GameCommand.Muster(PlayerId(player), target)
      case "trade" =>
        for {
          _ <- exactFields(obj, Set("type", "playerId", "target", "resource"), path)
          player <- stringField(obj, "playerId", path)
          targetValue <- field(obj, "target", path)
          target <- decodeEconomyTarget(targetValue, s"$path.target")
          value <- stringField(obj, "resource", path)
          resource <- value match {
            case "favor" => Right(TradeResource.Favor)
            case "secret" => Right(TradeResource.Secret)
            case other => Left(HttpInputError(s"$path.resource",
              s"unknown Trade resource '$other'"))
          }
        } yield GameCommand.Trade(PlayerId(player), target, resource)
      case "beginSearch" =>
        for {
          _ <- exactFields(obj, Set("type", "playerId", "source", "region"), path)
          player <- stringField(obj, "playerId", path)
          kind <- stringField(obj, "source", path)
          source <- decodeSearchSource(kind, obj.value.get("region"), path)
        } yield GameCommand.BeginSearch(PlayerId(player), source)
      case "beginRecover" =>
        stringField(obj, "playerId", path).map(p => GameCommand.BeginRecover(PlayerId(p)))
      case "addRecoverDice" => for {
        p <- stringField(obj, "playerId", path)
        d <- stringField(obj, "decisionId", path)
      } yield GameCommand.AddRecoverDice(PlayerId(p), DecisionId(d))
      case "stopRecover" => for {
        p <- stringField(obj, "playerId", path)
        d <- stringField(obj, "decisionId", path)
      } yield GameCommand.StopRecover(PlayerId(p), DecisionId(d))
      case "completeSearch" =>
        Left(HttpInputError(s"$path.type", "use resolveCardDecision"))
      case "resolveCardDecision" =>
        for {
          _ <- exactFields(obj, Set("type", "playerId", "decisionId", "resolution"), path)
          player <- stringField(obj, "playerId", path)
          decision <- stringField(obj, "decisionId", path)
          value <- field(obj, "resolution", path)
          resolution <- decodeDecisionResolution(value, s"$path.resolution")
        } yield GameCommand.ResolveCardDecision(
          PlayerId(player), DecisionId(decision), resolution)
      case other =>
        Left(HttpInputError(
          s"$path.type",
          s"unknown command type '$other'"
        ))
    }

  private def decodeDecisionResolution(value: ujson.Value, path: String)
      : Either[HttpInputError, CardDecisionResolution] = objectValue(value, path).flatMap { obj =>
    stringField(obj, "kind", path).flatMap {
      case "starting-adviser" => for {
        _ <- exactFields(obj, Set("kind", "adviserId"), path)
        id <- stringField(obj, "adviserId", path)
      } yield CardDecisionResolution.StartingAdviser(DenizenId(id))
      case "search" => for {
        _ <- exactFields(obj, Set("kind", "kept", "discardedInOrder", "placement"), path)
        keptValue <- field(obj, "kept", path)
        kept <- decodeWorldCard(keptValue, s"$path.kept")
        discardedValue <- field(obj, "discardedInOrder", path)
        discardedArray <- arrayValue(discardedValue, s"$path.discardedInOrder")
        discarded <- traverse(discardedArray.zipWithIndex) { case (card, index) =>
          decodeWorldCard(card, s"$path.discardedInOrder[$index]")
        }
        placementValue <- field(obj, "placement", path)
        placement <- decodePlacement(placementValue, s"$path.placement")
      } yield CardDecisionResolution.Search(kept, discarded, placement)
      case "take-facedown-relic" => for {
        _ <- exactFields(obj, Set("kind", "relicId"), path)
        id <- stringField(obj, "relicId", path)
      } yield CardDecisionResolution.TakeFacedownRelic(RelicId(id))
      case other => Left(HttpInputError(s"$path.kind", s"unknown decision resolution '$other'"))
    }
  }

  private def safeSequence(
      value: ujson.Value,
      path: String
  ): Either[HttpInputError, Long] =
    value match {
      case ujson.Num(number)
          if number.isFinite && number == Math.rint(number) &&
            number >= 0 &&
            number <= GameEventWire.MaxSafeSequence =>
        Right(number.toLong)
      case _: ujson.Num =>
        Left(HttpInputError(
          path,
          s"must be an integer between 0 and " +
            GameEventWire.MaxSafeSequence
        ))
      case _ => Left(HttpInputError(path, "expected a number"))
    }

  private def decodeEconomyTarget(value: ujson.Value, path: String)
      : Either[HttpInputError, EconomyTargetRef] = for {
    obj <- objectValue(value, path)
    _ <- exactFields(obj, Set("kind", "id"), path)
    kind <- stringField(obj, "kind", path)
    id <- stringField(obj, "id", path)
    target <- kind match {
      case "denizen" => Right(EconomyTargetRef.Denizen(DenizenId(id)))
      case "edifice" => Right(EconomyTargetRef.Edifice(EdificeId(id)))
      case other => Left(HttpInputError(s"$path.kind",
        s"unsupported Economy target kind '$other'"))
    }
  } yield target

  private def exactFields(
      obj: ujson.Obj,
      allowed: Set[String],
      path: String
  ): Either[HttpInputError, Unit] =
    obj.value.keys.find(key => !allowed.contains(key)) match {
      case Some(key) => Left(HttpInputError(s"$path.$key", "field is not accepted"))
      case None => Right(())
    }

  private def decodeSearchSource(kind: String, region: Option[ujson.Value], path: String)
      : Either[HttpInputError, SearchSource] = kind match {
    case "world" => Right(SearchSource.WorldDeck)
    case "regional-discard" => region match {
      case Some(ujson.Str(value)) => Region.all.find(_.key == value)
        .map(r => SearchSource.RegionalDiscard(r): SearchSource)
        .toRight(HttpInputError(s"$path.region", "unknown region"))
      case _ => Left(HttpInputError(s"$path.region", "region is required"))
    }
    case _ => Left(HttpInputError(s"$path.source", "unknown Search source"))
  }

  private def decodeWorldCard(value: ujson.Value, path: String)
      : Either[HttpInputError, WorldCardId] = objectValue(value, path).flatMap { obj =>
    for {
      kind <- stringField(obj, "kind", path)
      id <- stringField(obj, "id", path)
      card <- kind match {
        case "denizen" => Right(DenizenId(id): WorldCardId)
        case "vision" => Right(VisionId(id): WorldCardId)
        case _ => Left(HttpInputError(s"$path.kind", "unknown world card kind"))
      }
    } yield card
  }

  private def decodeCard(value: ujson.Value, path: String): Either[HttpInputError, CardId] =
    objectValue(value, path).flatMap { obj => for {
      kind <- stringField(obj, "kind", path)
      id <- stringField(obj, "id", path)
      card <- kind match {
        case "denizen" => Right(DenizenId(id): CardId)
        case "vision" => Right(VisionId(id): CardId)
        case "edifice" => Right(EdificeId(id): CardId)
        case _ => Left(HttpInputError(s"$path.kind", "unsupported replacement card kind"))
      }
    } yield card }

  private def decodePlacement(value: ujson.Value, path: String)
      : Either[HttpInputError, SearchPlacement] = objectValue(value, path).flatMap { obj =>
    def replacement = obj.value.get("replace") match {
      case None | Some(ujson.Null) => Right(None)
      case Some(value) => decodeCard(value, s"$path.replace").map(Some(_))
    }
    stringField(obj, "kind", path).flatMap {
      case "discard" => Right(SearchPlacement.Discard)
      case "site" => replacement.map(SearchPlacement.Site)
      case "adviser-face-up" => replacement.map(SearchPlacement.Adviser(
        Orientation.FaceUp, _))
      case "adviser-face-down" => replacement.map(SearchPlacement.Adviser(
        Orientation.FaceDown, _))
      case _ => Left(HttpInputError(s"$path.kind", "unknown Search placement"))
    }
  }

  private def stringField(
      obj: ujson.Obj,
      name: String,
      path: String
  ): Either[HttpInputError, String] =
    field(obj, name, path).flatMap(stringValue(_, s"$path.$name"))

  private def stringValue(
      value: ujson.Value,
      path: String
  ): Either[HttpInputError, String] =
    value match {
      case ujson.Str(text) if text.trim.nonEmpty => Right(text)
      case ujson.Str(_) => Left(HttpInputError(path, "must not be blank"))
      case _ => Left(HttpInputError(path, "expected a string"))
    }

  private def objectValue(
      value: ujson.Value,
      path: String
  ): Either[HttpInputError, ujson.Obj] =
    value match {
      case obj: ujson.Obj => Right(obj)
      case _ => Left(HttpInputError(path, "expected an object"))
    }

  private def arrayValue(
      value: ujson.Value,
      path: String
  ): Either[HttpInputError, Vector[ujson.Value]] =
    value match {
      case array: ujson.Arr => Right(array.value.toVector)
      case _ => Left(HttpInputError(path, "expected an array"))
    }

  private def field(
      obj: ujson.Obj,
      name: String,
      path: String
  ): Either[HttpInputError, ujson.Value] =
    obj.value.get(name).toRight(
      HttpInputError(s"$path.$name", "field is required")
    )

  private def traverse[A, B](
      values: Vector[A]
  )(f: A => Either[HttpInputError, B])
      : Either[HttpInputError, Vector[B]] =
    values.foldLeft[Either[HttpInputError, Vector[B]]](Right(Vector.empty)) {
      case (Right(accumulated), value) =>
        f(value).map(accumulated :+ _)
      case (failure @ Left(_), _) => failure
    }
}
