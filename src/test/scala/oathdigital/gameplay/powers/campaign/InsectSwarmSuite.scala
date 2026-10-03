package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.Look

/** Insect Swarm: while its faceup holder is in a Campaign, every plan the enemy
  * chooses costs one more favor, burnt from the plan's user's board, whatever
  * the turn. A plan the enemy cannot afford with it is not offered, and the
  * option states the price with it.
  */
class InsectSwarmSuite extends munit.FunSuite:
  private val swarm = cardWith("denizen.insect-swarm")
  private val armor = cardWith("denizen.gleaming-armor")
  private val watchdog = cardWith("denizen.watchdog")
  private val honors = cardWith("denizen.battle-honors")
  private val provisions = cardWith("denizen.extra-provisions")
  private val titleRef: DecisionOptionRef = DecisionOptionRef.Button("title")

  private def ref(card: DenizenId): DecisionOptionRef =
    DecisionOptionRef.Denizen(card)

  private def price(option: DecisionOption): OptionPrice = option match
    case DecisionOption.Priced(_, price) => price
    case _ => OptionPrice()

  private def favor(state: OathState, who: PlayerId): Int =
    Look(state).player(who).board.favor

  private def bank(state: OathState, suit: Suit): Int =
    ready(state).banks.favor.getOrElse(suit, 0)

  private def tokensOn(state: OathState, who: PlayerId, card: DenizenId)
      : Option[Tokens] = Look(state).player(who).advisers.collectFirst {
    case held: DenizenState if held.id == card => held.tokens }

  // ---- the attacker holds it: the defender's plans cost more ---------------

  /** The attacker holds Insect Swarm. The defender holds the title, a Watchdog,
    * which costs nothing of itself, and `defenderFavor` favor.
    */
  private def attackerHolds(defenderFavor: Int): Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withAdviserFor(withAdviser(base, swarm, Orientation.FaceUp), base.rival,
      watchdog, Orientation.FaceUp))(_.favor(base.rival, defenderFavor))

  test("a defender's plan costs one more favor, burnt even though it is paid " +
      "off turn"):
    val b = attackerHolds(1)
    val suit = catalog.suitOf(watchdog).get
    val run = commit(rules(losing), b, 4)
    val option = run.options(b.actor).find(_.ref == ref(watchdog)).get
    assertEquals(price(option), OptionPrice(favorBurnt = 1))
    val picked = run.pick(b.rival, CampaignIds.defenderPlan,
      ref(watchdog))
    assertEquals(favor(picked.state, b.rival), 0)
    // Burnt to the shared bank: not settled into the card's suit bank, and
    // nothing rests on the card.
    assertEquals(bank(picked.state, suit), bank(run.state, suit))
    assertEquals(tokensOn(picked.state, b.rival, watchdog),
      Some(Tokens.empty))

  test("the title's plan burns a favor from its user's board, since it has " +
      "no card"):
    val b = attackerHolds(1)
    val run = commit(rules(losing), b, 4)
    val title = run.options(b.actor).find(_.ref == titleRef).get
    assertEquals(price(title), OptionPrice(favorBurnt = 1))
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, titleRef)
    assertEquals(favor(picked.state, b.rival), 0)

  test("a defender with no favor is offered no plan"):
    val b = attackerHolds(0)
    assert(awaits(commit(rules(losing), b, 4), b.actor, CampaignIds.sacrifice))

  test("a plan whose own cost and the added favor cannot both be paid " +

      "is not offered"):
    val base = attackerHolds(1)
    // Extra Provisions places a favor of its own, so it needs two.
    val b = withAdviserFor(base, base.rival, provisions, Orientation.FaceUp)
    val run = commit(rules(losing), b, 4)
    assert(awaits(run, b.rival, CampaignIds.defenderPlan))
    assert(run.offered(b.actor).contains(titleRef))
    assert(!run.offered(b.actor).contains(ref(provisions)))

  test("a facedown Insect Swarm is not active"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withAdviserFor(withAdviser(base, swarm, Orientation.FaceDown),
      base.rival, watchdog, Orientation.FaceUp))(_.favor(base.rival, 0))
    // The Watchdog, the title's plan and Finish.
    assertEquals(commit(rules(losing), b, 4).options(b.actor).map(price),
      Vector.fill(3)(OptionPrice()))

  test("bandits are enemies too: a bandit defender is taxed, cannot pay, and " +
      "applies no plan"):
    val base = board(warbands = 5, supply = 7)
    val free = withSiteCard(base, base.origin, watchdog)
    val taxed = withAdviser(free, swarm, Orientation.FaceUp)
    // Every pool change but the attacker's: the printed defense, Watchdog's die
    // and the record that the bandit applied it.
    def defenseChanges(run: Run): Int = run.ops.count:
      case ModifyDicePool(pool, _, _) => pool != CampaignIds.attackPool
      case _ => false
    val plain = commit(rules(losing), free, 2)
    val swarmed = commit(rules(losing), taxed, 2)
    assert(awaits(plain, free.actor, CampaignIds.sacrifice))
    assert(awaits(swarmed, taxed.actor, CampaignIds.sacrifice))
    assertEquals(defenseChanges(plain) - defenseChanges(swarmed), 2)
    assertEquals(swarmed.ops.count(_.isInstanceOf[PayCost]), 0)

  // ---- the defender holds it: the attacker's plans cost more ---------------

  /** The other player holds Insect Swarm. The attacker holds Battle Honors,
    * which costs nothing of itself, and `attackerFavor` favor.
    */
  private def defenderHolds(attackerFavor: Int): Board =
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    on(withAdviser(withAdviserFor(base, base.rival, swarm, Orientation.FaceUp),
      honors, Orientation.FaceUp))(_.favor(base.actor, attackerFavor))

  test("an attacker's plan costs one more favor, burnt from " +

      "the attacker's board"):
    val b = defenderHolds(1)
    val run = commit(rules(winning), b, 4)
    val option = run.options(b.actor).find(_.ref == ref(honors)).get
    assertEquals(price(option), OptionPrice(favorBurnt = 1))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref(honors))
    assertEquals(favor(picked.state, b.actor), 0)
    assertEquals(tokensOn(picked.state, b.actor, honors),
      Some(Tokens.empty))

  test("with Gleaming Armor on the same side, both added costs apply"):
    val base = defenderHolds(1)
    val b = on(withAdviserFor(base, base.rival, armor, Orientation.FaceUp))(
      _.secrets(base.actor, faceUp = 1))
    val run = commit(rules(winning), b, 4)
    val option = run.options(b.actor).find(_.ref == ref(honors)).get
    assertEquals(price(option), OptionPrice(secrets = 1, favorBurnt = 1))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref(honors))
    assertEquals(favor(picked.state, b.actor), 0)
    assertEquals(Look(picked.state).player(b.actor).board.faceUpSecrets, 0)
    // Gleaming Armor's secret is placed onto the card; the favor is burnt.
    assertEquals(tokensOn(picked.state, b.actor, honors),
      Some(Tokens(0, 1)))

  test("the holder's own plans are not taxed"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = on(withAdviserFor(withAdviserFor(base, base.rival, swarm,
      Orientation.FaceUp), base.rival, watchdog, Orientation.FaceUp))(
      _.favor(base.rival, 0))
    // The Watchdog, the title's plan and Finish.
    assertEquals(commit(rules(losing), b, 4).options(b.actor).map(price),
      Vector.fill(3)(OptionPrice()))

  // ---- Lines ----

  private val power = InsectSwarm.forCatalog(catalog)
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("a taxed plan writes the Swarm's line, naming the plan's user"):
    val b = attackerHolds(1)
    val run = commit(rules(losing), b, 4)
    // Pricing the offered plans writes nothing: only a plan applied does.
    assertEquals(said(run.events), Vector.empty)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan,
      ref(watchdog))
    assertEquals(said(picked.events), Vector(NoteText.Said("taxed",
      s"${b.rival.value}'s battle plans cost 1 extra favor, burnt.",
      covers = false)))
