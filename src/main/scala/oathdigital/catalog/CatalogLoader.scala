package oathdigital.catalog

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.util.control.NonFatal

import oathdigital.catalog.CatalogLoadError._
import oathdigital.model.{CatalogRef, SiteId, Tokens}
import ujson.{Arr, Bool, Null, Obj, Str, Value}

object CatalogLoader {
  val SupportedSchemaVersion: String = "1.2.0"
  val RulesetId: String = "oath-new-foundations"

  private type Result[A] = Either[Vector[CatalogLoadError], A]

  def load(path: Path): Result[ExecutableCatalog] =
    load(path, CatalogLoadRequest())

  def load(
      path: Path,
      request: CatalogLoadRequest
  ): Result[ExecutableCatalog] =
    try load(Files.readString(path, StandardCharsets.UTF_8), request)
    catch {
      case NonFatal(error) =>
        Left(
          Vector(
            FileReadFailed(
              path.toString,
              Option(error.getMessage).getOrElse(error.getClass.getName)
            )
          )
        )
    }

  def load(json: String): Result[ExecutableCatalog] =
    load(json, CatalogLoadRequest())

  def load(
      json: String,
      request: CatalogLoadRequest
  ): Result[ExecutableCatalog] =
    parse(json).flatMap(decode(_, request))

  private def parse(json: String): Result[Value] =
    try Right(ujson.read(json))
    catch {
      case NonFatal(error) =>
        Left(
          Vector(
            InvalidJson(
              Option(error.getMessage).getOrElse(error.getClass.getName)
            )
          )
        )
    }

  private def decode(
      value: Value,
      request: CatalogLoadRequest
  ): Result[ExecutableCatalog] =
    for {
      root <- asObject(value, "$")
      schemaVersion <- requiredString(root, "schemaVersion", "$")
      _ <-
        if (schemaVersion == SupportedSchemaVersion) Right(())
        else
          Left(
            Vector(
              UnsupportedSchemaVersion(
                "$.schemaVersion",
                SupportedSchemaVersion,
                schemaVersion
              )
            )
          )
      catalogVersion <- requiredString(root, "catalogVersion", "$")
      ref <- construct(
        "$.catalogVersion",
        CatalogRef(RulesetId, catalogVersion)
      )
      _ <- checkCompatibility(request.expectedCatalog, ref)
      denizens <- decodeArray(root, "denizens")(decodeDenizen)
      relics <- decodeArray(root, "relics")(decodeRelic)
      edifices <- decodeArray(root, "edifices")(decodeEdifice)
      legacies <- decodeArray(root, "legacies")(decodeLegacy)
      sites <- decodeArray(root, "sites")(decodeSite)
      _ <- validateUniqueIds(
        denizens.map(_.id) ++
          relics.map(_.id) ++
          edifices.map(_.id) ++
          legacies.map(_.id) ++
          sites.map(site => DefinitionId(site.id.value))
      )
    } yield ExecutableCatalog(
      schemaVersion,
      ref,
      denizens.sortBy(_.id.value),
      relics.sortBy(_.id.value),
      edifices.sortBy(_.id.value),
      legacies.sortBy(_.id.value),
      sites.sortBy(_.id.value)
    )

  private def checkCompatibility(
      expected: Option[CatalogRef],
      actual: CatalogRef
  ): Result[Unit] =
    expected match {
      case Some(value) if value != actual =>
        Left(Vector(IncompatibleCatalog("$.catalogVersion", value, actual)))
      case _ => Right(())
    }

  private def decodeDenizen(obj: Obj, path: String): Result[DenizenDefinition] =
    for {
      id <- decodeDefinitionId(obj, path)
      _ <- requirePattern(
        id,
        "(?:[1-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-8])",
        "printed denizen ID from 1 through 258",
        s"$path.id"
      )
      name <- requiredString(obj, "name", path)
      suit <- decodeSuit(obj, path)
      restrictions <- decodeDenizenRestrictions(obj, path)
      powers <- decodePowers(obj, path)
    } yield DenizenDefinition(
      id,
      name,
      suit,
      restrictions,
      powers
    )

