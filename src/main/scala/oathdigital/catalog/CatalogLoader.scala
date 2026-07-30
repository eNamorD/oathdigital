package oathdigital.catalog

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.util.control.NonFatal

import oathdigital.catalog.CatalogLoadError._
import oathdigital.model.{
  CatalogRef,
  InclusiveIntRange,
  SiteId,
  SupplyRefreshBand,
  SupplyRules,
  Tokens,
  VisionId
}
import ujson.{Arr, Bool, Null, Num, Obj, Str, Value}

object CatalogLoader {
  val SupportedSchemaVersion: String = "1.0.0"
  private val SupplyBoardExcludedReviewItems: Set[String] =
    Set("non-Supply board icons require crop-level review")

  private type Result[A] = Either[Vector[CatalogLoadError], A]

  private final case class DecodedDocument(
      schemaVersion: String,
      ref: CatalogRef,
      ruleset: RulesetMetadata,
      sourceIds: Set[String],
      components: Vector[(ComponentMetadata, Obj)]
  )

  def load(path: Path, request: CatalogLoadRequest): Result[ExecutableCatalog] =
    try {
      val json = Files.readString(path, StandardCharsets.UTF_8)
      load(json, request)
    } catch {
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

  def load(json: String, request: CatalogLoadRequest): Result[ExecutableCatalog] =
    parse(json).flatMap(decodeDocument).flatMap { document =>
      val compatibilityErrors = request.expectedCatalog.toVector.collect {
        case expected if expected != document.ref =>
          IncompatibleCatalog("$.catalogVersion", expected, document.ref)
      }

      val normativeSourceErrors =
        if (
          document.sourceIds.contains(
            document.ruleset.normativeGeneralRulesSourceId
          )
        ) Vector.empty
        else
          Vector(
            UnknownSourceReference(
              "$.ruleset.normativeGeneralRulesSourceId",
              document.ruleset.normativeGeneralRulesSourceId
            )
          )
      val componentSourceErrors =
        document.components.flatMap { case (metadata, _) =>
          metadata.provenance.collect {
            case provenance
                if !document.sourceIds.contains(provenance.sourceId) =>
              UnknownSourceReference(
                s"$$.components[${metadata.definitionId.value}].printEvidence",
                provenance.sourceId
              )
          }
        }
      val sourceErrors = normativeSourceErrors ++ componentSourceErrors

      val identityErrors =
        duplicateDefinitionErrors(document.components) ++
          duplicatePrintedIdErrors(document.components)

      val preflightErrors =
        compatibilityErrors ++ sourceErrors ++ identityErrors

      if (preflightErrors.nonEmpty) Left(preflightErrors)
      else decodeSelection(document, request.selection)
    }

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

  private def decodeDocument(value: Value): Result[DecodedDocument] =
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
      rulesetObject <- requiredObject(root, "ruleset", "$")
      ruleset <- decodeRuleset(rulesetObject)
      ref <- construct(
        "$.catalogVersion",
        CatalogRef(ruleset.id, catalogVersion)
      )
      identityPolicy <- requiredObject(root, "identityPolicy", "$")
      _ <- validateIdentityPolicy(identityPolicy)
      sourceValues <- requiredArray(root, "sources", "$")
      sourceIds <- decodeSourceIds(sourceValues)
      componentValues <- requiredArray(root, "components", "$")
      components <- collectResults(
        componentValues.value.zipWithIndex.map { case (component, index) =>
          for {
            obj <- asObject(component, s"$$.components[$index]")
            metadata <- decodeMetadata(obj, index)
          } yield metadata -> obj
        }.toVector
      )
    } yield DecodedDocument(
      schemaVersion,
      ref,
      ruleset,
      sourceIds,
      components
    )

  private def decodeRuleset(obj: Obj): Result[RulesetMetadata] =
    for {
      id <- requiredString(obj, "id", "$.ruleset")
      version <- requiredString(obj, "version", "$.ruleset")
      source <- requiredString(
        obj,
        "normativeGeneralRulesSourceId",
        "$.ruleset"
      )
    } yield RulesetMetadata(id, version, source)

