package oathdigital.testkit

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  PowerCtx}
import oathdigital.model._

/** Stubs of the production power traits, shared by the walker suites. */
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
