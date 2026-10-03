package oathdigital.testkit

import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Restriction, Transform}
import oathdigital.model._

/** The walker suites' synthetic tree parts: the buttons of a hand-built
  * decision, a windowed composite, stubs of ContributingPower that speak at
  * one window, and one that registers an operation restriction.
  */
object WalkerStubs:
  val continueOption: DecisionOptionRef.Button =
    DecisionOptionRef.Button("continue")
  val stopOption: DecisionOptionRef.Button = DecisionOptionRef.Button("stop")

  /** Test-only composite that hooks a `PowerWindow` on an arbitrary children
    * vector. `Operation` is deliberately unsealed (unlike `CoreOperation`/
    * `PrimitiveOperation`, which Scala 2.13 pins to `CoreOperations.scala`)
    * precisely so a windowed node can be authored outside that file, so a
    * suite can prove the walker's generic wiring with a tree it builds
    * itself.
    */
  final case class WindowedNode(hook: PowerWindow,
      override val children: Vector[Operation]) extends Operation:
    override def window: Option[PowerWindow] = Some(hook)

  /** A minimal `ContributingPower` contributing exactly one `Transform` at
    * one window -- enough to prove the walker's gather/fold wiring without
    * pulling in the real power catalog.
    */
  final case class TestTransformPower(id: PowerId, hook: PowerWindow,
      fn: (PowerCtx, Vector[Operation]) => Vector[Operation],
      override val resolution: PowerResolution = PowerResolution.Automatic)
      extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(hook -> Vector(Transform(fn)))

  /** A minimal `ContributingPower` contributing exactly one `Restriction` at
    * one window.
    */
  final case class TestRestrictionPower(id: PowerId, hook: PowerWindow,
      fn: (PowerCtx, Operation) => Option[OathViolation])
      extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(hook -> Vector(Restriction(fn)))

  /** A `ContributingPower` registering exactly one operation restriction. */
  final case class TestOperationRestrictionPower(id: PowerId,
      restriction: OperationRestriction) extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def operationRestrictions: Vector[OperationRestriction] =
      Vector(restriction)
