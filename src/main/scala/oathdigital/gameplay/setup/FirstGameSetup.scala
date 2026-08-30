package oathdigital.gameplay.setup

import oathdigital.catalog.{ExecutableCatalog, Suit => CatalogSuit}
import oathdigital.engine.EventEvolution
import oathdigital.gameplay.GameplayTransition
import oathdigital.gameplay._
import oathdigital.model._

final case class PlayerColor(value: String) {
  require(value.trim.nonEmpty, "player color must not be blank")
}

final case class FirstGameParticipant(
    playerId: PlayerId,
    lineageId: LineageId,
    color: PlayerColor
)

final case class PawnPlacement(playerId: PlayerId, siteId: SiteId)

final case class FirstGameSetupPlan(
    catalog: CatalogRef,
    participants: Vector[FirstGameParticipant],
    firstPlayer: PlayerId,
    orderedSites: Vector[SiteId],
    denizenOrder: Vector[DenizenId],
    worldDeckOrder: Vector[WorldCardId],
    relicOrder: Vector[RelicId],
    homelandEdifices: Vector[(SiteId, EdificeId)],
    oathkeeperGoal: OathkeeperGoal = OathkeeperGoal.Supremacy
)

sealed trait FirstGameFoundationProfile extends Product with Serializable
object FirstGameFoundationProfile {
  case object FixedUnaltered extends FirstGameFoundationProfile
}

final case class FirstGameSupportState(
    foundationProfile: FirstGameFoundationProfile,
    favorBanks: Map[Suit, Int],
    firstPlayer: PlayerId,
    relicKnowledge: Map[PlayerId, Map[SiteId, Vector[RelicId]]] = Map.empty,
    adviserKnowledge: Map[PlayerId, Vector[WorldCardId]] = Map.empty,
    heldRelicKnowledge: Map[PlayerId, Vector[RelicId]] = Map.empty
)

sealed trait FirstGameSetupCommand extends Product with Serializable
object FirstGameSetupCommand {
  final case class Begin(plan: FirstGameSetupPlan)
      extends FirstGameSetupCommand
  final case class ChooseAdviser(playerId: PlayerId, adviserId: DenizenId)
      extends FirstGameSetupCommand
  final case class PlacePawn(playerId: PlayerId, siteId: SiteId)
      extends FirstGameSetupCommand
}

object FirstGameRulesData {
  val visions: Vector[VisionId] = Vector(
    VisionId("vision:vision-of-sanctuary"),
    VisionId("vision:vision-of-rebellion"),
    VisionId("vision:vision-of-faith"),
    VisionId("vision:conspiracy"),
    VisionId("vision:vision-of-conquest")
  )
}

/**
 * CR pp. 6-7 first-game setup with NF p. 8's all-Exile boundary.
 *
 * Every shuffled order is supplied in `FirstGameSetupPlan`; neither command
 * handling nor replay has an RNG.
 */
