package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.ActPowerHarness.usableIds
import oathdigital.testkit.Outcomes.{assertWireRoundTrips, ready}

class BrassHorseSuite extends munit.FunSuite:
  import PowerFixture._
  import MovementFixture._
  import PhasePowerHarness.{awaits, backToActing, ops, replayed}

  private val horse = RelicId("R03")
  private def staged = inPhase(Table.from(withRelic(homelands(quietTable),
    horse)).secrets(actor, 2).unchecked, Phase.Act)
  // Three denizens no site, hand or discard of the quiet table holds.
  private val beastTop = CatalogNames.denizen("Insect Swarm")
  private val beastElsewhere = CatalogNames.denizen("Vow of Union")
  private val nomadTop = CatalogNames.denizen("Oracle")
  private def cradleTopped(card: WorldCardId) =
    Table.from(staged).discarded(Region.Cradle, card).unchecked
  private def site(name: String) = DecisionOptionRef.Site(CatalogNames.site(
    name))
  private def reveals(events: Vector[OathEvent]) = ops(events).collect {
    case reveal: Reveal => reveal }

  // `homelands` stands a ruined beast edifice at Deep Woods and a ruined
  // hearth edifice at Golden Valley, and no other card lies at any site.

  test("one matching site takes the pawn there without a question"):
    val start = cradleTopped(beastTop)
    val done = use(start, BrassHorse.id, horse).toOption.get
    val after = ready(done.state)
    assert(backToActing(done))
    assertEquals(Look(after).pawn(actor), CatalogNames.site("Deep Woods"))
    assertEquals(relicOf(after, horse).get.tokens, Tokens(0, 1))
    assertEquals(after.game.current.commonCards.discard(Region.Cradle).last,
      beastTop)
    assertEquals(reveals(done.events), Vector(
      Reveal(beastTop, Location.RegionalDiscard(Region.Cradle))))
    // The reveal is public: every player now knows the card.
    after.game.current.players.map(_.player).foreach(player =>
      assert(after.knowledge.advisers.getOrElse(player, Vector.empty)
        .contains(beastTop), player))
    assertWireRoundTrips(done.events)
    assertEquals(replayed(start, done.events), Right(done.state))

  test("several matching sites ask, and only they are offered"):
    val start = atSite(cradleTopped(beastTop), beastElsewhere,
      CatalogNames.site("Dunes"))
    val parked = use(start, BrassHorse.id, horse).toOption.get
    assert(awaits(parked, BrassHorse.decisionId))
    assert(choose(parked.state, BrassHorse.decisionId, site("Buried Giant"))
      .isLeft)
    val done = choose(parked.state, BrassHorse.decisionId, site("Dunes"))
      .toOption.get
    assert(backToActing(done))
    assertEquals(Look(ready(done.state)).pawn(actor),
      CatalogNames.site("Dunes"))
    assertEquals(replayed(start, parked.events ++ done.events), Right(done.state))

  test("the current site never counts as a match"):
    val start = atSite(cradleTopped(beastTop), beastElsewhere,
      CatalogNames.site("Ancient City"))
    val done = use(start, BrassHorse.id, horse).toOption.get
    assert(backToActing(done))
    assertEquals(Look(ready(done.state)).pawn(actor),
      CatalogNames.site("Deep Woods"))

  test("with no matching site any other site may be chosen"):
    val start = cradleTopped(nomadTop)
    val parked = use(start, BrassHorse.id, horse).toOption.get
    assert(awaits(parked, BrassHorse.decisionId))
    assert(choose(parked.state, BrassHorse.decisionId, site("Ancient City"))
      .isLeft)
    val done = choose(parked.state, BrassHorse.decisionId,
      site("Desolate Shore")).toOption.get
    assertEquals(Look(ready(done.state)).pawn(actor),
      CatalogNames.site("Desolate Shore"))

  test("an empty pile reveals nothing, so the power cannot be used"):
    // The quiet table's Cradle discard is empty.
    val start = staged
    assert(!usableIds(start).contains(BrassHorse.id))
    assert(use(start, BrassHorse.id, horse).isLeft)

  test("a Vision on top has no suit, so any other site may be chosen"):
    val vision = VisionRules.Sanctuary
    val start = cradleTopped(vision)
    val parked = use(start, BrassHorse.id, horse).toOption.get
    assert(awaits(parked, BrassHorse.decisionId))
    assertEquals(reveals(parked.events),
      Vector(Reveal(vision, Location.RegionalDiscard(Region.Cradle))))
    val done = choose(parked.state, BrassHorse.decisionId, site("Deep Woods"))
      .toOption.get
    assertEquals(Look(ready(done.state)).pawn(actor),
      CatalogNames.site("Deep Woods"))

  test("the region is the one the pawn's site is in"):
    val provincesTop = beastTop
    val start = Table.from(staged).pawn(actor, "Buried Giant")
      .discarded(Region.Provinces, provincesTop).unchecked
    val done = use(start, BrassHorse.id, horse).toOption.get
    assertEquals(reveals(done.events), Vector(
      Reveal(provincesTop, Location.RegionalDiscard(Region.Provinces))))
    assertEquals(Look(ready(done.state)).pawn(actor),
      CatalogNames.site("Deep Woods"))

  test("it is unusable without a secret"):
    val id = BrassHorse.id
    val start = cradleTopped(beastTop)
    assert(usableIds(start).contains(id))
    assert(!usableIds(Table.from(start).secrets(actor, 0).unchecked)
      .contains(id))
    assert(use(Table.from(start).secrets(actor, 0).unchecked, id, horse).isLeft)

  test("it writes the card it revealed in place of the generic line, then where the pawn went"):
    val done = use(cradleTopped(beastTop), BrassHorse.id, horse).toOption.get
    assertEquals(NoteText.said(new BrassHorse(catalog), done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} revealed ${beastTop.value}.",
        covers = true),
      NoteText.Said("placed",
        s"${actor.value} placed at ${CatalogNames.site("Deep Woods").value}.",
        covers = false)))
