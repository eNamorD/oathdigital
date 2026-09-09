package oathdigital.gameplay.actions.forge

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.ForgeRules
import oathdigital.gameplay.operations._
import oathdigital.gameplay.powerresolver.PowerWindow
import oathdigital.gameplay.walker.{OwnerQuery, WalkerCtx}
import oathdigital.gameplay.{OathViolation, ReadyGame}
import oathdigital.model.DecisionPayload.ForgeAssignmentPayload
import oathdigital.model.{Answered, DecisionPayload, DenizenId, DenizenState,
  ForgeResource, ForgeResourceAssignment, Orientation, PendingTree, PlayerId,
  PlayerState, SiteDenizenTarget, SiteId, Suit, Tokens}

/** Declared Forge procedure tree for the walker (batch 1, Task 2).
  *
  * Reproduces the legacy `Forge.handle`/`Forge.evolve` observable flow on the
  * generic walker:
  *
  * {{{
  * Sequence(                                  // window = ForgeActionEligibility
  *   BuildOps(AdjustSupply(actor, -1)),       // window = ForgeCost
  *   Decide("forge.assignment"),              // validate = assignment legality
  *   BuildOps(one favor/secret move per assignment,
  *            Play(relic-deck top -> play area, FaceDown)))
  * }}}
  *
  * Windows: the root carries `ForgeActionEligibility` (eligibility-shaped
  * restrictions/relaxations gather there) and the supply payment carries
  * `ForgeCost`. `AdjustSupply` is a bare `PrimitiveOperation` with no window
  * field of its own, so the payment is stated as a `BuildOps` whose window
  * hooks the node -- the same shape Recover's relic move uses at
  * `RecoverAfterRelic`. `ForgeModifierSelection` is not a tree node: it is the
  * window a player-selected power is offered at, answered by `StartWalker`'s
  * `modifiers`. No other node carries a window.
  *
  * Start gates are `ForgeRules.validate` unchanged -- exile-only unaltered
  * foundations, audited catalog, the actor ruling their pawn site, a printed
  * Forge cost totalling three resources, exactly three empty faceup denizens,
  * supply >= 1, and a non-empty relic deck. Together they are what makes a
  * started Forge always answerable: three eligible targets exist, the printed
  * cost can be spread over them, and a relic is waiting on the deck.
  *
  * Two decisions worth stating outright, because both are places the obvious
  * implementation is wrong:
  *
  *  - **The forged relic is read from state at execution time** (ruling R12).
  *    It is not closed over by the tree, does not ride the answer, and is not
  *    produced by a port: the legacy completion validated that the recorded
  *    relic *is* `commonCards.relicDeck.head`, so there was never a free
  *    choice to prepare. The trailing `BuildOps` reads the top off `ready`
  *    when it runs and fails with `ForgeUnavailable("relic deck is empty")`
  *    when there is none.
  *  - **Eligible targets are read live off `ready`** by [[eligibleTargets]]
  *    (ruling R14), never off this tree's closure, exactly as
  *    `RecoverProcedure.actorFacedownRelics` is. The application-layer
  *    projector builds a client's candidate list from that SAME method the
  *    `Decide`'s `validate` accepts answers against, so the offered set and
  *    the accepted set have one definition rather than two expressions that
  *    agree today and diverge the first time a power moves a denizen.
  *
  * `build`/`rebuild` take the [[ExecutableCatalog]] for the same reason
  * `RecoverProcedure` does (the printed cost and each denizen's suit are
  * static catalog data), and the tree closes over the catalog itself rather
  * than a derived scalar: the suit of a target is only knowable once the
  * target is known, and targets are read live. That is the precedent
  * `CatacombsContribution` already set for catalog-dependent walker code.
  */
object ForgeProcedure {
  val assignmentDecisionId: String = "forge.assignment"

  /** Forge spends exactly one Supply to start, whatever the printed cost. */
  private val supplyCost: Int = 1

  /** The actor's current pawn site -- the single definition `build`,
    * `rebuild` and [[eligibleTargets]] all read, so nothing in this module can
    * derive "the Forge site" a different way and silently disagree.
    */
  def actorSite(state: ReadyGame, actor: PlayerId): Option[SiteId] =
    state.game.current.players.find(_.player == actor).flatMap(_.pawnSite)

