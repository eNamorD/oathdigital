package oathdigital.serialization

import scala.util.control.NonFatal
import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.OathEvent._

/** Shared primitive and nested-model JSON vocabulary for v1 event families. */
private[serialization] trait GameEventJsonSupport {
  import WireError._

  protected final def decodeBanner(value: String, path: String): Either[WireError, Banner] =
    Banner.fromKey(value).toRight(InvalidValue(path, s"unknown banner '$value'"))

  protected final def encodeNegotiationTerms(terms: NegotiationTerms): ujson.Value = ujson.Obj(
    "transfers" -> ujson.Arr.from(terms.transfers.map(transfer => ujson.Obj(
      "recipientPlayerId" -> transfer.recipient.value, "favor" -> transfer.favor,
      "relicIds" -> stringArray(transfer.relics.map(_.value))))),
    "disclosures" -> ujson.Arr.from(terms.disclosures.map { disclosure =>
      val information = disclosure.information match {
        case NegotiationDisclosureRef.Adviser(owner, card) => ujson.Obj(
          "kind" -> "adviser", "ownerPlayerId" -> owner.value,
          "card" -> encodeWorldCard(card))
        case NegotiationDisclosureRef.HeldRelic(owner, relic) => ujson.Obj(
          "kind" -> "held-relic", "ownerPlayerId" -> owner.value,
          "relicId" -> relic.value)
        case NegotiationDisclosureRef.SiteRelic(site, relic) => ujson.Obj(
          "kind" -> "site-relic", "siteId" -> site.value, "relicId" -> relic.value)
      }
      ujson.Obj("recipientPlayerId" -> disclosure.recipient.value,
        "information" -> information)
    }))

  protected final def decodeNegotiationTerms(value: ujson.Value,
      path: String): Either[WireError, NegotiationTerms] = try {
    for {
      transfers <- traverse(value("transfers").arr.toVector) { row => for {
        favor <- safeIntField(row.obj, "favor", s"$path.transfers")
      } yield NegotiationTransfer(PlayerId(row("recipientPlayerId").str), favor,
        row("relicIds").arr.toVector.map(v => RelicId(v.str))) }
      disclosures <- traverse(value("disclosures").arr.toVector) { row =>
        val info = row("information")
        val decoded: Either[WireError, NegotiationDisclosureRef] = info("kind").str match {
          case "adviser" => decodeWorldCard(info("card"), s"$path.disclosures.card")
            .map(card => NegotiationDisclosureRef.Adviser(
              PlayerId(info("ownerPlayerId").str), card))
          case "held-relic" => Right(NegotiationDisclosureRef.HeldRelic(
            PlayerId(info("ownerPlayerId").str), RelicId(info("relicId").str)))
          case "site-relic" => Right(NegotiationDisclosureRef.SiteRelic(
            SiteId(info("siteId").str), RelicId(info("relicId").str)))
          case other => Left(InvalidValue(s"$path.disclosures.kind",
            s"unknown disclosure kind '$other'"))
        }
        decoded.map(NegotiationDisclosure(PlayerId(row("recipientPlayerId").str), _))
      }
    } yield NegotiationTerms(transfers, disclosures)
  } catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid Negotiation terms"))) }

  protected final def encodePlan(plan: FirstGameSetupPlan): ujson.Value =
    ujson.Obj(
      "catalog" -> encodeCatalog(plan.catalog),
      "participants" -> ujson.Arr.from(plan.participants.map { participant =>
        ujson.Obj(
          "playerId" -> participant.playerId.value,
          "lineageId" -> participant.lineageId.value,
          "color" -> participant.color.value
        )
      }),
      "firstPlayer" -> plan.firstPlayer.value,
      "oathkeeperGoal" -> plan.oathkeeperGoal.key,
      "orderedSites" -> stringArray(plan.orderedSites.map(_.value)),
      "denizenOrder" -> stringArray(plan.denizenOrder.map(_.value)),
      "worldDeckOrder" -> ujson.Arr.from(
        plan.worldDeckOrder.map {
          case id: DenizenId =>
            ujson.Obj("kind" -> "denizen", "id" -> id.value)
          case id: VisionId =>
            ujson.Obj("kind" -> "vision", "id" -> id.value)
        }
      ),
      "relicOrder" -> stringArray(plan.relicOrder.map(_.value)),
      "homelandEdifices" -> ujson.Arr.from(
        plan.homelandEdifices.map { case (siteId, edificeId) =>
          ujson.Obj(
            "siteId" -> siteId.value,
            "edificeId" -> edificeId.value
          )
        }
      )
    )

  protected final def decodePlan(
      value: ujson.Value,
      path: String
  ): Either[WireError, FirstGameSetupPlan] =
    try {
      val obj = value.obj
      for {
        catalog <- decodeCatalog(obj("catalog"), s"$path.catalog")
        participants <- traverse(
          obj("participants").arr.zipWithIndex.toVector
        ) { case (participant, _) =>
          Right(
            FirstGameParticipant(
              PlayerId(participant("playerId").str),
              LineageId(participant("lineageId").str),
              PlayerColor(participant("color").str)
            )
          )
        }
        world <- traverse(obj("worldDeckOrder").arr.toVector) { item =>
          item("kind").str match {
            case "denizen" =>
              Right(DenizenId(item("id").str): WorldCardId)
            case "vision" =>
              Right(VisionId(item("id").str): WorldCardId)
            case kind =>
              Left(
                InvalidValue(
                  s"$path.worldDeckOrder",
                  s"unknown card kind $kind"
                )
              )
          }
        }
        oathkeeperGoal <- OathkeeperGoal.all
          .find(_.key == obj("oathkeeperGoal").str)
          .toRight(InvalidValue(s"$path.oathkeeperGoal",
            s"unknown Oathkeeper goal '${obj("oathkeeperGoal").str}'"))
      } yield FirstGameSetupPlan(
        catalog,
        participants,
        PlayerId(obj("firstPlayer").str),
        obj("orderedSites").arr.toVector.map(v => SiteId(v.str)),
        obj("denizenOrder").arr.toVector.map(v => DenizenId(v.str)),
        world,
        obj("relicOrder").arr.toVector.map(v => RelicId(v.str)),
        obj("homelandEdifices").arr.toVector.map { entry =>
          SiteId(entry("siteId").str) -> EdificeId(entry("edificeId").str)
        },
        oathkeeperGoal
      )
    } catch {
      case NonFatal(error) =>
        Left(
          InvalidValue(
            path,
            Option(error.getMessage).getOrElse("invalid setup plan")
          )
        )
    }

  protected final def validateEventCatalog(
      event: OathEvent,
      catalog: CatalogRef,
      path: String
  ): Either[WireError, Unit] =
    event match {
      case FirstGameStarted(plan) if plan.catalog != catalog =>
        Left(CatalogMismatch(s"$path.payload.catalog", catalog, plan.catalog))
      case _ => Right(())
    }

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

  protected final def encodeConspiracyTarget(target: ConspiracyTarget): ujson.Value =
    target match {
      case ConspiracyTarget.Relic(owner, relic) => ujson.Obj(
        "kind" -> "relic", "ownerPlayerId" -> owner.value,
        "relicId" -> relic.value)
      case ConspiracyTarget.Banner(owner, banner) => ujson.Obj(
        "kind" -> "banner", "ownerPlayerId" -> owner.value,
        "banner" -> banner.key)
    }

  protected final def decodeOptionalConspiracyTarget(value: ujson.Value, path: String)
      : Either[WireError, Option[ConspiracyTarget]] = value match {
    case ujson.Null => Right(None)
    case other => try other("kind").str match {
      case "relic" => Right(Some(ConspiracyTarget.Relic(
        PlayerId(other("ownerPlayerId").str), RelicId(other("relicId").str))))
      case "banner" => decodeBanner(other("banner").str, s"$path.banner").map(
        banner => Some(ConspiracyTarget.Banner(
          PlayerId(other("ownerPlayerId").str), banner)))
      case kind => Left(InvalidValue(s"$path.kind",
        s"unknown Conspiracy target '$kind'"))
    } catch { case NonFatal(error) => Left(InvalidValue(path,
      Option(error.getMessage).getOrElse("invalid Conspiracy target"))) }
  }

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

  protected final def encodeLosingForceEffect(
      effect: CampaignLosingForceEffect): ujson.Value = {
    val base = ujson.Obj(
      "siteId" -> effect.site.value,
      "force" -> encodeForceKind(effect match {
        case CampaignLosingForceEffect.Remove(_, force, _) => force
        case CampaignLosingForceEffect.Preserve(_, force, _) => force
        case CampaignLosingForceEffect.Relocate(_, _, force, _) => force
        case CampaignLosingForceEffect.Replace(_, force, _, _, _) => force
        case CampaignLosingForceEffect.ReturnToBoard(_, _, force, _) => force
        case CampaignLosingForceEffect.KillCommitted(_, _, force, _) => force
        case CampaignLosingForceEffect.RelocateCommitted(_, _, force, _) => force
        case CampaignLosingForceEffect.PreserveCommitted(_, _, force, _) => force
      }),
      "count" -> (effect match {
        case CampaignLosingForceEffect.Remove(_, _, count) => count
        case CampaignLosingForceEffect.Preserve(_, _, count) => count
        case CampaignLosingForceEffect.Relocate(_, _, _, count) => count
        case CampaignLosingForceEffect.Replace(_, _, count, _, _) => count
        case CampaignLosingForceEffect.ReturnToBoard(_, _, _, count) => count
        case CampaignLosingForceEffect.KillCommitted(_, _, _, count) => count
        case CampaignLosingForceEffect.RelocateCommitted(_, _, _, count) => count
        case CampaignLosingForceEffect.PreserveCommitted(_, _, _, count) => count
      }))
    effect match {
      case _: CampaignLosingForceEffect.Remove => base("kind") = "remove"
      case _: CampaignLosingForceEffect.Preserve => base("kind") = "preserve"
      case CampaignLosingForceEffect.Relocate(_, destination, _, _) =>
        base("kind") = "relocate"
        base("destinationSiteId") = destination.value
      case CampaignLosingForceEffect.Replace(_, _, _, replacement, count) =>
        base("kind") = "replace"
        base("replacementForce") = replacement.map(encodeForceKind)
          .getOrElse(ujson.Null)
        base("replacementCount") = count
      case CampaignLosingForceEffect.ReturnToBoard(_, player, _, _) =>
        base("kind") = "return-to-board"
        base("playerId") = player.value
      case CampaignLosingForceEffect.KillCommitted(_, player, _, _) =>
        base("kind") = "kill-committed"
        base("playerId") = player.value
      case CampaignLosingForceEffect.RelocateCommitted(_, player, _, _) =>
        base("kind") = "relocate-committed"
        base("playerId") = player.value
      case CampaignLosingForceEffect.PreserveCommitted(_, player, _, _) =>
        base("kind") = "preserve-committed"
        base("playerId") = player.value
    }
    base
  }

  protected final def decodeLosingForceEffect(value: ujson.Value, path: String)
      : Either[WireError, CampaignLosingForceEffect] = try {
    val obj = value.obj
    for {
      kind <- stringField(obj, "kind", path)
      site <- stringField(obj, "siteId", path).map(SiteId(_))
      forceValue <- requiredField(obj, "force", path)
      force <- decodeForceKind(forceValue, s"$path.force")
      count <- safeIntField(obj, "count", path)
      _ <- Either.cond(count > 0, (), InvalidValue(
        s"$path.count", "expected a positive integer"))
      effect <- kind match {
        case "remove" => Right(CampaignLosingForceEffect.Remove(site, force, count))
        case "preserve" => Right(CampaignLosingForceEffect.Preserve(site, force, count))
        case "relocate" => stringField(obj, "destinationSiteId", path).flatMap {
          destination => Either.cond(destination != site.value,
            CampaignLosingForceEffect.Relocate(site, SiteId(destination), force, count),
            InvalidValue(s"$path.destinationSiteId",
              "relocation requires a different site"))
        }
        case "replace" => for {
          replacementCount <- safeIntField(obj, "replacementCount", path)
          replacementValue <- requiredField(obj, "replacementForce", path)
          replacement <- replacementValue match {
            case ujson.Null => Right(None)
            case other => decodeForceKind(other, s"$path.replacementForce").map(Some(_))
          }
          _ <- Either.cond(replacement.nonEmpty == (replacementCount > 0), (),
            InvalidValue(s"$path.replacementForce",
              "replacement force and count must agree"))
        } yield CampaignLosingForceEffect.Replace(site, force, count,
          replacement, replacementCount)
        case "return-to-board" => stringField(obj, "playerId", path).map(
          player => CampaignLosingForceEffect.ReturnToBoard(site,
            PlayerId(player), force, count))
        case "kill-committed" => stringField(obj, "playerId", path).map(
          player => CampaignLosingForceEffect.KillCommitted(site,
            PlayerId(player), force, count))
        case "relocate-committed" => stringField(obj, "playerId", path).map(
          player => CampaignLosingForceEffect.RelocateCommitted(site,
            PlayerId(player), force, count))
        case "preserve-committed" => stringField(obj, "playerId", path).map(
          player => CampaignLosingForceEffect.PreserveCommitted(site,
            PlayerId(player), force, count))
        case other => Left(InvalidValue(s"$path.kind",
          s"unknown losing-force effect '$other'"))
      }
    } yield effect
  } catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid losing-force effect"))) }

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

  protected final def encodeCampaignPlanSource(
      source: PendingProcedure.CampaignPlanSource): ujson.Value = source match {
    case PendingProcedure.CampaignPlanSource.Adviser(player, id) => ujson.Obj(
      "kind" -> "adviser", "playerId" -> player.value, "cardId" -> id.value)
    case PendingProcedure.CampaignPlanSource.SiteCard(site, id) => ujson.Obj(
      "kind" -> "site-card", "siteId" -> site.value, "cardId" -> id.value)
    case PendingProcedure.CampaignPlanSource.Relic(player, id) => ujson.Obj(
      "kind" -> "relic", "playerId" -> player.value, "cardId" -> id.value)
    case PendingProcedure.CampaignPlanSource.Title(player) => ujson.Obj(
      "kind" -> "title", "playerId" -> player.value)
  }

  protected final def decodeCampaignPlanSource(value: ujson.Value, path: String)
      : Either[WireError, PendingProcedure.CampaignPlanSource] =
    try value("kind").str match {
      case "adviser" => Right(PendingProcedure.CampaignPlanSource.Adviser(
        PlayerId(value("playerId").str), DenizenId(value("cardId").str)))
      case "site-card" => Right(PendingProcedure.CampaignPlanSource.SiteCard(
        SiteId(value("siteId").str), DenizenId(value("cardId").str)))
      case "relic" => Right(PendingProcedure.CampaignPlanSource.Relic(
        PlayerId(value("playerId").str), RelicId(value("cardId").str)))
      case "title" => Right(PendingProcedure.CampaignPlanSource.Title(
        PlayerId(value("playerId").str)))
      case other => Left(InvalidValue(s"$path.kind",
        s"unknown Campaign plan source '$other'"))
    } catch { case NonFatal(error) => Left(InvalidValue(path,
      Option(error.getMessage).getOrElse("invalid Campaign plan source"))) }

  protected final def encodeCampaignPlanSide(side: PendingProcedure.CampaignPlanSide) =
    ujson.Str(side match {
      case PendingProcedure.CampaignPlanSide.Attacker => "attacker"
      case PendingProcedure.CampaignPlanSide.Defender => "defender"
    })
  protected final def decodeCampaignPlanSide(value: ujson.Value, path: String) = value.str match {
    case "attacker" => Right(PendingProcedure.CampaignPlanSide.Attacker)
    case "defender" => Right(PendingProcedure.CampaignPlanSide.Defender)
    case other => Left(InvalidValue(path, s"unknown Campaign plan side '$other'"))
  }
  protected final def encodeCampaignPlanCost(cost: PendingProcedure.CampaignPlanCost) = cost match {
    case PendingProcedure.CampaignPlanCost.Favor(n) => ujson.Obj("kind" -> "favor", "count" -> n)
    case PendingProcedure.CampaignPlanCost.Secret(n) => ujson.Obj("kind" -> "secret", "count" -> n)
  }
  protected final def decodeCampaignPlanCost(value: ujson.Value, path: String) = value("kind").str match {
    case "favor" => Right(PendingProcedure.CampaignPlanCost.Favor(value("count").num.toInt))
    case "secret" => Right(PendingProcedure.CampaignPlanCost.Secret(value("count").num.toInt))
    case other => Left(InvalidValue(path, s"unknown Campaign plan cost '$other'"))
  }
  protected final def encodeCampaignPlanEffect(effect: PendingProcedure.CampaignPlanEffect) = effect match {
    case PendingProcedure.CampaignPlanEffect.AddAttackDice(n) => ujson.Obj("kind" -> "add-attack-dice", "count" -> n)
    case PendingProcedure.CampaignPlanEffect.AddDefenseDice(n) => ujson.Obj("kind" -> "add-defense-dice", "count" -> n)
    case PendingProcedure.CampaignPlanEffect.IgnoreAttackSkulls => ujson.Obj("kind" -> "ignore-attack-skulls")
    case PendingProcedure.CampaignPlanEffect.RevealSource => ujson.Obj("kind" -> "reveal-source")
    case PendingProcedure.CampaignPlanEffect.TransformAttackResult(id) => ujson.Obj("kind" -> "transform-attack-result", "handlerId" -> id)
    case PendingProcedure.CampaignPlanEffect.ReplaceLosingForcePolicy(id) => ujson.Obj("kind" -> "replace-losing-force-policy", "policyId" -> id)
    case PendingProcedure.CampaignPlanEffect.Suspend(kind) => ujson.Obj("kind" -> "suspend", "decisionKind" -> kind)
  }
  protected final def decodeCampaignPlanEffect(value: ujson.Value, path: String) = value("kind").str match {
    case "add-attack-dice" => Right(PendingProcedure.CampaignPlanEffect.AddAttackDice(value("count").num.toInt))
    case "add-defense-dice" => Right(PendingProcedure.CampaignPlanEffect.AddDefenseDice(value("count").num.toInt))
    case "ignore-attack-skulls" => Right(PendingProcedure.CampaignPlanEffect.IgnoreAttackSkulls)
    case "reveal-source" => Right(PendingProcedure.CampaignPlanEffect.RevealSource)
    case "transform-attack-result" => Right(PendingProcedure.CampaignPlanEffect.TransformAttackResult(value("handlerId").str))
    case "replace-losing-force-policy" => Right(PendingProcedure.CampaignPlanEffect.ReplaceLosingForcePolicy(value("policyId").str))
    case "suspend" => Right(PendingProcedure.CampaignPlanEffect.Suspend(value("decisionKind").str))
    case other => Left(InvalidValue(path, s"unknown Campaign plan effect '$other'"))
  }

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

  protected final def decodeEconomyTarget(value: ujson.Value, path: String)
      : Either[WireError, EconomyTargetRef] =
    decodeCardRef(value, path).flatMap { id =>
      EconomyTargetRef.fromCard(id).toRight(InvalidValue(path,
        "Economy target must be a denizen or edifice"))
    }

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
