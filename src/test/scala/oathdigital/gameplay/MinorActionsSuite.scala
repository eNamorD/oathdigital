package oathdigital.gameplay

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.actions.{MinorActionCommand,
  MinorActionOperationPolicy, MinorActions}
import oathdigital.gameplay.actions.minor.MinorActionProcedures
import oathdigital.gameplay.actions.minor.MinorActionProcedures.{toBoard,
  toSite}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.economy.VowOfWandering
import oathdigital.gameplay.walker.ActionBoundaryRan
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer, ChooseOneAnswer}
import oathdigital.testkit.{ParkedNode, PowerStubs}
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Journal, Look, Outcomes, PlacementPicks, PowerRules, Table}
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.gameplay.setup._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathEvent._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.Unwrap.{accepted, present}

class MinorActionsSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)
  private val parked = new ParkedDecisionAssertions(catalog)

  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  private val magiciansCode = CatalogNames.denizen("Magician's Code")
  private val stickyFire = CatalogNames.relic("Sticky Fire")
  private val cursedCauldron = CatalogNames.relic("Cursed Cauldron")

  /** p1 in Act at Broken Peaks (where p2 also stands), ruling it with 3
    * warbands and holding 4 more, a facedown Magician's Code and a facedown
    * Cursed Cauldron. Broken Peaks holds Sticky Fire, facedown. */
  private def minorBoard: Table = Table.start
    .pawn(p1, at = brokenPeaks).boardWarbands(p1, 4)
    .warbandsAt(brokenPeaks, p1, 3)
    .adviser(p1, magiciansCode, facedown = true)
    .relic(p1, cursedCauldron, facedown = true)
    .relicAt(stickyFire, at = brokenPeaks)

  private def base: ReadyGame = minorBoard.ready

  private val dowsingSticks = CatalogNames.relic("Dowsing Sticks")

  /** p1's warbands on the board, and the forces at Broken Peaks. The rest
    * of the state is not compared: the action boundary may refill bandits
    * elsewhere. */
  private def warbandCounts(state: OathState): (Int, SiteForces) =
    val ready = Outcomes.ready(state)
    (Look(ready).boardWarbands(p1),
      ready.game.current.map.sites(brokenPeaks).forces)

  /** The query of the decision `state` is parked on, rebuilt as the
    * projector rebuilds it. */
  private def parkedDecisionQuery(state: OathState,
      powers: WalkerPowers = WalkerPowers.empty)(
      using munit.Location): DecisionQuery =
    ParkedNode.of(state, catalog, powers) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide.query
      case other => fail(s"expected a parked decision, got $other")

  /** `events` evolved one by one from `start`. */
  private def replayedFrom(start: ReadyGame, events: Vector[OathEvent])
      : Either[OathViolation, OathState] =
    events.foldLeft[Either[OathViolation, OathState]](Right(Ready(start))):
      case (Right(state), event) => rules.evolve(state, event)
      case (failure @ Left(_), _) => failure

  private def wayButton(ref: DecisionOptionRef.Button, label: String) =
    DecisionOption.Button(ref, label)

  private def blockedWay(ref: DecisionOptionRef.Button, label: String,
      reason: String) = DecisionOption.Unavailable(wayButton(ref, label),
    OptionReason.Written(reason))

  test("facedown adviser discard uses the next region and costs no Supply"):
    val started = accepted(FacedownAdviserPlay.parkedOnPlacement(rules,
      Ready(base), p1, magiciansCode))
    val discarded = accepted(rules.resolveWalker(started.state, p1,
      PlacementFixture.cardPlayDecisionId(magiciansCode, "place"),
      PlacementPicks.parkedAnswer(started.state, PlayedAs.Discarded,
        WalkerPowers.empty)))
    val after = Outcomes.ready(discarded.state)
    assertEquals(Look(after).supply(p1), Look(base).supply(p1))
    // Broken Peaks is in the Cradle, so the discard goes to the Provinces.
    assertEquals(base.game.current.map.regionOf(brokenPeaks),
      Some(Region.Cradle))
    assert(after.game.current.commonCards.discard(Region.Provinces)
      .contains(magiciansCode))

  test("site relic peek uses core operations and preserves knowledge"):
    val command = MinorActionCommand.PeekSiteRelics(p1)
    val peeked = accepted(MinorActions.handle(catalog, Ready(base), command,
      Vector.empty))
    val peekReady = Outcomes.ready(peeked.state)
    assert(peekReady.knowledge.siteRelics(p1)(brokenPeaks).contains(stickyFire))
    val event = present(peeked.events.collectFirst {
      case value: SiteRelicsPeeked => value }, "the peek event")
    val expected = base.copy(knowledge = base.knowledge.copy(siteRelics =
      base.knowledge.siteRelics.updated(p1,
        base.knowledge.siteRelics.getOrElse(p1, Map.empty)
          .updated(brokenPeaks, event.relics))))
    assertEquals(peeked.state, Ready(expected))
    assertEquals(peeked.events, Vector(event))
    parked.assertResumed(peeked.state, Phase.Act, p1)
    assertEquals(rules.evolve(Ready(base), event), Right(Ready(expected)))

    // p1 remembers Dowsing Sticks at Broken Peaks from a peek made before
    // Dowsing Sticks left the site; the new peek adds to that memory. A peek
    // replays only against the relics the site holds now, and
    // `Table.knowsRelicAt` records only a relic still there, so the memory is
    // a narrow patch.
    val dowsingSticks = CatalogNames.relic("Dowsing Sticks")
    val withPriorKnowledge = base.copy(knowledge = base.knowledge.copy(
      siteRelics = Map(p1 -> Map(brokenPeaks -> Vector(dowsingSticks)))))
    val expectedMerged = withPriorKnowledge.copy(knowledge =
      withPriorKnowledge.knowledge.copy(siteRelics = Map(p1 -> Map(
        brokenPeaks -> (Vector(dowsingSticks) ++ event.relics)))))
    assertEquals(MinorActions.evolve(catalog, Ready(withPriorKnowledge), event,
      Vector.empty), Right(Ready(expectedMerged)))
    val changedSite = minorBoard.relicAt(dowsingSticks, at = brokenPeaks).ready
    assertEquals(MinorActions.evolve(catalog, Ready(changedSite), event,
      Vector.empty).left.toOption,
      Some(OathViolation.MinorActionOutcomeMismatch(
        "recorded site relic peek does not match the pawn's site")))

  test("owned relic reveal uses core operation and preserves replay"):
    val revealCommand = MinorActionCommand.RevealOwnedRelic(p1, cursedCauldron)
    val revealed = accepted(MinorActions.handle(catalog, Ready(base),
      revealCommand, Vector.empty))
    val completed = accepted(rules.handle(Ready(base), revealCommand))
    val revealedReady = Outcomes.ready(revealed.state)
    assertEquals(Look(revealedReady).player(p1).relics.head.orientation,
      Orientation.FaceUp)

    val revealEvent = present(revealed.events.collectFirst {
      case event: OwnedRelicRevealed => event }, "the reveal event")
    val expected = minorBoard.relic(p1, cursedCauldron, facedown = false).ready
    assertEquals(revealedReady, expected)
    assertEquals(revealed.events, Vector(revealEvent))
    parked.assertResumed(revealed.state, Phase.Act, p1)
    assertEquals(rules.evolve(Ready(base), revealEvent), Right(Ready(expected)))
    val replayed = completed.events.foldLeft[
      Either[OathViolation, OathState]](Right(Ready(base))):
      case (Right(state), event) => rules.evolve(state, event)
      case (failure @ Left(_), _) => failure
    assertEquals(replayed, Right(completed.state))

  test("owned relic reveal rejects unheld and already-faceup relics"):
    val revealEvent = OwnedRelicRevealed(p1, cursedCauldron)
    assertEquals(MinorActions.evolve(catalog, Ready(base),
      OwnedRelicRevealed(p1, stickyFire), Vector.empty).left.toOption,
      Some(OathViolation.MinorActionUnavailable(
        "relic is not held by the actor")))
    val alreadyFaceUp = minorBoard.relic(p1, cursedCauldron, facedown = false)
      .ready
    assertEquals(MinorActions.evolve(catalog, Ready(alreadyFaceUp),
      revealEvent, Vector.empty).left.toOption,
      Some(OathViolation.MinorActionOutcomeMismatch(
        "recorded relic was not facedown")))

  test("minor-action operation policy permits roots only in validated context"):
    val reveal = oathdigital.model.Reveal(cursedCauldron,
      oathdigital.model.Location.PlayArea(p1))
    val directFlip = oathdigital.model.Flip(cursedCauldron,
      oathdigital.model.Location.PlayArea(p1),
      Orientation.FaceUp)

    assertEquals(MinorActionOperationPolicy.validate(base, reveal), Right(()))
    assertEquals(MinorActionOperationPolicy.validate(base, directFlip)
      .left.toOption, Some(OperationError.RestrictedOperation(
        "minor-action semantic root is not permitted")))

  test("warband moves use core operations in both directions and replay"):
    val lineage = Look(base).player(p1).lineage
    val toSite = accepted(MinorActions.handle(catalog, Ready(base),
      MinorActionCommand.MoveWarbands(p1, toSite = true, 2), Vector.empty))
    val toSiteEvent = present(toSite.events.collectFirst {
      case value: WarbandsMoved => value }, "the move event")
    val atSite = Outcomes.ready(toSite.state)
    // Two move to the site: 4 - 2 on the board, 3 + 2 at the site.
    val expectedAtSite = minorBoard.boardWarbands(p1, 2)
      .warbandsAt(brokenPeaks, p1, 5).ready
    assertEquals(atSite, expectedAtSite)
    assertEquals(toSite.events, Vector(toSiteEvent))
    parked.assertResumed(toSite.state, Phase.Act, p1)
    assertEquals(rules.evolve(Ready(base), toSiteEvent), Right(Ready(atSite)))
    assertEquals(Look(atSite).boardWarbands(p1), 2)
    assertEquals(atSite.game.current.map.sites(brokenPeaks).forces,
      SiteForces.Occupied(ForceKind.Exile(lineage), 5))

    assertEquals(rules.handle(Ready(base), MinorActionCommand.MoveWarbands(
      p1, toSite = false, 3)), Left(OathViolation.MinorActionUnavailable(
        "at least one warband must remain at the site")))
    val toBoard = accepted(MinorActions.handle(catalog, Ready(base),
      MinorActionCommand.MoveWarbands(p1, toSite = false, 2), Vector.empty))
    val toBoardEvent = present(toBoard.events.collectFirst {
      case value: WarbandsMoved => value }, "the move event")
    val onBoard = Outcomes.ready(toBoard.state)
    // Two move back: 4 + 2 on the board, 3 - 2 at the site.
    val expectedOnBoard = minorBoard.boardWarbands(p1, 6)
      .warbandsAt(brokenPeaks, p1, 1).ready
    assertEquals(onBoard, expectedOnBoard)
    assertEquals(rules.evolve(Ready(base), toBoardEvent), Right(Ready(onBoard)))
    assertEquals(Look(onBoard).boardWarbands(p1), 6)
    assertEquals(onBoard.game.current.map.sites(brokenPeaks).forces,
      SiteForces.Occupied(ForceKind.Exile(lineage), 1))

  test("minor actions run under the global restrictions"):
    // Stub of OperationRestriction: refuses every operation.
    val refuseAllStub = new OperationRestriction:
      override def reason(state: ReadyGame, operation: CoreOperation)
          : Option[OperationReason] = Some(OperationReason("refused",
        "every operation is refused", OperationReasonKind.Impossible))
    assertEquals(MinorActions.handle(catalog, Ready(base),
      MinorActionCommand.MoveWarbands(p1, toSite = true, 2),
      Vector(refuseAllStub)), Left(OathViolation.MinorActionUnavailable(
        "every operation is refused")))

  test("Vow of Wandering refuses a move to the site with its reason, and " +
      "allows one back to the board"):
    val holding = minorBoard.adviser(p1,
      CatalogNames.denizen("Vow of Wandering")).ready
    val withPowers = PowerRules.withWalkerPowers
    assertEquals(withPowers.handle(Ready(holding),
      MinorActionCommand.MoveWarbands(p1, toSite = true, 1)),
      Left(OathViolation.MinorActionUnavailable(
        "Vow of Wandering forbids placing warbands on sites")))
    assert(withPowers.handle(Ready(holding),
      MinorActionCommand.MoveWarbands(p1, toSite = false, 2)).isRight)

  test("Peek Site Relics peeks at every relic at the pawn's site, runs the " +
      "boundary and replays"):
    val peeked = accepted(rules.startWalker(Ready(base),
      ActionRef.PeekSiteRelics, p1))
    val after = Outcomes.ready(peeked.state)
    assert(after.knowledge.siteRelics(p1)(brokenPeaks).contains(stickyFire))
    parked.assertResumed(peeked.state, Phase.Act, p1)
    assertEquals(peeked.events.count(_ == ActionBoundaryRan), 1)
    assertEquals(replayedFrom(base, peeked.events), Right(peeked.state))

  test("Peek Site Relics refuses a site with no relics"):
    val bare = Table.start.pawn(p1, at = brokenPeaks).ready
    // The precondition: nothing lies at Broken Peaks.
    assertEquals(Look(bare).relicsAt(brokenPeaks), Vector.empty)
    assertEquals(rules.startWalker(Ready(bare), ActionRef.PeekSiteRelics, p1)
      .left.toOption, Some(OathViolation.MinorActionUnavailable(
        "the pawn's site has no relics")))

  test("Reveal Owned Relic asks which facedown relic, then reveals it"):
    val twoRelics = minorBoard.relic(p1, dowsingSticks, facedown = true).ready
    val asked = accepted(rules.startWalker(Ready(twoRelics),
      ActionRef.RevealOwnedRelic, p1))
    parked.assertParked(asked.state, ActionRef.RevealOwnedRelic,
      MinorActionProcedures.relicDecisionId, p1)
    assertEquals(parkedDecisionQuery(asked.state),
      DecisionQuery.ChooseOne(Vector(cursedCauldron, dowsingSticks).map(id =>
        DecisionOption.Relic(DecisionOptionRef.Relic(id))),
        heading = Some("Choose a relic to reveal")))
    val revealed = accepted(rules.resolveWalker(asked.state, p1,
      MinorActionProcedures.relicDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Relic(cursedCauldron))))
    assertEquals(Look(revealed.state).player(p1).relics
      .map(relic => relic.id -> relic.orientation), Vector(
        cursedCauldron -> Orientation.FaceUp,
        dowsingSticks -> Orientation.FaceDown))
    parked.assertResumed(revealed.state, Phase.Act, p1)
    assertEquals(replayedFrom(twoRelics, asked.events ++ revealed.events),
      Right(revealed.state))

  test("Reveal Owned Relic refuses when no relic is facedown"):
    val faceUp = minorBoard.relic(p1, cursedCauldron, facedown = false).state
    assertEquals(rules.startWalker(faceUp, ActionRef.RevealOwnedRelic, p1)
      .left.toOption, Some(OathViolation.MinorActionUnavailable(
        "no facedown relic to reveal")))

  test("Move Warbands asks the way, then how many, in both directions"):
    val exiles = ForceKind.Exile(Look(base).player(p1).lineage)
    val asked = accepted(rules.startWalker(Ready(base),
      ActionRef.MoveWarbands, p1))
    parked.assertParked(asked.state, ActionRef.MoveWarbands,
      MinorActionProcedures.directionDecisionId, p1)
    assertEquals(parkedDecisionQuery(asked.state), DecisionQuery.ChooseOne(
      Vector(wayButton(toSite, "To your site"),
        wayButton(toBoard, "To your board")),
      heading = Some("Move warbands")))

    // To the site: up to all 4 on the board.
    val towardSite = accepted(rules.resolveWalker(asked.state, p1,
      MinorActionProcedures.directionDecisionId, ChooseOneAnswer(toSite)))
    assertEquals(parkedDecisionQuery(towardSite.state),
      DecisionQuery.ChooseAmount(1, 4,
        Some("Move how many warbands to your site?"), "Move"))
    val atSite = accepted(rules.resolveWalker(towardSite.state, p1,
      MinorActionProcedures.amountDecisionId, ChooseAmountAnswer(2)))
    assertEquals(warbandCounts(atSite.state),
      (2, SiteForces.Occupied(exiles, 5)))
    assertEquals(replayedFrom(base,
      asked.events ++ towardSite.events ++ atSite.events),
      Right(atSite.state))

    // To the board: one warband must stay, so up to 2 of the 3.
    val towardBoard = accepted(rules.resolveWalker(asked.state, p1,
      MinorActionProcedures.directionDecisionId, ChooseOneAnswer(toBoard)))
    assertEquals(parkedDecisionQuery(towardBoard.state),
      DecisionQuery.ChooseAmount(1, 2,
        Some("Move how many warbands to your board?"), "Move"))
    val onBoard = accepted(rules.resolveWalker(towardBoard.state, p1,
      MinorActionProcedures.amountDecisionId, ChooseAmountAnswer(2)))
    assertEquals(warbandCounts(onBoard.state),
      (6, SiteForces.Occupied(exiles, 1)))

  test("Move Warbands shows a way it cannot take with its reason"):
    // No warbands on the board: only the way back is open.
    val emptyBoard = minorBoard.boardWarbands(p1, 0).state
    val asked = accepted(rules.startWalker(emptyBoard,
      ActionRef.MoveWarbands, p1))
    assertEquals(parkedDecisionQuery(asked.state), DecisionQuery.ChooseOne(
      Vector(blockedWay(toSite, "To your site", "No warbands on your board"),
        wayButton(toBoard, "To your board")),
      heading = Some("Move warbands")))
    assert(rules.resolveWalker(asked.state, p1,
      MinorActionProcedures.directionDecisionId,
      ChooseOneAnswer(toSite)).isLeft)

  test("Vow of Wandering blocks the way to the site with its reason, and " +
      "leaves the way back open"):
    val holding = minorBoard.adviser(p1,
      CatalogNames.denizen("Vow of Wandering")).state
    val withPowers = PowerRules.withWalkerPowers
    val asked = accepted(withPowers.startWalker(holding,
      ActionRef.MoveWarbands, p1))
    assertEquals(parkedDecisionQuery(asked.state,
      WalkerPowerCatalog.default(catalog)), DecisionQuery.ChooseOne(
      Vector(blockedWay(toSite, "To your site", VowOfWandering.Reason),
        wayButton(toBoard, "To your board")),
      heading = Some("Move warbands")))
    val towardBoard = accepted(withPowers.resolveWalker(asked.state, p1,
      MinorActionProcedures.directionDecisionId, ChooseOneAnswer(toBoard)))
    assert(withPowers.resolveWalker(towardBoard.state, p1,
      MinorActionProcedures.amountDecisionId, ChooseAmountAnswer(2)).isRight)

  test("Move Warbands refuses to start when neither way is open"):
    // Nothing on the board, and only one warband at the site.
    val stuck = minorBoard.boardWarbands(p1, 0)
      .warbandsAt(brokenPeaks, p1, 1).state
    assertEquals(rules.startWalker(stuck, ActionRef.MoveWarbands, p1)
      .left.toOption, Some(OathViolation.MinorActionUnavailable(
        "No warbands on your board; One warband must stay at your site")))

  test("a minor action refuses what a global restriction refuses"):
    // Stub of OperationRestriction: refuses every operation.
    val refuseAllStub = new OperationRestriction:
      override def reason(state: ReadyGame, operation: CoreOperation)
          : Option[OperationReason] = Some(OperationReason("refused",
        "every operation is refused", OperationReasonKind.Impossible))
    val refusing = new OathRules(catalog, walkerPowerCatalog = WalkerPowers(
      Vector(PowerStubs.OperationRestrictionStub(PowerId("test.refuse-all"),
        refuseAllStub))))
    assertEquals(refusing.startWalker(Ready(base), ActionRef.PeekSiteRelics,
      p1).left.toOption, Some(OathViolation.CoreOperationRejected("refused",
        "every operation is refused")))

  test("source-scoped fallback and replay use the recorded off-turn actor"):
    // p2, not the active player, holds Bewitch faceup.
    val bewitch = CatalogNames.denizen("Bewitch")
    val changed = minorBoard.adviser(p2, bewitch).ready
    val source = RuleSourceRef.Adviser(p2, bewitch)
    val expected = accepted(PowerRuntime.ignoredAtSource(catalog, changed, p2,
      ActionKind.WhenPlayed, source))
    assertEquals(expected.map(_.handlerId), Vector("denizen.bewitch"))
    assertEquals(accepted(PowerRuntime.ignoredAtSource(catalog, changed, p1,
      ActionKind.WhenPlayed, source)), Vector.empty)
    // Replay accepts the off-turn player's diagnostics and changes no state.
    val event = IgnoredRulesRecorded(p2, ActionKind.WhenPlayed, expected)
    assertEquals(rules.evolve(Ready(changed), event), Right(Ready(changed)))

  // The lock itself, which stops a faceup adviser being discarded, is
  // OperationRestrictionsSuite's; this is the facedown side of it.
  test("a locked adviser held facedown can still be played and discarded"):
    // Sealing Ward is Locked and adviser-only; p1 holds only it.
    val locked = CatalogNames.denizen("Sealing Ward")
    val modified = Table.start.pawn(p1, at = brokenPeaks)
      .adviser(p1, locked, facedown = true).ready
    val started = accepted(FacedownAdviserPlay.parkedOnPlacement(rules,
      Ready(modified), p1, locked))
    val discarded = accepted(rules.resolveWalker(started.state, p1,
      PlacementFixture.cardPlayDecisionId(locked, "place"),
      PlacementPicks.parkedAnswer(started.state, PlayedAs.Discarded,
        WalkerPowers.empty)))
    assertEquals(Look(discarded.state).advisers(p1), Vector.empty)

  test("valid setup history replays exactly through a completed minor action"):
    val SetupResult(setupState, setupEvents) = setupThroughWake()
    val active = firstPlayer
    assertEquals(Look(setupState).active, active)
    // The first card the first player kept in Setup.
    val adviser = present(Look(setupState).player(active).advisers.headOption
      .map(_.id).collect { case id: WorldCardId => id }, "a kept adviser")
    val act = accepted(rules.startWalker(setupState, PhaseTransitionRef.EndWake,
      active))
    val started = accepted(FacedownAdviserPlay.parkedOnPlacement(rules,
      act.state, active, adviser))
    val discarded = accepted(rules.resolveWalker(started.state, active,
      PlacementFixture.cardPlayDecisionId(adviser, "place"),
      PlacementPicks.parkedAnswer(started.state, PlayedAs.Discarded,
        WalkerPowers.empty)))
    val events = setupEvents ++ act.events ++ started.events ++ discarded.events
    val replayed = Journal.replayed(rules, events)
    assertEquals(replayed, discarded.state)
