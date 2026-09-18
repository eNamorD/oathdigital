package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.catalog.CatalogHandlerInventory
import oathdigital.gameplay.{GameStateUpdates, OathLifecycle, RuleActivation,
  RuleOutcome, RuleQueryContext, RuleSourceRef, RuntimeRuleRegistry}
import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.gameplay.OathContinue.ActActionSelection
import oathdigital.gameplay.OathEvent._
import oathdigital.gameplay.OathState._
import oathdigital.gameplay.OathViolation._
import oathdigital.gameplay.operations.{CoreOperation, Give, Location,
  OperationPipeline, OperationPolicy, Piece,
  Peek => CorePeek}

sealed trait NegotiationCommand extends Product with Serializable
object NegotiationCommand {
  final case class Begin(actor: PlayerId, decision: DecisionId,
      participants: Vector[PlayerId]) extends NegotiationCommand
  final case class ReplaceTerms(participant: PlayerId, decision: DecisionId,
      terms: NegotiationTerms) extends NegotiationCommand
  final case class Accept(participant: PlayerId, decision: DecisionId)
      extends NegotiationCommand
  final case class Decline(participant: PlayerId, decision: DecisionId)
      extends NegotiationCommand
}

object Negotiation {
  def legalParticipants(ready: ReadyGame, actor: PlayerId): Vector[PlayerId] =
    ready.game.current.players.find(_.player == actor).flatMap(_.pawnSite).toVector
      .flatMap(site => ready.game.current.players.collect {
        case player if player.player != actor && player.pawnSite.contains(site) => player.player
      })

  def handle(catalog: ExecutableCatalog, state: OathState,
      command: NegotiationCommand): Either[OathViolation, OathTransition] = command match {
    case NegotiationCommand.Begin(actor, decision, selected) => for {
      ready <- validateBegin(catalog, state, actor)
      site <- ready.game.current.players.find(_.player == actor).flatMap(_.pawnSite)
        .toRight(PawnSiteMissing(actor))
      participants <- canonicalParticipants(ready, actor, selected)
      _ <- NegotiationPowerSupport.validate(catalog, ready, site, participants)
      event = NegotiationStarted(actor, decision, site, participants)
      next <- evolve(catalog, state, event)
    } yield OathTransition(next, Vector(event), ActActionSelection(actor))

    case NegotiationCommand.ReplaceTerms(participant, decision, terms) => for {
      pending <- pending(state, participant, decision)
      _ <- validateTerms(state.asInstanceOf[Ready].value, pending, participant, terms)
      event = NegotiationTermsReplaced(participant, decision, terms)
      next <- evolve(catalog, state, event)
    } yield OathTransition(next, Vector(event), ActActionSelection(pending.actor))

    case NegotiationCommand.Accept(participant, decision) => for {
      current <- pending(state, participant, decision)
      ready = state.asInstanceOf[Ready].value
      _ <- validateAll(ready, current)
      _ <- Either.cond(hasSubstance(current.terms), (),
        NegotiationUnavailable("the current deal contains no transfer or disclosure"))
      acceptedEvent = NegotiationAccepted(participant, decision)
      acceptedState <- evolve(catalog, state, acceptedEvent)
      acceptedPending = acceptedState.asInstanceOf[Ready].value.game.current.pending.get
        .asInstanceOf[PendingProcedure.Negotiation]
      completion = Option.when(acceptedPending.accepted == acceptedPending.participants.toSet)(
        NegotiationCompleted(acceptedPending.actor, decision,
          acceptedPending.participants, acceptedPending.terms))
      completedState <- completion.fold[Either[OathViolation, OathState]](
        Right(acceptedState))(evolve(catalog, acceptedState, _))
    } yield OathTransition(completedState, Vector(acceptedEvent) ++ completion,
      ActActionSelection(current.actor))

    case NegotiationCommand.Decline(participant, decision) => for {
      current <- pending(state, participant, decision)
      event = NegotiationDeclined(participant, decision)
      next <- evolve(catalog, state, event)
    } yield OathTransition(next, Vector(event), ActActionSelection(current.actor))
  }

