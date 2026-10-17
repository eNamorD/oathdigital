package oathdigital.gameplay.powers.action

import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.powerresolver.{ModifierPicks, QueuedStarts}
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.powers.travel.{BrassArmyMoves, TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureDequeued, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  ParkedNode, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Palanquin: the user is placed where the target could travel, and the
  * target travels there, off turn and for no Supply (catalog batch 8,
  * slice 15). */
class PalanquinSuite extends munit.FunSuite:
  import TravelFixture.{board, coast, cradlePlains, on, provincePlains}

  private val rules = ActPowerHarness.rulesWithPrintedRestrictions()
  private val source = DecisionOptionRef.Denizen(PalanquinCard.id)

  /** TravelFixture's board: p1 at the Cradle's plains holds Palanquin and
    * 1 favor, and p2 stands beside p1. p3 stands at the mountain. */
  private def palanquinWithP2Beside(steps: Table => Table = identity)
      : ReadyGame =
    on(board())(table => steps(table.adviser(p1, PalanquinCard.id)
      .favor(p1, 1).pawn(p2, cradlePlains)))

  /** p1 uses Palanquin, aims it at `target` and picks `site`, with every
    * command's events. */
  private def sent(ready: ReadyGame, target: PlayerId, site: SiteId)
      : OathTransition =
    val used = accepted(ActPowerHarness.use(rules, ready, Palanquin.id,
      source))
    val aimed = accepted(ActPowerHarness.answer(rules, used.state,
      Palanquin.targetDecisionId, DecisionOptionRef.Player(target)))
    val done = accepted(ActPowerHarness.answer(rules, aimed.state,
      Palanquin.destinationDecisionId, DecisionOptionRef.Site(site)))
    done.copy(events = used.events ++ aimed.events ++ done.events)

  private def parkedDecide(state: OathState): Decide =
    ParkedNode.of(state, catalog, WalkerPowerCatalog.default(catalog),
      PhasePowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide
      case other => fail(s"expected a parked decision, got $other")

  private def options(decide: Decide): Vector[DecisionOption] =
    decide.query match
      case one: DecisionQuery.ChooseOne => one.options
      case other => fail(s"expected a choose-one, got $other")

  /** Why the Act menu shows Palanquin unavailable, if it does. */
  private def menuReason(ready: ReadyGame): Option[OptionReason] =
    PhaseMenu.offers(PhaseMenu.Menu.Act, catalog, ready, p1,
      PhasePowerCatalog.default(catalog), WalkerPowers.empty)
      .map(_.option).collectFirst {
        case DecisionOption.Unavailable(shown, reason)
            if shown.ref == source => reason
      }

  private def dequeued(events: Vector[OathEvent]): Vector[QueuedProcedure] =
    events.collect { case ProcedureDequeued(entry) => entry }

  test("it offers only the other players at the user's site"):
    val used = accepted(ActPowerHarness.use(rules, palanquinWithP2Beside(),
      Palanquin.id, source))
    val decide = parkedDecide(used.state)
    assertEquals(decide.decisionId, Palanquin.targetDecisionId)
    assertEquals(options(decide).map(_.ref),
      Vector(DecisionOptionRef.Player(p2)))

  test("with no other player at the site, the menu says why"):
    val alone = on(board())(_.adviser(p1, PalanquinCard.id).favor(p1, 1))
    assertEquals(menuReason(alone),
      Some(OptionReason.Written(PawnMoves.noValidEnemyPawn)))

  test("the user is placed there, and the target travels there off turn " +
      "for no Supply"):
    val ready = palanquinWithP2Beside()
    val done = sent(ready, p2, coast)
    val result = Outcomes.ready(done.state)
    val after = Look(result)
    assertEquals(after.pawn(p1), coast)
    assertEquals(after.pawn(p2), coast)
    assertEquals(after.supply(p2), Look(ready).supply(p2))
    assertEquals(after.favor(p1), 0)
    assertEquals(after.active, p1)
    assertEquals(after.phase, Phase.Act)
    assertEquals(dequeued(done.events),
      Vector(Palanquin.entry(p1, p2, coast)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("a destination the target cannot reach is unavailable with why"):
    val aimed = accepted(ActPowerHarness.answer(rules,
      accepted(ActPowerHarness.use(rules, palanquinWithP2Beside(),
        Palanquin.id, source)).state,
      Palanquin.targetDecisionId, DecisionOptionRef.Player(p2)))
    // The Pass's bandits block Dunes from the Cradle's plains.
    assertEquals(options(parkedDecide(aimed.state))
      .find(_.ref == DecisionOptionRef.Site(provincePlains)),
      Some(DecisionOption.Unavailable(
        DecisionOption.Site(DecisionOptionRef.Site(provincePlains)),
        OptionReason.Written("Cannot travel to other sites in the region"))))

  test("a target who can reach no site is unavailable with why"):
    val refusingEvery: QueuedStarts = (_, _) =>
      Some(OathViolation.InvalidEventOrder("refused"))
    assertEquals(Palanquin.targetOptions(palanquinWithP2Beside(), p1,
      refusingEvery), Vector(DecisionOption.Unavailable(
        DecisionOption.Player(DecisionOptionRef.Player(p2)),
        OptionReason.Written(Palanquin.noDestination))))

  test("Brass Army asks the user right after the placement, before the " +
      "target travels"):
    val ready = palanquinWithP2Beside(_.relic(p1,
      CatalogNames.relic("Brass Army")).secrets(p1, faceUp = 1))
    val used = accepted(ActPowerHarness.use(rules, ready, Palanquin.id,
      source))
    val aimed = accepted(ActPowerHarness.answer(rules, used.state,
      Palanquin.targetDecisionId, DecisionOptionRef.Player(p2)))
    val placed = accepted(ActPowerHarness.answer(rules, aimed.state,
      Palanquin.destinationDecisionId, DecisionOptionRef.Site(coast)))
    val decide = parkedDecide(placed.state)
    assertEquals(decide.decisionId, BrassArmyMoves.decisionId)
    assertEquals(decide.owner, p1)
    assertEquals(Look(Outcomes.ready(placed.state)).pawn(p2), cradlePlains)
    val flipped = accepted(ActPowerHarness.answer(rules, placed.state,
      BrassArmyMoves.decisionId, BrassArmyMoves.flip))
    assertEquals(Look(Outcomes.ready(flipped.state)).pawn(p2), coast)

  test("Brass Army asks the target after their own move"):
    val ready = palanquinWithP2Beside(_.relic(p2,
      CatalogNames.relic("Brass Army")).secrets(p2, faceUp = 1))
    val decide = parkedDecide(sent(ready, p2, coast).state)
    assertEquals(decide.decisionId, BrassArmyMoves.decisionId)
    assertEquals(decide.owner, p2)

  test("Hospitality pays the target"):
    val ready = palanquinWithP2Beside(_
      .adviser(p2, CatalogNames.denizen("Hospitality"))
      .adviser(p2, CatalogNames.denizen("Messenger"))
      .denizen(CatalogNames.denizen("Siege Engines"), coast)
      .banksHoldingOnly(Suit.Order -> 2))
    val after = Look(Outcomes.ready(sent(ready, p2, coast).state))
    assertEquals(after.favor(p2), Look(ready).favor(p2) + 1)

  test("Tyrant acts for the target off turn"):
    val ready = palanquinWithP2Beside(_
      .adviser(p2, CatalogNames.denizen("Tyrant")).bandits(coast, 2))
    assertEquals(Look(Outcomes.ready(sent(ready, p2, coast).state))
      .forces(coast), SiteForces.Occupied(ForceKind.Bandit, 1))

  test("the target picks their own Travel modifiers, and the resumed walk " +
      "still goes to the named site"):
    val ready = palanquinWithP2Beside(_
      .adviser(p2, CatalogNames.denizen("Special Envoy")))
    val parked = sent(ready, p2, coast).state
    val decide = parkedDecide(parked)
    assertEquals(decide.decisionId, TravelFixture.modifierDecisionId)
    assertEquals(decide.owner, p2)
    val started = accepted(rules.resolveWalker(parked, p2,
      TravelFixture.modifierDecisionId,
      DecisionAnswer.ChooseOneAnswer(ModifierPicks.start)))
    assertEquals(Look(Outcomes.ready(started.state)).pawn(p2), coast)