  private def decodeDenizenRestrictions(
      obj: Obj,
      path: String
  ): Result[CardRestrictions] = {
    val fieldPath = s"$path.restrictions"
    obj.value.get("restrictions") match {
      case None => Left(Vector(MissingField(fieldPath)))
      case Some(Null) => Right(CardRestrictions.Unrestricted)
      case Some(values: Arr) =>
        val tokens = values.value.zipWithIndex.map {
          case (Str(value), _) => Right(value)
          case (value, index) =>
            Left(
              Vector(
                WrongType(s"$fieldPath[$index]", "string", typeName(value))
              )
            )
        }.toVector
        collectResults(tokens).flatMap {
          case Vector("site-only") => Right(CardRestrictions.SiteOnly)
          case Vector("adviser-only") => Right(CardRestrictions.AdviserOnly)
          case Vector("adviser-only", "locked") =>
            Right(CardRestrictions.LockedAdviserOnly)
          case values =>
            val allowed = Set("site-only", "adviser-only", "locked")
            values.zipWithIndex.find { case (value, _) =>
              !allowed.contains(value)
            } match {
              case Some((value, index)) =>
                Left(
                  Vector(
                    InvalidValue(
                      s"$fieldPath[$index]",
                      s"unsupported restriction $value"
                    )
                  )
                )
              case None =>
                Left(
                  Vector(
                    InvalidValue(
                      fieldPath,
                      "expected null, [site-only], [adviser-only], or " +
                        "[adviser-only, locked]"
                    )
                  )
                )
            }
        }
      case Some(value) =>
        Left(Vector(WrongType(fieldPath, "null or array", typeName(value))))
    }
  }

  private def decodeRelic(obj: Obj, path: String): Result[RelicDefinition] =
    for {
      id <- decodeDefinitionId(obj, path)
      _ <- requirePattern(
        id,
        "(?:R(?:0[1-9]|[1-3][0-9]|4[0-7])|grand-scepter)",
        "printed relic ID R01 through R47 or grand-scepter",
        s"$path.id"
      )
      name <- requiredString(obj, "name", path)
      roleValue <- requiredString(obj, "role", path)
      role <- roleValue match {
        case "ordinary" => Right(RelicRole.Ordinary)
        case "grand-scepter" => Right(RelicRole.GrandScepter)
        case other =>
          Left(Vector(InvalidValue(s"$path.role", s"unsupported role $other")))
      }
      value <- requiredInt(obj, "value", path)
      defense <- requiredInt(obj, "defense", path)
      _ <- collectResults(
        Vector(
          nonNegative(value, s"$path.value"),
          nonNegative(defense, s"$path.defense")
        )
      ).map(_ => ())
      powers <- decodePowers(obj, path)
    } yield RelicDefinition(id, name, role, value, defense, powers)

  private def decodeEdifice(obj: Obj, path: String): Result[EdificeDefinition] =
    for {
      id <- decodeDefinitionId(obj, path)
      _ <- requirePattern(
        id,
        "E(?:0[1-9]|[12][0-9]|30)",
        "printed edifice ID E01 through E30",
        s"$path.id"
      )
      suit <- decodeSuit(obj, path)
      restrictions <- decodeUnrestrictedEdifice(obj, path)
      intactObject <- requiredObject(obj, "intact", path)
      intact <- decodeEdificeFace(intactObject, s"$path.intact")
      ruinedObject <- requiredObject(obj, "ruined", path)
      ruined <- decodeEdificeFace(ruinedObject, s"$path.ruined")
    } yield EdificeDefinition(id, suit, restrictions, intact, ruined)

  private def decodeUnrestrictedEdifice(
      obj: Obj,
      path: String
  ): Result[CardRestrictions] = {
    val fieldPath = s"$path.restrictions"
    obj.value.get("restrictions") match {
      case None => Left(Vector(MissingField(fieldPath)))
      case Some(Null) => Right(CardRestrictions.Unrestricted)
      case Some(value) =>
        Left(
          Vector(
            InvalidValue(
              fieldPath,
              s"edifice restrictions must be null, found ${typeName(value)}"
            )
          )
        )
    }
  }

  private def decodeEdificeFace(
      obj: Obj,
      path: String
  ): Result[EdificeFaceDefinition] =
    for {
      name <- requiredString(obj, "name", path)
      powers <- decodePowers(obj, path)
    } yield EdificeFaceDefinition(name, powers)

  private def decodeLegacy(obj: Obj, path: String): Result[LegacyDefinition] =
    for {
      id <- decodeDefinitionId(obj, path)
      _ <- requirePattern(
        id,
        "L(?:0[1-9]|[12][0-9]|3[0-6])",
        "printed legacy ID L01 through L36",
        s"$path.id"
      )
      name <- requiredString(obj, "name", path)
      powers <- decodePowers(obj, path)
    } yield LegacyDefinition(id, name, powers)

