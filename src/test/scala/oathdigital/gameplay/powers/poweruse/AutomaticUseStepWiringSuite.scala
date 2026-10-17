package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerUse, PowerUseRefusal, PowerUseRestriction, PowerUseStep}
import oathdigital.gameplay.powers.economy.{VowOfBeastkin,
  VowOfWanderingMuster}
import oathdigital.gameplay.powers.travel.{Hospitality, Tyrant}
import oathdigital.gameplay.powers.whenplayed.{ConspiracyWhenPlayed,
  ThreateningRoar, ThreateningRoarCard}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedUses, PowerNoted, ProcedureWalker,
  WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{AppendedAutomaticUses, CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Every wrapped automatic power waits at its own step when only a
  * waivable refusal stops it, and stays deactivated by a refusal
  * remembered this turn (automatic power consent design, slice 2). */
class AutomaticUseStepWiringSuite extends munit.FunSuite:
  private val refusingCard = CatalogNames.denizen("Wrestlers")
  private val refusingSource = PowerSourceRef.Card(refusingCard)

  /** The key p3's answer on `use` is remembered under. */
  private def keyOf(use: AutomaticUse): ConsentKey = ConsentKey(
    RefusesEveryUseUnlessP3Consents.id, use.user, PowerUseRules.useKey,
    PowerUseRules.target(PowerUse.automatic(use.user, use.power, use.card)))

  /** A test power: refuses every automatic use, and p3 may waive it. */
  private object RefusesEveryUseUnlessP3Consents extends ContributingPower
      with PowerUseRestriction:
    val id: PowerId = PowerId("test.refuses-every-use-waivably")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def powerUseRestrictions: Vector[PowerUseRestriction] =
      Vector(this)
    private val note = PowerNote(Some(refusingSource), "refused",
      Vector.empty)
    def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal] =
      Some(PowerUseRefusal(id, note, OperationReason("test-refused",
        "every use", OperationReasonKind.Impossible, Some(OperationWaiver(
          id, Some(p3), ConsentKey(id, use.user, PowerUseRules.useKey,
            PowerUseRules.target(use)), note, note)))))

  private val refusing = WalkerPowers(Vector(RefusesEveryUseUnlessP3Consents))

  /** p2's pawn moving from Ancient City to Deep Woods in a travel's cost
    * node, as `HospitalitySuite` stages it. */
  private val p2Travels: Operation = Sequence(Vector[Operation](Move(
    Piece.Pawn(p2), PositionedLocation(Location.Site(
      CatalogNames.site("Ancient City"))), PositionedLocation(Location.Site(
      CatalogNames.site("Deep Woods"))))), Some(PowerWindow.TravelCost))

  /** A Muster's gain node: one warband for p1. */
  private val p1MusterGain: Operation = Sequence(Vector[Operation](
    Gain.Warbands(p1, ForceKind.Bandit, 1)), Some(PowerWindow.MusterGain))

  private val wrapped: Vector[(String, ContributingPower, PowerWindow,
      Operation)] = Vector(
    ("Threatening Roar", ThreateningRoar.forCatalog(catalog),
      PowerWindow.ActionCardPlayedFaceup, CardPlayedFaceup(
        ThreateningRoarCard.id, RuleSourceRef.GameRule(
          ThreateningRoar.id.value))),
    ("Conspiracy", ConspiracyWhenPlayed, PowerWindow.ActionCardPlayedFaceup,
      CardPlayedFaceup(VisionRules.Conspiracy, RuleSourceRef.GameRule(
        ConspiracyWhenPlayed.id.value))),
    ("Tyrant", Tyrant, PowerWindow.TravelCost, p2Travels),
    ("Hospitality", Hospitality.forCatalog(catalog), PowerWindow.TravelCost,
      p2Travels),
    ("Vow of Beastkin", VowOfBeastkin.forCatalog(catalog),
      PowerWindow.MusterGain, p1MusterGain),
    ("Vow of Wandering", VowOfWanderingMuster, PowerWindow.MusterGain,
      p1MusterGain))

  wrapped.foreach { (name, power, window, operation) =>
    test(s"$name's use waits at its own step, owned by its user"):
      val use = present(AppendedAutomaticUses.nodes(power, window,
        operation).headOption, s"$name's node")
      val tree = Sequence(Vector[Operation](use))
      val ready = Table.start.ready
      val pending = accepted(ProcedureWalker.advance(ready, tree, None,
        refusing)) match
        case WalkerOutcome.Parked(at, _) => at
        case other => fail(s"the walk did not park: $other")
      val step = present(ProcedureWalker.parkedDecide(ready, tree, pending,
        refusing), "the step")
      assertEquals((step.decisionId, step.owner),
        (PowerUseStep.decisionId(use.power), use.user))
      assertEquals(ParkedUses.of(ready, tree, pending, refusing)
        .map(found => (found.user, found.power, found.card)),
        Some((use.user, use.power, use.card)))

    test(s"$name stays deactivated by a refusal remembered this turn"):
      val use = present(AppendedAutomaticUses.nodes(power, window,
        operation).headOption, s"$name's node")
      val start = Table.start.ready
      val refused = start.copy(consent = ConsentState(answers = Map(
        keyOf(use) -> ConsentAnswer.Refused.by(p3)),
        turn = Some(ConsentTurn.of(start))))
      accepted(ProcedureWalker.advance(refused,
          Sequence(Vector[Operation](use)), None, refusing)) match
        case WalkerOutcome.Finished(_, events) =>
          assertEquals(events.collect { case PowerNoted(_, note, _) =>
            note }, Vector(PowerUseStep.staysDeactivated(refusingSource,
              NoteArg.Card(use.card))))
        case other => fail(s"the walk did not finish: $other")
  }
