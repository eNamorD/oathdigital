package oathdigital.gameplay.powers.recover

import oathdigital.gameplay.{ModifierPlay, OathRules, WalkerDiceFixture}
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.walker.WalkerDice
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatacombsGame, CatalogNames, Look,
  Outcomes, Table}
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
  private def relicWorshipAtFairIsleTable(secrets: Int = 2,
      relicAtSite: Boolean = true): Table =
    val atFairIsle = Table.start.pawn(p1, "Fair Isle").secrets(p1, secrets)
      .denizen("Catacombs", at = "Fair Isle")
    val withRelic =
      if relicAtSite then atFairIsle.relicAt("Dowsing Sticks", "Fair Isle")
      else atFairIsle
    withRelic.adviser(p1, worship).supply(p1, 4)
  private def relicWorshipAtFairIsle(secrets: Int = 2): ReadyGame =
    relicWorshipAtFairIsleTable(secrets).ready

  /** Every event of a whole Recover, and the game after it. */
  private case class Recovered(events: Vector[OathEvent], result: ReadyGame)

  /** A whole Recover that succeeds and takes the site's relic. */
  private def recover(ready: ReadyGame, selected: Vector[PowerId])
      : Recovered =
    val started = accepted(ModifierPlay.started(rules, Ready(ready),
      ActionRef.Recover, p1, PowerWindow.RecoverModifierSelection, selected))
    val relic = RecoverProcedure.actorFacedownRelics(
      Outcomes.ready(started.state), p1).head.id
    val done = accepted(
      rules.resolveWalker(started.state, p1, RecoverProcedure.relicDecisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Relic(relic))))
    Recovered(started.events ++ done.events, Outcomes.ready(done.state))

  private def me(ready: ReadyGame): PlayerState = Look(ready).player(p1)

  test("after recovering a relic the player pays a secret onto the card and " +
      "gains 2 Supply"):
    val ready = relicWorshipAtFairIsle()
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
    val ready = relicWorshipAtFairIsle()
    val result = recover(ready, Vector.empty).result
    assertEquals(me(result).board.faceUpSecrets, 2)
    assertEquals(me(result).board.supply.supply, 4 - 1)

  test("a Recover that ends without a relic has still paid the secret, and " +
      "gains nothing"):
    val ready = relicWorshipAtFairIsle()
    val started = accepted(ModifierPlay.started(failing, Ready(ready),
      ActionRef.Recover, p1, PowerWindow.RecoverModifierSelection, modifiers))
    // The secret is paid at the pick, before the roll that fails.
    val paid = Outcomes.ready(started.state)
    assertEquals(me(paid).board.faceUpSecrets, 1)
    assertEquals(Look(paid).tokensOn(worship), Tokens(0, 1))
    val stopped = accepted(
      failing.resolveWalker(started.state, p1,
      RecoverProcedure.choiceDecisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("stop"))))
    val result = Outcomes.ready(stopped.state)
    assertEquals(me(result).board.faceUpSecrets, 1)
    assertEquals(me(result).board.supply.supply, 4 - 1)

  test("with one faceup secret, picking Catacombs leaves Relic Worship " +
      "unpayable; with two, both are picked and paid"):
    def table(secrets: Int) =
      relicWorshipAtFairIsleTable(secrets, relicAtSite = false).ready
    val begun = accepted(rules.startWalker(Ready(table(1)),
      ActionRef.Recover, p1))
    val afterCatacombs = accepted(rules.resolveWalker(begun.state, p1,
      CatacombsGame.modifierDecisionId,
      ModifierPlay.pick(CatacombsContribution.id)))
    assertEquals(CatacombsGame.modifierOption(afterCatacombs.state, worship),
      DecisionOption.Unavailable(
        DecisionOption.Denizen(DecisionOptionRef.Denizen(worship)),
        OptionReason.Written("Needs 1 secret; you have 0")))
    val both = accepted(ModifierPlay.started(rules, Ready(table(2)),
      ActionRef.Recover, p1, PowerWindow.RecoverModifierSelection,
      Vector(CatacombsContribution.id, RelicWorship.id)))
    // Catacombs and Relic Worship each pay one of the two secrets.
    assertEquals(me(Outcomes.ready(both.state)).board.faceUpSecrets, 0)

  test("without a faceup secret nothing is asked; onto an occupied card it " +
      "is listed unpayable"):
    // Neither modifier can be paid for, so the Recover rolls at once.
    val broke = accepted(rules.startWalker(
      Ready(relicWorshipAtFairIsle(secrets = 0)), ActionRef.Recover, p1))
    assertEquals(me(Outcomes.ready(broke.state)).board.supply.supply, 4 - 1)
    // Catacombs can be picked at a relic-less Fair Isle, so the decision
    // asks, and lists Relic Worship unpayable.
    val occupied = relicWorshipAtFairIsleTable(relicAtSite = false)
      .tokens(worship, secrets = 1).ready
    val begun = accepted(rules.startWalker(Ready(occupied),
      ActionRef.Recover, p1))
    assertEquals(CatacombsGame.modifierOption(begun.state, worship),
      DecisionOption.Unavailable(
        DecisionOption.Denizen(DecisionOptionRef.Denizen(worship)),
        OptionReason.Written("Cannot be paid for now")))

  test("a Recover that selects no power records no ignored-rule diagnostic"):
    val events = recover(relicWorshipAtFairIsle(), Vector.empty).events
    assertEquals(events.collect {
      case ignored: OathEvent.IgnoredRulesRecorded => ignored }, Vector.empty)
