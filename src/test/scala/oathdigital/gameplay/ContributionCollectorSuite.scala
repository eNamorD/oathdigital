package oathdigital.gameplay

import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.setup.{FirstGameSetupFixture, FirstGameSetupRules}
import oathdigital.model.PowerId

/** Task 2: the gather protocol as a pure collector. Exercises each of the
  * five decision-10 steps in isolation, with hand-built fixture powers --
  * no catalog, no walker.
  */
class ContributionCollectorSuite extends munit.FunSuite {

  private val ready = FirstGameSetupFixture
    .execute(new FirstGameSetupRules(FirstGameSetupFixture.catalog))._1 match {
    case OathState.Ready(state) => state
    case other => fail(s"expected Ready state, got $other")
  }

  private def ctxFor(power: ContributingPower, window: PowerWindow): PowerCtx =
    PowerCtx(
      state = ready,
      actor = ready.game.current.turn.activePlayer,
      source = power.source,
      window = window,
      nodePath = Vector("root")
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
      contribs: Vector[Contribution] = Vector.empty,
      priorityValue: Int = 0,
      applicableFlag: Boolean = true,
      ignore: Set[String] = Set.empty
  ): ContributingPower =
    new ContributingPower {
      def id: PowerId = PowerId(idValue)
      def source: RuleSourceRef = RuleSourceRef.GameRule(sourceKey)
      override def priority: Int = priorityValue
      def contributions: Map[PowerWindow, Vector[Contribution]] =
        windows.map(_ -> contribs).toMap
      override def applicable(ctx: PowerCtx): Boolean = applicableFlag
      override def shouldIgnore(other: ContributingPower): Boolean =
        ignore(other.id.value)
    }

  private val window = PowerWindow.RecoverEligibility
  private val otherWindow = PowerWindow.RecoverBeforeFirstRoll

  /** A power classifying its target by the major action its windows belong to
    * rather than by name — the shape `shouldIgnore(PowerId)` could not express,
    * since a `PowerId` carries no classification.
    */
  private def ignoresTravelModifiers(
      idValue: String, sourceKey: String,
      contribs: Vector[Contribution]): ContributingPower =
    new ContributingPower {
      def id: PowerId = PowerId(idValue)
      def source: RuleSourceRef = RuleSourceRef.GameRule(sourceKey)
      def contributions: Map[PowerWindow, Vector[Contribution]] =
        Map(window -> contribs)
      override def shouldIgnore(other: ContributingPower): Boolean =
        other.contributions.keys.flatMap(_.associatedMajorAction)
          .exists(_ == MajorActionType.Travel)
    }

  test("a power may ignore candidates by classification, not only by name") {
    val ignorerTransform = Transform((_, ops) => ops)
    val travelTransform = Transform((_, ops) => ops)
    val plainTransform = Transform((_, ops) => ops)
    // Declares the gathered window plus a Travel window, so it classifies as
    // a Travel modifier while still being a candidate here.
    val travelModifier = fixturePower(
      "power.travel", "src-travel", Set(window, PowerWindow.TravelCost),
      Vector(travelTransform))
    val plain = fixturePower(
      "power.plain", "src-plain", Set(window), Vector(plainTransform))
    val ignorer = ignoresTravelModifiers(
      "power.ignorer", "src-ignorer", Vector(ignorerTransform))

    val gathered = ContributionCollector.gather(
      window, Vector(ignorer, travelModifier, plain), ctxFor(_, window))

    assertEquals(gathered.order,
      Vector(PowerId("power.ignorer"), PowerId("power.plain")))
    assertEquals(gathered.transforms, Vector(
      PowerId("power.ignorer") -> ignorerTransform,
      PowerId("power.plain") -> plainTransform))
  }

  test("a power that does not declare the window is not gathered") {
    val declaresElsewhere = fixturePower(
      "power.elsewhere", "a", Set(otherWindow),
      Vector(Transform((_, ops) => ops)))

    val gathered = ContributionCollector.gather(
      window, Vector(declaresElsewhere), ctxFor(_, window))

    assertEquals(gathered.order, Vector.empty[PowerId])
    assertEquals(gathered.transforms, Vector.empty)
  }

  test("a power whose applicable returns false is not gathered") {
    val inapplicable = fixturePower(
      "power.inapplicable", "a", Set(window),
      Vector(Transform((_, ops) => ops)),
      applicableFlag = false)

    val gathered = ContributionCollector.gather(
      window, Vector(inapplicable), ctxFor(_, window))

    assertEquals(gathered.order, Vector.empty[PowerId])
    assertEquals(gathered.transforms, Vector.empty)
  }

  test("A ignores B: B's transform is absent, A's is present") {
    val transformA = Transform((_, ops) => ops)
    val transformB = Transform((_, ops) => ops)
    val powerA = fixturePower(
      "power.a", "src-a", Set(window), Vector(transformA), ignore = Set("power.b"))
    val powerB = fixturePower(
      "power.b", "src-b", Set(window), Vector(transformB))

    val gathered = ContributionCollector.gather(
      window, Vector(powerA, powerB), ctxFor(_, window))

    assertEquals(gathered.order, Vector(PowerId("power.a")))
    assertEquals(gathered.transforms, Vector(PowerId("power.a") -> transformA))
  }

  test("A ignores B and B ignores C: B and C are both dropped (no transitivity)") {
    val transformA = Transform((_, ops) => ops)
    val transformB = Transform((_, ops) => ops)
    val transformC = Transform((_, ops) => ops)
    val powerA = fixturePower(
      "power.a", "src-a", Set(window), Vector(transformA), ignore = Set("power.b"))
    val powerB = fixturePower(
      "power.b", "src-b", Set(window), Vector(transformB), ignore = Set("power.c"))
    val powerC = fixturePower(
      "power.c", "src-c", Set(window), Vector(transformC))

    val gathered = ContributionCollector.gather(
      window, Vector(powerA, powerB, powerC), ctxFor(_, window))

    // B is dropped by A's vote, but B's own vote against C was collected
    // in the same pass before B was dropped -- so C is also dropped. A
    // survives untouched. A fixpoint implementation would instead
    // re-evaluate after dropping B, find no applicable power left voting
    // against C, and incorrectly keep C.
    assertEquals(gathered.order, Vector(PowerId("power.a")))
    assertEquals(gathered.transforms, Vector(PowerId("power.a") -> transformA))
  }

  test("two powers with interleaved sort keys produce transforms in sortKey order") {
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
  }

  test("a power declaring both a Transform and a Restriction lands one in each output, id once") {
    val transform = Transform((_, ops) => ops)
    val restriction = Restriction((_, _) => None)
    val power = fixturePower(
      "power.both", "src", Set(window), Vector(transform, restriction))

    val gathered = ContributionCollector.gather(
      window, Vector(power), ctxFor(_, window))

    assertEquals(gathered.transforms, Vector(PowerId("power.both") -> transform))
    assertEquals(gathered.restrictions, Vector(PowerId("power.both") -> restriction))
    assertEquals(gathered.order, Vector(PowerId("power.both")))
  }
}
