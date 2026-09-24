package oathdigital.serialization

import scala.util.control.NonFatal
import oathdigital.model._
import oathdigital.model.OathEvent._

/** Shared primitive and nested-model JSON vocabulary for v1 event families. */
private[serialization] trait GameEventJsonSupport {
  import WireError._

  protected final def decodeBanner(value: String, path: String): Either[WireError, Banner] =
    Banner.fromKey(value).toRight(InvalidValue(path, s"unknown banner '$value'"))

  /** A `CardId`, kind-tagged so a heterogeneous vector (a Chronicle's stored
    * site items, a dealt world-deck order) round-trips through one shape
    * instead of five ad hoc ones (2026-09-21 Chronicle design, slice 2).
    */
  protected final def encodeCardId(id: CardId): ujson.Value = id match {
    case id: DenizenId => ujson.Obj("kind" -> "denizen", "id" -> id.value)
    case id: VisionId => ujson.Obj("kind" -> "vision", "id" -> id.value)
    case id: RelicId => ujson.Obj("kind" -> "relic", "id" -> id.value)
    case id: EdificeId => ujson.Obj("kind" -> "edifice", "id" -> id.value)
    case id: LegacyId => ujson.Obj("kind" -> "legacy", "id" -> id.value)
  }

  protected final def decodeCardId(value: ujson.Value, path: String)
      : Either[WireError, CardId] = value("kind").str match {
    case "denizen" => Right(DenizenId(value("id").str))
    case "vision" => Right(VisionId(value("id").str))
    case "relic" => Right(RelicId(value("id").str))
    case "edifice" => Right(EdificeId(value("id").str))
    case "legacy" => Right(LegacyId(value("id").str))
    case kind => Left(InvalidValue(s"$path.kind", s"unknown card kind '$kind'"))
  }

  protected final def encodeWorldCardId(id: WorldCardId): ujson.Value = id match {
    case id: DenizenId => ujson.Obj("kind" -> "denizen", "id" -> id.value)
    case id: VisionId => ujson.Obj("kind" -> "vision", "id" -> id.value)
  }

  protected final def decodeWorldCardId(value: ujson.Value, path: String)
      : Either[WireError, WorldCardId] = value("kind").str match {
    case "denizen" => Right(DenizenId(value("id").str))
    case "vision" => Right(VisionId(value("id").str))
    case kind => Left(InvalidValue(s"$path.kind", s"unknown card kind '$kind'"))
  }

  protected final def encodeStoredSite(site: StoredSite): ujson.Value =
    ujson.Obj("site" -> site.site.value,
      "items" -> ujson.Arr.from(site.items.map(encodeCardId)))

  protected final def decodeStoredSite(value: ujson.Value, path: String)
      : Either[WireError, StoredSite] = for {
    items <- traverse(value("items").arr.zipWithIndex.toVector) {
      case (item, index) => decodeCardId(item, s"$path.items[$index]")
    }
  } yield StoredSite(SiteId(value("site").str), items)

  protected final def encodeFoundationState(
      number: FoundationNumber, state: FoundationState): ujson.Value =
    ujson.Obj("number" -> number.value,
      "face" -> (state.face match {
        case FoundationFace.Normal => "normal"
        case FoundationFace.Altered => "altered"
      }),
      "alterationSources" -> stringArray(
        state.alterationSources.toVector.map(_.value)))

  protected final def decodeFoundationState(value: ujson.Value, path: String)
      : Either[WireError, (FoundationNumber, FoundationState)] = for {
    number <- FoundationNumber.all.find(_.value == value("number").num.toInt)
      .toRight(InvalidValue(s"$path.number",
        s"unknown Foundation number ${value("number")}"))
    face <- value("face").str match {
      case "normal" => Right(FoundationFace.Normal)
      case "altered" => Right(FoundationFace.Altered)
      case other => Left(InvalidValue(s"$path.face",
        s"unknown Foundation face '$other'"))
    }
  } yield number -> FoundationState(face,
    value("alterationSources").arr.toVector.map(v => LegacyId(v.str)).toSet)

  protected final def encodeAdviserState(adviser: AdviserState): ujson.Value =
    adviser match {
      case DenizenState(id, orientation, tokens) => ujson.Obj(
        "kind" -> "denizen", "id" -> id.value,
        "orientation" -> encodeOrientation(orientation),
        "tokens" -> encodeTokens(tokens))
      case VisionState(id, orientation) => ujson.Obj(
        "kind" -> "vision", "id" -> id.value,
        "orientation" -> encodeOrientation(orientation))
    }

  protected final def decodeAdviserState(value: ujson.Value, path: String)
      : Either[WireError, AdviserState] = value("kind").str match {
    case "denizen" => for {
      orientation <- decodeOrientation(value("orientation"), s"$path.orientation")
      tokens <- decodeTokens(value("tokens"), s"$path.tokens")
    } yield DenizenState(DenizenId(value("id").str), orientation, tokens)
    case "vision" => decodeOrientation(value("orientation"), s"$path.orientation")
      .map(orientation => VisionState(VisionId(value("id").str), orientation))
    case kind => Left(InvalidValue(s"$path.kind", s"unknown adviser kind '$kind'"))
  }

  protected final def encodeOrientation(orientation: Orientation): ujson.Value =
    orientation match {
      case Orientation.FaceUp => "face-up"
      case Orientation.FaceDown => "face-down"
    }

  protected final def decodeOrientation(value: ujson.Value, path: String)
      : Either[WireError, Orientation] = value.str match {
    case "face-up" => Right(Orientation.FaceUp)
    case "face-down" => Right(Orientation.FaceDown)
    case other => Left(InvalidValue(path, s"unknown orientation '$other'"))
  }

  protected final def encodeTokens(tokens: Tokens): ujson.Value =
    ujson.Obj("favor" -> tokens.favor, "secrets" -> tokens.secrets)

  protected final def decodeTokens(value: ujson.Value, path: String)
      : Either[WireError, Tokens] =
    Right(Tokens(value("favor").num.toInt, value("secrets").num.toInt))

  protected final def encodeLineageState(lineage: LineageState): ujson.Value =
    ujson.Obj("id" -> lineage.id.value,
      "previousPlayer" -> lineage.previousPlayer.fold[ujson.Value](
        ujson.Null)(id => ujson.Str(id.value)),
      "role" -> (if (lineage.role.isImperial) "citizen" else "exile"),
      "legacies" -> ujson.Arr.from(lineage.legacies.map(legacy =>
        ujson.Obj("id" -> legacy.id.value, "active" -> legacy.active))),
      "startingAdvisers" -> ujson.Arr.from(
        lineage.startingAdvisers.map(encodeAdviserState)))

  protected final def decodeLineageState(value: ujson.Value, path: String)
      : Either[WireError, LineageState] = for {
    role <- value("role").str match {
      case "exile" => Right(Role.Exile)
      case "citizen" => Right(Role.Citizen)
      case other => Left(InvalidValue(s"$path.role", s"unknown role '$other'"))
    }
    legacies = value("legacies").arr.toVector.map(legacy =>
      LegacyState(LegacyId(legacy("id").str), legacy("active").bool))
    startingAdvisers <- traverse(value("startingAdvisers").arr.zipWithIndex.toVector) {
      case (adviser, index) =>
        decodeAdviserState(adviser, s"$path.startingAdvisers[$index]")
    }
  } yield LineageState(LineageId(value("id").str),
    value("previousPlayer") match {
      case ujson.Null => None
      case id => Some(PlayerId(id.str))
    }, role, legacies, startingAdvisers)

  protected final def encodeChronicle(chronicle: Chronicle): ujson.Value =
    ujson.Obj(
      "atlasBox" -> ujson.Arr.from(chronicle.atlasBox.map(encodeStoredSite)),
      "world" -> ujson.Arr.from(chronicle.world.map(encodeStoredSite)),
      "worldDeck" -> stringArray(chronicle.worldDeck.map(_.value)),
      "relicDeck" -> stringArray(chronicle.relicDeck.map(_.value)),
      "dispossessed" -> stringArray(chronicle.dispossessed.map(_.value)),
      "reliquary" -> stringArray(chronicle.reliquary.map(_.value)),
      "foundations" -> ujson.Arr.from(chronicle.foundations.toVector
        .map { case (number, state) => encodeFoundationState(number, state) }),
      "lineages" -> ujson.Arr.from(chronicle.lineages.map(encodeLineageState))
    )

  protected final def decodeChronicle(value: ujson.Value, path: String)
      : Either[WireError, Chronicle] = for {
    atlasBox <- traverse(value("atlasBox").arr.zipWithIndex.toVector) {
      case (site, index) => decodeStoredSite(site, s"$path.atlasBox[$index]")
    }
    world <- traverse(value("world").arr.zipWithIndex.toVector) {
      case (site, index) => decodeStoredSite(site, s"$path.world[$index]")
    }
    foundations <- traverse(value("foundations").arr.zipWithIndex.toVector) {
      case (entry, index) => decodeFoundationState(entry, s"$path.foundations[$index]")
    }
    lineages <- traverse(value("lineages").arr.zipWithIndex.toVector) {
      case (lineage, index) => decodeLineageState(lineage, s"$path.lineages[$index]")
    }
  } yield Chronicle(atlasBox, world,
    value("worldDeck").arr.toVector.map(v => DenizenId(v.str)),
    value("relicDeck").arr.toVector.map(v => RelicId(v.str)),
    value("dispossessed").arr.toVector.map(v => DenizenId(v.str)),
    value("reliquary").arr.toVector.map(v => RelicId(v.str)),
    foundations.toMap, lineages)

  protected final def encodeSetupOrders(orders: SetupOrders): ujson.Value =
    ujson.Obj(
      "participants" -> ujson.Arr.from(orders.participants.map { participant =>
        ujson.Obj(
          "playerId" -> participant.playerId.value,
          "lineageId" -> participant.lineageId.value,
          "color" -> participant.color.key
        )
      }),
      "firstPlayer" -> orders.firstPlayer.value,
      "worldDeckOrder" -> ujson.Arr.from(orders.worldDeckOrder.map(encodeWorldCardId)),
      "relicOrder" -> stringArray(orders.relicOrder.map(_.value))
    )

  protected final def decodeSetupOrders(value: ujson.Value, path: String)
      : Either[WireError, SetupOrders] = try {
    val obj = value.obj
    for {
      participants <- traverse(obj("participants").arr.zipWithIndex.toVector) {
        case (participant, index) =>
          val color = participant("color").str
          PlayerColor.fromKey(color).toRight(InvalidValue(
            s"$path.participants[$index].color",
            s"unknown player color '$color'")).map(FirstGameParticipant(
            PlayerId(participant("playerId").str),
            LineageId(participant("lineageId").str), _))
      }
      worldDeckOrder <- traverse(obj("worldDeckOrder").arr.zipWithIndex.toVector) {
        case (item, index) => decodeWorldCardId(item, s"$path.worldDeckOrder[$index]")
      }
    } yield SetupOrders(participants, PlayerId(obj("firstPlayer").str),
      worldDeckOrder, obj("relicOrder").arr.toVector.map(v => RelicId(v.str)))
  } catch {
    case NonFatal(error) =>
      Left(InvalidValue(path,
        Option(error.getMessage).getOrElse("invalid setup orders")))
  }

  protected final def validateEventCatalog(
      event: OathEvent,
      catalog: CatalogRef,
      path: String
  ): Either[WireError, Unit] = Right(())

  protected final def encodeCatalog(ref: CatalogRef): ujson.Value =
    ujson.Obj("ruleset" -> ref.ruleset, "version" -> ref.version)

  protected final def decodeCatalog(
      value: ujson.Value,
      path: String
  ): Either[WireError, CatalogRef] =
    try Right(CatalogRef(value("ruleset").str, value("version").str))
    catch {
      case NonFatal(error) =>
        Left(
          InvalidValue(
            path,
            Option(error.getMessage).getOrElse("invalid catalog")
          )
        )
    }

  protected final def requiredField(
      obj: ujson.Obj,
      name: String,
      path: String
  ): Either[WireError, ujson.Value] =
    obj.value.get(name).toRight(
      MissingField(s"$path.$name", "field is required")
    )

  protected final def stringField(
      obj: ujson.Obj,
      name: String,
      path: String
  ): Either[WireError, String] =
    requiredField(obj, name, path).flatMap {
      case ujson.Str(value) => Right(value)
      case _ => Left(WrongType(s"$path.$name", "expected a string"))
    }

  protected final def formatVersionField(
      obj: ujson.Obj,
      path: String
  ): Either[WireError, Int] =
    requiredField(obj, "formatVersion", path)
      .flatMap(value => safeInteger(value, s"$path.formatVersion"))
      .flatMap { value =>
        if (value <= Int.MaxValue.toLong) Right(value.toInt)
        else
          Left(
            InvalidValue(
              s"$path.formatVersion",
              s"must be between 0 and ${Int.MaxValue} inclusive"
            )
          )
      }

  protected final def safeIntegerField(
      obj: ujson.Obj,
      name: String,
      path: String
  ): Either[WireError, Long] =
    requiredField(obj, name, path)
      .flatMap(value => safeInteger(value, s"$path.$name"))

  protected final def safeIntField(obj: ujson.Obj, name: String, path: String)
      : Either[WireError, Int] =
    requiredField(obj, name, path).flatMap(value =>
      safeInt(value, s"$path.$name"))

  protected final def safeInt(value: ujson.Value, path: String)
      : Either[WireError, Int] =
    safeInteger(value, path).flatMap { number =>
      if (number <= Int.MaxValue.toLong) Right(number.toInt)
      else Left(InvalidValue(path,
        s"must be between 0 and ${Int.MaxValue} inclusive"))
    }

  protected final def safeInteger(
      value: ujson.Value,
      path: String
  ): Either[WireError, Long] =
    value match {
      case ujson.Num(number)
          if !number.isNaN &&
            !number.isInfinity &&
            number == math.rint(number) &&
            number >= 0 &&
            number <= GameEventWire.MaxSafeSequence.toDouble =>
        Right(number.toLong)
      case ujson.Num(number)
          if !number.isNaN &&
            !number.isInfinity &&
            number == math.rint(number) =>
        Left(
          InvalidValue(
            path,
            s"must be between 0 and ${GameEventWire.MaxSafeSequence} inclusive"
          )
        )
      case _ => Left(WrongType(path, "expected an integer"))
    }

  protected final def validateSequence(
      sequence: Long,
      path: String
  ): Either[WireError, Unit] =
    if (sequence >= 0 && sequence <= GameEventWire.MaxSafeSequence) Right(())
    else
      Left(
        InvalidValue(
          path,
          s"must be between 0 and ${GameEventWire.MaxSafeSequence} inclusive"
        )
      )

  protected final def stringArray(values: Vector[String]): ujson.Value =
    ujson.Arr.from(values.map(ujson.Str(_)))

  protected final def encodeForceKind(force: ForceKind): ujson.Value = force match {
    case ForceKind.Bandit => ujson.Obj("kind" -> "bandit")
    case ForceKind.Imperial => ujson.Obj("kind" -> "imperial")
    case ForceKind.Exile(lineage) => ujson.Obj(
      "kind" -> "exile", "lineageId" -> lineage.value)
  }

  protected final def decodeForceKind(value: ujson.Value, path: String)
      : Either[WireError, ForceKind] = try value("kind").str match {
    case "bandit" => Right(ForceKind.Bandit)
    case "imperial" => Right(ForceKind.Imperial)
    case "exile" => Right(ForceKind.Exile(LineageId(value("lineageId").str)))
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown force kind '$other'"))
  } catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid force kind"))) }

  protected final def encodeDefenseFace(face: DefenseDieFace): String = face match {
    case DefenseDieFace.Blank => "blank"
    case DefenseDieFace.OneShield => "one-shield"
    case DefenseDieFace.TwoShields => "two-shields"
    case DefenseDieFace.Doubler => "doubler"
  }

  protected final def encodeAttackFace(face: AttackDieFace): String = face match {
    case AttackDieFace.HollowSword => "hollow-sword"
    case AttackDieFace.OneSword => "one-sword"
    case AttackDieFace.TwoSwordsSkull => "two-swords-skull"
  }

  protected final def decodeAttackFace(value: String, path: String) = value match {
    case "hollow-sword" => Right(AttackDieFace.HollowSword)
    case "one-sword" => Right(AttackDieFace.OneSword)
    case "two-swords-skull" => Right(AttackDieFace.TwoSwordsSkull)
    case other => Left(InvalidValue(path, s"unknown attack die face '$other'"))
  }

  protected final def decodeDefenseFace(value: String, path: String) = value match {
    case "blank" => Right(DefenseDieFace.Blank)
    case "one-shield" => Right(DefenseDieFace.OneShield)
    case "two-shields" => Right(DefenseDieFace.TwoShields)
    case "doubler" => Right(DefenseDieFace.Doubler)
    case other => Left(InvalidValue(path, s"unknown defense die face '$other'"))
  }

  protected final def encodeWorldCard(id: WorldCardId): ujson.Value = id match {
    case value: DenizenId => ujson.Obj("kind" -> "denizen", "id" -> value.value)
    case value: VisionId => ujson.Obj("kind" -> "vision", "id" -> value.value)
  }

  protected final def decodeCampaignKind(value: ujson.Value, path: String)
      : Either[WireError, CampaignKind] = value.str match {
    case "conquest" => Right(CampaignKind.Conquest)
    case "raid" => Right(CampaignKind.Raid)
    case other => Left(InvalidValue(path, s"unknown Campaign kind '$other'"))
  }

  protected final def encodeCampaignRaidTarget(target: CampaignRaidTarget): ujson.Value =
    target match {
      case CampaignRaidTarget.Pawn(player) => ujson.Obj(
        "kind" -> "pawn", "playerId" -> player.value)
      case CampaignRaidTarget.Relic(player, relic) => ujson.Obj(
        "kind" -> "relic", "playerId" -> player.value,
        "relicId" -> relic.value)
      case CampaignRaidTarget.Banner(player, banner) => ujson.Obj(
        "kind" -> "banner", "playerId" -> player.value,
        "banner" -> banner.key)
    }

  protected final def decodeCampaignRaidTarget(value: ujson.Value, path: String)
      : Either[WireError, CampaignRaidTarget] = try value("kind").str match {
    case "pawn" => Right(CampaignRaidTarget.Pawn(
      PlayerId(value("playerId").str)))
    case "relic" => Right(CampaignRaidTarget.Relic(
      PlayerId(value("playerId").str), RelicId(value("relicId").str)))
    case "banner" => decodeBanner(value("banner").str, s"$path.banner")
      .map(CampaignRaidTarget.Banner(PlayerId(value("playerId").str), _))
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown Campaign Raid target '$other'"))
  } catch { case error: Exception => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid Campaign Raid target"))) }

  protected final def decodeWorldCard(value: ujson.Value, path: String)
      : Either[WireError, WorldCardId] = try value("kind").str match {
    case "denizen" => Right(DenizenId(value("id").str))
    case "vision" => Right(VisionId(value("id").str))
    case other => Left(InvalidValue(s"$path.kind", s"unknown world card kind '$other'"))
  } catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid world card"))) }

  protected final def encodeSearchSource(source: SearchSource): ujson.Value = source match {
    case SearchSource.WorldDeck => ujson.Obj("kind" -> "world")
    case SearchSource.RegionalDiscard(region) =>
      ujson.Obj("kind" -> "regional-discard", "region" -> region.key)
  }

  protected final def decodeSearchSource(value: ujson.Value, path: String)
      : Either[WireError, SearchSource] = try value("kind").str match {
    case "world" => Right(SearchSource.WorldDeck)
    case "regional-discard" => decodeRegion(value("region").str, s"$path.region")
      .map(SearchSource.RegionalDiscard)
    case other => Left(InvalidValue(s"$path.kind", s"unknown Search source '$other'"))
  } catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid Search source"))) }

  protected final def decodeRegion(value: String, path: String): Either[WireError, Region] =
    Region.all.find(_.key == value).toRight(InvalidValue(path, s"unknown region '$value'"))

  protected final def decodeSuit(value: String, path: String): Either[WireError, Suit] =
    Suit.fromKey(value).toRight(InvalidValue(path, s"unknown suit '$value'"))

  protected final def encodeCardRef(id: CardId): ujson.Value = id match {
    case value: DenizenId => encodeWorldCard(value)
    case value: VisionId => encodeWorldCard(value)
    case value: EdificeId => ujson.Obj("kind" -> "edifice", "id" -> value.value)
    case value: RelicId => ujson.Obj("kind" -> "relic", "id" -> value.value)
    case value: LegacyId => ujson.Obj("kind" -> "legacy", "id" -> value.value)
  }

  protected final def decodeCardRef(value: ujson.Value, path: String): Either[WireError, CardId] =
    try value("kind").str match {
      case "denizen" => Right(DenizenId(value("id").str))
      case "vision" => Right(VisionId(value("id").str))
      case "edifice" => Right(EdificeId(value("id").str))
      case "relic" => Right(RelicId(value("id").str))
      case "legacy" => Right(LegacyId(value("id").str))
      case other => Left(InvalidValue(s"$path.kind", s"unknown card kind '$other'"))
    } catch { case NonFatal(error) => Left(InvalidValue(path,
      Option(error.getMessage).getOrElse("invalid card reference"))) }

  protected final def encodeSearchPlacement(value: SearchPlacement): ujson.Value = value match {
    case SearchPlacement.Discard => ujson.Obj("kind" -> "discard")
    case SearchPlacement.Site(replace) => ujson.Obj(
      "kind" -> "site", "replace" -> replace.fold[ujson.Value](ujson.Null)(encodeCardRef))
    case SearchPlacement.Adviser(orientation, replace) => ujson.Obj(
      "kind" -> "adviser",
      "orientation" -> (if (orientation == Orientation.FaceUp) "face-up" else "face-down"),
      "replace" -> replace.fold[ujson.Value](ujson.Null)(encodeCardRef))
  }

  protected final def decodeSearchPlacement(value: ujson.Value, path: String)
      : Either[WireError, SearchPlacement] = {
    def replacement: Either[WireError, Option[CardId]] = value("replace") match {
      case ujson.Null => Right(None)
      case card => decodeCardRef(card, s"$path.replace").map(Some(_))
    }
    try value("kind").str match {
      case "discard" => Right(SearchPlacement.Discard)
      case "site" => replacement.map(SearchPlacement.Site)
      case "adviser" => for {
        orientation <- value("orientation").str match {
          case "face-up" => Right(Orientation.FaceUp)
          case "face-down" => Right(Orientation.FaceDown)
          case other => Left(InvalidValue(s"$path.orientation", s"unknown orientation '$other'"))
        }
        replace <- replacement
      } yield SearchPlacement.Adviser(orientation, replace)
      case other => Left(InvalidValue(s"$path.kind", s"unknown placement '$other'"))
    } catch { case NonFatal(error) => Left(InvalidValue(path,
      Option(error.getMessage).getOrElse("invalid Search placement"))) }
  }

  protected final def traverse[A, B](
      values: Vector[A]
  )(f: A => Either[WireError, B]): Either[WireError, Vector[B]] =
    values.foldLeft[Either[WireError, Vector[B]]](Right(Vector.empty)) {
      case (Right(acc), value) => f(value).map(acc :+ _)
      case (failure @ Left(_), _) => failure
    }
}