  /** The empty faceup denizens at the actor's CURRENT pawn site, as assignment
    * targets: exactly the set [[assignmentDecisionId]]'s validate accepts an
    * answer against, and exactly the set the projector offers a client (Task
    * 3). Read live off `state.game.current.map.sites` on every call, never off
    * a tree closure -- an edifice, a facedown denizen, or a denizen already
    * carrying a token is not a target.
    */
  def eligibleTargets(state: ReadyGame,
      actor: PlayerId): Vector[SiteDenizenTarget] =
    actorSite(state, actor).fold(Vector.empty[SiteDenizenTarget]) { siteId =>
      state.game.current.map.sites.get(siteId)
        .fold(Vector.empty[SiteDenizenTarget])(_.denizens.collect {
          case denizen: DenizenState
              if denizen.orientation == Orientation.FaceUp &&
                denizen.tokens == Tokens.empty =>
            SiteDenizenTarget(siteId, denizen.id)
        })
    }

  /** Fresh start: every gate in `ForgeRules.validate` runs. */
  def build(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] = for {
    player <- actorState(state, actor)
    siteId <- player.pawnSite.toRight(OathViolation.PawnSiteMissing(actor))
    facts <- ForgeRules.validate(catalog, state, player, siteId)
  } yield tree(catalog, actor, facts._2)

  /** Rebuilds the same command-local tree for an already-started Forge.
    *
    * Start-only gates do not re-run, and supply is the one that matters:
    * starting a Forge spends 1 Supply, so a player who began with exactly 1
    * holds 0 when the assignment decision parks. `ForgeRules.validate`
    * requires supply >= 1 and would therefore strand a legally started Forge
    * with no way to answer it -- the walker's worst failure mode. Only what
    * resuming genuinely requires runs here: the actor's site, and the printed
    * cost the decision validates an answer against.
    */
  def rebuild(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] = for {
    siteId <- actorSite(state, actor).toRight(
      OathViolation.PawnSiteMissing(actor))
    cost <- printedCost(catalog, siteId)
  } yield tree(catalog, actor, cost)

  private def actorState(state: ReadyGame,
      actor: PlayerId): Either[OathViolation, PlayerState] =
    state.game.current.players.find(_.player == actor)
      .toRight(OathViolation.PawnSiteMissing(actor))

  /** The site's printed Forge cost, worded exactly as `ForgeRules.validate`
    * words the same rejection so a resume and a start fail alike.
    */
  private def printedCost(catalog: ExecutableCatalog,
      siteId: SiteId): Either[OathViolation, Tokens] =
    catalog.sites.find(_.id == siteId).flatMap(_.forgeRequirements)
      .toRight(OathViolation.ForgeUnavailable("site has no printed Forge cost"))

  private def suitOf(catalog: ExecutableCatalog,
      denizen: DenizenId): Either[OathViolation, Suit] =
    catalog.denizens.find(_.id.value == denizen.value)
      .flatMap(definition => Suit.all.find(_.key == definition.suit.value))
      .toRight(OathViolation.ForgeOutcomeMismatch(
        s"no catalog suit for ${denizen.value}"))

  /** One move per assignment, in assignment order: favor comes out of the
    * target denizen's own suit bank, secrets out of the shared bank. Mirrors
    * legacy `Forge.completionOperations` minus its trailing relic play, which
    * the caller appends from live state.
    */
  private def assignmentOperations(catalog: ExecutableCatalog,
      assignments: Vector[ForgeResourceAssignment])
      : Either[OathViolation, Vector[CoreOperation]] =
    assignments.foldLeft[Either[OathViolation, Vector[CoreOperation]]](
      Right(Vector.empty)) { case (result, assignment) =>
      val onCard = PositionedLocation(
        Location.OnCard(assignment.target.denizenId))
      for {
        operations <- result
        operation <- assignment.resource match {
          case ForgeResource.Favor =>
            suitOf(catalog, assignment.target.denizenId).map(suit => Move(
              Piece.Favor(1),
              PositionedLocation(Location.FavorBank(suit)), onCard))
          case ForgeResource.Secret => Right(Move(Piece.Secrets(1),
            PositionedLocation(Location.SharedBank), onCard))
        }
      } yield operations :+ operation
    }

