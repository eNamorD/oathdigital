package oathdigital.gameplay

import oathdigital.gameplay.powerresolver._
import oathdigital.model.PowerId
import oathdigital.model.{PowerWindow, RuleSourceRef, Sequence}
import oathdigital.testkit.Table

/** Task 2: the gather protocol as a pure collector. Exercises each of the
  * five decision-10 steps in isolation, with hand-built fixture powers --
  * no catalog, no walker.
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

  /** Builds a fixture power. `ignore` names ids this power votes to ignore;
    * `applicableFlag` controls step 2; `windows` is the set of windows this
    * power declares contributions at (all mapping to the same
    * `contributions` vector, sufficient for these tests).
    */
  private def fixturePower(
      idValue: String,
      sourceKey: String,
      windows: Set[PowerWindow],
      contribs: Vector[Contribution],
      priorityValue: Int = 0,
      applicableFlag: Boolean = true,
      ignore: Set[String] = Set.empty
  ): ContributingPower =
    new ContributingPower:
      def id: PowerId = PowerId(idValue)
      def source: RuleSourceRef = RuleSourceRef.GameRule(sourceKey)
      override def priority: Int = priorityValue
      def contributions: Map[PowerWindow, Vector[Contribution]] =
        windows.map(_ -> contribs).toMap
      override def applicable(ctx: PowerCtx): Boolean = applicableFlag
      override def shouldIgnore(other: ContributingPower): Boolean =
        ignore(other.id.value)

  private val window = PowerWindow.RecoverEligibility
  private val otherWindow = PowerWindow.RecoverBeforeFirstRoll

  test("a power that does not declare the window is not gathered"):
    val declaresElsewhere = fixturePower(
      "power.elsewhere", "a", Set(otherWindow),
      Vector(Transform((_, ops) => ops)))

    val gathered = ContributionCollector.gather(
      window, Vector(declaresElsewhere), ctxFor(_, window))

    assertEquals(gathered.order, Vector.empty[PowerId])
    assertEquals(gathered.transforms, Vector.empty)

  test("a power whose applicable returns false is not gathered"):
    val inapplicable = fixturePower(
      "power.inapplicable", "a", Set(window),
      Vector(Transform((_, ops) => ops)),
      applicableFlag = false)

    val gathered = ContributionCollector.gather(
      window, Vector(inapplicable), ctxFor(_, window))

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
      window, Vector(bravoA, alphaZ), ctxFor(_, window))

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
      window, Vector(power), ctxFor(_, window))

    assertEquals(gathered.transforms, Vector(PowerId("power.both") -> transform))
    assertEquals(gathered.restrictions, Vector(PowerId("power.both") -> restriction))
    assertEquals(gathered.order, Vector(PowerId("power.both")))