  private def validateIdentityPolicy(obj: Obj): Result[Unit] = {
    val expectations = Vector(
      "printedCardsAreSingletonDefinitions" -> true,
      "duplicatePhysicalCopiesRequireSourceEvidence" -> true,
      "runtimeCardInstanceIdsAllowed" -> false,
      "componentIdsAreTypedPrintedIds" -> true
    )

    collectResults(expectations.map { case (field, expected) =>
      requiredBoolean(obj, field, "$.identityPolicy").flatMap { actual =>
        if (actual == expected) Right(())
        else
          Left(
            Vector(
              IdentityPolicyMismatch(
                s"$$.identityPolicy.$field",
                s"expected $expected, found $actual"
              )
            )
          )
      }
    }).map(_ => ())
  }

  private def decodeSourceIds(values: Arr): Result[Set[String]] =
    collectResults(
      values.value.zipWithIndex.map { case (value, index) =>
        for {
          obj <- asObject(value, s"$$.sources[$index]")
          id <- requiredString(obj, "sourceId", s"$$.sources[$index]")
        } yield id
      }.toVector
    ).flatMap { ids =>
      val duplicates = ids.groupBy(identity).collect {
        case (id, occurrences) if occurrences.size > 1 => id
      }.toVector.sorted
      if (duplicates.isEmpty) Right(ids.toSet)
      else
        Left(
          duplicates.map { id =>
            InvalidValue("$.sources", s"duplicate source ID $id")
          }
        )
    }

  private def decodeMetadata(obj: Obj, index: Int): Result[ComponentMetadata] = {
    val path = s"$$.components[$index]"
    for {
      definitionIdValue <- requiredString(obj, "definitionId", path)
      definitionId <- construct(
        s"$path.definitionId",
        DefinitionId(definitionIdValue)
      )
      kind <- requiredString(obj, "kind", path)
      name <- requiredString(obj, "name", path)
      physicalCopyCount <- requiredInt(obj, "physicalCopyCount", path)
      _ <-
        if (physicalCopyCount == 1) Right(())
        else
          Left(
            Vector(
              IdentityPolicyMismatch(
                s"$path.physicalCopyCount",
                s"singleton catalog requires 1, found $physicalCopyCount"
              )
            )
          )
      printedId <- optionalPrintedId(obj, path)
      evidence <- requiredArray(obj, "printEvidence", path)
      provenance <- decodeEvidence(evidence, s"$path.printEvidence")
      transcriptionObject <- requiredObject(obj, "transcription", path)
      transcription <- decodeTranscription(
        transcriptionObject,
        s"$path.transcription"
      )
      unresolved <- optionalStringArray(obj, "unresolved", path)
    } yield ComponentMetadata(
      definitionId,
      kind,
      name,
      printedId,
      provenance,
      transcription,
      unresolved
    )
  }

  private def optionalPrintedId(
      obj: Obj,
      path: String
  ): Result[Option[PrintedComponentId]] =
    obj.value.get("printedComponentId") match {
      case None => Left(Vector(MissingField(s"$path.printedComponentId")))
      case Some(Null) => Right(None)
      case Some(value) =>
        for {
          idObject <- asObject(value, s"$path.printedComponentId")
          kind <- requiredString(
            idObject,
            "type",
            s"$path.printedComponentId"
          )
          id <- requiredString(
            idObject,
            "value",
            s"$path.printedComponentId"
          )
          printed <- construct(
            s"$path.printedComponentId",
            PrintedComponentId(kind, id)
          )
        } yield Some(printed)
    }

