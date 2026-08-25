package oathdigital.server

import scala.util.control.NonFatal

import oathdigital.application.{
  BootstrapParticipant,
  FirstGameBootstrapConfig,
  GameIntent,
  GameProjection
}
import oathdigital.model._
import oathdigital.serialization.GameEventWire
import oathdigital.gameplay.setup.PlayerColor

final case class GameCommandRequest(
    expectedNextSequence: Long,
    intent: GameIntent
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
    AuthenticatedGameHttpWire.decodeCommand(json)
      .map(value => GameCommandRequest(value.expectedNextSequence, value.intent))

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
