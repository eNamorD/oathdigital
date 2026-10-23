package oathdigital.gameplay

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
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

  test("a minor action refuses an off-turn actor and a start selection"):
    assertEquals(rules.startWalker(Ready(base), ActionRef.PeekSiteRelics, p2)
      .left.toOption, Some(OathViolation.WrongPlayer(p1, p2)))
    val selected = rules.startWalker(Ready(base), ActionRef.RevealOwnedRelic,
      p1, Vector(DecisionOptionRef.Relic(cursedCauldron)))
    assert(selected.isLeft, selected)

  test("Reveal Owned Relic and Move Warbands refuse what a global " +
      "restriction refuses"):
    val refuseAllStub = new OperationRestriction:
      override def reason(state: ReadyGame, operation: CoreOperation)
          : Option[OperationReason] = Some(OperationReason("refused",
        "every operation is refused", OperationReasonKind.Impossible))
    val refusing = new OathRules(catalog, walkerPowerCatalog = WalkerPowers(
      Vector(PowerStubs.OperationRestrictionStub(PowerId("test.refuse-all"),
        refuseAllStub))))
    val refused = Some(OathViolation.CoreOperationRejected("refused",
      "every operation is refused"))
    // Reveal asks first, and its step is refused once the relic is chosen.
    val asked = accepted(refusing.startWalker(Ready(base),
      ActionRef.RevealOwnedRelic, p1))
    assertEquals(refusing.resolveWalker(asked.state, p1,
      MinorActionProcedures.relicDecisionId,
      ChooseOneAnswer(DecisionOptionRef.Relic(cursedCauldron))).left.toOption,
      refused)
    // Move Warbands looks ahead at its direction question and is refused at
    // the start.
    assertEquals(refusing.startWalker(Ready(base), ActionRef.MoveWarbands, p1)
      .left.toOption, refused)

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