  private def decodeEvidence(values: Arr, path: String): Result[Vector[SourceProvenance]] =
    if (values.value.isEmpty)
      Left(Vector(InvalidValue(path, "print evidence must not be empty")))
    else
      collectResults(
        values.value.zipWithIndex.map { case (value, evidenceIndex) =>
          for {
            evidence <- asObject(value, s"$path[$evidenceIndex]")
            provenance <- requiredArray(
              evidence,
              "provenance",
              s"$path[$evidenceIndex]"
            )
            _ <-
              if (provenance.value.nonEmpty) Right(())
              else
                Left(
                  Vector(
                    InvalidValue(
                      s"$path[$evidenceIndex].provenance",
                      "provenance must not be empty"
                    )
                  )
                )
            decoded <- collectResults(
              provenance.value.zipWithIndex.map {
                case (provenanceValue, provenanceIndex) =>
                  decodeProvenance(
                    provenanceValue,
                    s"$path[$evidenceIndex].provenance[$provenanceIndex]"
                  )
              }.toVector
            )
          } yield decoded
        }.toVector
      ).map(_.flatten)

  private def decodeProvenance(
      value: Value,
      path: String
  ): Result[SourceProvenance] =
    for {
      obj <- asObject(value, path)
      sourceId <- requiredString(obj, "sourceId", path)
      file <- requiredString(obj, "file", path)
      page <- requiredInt(obj, "page", path)
      _ <-
        if (page >= 1) Right(())
        else Left(Vector(InvalidValue(s"$path.page", "page must be positive")))
      sheetSlot <- optionalScalarString(obj, "sheetSlot", path)
    } yield SourceProvenance(sourceId, file, page, sheetSlot)

  private def decodeTranscription(
      obj: Obj,
      path: String
  ): Result[TranscriptionMetadata] =
    for {
      method <- requiredString(obj, "method", path)
      confidence <- requiredString(obj, "confidence", path)
      reviewStatus <- requiredString(obj, "reviewStatus", path)
    } yield TranscriptionMetadata(method, confidence, reviewStatus)

  private def decodeSelection(
      document: DecodedDocument,
      selection: CatalogSelection
  ): Result[ExecutableCatalog] = {
    val setupCards =
      if (selection.setupCards)
        decodeKind(document.components, "setup-card")(decodeSetupCard)
      else Right(Vector.empty)
    val supplyBoards =
      if (selection.supplyBoards)
        decodeKind(document.components, "player-board")(decodeSupplyBoard)
      else Right(Vector.empty)
    val sites =
      if (selection.sites)
        decodeKind(document.components, "site")(decodeSite)
      else Right(Vector.empty)
    val visions =
      if (selection.visions)
        decodeKind(document.components, "vision")(decodeVision)
      else Right(Vector.empty)

    for {
      decodedSetupCards <- setupCards
      decodedSupplyBoards <- supplyBoards
      decodedSites <- sites
      decodedVisions <- visions
      _ <- validateUniqueSetupSteps(decodedSetupCards)
    } yield ExecutableCatalog(
      document.schemaVersion,
      document.ref,
      document.ruleset,
      decodedSetupCards.sortBy(_.step),
      decodedSupplyBoards.sortBy(_.metadata.definitionId.value),
      decodedSites.sortBy(_.metadata.definitionId.value),
      decodedVisions.sortBy(_.metadata.definitionId.value)
    )
  }

  private def decodeKind[A](
      components: Vector[(ComponentMetadata, Obj)],
      kind: String
  )(
      decoder: (ComponentMetadata, Obj) => Result[A]
  ): Result[Vector[A]] =
    collectResults(
      components.collect {
        case (metadata, obj) if metadata.kind == kind =>
          decoder(metadata, obj)
      }
    )

  private def decodeSetupCard(
      metadata: ComponentMetadata,
      obj: Obj
  ): Result[SetupCardDefinition] = {
    val path = componentPath(metadata)
    for {
      _ <- requireResolved(metadata, path)
      step <- requiredInt(obj, "step", path)
      _ <-
        if (step > 0) Right(())
        else Left(Vector(InvalidValue(s"$path.step", "step must be positive")))
      handlers <- requiredStringArray(obj, "handlers", path)
      _ <-
        if (handlers.nonEmpty) Right(())
        else
          Left(
            Vector(
              InvalidValue(s"$path.handlers", "handlers must not be empty")
            )
          )
      physicalRole <- requiredString(obj, "physicalRole", path)
    } yield SetupCardDefinition(metadata, step, handlers, physicalRole)
  }