  private def decodeSite(obj: Obj, path: String): Result[SiteDefinition] =
    for {
      idValue <- requiredString(obj, "id", path)
      _ <-
        if (idValue.startsWith("site:")) Right(())
        else Left(Vector(InvalidValue(s"$path.id", "expected site: prefix")))
      id <- construct(s"$path.id", SiteId(idValue))
      name <- requiredString(obj, "name", path)
      defense <- requiredInt(obj, "defense", path)
      capacity <- requiredInt(obj, "capacity", path)
      relicSlots <- requiredInt(obj, "relicSlots", path)
      recoverDifficulty <- optionalInt(obj, "recoverDifficulty", path)
      _ <- collectResults(
        Vector(
          nonNegative(defense, s"$path.defense"),
          nonNegative(capacity, s"$path.capacity"),
          nonNegative(relicSlots, s"$path.relicSlots")
        ) ++ recoverDifficulty.toVector.map(value =>
          nonNegative(value, s"$path.recoverDifficulty")
        )
      ).map(_ => ())
      resources <- requiredObject(obj, "startingResources", path)
      startingResources <- decodeTokens(resources, s"$path.startingResources")
      forgeRequirements <- optionalTokens(obj, "forgeRequirements", path)
      _ <-
        if (capacity == 3 == forgeRequirements.nonEmpty) Right(())
        else
          Left(
            Vector(
              InvalidValue(
                s"$path.forgeRequirements",
                "Forge requirements are required only on three-slot sites"
              )
            )
          )
      handlers <- decodeHandlers(obj, path, allowEmpty = true)
    } yield SiteDefinition(
      id,
      name,
      defense,
      capacity,
      relicSlots,
      recoverDifficulty,
      startingResources,
      forgeRequirements,
      handlers
    )

  private def decodeDefinitionId(obj: Obj, path: String): Result[DefinitionId] =
    requiredString(obj, "id", path).flatMap(value =>
      construct(s"$path.id", DefinitionId(value))
    )

  private def decodeSuit(obj: Obj, path: String): Result[Suit] =
    requiredString(obj, "suit", path).flatMap(value =>
      construct(s"$path.suit", Suit(value))
    )

  private def decodeHandlers(
      obj: Obj,
      path: String,
      allowEmpty: Boolean
  ): Result[Vector[String]] =
    requiredStringArray(obj, "handlers", path).flatMap { handlers =>
      val invalid = handlers.filterNot(
        _.matches("[a-z][a-z0-9-]*(\\.[a-z0-9-]+)+")
      )
      if (invalid.nonEmpty)
        Left(
          invalid.map(value =>
            InvalidValue(s"$path.handlers", s"invalid handler key $value")
          )
        )
      else if (!allowEmpty && handlers.isEmpty)
        Left(Vector(InvalidValue(s"$path.handlers", "must not be empty")))
      else Right(handlers)
    }

  private def decodePowers(obj: Obj, path: String): Result[Vector[CatalogPower]] =
    requiredArray(obj, "powers", path).flatMap { values =>
      if (values.value.isEmpty)
        Left(Vector(InvalidValue(s"$path.powers", "must not be empty")))
      else collectResults(values.value.zipWithIndex.map { case (value, index) =>
        val powerPath = s"$path.powers[$index]"
        asObject(value, powerPath).flatMap { power => for {
          id <- requiredString(power, "id", powerPath)
          persistent <- requiredBoolean(power, "persistent", powerPath)
          rulesText <- requiredString(power, "rulesText", powerPath, allowBlank = true)
          result <- construct(powerPath, CatalogPower(id, persistent, rulesText))
        } yield result }
      }.toVector).flatMap { powers =>
        val duplicates = powers.groupBy(_.id).collect {
          case (id, matches) if matches.size > 1 => id
        }.toVector.sorted
        if (duplicates.isEmpty) Right(powers)
        else Left(duplicates.map(id => InvalidValue(s"$path.powers",
          s"duplicate power ID $id")))
      }
    }

  private def requiredBoolean(obj: Obj, field: String, path: String)
      : Result[Boolean] = obj.value.get(field) match {
    case None => Left(Vector(MissingField(s"$path.$field")))
    case Some(Bool(value)) => Right(value)
    case Some(value) => Left(Vector(WrongType(s"$path.$field", "boolean",
      typeName(value))))
  }

  private def decodeTokens(obj: Obj, path: String): Result[Tokens] =
    for {
      favor <- requiredInt(obj, "favor", path)
      secrets <- requiredInt(obj, "secrets", path)
      tokens <- construct(path, Tokens(favor, secrets))
    } yield tokens

  private def optionalTokens(
      obj: Obj,
      field: String,
      path: String
  ): Result[Option[Tokens]] =
    obj.value.get(field) match {
      case None => Left(Vector(MissingField(s"$path.$field")))
      case Some(Null) => Right(None)
      case Some(value) =>
        asObject(value, s"$path.$field")
          .flatMap(decodeTokens(_, s"$path.$field"))
          .map(Some(_))
    }

  private def decodeArray[A](
      root: Obj,
      field: String
  )(decoder: (Obj, String) => Result[A]): Result[Vector[A]] =
    requiredArray(root, field, "$").flatMap { values =>
      collectResults(
        values.value.zipWithIndex.map { case (value, index) =>
          val path = s"$$.$field[$index]"
          asObject(value, path).flatMap(decoder(_, path))
        }.toVector
      )
    }