final class FirstGameSetupRules(catalog: ExecutableCatalog)
    extends EventEvolution[
      OathState,
      OathEvent,
      OathViolation
    ] {
  import OathContinue._
  import FirstGameSetupCommand._
  import OathEvent._
  import OathState._
  import OathViolation._

  override val initialState: OathState = NoGame

  private val sitesById = catalog.sites.map(site => site.id -> site).toMap
  private val denizensById =
    catalog.denizens.map(d => DenizenId(d.id.value) -> d).toMap
  private val relicIds =
    catalog.relics.filter(_.role == oathdigital.catalog.RelicRole.Ordinary)
      .map(r => RelicId(r.id.value))
  private val edificesById =
    catalog.edifices.map(e => EdificeId(e.id.value) -> e).toMap
  private val materializer = new FirstGameSetupMaterializer(catalog)

  def handle(
      state: OathState,
      command: FirstGameSetupCommand
  ): Either[OathViolation, OathTransition] =
    command match {
      case command: PlacePawn => handle(state, command)
      case Begin(plan) =>
        state match {
          case NoGame =>
            validatePlan(plan).flatMap { _ =>
              transition(
                state,
                Vector(FirstGameStarted(plan)),
                AwaitingPawn(turnOrder(plan).head.playerId)
              )
            }
          case _ => Left(GameAlreadyExists)
        }
      case ChooseAdviser(playerId, adviserId) =>
        state match {
          case NoGame => Left(GameNotStarted)
          case _: Ready => Left(GameAlreadyReady)
          case progress: InProgress =>
            expectedAdviserPlayer(progress) match {
              case None =>
                Left(InvalidEventOrder("a pawn must be placed first"))
              case Some(expected) if expected != playerId =>
                Left(WrongPlayer(expected, playerId))
              case Some(_) if !handFor(progress.plan, playerId)
                    .contains(adviserId) =>
                Left(AdviserNotInHand(playerId, adviserId))
              case Some(_) =>
                val chosen = StartingAdviserChosen(playerId, adviserId)
                val isFinal =
                  progress.adviserChoices.size + 1 ==
                    progress.plan.participants.size
                val events =
                  if (isFinal) Vector(chosen, FirstGameCompleted)
                  else Vector(chosen)
                val next =
                  if (isFinal)
                    ReadyForFirstTurn(progress.plan.firstPlayer)
                  else {
                    val order = turnOrder(progress.plan)
                    AwaitingPawn(order(progress.adviserChoices.size + 1).playerId)
                  }
                transition(state, events, next)
            }
        }
    }

  /** Handles the first-game pawn placement step. */
  def handle(
      state: OathState,
      command: PlacePawn
  ): Either[OathViolation, OathTransition] =
    state match {
      case NoGame => Left(GameNotStarted)
      case _: Ready => Left(GameAlreadyReady)
      case progress: InProgress =>
        if (expectedAdviserPlayer(progress).nonEmpty)
          Left(InvalidEventOrder("the current player must choose an adviser"))
        else {
          val order = turnOrder(progress.plan)
          val expected = order(progress.placements.size).playerId
          if (command.playerId != expected)
            Left(WrongPlayer(expected, command.playerId))
          else if (!progress.plan.orderedSites.contains(command.siteId))
            Left(SiteNotInPlay(command.siteId))
          else
            transition(
              state,
              Vector(GamePawnPlaced(command.playerId, command.siteId)),
              AwaitingAdviser(command.playerId)
            )
        }
    }

  override def evolve(
      state: OathState,
      event: OathEvent
  ): Either[OathViolation, OathState] =
    event match {
      case FirstGameStarted(plan) =>
        state match {
          case NoGame =>
            validatePlan(plan).map(_ => InProgress(plan, Vector.empty, Vector.empty))
          case _ => Left(GameAlreadyExists)
        }
      case GamePawnPlaced(playerId, siteId) =>
        state match {
          case progress: InProgress
              if expectedAdviserPlayer(progress).isEmpty &&
                progress.placements.size < progress.plan.participants.size =>
            val expected =
              turnOrder(progress.plan)(progress.placements.size).playerId
            if (playerId != expected) Left(WrongPlayer(expected, playerId))
            else if (!progress.plan.orderedSites.contains(siteId))
              Left(SiteNotInPlay(siteId))
            else
              Right(
                progress.copy(
                  placements =
                    progress.placements :+ PawnPlacement(playerId, siteId)
                )
              )
          case _: Ready => Left(GameAlreadyReady)
          case NoGame => Left(GameNotStarted)
          case _ => Left(InvalidEventOrder("unexpected pawn placement"))
        }
      case StartingAdviserChosen(playerId, adviserId) =>
        state match {
          case progress: InProgress =>
            expectedAdviserPlayer(progress) match {
              case Some(expected) if expected == playerId &&
                    handFor(progress.plan, playerId).contains(adviserId) =>
                Right(
                  progress.copy(
                    adviserChoices =
                      progress.adviserChoices :+ (playerId -> adviserId)
                  )
                )
              case Some(expected) if expected != playerId =>
                Left(WrongPlayer(expected, playerId))
              case Some(_) => Left(AdviserNotInHand(playerId, adviserId))
              case None => Left(InvalidEventOrder("unexpected adviser choice"))
            }
          case _: Ready => Left(GameAlreadyReady)
          case NoGame => Left(GameNotStarted)
        }
      case FirstGameCompleted =>
        state match {
          case progress: InProgress
              if progress.placements.size == progress.plan.participants.size &&
                progress.adviserChoices.size ==
                  progress.plan.participants.size =>
            buildReady(progress).map(Ready)
          case _: Ready => Left(GameAlreadyReady)
          case NoGame => Left(GameNotStarted)
          case _ => Left(InvalidEventOrder("setup is incomplete"))
        }
      case _: Traveled =>
        Left(InvalidEventOrder("Travel requires the gameplay evolution"))
      case _: Mustered | _: Traded =>
        Left(InvalidEventOrder("Economy requires the gameplay evolution"))
      case _: WealthTaken | _: WakeEnded =>
        Left(InvalidEventOrder("gameplay event cannot be applied by setup rules"))
      case _: SearchStarted | _: SearchCompleted =>
        Left(InvalidEventOrder("Search requires the gameplay evolution"))
      case _: RestStarted | _: RestCompleted =>
        Left(InvalidEventOrder("Rest requires the gameplay evolution"))
      case _: CatacombsActivated | _: RecoverRolled | _: RecoverStopped | _: RelicRecovered |
          _: ForgeStarted | _: ForgeCompleted |
          _: BannerChallengeStarted | _: BannerRibbonChoiceMade |
          _: BannerChallengeCompleted | _: BannerResourcePlaced |
          _: FacedownAdviserDiscarded | _: FacedownAdviserPlayed |
          _: SiteRelicsPeeked | _: OwnedRelicRevealed | _: WarbandsMoved |
          _: NegotiationStarted | _: NegotiationTermsReplaced |
          _: NegotiationAccepted | _: NegotiationDeclined | _: NegotiationCompleted |
          _: CampaignStarted | _: CampaignPlanChosen | _: CampaignPlansFinished | _: CampaignSacrificed | _: CampaignConquered |
          _: CampaignRaided | _: CampaignRaidPawnRelocated |
          _: BanditsRefilled =>
        Left(InvalidEventOrder("Recover requires the gameplay evolution"))
      case _: OathkeeperChanged | _: OathkeeperRecipientChoiceStarted |
          _: OathkeeperRecipientChosen | _: UsurperFlipped |
          _: UsurperVictory =>
        Left(InvalidEventOrder("state-based checks require gameplay evolution"))
    }

  private def validatePlan(
      plan: FirstGameSetupPlan
  ): Either[OathViolation, Unit] = {
    def duplicate[A](values: Vector[A]): Option[A] = {
      val seen = scala.collection.mutable.HashSet.empty[A]
      values.find(value => !seen.add(value))
    }
    val playerIds = plan.participants.map(_.playerId)
    val lineageIds = plan.participants.map(_.lineageId)
    val colors = plan.participants.map(_.color)
    val siteDuplicate = duplicate(plan.orderedSites)
    val denizenDuplicate = duplicate(plan.denizenOrder)
    val relicDuplicate = duplicate(plan.relicOrder)

    if (plan.catalog != catalog.ref)
      Left(CatalogMismatch(catalog.ref, plan.catalog))
    else if (plan.participants.isEmpty) Left(ParticipantsEmpty)
    else if (duplicate(playerIds).nonEmpty)
      Left(DuplicatePlayer(duplicate(playerIds).get))
    else if (duplicate(lineageIds).nonEmpty)
      Left(DuplicateLineage(duplicate(lineageIds).get))
    else if (duplicate(colors).nonEmpty)
      Left(DuplicateColor(duplicate(colors).get))
    else if (!playerIds.contains(plan.firstPlayer))
      Left(UnknownFirstPlayer(plan.firstPlayer))
    else if (plan.orderedSites.size != 8)
      Left(WrongCount("orderedSites", 8, plan.orderedSites.size))
    else if (siteDuplicate.nonEmpty)
      Left(DuplicateComponent("orderedSites", siteDuplicate.get.value))
    else if (plan.orderedSites.exists(id => !sitesById.contains(id)))
      Left(UnknownComponent(
        "orderedSites",
        plan.orderedSites.find(id => !sitesById.contains(id)).get.value
      ))
    else if (plan.denizenOrder.size != 60)
      Left(WrongCount("denizenOrder", 60, plan.denizenOrder.size))
    else if (denizenDuplicate.nonEmpty)
      Left(DuplicateComponent("denizenOrder", denizenDuplicate.get.value))
    else if (plan.denizenOrder.exists(id => !denizensById.contains(id)))
      Left(UnknownComponent(
        "denizenOrder",
        plan.denizenOrder.find(id => !denizensById.contains(id)).get.value
      ))
    else
      validateSuitCounts(plan)
        .flatMap(_ => validateWorldDeck(plan))
        .flatMap(_ => validateRelics(plan, relicDuplicate))
        .flatMap(_ => validateHomelands(plan))
  }

  private def validateSuitCounts(
      plan: FirstGameSetupPlan
  ): Either[OathViolation, Unit] =
    CatalogSuit.values.toVector.sorted
      .collectFirst {
        case suit
            if plan.denizenOrder.count(
              id => denizensById(id).suit.value == suit
            ) != 10 =>
          val actual = plan.denizenOrder.count(
            id => denizensById(id).suit.value == suit
          )
          WrongDenizenSuitCount(suit, actual)
      }
      .toLeft(())

  private def validateWorldDeck(
      plan: FirstGameSetupPlan
  ): Either[OathViolation, Unit] = {
    val dealt = 6 + plan.participants.size * 3
    val remaining = plan.denizenOrder.drop(dealt).toSet
    val deckDenizens = plan.worldDeckOrder.collect {
      case id: DenizenId => id
    }
    val deckVisions = plan.worldDeckOrder.collect {
      case id: VisionId => id
    }
    val first = plan.worldDeckOrder.take(12)
    val second = plan.worldDeckOrder.slice(12, 30)
    def counts(cards: Vector[WorldCardId]) =
      cards.count(_.isInstanceOf[DenizenId]) ->
        cards.count(_.isInstanceOf[VisionId])

    if (dealt > plan.denizenOrder.size)
      Left(InvalidWorldDeck("too many player hands for the 60-card pool"))
    else if (deckDenizens.size != deckDenizens.distinct.size)
      Left(InvalidWorldDeck("denizens must be unique"))
    else if (deckDenizens.toSet != remaining)
      Left(InvalidWorldDeck("denizens must exactly equal the undealt pool"))
    else if (
      deckVisions.size != FirstGameRulesData.visions.size ||
      deckVisions.distinct.size != deckVisions.size ||
      deckVisions.toSet != FirstGameRulesData.visions.toSet
    )
      Left(InvalidWorldDeck("the five fixed Vision identities are required"))
    else if (counts(first) != (10 -> 2))
      Left(InvalidWorldDeck("top packet must contain 10 denizens and 2 Visions"))
    else if (counts(second) != (15 -> 3))
      Left(InvalidWorldDeck("second packet must contain 15 denizens and 3 Visions"))
    else Right(())
  }

  private def validateRelics(
      plan: FirstGameSetupPlan,
      duplicate: Option[RelicId]
  ): Either[OathViolation, Unit] =
    if (duplicate.nonEmpty)
      Left(DuplicateComponent("relicOrder", duplicate.get.value))
    else if (plan.relicOrder.toSet != relicIds.toSet)
      Left(InvalidRelicOrder(
        "order must contain every ordinary relic exactly once and no Grand Scepter"
      ))
    else Right(())

  private def validateHomelands(
      plan: FirstGameSetupPlan
  ): Either[OathViolation, Unit] = {
    val entries = plan.homelandEdifices
    val duplicates = entries.groupBy(_._1).collectFirst {
      case (site, values) if values.size > 1 => site
    }
    val required = plan.orderedSites.filter(homelandSuit(_).nonEmpty)
    if (duplicates.nonEmpty)
      Left(InvalidHomelandEdifice(duplicates.get, "duplicate assignment"))
    else if (entries.map(_._1).toSet != required.toSet)
      Left(InvalidHomelandEdifice(
        required.find(id => !entries.map(_._1).contains(id))
          .orElse(entries.map(_._1).find(id => !required.contains(id)))
          .getOrElse(plan.orderedSites.head),
        "assign exactly one edifice to each selected Homeland"
      ))
    else
      entries.collectFirst {
        case (site, edificeId) if !edificesById.contains(edificeId) =>
          InvalidHomelandEdifice(site, s"unknown edifice ${edificeId.value}")
        case (site, edificeId)
            if edificesById(edificeId).suit.value != homelandSuit(site).get =>
          InvalidHomelandEdifice(site, "edifice suit does not match Homeland")
      }.toLeft(())
  }

  private def homelandSuit(siteId: SiteId): Option[String] =
    sitesById(siteId).handlers.collectFirst {
      case handler if handler.contains(".homeland-") =>
        handler.substring(handler.indexOf(".homeland-") + 10)
    }

  private def turnOrder(
      plan: FirstGameSetupPlan
  ): Vector[FirstGameParticipant] = {
    val start = plan.participants.indexWhere(_.playerId == plan.firstPlayer)
    plan.participants.drop(start) ++ plan.participants.take(start)
  }

  private def handFor(
      plan: FirstGameSetupPlan,
      playerId: PlayerId
  ): Vector[DenizenId] = {
    materializer.handFor(plan, playerId)
  }

  private def expectedAdviserPlayer(
      progress: InProgress
  ): Option[PlayerId] =
    if (progress.placements.size == progress.adviserChoices.size + 1)
      Some(turnOrder(progress.plan)(progress.adviserChoices.size).playerId)
    else None

  private def transition(
      state: OathState,
      events: Vector[OathEvent],
      continue: OathContinue
  ): Either[OathViolation, OathTransition] =
    GameplayTransition(state, events, continue)(evolve)

  private def buildReady(
      progress: InProgress
  ): Either[OathViolation, ReadyGame] = {
    val plan = progress.plan
    val material = materializer.materialize(plan, progress.placements,
      progress.adviserChoices)
    val lineages = plan.participants.map { participant =>
      participant.lineageId -> LineageState(
        participant.lineageId,
        None,
        Role.Exile,
        Vector.empty,
        Vector.empty
      )
    }.toMap
    val foundations = FoundationNumber.all.map { number =>
      number -> FoundationState(FoundationFace.Normal, Set.empty)
    }.toMap
    val game = OathGame(
      plan.catalog,
      CampaignState(
        AtlasState(Vector.empty),
        foundations,
        lineages,
        Vector.empty,
        Vector.empty,
        Map.empty,
        plan.oathkeeperGoal,
        EraState(20, lineages.keys.map(_ -> 0).toMap)
      ),
      CurrentGameState(
        material.players, material.map, material.commonCards, material.banners,
        OathkeeperState(None, TitleSide.Oathkeeper),
        TurnState(plan.firstPlayer, Phase.Wake, Set.empty),
        material.tracks,
        None,
        None
      )
    )
    val problems = DomainValidation.validate(game)
    if (problems.nonEmpty) Left(InvalidAggregate(problems))
    else
      Right(
        ReadyGame(
          game,
          plan.participants.map(p => p.playerId -> p.color).toMap,
          FirstGameSupportState(
            FirstGameFoundationProfile.FixedUnaltered,
            material.favorBanks,
            plan.firstPlayer
          )
        )
      )
  }

}
