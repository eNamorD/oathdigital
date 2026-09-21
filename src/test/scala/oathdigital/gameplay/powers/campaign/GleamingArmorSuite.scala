package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** Gleaming Armor: while its faceup holder is in a Campaign, every plan the enemy
  * chooses costs one more secret, placed onto its card, or turned facedown for the
  * title's plan. A plan the enemy cannot afford with it is not offered, and the
  * option states the price with it.
  */
class GleamingArmorSuite extends munit.FunSuite {
  private val armor = cardWith("denizen.gleaming-armor")
  private val watchdog = cardWith("denizen.watchdog")
  private val honors = cardWith("denizen.battle-honors")
  private val brass = relicWith("relic.brass-army.campaign")
  private val titleRef: DecisionOptionRef = DecisionOptionRef.Button("title")

  private def secrets(b: Board, who: PlayerId, faceUp: Int): Board =
    replacePlayer(b, who)(p => p.copy(board = p.board.copy(faceUpSecrets = faceUp,
      faceDownSecrets = 0)))

  private def held(state: OathState, who: PlayerId): (Int, Int) = {
    val board = player(state, who).board
    board.faceUpSecrets -> board.faceDownSecrets
  }

  private def price(option: DecisionOption): OptionPrice = option match {
    case DecisionOption.Priced(_, price) => price
    case _ => OptionPrice()
  }

  // ---- the attacker holds it: the defender's plans cost more ---------------

  /** The attacker holds Gleaming Armor. The defender holds the title, and a
    * Watchdog, which costs nothing of itself.
    */
  private def attackerHolds(defenderSecrets: Int): Board = {
    val base = againstPlayer(board())
    secrets(withAdviserFor(withAdviser(base, armor, Orientation.FaceUp),
      base.other, watchdog, Orientation.FaceUp), base.other, defenderSecrets)
  }

  test("a defender's plan costs one more secret, which turns facedown at once because it is paid off turn") {
    val b = attackerHolds(1)
    val run = commit(rules(losing), b, 4)
    val options = run.options(b.actor)
    val watchdogOption = options.find(_.ref == DecisionOptionRef.Denizen(
      DenizenId(watchdog))).get
    assertEquals(price(watchdogOption), OptionPrice(secrets = 1))
    val picked = run.pick(b.other, CampaignIds.defenderPlan,
      DecisionOptionRef.Denizen(DenizenId(watchdog)))
    assertEquals(held(picked.state, b.other), (0, 1))
    assertEquals(player(picked.state, b.other).advisers.collectFirst {
      case card: DenizenState if card.id.value == watchdog => card.tokens },
      Some(Tokens.empty))
  }

  test("the title's plan costs a faceup secret turned facedown, since it has no card") {
    val b = attackerHolds(1)
    val run = commit(rules(losing), b, 4)
    val title = run.options(b.actor).find(_.ref == titleRef).get
    assertEquals(price(title), OptionPrice(secrets = 1))
    val picked = run.pick(b.other, CampaignIds.defenderPlan, titleRef)
    assertEquals(held(picked.state, b.other), (0, 1))
  }

  test("a plan the defender cannot afford with the added cost is not offered") {
    val b = attackerHolds(0)
    // Neither the title's plan nor the Watchdog can be paid for.
    assertEquals(commit(rules(losing), b, 4).continue,
      awaits(b.actor, CampaignIds.sacrifice))
  }

  test("without Gleaming Armor the same plans are free") {
    val base = againstPlayer(board())
    val b = secrets(withAdviserFor(base, base.other, watchdog, Orientation.FaceUp),
      base.other, 0)
    val run = commit(rules(losing), b, 4)
    assertEquals(run.options(b.actor).map(price), Vector.fill(3)(OptionPrice()))
  }

  test("a facedown Gleaming Armor is not active") {
    val base = againstPlayer(board())
    val b = secrets(withAdviserFor(withAdviser(base, armor, Orientation.FaceDown),
      base.other, watchdog, Orientation.FaceUp), base.other, 0)
    assertEquals(commit(rules(losing), b, 4).options(b.actor).map(price),
      Vector.fill(3)(OptionPrice()))
  }

