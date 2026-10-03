package oathdigital.testkit

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  PowerCtx, Restriction, Transform}
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
