package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.dice.RerollChoice
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, ParkedNode, Table}
import oathdigital.testkit.Unwrap.accepted

/** Spell Breaker over battle plans and rerolls (catalog batch 8, slice
  * 10): a refused plan is withheld with the plain reason, since nobody is
  * asked inside a Campaign; a refused reroll is askable outside a
  * Campaign's own dice. */
class SpellBreakerOfferSuite extends munit.FunSuite:
  import AttackDieFace.HollowSword
  import DefenseDieFace.{Blank, TwoShields}

  private val breaker = CatalogNames.denizen("Spell Breaker")
  private val woods = CatalogNames.site("Deep Woods")
  private val fireTalkers = CatalogNames.denizen("Fire Talkers")
  private val fireTalkersRef = DecisionOptionRef.Denizen(fireTalkers)
  private val armor = CatalogNames.denizen("Gleaming Armor")
  private val honors = CatalogNames.denizen("Battle Honors")
  private val watchdog = CatalogNames.denizen("Watchdog")
  private val watchdogRef = DecisionOptionRef.Denizen(watchdog)
  private val titleRef: DecisionOptionRef = DecisionOptionRef.Button("title")
  private val jinx = CatalogNames.denizen("Jinx")
  private val jinxRef = DecisionOptionRef.Denizen(jinx)
  private val broken = Some(OptionReason.Noted(SpellBreaker.id,
    SpellBreaker.plain(PowerSourceRef.Card(breaker))))

  /** `b` with Spell Breaker at Deep Woods, ruled by `ruler`. */
  private def breakerRuledBy(b: Board, ruler: PlayerId): Board =
    on(b)(_.denizen(breaker, at = woods).warbandsAt(woods, ruler, 1))

  /** p1 attacks holding Fire Talkers faceup, a faceup secret and the
    * Darkest Secret, as `FireTalkersSuite` sets it up. */
  private def attackerWithFireTalkers: Board =
    val b = withSecrets(withAdviser(board(warbands = 5, supply = 7),
      fireTalkers, Orientation.FaceUp), 1)
    on(b)(_.darkestSecret(Some(b.actor), secrets = 1))

  test("an enemy's Fire Talkers is withheld with Spell Breaker's reason"):
    // Battle Honors, free, keeps the plan window asking: a window with no
    // plan to choose is not asked.
    val base = withAdviser(attackerWithFireTalkers, honors,
      Orientation.FaceUp)
    val b = breakerRuledBy(base, base.rival)
    val run = commit(rules(winningDice), b, 2)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    assertEquals(run.withheld(b.actor, fireTalkersRef), broken)

  test("Spell Breaker's ruler uses Fire Talkers freely"):
    val base = attackerWithFireTalkers
    val b = breakerRuledBy(base, base.actor)
    assert(commit(rules(winningDice), b, 2).offered(b.actor)
      .contains(fireTalkersRef))

  test("a free plan Gleaming Armor taxes with a secret is refused too, " +
      "the title's plan included, so the defender is not asked"):
    // p1 attacks holding Gleaming Armor and rules Spell Breaker; the
    // defender's Watchdog costs nothing of itself, but a secret with the
    // tax, so the defender, p1's enemy, may not use it. The title's plan,
    // which the tax makes flip a secret, is a power too.
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val held = withAdviserFor(withAdviser(base, armor, Orientation.FaceUp),
      base.rival, watchdog, Orientation.FaceUp)
    val b = breakerRuledBy(on(held)(_.secrets(base.rival, faceUp = 1)),
      base.actor)
    assert(awaits(commit(rules(losingDice), b, 4), b.actor,
      CampaignIds.sacrifice))
    val free = breakerRuledBy(on(held)(_.secrets(base.rival, faceUp = 1)),
      base.rival)
    val offered = commit(rules(losingDice), free, 4).offered(free.actor)
    assert(offered.contains(watchdogRef) && offered.contains(titleRef),
      offered.toString)

  test("Jinx after a Campaign roll is not offered, and nobody is asked"):
    val base = withSecrets(withAdviser(board(warbands = 2, supply = 7), jinx,
      Orientation.FaceUp), faceUp = 1)
    val b = breakerRuledBy(base, base.rival)
    val game = rules(scripted(Vector(HollowSword, HollowSword),
      Vector(Blank, Blank)))
    val rolled = commit(game, b, 2).finishPlanWindows
    assert(!awaits(rolled, b.actor,
      RerollChoice.decisionId(CampaignIds.attackPool, 1)))

  /** p1's Recover at Fair Isle with Jinx faceup, as `JinxSuite` sets it
    * up, with Spell Breaker ruled by p2, parked on its first reroll
    * choice. */
  private def recoverRolledBesideBreaker: OathTransition =
    val site = CatalogNames.site("Fair Isle")
    val start = Table.start.turn(Table.p1, Phase.Act)
      .pawn(Table.p1, at = site)
      .relicAt(CatalogNames.relic("Dowsing Sticks"), at = site)
      .adviser(Table.p1, jinx).secrets(Table.p1, faceUp = 2)
      .denizen(breaker, at = woods).warbandsAt(woods, Table.p2, 1).ready
    val game = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
      walkerDice = scripted(Vector(Blank, Blank), Vector(TwoShields,
        TwoShields)))
    accepted(game.startWalker(Ready(start), ActionRef.Recover, Table.p1))

  private val firstRecoverReroll =
    RerollChoice.decisionId(RecoverProcedure.recoverPool, 1)

  /** Jinx's option at the first Recover reroll choice `state` is parked
    * on. */
  private def jinxAtRecoverReroll(state: OathState): Option[DecisionOption] =
    ParkedNode.of(state, catalog, WalkerPowerCatalog.default(catalog))
      .toOption.flatten.collect {
        case ParkedNode.Decision(_, decide, _)
            if decide.decisionId == firstRecoverReroll => decide.query
      }.collect { case one: DecisionQuery.ChooseOne => one.options }
      .flatMap(_.find(_.ref == jinxRef))

  test("Jinx after a Recover roll is askable of Spell Breaker's ruler"):
    val jinxOption = jinxAtRecoverReroll(recoverRolledBesideBreaker.state)
    assert(jinxOption.exists(_.askable), jinxOption.toString)

  test("once the ruler refuses, the parked reroll choice stays, showing " +
      "the plain reason"):
    val parked = recoverRolledBesideBreaker.state match
      case Ready(ready) => ready
      case other => fail(s"expected a ready game, got $other")
    val refused = Ready(parked.copy(consent = ConsentState(
      answers = Map(ConsentKey(SpellBreaker.id, Table.p1, "power.use",
        jinxRef) -> ConsentAnswer.Refused.by(Table.p2)),
      turn = Some(ConsentTurn.of(parked)))))
    assertEquals(jinxAtRecoverReroll(refused), broken.map(reason =>
      DecisionOption.Unavailable(DecisionOption.Denizen(jinxRef), reason)))

  test("an enemy's Arcane Armor, which pays X secrets, is withheld too"):
    val arcane = CatalogNames.denizen("Arcane Armor")
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val held = on(withAdviserFor(base, base.rival, arcane,
      Orientation.FaceUp))(_.secrets(base.rival, faceUp = 2))
    val b = breakerRuledBy(held, base.actor)
    val run = commit(rules(losingDice), b, 4)
    assertEquals(run.withheld(b.actor, DecisionOptionRef.Denizen(arcane)),
      broken)