  test("bandits are enemies too: a bandit defender is taxed, cannot pay, and applies no plan") {
    val two = board(extras = 1)
    val free = withSiteCard(withSiteCard(two, two.origin, watchdog),
      two.extras.head, honors)
    val taxed = withAdviser(free, armor, Orientation.FaceUp)
    // Every pool change but the attacker's: the printed defense, Watchdog's die
    // and the record of each plan the bandit applied.
    def defenseChanges(run: Run): Int = run.ops.count {
      case ModifyDicePool(pool, _, _) => pool != CampaignIds.attackPool
      case _ => false
    }
    def orderBank(state: OathState): Int =
      ready(state).banks.favor.getOrElse(Suit.Order, 0)
    // Without the holder the bandit applies both plans by itself: Watchdog's die
    // and a record of each, on top of the printed defense.
    val plain = commit(rules(losing), free, 2)
    val armored = commit(rules(losing), taxed, 2)
    assertEquals(plain.continue, awaits(free.actor, CampaignIds.sacrifice))
    assertEquals(armored.continue, awaits(taxed.actor, CampaignIds.sacrifice))
    assertEquals(defenseChanges(plain) - defenseChanges(armored), 3)
    // It offers nothing to the attacker either, and nothing is paid or flipped.
    assertEquals(armored.ops.count(op => op.isInstanceOf[PayCost] ||
      op.isInstanceOf[FlipSecrets]), 0)
    // Battle Honors would have paid the winning bandit, and now does not.
    val won = plain.finish
    val lost = armored.finish
    assertEquals(ready(won.state).game.current.lastCampaignResult.map(
      _.attackerWins), Some(false))
    assertEquals(orderBank(won.state), orderBank(OathState.Ready(free.ready)) - 2)
    assertEquals(orderBank(lost.state), orderBank(OathState.Ready(free.ready)))
  }

  // ---- the defender holds it: the attacker's plans cost more ---------------

  private def defenderHolds(attackerSecrets: Int): Board = {
    val base = againstPlayer(board())
    secrets(withRelic(withAdviserFor(base, base.other, armor, Orientation.FaceUp),
      brass), base.actor, attackerSecrets)
  }

  test("an attacker's plan costs one more secret, placed onto its card") {
    val b = defenderHolds(2)
    val run = commit(rules(winning), b, 2)
    val option = run.options(b.actor).find(_.ref == DecisionOptionRef.Relic(
      RelicId(brass))).get
    assertEquals(price(option), OptionPrice(secrets = 2))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan,
      DecisionOptionRef.Relic(RelicId(brass)))
    assertEquals(player(picked.state, b.actor).relics.map(_.tokens),
      Vector(Tokens(0, 2)))
    assertEquals(held(picked.state, b.actor), (0, 0))
  }

  test("an attacker with only the secret Brass Army needs cannot pay the added cost, so it is not offered") {
    val b = defenderHolds(1)
    assertEquals(commit(rules(winning), b, 2).continue,
      awaits(b.other, CampaignIds.defenderPlan))
  }

  test("the holder's own plans are not taxed") {
    val base = againstPlayer(board())
    val b = secrets(withAdviserFor(withAdviserFor(base, base.other, armor,
      Orientation.FaceUp), base.other, watchdog, Orientation.FaceUp), base.other, 0)
    val run = commit(rules(losing), b, 4)
    assertEquals(run.options(b.actor).map(price), Vector.fill(3)(OptionPrice()))
  }

  test("the card is registered once and is automatic, as the catalog marks it persistent") {
    val plans = oathdigital.gameplay.powers.WalkerPowerCatalog.default(catalog)
      .powers.filter(_.id == GleamingArmor.id)
    assertEquals(plans.size, 1)
    assertEquals(plans.head.resolution, PowerResolution.Automatic)
  }
}
