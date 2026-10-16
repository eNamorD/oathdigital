package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Signal Trees: a secret placed; the top three cards of the user's
  * region's discard pile are revealed and discarded, for two attack dice
  * per Beast. */
class SignalTreesSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Signal Trees")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val power = SignalTrees.forCatalog(catalog)
  private val wolves = CatalogNames.denizen("Wolves")
  private val swarm = CatalogNames.denizen("Insect Swarm")
  private val songs = CatalogNames.denizen("Tavern Songs")
  private val oracle = CatalogNames.denizen("Oracle")
  private val homesteaders = CatalogNames.denizen("Homesteaders")
  // The origin, Ancient City, is in the Cradle.
  private val next = CardPlay.nextRegion(Region.Cradle)

  /** p1 attacks the bandit-ruled origin with 5 warbands, holding Signal
    * Trees faceup and one faceup secret, with `pile` in the Cradle's
    * discard pile, the last on top. */
  private def attacker(pile: DenizenId*): Board =
    val b = withSecrets(withAdviser(board(warbands = 5, supply = 7), card,
      Orientation.FaceUp), faceUp = 1)
    on(b)(_.discarded(Region.Cradle, pile*))

  private def everyone(b: Board): Vector[PlayerId] =
    Vector(b.actor, b.rival, b.bystander)

  private def line(key: String, text: String): NoteText.Said =
    NoteText.Said(key, text, covers = true)

  test("an attacker reveals the top three and adds two dice per Beast"):
    assertEquals(Vector(wolves, swarm, songs).map(catalog.suitOf),
      Vector(Some(Suit.Beast), Some(Suit.Beast), Some(Suit.Hearth)))
    val b = attacker(oracle, swarm, songs, wolves)
    val run = commit(rules(winningDice), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(
      ModifyDicePool(CampaignIds.attackPool, 4)))
    val after = Look(picked.state)
    assertEquals(after.discardPile(Region.Cradle), Vector(oracle))
    // Drawn top first, so the first drawn lies deepest.
    assertEquals(after.discardPile(next), Vector(wolves, songs, swarm))
    for shown <- Vector(wolves, songs, swarm); viewer <- everyone(b) do
      assert(after.knowsAdviser(viewer, shown), s"$viewer misses $shown")
    assertEquals(said(power, picked.events), Vector(line("added",
      s"Revealed and discarded ${wolves.value}, ${songs.value}, " +
        s"${swarm.value}; added 4 attack dice.")))

  test("an attacker campaigning from an origin reveals the origin region's " +
      "pile"):
    // The origin, Buried Giant, lies outside the pawn's Cradle, whose pile
    // holds no Beast.
    val (base, away) = rivalAloneAtASiteTheActorHolds(board = 2, atSite = 1)
    val originRegion = base.ready.game.current.map.regionOf(away).get
    assertNotEquals(originRegion, Region.Cradle)
    val b = on(withSecrets(withAdviser(base, card, Orientation.FaceUp),
      faceUp = 1))(_.discarded(Region.Cradle, homesteaders)
      .discarded(originRegion, oracle, swarm, songs, wolves))
    val game = rules(winningDice)
    val run = committed(game, campaignQueuedFrom(game, b, away,
      PowerSourceRef.Card(CatalogNames.denizen("Wild Allies"))), b,
      force = 2, raid = true)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(
      ModifyDicePool(CampaignIds.attackPool, 4)))
    assertEquals(Look(picked.state).discardPile(originRegion), Vector(oracle))
    assertEquals(Look(picked.state).discardPile(Region.Cradle),
      Vector(homesteaders))

  // A dry run records no event and leaves no state, so no test can see a
  // reveal in one. A reveal wired outside the chosen plan, as a rewrite or
  // a window hook, would run unchosen; this test catches that.
  test("unchosen, it reveals and moves nothing up to the Campaign's end"):
    val b = attacker(oracle, swarm, songs, wolves)
    def untouched(state: OathState): Unit =
      val now = Look(state)
      for hidden <- Vector(wolves, songs, swarm); viewer <- everyone(b) do
        assert(!now.knowsAdviser(viewer, hidden), s"$viewer knows $hidden")
      assertEquals(now.discardPile(Region.Cradle),
        Vector(oracle, swarm, songs, wolves))
    val run = commit(rules(winningDice), b, 2)
    assert(run.offers(b.actor, b.actor, CampaignIds.attackerPlan, ref))
    untouched(run.state)
    val done = run.finishPlanWindows
    assert(done.winner.nonEmpty)
    untouched(done.state)
    assertEquals(said(power, done.events), Vector.empty)

  test("a short pile reveals what it has"):
    val b = attacker(songs, wolves)
    val run = commit(rules(winningDice), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(
      ModifyDicePool(CampaignIds.attackPool, 2)))
    assertEquals(Look(picked.state).discardPile(Region.Cradle), Vector.empty)
    assertEquals(Look(picked.state).discardPile(next), Vector(wolves, songs))
    assertEquals(said(power, picked.events), Vector(line("added",
      s"Revealed and discarded ${wolves.value}, ${songs.value}; added 2 " +
        "attack dice.")))

  test("with no Beast revealed the cards are discarded and no dice change"):
    assertEquals(Vector(oracle, songs).map(catalog.suitOf).flatten
      .contains(Suit.Beast), false)
    val b = attacker(oracle, songs)
    val run = commit(rules(winningDice), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(!picked.since(run).exists {
      case ModifyDicePool(pool, _, _) => pool == CampaignIds.attackPool
      case _ => false })
    assertEquals(Look(picked.state).discardPile(Region.Cradle), Vector.empty)
    assertEquals(Look(picked.state).discardPile(next), Vector(songs, oracle))
    assertEquals(said(power, picked.events), Vector(line("revealed",
      s"Revealed and discarded ${songs.value}, ${oracle.value}.")))

  test("a defender removes two dice per Beast"):
    val base = withEnemyAtOrigin(rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7)))
    val b = on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.secrets(base.rival, faceUp = 1)
        .discarded(Region.Cradle, oracle, wolves))
    val run = commit(rules(winningDice), b, 4)
    val picked = run.pick(b.rival, CampaignIds.defenderPlan, ref)
    assert(picked.since(run).contains(
      ModifyDicePool(CampaignIds.attackPool, -2)))
    assertEquals(said(power, picked.events), Vector(line("removed",
      s"Revealed and discarded ${wolves.value}, ${oracle.value}; removed 2 " +
        "attack dice.")))

  test("an empty pile reveals nothing"):
    val b = attacker()
    val run = commit(rules(winningDice), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(!picked.since(run).exists {
      case ModifyDicePool(pool, _, _) => pool == CampaignIds.attackPool
      case _ => false })
    assertEquals(said(power, picked.events), Vector(NoteText.Said("none",
      "Nothing was revealed.", covers = false)))