  private def decodeSupplyBoard(
      metadata: ComponentMetadata,
      obj: Obj
  ): Result[SupplyBoardDefinition] = {
    val path = componentPath(metadata)
    for {
      _ <- requireOnlyExcludedReviewItems(
        metadata,
        SupplyBoardExcludedReviewItems,
        path
      )
      boardKindValue <- requiredString(obj, "boardKind", path)
      boardKind <- boardKindValue match {
        case "chancellor" => Right(PlayerBoardKind.Chancellor)
        case "player" => Right(PlayerBoardKind.Player)
        case other =>
          Left(
            Vector(
              InvalidValue(
                s"$path.boardKind",
                s"unsupported board kind $other"
              )
            )
          )
      }
      supply <- requiredObject(obj, "supply", path)
      maximum <- requiredInt(supply, "maximum", s"$path.supply")
      remainingValues <- requiredIntArray(
        supply,
        "remainingValues",
        s"$path.supply"
      )
      refreshValues <- requiredArray(
        supply,
        "refreshByWarbandsInBank",
        s"$path.supply"
      )
      bands <- collectResults(
        refreshValues.value.zipWithIndex.map { case (value, index) =>
          decodeSupplyBand(value, s"$path.supply.refreshByWarbandsInBank[$index]")
        }.toVector
      )
      rules <- construct(
        s"$path.supply",
        SupplyRules(maximum, bands)
      )
      expectedRemaining = (maximum to 0 by -1).toVector
      _ <-
        if (remainingValues == expectedRemaining) Right(())
        else
          Left(
            Vector(
              InvalidValue(
                s"$path.supply.remainingValues",
                s"expected ${expectedRemaining.mkString("[", ",", "]")}"
              )
            )
          )
    } yield SupplyBoardDefinition(
      metadata,
      boardKind,
      rules,
      remainingValues,
      metadata.unresolved
    )
  }

  private def decodeSupplyBand(
      value: Value,
      path: String
  ): Result[SupplyRefreshBand] =
    for {
      obj <- asObject(value, path)
      warbands <- requiredObject(obj, "warbandsInBank", path)
      minimum <- requiredInt(warbands, "min", s"$path.warbandsInBank")
      maximum <- optionalInt(warbands, "max", s"$path.warbandsInBank")
      refreshTo <- requiredInt(obj, "refreshTo", path)
      range <- construct(
        s"$path.warbandsInBank",
        InclusiveIntRange(minimum, maximum.getOrElse(Int.MaxValue))
      )
      band <- construct(
        path,
        SupplyRefreshBand(range, refreshTo)
      )
    } yield band

  private def decodeSite(
      metadata: ComponentMetadata,
      obj: Obj
  ): Result[SiteDefinition] = {
    val path = componentPath(metadata)
    for {
      _ <- requireResolved(metadata, path)
      printed <- requirePrintedId(metadata, "site-id", path)
      statistics <- requiredObject(obj, "statistics", path)
      capacity <- requiredInt(statistics, "capacity", s"$path.statistics")
      _ <-
        if (capacity >= 0) Right(())
        else
          Left(
            Vector(
              InvalidValue(
                s"$path.statistics.capacity",
                "capacity must be non-negative"
              )
            )
          )
      recoverDifficulty <- requiredInt(
        statistics,
        "recoverDifficulty",
        s"$path.statistics"
      )
      _ <-
        if (recoverDifficulty >= 0) Right(())
        else
          Left(
            Vector(
              InvalidValue(
                s"$path.statistics.recoverDifficulty",
                "recover difficulty must be non-negative"
              )
            )
          )
      resources <- requiredArray(
        statistics,
        "startingResources",
        s"$path.statistics"
      )
      tokens <- decodeStartingResources(
        resources,
        s"$path.statistics.startingResources"
      )
      id <- construct(s"$path.printedComponentId", SiteId(printed.value))
    } yield SiteDefinition(
      metadata,
      id,
      capacity,
      recoverDifficulty,
      tokens
    )
  }