  def evolve(catalog: ExecutableCatalog, state: OathState,
      event: OathEvent): Either[OathViolation, OathState] = event match {
    case e: NegotiationStarted => for {
      ready <- validateBegin(catalog, state, e.playerId)
      site <- ready.game.current.players.find(_.player == e.playerId).flatMap(_.pawnSite)
        .toRight(PawnSiteMissing(e.playerId))
      participants <- canonicalParticipants(ready, e.playerId, e.participants.tail)
      _ <- Either.cond(site == e.siteId && participants == e.participants, (),
        NegotiationOutcomeMismatch("recorded site or participant order is invalid"))
      _ <- NegotiationPowerSupport.validate(catalog, ready, site, participants)
      terms = participants.map(_ -> NegotiationTerms()).toMap
    } yield Ready(GameStateUpdates.updateCurrent(ready)(_.copy(pending = Some(
      PendingProcedure.Negotiation(e.decision, e.playerId, site, participants,
        terms, Set.empty)))))

    case e: NegotiationTermsReplaced => for {
      current <- pending(state, e.playerId, e.decision)
      ready = state.asInstanceOf[Ready].value
      _ <- validateTerms(ready, current, e.playerId, e.terms)
    } yield Ready(GameStateUpdates.updateCurrent(ready)(_.copy(pending = Some(
      current.copy(terms = current.terms.updated(e.playerId, e.terms),
        accepted = Set.empty)))))

    case e: NegotiationAccepted => for {
      current <- pending(state, e.playerId, e.decision)
      ready = state.asInstanceOf[Ready].value
      _ <- validateAll(ready, current)
      _ <- Either.cond(hasSubstance(current.terms), (),
        NegotiationOutcomeMismatch("recorded acceptance has an empty deal"))
      _ <- Either.cond(!current.accepted.contains(e.playerId), (),
        NegotiationOutcomeMismatch("participant already accepted this deal"))
    } yield Ready(GameStateUpdates.updateCurrent(ready)(_.copy(pending = Some(
      current.copy(accepted = current.accepted + e.playerId)))))

    case e: NegotiationDeclined => pending(state, e.playerId, e.decision).map { _ =>
      val ready = state.asInstanceOf[Ready].value
      Ready(GameStateUpdates.updateCurrent(ready)(_.copy(pending = None)))
    }

    case e: NegotiationCompleted => for {
      current <- pending(state, e.playerId, e.decision)
      ready = state.asInstanceOf[Ready].value
      _ <- Either.cond(current.actor == e.playerId &&
        current.participants == e.participants && current.terms == e.terms &&
        current.accepted == current.participants.toSet, (),
        NegotiationOutcomeMismatch("recorded completion facts differ from accepted deal"))
      _ <- validateAll(ready, current)
      _ <- Either.cond(hasSubstance(current.terms), (),
        NegotiationOutcomeMismatch("recorded completion has an empty deal"))
      deal <- applyDeal(ready, current)
    } yield Ready(deal)

    case _ => Left(InvalidEventOrder("Negotiation received a non-Negotiation event"))
  }

  private def validateBegin(catalog: ExecutableCatalog, state: OathState,
      actor: PlayerId) = OathLifecycle.validateAct(state, actor).flatMap { ready =>
    val supported = ready.setup.foundationProfile ==
      FirstGameFoundationProfile.FixedUnaltered &&
      ready.game.campaign.lineages.values.forall(_.role == Role.Exile) &&
      ready.game.campaign.foundations.values.forall(f =>
        f.face == FoundationFace.Normal && f.alterationSources.isEmpty)
    Either.cond(supported, ready, NegotiationUnavailable(
      "Negotiation is limited to fixed unaltered all-Exile first-game rules"))
  }

  private def canonicalParticipants(ready: ReadyGame, actor: PlayerId,
      selected: Vector[PlayerId]) = {
    val legal = legalParticipants(ready, actor)
    val wanted = selected.toSet
    Either.cond(selected.nonEmpty && selected.distinct.size == selected.size &&
      selected.forall(legal.contains), actor +: ready.game.current.players
        .map(_.player).filter(wanted), NegotiationUnavailable(
          "Negotiators must be distinct co-located non-actor players"))
  }

  private def pending(state: OathState, participant: PlayerId,
      decision: DecisionId) = state match {
    case Ready(ready) => ready.game.current.pending match {
      case Some(value: PendingProcedure.Negotiation) =>
        if (value.decision != decision) Left(NegotiationDecisionMismatch(
          value.decision, decision))
        else Either.cond(value.participants.contains(participant), value,
          NegotiationUnavailable("caller is not a participant"))
      case _ => Left(NegotiationUnavailable("no Negotiation is pending"))
    }
    case _ => Left(NegotiationUnavailable("game is not ready"))
  }

  private def validateAll(ready: ReadyGame,
      current: PendingProcedure.Negotiation): Either[OathViolation, Unit] = for {
    _ <- Either.cond(ready.game.current.turn.phase == Phase.Act &&
      ready.game.current.turn.activePlayer == current.actor, (),
      NegotiationUnavailable("the actor is no longer active in Act"))
    _ <- Either.cond(current.participants.forall(id => ready.game.current.players
      .find(_.player == id).flatMap(_.pawnSite).contains(current.site)), (),
      NegotiationUnavailable("all participants must remain co-located"))
    _ <- current.participants.foldLeft[Either[OathViolation, Unit]](Right(())) {
      case (result, author) => result.flatMap(_ => validateTerms(
        ready, current, author, current.terms(author)))
    }
    relics = current.terms.valuesIterator.flatMap(_.transfers)
      .flatMap(_.relics).toVector
    _ <- Either.cond(relics.distinct.size == relics.size, (),
      NegotiationUnavailable("a relic may be allocated only once in the current deal"))
  } yield ()

