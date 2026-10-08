package oathdigital.gameplay.powers.action

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Refusals,
  Table}
import oathdigital.testkit.ActPowerHarness.usableIds
import oathdigital.testkit.Outcomes.{assertWireRoundTrips, ready}
import oathdigital.testkit.Unwrap.accepted

class BrassHorseSuite extends munit.FunSuite:
  import PowerFixture._
  import MovementFixture._
  import PhasePowerHarness.{awaits, backToActing, ops, replayed}

  private val horse = CatalogNames.relic("Brass Horse")

  /** p1's Act at Ancient City, in the Cradle, holding Brass Horse with 2
    * secrets; Buried Giant lies in the Provinces. `homelands` stands a
    * ruined Beast edifice at Deep Woods and a ruined Hearth one at Golden
    * Valley, and no other card lies at any site. */
  private def staged = Table.from(homelands(quietTable)).turn(actor, Phase.Act)
    .pawn(actor, "Ancient City").relic(actor, horse).secrets(actor, 2)
    .unchecked
  // Three denizens no site, hand or discard of the quiet table holds.
  private val beastTop = CatalogNames.denizen("Insect Swarm")
  private val beastElsewhere = CatalogNames.denizen("Vow of Union")
  private val nomadTop = CatalogNames.denizen("Oracle")
  private def cradleTopped(card: WorldCardId) =
    Table.from(staged).discarded(Region.Cradle, card).unchecked
  private def site(name: String) =
    DecisionOptionRef.Site(CatalogNames.site(name))
  private def reveals(events: Vector[OathEvent]) = ops(events).collect {
    case reveal: Reveal => reveal }

  test("one matching site takes the pawn there without a question"):
    val start = cradleTopped(beastTop)
    val done = accepted(use(start, BrassHorse.id, horse))
    val after = ready(done.state)
    assert(backToActing(done))
    assertEquals(Look(after).pawn(actor), CatalogNames.site("Deep Woods"))
    assertEquals(Look(after).tokensOn(horse), Tokens(0, 1))
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
    val parked = accepted(use(start, BrassHorse.id, horse))
    assert(awaits(parked, BrassHorse.decisionId))
    assertEquals(
      choose(parked.state, BrassHorse.decisionId, site("Buried Giant"))
        .left.toOption,
      Some(Refusals.notOffered(BrassHorse.decisionId)))
    val done = accepted(choose(parked.state, BrassHorse.decisionId,
      site("Dunes")))
    assert(backToActing(done))
    assertEquals(Look(ready(done.state)).pawn(actor),
      CatalogNames.site("Dunes"))
    assertEquals(replayed(start, parked.events ++ done.events), Right(done.state))

  test("the current site never counts as a match"):
    val start = atSite(cradleTopped(beastTop), beastElsewhere,
      CatalogNames.site("Ancient City"))
    val done = accepted(use(start, BrassHorse.id, horse))
    assert(backToActing(done))
    assertEquals(Look(ready(done.state)).pawn(actor),
      CatalogNames.site("Deep Woods"))

  test("with no matching site any other site may be chosen"):
    val start = cradleTopped(nomadTop)
    val parked = accepted(use(start, BrassHorse.id, horse))
    assert(awaits(parked, BrassHorse.decisionId))
    assertEquals(
      choose(parked.state, BrassHorse.decisionId, site("Ancient City"))
        .left.toOption,
      Some(Refusals.notOffered(BrassHorse.decisionId)))
    val done = accepted(choose(parked.state, BrassHorse.decisionId,
      site("Desolate Shore")))
    assertEquals(Look(ready(done.state)).pawn(actor),
      CatalogNames.site("Desolate Shore"))

  test("an empty pile reveals nothing, so the power cannot be used"):
    // The quiet table's Cradle discard is empty.
    val start = staged
    assert(!usableIds(start).contains(BrassHorse.id))
    assertEquals(use(start, BrassHorse.id, horse).left.toOption,
      Some(Refusals.nothingToDo(BrassHorse.id,
        DecisionOptionRef.Relic(horse))))

  test("a Vision on top has no suit, so any other site may be chosen"):
    val vision = VisionRules.Sanctuary
    val start = cradleTopped(vision)
    val parked = accepted(use(start, BrassHorse.id, horse))
    assert(awaits(parked, BrassHorse.decisionId))
    assertEquals(reveals(parked.events),
      Vector(Reveal(vision, Location.RegionalDiscard(Region.Cradle))))
    val done = accepted(choose(parked.state, BrassHorse.decisionId,
      site("Deep Woods")))
    assertEquals(Look(ready(done.state)).pawn(actor),
      CatalogNames.site("Deep Woods"))

  test("the region is the one the pawn's site is in"):
    val provincesTop = beastTop
    val start = Table.from(staged).pawn(actor, "Buried Giant")
      .discarded(Region.Provinces, provincesTop).unchecked
    val done = accepted(use(start, BrassHorse.id, horse))
    assertEquals(reveals(done.events), Vector(
      Reveal(provincesTop, Location.RegionalDiscard(Region.Provinces))))
    assertEquals(Look(ready(done.state)).pawn(actor),
      CatalogNames.site("Deep Woods"))

  test("it is unusable without a secret"):
    val id = BrassHorse.id
    val start = cradleTopped(beastTop)
    val broke = Table.from(start).secrets(actor, 0).unchecked
    assert(usableIds(start).contains(id))
    assert(!usableIds(broke).contains(id))
    assertEquals(use(broke, id, horse).left.toOption,
      Some(OathViolation.InsufficientSecrets(1, 0)))

  test("it writes the card it revealed in place of the generic line, then where the pawn went"):
    val done = accepted(use(cradleTopped(beastTop), BrassHorse.id, horse))
    assertEquals(NoteText.said(new BrassHorse(catalog), done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} revealed ${beastTop.value}.",
        covers = true),
      NoteText.Said("placed",
        s"${actor.value} placed at ${CatalogNames.site("Deep Woods").value}.",
        covers = false)))