  private def decodeStartingResources(values: Arr, path: String): Result[Tokens] =
    collectResults(
      values.value.zipWithIndex.map { case (value, index) =>
        for {
          obj <- asObject(value, s"$path[$index]")
          kind <- requiredString(obj, "type", s"$path[$index]")
          count <- requiredInt(obj, "count", s"$path[$index]")
          _ <-
            if (count >= 0) Right(())
            else
              Left(
                Vector(
                  InvalidValue(
                    s"$path[$index].count",
                    "resource count must be non-negative"
                  )
                )
              )
        } yield kind -> count
      }.toVector
    ).flatMap { resources =>
      val duplicates = resources.groupBy(_._1).collect {
        case (kind, occurrences) if occurrences.size > 1 => kind
      }.toVector.sorted
      val unsupported = resources.collect {
        case (kind, _) if kind != "favor" && kind != "secret" => kind
      }.distinct.sorted
      if (duplicates.nonEmpty)
        Left(
          duplicates.map(kind =>
            InvalidValue(path, s"duplicate starting resource $kind")
          )
        )
      else if (unsupported.nonEmpty)
        Left(
          unsupported.map(kind =>
            InvalidValue(path, s"unsupported starting resource $kind")
          )
        )
      else {
        val byKind = resources.toMap
        construct(
          path,
          Tokens(
            favor = byKind.getOrElse("favor", 0),
            secrets = byKind.getOrElse("secret", 0)
          )
        )
      }
    }

  private def decodeVision(
      metadata: ComponentMetadata,
      obj: Obj
  ): Result[VisionDefinition] = {
    val path = componentPath(metadata)
    for {
      _ <- requireResolved(metadata, path)
      printed <- requirePrintedId(metadata, "vision-id", path)
      handlers <- requiredStringArray(obj, "handlers", path)
      goal <- optionalVisionGoal(obj, path)
      id <- construct(s"$path.printedComponentId", VisionId(printed.value))
    } yield VisionDefinition(metadata, id, handlers, goal)
  }

  private def optionalVisionGoal(
      obj: Obj,
      path: String
  ): Result[Option[VisionGoal]] =
    obj.value.get("goal") match {
      case None => Left(Vector(MissingField(s"$path.goal")))
      case Some(Null) => Right(None)
      case Some(value) =>
        for {
          goal <- asObject(value, s"$path.goal")
          kind <- requiredString(goal, "type", s"$path.goal")
          minimum <- requiredInt(
            goal,
            "minimumVisionsDrawn",
            s"$path.goal"
          )
          _ <-
            if (minimum >= 0) Right(())
            else
              Left(
                Vector(
                  InvalidValue(
                    s"$path.goal.minimumVisionsDrawn",
                    "minimum Visions Drawn must be non-negative"
                  )
                )
              )
        } yield Some(VisionGoal(kind, minimum))
    }

  private def requirePrintedId(
      metadata: ComponentMetadata,
      expectedKind: String,
      path: String
  ): Result[PrintedComponentId] =
    metadata.printedComponentId match {
      case None => Left(Vector(MissingField(s"$path.printedComponentId")))
      case Some(id) if id.kind != expectedKind =>
        Left(
          Vector(
            InvalidValue(
              s"$path.printedComponentId.type",
              s"expected $expectedKind, found ${id.kind}"
            )
          )
        )
      case Some(id) => Right(id)
    }

  private def requireResolved(
      metadata: ComponentMetadata,
      path: String
  ): Result[Unit] =
    if (metadata.unresolved.isEmpty) Right(())
    else
      Left(
        Vector(
          UnresolvedRequiredFields(
            s"$path.unresolved",
            metadata.definitionId,
            metadata.unresolved
          )
        )
      )

