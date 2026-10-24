package oathdigital.gameplay.powers.dice

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.dice.RerollChoice
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Unwrap.accepted

/** Jinx (card 68): after its ruler rolls, they may pay a secret to reroll
  * every die of that throw, once (catalog batch 7, slice 1). The Campaign
  * and Recover rolls are tested here; each ACTION power's roll is tested in
  * that power's suite. */
class JinxSuite extends munit.FunSuite:
  import AttackDieFace.{HollowSword, OneSword}
  import DefenseDieFace.{Blank, TwoShields}

  private val jinx = CatalogNames.denizen("Jinx")
  private val ref: DecisionOptionRef = RerollChoice.optionRef(jinx)
  private val attackReroll = RerollChoice.decisionId(CampaignIds.attackPool, 1)
  private val defenseReroll =
    RerollChoice.decisionId(CampaignIds.defensePool, 1)

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(Jinx.id, Vector(Jinx.rerolledAttack, Jinx.rerolledDefense),
      run.events)

  private def rerolled(dice: String): NoteText.Said =
    NoteText.Said(s"rerolled-$dice", s"Rerolled the $dice dice.",
      covers = false)

  /** p1 attacks the bandit-ruled origin with 2 warbands, holding Jinx
    * faceup and `secrets` faceup secrets. */
  private def attacker(secrets: Int = 1): Board =
    withSecrets(withAdviser(board(warbands = 2, supply = 7), jinx,
      Orientation.FaceUp), faceUp = secrets)

  test("the attacker pays a secret to reroll every attack die"):
    val b = attacker()
    val game = rules(scripted(Vector(HollowSword, HollowSword),
      Vector(OneSword, OneSword), Vector(Blank, Blank)))
    val asked = commit(game, b, 2).finishPlanWindows
    assert(awaits(asked, b.actor, attackReroll))
    val done = asked.pick(b.actor, attackReroll, ref).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](OneSword, OneSword))
    assertEquals(Look(ready(done.state)).faceUpSecrets(b.actor), 0)
    assertEquals(lines(done), Vector(rerolled("attack")))

  test("Jinx is a button naming the card after the secret it costs"):
    val b = attacker()
    val game = rules(scripted(Vector(HollowSword, HollowSword),
      Vector(Blank, Blank)))
    val asked = commit(game, b, 2).finishPlanWindows
    assertEquals(asked.options(b.actor), Vector(
      DecisionOption.Button(RerollChoice.optionRef(jinx),
        "[secret] Reroll all with Jinx"),
      DecisionOption.Button(RerollChoice.keep, RerollChoice.keepLabel)))

  test("Jinx rerolls a throw only once"):
    val b = attacker(secrets = 2)
    val game = rules(scripted(Vector(HollowSword, HollowSword),
      Vector(HollowSword, OneSword), Vector(Blank, Blank)))
    val done = commit(game, b, 2).finishPlanWindows
      .pick(b.actor, attackReroll, ref).finishPlanWindows
    // Nothing more is asked: the second secret stays.
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](HollowSword, OneSword))
    assertEquals(Look(ready(done.state)).faceUpSecrets(b.actor), 1)

  test("without a secret, Jinx is not offered and nothing is asked"):
    val b = attacker(secrets = 0)
    val game = rules(scripted(Vector(HollowSword, HollowSword),
      Vector(Blank, Blank)))
    val done = commit(game, b, 2).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](HollowSword, HollowSword))

  test("a facedown Jinx has no power"):
    val b = withSecrets(withAdviser(board(warbands = 2, supply = 7), jinx,
      Orientation.FaceDown), faceUp = 1)
    val game = rules(scripted(Vector(HollowSword, HollowSword),
      Vector(Blank, Blank)))
    val done = commit(game, b, 2).finishPlanWindows
    assertEquals(done.result.attackFaces,
      Vector[AttackDieFace](HollowSword, HollowSword))

  test("a defender rerolls the defense dice, paying outside their turn"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 4, supply = 7))
    val b = on(withAdviserFor(base, base.rival, jinx, Orientation.FaceUp))(
      _.secrets(base.rival, faceUp = 1))
    val game = rules(scripted(Vector.fill(4)(OneSword), Vector(Blank, Blank),
      Vector(TwoShields, TwoShields)))
    val asked = commit(game, b, 4).finishPlanWindows
    assert(awaits(asked, b.rival, defenseReroll))
    val done = asked.pick(b.rival, defenseReroll, ref).finishPlanWindows
    assertEquals(done.result.defenseFaces,
      Vector[DefenseDieFace](TwoShields, TwoShields))
    assertEquals(Look(ready(done.state)).faceUpSecrets(b.rival), 0)
    assertEquals(lines(done), Vector(rerolled("defense")))

  test("Jinx at a site its ruler rules rerolls their roll"):
    // p1 rules Broken Peaks with two warbands, and Jinx stands there.
    val base = board(warbands = 2, supply = 7)
    val peaks = CatalogNames.site("Broken Peaks")
    val b = withSecrets(withSiteCard(actorRules(base, peaks), peaks, jinx),
      faceUp = 1)
    val game = rules(scripted(Vector(HollowSword, HollowSword),
      Vector(OneSword, OneSword), Vector(Blank, Blank)))
    val asked = commit(game, b, 2).finishPlanWindows
    assert(awaits(asked, b.actor, attackReroll))

  test("the actor rerolls a Recover roll, and each roll is its own throw"):
    // Fair Isle's difficulty is 3. The first roll and its reroll fail, the
    // second roll is rerolled into a success.
    val site = CatalogNames.site("Fair Isle")
    val relic = CatalogNames.relic("Dowsing Sticks")
    val start = Table.start.turn(Table.p1, Phase.Act).pawn(Table.p1, at = site)
      .relicAt(relic, at = site).adviser(Table.p1, jinx)
      .secrets(Table.p1, faceUp = 2).ready
    val game = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
      walkerDice = scripted(Vector(Blank, Blank), Vector(Blank, Blank),
        Vector(Blank, Blank), Vector(TwoShields, TwoShields)))
    val first = RerollChoice.decisionId(RecoverProcedure.recoverPool, 1)
    val second = RerollChoice.decisionId(RecoverProcedure.recoverPool, 2)
    val started = accepted(game.startWalker(Ready(start), ActionRef.Recover,
      Table.p1))
    val rerolledOnce = accepted(game.resolveWalker(started.state, Table.p1,
      first, DecisionAnswer.ChooseOneAnswer(ref)))
    val continued = accepted(game.resolveWalker(rerolledOnce.state, Table.p1,
      RecoverProcedure.choiceDecisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("continue"))))
    val rerolledTwice = accepted(game.resolveWalker(continued.state,
      Table.p1, second, DecisionAnswer.ChooseOneAnswer(ref)))
    val outcome = ready(rerolledTwice.state).game.current.rollOutcomes(
      RecoverProcedure.recoverPool)
    assertEquals(outcome.score, 4)
    assertEquals(Look(ready(rerolledTwice.state)).faceUpSecrets(Table.p1), 0)

  test("a replayed game reaches the same rerolled faces"):
    val b = attacker()
    val game = rules(scripted(Vector(HollowSword, HollowSword),
      Vector(OneSword, OneSword), Vector(Blank, Blank)))
    val done = commit(game, b, 2).finishPlanWindows
      .pick(b.actor, attackReroll, ref).finishPlanWindows
    // Replay rolls nothing: the faces ride the recorded reroll, and these
    // rules have no dice source, so a replay that rolled would fail.
    val silent = rules()
    val replay = done.events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(b.ready)))((state, event) =>
      state.flatMap(silent.evolve(_, event)))
    assertEquals(ready(accepted(replay)).game.current.lastCampaignResult,
      ready(done.state).game.current.lastCampaignResult)
