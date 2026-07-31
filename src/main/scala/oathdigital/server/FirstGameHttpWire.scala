package oathdigital.server

import scala.util.control.NonFatal

import oathdigital.application.{
  FirstGameCommand,
  FirstGameProjection
}
import oathdigital.model._
import oathdigital.serialization.FirstGameEventWire
import oathdigital.setup.{
  FirstGameParticipant,
  FirstGameSetupPlan,
  PlayerColor
}

final case class FirstGameCommandRequest(
    expectedNextSequence: Long,
    command: FirstGameCommand
)

final case class HttpInputError(path: String, message: String)

object FirstGameHttpWire {
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
                "label" -> site.label
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
        )
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
        field(obj, "plan", path)
          .flatMap(decodePlan(_, s"$path.plan"))
          .map(FirstGameCommand.Begin)
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
      case other =>
        Left(HttpInputError(
          s"$path.type",
          s"unknown command type '$other'"
        ))
    }

  private def decodePlan(
      value: ujson.Value,
      path: String
  ): Either[HttpInputError, FirstGameSetupPlan] =
    for {
      obj <- objectValue(value, path)
      catalogValue <- field(obj, "catalog", path)
      catalogObject <- objectValue(catalogValue, s"$path.catalog")
      ruleset <- stringField(catalogObject, "ruleset", s"$path.catalog")
      version <- stringField(catalogObject, "version", s"$path.catalog")
      participantsValue <- field(obj, "participants", path)
      participantValues <- arrayValue(
        participantsValue,
        s"$path.participants"
      )
      participants <- traverse(participantValues.zipWithIndex) {
        case (participant, index) =>
          val itemPath = s"$path.participants[$index]"
          for {
            item <- objectValue(participant, itemPath)
            player <- stringField(item, "playerId", itemPath)
            lineage <- stringField(item, "lineageId", itemPath)
            color <- stringField(item, "color", itemPath)
          } yield FirstGameParticipant(
            PlayerId(player),
            LineageId(lineage),
            PlayerColor(color)
          )
      }
      firstPlayer <- stringField(obj, "firstPlayer", path)
      orderedSites <- stringArray(obj, "orderedSites", path)
      denizens <- stringArray(obj, "denizenOrder", path)
      worldValue <- field(obj, "worldDeckOrder", path)
      worldValues <- arrayValue(worldValue, s"$path.worldDeckOrder")
      world <- traverse(worldValues.zipWithIndex) {
        case (card, index) =>
          val itemPath = s"$path.worldDeckOrder[$index]"
          for {
            item <- objectValue(card, itemPath)
            kind <- stringField(item, "kind", itemPath)
            id <- stringField(item, "id", itemPath)
            result <- kind match {
              case "denizen" =>
                Right(DenizenId(id): WorldCardId)
              case "vision" =>
                Right(VisionId(id): WorldCardId)
              case other =>
                Left(HttpInputError(
                  s"$itemPath.kind",
                  s"unknown card kind '$other'"
                ))
            }
          } yield result
      }
      relics <- stringArray(obj, "relicOrder", path)
      homelandsValue <- field(obj, "homelandEdifices", path)
      homelandValues <- arrayValue(
        homelandsValue,
        s"$path.homelandEdifices"
      )
      homelands <- traverse(homelandValues.zipWithIndex) {
        case (entry, index) =>
          val itemPath = s"$path.homelandEdifices[$index]"
          for {
            item <- objectValue(entry, itemPath)
            site <- stringField(item, "siteId", itemPath)
            edifice <- stringField(item, "edificeId", itemPath)
          } yield SiteId(site) -> EdificeId(edifice)
      }
    } yield FirstGameSetupPlan(
      CatalogRef(ruleset, version),
      participants,
      PlayerId(firstPlayer),
      orderedSites.map(SiteId),
      denizens.map(DenizenId),
      world,
      relics.map(RelicId),
      homelands
    )

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

  private def stringArray(
      obj: ujson.Obj,
      name: String,
      path: String
  ): Either[HttpInputError, Vector[String]] =
    for {
      value <- field(obj, name, path)
      values <- arrayValue(value, s"$path.$name")
      strings <- traverse(values.zipWithIndex) {
        case (item, index) =>
          stringValue(item, s"$path.$name[$index]")
      }
    } yield strings

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
