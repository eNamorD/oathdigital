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
import oathdigital.gameplay.{TradeResource, WakeResource}
import oathdigital.gameplay.setup.PlayerColor

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
                  "facedownCount" -> site.relics.facedownCount,
                  "knownRelics" -> ujson.Arr.from(
                    site.relics.knownRelics.map(encodeCardDetails))
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
            "winnerPlayerId" -> oath.winnerPlayerId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
            "winnerVictoryKind" -> oath.winnerVictoryKind.fold[ujson.Value](ujson.Null)(ujson.Str(_)))
        },
        "oathkeeperRecipient" -> projection.oathkeeperRecipient.fold[
          ujson.Value](ujson.Null) { decision =>
          ujson.Obj("decisionId" -> decision.decisionId,
            "actorPlayerId" -> decision.actorPlayerId,
            "candidatePlayerIds" -> ujson.Arr.from(
              decision.candidatePlayerIds.map(ujson.Str(_))))
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
            "decisionId" -> action.decisionId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
            "prompt" -> action.prompt,
            "minimum" -> action.minimum,
            "maximum" -> action.maximum,
            "autoActivate" -> action.autoActivate,
            "requiredTargets" -> ujson.Arr.from(
              action.requiredTargets.map(encodeBoardTarget)),
            "formation" -> action.formation.fold[ujson.Value](ujson.Null) { formation =>
              ujson.Obj(
                "minimumForce" -> formation.minimumForce,
                "maximumForce" -> formation.maximumForce,
                "availableWarbands" -> formation.availableWarbands,
                "supplyCost" -> formation.supplyCost)
            },
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
        "forge" -> projection.forge.fold[ujson.Value](ujson.Null) { forge =>
          ujson.Obj("decisionId" -> forge.decisionId,
            "actorPlayerId" -> forge.actorPlayerId,
            "favor" -> forge.favor, "secrets" -> forge.secrets,
            "targets" -> ujson.Arr.from(forge.targets.map(t => ujson.Obj(
              "siteId" -> t.siteId, "denizenId" -> t.denizenId,
              "label" -> t.label))))
        },
        "campaign" -> projection.campaign.fold[ujson.Value](ujson.Null) { campaign =>
          ujson.Obj("decisionId" -> campaign.decisionId,
            "kind" -> campaign.kind,
            "raidTargets" -> ujson.Arr.from(campaign.raidTargets.map(ujson.Str(_))),
            "targetSiteIds" -> ujson.Arr.from(
              campaign.targetSiteIds.map(ujson.Str(_))),
            "defenderKind" -> campaign.defenderKind,
            "defenderPlayerId" -> campaign.defenderPlayerId.fold[ujson.Value](
              ujson.Null)(ujson.Str(_)),
            "defenderForce" -> campaign.defenderForce,
            "defenseDiceCount" -> campaign.defenseDiceCount,
            "planSide" -> campaign.planSide,
            "decisionOwnerPlayerId" -> campaign.decisionOwnerPlayerId.fold[ujson.Value](
              ujson.Null)(ujson.Str(_)),
            "force" -> campaign.force,
            "plansFinished" -> campaign.plansFinished,
            "planChoices" -> ujson.Arr.from(campaign.planChoices.map { choice =>
              ujson.Obj("kind" -> choice.kind,
                "sourceKey" -> choice.sourceKey.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
                "playerId" -> choice.playerId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
                "siteId" -> choice.siteId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
                "cardId" -> choice.cardId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
                "label" -> choice.label,
                "handlerId" -> choice.handlerId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
                "favorCost" -> choice.favorCost, "secretCost" -> choice.secretCost,
                "mechanicalResult" -> choice.mechanicalResult)
            }),
            "selectedPlans" -> ujson.Arr.from(campaign.selectedPlans.map { choice =>
              ujson.Obj("kind" -> choice.kind,
                "sourceKey" -> choice.sourceKey.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
                "playerId" -> choice.playerId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
                "siteId" -> choice.siteId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
                "cardId" -> choice.cardId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
                "label" -> choice.label,
                "handlerId" -> choice.handlerId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
                "favorCost" -> choice.favorCost, "secretCost" -> choice.secretCost,
                "mechanicalResult" -> choice.mechanicalResult)
            }),
            "attackDice" -> ujson.Arr.from(campaign.attackDice.map(ujson.Str(_))),
            "attack" -> campaign.attack, "skullLosses" -> campaign.skullLosses,
            "maxSacrifice" -> campaign.maxSacrifice,
            "sacrificed" -> campaign.sacrificed.fold[ujson.Value](ujson.Null)(ujson.Num(_)),
            "defenseDice" -> ujson.Arr.from(campaign.defenseDice.map(ujson.Str(_))),
            "defense" -> campaign.defense.fold[ujson.Value](ujson.Null)(ujson.Num(_)),
            "victorious" -> campaign.victorious.fold[ujson.Value](ujson.Null)(ujson.Bool(_)),
            "maxPlacement" -> campaign.maxPlacement,
            "placementTargets" -> ujson.Arr.from(
              campaign.placementTargets.map(target => ujson.Obj(
                "siteId" -> target.siteId, "label" -> target.label))))
        },
        "campaignRaidRelocation" -> projection.campaignRaidRelocation.fold[ujson.Value](
          ujson.Null) { relocation =>
          ujson.Obj("decisionId" -> relocation.decisionId,
            "actorPlayerId" -> relocation.actorPlayerId,
            "defenderPlayerId" -> relocation.defenderPlayerId,
            "originSiteId" -> relocation.originSiteId,
            "legalSiteIds" -> ujson.Arr.from(
              relocation.legalSiteIds.map(ujson.Str(_))))
        },
        "banners" -> ujson.Arr.from(projection.banners.map(b => ujson.Obj(
          "banner" -> b.key, "face" -> b.face,
          "holderPlayerId" -> b.holderPlayerId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
          "resources" -> b.resources))),
        "challenge" -> projection.challenge.fold[ujson.Value](ujson.Null) { c =>
          ujson.Obj("decisionId" -> c.decisionId, "actorPlayerId" -> c.actorPlayerId,
            "banner" -> c.banner,
            "priorHolderPlayerId" -> c.priorHolderPlayerId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
            "priorResources" -> c.priorResources,
            "legalSecretSiteIds" -> ujson.Arr.from(c.legalSecretSiteIds.map(ujson.Str(_))),
            "minimumPlacement" -> c.minimumPlacement,
            "maximumPlacement" -> c.maximumPlacement)
        },
        "minorActions" -> projection.minorActions.fold[ujson.Value](ujson.Null) { minor =>
          ujson.Obj(
            "advisers" -> ujson.Arr.from(minor.advisers.map(encodeMinorAdviser)),
            "canPeekSiteRelics" -> minor.canPeekSiteRelics,
            "facedownRelics" -> ujson.Arr.from(minor.facedownRelics.map(encodeCardDetails)),
            "siteId" -> minor.siteId.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
            "maxBoardToSite" -> minor.maxBoardToSite,
            "maxSiteToBoard" -> minor.maxSiteToBoard)
        },
        "negotiation" -> projection.negotiation.fold[ujson.Value](ujson.Null) { deal =>
          ujson.Obj("decisionId" -> deal.decisionId,
            "actorPlayerId" -> deal.actorPlayerId, "siteId" -> deal.siteId,
            "participantPlayerIds" -> ujson.Arr.from(deal.participantPlayerIds.map(ujson.Str(_))),
            "acceptedPlayerIds" -> ujson.Arr.from(deal.acceptedPlayerIds.map(ujson.Str(_))),
            "transfers" -> ujson.Arr.from(deal.transfers.map(t => ujson.Obj(
              "authorPlayerId" -> t.authorPlayerId,
              "recipientPlayerId" -> t.recipientPlayerId, "favor" -> t.favor,
              "relicCount" -> t.relicCount,
              "relics" -> ujson.Arr.from(t.relics.map(encodeCardDetails))))),
            "disclosures" -> ujson.Arr.from(deal.disclosures.map(d => ujson.Obj(
              "authorPlayerId" -> d.authorPlayerId,
              "recipientPlayerId" -> d.recipientPlayerId, "kind" -> d.kind,
              "card" -> d.card.fold[ujson.Value](ujson.Null)(encodeCardDetails)))),
            "editableFavor" -> deal.editableFavor,
            "editableRelics" -> ujson.Arr.from(deal.editableRelics.map(encodeCardDetails)),
            "editableAdvisers" -> ujson.Arr.from(deal.editableAdvisers.map(encodeCardDetails)),
            "editableSiteRelics" -> ujson.Arr.from(
              deal.editableSiteRelics.map(encodeCardDetails)))
        },
        "negotiationWaiting" -> projection.negotiationWaiting,
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

  private def encodeMinorAdviser(
      adviser: oathdigital.application.MinorAdviserProjection): ujson.Obj =
    ujson.Obj("card" -> encodeCardDetails(adviser.card),
      "placements" -> ujson.Arr.from(adviser.placements.map { placement =>
        ujson.Obj("kind" -> placement.kind,
          "orientation" -> placement.orientation.fold[ujson.Value](ujson.Null)(ujson.Str(_)),
          "replacementRequired" -> placement.replacementRequired,
          "replacementTargets" -> ujson.Arr.from(
            placement.replacementTargets.map(encodeCardDetails)))
      }))

  private def encodeBoardTarget(
      target: oathdigital.application.BoardTargetRefProjection
  ): ujson.Obj = target match {
    case oathdigital.application.BoardTargetRefProjection.Player(playerId) =>
      ujson.Obj("kind" -> "player", "playerId" -> playerId)
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
    case oathdigital.application.BoardTargetRefProjection.PlayerPawn(playerId) =>
      ujson.Obj("kind" -> "player-pawn", "playerId" -> playerId)
    case oathdigital.application.BoardTargetRefProjection.PlayerBanner(playerId, banner) =>
      ujson.Obj("kind" -> "player-banner", "playerId" -> playerId,
        "banner" -> banner)
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
      case "beginForge" =>
        exactFields(obj, Set("type", "playerId"), path)
          .flatMap(_ => stringField(obj, "playerId", path))
          .map(p => GameCommand.BeginForge(PlayerId(p)))
      case "completeForge" => for {
        _ <- exactFields(obj,
          Set("type", "playerId", "decisionId", "assignments"), path)
        p <- stringField(obj, "playerId", path)
        decision <- stringField(obj, "decisionId", path)
        values <- field(obj, "assignments", path).flatMap(arrayValue(_, s"$path.assignments"))
        assignments <- traverse(values.zipWithIndex) { case (value, index) =>
          val pth = s"$path.assignments[$index]"
          for {
            a <- objectValue(value, pth)
            _ <- exactFields(a, Set("siteId", "denizenId", "resource"), pth)
            site <- stringField(a, "siteId", pth)
            denizen <- stringField(a, "denizenId", pth)
            name <- stringField(a, "resource", pth)
            resource <- name match {
              case "favor" => Right(ForgeResource.Favor)
              case "secret" => Right(ForgeResource.Secret)
              case other => Left(HttpInputError(s"$pth.resource", s"unknown Forge resource '$other'"))
            }
          } yield ForgeResourceAssignment(SiteDenizenTarget(SiteId(site), DenizenId(denizen)), resource)
        }
      } yield GameCommand.CompleteForge(PlayerId(p), DecisionId(decision), assignments)
      case "beginChallenge" => for {
        _ <- exactFields(obj, Set("type", "playerId", "banner"), path)
        p <- stringField(obj, "playerId", path)
        key <- stringField(obj, "banner", path)
        banner <- Banner.fromKey(key).toRight(HttpInputError(s"$path.banner", "unknown banner"))
      } yield GameCommand.BeginChallenge(PlayerId(p), banner)
      case "chooseChallengeSecretSite" => for {
        _ <- exactFields(obj, Set("type", "playerId", "decisionId", "siteId"), path)
        p <- stringField(obj, "playerId", path); d <- stringField(obj, "decisionId", path)
        site <- stringField(obj, "siteId", path)
      } yield GameCommand.ChooseChallengeSecretSite(PlayerId(p), DecisionId(d), SiteId(site))
      case "completeChallenge" => for {
        _ <- exactFields(obj, Set("type", "playerId", "decisionId", "amount"), path)
        p <- stringField(obj, "playerId", path); d <- stringField(obj, "decisionId", path)
        amount <- field(obj, "amount", path).flatMap(v => nonNegativeInt(v, s"$path.amount"))
      } yield GameCommand.CompleteChallenge(PlayerId(p), DecisionId(d), amount)
      case "placeBannerResource" => for {
        _ <- exactFields(obj, Set("type", "playerId", "banner", "amount"), path)
        p <- stringField(obj, "playerId", path); key <- stringField(obj, "banner", path)
        banner <- Banner.fromKey(key).toRight(HttpInputError(s"$path.banner", "unknown banner"))
        amount <- field(obj, "amount", path).flatMap(v => nonNegativeInt(v, s"$path.amount"))
      } yield GameCommand.PlaceBannerResource(PlayerId(p), banner, amount)
      case "discardFacedownAdviser" => for {
        _ <- exactFields(obj, Set("type", "playerId", "adviser"), path)
        p <- stringField(obj, "playerId", path)
        value <- field(obj, "adviser", path)
        adviser <- decodeWorldCard(value, s"$path.adviser")
      } yield GameCommand.DiscardFacedownAdviser(PlayerId(p), adviser)
      case "playFacedownAdviser" => for {
        _ <- exactFields(obj, Set("type", "playerId", "adviser", "placement"), path)
        p <- stringField(obj, "playerId", path)
        value <- field(obj, "adviser", path)
        adviser <- decodeWorldCard(value, s"$path.adviser")
        placementValue <- field(obj, "placement", path)
        placement <- decodePlacement(placementValue, s"$path.placement")
      } yield GameCommand.PlayFacedownAdviser(PlayerId(p), adviser, placement)
      case "revealVision" => for {
        _ <- exactFields(obj, Set("type", "playerId", "visionId"), path)
        p <- stringField(obj, "playerId", path)
        vision <- stringField(obj, "visionId", path)
      } yield GameCommand.RevealVision(PlayerId(p), VisionId(vision))
      case "playConspiracy" => for {
        _ <- exactFields(obj, Set("type", "playerId", "target"), path)
        p <- stringField(obj, "playerId", path)
        targetValue <- field(obj, "target", path)
        target <- decodeConspiracyTarget(targetValue, s"$path.target")
      } yield GameCommand.PlayConspiracy(PlayerId(p), target)
      case "chooseConspiracySecretSite" => for {
        _ <- exactFields(obj, Set("type", "playerId", "decisionId", "siteId"), path)
        p <- stringField(obj, "playerId", path)
        decision <- stringField(obj, "decisionId", path)
        site <- stringField(obj, "siteId", path)
      } yield GameCommand.ChooseConspiracySecretSite(
        PlayerId(p), DecisionId(decision), SiteId(site))
      case "peekSiteRelics" =>
        exactFields(obj, Set("type", "playerId"), path)
          .flatMap(_ => stringField(obj, "playerId", path))
          .map(p => GameCommand.PeekSiteRelics(PlayerId(p)))
      case "revealOwnedRelic" => for {
        _ <- exactFields(obj, Set("type", "playerId", "relicId"), path)
        p <- stringField(obj, "playerId", path)
        relic <- stringField(obj, "relicId", path)
      } yield GameCommand.RevealOwnedRelic(PlayerId(p), RelicId(relic))
      case "moveWarbands" => for {
        _ <- exactFields(obj, Set("type", "playerId", "toSite", "amount"), path)
        p <- stringField(obj, "playerId", path)
        toSite <- field(obj, "toSite", path).flatMap {
          case ujson.Bool(value) => Right(value)
          case _ => Left(HttpInputError(s"$path.toSite", "expected boolean"))
        }
        amount <- field(obj, "amount", path).flatMap(v => nonNegativeInt(v, s"$path.amount"))
      } yield GameCommand.MoveWarbands(PlayerId(p), toSite, amount)
      case "beginNegotiation" => for {
        _ <- exactFields(obj, Set("type", "playerId", "participantPlayerIds"), path)
        p <- stringField(obj, "playerId", path)
        values <- field(obj, "participantPlayerIds", path).flatMap {
          case array: ujson.Arr => Right(array.value.toVector)
          case _ => Left(HttpInputError(s"$path.participantPlayerIds", "expected array"))
        }
      } yield GameCommand.BeginNegotiation(PlayerId(p), values.map(v => PlayerId(v.str)))
      case "replaceNegotiationTerms" => for {
        _ <- exactFields(obj, Set("type", "playerId", "decisionId", "terms"), path)
        p <- stringField(obj, "playerId", path); d <- stringField(obj, "decisionId", path)
        termsValue <- field(obj, "terms", path)
        terms <- NegotiationHttpCodec.decodeTerms(termsValue, s"$path.terms")
      } yield GameCommand.ReplaceNegotiationTerms(PlayerId(p), DecisionId(d), terms)
      case "acceptNegotiation" => for {
        _ <- exactFields(obj, Set("type", "playerId", "decisionId"), path)
        p <- stringField(obj, "playerId", path); d <- stringField(obj, "decisionId", path)
      } yield GameCommand.AcceptNegotiation(PlayerId(p), DecisionId(d))
      case "declineNegotiation" => for {
        _ <- exactFields(obj, Set("type", "playerId", "decisionId"), path)
        p <- stringField(obj, "playerId", path); d <- stringField(obj, "decisionId", path)
      } yield GameCommand.DeclineNegotiation(PlayerId(p), DecisionId(d))
      case "addRecoverDice" => for {
        p <- stringField(obj, "playerId", path)
        d <- stringField(obj, "decisionId", path)
      } yield GameCommand.AddRecoverDice(PlayerId(p), DecisionId(d))
      case "stopRecover" => for {
        p <- stringField(obj, "playerId", path)
        d <- stringField(obj, "decisionId", path)
      } yield GameCommand.StopRecover(PlayerId(p), DecisionId(d))
      case "beginCampaignConquest" => for {
        _ <- exactFields(obj,
          Set("type", "playerId", "targetSiteIds", "attackDiceCount"), path)
        player <- stringField(obj, "playerId", path)
        sitesValue <- field(obj, "targetSiteIds", path)
        siteValues <- arrayValue(sitesValue, s"$path.targetSiteIds")
        sites <- siteValues.zipWithIndex.foldLeft[
          Either[HttpInputError, Vector[SiteId]]](Right(Vector.empty)) {
            case (result, (value, index)) => result.flatMap(existing => value match {
              case ujson.Str(site) => Right(existing :+ SiteId(site))
              case _ => Left(HttpInputError(
                s"$path.targetSiteIds[$index]", "expected a string"))
            })
          }
        countValue <- field(obj, "attackDiceCount", path)
        count <- nonNegativeInt(countValue, s"$path.attackDiceCount")
      } yield GameCommand.BeginCampaignConquest(
        PlayerId(player), sites, count)
      case "beginCampaignRaid" => for {
        _ <- exactFields(obj,
          Set("type", "playerId", "targets", "attackDiceCount"), path)
        player <- stringField(obj, "playerId", path)
        values <- field(obj, "targets", path).flatMap(arrayValue(_, s"$path.targets"))
        targets <- values.zipWithIndex.foldLeft[
          Either[HttpInputError, Vector[CampaignRaidTarget]]](Right(Vector.empty)) {
          case (result, (value, index)) => result.flatMap(existing =>
            decodeRaidTarget(value, s"$path.targets[$index]").map(existing :+ _))
        }
        countValue <- field(obj, "attackDiceCount", path)
        count <- nonNegativeInt(countValue, s"$path.attackDiceCount")
      } yield GameCommand.BeginCampaignRaid(PlayerId(player), targets, count)
      case "chooseCampaignPlan" => for {
        _ <- exactFields(obj, Set("type", "playerId", "decisionId", "source"), path)
        player <- stringField(obj, "playerId", path)
        decision <- stringField(obj, "decisionId", path)
        sourceValue <- field(obj, "source", path)
        source <- decodeCampaignPlanSource(sourceValue, s"$path.source")
        selected <- source.toRight(HttpInputError(s"$path.source", "plan source is required"))
      } yield GameCommand.ChooseCampaignPlan(PlayerId(player),
        DecisionId(decision), selected)
      case "finishCampaignPlans" => for {
        _ <- exactFields(obj, Set("type", "playerId", "decisionId"), path)
        player <- stringField(obj, "playerId", path)
        decision <- stringField(obj, "decisionId", path)
      } yield GameCommand.FinishCampaignPlans(PlayerId(player), DecisionId(decision))
      case "chooseCampaignSacrifice" => for {
        _ <- exactFields(obj, Set("type", "playerId", "decisionId", "count"), path)
        player <- stringField(obj, "playerId", path)
        decision <- stringField(obj, "decisionId", path)
        countValue <- field(obj, "count", path)
        count <- nonNegativeInt(countValue, s"$path.count")
      } yield GameCommand.ChooseCampaignSacrifice(
        PlayerId(player), DecisionId(decision), count)
      case "placeCampaignForce" => for {
        _ <- exactFields(obj,
          Set("type", "playerId", "decisionId", "allocations"), path)
        player <- stringField(obj, "playerId", path)
        decision <- stringField(obj, "decisionId", path)
        values <- field(obj, "allocations", path).flatMap(
          arrayValue(_, s"$path.allocations"))
        allocations <- values.zipWithIndex.foldLeft[
          Either[HttpInputError, Vector[CampaignForceAllocation]]](
          Right(Vector.empty)) { case (result, (value, index)) =>
            val itemPath = s"$path.allocations[$index]"
            result.flatMap(existing => objectValue(value, itemPath).flatMap { item =>
              for {
                _ <- exactFields(item, Set("siteId", "count"), itemPath)
                site <- stringField(item, "siteId", itemPath)
                countValue <- field(item, "count", itemPath)
                count <- nonNegativeInt(countValue, s"$itemPath.count")
              } yield existing :+ CampaignForceAllocation(SiteId(site), count)
            })
          }
      } yield GameCommand.PlaceCampaignForce(
        PlayerId(player), DecisionId(decision), allocations)
      case "relocateCampaignRaidPawn" => for {
        _ <- exactFields(obj,
          Set("type", "playerId", "decisionId", "destinationSiteId"), path)
        player <- stringField(obj, "playerId", path)
        decision <- stringField(obj, "decisionId", path)
        destination <- stringField(obj, "destinationSiteId", path)
      } yield GameCommand.RelocateCampaignRaidPawn(PlayerId(player),
        DecisionId(decision), SiteId(destination))
      case "chooseOathkeeperRecipient" => for {
        _ <- exactFields(obj, Set("type", "playerId", "decisionId",
          "recipientPlayerId"), path)
        player <- stringField(obj, "playerId", path)
        decision <- stringField(obj, "decisionId", path)
        recipient <- stringField(obj, "recipientPlayerId", path)
      } yield GameCommand.ChooseOathkeeperRecipient(
        PlayerId(player), DecisionId(decision), PlayerId(recipient))
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

  private def decodeConspiracyTarget(value: ujson.Value, path: String)
      : Either[HttpInputError, Option[ConspiracyTargetRef]] = value match {
    case ujson.Null => Right(None)
    case other => objectValue(other, path).flatMap { obj =>
      stringField(obj, "kind", path).flatMap {
        case "relic-slot" => for {
          _ <- exactFields(obj, Set("kind", "ownerPlayerId", "slot"), path)
          owner <- stringField(obj, "ownerPlayerId", path)
          slotValue <- field(obj, "slot", path)
          slot <- nonNegativeInt(slotValue, s"$path.slot")
        } yield Some(ConspiracyTargetRef.RelicSlot(PlayerId(owner), slot))
        case "banner" => for {
          _ <- exactFields(obj, Set("kind", "ownerPlayerId", "banner"), path)
          owner <- stringField(obj, "ownerPlayerId", path)
          key <- stringField(obj, "banner", path)
          banner <- Banner.fromKey(key).toRight(HttpInputError(
            s"$path.banner", s"unknown banner '$key'"))
        } yield Some(ConspiracyTargetRef.Banner(PlayerId(owner), banner))
        case kind => Left(HttpInputError(s"$path.kind",
          s"unknown Conspiracy target '$kind'"))
      }
    }
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

  private def decodeCampaignPlanSource(value: ujson.Value, path: String)
      : Either[HttpInputError, Option[PendingProcedure.CampaignPlanSource]] = value match {
    case ujson.Null => Right(None)
    case _ => objectValue(value, path).flatMap { obj =>
      stringField(obj, "kind", path).flatMap {
        case "adviser" => for {
          _ <- exactFields(obj, Set("kind", "playerId", "cardId"), path)
          player <- stringField(obj, "playerId", path)
          card <- stringField(obj, "cardId", path)
        } yield Some(PendingProcedure.CampaignPlanSource.Adviser(
          PlayerId(player), DenizenId(card)))
        case "site-card" => for {
          _ <- exactFields(obj, Set("kind", "siteId", "cardId"), path)
          site <- stringField(obj, "siteId", path)
          card <- stringField(obj, "cardId", path)
        } yield Some(PendingProcedure.CampaignPlanSource.SiteCard(
          SiteId(site), DenizenId(card)))
        case "relic" => for {
          _ <- exactFields(obj, Set("kind", "playerId", "cardId"), path)
          player <- stringField(obj, "playerId", path)
          card <- stringField(obj, "cardId", path)
        } yield Some(PendingProcedure.CampaignPlanSource.Relic(
          PlayerId(player), RelicId(card)))
        case "title" => for {
          _ <- exactFields(obj, Set("kind", "playerId"), path)
          player <- stringField(obj, "playerId", path)
        } yield Some(PendingProcedure.CampaignPlanSource.Title(PlayerId(player)))
        case other => Left(HttpInputError(s"$path.kind",
          s"unknown Campaign plan source '$other'"))
      }
    }
  }

  private def nonNegativeInt(value: ujson.Value, path: String)
      : Either[HttpInputError, Int] = value match {
    case ujson.Num(number) if number.isFinite && number == Math.rint(number) &&
        number >= 0 && number <= Int.MaxValue => Right(number.toInt)
    case _: ujson.Num => Left(HttpInputError(path,
      "expected a non-negative 32-bit integer"))
    case _ => Left(HttpInputError(path, "expected a number"))
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

  private def decodeRaidTarget(value: ujson.Value, path: String)
      : Either[HttpInputError, CampaignRaidTarget] =
    objectValue(value, path).flatMap { obj =>
      stringField(obj, "kind", path).flatMap {
        case "pawn" => for {
          _ <- exactFields(obj, Set("kind", "playerId"), path)
          player <- stringField(obj, "playerId", path)
        } yield CampaignRaidTarget.Pawn(PlayerId(player))
        case "relic" => for {
          _ <- exactFields(obj, Set("kind", "playerId", "relicId"), path)
          player <- stringField(obj, "playerId", path)
          relic <- stringField(obj, "relicId", path)
        } yield CampaignRaidTarget.Relic(PlayerId(player), RelicId(relic))
        case "peoples-favor" | "darkest-secret" => for {
          _ <- exactFields(obj, Set("kind", "playerId"), path)
          kind <- stringField(obj, "kind", path)
          player <- stringField(obj, "playerId", path)
        } yield CampaignRaidTarget.Banner(PlayerId(player),
          if (kind == "peoples-favor") CampaignBanner.PeoplesFavor
          else CampaignBanner.DarkestSecret)
        case other => Left(HttpInputError(s"$path.kind",
          s"unknown Campaign Raid target '$other'"))
      }
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