  private def validateUniqueIds(ids: Vector[DefinitionId]): Result[Unit] = {
    val duplicates = ids.groupBy(_.value).collect {
      case (_, values) if values.size > 1 => values.head
    }.toVector.sortBy(_.value)
    if (duplicates.isEmpty) Right(())
    else
      Left(
        duplicates.map(id => DuplicateDefinitionId("$.components", id))
      )
  }

  private def requirePattern(
      id: DefinitionId,
      pattern: String,
      expected: String,
      path: String
  ): Result[Unit] =
    if (id.value.matches(pattern)) Right(())
    else Left(Vector(InvalidValue(path, s"expected $expected")))

  private def nonNegative(value: Int, path: String): Result[Unit] =
    if (value >= 0) Right(())
    else Left(Vector(InvalidValue(path, "must be non-negative")))

  private def requiredObject(
      obj: Obj,
      field: String,
      path: String
  ): Result[Obj] =
    obj.value.get(field) match {
      case None => Left(Vector(MissingField(s"$path.$field")))
      case Some(value) => asObject(value, s"$path.$field")
    }

  private def requiredArray(
      obj: Obj,
      field: String,
      path: String
  ): Result[Arr] =
    obj.value.get(field) match {
      case None => Left(Vector(MissingField(s"$path.$field")))
      case Some(value: Arr) => Right(value)
      case Some(value) =>
        Left(Vector(WrongType(s"$path.$field", "array", typeName(value))))
    }

  private def requiredString(
      obj: Obj,
      field: String,
      path: String,
      allowBlank: Boolean = false
  ): Result[String] =
    obj.value.get(field) match {
      case None => Left(Vector(MissingField(s"$path.$field")))
      case Some(Str(value)) if allowBlank || value.trim.nonEmpty => Right(value)
      case Some(Str(_)) =>
        Left(Vector(InvalidValue(s"$path.$field", "must not be blank")))
      case Some(value) =>
        Left(Vector(WrongType(s"$path.$field", "string", typeName(value))))
    }

  private def requiredStringArray(
      obj: Obj,
      field: String,
      path: String
  ): Result[Vector[String]] =
    requiredArray(obj, field, path).flatMap { values =>
      collectResults(
        values.value.zipWithIndex.map {
          case (Str(value), _) if value.trim.nonEmpty => Right(value)
          case (Str(_), index) =>
            Left(
              Vector(
                InvalidValue(s"$path.$field[$index]", "must not be blank")
              )
            )
          case (value, index) =>
            Left(
              Vector(
                WrongType(s"$path.$field[$index]", "string", typeName(value))
              )
            )
        }.toVector
      )
    }

  private def requiredInt(
      obj: Obj,
      field: String,
      path: String
  ): Result[Int] =
    obj.value.get(field) match {
      case None => Left(Vector(MissingField(s"$path.$field")))
      case Some(value) => asInt(value, s"$path.$field")
    }

  private def optionalInt(
      obj: Obj,
      field: String,
      path: String
  ): Result[Option[Int]] =
    obj.value.get(field) match {
      case None => Left(Vector(MissingField(s"$path.$field")))
      case Some(Null) => Right(None)
      case Some(value) => asInt(value, s"$path.$field").map(Some(_))
    }

  private def asObject(value: Value, path: String): Result[Obj] =
    value match {
      case obj: Obj => Right(obj)
      case other => Left(Vector(WrongType(path, "object", typeName(other))))
    }

  private def asInt(value: Value, path: String): Result[Int] =
    value match {
      case ujson.Num(number)
          if number.isWhole && number >= Int.MinValue && number <= Int.MaxValue =>
        Right(number.toInt)
      case ujson.Num(_) =>
        Left(Vector(InvalidValue(path, "must be a 32-bit integer")))
      case other => Left(Vector(WrongType(path, "integer", typeName(other))))
    }

  private def typeName(value: Value): String =
    value match {
      case _: Obj => "object"
      case _: Arr => "array"
      case _: Str => "string"
      case _: ujson.Num => "number"
      case _: ujson.Bool => "boolean"
      case Null => "null"
    }

  private def construct[A](path: String, value: => A): Result[A] =
    try Right(value)
    catch {
      case NonFatal(error) =>
        Left(
          Vector(
            InvalidValue(
              path,
              Option(error.getMessage).getOrElse(error.getClass.getName)
            )
          )
        )
    }

  private def collectResults[A](
      results: Vector[Result[A]]
  ): Result[Vector[A]] = {
    val errors = results.flatMap(_.left.toOption.toVector.flatten)
    if (errors.nonEmpty) Left(errors)
    else Right(results.flatMap(_.toOption))
  }
}
