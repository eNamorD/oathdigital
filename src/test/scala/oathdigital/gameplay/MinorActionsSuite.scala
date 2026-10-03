package oathdigital.gameplay

import oathdigital.gameplay.actions.{MinorActionCommand,
  MinorActionOperationPolicy, MinorActions}
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Journal, Look, Outcomes, Table}
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

  test("facedown adviser discard uses the next region and costs no Supply"):
    val started = accepted(rules.startWalker(Ready(base),
      ActionRef.PlayFacedownAdviser, p1,
      startArgs = Vector(DecisionOptionRef.Denizen(magiciansCode))))
    val discarded = accepted(rules.resolveWalker(started.state, p1,
      PlacementFixture.cardPlayDecisionId(magiciansCode, "place"),
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("discard"))))
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

    assert(rules.handle(Ready(base), MinorActionCommand.MoveWarbands(
      p1, toSite = false, 3)).isLeft)
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
    val refuseAll = new OperationRestriction:
      override def reason(state: ReadyGame, operation: CoreOperation)
          : Option[OperationReason] = Some(OperationReason("refused",
        "every operation is refused", OperationReasonKind.Impossible))
    assert(MinorActions.handle(catalog, Ready(base),
      MinorActionCommand.MoveWarbands(p1, toSite = true, 2),
      Vector(refuseAll)).isLeft)

  test("source-scoped fallback and replay use the recorded off-turn actor"):
    // p2, not the active player, holds Revelation faceup.
    val revelation = CatalogNames.denizen("Revelation")
    val changed = minorBoard.adviser(p2, revelation).ready
    val source = RuleSourceRef.Adviser(p2, revelation)
    val expected = accepted(PowerRuntime.ignoredAtSource(catalog, changed, p2,
      ActionKind.WhenPlayed, source))
    assertEquals(expected.map(_.handlerId), Vector("denizen.revelation"))
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
    val started = accepted(rules.startWalker(Ready(modified),
      ActionRef.PlayFacedownAdviser, p1,
      startArgs = Vector(DecisionOptionRef.Denizen(locked))))
    val discarded = accepted(rules.resolveWalker(started.state, p1,
      PlacementFixture.cardPlayDecisionId(locked, "place"),
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("discard"))))
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
    val started = accepted(rules.startWalker(act.state,
      ActionRef.PlayFacedownAdviser, active, startArgs = Vector(adviser match {
        case id: DenizenId => DecisionOptionRef.Denizen(id)
        case id: VisionId => DecisionOptionRef.Vision(id)
      })))
    val discarded = accepted(rules.resolveWalker(started.state, active,
      PlacementFixture.cardPlayDecisionId(adviser, "place"),
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("discard"))))
    val events = setupEvents ++ act.events ++ started.events ++ discarded.events
    val replayed = Journal.replayed(rules, events)
    assertEquals(replayed, discarded.state)