  private def validateTerms(ready: ReadyGame, current: PendingProcedure.Negotiation,
      author: PlayerId, terms: NegotiationTerms): Either[OathViolation, Unit] = {
    val participantSet = current.participants.toSet
    val player = ready.game.current.players.find(_.player == author).get
    val recipients = terms.transfers.map(_.recipient) ++ terms.disclosures.map(_.recipient)
    val relics = terms.transfers.flatMap(_.relics)
    for {
      _ <- Either.cond(recipients.forall(id => id != author && participantSet(id)), (),
        NegotiationUnavailable("terms may target only other participants"))
      _ <- Either.cond(terms.transfers.map(_.favor).sum <= player.board.favor, (),
        InsufficientFavor(terms.transfers.map(_.favor).sum, player.board.favor))
      _ <- Either.cond(relics.distinct.size == relics.size &&
        relics.forall(id => player.relics.exists(_.id == id)), (),
        NegotiationUnavailable("outgoing relics must be uniquely owned by their author"))
      _ <- Either.cond(terms.disclosures.forall(disclosureAllowed(
        ready, current, author, _)), (), NegotiationUnavailable(
          "a promised disclosure is not currently inspectable by its author"))
    } yield ()
  }

  private def disclosureAllowed(ready: ReadyGame,
      current: PendingProcedure.Negotiation, author: PlayerId,
      disclosure: NegotiationDisclosure): Boolean = {
    val player = ready.game.current.players.find(_.player == author).get
    disclosure.information match {
      case NegotiationDisclosureRef.Adviser(owner, card) => owner == author &&
        player.advisers.exists {
          case DenizenState(id, Orientation.FaceDown, _) => id == card
          case VisionState(id, Orientation.FaceDown) => id == card
          case _ => false
        }
      case NegotiationDisclosureRef.HeldRelic(owner, relic) => owner == author &&
        player.relics.exists(r => r.id == relic && r.orientation == Orientation.FaceDown)
      case NegotiationDisclosureRef.SiteRelic(site, relic) =>
        ready.knowledge.siteRelics.getOrElse(author, Map.empty)
          .getOrElse(site, Vector.empty).contains(relic) &&
          ready.game.current.map.sites.get(site).exists(_.relics.exists(_.id == relic))
    }
  }

  private def hasSubstance(terms: Map[PlayerId, NegotiationTerms]) =
    terms.values.exists(t => t.transfers.exists(x => x.favor > 0 || x.relics.nonEmpty) ||
      t.disclosures.nonEmpty)

  def canAccept(ready: ReadyGame, current: PendingProcedure.Negotiation,
      participant: PlayerId): Boolean =
    current.participants.contains(participant) && !current.accepted(participant) &&
      hasSubstance(current.terms) && validateAll(ready, current).isRight

  private def applyDeal(ready: ReadyGame,
      current: PendingProcedure.Negotiation): Either[OathViolation, ReadyGame] = {
    val outgoing = current.terms.toVector.flatMap { case (author, terms) =>
      terms.transfers.map(author -> _)
    }
    // Disclosures record knowledge about cards that are still physically at
    // their disclosed location, so they execute before any transfer relocates
    // one of those cards.
    val knowledgeOps = current.terms.valuesIterator.flatMap(_.disclosures)
      .toVector.map {
        case NegotiationDisclosure(recipient,
            NegotiationDisclosureRef.Adviser(owner, card)) =>
          CorePeek(recipient, card, Location.PlayArea(owner))
        case NegotiationDisclosure(recipient,
            NegotiationDisclosureRef.HeldRelic(owner, relic)) =>
          CorePeek(recipient, relic, Location.PlayArea(owner))
        case NegotiationDisclosure(recipient,
            NegotiationDisclosureRef.SiteRelic(site, relic)) =>
          CorePeek(recipient, relic, Location.Site(site))
      }
    val transferOps = outgoing.flatMap { case (author, transfer) =>
      val favor = Option.when(transfer.favor > 0)(Give(Piece.Favor(
        transfer.favor), author, Location.PlayArea(author),
        Location.PlayArea(transfer.recipient)): CoreOperation).toVector
      val relics = transfer.relics.map(relic => Give(Piece.Card(relic),
        author, Location.PlayArea(author),
        Location.PlayArea(transfer.recipient)): CoreOperation)
      favor ++ relics
    }
    val operations = knowledgeOps ++ transferOps
    OperationPipeline.run(ready, operations, OperationPolicy.exact(
      operations, "Negotiation semantic root is not permitted")) { evolved =>
      Right(GameStateUpdates.updateCurrent(evolved)(_.copy(pending = None)))
    }.flatMap(_.expectEffects(operations,
      "Negotiation transfer differs from recorded outcome"))
  }
}

