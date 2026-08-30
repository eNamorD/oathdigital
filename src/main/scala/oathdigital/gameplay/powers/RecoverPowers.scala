package oathdigital.gameplay.powers

import oathdigital.gameplay._
import oathdigital.gameplay.actions.{RecoverModifierContribution, RecoverRules}
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

object RecoverPowers {
  val Catacombs: PowerId = PowerId("denizen.catacombs")
  private val ids = Set("denizen.relic-worship", "edifice.e13.ruined",
    "edifice.e17.intact", "edifice.e17.ruined")

  private object CatacombsInspector extends PowerInspector {
    def inspect(context: PowerContext): PowerInspection = context.facts match {
      case facts: ReviewedPowerFacts =>
        val applicable = context.source match {
          case RuleSourceRef.SiteCard(siteId, _) =>
            val player = facts.ready.game.current.players.find(_.player == facts.actor)
            val site = facts.ready.game.current.map.sites.get(siteId)
            val definition = facts.catalog.sites.find(_.id == siteId)
            ReviewedPowerInspector.inspect(context).applicable &&
              facts.ready.game.campaign.lineages.values.forall(
                _.role == oathdigital.model.Role.Exile) &&
              facts.ready.game.campaign.foundations.values.forall(f =>
                f.face == oathdigital.model.FoundationFace.Normal &&
                  f.alterationSources.isEmpty) &&
              player.exists(p => p.board.faceUpSecrets > 0 &&
                p.board.supply.supply > 0 && p.pawnSite.contains(siteId)) &&
              site.exists(value => definition.exists(d =>
                value.relics.size < d.relicSlots && d.recoverDifficulty.nonEmpty)) &&
              facts.ready.game.current.commonCards.relicDeck.nonEmpty
          case _ => false
        }
        PowerInspection(applicable, Some(facts.actor), Some(facts.actor))
      case _ => PowerInspection(applicable = false)
    }
  }
  private object CatacombsHandler extends PowerHandler
  private final case class CatacombsContribution(siteId: SiteId,
      cardId: DenizenId, relicId: RelicId) extends RecoverModifierContribution

  val registrations: Vector[RegisteredPower] = ids.toVector.sorted.map(id =>
    PowerRegistration.automatic(id, Some(MajorActionType.Recover),
      Vector(PowerWindow.RecoverBeforeFirstRoll))) :+ RegisteredPower(
    PowerDefinition(Catacombs, Some(MajorActionType.Recover), Vector(
      PowerWindow.RecoverEligibility, PowerWindow.RecoverModifierSelection,
      PowerWindow.RecoverBeforeFirstRoll), PowerResolution.PlayerSelected),
    CatacombsInspector, Some(CatacombsHandler))

  def validatePotential(catalog: oathdigital.catalog.ExecutableCatalog,
      ready: ReadyGame, player: PlayerState, siteId: SiteId)
      : Either[OathViolation, Unit] =
    RecoverRules.validate(catalog, ready, player, siteId).orElse(
      PowerRuntime.options(catalog, ready, player.player, MajorActionKind.Recover)
        .flatMap(options => Either.cond(options.exists(_.handlerId == Catacombs.value),
          (), OathViolation.RecoverUnavailable(
            "site has no facedown relic or usable Recover modifier"))))

  def prepare(catalog: oathdigital.catalog.ExecutableCatalog, ready: ReadyGame,
      player: PlayerId, ordered: Vector[OrderedRuleInvocation],
      drawRelic: () => Either[OathViolation, RelicId])
      : Either[OathViolation, Option[RecoverModifierContribution]] = ordered match {
    case Vector() => Right(None)
    case Vector(OrderedRuleInvocation(source: RuleSourceRef.SiteCard, id))
        if id == Catacombs.value => source.id match {
      case cardId: DenizenId => for {
        top <- drawRelic()
        _ <- validateCatacombs(catalog, ready, player, source, top)
      } yield Some(CatacombsContribution(source.siteId, cardId, top))
      case _ => Left(OathViolation.InvalidModifierInvocation(
        "Recover modifier requires a denizen site-card source"))
    }
    case _ => Left(OathViolation.InvalidModifierInvocation(
      "Recover has an unsupported modifier combination"))
  }

  def activate(catalog: oathdigital.catalog.ExecutableCatalog, state: OathState,
      player: PlayerId, decision: DecisionId,
      contribution: RecoverModifierContribution): Either[OathViolation, OathTransition] =
    contribution match {
      case value: CatacombsContribution => state match {
        case OathState.Ready(ready) =>
          val source = RuleSourceRef.SiteCard(value.siteId, value.cardId)
          validateCatacombs(catalog, ready, player, source, value.relicId)
            .flatMap(_ => GameplayTransition(state, Vector(OathEvent.CatacombsActivated(
              player, decision, value.siteId, value.cardId, value.relicId, 1)),
              OathContinue.ActActionSelection(player))(evolve(catalog, _, _)))
        case _ => Left(OathViolation.GameNotStarted)
      }
      case _ => Left(OathViolation.InvalidModifierInvocation(
        "unknown Recover modifier contribution"))
    }

