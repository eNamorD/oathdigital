package oathdigital.gameplay.powers.recover

import oathdigital.gameplay.{ModifierPlay, OathRules}
import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.actions.dice.RerollChoice
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.walker.{LogNoted, WalkerDice,
  WalkerProcedureRegistry, WalkerRollFeedback, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseManyAnswer, ChooseOneAnswer}
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Outcomes, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

/** Silver Charm (relic R31): each time its user pays Supply for a Recover
  * roll, after the first, they may reroll any earlier Recover dice with the
  * two new ones (catalog batch 7, slice 2). */
class SilverCharmSuite extends munit.FunSuite:
  import DefenseDieFace.{Blank, OneShield, TwoShields}

  private val pool = RecoverProcedure.recoverPool
  private val continueRecovering: DecisionAnswer =
    ChooseOneAnswer(DecisionOptionRef.Button("continue"))

  private def rulesRolling(dice: WalkerDice): OathRules = new OathRules(
    catalog, walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = dice)

  /** p1's Act at Fair Isle, whose Recover difficulty is 3, with Dowsing
    * Sticks facedown there, holding Silver Charm and 4 Supply. */
  private def silverCharmAtFairIsle: Table = Table.start
    .pawn(p1, "Fair Isle").relicAt("Dowsing Sticks", "Fair Isle")
    .relic(p1, "Silver Charm").supply(p1, 4)

  /** p1's Recover from `table` up to its first park, with Silver Charm
    * picked unless `picked` is false. */
  private def recoverStarted(rules: OathRules, table: Table,
      picked: Boolean = true): OathTransition =
    accepted(ModifierPlay.started(rules, Ready(table.ready),
      ActionRef.Recover, p1, PowerWindow.RecoverModifierSelection,
      if picked then Vector(SilverCharm.id) else Vector.empty))

  private def resolved(rules: OathRules, from: OathTransition,
      decisionId: String, answer: DecisionAnswer): OathTransition =
    accepted(rules.resolveWalker(from.state, p1, decisionId, answer))

  /** Silver Charm's answer that rerolls the earlier dice at `indices`. */
  private def rerollingDice(indices: Int*): DecisionAnswer =
    ChooseManyAnswer(indices.toVector.map(SilverCharm.die))

  private def recoverOutcome(at: OathTransition): RollOutcome =
    Outcomes.ready(at.state).game.current.rollOutcomes(pool)

  private def parkedDecisionId(at: OathTransition): String =
    EconomyActions.parkedDecision(at.state).decisionId

  /** What each of Silver Charm's notes names, and whether it covers. */
  private def silverCharmNotes(events: Vector[OathEvent])
      : Vector[(Vector[NoteArg], Boolean)] = events.collect {
    case LogNoted(SilverCharm.id, note, covers) => (note.args, covers)
  }

  test("its text reads [defense-die], where the card misprints " +
      "[attack-die]"):
    assert(SilverCharmCard.power.text.contains("[defense-die]"))
    assert(!SilverCharmCard.power.text.contains("[attack-die]"))

  test("the first payment has no earlier dice, so nothing is asked"):
    val rules = rulesRolling(scripted(Vector(Blank, Blank)))
    val started = recoverStarted(rules, silverCharmAtFairIsle)
    assertEquals(parkedDecisionId(started),
      RecoverProcedure.choiceDecisionId)

  test("a later payment asks which earlier dice to reroll, one option per " +
      "die"):
    val rules = rulesRolling(scripted(Vector(OneShield, Blank)))
    val started = recoverStarted(rules, silverCharmAtFairIsle)
    val asked = resolved(rules, started, RecoverProcedure.choiceDecisionId,
      continueRecovering)
    val decide = EconomyActions.parkedDecision(asked.state)
    assertEquals(decide.decisionId, SilverCharm.decisionId)
    assertEquals(decide.query, DecisionQuery.ChooseMany(0, 2, Vector(
      DecisionOption.Button(SilverCharm.die(0), "Die 1: shield"),
      DecisionOption.Button(SilverCharm.die(1), "Die 2: blank")),
      heading = Some("Reroll earlier dice with this roll"),
      summary = Some(DecisionSummary("Reroll", "die", "dice")),
      confirm = Some(CountedConfirm("Reroll", "die", "dice",
        Some("Keep the roll")))))
    // Both payments are made, and the second roll waits for the answer.
    assertEquals(Look(Outcomes.ready(asked.state)).player(p1).board.supply
      .supply, 4 - 2)
    assertEquals(recoverOutcome(asked).faces.size, 2)

  test("its question shows the Recover dice and the difficulty"):
    val rules = rulesRolling(scripted(Vector(OneShield, Blank)))
    val asked = resolved(rules, recoverStarted(rules, silverCharmAtFairIsle),
      RecoverProcedure.choiceDecisionId, continueRecovering)
    assertEquals(WalkerProcedureRegistry.rollFeedback(ActionRef.Recover,
      catalog, Outcomes.ready(asked.state), p1, SilverCharm.decisionId),
      Some(WalkerRollFeedback(pool, target = Some(3))))
    // The modifier decision still shows none.
    assertEquals(WalkerProcedureRegistry.rollFeedback(ActionRef.Recover,
      catalog, silverCharmAtFairIsle.ready, p1,
      ModifierPicks.decisionId(PowerWindow.RecoverModifierSelection)), None)

  test("the chosen earlier dice are rerolled with the two new dice, and " +
      "its line names them as they showed"):
    val ready = silverCharmAtFairIsle.ready
    val rules = rulesRolling(scripted(Vector(OneShield, Blank),
      Vector(Blank, Blank), Vector(TwoShields)))
    val started = recoverStarted(rules, silverCharmAtFairIsle)
    val continued = resolved(rules, started,
      RecoverProcedure.choiceDecisionId, continueRecovering)
    val rerolled = resolved(rules, continued, SilverCharm.decisionId,
      rerollingDice(1))
    val outcome = recoverOutcome(rerolled)
    assertEquals(outcome.faces,
      Vector[DieFace](OneShield, TwoShields, Blank, Blank))
    assertEquals(outcome.score, 3)
    // The throw holds the rerolled die beside the two new ones.
    assertEquals(outcome.rerolled, Vector(1))
    assertEquals(parkedDecisionId(rerolled),
      RecoverProcedure.relicDecisionId)
    val events = started.events ++ continued.events ++ rerolled.events
    assertEquals(silverCharmNotes(events),
      Vector((Vector[NoteArg](NoteArg.Dice(Vector(Blank))), true)))
    assertEquals(ActPowerHarness.replayed(rules, ready, events),
      Outcomes.ready(rerolled.state))
    // The dice-pool change every Recover starts with does not round-trip
    // the wire (its window is not kept), so it is left out of the check.
    assertWireRoundTrips(events.filterNot {
      case step: WalkerStepRecorded =>
        step.ops.exists(_.isInstanceOf[ModifyDicePool])
      case _ => false
    })

  test("choosing no die rerolls nothing and writes no line"):
    val rules = rulesRolling(scripted(Vector(OneShield, Blank),
      Vector(Blank, Blank)))
    val started = recoverStarted(rules, silverCharmAtFairIsle)
    val continued = resolved(rules, started,
      RecoverProcedure.choiceDecisionId, continueRecovering)
    val kept = resolved(rules, continued, SilverCharm.decisionId,
      rerollingDice())
    val outcome = recoverOutcome(kept)
    assertEquals(outcome.faces,
      Vector[DieFace](OneShield, Blank, Blank, Blank))
    assertEquals(outcome.rerolled, Vector.empty[Int])
    assertEquals(silverCharmNotes(continued.events ++ kept.events),
      Vector.empty)
    assertEquals(parkedDecisionId(kept), RecoverProcedure.choiceDecisionId)

  test("a die may be rerolled again on a later payment"):
    // Die 1 is rerolled on the second payment and again on the third.
    val rules = rulesRolling(scripted(Vector(Blank, Blank),
      Vector(Blank, Blank), Vector(Blank),
      Vector(OneShield, Blank), Vector(TwoShields)))
    val started = recoverStarted(rules, silverCharmAtFairIsle)
    val second = resolved(rules, resolved(rules, started,
      RecoverProcedure.choiceDecisionId, continueRecovering),
      SilverCharm.decisionId, rerollingDice(0))
    assertEquals(parkedDecisionId(second), RecoverProcedure.choiceDecisionId)
    val third = resolved(rules, resolved(rules, second,
      RecoverProcedure.choiceDecisionId, continueRecovering),
      SilverCharm.decisionId, rerollingDice(0))
    val outcome = recoverOutcome(third)
    assertEquals(outcome.faces, Vector[DieFace](TwoShields, Blank, Blank,
      Blank, OneShield, Blank))
    assertEquals(outcome.score, 3)
    assertEquals(parkedDecisionId(third), RecoverProcedure.relicDecisionId)

  test("without the pick, a later payment rolls at once"):
    val rules = rulesRolling(scripted(Vector(Blank, Blank),
      Vector(TwoShields, TwoShields)))
    val started = recoverStarted(rules, silverCharmAtFairIsle,
      picked = false)
    val continued = resolved(rules, started,
      RecoverProcedure.choiceDecisionId, continueRecovering)
    assertEquals(parkedDecisionId(continued),
      RecoverProcedure.relicDecisionId)

  test("Jinx after Silver Charm rerolls every die of the throw, the " +
      "earlier one too"):
    // Jinx is kept on the first throw. On the second, Silver Charm rerolls
    // die 1 with the two new dice, and Jinx then rerolls those three,
    // leaving die 2 as it was.
    val jinx = CatalogNames.denizen("Jinx")
    val table = silverCharmAtFairIsle.adviser(p1, jinx)
      .secrets(p1, faceUp = 1)
    val rules = rulesRolling(scripted(Vector(Blank, Blank),
      Vector(Blank, Blank), Vector(Blank),
      Vector(OneShield, OneShield, OneShield)))
    val first = RerollChoice.decisionId(pool, 1)
    val second = RerollChoice.decisionId(pool, 2)
    val started = recoverStarted(rules, table)
    assertEquals(parkedDecisionId(started), first)
    val kept = resolved(rules, started, first,
      ChooseOneAnswer(RerollChoice.keep))
    val continued = resolved(rules, kept, RecoverProcedure.choiceDecisionId,
      continueRecovering)
    val charmed = resolved(rules, continued, SilverCharm.decisionId,
      rerollingDice(0))
    assertEquals(parkedDecisionId(charmed), second)
    val jinxed = resolved(rules, charmed, second,
      ChooseOneAnswer(DecisionOptionRef.Denizen(jinx)))
    assertEquals(recoverOutcome(jinxed).faces,
      Vector[DieFace](OneShield, Blank, OneShield, OneShield))
    assertEquals(parkedDecisionId(jinxed), RecoverProcedure.relicDecisionId)