object NegotiationPowerSupport {
  private val ExpectedInventory =
    "5fc88b0d9622a3f523722c288ea7a78d0ec09b7ce191bdabc7f471139ec85898"
  private val RelevantHandlers = Set("denizen.council-arbiter",
    "denizen.deed-writer", "denizen.traveling-negotiator",
    "edifice.e19.intact", "edifice.e19.ruined", "edifice.e21.intact",
    "relic.the-grand-scepter.negotiation", "legacy.high-priest")

  def validate(catalog: ExecutableCatalog, ready: ReadyGame, site: SiteId,
      participants: Vector[PlayerId]): Either[OathViolation, Unit] = {
    val actual = CatalogHandlerInventory.structuralFingerprint(catalog)
    if (actual != ExpectedInventory) return Left(
      UnsupportedNegotiationCatalogInventory(ExpectedInventory, actual))
    val activations: Vector[RuleActivation] = participants.flatMap { participant =>
      val player = ready.game.current.players.find(_.player == participant).get
      val adviserSources = player.advisers.collect {
        case d: DenizenState if d.orientation == Orientation.FaceUp =>
          RuleSourceRef.Adviser(participant, d.id) -> d.id
      }
      val accessibleSites = ready.game.current.map.inPlay.filter { candidate =>
        player.pawnSite.contains(candidate) || SiteRule.ruledBy(
          ready.game.current.map.sites(candidate).forces,
          ready.game.current.players, participant).getOrElse(false)
      }
      val siteSources = accessibleSites.flatMap(candidate =>
        ready.game.current.map.sites(candidate).denizens.collect {
          case d: DenizenState if d.orientation == Orientation.FaceUp =>
            RuleSourceRef.SiteCard(candidate, d.id) -> catalog.denizens
              .find(_.id.value == d.id.value).toVector.flatMap(_.handlers)
          case e: EdificeState => RuleSourceRef.Edifice(candidate, e.id) ->
            catalog.edifices.find(_.id.value == e.id.value).toVector.flatMap { definition =>
              e.side match {
                case EdificeSide.Intact => definition.intact.handlers
                case EdificeSide.Ruined => definition.ruined.handlers
              }
            }
        })
      val adviserActivations = adviserSources.flatMap { case (source, id) =>
        catalog.denizens.find(_.id.value == id.value).toVector.flatMap(_.handlers)
          .filter(RelevantHandlers).map(handler => RuleActivation(source, handler, 0)) }
      val siteActivations = siteSources.flatMap { case (source, handlers) =>
        handlers.filter(RelevantHandlers).map(handler => RuleActivation(source, handler, 0)) }
      val relicActivations = player.relics.flatMap { relic => catalog.relics
        .find(_.id.value == relic.id.value).toVector.flatMap(_.handlers)
        .filter(RelevantHandlers).map(handler => RuleActivation(
          RuleSourceRef.Relic(participant, relic.id), handler, 0)) }
      val lineage = ready.game.campaign.lineages(player.lineage)
      val legacyActivations = lineage.legacies.filter(_.active).flatMap { legacy =>
        catalog.legacies.find(_.id.value == legacy.id.value).toVector.flatMap(_.handlers)
          .filter(RelevantHandlers).map(handler => RuleActivation(
            RuleSourceRef.Legacy(lineage.id, legacy.id), handler, 0)) }
      adviserActivations ++ siteActivations ++ relicActivations ++ legacyActivations
    }.distinct
    val contextReady = participants.map(id => ready.game.current.players.find(_.player == id).get)
    activations.sortBy(a => (a.source.stableKey, a.handlerId)).iterator.flatMap { activation =>
      contextReady.iterator.map(player => RuntimeRuleRegistry.default.resolve(
        Vector(activation), RuleQueryContext.Negotiation(ready, player, site, participants)).head)
    }.collectFirst {
      case resolved if resolved.outcome.isInstanceOf[RuleOutcome.UnsupportedRelevantRule] =>
        UnsupportedNegotiationRule(resolved.activation.source.stableKey,
          resolved.activation.handlerId)
      case resolved if resolved.outcome.isInstanceOf[RuleOutcome.Block] =>
        resolved.outcome.asInstanceOf[RuleOutcome.Block].violation
    }.toLeft(())
  }
}