  private def requireOnlyExcludedReviewItems(
      metadata: ComponentMetadata,
      allowed: Set[String],
      path: String
  ): Result[Unit] = {
    val requiredItems = metadata.unresolved.filterNot(allowed.contains)
    if (requiredItems.isEmpty) Right(())
    else
      Left(
        Vector(
          UnresolvedRequiredFields(
            s"$path.unresolved",
            metadata.definitionId,
            requiredItems
          )
        )
      )
  }

  private def validateUniqueSetupSteps(
      cards: Vector[SetupCardDefinition]
  ): Result[Unit] = {
    val duplicates = cards.groupBy(_.step).collect {
      case (step, occurrences) if occurrences.size > 1 => step
    }.toVector.sorted
    if (duplicates.isEmpty) Right(())
    else
      Left(
        duplicates.map { step =>
          InvalidValue("$.components", s"duplicate setup step $step")
        }
      )
  }

  private def duplicateDefinitionErrors(
      components: Vector[(ComponentMetadata, Obj)]
  ): Vector[CatalogLoadError] =
    components
      .groupBy(_._1.definitionId)
      .collect {
        case (id, occurrences) if occurrences.size > 1 =>
          DuplicateDefinitionId("$.components", id)
      }
      .toVector
      .sortBy {
        case error: DuplicateDefinitionId => error.id.value
        case _ => ""
      }

  private def duplicatePrintedIdErrors(
      components: Vector[(ComponentMetadata, Obj)]
  ): Vector[CatalogLoadError] =
    components
      .flatMap { case (metadata, _) =>
        metadata.printedComponentId.map(_ -> metadata)
      }
      .groupBy(_._1)
      .collect {
        case (id, occurrences)
            if occurrences.size > 1 &&
              !isEdificeFacePair(occurrences.map(_._2)) =>
          DuplicatePrintedComponentId("$.components", id)
      }
      .toVector
      .sortBy {
        case error: DuplicatePrintedComponentId =>
          (error.id.kind, error.id.value)
        case _ => ("", "")
      }

  private def isEdificeFacePair(
      metadata: Vector[ComponentMetadata]
  ): Boolean = {
    val suffixes = metadata.map(_.definitionId.value.split(":").last).toSet
    metadata.size == 2 &&
    metadata.forall(_.kind == "edifice-face") &&
    suffixes == Set("intact", "ruined")
  }

  private def componentPath(metadata: ComponentMetadata): String =
    s"$$.components[${metadata.definitionId.value}]"

  private def asObject(value: Value, path: String): Result[Obj] =
    value match {
      case obj: Obj => Right(obj)
      case other => Left(Vector(WrongType(path, "object", typeName(other))))
    }

  private def requiredObject(
      obj: Obj,
      key: String,
      path: String
  ): Result[Obj] =
    requiredValue(obj, key, path).flatMap(asObject(_, s"$path.$key"))

  private def requiredArray(
      obj: Obj,
      key: String,
      path: String
  ): Result[Arr] =
    requiredValue(obj, key, path).flatMap {
      case array: Arr => Right(array)
      case other =>
        Left(Vector(WrongType(s"$path.$key", "array", typeName(other))))
    }

  private def requiredString(
      obj: Obj,
      key: String,
      path: String
  ): Result[String] =
    requiredValue(obj, key, path).flatMap {
      case Str(value) if value.trim.nonEmpty => Right(value)
      case Str(_) =>
        Left(Vector(InvalidValue(s"$path.$key", "must not be blank")))
      case other =>
        Left(Vector(WrongType(s"$path.$key", "string", typeName(other))))
    }

  private def requiredBoolean(
      obj: Obj,
      key: String,
      path: String
  ): Result[Boolean] =
    requiredValue(obj, key, path).flatMap {
      case Bool(value) => Right(value)
      case other =>
        Left(Vector(WrongType(s"$path.$key", "boolean", typeName(other))))
    }

