package oathdigital.gameplay.actions.forge

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.ForgeRules
import oathdigital.gameplay.OathLifecycle
import oathdigital.model._
import oathdigital.model.DecisionAnswer.PartitionAnswer

/** Declared Forge procedure tree for the walker (batch 1, Task 2).
  *
  * Reproduces the legacy `Forge.handle`/`Forge.evolve` observable flow on the
  * generic walker:
  *
  * {{{
  * Sequence(                                  // window = ForgeActionEligibility
  *   BuildOps(SpendSupply(actor, 1)),        // window = ForgeCost
  *   Branch(-> Decide("forge.assignment")),   // mixed printed cost only
  *   BuildOps(one PayCost per placed resource,
  *            Play(relic-deck top -> play area, FaceDown)))
  * }}}
  *
  * Windows: the root carries `ForgeActionEligibility` (eligibility-shaped
  * restrictions/relaxations gather there) and the supply payment carries
  * `ForgeCost`. `SpendSupply` is a bare `PrimitiveOperation` with no window
  * field of its own, so the payment is stated as a `BuildOps` whose window
  * hooks the node -- the same shape Recover's relic move uses at
  * `RecoverAfterRelic`. `ForgeModifierSelection` is not a tree node: it is the
  * window a player-selected power is offered at, answered by `StartWalker`'s
  * `modifiers`. No other node carries a window.
  *
  * Start gates are `ForgeRules.validate` unchanged -- audited catalog, the
  * actor ruling their pawn site, a printed Forge cost totalling three
  * resources, exactly three empty faceup denizens, supply >= 1, and a
  * non-empty relic deck. Together they are what makes a started Forge always
  * answerable: three eligible targets exist, the printed cost can be spread
  * over them, and a relic is waiting on the deck.
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
  *    `RecoverProcedure.actorFacedownRelics` is. They are what the declared
  *    query offers, and the projector projects that query, so the offered set
  *    and the accepted set have one definition rather than two expressions
  *    that agree today and diverge the first time a power moves a denizen.
  *
  * **Who pays.** The actor funds the printed cost from their own play area:
  * each placed resource is a `PayCost` out of `Location.PlayArea(actor)` onto
  * the chosen denizen. The suit banks are not consulted, which reverses the
  * pre-walker behaviour of drawing favor from the target denizen's own suit
  * bank. `ForgeRules.validate` gates affordability up front, because a Forge
  * that starts with the player unable to pay spends Supply and then fails at
  * its last node.
  *
  * `build`/`rebuild` take the [[ExecutableCatalog]] to read the site's printed
  * cost; nothing else about the catalog reaches the tree, which closes over
  * the actor and that cost alone.
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
    * targets: exactly the options the assignment decision declares, and so
    * exactly the set a client is offered. Read live off
    * `state.game.current.map.sites` on every call, never off a tree closure --
    * an edifice, a facedown denizen, or a denizen already carrying a token is
    * not a target.
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

  /** Fresh start: every gate in `ForgeRules.validate` runs, plus the
    * lifecycle gate every other fresh start checks (`RecoverProcedure`,
    * `TravelProcedure`) -- `completeAction` no longer stops the boundary at
    * a finished game itself (Task 8), so every action start must refuse one.
    */
  def build(catalog: ExecutableCatalog, state: ReadyGame,
      activePlayer: PlayerId): Either[OathViolation, Operation] = for {
    _ <- OathLifecycle.validateAct(OathState.Ready(state), activePlayer)
    player <- actorState(state, activePlayer)
    siteId <- player.pawnSite.toRight(OathViolation.PawnSiteMissing(activePlayer))
    facts <- ForgeRules.validate(catalog, state, player, siteId)
  } yield tree(activePlayer, facts._2)

  /** Rebuilds the same command-local tree for an already-started Forge.
    *
    * Start-only gates do not re-run, and supply is the one that matters:
    * starting a Forge spends 1 Supply, so a player who began with exactly 1
    * holds 0 when the assignment decision parks. `ForgeRules.validate`
    * requires supply >= 1 and would therefore strand a legally started Forge
    * with no way to answer it -- the walker's worst failure mode. Only what
    * resuming genuinely requires runs here: the actor's site, and the printed
    * cost the decision's section minima come from.
    */
  def rebuild(catalog: ExecutableCatalog, state: ReadyGame,
      activePlayer: PlayerId): Either[OathViolation, Operation] = for {
    siteId <- actorSite(state, activePlayer).toRight(
      OathViolation.PawnSiteMissing(activePlayer))
    cost <- printedCost(catalog, siteId)
  } yield tree(activePlayer, cost)

  private def actorState(state: ReadyGame,
      actor: PlayerId): Either[OathViolation, PlayerState] =
    state.game.current.players.find(_.player == actor)
      .toRight(OathViolation.PawnSiteMissing(actor))

  /** The site's printed Forge cost, worded exactly as `ForgeRules.validate`
    * words the same rejection so a resume and a start fail alike.
    *
    * Public for the same reason [[eligibleTargets]] is: it is the source of
    * both section minima, which is what tells a client how many favor and how
    * many secrets its answer must name -- one definition, not a second read
    * of `forgeRequirements` at the projector.
    */
  def printedCost(catalog: ExecutableCatalog,
      siteId: SiteId): Either[OathViolation, Tokens] =
    catalog.sites.find(_.id == siteId).flatMap(_.forgeRequirements)
      .toRight(OathViolation.ForgeUnavailable("site has no printed Forge cost"))

  /** The two sections a Forge assignment spreads its targets across. Stable
    * keys: a recorded [[oathdigital.model.DecisionPlacement]] names one of
    * these, and the trailing operation node reads the resource back out of it.
    */
  val favorSectionKey: String = "pay-favor"
  val secretSectionKey: String = "pay-secret"

  /** Whether a printed cost leaves the player an actual decision to make.
    *
    * Four of the seven forgeable sites print three of a single resource, and
    * at those sites one section demands every eligible target: there is
    * exactly one legal answer, so prompting for it asks the player to
    * rubber-stamp a foregone conclusion. `DecisionQueries.wellFormed` rejects
    * such a query outright, which makes this a hard constraint rather than a
    * courtesy -- a Forge that parked there could never be answered at all.
    *
    * A single-resource Forge therefore declares no decision node, and its
    * trailing operation node applies the determined split itself. This is a
    * deliberate behaviour change: the pre-walker Forge parked and prompted at
    * those four sites.
    */
  def parks(cost: Tokens): Boolean = cost.favor > 0 && cost.secrets > 0

  /** The tree closes only over command-stable data (the actor and the printed
    * cost), so the walker re-derives an identical tree every command (spec
    * decision S1). Everything state-dependent -- which denizens are eligible,
    * which relic is on top of the deck -- is read off `ready` when the node
    * it belongs to runs.
    */
  private def tree(actor: PlayerId, cost: Tokens): Operation = {
    val total = cost.favor + cost.secrets

    val sections = Vector(
      DecisionSection(favorSectionKey, "Pay Favor", cost.favor),
      DecisionSection(secretSectionKey, "Pay Secret", cost.secrets))

    // A `Branch`, not a bare `Decide`, because the options are the eligible
    // targets read LIVE off `ready` (ruling R14) -- the same method the
    // projector reads, so the offered set and the accepted set are one
    // expression. A denizen that gained a token since the park is simply
    // absent from the rebuilt query, and an answer naming it is rejected by
    // the generic validator without this action stating a rule of its own.
    val assignmentDecide = Branch((ready, _) => Vector(Decide(
      decisionId = assignmentDecisionId,
      owner = actor,
      query = DecisionQuery.Partition(sections,
        eligibleTargets(ready, actor).map(target =>
          DecisionOption.Denizen(
            DecisionOptionRef.Denizen(target.denizenId))),
        // The panel's own copy, declared where the sections are declared
        // (plan ruling R4). Before this the frontend read `action ==
        // "forge"` to title a panel whose interaction was already generic,
        // which put the last action-shaped string in the engine's client.
        heading = Some("Forge a relic"),
        confirmLabel = Some("Complete Forge")))))

    // The actor funds the payment from their own play area, and the suit
    // banks are never consulted. `PayCost` is what states that: its placed
    // portions move out of `Location.PlayArea(actor)` onto the named card,
    // and `OperationPipeline` validates the whole batch atomically.
    def payment(denizen: DenizenId,
        sectionKey: String): Either[OathViolation, CoreOperation] =
      sectionKey match {
        case `favorSectionKey` =>
          Right(PayCost(actor, Location.OnCard(denizen), Cost(favor = 1)))
        case `secretSectionKey` =>
          Right(PayCost(actor, Location.OnCard(denizen), Cost(secret = 1)))
        case other => Left(OathViolation.InvalidEventOrder(
          s"$assignmentDecisionId has no section '$other'"))
      }

    def payments(denizens: Vector[(DenizenId, String)])
        : Either[OathViolation, Vector[CoreOperation]] =
      denizens.foldLeft[Either[OathViolation, Vector[CoreOperation]]](
        Right(Vector.empty)) { case (result, (denizen, sectionKey)) =>
        for {
          operations <- result
          operation <- payment(denizen, sectionKey)
        } yield operations :+ operation
      }

    /** The answered split, for a site whose printed cost names both
      * resources. The generic validator has already checked that every
      * declared option is placed exactly once in a declared section, so the
      * two rejections below are contract failures, not bad submissions.
      */
    def answeredPayments(pending: PendingTree)
        : Either[OathViolation, Vector[CoreOperation]] =
      pending.answered.lastOption match {
        case Some(Answered(_, PartitionAnswer(placements), _)) =>
          placements.foldLeft[Either[OathViolation,
              Vector[(DenizenId, String)]]](Right(Vector.empty)) {
            case (result, placement) => for {
              rows <- result
              denizen <- placement.option match {
                case DecisionOptionRef.Denizen(id) => Right(id)
                case other => Left(OathViolation.InvalidEventOrder(
                  s"$assignmentDecisionId placed a non-denizen option $other"))
              }
            } yield rows :+ (denizen -> placement.sectionKey)
          }.flatMap(payments)
        case _ => Left(OathViolation.InvalidEventOrder(
          "no Forge assignment answer is recorded"))
      }

    /** The determined split, for a site printing three of one resource:
      * every eligible target takes that resource, so there is nothing to
      * read out of an answer and no answer was ever asked for.
      */
    def determinedPayments(ready: ReadyGame)
        : Either[OathViolation, Vector[CoreOperation]] = {
      val targets = eligibleTargets(ready, actor)
      val sectionKey =
        if (cost.favor > 0) favorSectionKey else secretSectionKey
      if (targets.size != total) Left(OathViolation.ForgeUnavailable(
        s"site offers ${targets.size} Forge targets but the printed cost " +
          s"needs $total"))
      else payments(targets.map(_.denizenId -> sectionKey))
    }

    // `SpendSupply` carries no window of its own, so the payment is a
    // BuildOps whose window is the hook point (see this object's doc).
    val paySupply = BuildOps((_, _) => Right(Vector[CoreOperation](
      SpendSupply(actor, supplyCost))),
      window = Some(PowerWindow.ForgeCost))

    val forgeRelic = BuildOps((ready, pending) => for {
      placed <- if (parks(cost)) answeredPayments(pending)
        else determinedPayments(ready)
      relic <- ready.game.current.commonCards.relicDeck.headOption.toRight(
        OathViolation.ForgeUnavailable("relic deck is empty"))
    } yield placed :+ Play(relic,
      PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
      Location.PlayArea(actor), Orientation.FaceDown))

    val nodes: Vector[Operation] =
      if (parks(cost)) Vector(paySupply, assignmentDecide, forgeRelic)
      else Vector(paySupply, forgeRelic)

    Sequence(nodes).copy(window = Some(PowerWindow.ForgeActionEligibility))
  }
}