  def evolve(catalog: oathdigital.catalog.ExecutableCatalog, state: OathState,
      event: OathEvent): Either[OathViolation, OathState] = event match {
    case e: OathEvent.CatacombsActivated => state match {
      case OathState.Ready(ready) => validateCatacombs(catalog, ready, e.playerId,
        RuleSourceRef.SiteCard(e.siteId, e.catacombsId), e.relicId).flatMap { _ =>
        Either.cond(e.secretSpent == 1, (), OathViolation.RecoverOutcomeMismatch(
          "Catacombs must spend exactly one secret")).map { _ =>
          val current = ready.game.current
          val site = current.map.sites(e.siteId)
          OathState.Ready(GameStateUpdates.updateCurrent(ready)(_.copy(
            commonCards = current.commonCards.copy(
              relicDeck = current.commonCards.relicDeck.tail),
            players = current.players.map(p => if (p.player != e.playerId) p else
              p.copy(board = p.board.copy(
                faceUpSecrets = p.board.faceUpSecrets - 1))),
            map = current.map.copy(sites = current.map.sites.updated(e.siteId,
              site.copy(denizens = site.denizens.map {
                case d: DenizenState if d.id == e.catacombsId => d.copy(
                  tokens = d.tokens.copy(secrets = d.tokens.secrets + 1))
                case other => other
              }, relics = site.relics :+ RelicState(e.relicId,
                Orientation.FaceDown, Tokens.empty)))))))
        }
      }
      case _ => Left(OathViolation.GameNotStarted)
    }
    case _ => Left(OathViolation.InvalidEventOrder(
      "Recover powers received a non-power event"))
  }

  private def validateCatacombs(catalog: oathdigital.catalog.ExecutableCatalog,
      ready: ReadyGame, playerId: PlayerId, source: RuleSourceRef.SiteCard,
      relicId: RelicId): Either[OathViolation, Unit] = for {
    _ <- OathLifecycle.validateAct(OathState.Ready(ready), playerId).map(_ => ())
    _ <- Either.cond(ready.game.campaign.lineages.values.forall(_.role == Role.Exile),
      (), OathViolation.UnsupportedRecoverState(
        "Recover is limited to the exile-only first game"))
    _ <- Either.cond(ready.game.campaign.foundations.values.forall(f =>
      f.face == FoundationFace.Normal && f.alterationSources.isEmpty), (),
      OathViolation.UnsupportedRecoverState(
        "altered Foundations are not supported for Recover"))
    player <- ready.game.current.players.find(_.player == playerId)
      .toRight(OathViolation.WrongPlayer(
        ready.game.current.turn.activePlayer, playerId))
    _ <- Either.cond(player.pawnSite.contains(source.siteId), (),
      OathViolation.RecoverUnavailable("pawn is not at Catacombs"))
    site <- ready.game.current.map.sites.get(source.siteId)
      .toRight(OathViolation.SiteNotInPlay(source.siteId))
    card <- site.denizens.collectFirst {
      case d: DenizenState if d.id == source.id &&
          d.orientation == Orientation.FaceUp => d
    }.toRight(OathViolation.RecoverUnavailable(
      "Catacombs is not accessible faceup at the site"))
    definition <- catalog.denizens.find(_.id.value == card.id.value)
      .toRight(OathViolation.RecoverUnavailable("Catacombs definition is missing"))
    _ <- Either.cond(definition.powers.exists(_.id == Catacombs), (),
      OathViolation.RecoverUnavailable("selected source is not Catacombs"))
    siteDefinition <- catalog.sites.find(_.id == source.siteId)
      .toRight(OathViolation.RecoverUnavailable("site definition is missing"))
    _ <- Either.cond(siteDefinition.recoverDifficulty.nonEmpty, (),
      OathViolation.RecoverUnavailable("site has no Recover Difficulty"))
    _ <- Either.cond(site.relics.size < siteDefinition.relicSlots, (),
      OathViolation.RecoverUnavailable("site has no empty relic slot"))
    _ <- Either.cond(player.board.faceUpSecrets >= 1, (),
      OathViolation.InsufficientSecrets(1, player.board.faceUpSecrets))
    _ <- Either.cond(player.board.supply.supply >= 1, (),
      OathViolation.InsufficientSupply(1, player.board.supply.supply))
    top <- ready.game.current.commonCards.relicDeck.headOption
      .toRight(OathViolation.RecoverUnavailable("relic deck is empty"))
    _ <- Either.cond(top == relicId, (), OathViolation.RecoverOutcomeMismatch(
      "Catacombs relic is not the top of the relic deck"))
  } yield ()
}
