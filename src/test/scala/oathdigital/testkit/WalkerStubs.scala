package oathdigital.testkit

import oathdigital.model._

/** The walker suites' synthetic tree parts: the buttons of a hand-built
  * decision and a windowed composite. Their stub powers are in
  * `PowerStubs`.
  */
object WalkerStubs:
  val continueOption: DecisionOptionRef.Button =
    DecisionOptionRef.Button("continue")
  val stopOption: DecisionOptionRef.Button = DecisionOptionRef.Button("stop")

  /** Stub of Operation: a test-only composite that hooks a `PowerWindow` on
    * an arbitrary children vector. `Operation` is deliberately unsealed
    * (unlike `CoreOperation`/`PrimitiveOperation`, which Scala 2.13 pins to
    * `CoreOperations.scala`) precisely so a windowed node can be authored
    * outside that file, so a suite can prove the walker's generic wiring
    * with a tree it builds itself.
    */
  final case class WindowedNode(hook: PowerWindow,
      override val children: Vector[Operation]) extends Operation:
    override def window: Option[PowerWindow] = Some(hook)
