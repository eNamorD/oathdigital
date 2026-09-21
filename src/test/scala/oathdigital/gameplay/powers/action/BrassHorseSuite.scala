package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class BrassHorseSuite extends munit.FunSuite {
  import PowerFixture._
  import MovementFixture._
  import TargetsFixture.{replayed, withPawn}

  private val horse = RelicId("R03")
  private def staged = inPhase(withSecrets(withRelic(base, horse), 2), Phase.Act)
  private val beastTop = freshDenizen(staged, Suit.Beast)
  private val beastElsewhere = freshDenizen(staged, Suit.Beast, skip = 1)
  private val nomadTop = freshDenizen(staged, Suit.Nomad)
  private def cradleTopped(card: WorldCardId) =
    withDiscard(staged, Region.Cradle, Vector(card))
  private def site(id: SiteId) = DecisionOptionRef.Site(id)
  private def reveals(events: Vector[OathEvent]) = ops(events).collect {
    case reveal: Reveal => reveal }

  // The first game puts a ruined beast edifice at deep-woods and a ruined
  // hearth edifice at golden-valley, and no other card at any site.

  test("Brass Horse is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(BrassHorse.id).isDefined)
    assert(usable(staged, BrassHorse.id))
  }

  test("one matching site takes the pawn there without a question") {
    val start = cradleTopped(beastTop)
    val done = use(start, BrassHorse.id, horse).toOption.get
    val after = readyOf(done.state)
    assert(backToActing(done))
    assertEquals(pawnOf(after), deepWoods)
    assertEquals(relicOf(after, horse).get.tokens, Tokens(0, 1))
    assertEquals(after.game.current.commonCards.discard(Region.Cradle).last,
      beastTop)
    assertEquals(reveals(done.events), Vector(
      Reveal(beastTop, Location.RegionalDiscard(Region.Cradle))))
    assertEquals(after.knowledge, start.knowledge)
    assert(PaidActionHarness.wireRoundTrips(done.events))
    assertEquals(replayed(start, done.events), Right(done.state))
  }

  test("several matching sites ask, and only they are offered") {
    val start = atSite(cradleTopped(beastTop), beastElsewhere, dunes)
    val parked = use(start, BrassHorse.id, horse).toOption.get
    assert(parkedAt(parked, BrassHorse.decisionId))
    assert(choose(parked.state, BrassHorse.decisionId, site(buriedGiant)).isLeft)
    val done = choose(parked.state, BrassHorse.decisionId, site(dunes)).toOption.get
    assert(backToActing(done))
    assertEquals(pawnOf(readyOf(done.state)), dunes)
    assertEquals(replayed(start, parked.events ++ done.events), Right(done.state))
  }

  test("the current site never counts as a match") {
    val start = atSite(cradleTopped(beastTop), beastElsewhere, ancientCity)
    val done = use(start, BrassHorse.id, horse).toOption.get
    assert(backToActing(done))
    assertEquals(pawnOf(readyOf(done.state)), deepWoods)
  }

  test("with no matching site any other site may be chosen") {
    val start = cradleTopped(nomadTop)
    val parked = use(start, BrassHorse.id, horse).toOption.get
    assert(parkedAt(parked, BrassHorse.decisionId))
    assert(choose(parked.state, BrassHorse.decisionId, site(ancientCity)).isLeft)
    val done = choose(parked.state, BrassHorse.decisionId,
      site(desolateShore)).toOption.get
    assertEquals(pawnOf(readyOf(done.state)), desolateShore)
  }

  test("an empty pile reveals nothing and any other site may be chosen") {
    val start = withDiscard(staged, Region.Cradle, Vector.empty)
    val parked = use(start, BrassHorse.id, horse).toOption.get
    assert(parkedAt(parked, BrassHorse.decisionId))
    assertEquals(reveals(parked.events), Vector.empty)
    assert(choose(parked.state, BrassHorse.decisionId, site(dunes)).isRight)
  }

  test("a Vision on top has no suit, so any other site may be chosen") {
    val vision = aVision(staged)
    val start = cradleTopped(vision)
    val parked = use(start, BrassHorse.id, horse).toOption.get
    assert(parkedAt(parked, BrassHorse.decisionId))
    assertEquals(reveals(parked.events),
      Vector(Reveal(vision, Location.RegionalDiscard(Region.Cradle))))
    assert(choose(parked.state, BrassHorse.decisionId, site(deepWoods)).isRight)
  }

  test("the region is the one the pawn's site is in") {
    val provincesTop = freshDenizen(staged, Suit.Beast)
    val start = withDiscard(withPawn(staged, actor, buriedGiant),
      Region.Provinces, Vector(provincesTop))
    val done = use(start, BrassHorse.id, horse).toOption.get
    assertEquals(reveals(done.events), Vector(
      Reveal(provincesTop, Location.RegionalDiscard(Region.Provinces))))
    assertEquals(pawnOf(readyOf(done.state)), deepWoods)
  }

  test("it is unusable without a secret, when occupied, or facedown") {
    val id = BrassHorse.id
    assert(!usable(withSecrets(staged, 0), id))
    assert(use(withSecrets(staged, 0), id, horse).isLeft)
    assert(!usable(withRelicTokens(staged, horse, Tokens(0, 1)), id))
    val facedown = inPhase(withSecrets(
      withRelic(base, horse, Orientation.FaceDown), 2), Phase.Act)
    assert(!usable(facedown, id))
  }
}
