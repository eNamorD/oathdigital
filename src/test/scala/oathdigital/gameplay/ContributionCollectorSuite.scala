package oathdigital.gameplay

import oathdigital.gameplay.powerresolver._
import oathdigital.model.PowerId
import oathdigital.model.{PowerWindow, RuleSourceRef, Sequence}
import oathdigital.testkit.Table

/** Task 2: the gather protocol as a pure collector, with hand-built fixture
  * powers -- no catalog, no walker. Ignores are ContributionIgnoresSuite's.
  */
class ContributionCollectorSuite extends munit.FunSuite:

  private val ready = Table.start.ready

  private def ctxFor(power: ContributingPower, window: PowerWindow): PowerCtx =
    PowerCtx(
      state = ready,
      activePlayer = ready.game.current.turn.activePlayer,
      source = power.source,
      window = window,
      nodePath = Vector("root"),
      operation = Sequence(Vector.empty, Some(window))
    )

  /** Stub of ContributingPower: declares `contribs` at every one of
    * `windows` (the same vector at each, sufficient for these tests), and is
    * applicable as `applicableFlag` says, which controls step 2.
    */
  private def fixturePower(
      idValue: String,
      sourceKey: String,
      windows: Set[PowerWindow],
      contribs: Vector[Contribution],
      priorityValue: Int = 0,
      applicableFlag: Boolean = true
  ): ContributingPower =
    new ContributingPower:
      def id: PowerId = PowerId(idValue)
      def source: RuleSourceRef = RuleSourceRef.GameRule(sourceKey)
      override def priority: Int = priorityValue
      def contributions: Map[PowerWindow, Vector[Contribution]] =
        windows.map(_ -> contribs).toMap
      override def applicable(ctx: PowerCtx): Boolean = applicableFlag

  private val window = PowerWindow.RecoverEligibility
  private val otherWindow = PowerWindow.RecoverBeforeFirstRoll

  test("a power that does not declare the window is not gathered"):
    val declaresElsewhere = fixturePower(
      "power.elsewhere", "a", Set(otherWindow),
      Vector(Transform((_, ops) => ops)))

    val gathered = ContributionCollector.gather(
      window, PowerWindowIndex.of(Vector(declaresElsewhere)), ctxFor(_, window))

    assertEquals(gathered.order, Vector.empty[PowerId])
    assertEquals(gathered.transforms, Vector.empty)

  test("a power whose applicable returns false is not gathered"):
    val inapplicable = fixturePower(
      "power.inapplicable", "a", Set(window),
      Vector(Transform((_, ops) => ops)),
      applicableFlag = false)

    val gathered = ContributionCollector.gather(
      window, PowerWindowIndex.of(Vector(inapplicable)), ctxFor(_, window))

    assertEquals(gathered.order, Vector.empty[PowerId])
    assertEquals(gathered.transforms, Vector.empty)

  test("two powers with interleaved sort keys produce transforms in sortKey order"):
    val transformBravoA = Transform((_, ops) => ops)
    val transformAlphaZ = Transform((_, ops) => ops)
    // "power.a" carries the larger stableKey ("bravo"); "power.z" carries
    // the smaller one ("alpha"). A naive id-only sort would put power.a
    // first; the spec's (priority, stableKey, id) key must put power.z
    // first instead.
    val bravoA = fixturePower("power.a", "bravo", Set(window), Vector(transformBravoA))
    val alphaZ = fixturePower("power.z", "alpha", Set(window), Vector(transformAlphaZ))

    val gathered = ContributionCollector.gather(
      window, PowerWindowIndex.of(Vector(bravoA, alphaZ)), ctxFor(_, window))

    assertEquals(gathered.order, Vector(PowerId("power.z"), PowerId("power.a")))
    assertEquals(gathered.transforms, Vector(
      PowerId("power.z") -> transformAlphaZ,
      PowerId("power.a") -> transformBravoA
    ))

  test("a power declaring both a Transform and a Restriction lands one in each output, id once"):
    val transform = Transform((_, ops) => ops)
    val restriction = Restriction((_, _) => None)
    val power = fixturePower(
      "power.both", "src", Set(window), Vector(transform, restriction))

    val gathered = ContributionCollector.gather(
      window, PowerWindowIndex.of(Vector(power)), ctxFor(_, window))

    assertEquals(gathered.transforms, Vector(PowerId("power.both") -> transform))
    assertEquals(gathered.restrictions, Vector(PowerId("power.both") -> restriction))
    assertEquals(gathered.order, Vector(PowerId("power.both")))

  test("a Late transform folds after a Normal one of higher priority, and " +
      "priority orders within a phase"):
    val powers = Vector(
      fixturePower("power.late-high", "a", Set(window),
        Vector(Transform((_, ops) => ops, FoldPhase.Late)),
        priorityValue = 5),
      fixturePower("power.late-low", "b", Set(window),
        Vector(Transform((_, ops) => ops, FoldPhase.Late))),
      fixturePower("power.normal-high", "c", Set(window),
        Vector(Transform((_, ops) => ops)), priorityValue = 9))

    val gathered = ContributionCollector.gather(
      window, PowerWindowIndex.of(powers), ctxFor(_, window))

    val folded = Vector("power.normal-high", "power.late-low",
      "power.late-high").map(PowerId(_))
    assertEquals(gathered.transforms.map(_._1), folded)
    assertEquals(gathered.order, folded)

  test("a refused power keeps only its offers and rerolls"):
    val offer = Offer(_ => None)
    val reroll = OfferReroll(_ => None)
    val refusedPower = fixturePower("power.refused", "a", Set(window),
      Vector(Transform((_, ops) => ops), offer, reroll))
    val gathered = ContributionCollector.gather(window,
      PowerWindowIndex.of(Vector(refusedPower)), ctxFor(_, window),
      refused = _.id == refusedPower.id)
    assertEquals(gathered.transforms, Vector.empty)
    assertEquals(gathered.offers, Vector(refusedPower.id -> offer))
    assertEquals(gathered.rerolls, Vector(refusedPower.id -> reroll))
    assertEquals(gathered.order, Vector(refusedPower.id))

  test("a refused power with nothing to offer is not gathered"):
    val refusedPower = fixturePower("power.refused", "a", Set(window),
      Vector(Transform((_, ops) => ops)))
    val gathered = ContributionCollector.gather(window,
      PowerWindowIndex.of(Vector(refusedPower)), ctxFor(_, window),
      refused = _ => true)
    assertEquals(gathered.order, Vector.empty[PowerId])
    assertEquals(gathered.transforms, Vector.empty)
