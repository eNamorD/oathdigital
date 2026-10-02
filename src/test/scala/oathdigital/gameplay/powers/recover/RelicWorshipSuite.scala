package oathdigital.gameplay.powers.recover

import oathdigital.gameplay.{OathRules, WalkerDiceFixture}
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.walker.WalkerDice
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class RelicWorshipSuite extends munit.FunSuite:
  private def rulesRolling(dice: WalkerDice): OathRules = new OathRules(
    catalog, walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = dice)

  /** Recover rolls as it walks, so what the dice show is settled before the
    * command runs: `rules` recovers on the first roll, `failing` never does.
    */
  private val rules = rulesRolling(WalkerDiceFixture.shields)
  private val failing = rulesRolling(WalkerDiceFixture.blanks)

  private val worship = CatalogNames.denizen("Relic Worship")
  private val modifiers = Vector(RelicWorship.id)
  /** p1's Act at Fair Isle, the easiest in-play Recover site with a relic
    * slot, beside a faceup Catacombs, holding Relic Worship as a faceup
    * adviser with `secrets` faceup secrets and 4 Supply. Dowsing Sticks lies
    * facedown at Fair Isle unless `relicAtSite` is false. */
  private def stagedTable(secrets: Int = 2, relicAtSite: Boolean = true)
      : Table =
    val atFairIsle = Table.start.pawn(p1, "Fair Isle").secrets(p1, secrets)
      .denizen("Catacombs", at = "Fair Isle")
    val withRelic =
      if relicAtSite then atFairIsle.relicAt("Dowsing Sticks", "Fair Isle")
      else atFairIsle
    withRelic.adviser(p1, worship).supply(p1, 4)
  private def staged(secrets: Int = 2): ReadyGame = stagedTable(secrets).ready

  /** Every event of a whole Recover, and the game after it. */
  private case class Recovered(events: Vector[OathEvent], result: ReadyGame)

  /** A whole Recover that succeeds and takes the site's relic. */
  private def recover(ready: ReadyGame, selected: Vector[PowerId])
      : Recovered =
    val started = accepted(
      rules.startWalker(Ready(ready), ActionRef.Recover, p1, selected))
    val relic = RecoverProcedure.actorFacedownRelics(
      Outcomes.ready(started.state), p1).head.id
    val done = accepted(
      rules.resolveWalker(started.state, p1, RecoverProcedure.relicDecisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Relic(relic))))
    Recovered(started.events ++ done.events, Outcomes.ready(done.state))

  private def me(ready: ReadyGame): PlayerState = Look(ready).player(p1)

  test("after recovering a relic the player pays a secret onto the card and " +
      "gains 2 Supply"):
    val ready = staged()
    val Recovered(events, result) = recover(ready, modifiers)
    assertEquals(me(result).relics.size, me(ready).relics.size + 1)
    assertEquals(me(result).board.faceUpSecrets, 1)
    assertEquals(Look(result).tokensOn(worship), Tokens(0, 1))
    // 4 Supply, less 1 for the roll, plus 2.
    assertEquals(me(result).board.supply.supply, 4 - 1 + 2)
    assertEquals(ActPowerHarness.replayed(rules, ready, events), result)
    // The dice-pool change every Recover starts with does not round-trip the
    // wire (its window is not kept), so it is left out of the check.
    assertWireRoundTrips(events.filterNot {
      case step: oathdigital.gameplay.walker.WalkerStepRecorded =>
        step.ops.exists(_.isInstanceOf[ModifyDicePool])
      case _ => false
    })

  test("without the selection nothing is paid and nothing is gained"):
    val ready = staged()
    val result = recover(ready, Vector.empty).result
    assertEquals(me(result).board.faceUpSecrets, 2)
    assertEquals(me(result).board.supply.supply, 4 - 1)

  test("a Recover that ends without a relic has still paid the secret, and " +
      "gains nothing"):
    val ready = staged()
    val actor = p1
    val started = accepted(
      failing.startWalker(Ready(ready), ActionRef.Recover, actor, modifiers))
    // The secret is paid at the start, before the roll that fails.
    val paid = Outcomes.ready(started.state)
    assertEquals(me(paid).board.faceUpSecrets, 1)
    assertEquals(Look(paid).tokensOn(worship), Tokens(0, 1))
    val stopped = accepted(
      failing.resolveWalker(started.state, actor,
      RecoverProcedure.choiceDecisionId, DecisionAnswer.ChooseOneAnswer(
      DecisionOptionRef.Button("stop"))))
    val result = Outcomes.ready(stopped.state)
    assertEquals(me(result).board.faceUpSecrets, 1)
    assertEquals(me(result).board.supply.supply, 4 - 1)

  test("Catacombs and Relic Worship with one faceup secret are refused at " +
      "selection, and accepted with two"):
    val catacombs = PowerId("denizen.catacombs")
    def attempt(secrets: Int) =
      val ready = stagedTable(secrets, relicAtSite = false).ready
      rules.startWalker(Ready(ready), ActionRef.Recover, p1,
        Vector(catacombs, RelicWorship.id))
    val refused = attempt(1)
    assert(refused.left.toOption.exists(_.toString.contains(
      "cannot all be paid together")), refused.toString)
    assert(attempt(2).isRight)

  test("it cannot be selected without a faceup secret, or onto an occupied card"):
    val broke = staged(secrets = 0)
    assertEquals(rules.startWalker(Ready(broke), ActionRef.Recover,
      p1, modifiers).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "power denizen.relic-worship is not applicable to this recover")))
    val occupied = stagedTable().tokens(worship, secrets = 1).ready
    assertEquals(rules.startWalker(Ready(occupied), ActionRef.Recover,
      p1, modifiers).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "power denizen.relic-worship is not applicable to this recover")))

  test("a Recover that selects no power records no ignored-rule diagnostic"):
    val events = recover(staged(), Vector.empty).events
    assert(!events.exists(_.isInstanceOf[OathEvent.IgnoredRulesRecorded]))
