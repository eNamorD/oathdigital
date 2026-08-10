package oathdigital.server

import scala.util.control.NonFatal

import oathdigital.application.{
  BootstrapParticipant,
  FirstGameCommand,
  FirstGameBootstrapConfig,
  FirstGameProjection
}
import oathdigital.model._
import oathdigital.serialization.FirstGameEventWire
import oathdigital.setup.{PlayerColor, WakeResource}

final case class FirstGameCommandRequest(
    expectedNextSequence: Long,
    command: FirstGameCommand
)
final case class FirstGameBootstrapRequest(
    expectedNextSequence: Long,
    config: FirstGameBootstrapConfig
)

final case class HttpInputError(path: String, message: String)

object FirstGameHttpWire {
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
    FirstGameCommandRequest] =
    try {
      for {
        root <- objectValue(ujson.read(json), "$")
        expectedValue <- field(root, "expectedNextSequence", "$")
        expected <- safeSequence(expectedValue, "$.expectedNextSequence")
        commandValue <- field(root, "command", "$")
        commandObject <- objectValue(commandValue, "$.command")
        command <- decodeCommandObject(commandObject, "$.command")
      } yield FirstGameCommandRequest(expected, command)
    } catch {
      case NonFatal(error) =>
        Left(HttpInputError(
          "$",
          Option(error.getMessage).getOrElse("malformed JSON")
        ))
    }

  def encodeProjection(projection: FirstGameProjection): String =
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
            "sites" -> ujson.Arr.from(region.sites.map { site =>
              ujson.Obj(
                "siteId" -> site.siteId,
                "label" -> site.label,
                "looseFavor" -> site.looseFavor,
                "looseSecrets" -> site.looseSecrets,
                "denizenCapacity" -> site.denizenCapacity,
                "relicCapacity" -> site.relicCapacity,
                "denizens" -> ujson.Arr.from(site.denizens.map { denizen =>
                  ujson.Obj(
                    "denizenId" -> denizen.cardId,
                    "label" -> denizen.label
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
        "privateAdviserChoices" -> ujson.Arr.from(
          projection.privateAdviserChoices.map { choice =>
            ujson.Obj(
              "adviserId" -> choice.adviserId,
              "label" -> choice.label
            )
          }
        ),
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
        "pendingSearch" -> projection.pendingSearch.fold[ujson.Value](ujson.Null) {
          search => ujson.Obj(
            "decisionId" -> search.decisionId,
            "drawnCards" -> ujson.Arr.from(search.drawnCards.map { card => ujson.Obj(
              "cardId" -> card.cardId,
              "cardKind" -> card.cardKind,
              "label" -> card.label,
              "legalPlacements" -> ujson.Arr.from(card.legalPlacements.map(ujson.Str(_)))
            )}),
            "replaceableAdvisers" -> ujson.Arr.from(
              search.replaceableAdvisers.map(ujson.Str(_))),
            "replaceableSiteCards" -> ujson.Arr.from(
              search.replaceableSiteCards.map(ujson.Str(_)))
          )
        }
      )
    )

  def encodeError(code: String, message: String): String =
    ujson.write(ujson.Obj("error" -> code, "message" -> message))

  private def decodeCommandObject(
      obj: ujson.Obj,
      path: String
  ): Either[HttpInputError, FirstGameCommand] =
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
        } yield FirstGameCommand.PlacePawn(PlayerId(player), SiteId(site))
      case "chooseAdviser" =>
        for {
          player <- stringField(obj, "playerId", path)
          adviser <- stringField(obj, "adviserId", path)
        } yield FirstGameCommand.ChooseAdviser(
          PlayerId(player),
          DenizenId(adviser)
        )
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
        } yield FirstGameCommand.TakeWealth(PlayerId(player), resource)
      case "endWake" =>
        stringField(obj, "playerId", path).map(player =>
          FirstGameCommand.EndWake(PlayerId(player)))
      case "travel" =>
        for {
          player <- stringField(obj, "playerId", path)
          destination <- stringField(obj, "destinationSiteId", path)
        } yield FirstGameCommand.Travel(
          PlayerId(player), SiteId(destination))
      case "beginSearch" =>
        for {
          _ <- exactFields(obj, Set("type", "playerId", "source", "region"), path)
          player <- stringField(obj, "playerId", path)
          kind <- stringField(obj, "source", path)
          source <- decodeSearchSource(kind, obj.value.get("region"), path)
        } yield FirstGameCommand.BeginSearch(PlayerId(player), source)
      case "completeSearch" =>
        for {
          _ <- exactFields(obj, Set("type", "playerId", "decisionId", "kept",
            "discardedInOrder", "placement"), path)
          player <- stringField(obj, "playerId", path)
          decision <- stringField(obj, "decisionId", path)
          kept <- worldCardField(obj, "kept", path)
          discardedValue <- field(obj, "discardedInOrder", path)
          discardedArray <- arrayValue(discardedValue, s"$path.discardedInOrder")
          discarded <- traverse(discardedArray.zipWithIndex) { case (value, index) =>
            decodeWorldCard(value, s"$path.discardedInOrder[$index]")
          }
          placementValue <- field(obj, "placement", path)
          placement <- decodePlacement(placementValue, s"$path.placement")
        } yield FirstGameCommand.CompleteSearch(
          PlayerId(player), DecisionId(decision), kept, discarded, placement)
      case other =>
        Left(HttpInputError(
          s"$path.type",
          s"unknown command type '$other'"
        ))
    }

  private def safeSequence(
      value: ujson.Value,
      path: String
  ): Either[HttpInputError, Long] =
    value match {
      case ujson.Num(number)
          if number.isFinite && number == Math.rint(number) &&
            number >= 0 &&
            number <= FirstGameEventWire.MaxSafeSequence =>
        Right(number.toLong)
      case _: ujson.Num =>
        Left(HttpInputError(
          path,
          s"must be an integer between 0 and " +
            FirstGameEventWire.MaxSafeSequence
        ))
      case _ => Left(HttpInputError(path, "expected a number"))
    }

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

  private def worldCardField(obj: ujson.Obj, name: String, path: String) =
    field(obj, name, path).flatMap(decodeWorldCard(_, s"$path.$name"))

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
