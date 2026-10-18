package oathdigital.testkit

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  OptionRestriction, PowerCtx, Restriction, Transform}
import oathdigital.model._

/** Stubs of ContributingPower, shared by the walker suites. */
object PowerStubs:
  /** Stub of ContributingPower: contributes `contributions` and nothing
    * else, for the cases that need one power speaking at two windows. */
  final case class ContributingStub(id: PowerId,
      contributions: Map[PowerWindow, Vector[Contribution]])
      extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  /** Stub of ContributingPower: player-selected, applicable at exactly the
    * window `at` and nowhere else, so which window a call site consulted is
    * observable from whether the power is offered. */
  final case class WindowScopedStub(id: PowerId, at: PowerWindow)
      extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def resolution: PowerResolution = PowerResolution.PlayerSelected
    override def applicable(ctx: PowerCtx): Boolean = ctx.window == at

  /** Stub of ContributingPower contributing exactly one `Transform` at
    * one window -- enough to prove the walker's gather/fold wiring without
    * pulling in the real power catalog.
    */
  final case class TransformStub(id: PowerId, hook: PowerWindow,
      fn: (PowerCtx, Vector[Operation]) => Vector[Operation],
      override val resolution: PowerResolution = PowerResolution.Automatic)
      extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(hook -> Vector(Transform(fn)))

  /** Stub of ContributingPower contributing exactly one `Restriction` at
    * one window.
    */
  final case class RestrictionStub(id: PowerId, hook: PowerWindow,
      fn: (PowerCtx, Operation) => Option[OathViolation])
      extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(hook -> Vector(Restriction(fn)))

  /** Stub of ContributingPower registering exactly one operation
    * restriction. */
  final case class OperationRestrictionStub(id: PowerId,
      restriction: OperationRestriction) extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def operationRestrictions: Vector[OperationRestriction] =
      Vector(restriction)

  /** The source of every note the hiding and forbidding stubs write. */
  val hideSource: PowerSourceRef = PowerSourceRef.Site(SiteId("test-site"))

  /** The violation the hiding and forbidding stubs refuse with. */
  val hiddenByTest: OathViolation =
    OathViolation.InvalidEventOrder("hidden by test")

  /** The note a hiding stub writes for each option it hides. */
  def hidNote(ref: DecisionOptionRef): LogNote =
    LogNote(Some(hideSource), "hid",
    ref match
      case DecisionOptionRef.Site(site) => Vector(NoteArg.Site(site))
      case _ => Vector.empty)

  /** Stub of ContributingPower: hides `forbidden` at `hook`, and notes each
    * one it hid when `says`. */
  final case class OptionHidingStub(id: PowerId, hook: PowerWindow,
      forbidden: Set[DecisionOptionRef], says: Boolean = true)
      extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(hook -> Vector(OptionRestriction(
        (_, ref) => Option.when(forbidden(ref))(hiddenByTest),
        (_, ref) => Option.when(says)(hidNote(ref)))))

  /** Stub of ContributingPower: rejects any action whose tree opens `hook`,
    * and says so. */
  final case class ActionForbiddingStub(id: PowerId, hook: PowerWindow)
      extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(hook -> Vector(Restriction((_, _) => Some(hiddenByTest),
        (_, _) => Some(LogNote(Some(hideSource), "forbade", Vector.empty)))))
