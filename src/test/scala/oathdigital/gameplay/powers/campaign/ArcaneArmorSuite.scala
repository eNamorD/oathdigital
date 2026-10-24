package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.dice.RerollChoice
import oathdigital.gameplay.powerresolver.{FoldPhase, Transform}
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{RollPayload, WalkerPowers,
  WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look}
import oathdigital.testkit.PowerStubs.ContributingStub

/** Arcane Armor: a defender's plan costing X secrets, which sets X defense
  * dice to double shields at the defense roll, before the rest are rolled.
  * It is the first plan whose amount costs secrets, and the first use of
  * fixed faces before a roll (catalog batch 7, slice 3). */
class ArcaneArmorSuite extends munit.FunSuite:
  import AttackDieFace.OneSword
  import DefenseDieFace.{Blank, TwoShields}
  private val card = CatalogNames.denizen("Arcane Armor")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val jinx = CatalogNames.denizen("Jinx")

  private def lines(run: Run): Vector[NoteText.Said] =
    said(ArcaneArmor, run.events)

  private def setLine(dice: String): NoteText.Said = NoteText.Said("set",
    s"Set $dice to double shields.", covers = false)

  /** Dice that roll four swords for the attack and `defense` for the
    * defense, failing on any other count. */
  private def fourSwordsAnd(defense: DefenseDieFace*) =
    dice(attack = Vector.fill(4)(OneSword), defense = defense.toVector)

  /** Production rules with `fourSwordsAnd(defense)`. */
  private def rolling(defense: DefenseDieFace*): OathRules =
    rules(fourSwordsAnd(defense*))

  /** p2 rules the origin, which prints two defense dice, with two warbands,
    * and holds the Oathkeeper title, Arcane Armor faceup and `secrets`
    * faceup secrets. */
  private def defenderWithArcaneArmor(secrets: Int): Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.secrets(base.rival, faceUp = secrets))

  /** p1 commits four warbands against `b`, and p2 chooses Arcane Armor. */
  private def chosen(b: Board, game: OathRules,
      powers: WalkerPowers = WalkerPowerCatalog.default(catalog)): Run =
    commit(game, b, 4, powers = powers)
      .pick(b.rival, CampaignIds.defenderPlan, ref)

  /** Every production power and `stub`. */
  private def productionAnd(stub: ContributingStub): WalkerPowers =
    val all = WalkerPowerCatalog.default(catalog)
    all.copy(powers = all.powers :+ stub)

  /** Rules with `powers` and `fourSwordsAnd(defense)`. */
  private def rollingWith(powers: WalkerPowers,
      defense: DefenseDieFace*): OathRules =
    new OathRules(catalog, walkerPowerCatalog = powers,
      walkerDice = fourSwordsAnd(defense*))

  /** A power that removes `n` defense dice once both pools are gathered. */
  private def removesDefenseDiceAfterGathering(n: Int): ContributingStub =
    ContributingStub(PowerId("test.removes-defense-dice"), Map(
      PowerWindow.CampaignGatherPools -> Vector(Transform((_, children) =>
        children :+ ModifyDicePool(CampaignIds.defensePool, -n),
        FoldPhase.Late))))

  /** A power that removes `n` defense dice just before the defense roll,
    * ahead of every other power's steps there. */
  private def removesDefenseDiceBeforeRoll(n: Int): ContributingStub =
    ContributingStub(PowerId("test.removes-defense-dice"), Map(
      PowerWindow.CampaignDefenseRoll -> Vector(Transform((_, children) =>
        ModifyDicePool(CampaignIds.defensePool, -n) +: children,
        FoldPhase.Late))))

  test("the defender pays a secret to set one defense die to double " +
      "shields, and the other is rolled"):
    val b = defenderWithArcaneArmor(secrets = 2)
    val asked = chosen(b, rolling(Blank))
    assert(awaits(asked, b.rival, ArcaneArmor.decisionId))
    val done = asked.answer(b.rival, ArcaneArmor.decisionId,
      ChooseAmountAnswer(1)).finishPlanWindows
    assertEquals(done.result.defenseFaces, Vector(TwoShields, Blank))
    // Two shields, plus the two warbands at the origin.
    assertEquals(done.result.defenseScore, 4)
    // Paid off turn, so the secret settles facedown at once.
    assertEquals(Look(done.state).faceUpSecrets(b.rival), 1)
    assertEquals(Look(done.state).faceDownSecrets(b.rival), 1)
    assertEquals(lines(done), Vector(setLine("1 defense die")))

  test("X runs from one to the smaller of the defender's secrets and the " +
      "defense dice"):
    def asked(secrets: Int): DecisionQuery =
      val b = defenderWithArcaneArmor(secrets)
      chosen(b, rolling()).query(b.actor)
    assertEquals(asked(3), DecisionQuery.ChooseAmount(1, 2,
      Some(ArcaneArmor.heading), ArcaneArmor.confirm))
    assertEquals(asked(1), DecisionQuery.ChooseAmount(1, 1,
      Some(ArcaneArmor.heading), ArcaneArmor.confirm))

  test("with every defense die set, nothing is rolled for the defense"):
    val b = defenderWithArcaneArmor(secrets = 3)
    val done = chosen(b, rolling())
      .answer(b.rival, ArcaneArmor.decisionId, ChooseAmountAnswer(2))
      .finishPlanWindows
    assertEquals(done.result.defenseFaces, Vector(TwoShields, TwoShields))
    assertEquals(done.result.defenseScore, 6)
    val rolled = done.events.collect {
      case WalkerStepRecorded(_, RollPayload(pool, _, _), _) => pool }
    assertEquals(rolled, Vector(CampaignIds.attackPool))
    assertEquals(lines(done), Vector(setLine("2 defense dice")))

  test("its price reads X secrets, and without a secret it is shown " +
      "unavailable"):
    val b = defenderWithArcaneArmor(secrets = 1)
    val option = commit(rolling(), b, 4).options(b.actor)
      .find(_.ref == ref)
    assertEquals(option.collect {
      case DecisionOption.Priced(_, price) => price
    }, Some(OptionPrice(secretsX = true)))
    val poor = defenderWithArcaneArmor(secrets = 0)
    assertEquals(commit(rolling(), poor, 4).withheld(poor.actor, ref),
      Some(OptionReason.Written(ArcaneArmor.noSecret)))

  test("with no defense dice it is shown unavailable"):
    val powers = productionAnd(removesDefenseDiceAfterGathering(2))
    val b = defenderWithArcaneArmor(secrets = 2)
    assertEquals(commit(rollingWith(powers), b, 4, powers = powers)
      .withheld(b.actor, ref, powers),
      Some(OptionReason.Written(ArcaneArmor.noDefenseDice)))

  test("a pool shrunk below X by the roll sets only what it holds, and " +
      "the secrets stay spent"):
    val powers = productionAnd(removesDefenseDiceBeforeRoll(1))
    val b = defenderWithArcaneArmor(secrets = 2)
    val done = chosen(b, rollingWith(powers), powers)
      .answer(b.rival, ArcaneArmor.decisionId, ChooseAmountAnswer(2))
      .finishPlanWindows
    assertEquals(done.result.defenseFaces, Vector(TwoShields))
    assertEquals(Look(done.state).faceDownSecrets(b.rival), 2)
    assertEquals(lines(done), Vector(setLine("1 defense die")))

  test("Jinx after the defense roll rerolls only the rolled die"):
    val base = defenderWithArcaneArmor(secrets = 2)
    val b = withAdviserFor(base, base.rival, jinx, Orientation.FaceUp)
    // The set die is no throw, so the roll after it is the first.
    val reroll = RerollChoice.decisionId(CampaignIds.defensePool, 1)
    val asked = chosen(b, rolling(Blank))
      .answer(b.rival, ArcaneArmor.decisionId, ChooseAmountAnswer(1))
      .finishPlanWindows
    assert(awaits(asked, b.rival, reroll))
    val done = asked.pick(b.rival, reroll, RerollChoice.optionRef(jinx))
    assertEquals(done.since(asked).collect {
      case Reroll(pool, indices, _) => pool -> indices
    }, Vector(CampaignIds.defensePool -> Vector(1)))

  test("a bandit defender does not apply it"):
    val base = board(warbands = 5, supply = 7)
    val b = withSiteCard(base, base.origin, card)
    val done = commit(rolling(Blank, Blank), b, 4).finishPlanWindows
    assert(!done.ops.exists(_.isInstanceOf[SetDieFaces]))
    assertEquals(done.result.defenseFaces, Vector(Blank, Blank))

  test("a replayed game reaches the same faces"):
    val b = defenderWithArcaneArmor(secrets = 2)
    val done = chosen(b, rolling(Blank))
      .answer(b.rival, ArcaneArmor.decisionId, ChooseAmountAnswer(1))
      .finishPlanWindows
    // Replay rolls nothing, and these rules have no dice source.
    val silent = rules()
    val replay = done.events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(b.ready)))((state, event) =>
      state.flatMap(silent.evolve(_, event)))
    assertEquals(replay.map(ready(_).game.current.lastCampaignResult),
      Right(ready(done.state).game.current.lastCampaignResult))

  /** `secrets` for the defender, and the attacker holds Gleaming Armor, which
    * makes every plan the defender chooses cost a secret more. */
  private def defenderTaxedByGleamingArmor(secrets: Int): Board =
    withAdviser(defenderWithArcaneArmor(secrets), "Gleaming Armor",
      Orientation.FaceUp)

  test("a surcharge takes its secret first, so X counts what is left"):
    // Two secrets and two dice would allow X = 2 without the surcharge.
    val b = defenderTaxedByGleamingArmor(secrets = 2)
    val asked = chosen(b, rolling(Blank))
    assertEquals(asked.query(b.actor), DecisionQuery.ChooseAmount(1, 1,
      Some(ArcaneArmor.heading), ArcaneArmor.confirm))
    val done = asked.answer(b.rival, ArcaneArmor.decisionId,
      ChooseAmountAnswer(1)).finishPlanWindows
    assertEquals(Look(done.state).faceUpSecrets(b.rival), 0)
    assertEquals(Look(done.state).faceDownSecrets(b.rival), 2)
    assertEquals(done.result.defenseFaces, Vector(TwoShields, Blank))

  test("when the surcharge takes the last secret, it is shown unavailable"):
    val b = defenderTaxedByGleamingArmor(secrets = 1)
    assertEquals(commit(rolling(), b, 4).withheld(b.actor, ref),
      Some(OptionReason.Written("Needs 1 secret; you have 0")))