  /** The tree closes only over command-stable data (the actor, the printed
    * cost, and the catalog the cost and suits are read from), so the walker
    * re-derives an identical tree every command (spec decision S1).
    */
  private def tree(catalog: ExecutableCatalog, actor: PlayerId,
      cost: Tokens): Operation = {
    val expectedResources: Vector[ForgeResource] =
      Vector.fill(cost.favor)(ForgeResource.Favor) ++
        Vector.fill(cost.secrets)(ForgeResource.Secret)

    /** How much favor each suit bank must cover for this answer. */
    def favorBySuit(assignments: Vector[ForgeResourceAssignment])
        : Either[OathViolation, Map[Suit, Int]] =
      assignments.filter(_.resource == ForgeResource.Favor)
        .foldLeft[Either[OathViolation, Map[Suit, Int]]](Right(Map.empty)) {
          case (result, assignment) => for {
            counts <- result
            suit <- suitOf(catalog, assignment.target.denizenId)
          } yield counts.updated(suit, counts.getOrElse(suit, 0) + 1)
        }

    // Legacy `Forge.validateCompletion`'s body, with its eligible-target set
    // read live off `ready` (ruling R14) instead of off the pending
    // procedure's frozen `eligibleTargets` field.
    def validateAssignment(ready: ReadyGame, pending: PendingTree,
        payload: DecisionPayload): Either[OathViolation, Unit] = payload match {
      case ForgeAssignmentPayload(assignments) =>
        val targets = assignments.map(_.target)
        for {
          _ <- Either.cond(
            assignments.size == 3 && targets.distinct.size == 3, (),
            OathViolation.ForgeOutcomeMismatch(
              "assign exactly one resource to each of three distinct denizens"))
          _ <- Either.cond(
            targets.toSet == eligibleTargets(ready, actor).toSet, (),
            OathViolation.ForgeOutcomeMismatch(
              "assignment targets are stale or ineligible"))
          _ <- Either.cond(assignments.map(_.resource).sortBy(_.key) ==
            expectedResources.sortBy(_.key), (),
            OathViolation.ForgeOutcomeMismatch(
              "assignments do not match the printed Forge resources"))
          needed <- favorBySuit(assignments)
          _ <- needed.toVector.foldLeft[Either[OathViolation, Unit]](
            Right(())) { case (result, (suit, amount)) =>
            result.flatMap(_ => Either.cond(
              ready.banks.favor.getOrElse(suit, 0) >= amount, (),
              OathViolation.ForgeUnavailable(
                s"$suit favor bank lacks $amount favor")))
          }
        } yield ()
      case other => Left(OathViolation.InvalidEventOrder(
        s"$assignmentDecisionId received an unexpected payload: $other"))
    }

    // `AdjustSupply` carries no window of its own, so the payment is a
    // BuildOps whose window is the hook point (see this object's doc).
    val paySupply = BuildOps((_, _) => Right(Vector[CoreOperation](
      AdjustSupply(actor, -supplyCost))),
      window = Some(PowerWindow.ForgeCost))

    // The payload here only type-tags the choice; the concrete answer rides
    // `resolve`. An empty vector is an inert marker no legal answer can equal
    // (a legal answer names exactly three assignments), so nothing downstream
    // can mistake the marker for a recorded decision.
    val assignmentDecide = Decide(
      payload = ForgeAssignmentPayload(Vector.empty),
      owner = ForgeProcedure.ActiveOwner,
      decisionId = assignmentDecisionId,
      validate = Some(validateAssignment))

    val forgeRelic = BuildOps((ready, pending) =>
      pending.answered.lastOption match {
        case Some(Answered(_, ForgeAssignmentPayload(assignments))) => for {
          moves <- assignmentOperations(catalog, assignments)
          relic <- ready.game.current.commonCards.relicDeck.headOption.toRight(
            OathViolation.ForgeUnavailable("relic deck is empty"))
        } yield moves :+ Play(relic,
          PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
          Location.PlayArea(actor), Orientation.FaceDown)
        case _ => Left(OathViolation.InvalidEventOrder(
          "no Forge assignment answer is recorded"))
      })

    Sequence(paySupply, assignmentDecide, forgeRelic)
      .copy(window = Some(PowerWindow.ForgeActionEligibility))
  }

  /** Forge is an Act action of the active player in this slice. */
  private object ActiveOwner extends OwnerQuery {
    def owner(ctx: WalkerCtx): Option[PlayerId] =
      Some(ctx.ready.game.current.turn.activePlayer)
  }
}
