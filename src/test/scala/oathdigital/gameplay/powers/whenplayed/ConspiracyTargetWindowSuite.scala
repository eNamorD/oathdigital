package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  Transform}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, as, present}

/** The window on Conspiracy's target decision: a power can remove targets, and
  * when it removes every one the decision is not asked and nothing is taken.
  */
class ConspiracyTargetWindowSuite extends munit.FunSuite:
  private val conspiracy = VisionRules.Conspiracy
  private val relics = Vector(CatalogNames.relic("Cracked Horn"),
    CatalogNames.relic("Bandit Crown"))

  /** p1 holds Conspiracy in a temporary hand. p1 and p2 stand at Ancient
    * City, and p2 holds Cracked Horn and Bandit Crown faceup.
    */
  private val staged: ReadyGame = Table.start
    .pawn(p1, "Ancient City").pawn(p2, "Ancient City")
    .relic(p2, "Cracked Horn").relic(p2, "Bandit Crown")
    .hand(p1, conspiracy).ready

  /** Stub of ContributingPower: at the Conspiracy target window it keeps only
    * the options that pass `keep`. */
  private def removing(keep: DecisionOptionRef => Boolean): ContributingPower =
    new ContributingPower:
      def id: PowerId = PowerId("test.conspiracy-window")
      def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
        PowerWindow.ConspiracyTargetSelection -> Vector(Transform((_, ops) =>
          ops.flatMap {
            case decide: Decide => decide.query match {
              case one: DecisionQuery.ChooseOne =>
                val options = one.options.filter(o => keep(o.ref))
                if options.isEmpty then Vector.empty
                else Vector(decide.copy(query = one.copy(options = options)))
              case _ => Vector(decide)
            }
            case other => Vector(other)
          })))

  private def hook: CardPlayedFaceup =
    CardPlayedFaceup(conspiracy, RuleSourceRef.Adviser(p1, conspiracy))

  /** Conspiracy's When Played power, with `power` beside it. */
  private def powers(power: Option[ContributingPower]): WalkerPowers =
    WalkerPowers(ConspiracyWhenPlayed +: power.toVector)

  private def walk(power: Option[ContributingPower])
      : Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.advance(staged, hook, None, powers(power))

  /** The target decision the walk parks on, with `power` beside it. */
  private def targetDecision(power: Option[ContributingPower]) =
    val parked = Outcomes.parked(walk(power))
    present(ProcedureWalker.parkedDecide(staged, hook, parked.tree,
      powers(power)), "the parked target decision")

  test("the target decision carries the Conspiracy target window"):
    val decide = targetDecision(None)
    assertEquals(decide.window, Some(PowerWindow.ConspiracyTargetSelection))
    val options = as[DecisionQuery.ChooseOne](decide.query).options.map(_.ref)
    assertEquals(options, Vector[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(p2, 0),
      DecisionOptionRef.RelicSlot(p2, 1)))

  test("a power at the window removes the targets it forbids"):
    val only = DecisionOptionRef.RelicSlot(p2, 1)
    val decide = targetDecision(Some(removing(_ == only)))
    val options = as[DecisionQuery.ChooseOne](decide.query).options.map(_.ref)
    assertEquals(options, Vector[DecisionOptionRef](only))

  test("when every target is removed nothing is asked and nothing is taken, " +
      "and the card still leaves the game"):
    val done = Outcomes.finished(walk(Some(removing(_ => false)))).treeless
    assertEquals(Look(done).player(p2).relics.map(_.id), relics)
    assertEquals(Look(done).player(p1).relics,
      Look(staged).player(p1).relics)
    assertEquals(done.game.current.temporaryHands(p1), Vector.empty)
    assert(!accepted(CardIndex.from(done.game)).ids.contains(conspiracy))