  private def requiredInt(
      obj: Obj,
      key: String,
      path: String
  ): Result[Int] =
    requiredValue(obj, key, path).flatMap(value =>
      asInt(value, s"$path.$key")
    )

  private def optionalInt(
      obj: Obj,
      key: String,
      path: String
  ): Result[Option[Int]] =
    obj.value.get(key) match {
      case None | Some(Null) => Right(None)
      case Some(value) => asInt(value, s"$path.$key").map(Some(_))
    }

  private def asInt(value: Value, path: String): Result[Int] =
    value match {
      case Num(number)
          if number.isWhole &&
            number >= Int.MinValue &&
            number <= Int.MaxValue =>
        Right(number.toInt)
      case Num(number) =>
        Left(Vector(InvalidValue(path, s"$number is not a 32-bit integer")))
      case other => Left(Vector(WrongType(path, "integer", typeName(other))))
    }

  private def requiredStringArray(
      obj: Obj,
      key: String,
      path: String
  ): Result[Vector[String]] =
    requiredArray(obj, key, path).flatMap { array =>
      collectResults(
        array.value.zipWithIndex.map { case (value, index) =>
          value match {
            case Str(text) if text.trim.nonEmpty => Right(text)
            case Str(_) =>
              Left(
                Vector(
                  InvalidValue(
                    s"$path.$key[$index]",
                    "must not be blank"
                  )
                )
              )
            case other =>
              Left(
                Vector(
                  WrongType(
                    s"$path.$key[$index]",
                    "string",
                    typeName(other)
                  )
                )
              )
          }
        }.toVector
      )
    }

  private def optionalStringArray(
      obj: Obj,
      key: String,
      path: String
  ): Result[Vector[String]] =
    obj.value.get(key) match {
      case None => Right(Vector.empty)
      case Some(_: Arr) => requiredStringArray(obj, key, path)
      case Some(other) =>
        Left(Vector(WrongType(s"$path.$key", "array", typeName(other))))
    }

  private def requiredIntArray(
      obj: Obj,
      key: String,
      path: String
  ): Result[Vector[Int]] =
    requiredArray(obj, key, path).flatMap { array =>
      collectResults(
        array.value.zipWithIndex.map { case (value, index) =>
          asInt(value, s"$path.$key[$index]")
        }.toVector
      )
    }

  private def optionalScalarString(
      obj: Obj,
      key: String,
      path: String
  ): Result[Option[String]] =
    obj.value.get(key) match {
      case None | Some(Null) => Right(None)
      case Some(Str(value)) => Right(Some(value))
      case Some(Num(value)) if value.isWhole => Right(Some(value.toLong.toString))
      case Some(other) =>
        Left(
          Vector(
            WrongType(s"$path.$key", "string or integer", typeName(other))
          )
        )
    }

  private def requiredValue(
      obj: Obj,
      key: String,
      path: String
  ): Result[Value] =
    obj.value.get(key) match {
      case Some(value) => Right(value)
      case None => Left(Vector(MissingField(s"$path.$key")))
    }

  private def construct[A](path: String, value: => A): Result[A] =
    try Right(value)
    catch {
      case error: IllegalArgumentException =>
        Left(
          Vector(
            InvalidValue(
              path,
              Option(error.getMessage).getOrElse("invalid value")
            )
          )
        )
    }

  private def collectResults[A](
      results: Vector[Result[A]]
  ): Result[Vector[A]] = {
    val errors = results.collect { case Left(values) => values }.flatten
    if (errors.nonEmpty) Left(errors)
    else Right(results.collect { case Right(value) => value })
  }

  private def typeName(value: Value): String =
    value match {
      case _: Obj => "object"
      case _: Arr => "array"
      case _: Str => "string"
      case _: Num => "number"
      case _: Bool => "boolean"
      case Null => "null"
    }
}
