package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p3

class WanderingFlameMoveSuite extends munit.FunSuite:
  import PowerFixture._
  import BannerFixture._
  import PhasePowerHarness.{after, answer, awaits, backToActing, ops, pick,
    replayed, use}
  import ActPowerHarness.usableIds

  private val power = WanderingFlameMove

  /** The actor holds the banner in the Act phase, and exactly one secret lies
    * on each site in `marked`.
    */
  private def staged(marked: SiteId*): ReadyGame = inPhase(
    marked.foldLeft(Table.from(quietTable).darkestSecret(Some(actor),
      1).unchecked)(
      ((r, site) => Table.from(r).siteTokens(site,
        secrets = 1).unchecked)), Phase.Act)

  test("one secret-bearing site: the pawn goes there with no question, by a " +
      "plain Move that leaves the secret where it is"):
    val start = staged(CatalogNames.site("Broken Peaks"))
    val done = use(start, power, darkestSecretOption).toOption.get
    assert(backToActing(done))
    assertEquals(Look(after(done)).pawn(actor),
      CatalogNames.site("Broken Peaks"))
    assertEquals(Look(after(done)).siteTokens(CatalogNames.site("Broken Peaks"))
      .secrets, 1)
    assertEquals(ops(done.events), Vector[CoreOperation](
      Move(Piece.Pawn(actor),
        PositionedLocation(Location.Site(CatalogNames.site("Ancient City"))),
        PositionedLocation(Location.Site(CatalogNames.site("Broken Peaks"))))))
    assertEquals(replayed(start, done.events), Right(done.state))
    assertWireRoundTrips(done.events)

  test("several candidates: the player chooses among exactly the other " +
      "sites that hold a secret"):
    val start = staged(CatalogNames.site("Broken Peaks"),
      CatalogNames.site("Buried Giant"), CatalogNames.site("Ancient City"))
    val parked = use(start, power, darkestSecretOption).toOption.get
    assert(awaits(parked, power.decisionId))
    assert(answer(parked, actor, power.decisionId,
      pick(DecisionOptionRef.Site(CatalogNames.site("Ancient City"))))
        .isLeft, "the pawn's own site")
    assert(answer(parked, actor, power.decisionId,
      pick(DecisionOptionRef.Site(CatalogNames.site("Deep Woods"))))
        .isLeft, "a site with no secret")
    val done = answer(parked, actor, power.decisionId,
      pick(DecisionOptionRef.Site(CatalogNames.site("Buried Giant"))))
        .toOption.get
    assert(backToActing(done))
    assertEquals(Look(after(done)).pawn(actor),
      CatalogNames.site("Buried Giant"))
    assertEquals(replayed(start, parked.events ++ done.events),
      Right(done.state))

  test("a secret on a card at a site does not count, only one on the site"):
    val card = DenizenId("7")
    val onCard = Table.from(atSite(staged(), card,
      CatalogNames.site("Broken Peaks"))).tokens(card, Tokens(0, 1)
      .favor, Tokens(0, 1).secrets).unchecked
    assert(!usableIds(onCard).contains(power.id))
    assert(usableIds(Table.from(onCard)
      .siteTokens(CatalogNames.site("Broken Peaks"),
      secrets = 1).unchecked).contains(power.id))

  test("it is unlimited: it can be used again in the same turn"):
    val start = staged(CatalogNames.site("Broken Peaks"),
      CatalogNames.site("Buried Giant"))
    val first = use(start, power, darkestSecretOption).toOption.get
    val parked = answer(first, actor, power.decisionId,
      pick(DecisionOptionRef.Site(CatalogNames.site("Broken Peaks"))))
        .toOption.get
    val second = use(after(parked), power, darkestSecretOption)
    assert(second.isRight, second.toString)
    assertEquals(after(parked).game.current.turn.usedPowers, Set.empty[PowerUseRef])

  test("it is unusable with no other site holding a secret, without the " +
      "banner, or on the Festival face"):
    assert(!usableIds(staged()).contains(power.id))
    assert(use(staged(), power, darkestSecretOption).isLeft)
    assert(!usableIds(staged(CatalogNames.site("Ancient City")))
      .contains(power.id), "only the pawn's own site")
    assert(usableIds(staged(CatalogNames.site("Broken Peaks")))
      .contains(power.id))
    val notHeld = inPhase(Table.from(quietTable).darkestSecret(None,
      1).siteTokens(CatalogNames.site("Broken Peaks"), secrets = 1)
      .unchecked, Phase.Act)
    assert(!usableIds(notHeld).contains(power.id))
    assert(use(notHeld, power, darkestSecretOption).isLeft)
    val theirs = inPhase(Table.from(quietTable).darkestSecret(Some(p3),
      1).siteTokens(CatalogNames.site("Broken Peaks"), secrets = 1)
      .unchecked, Phase.Act)
    assert(!usableIds(theirs).contains(power.id))
    val festival = inPhase(Table.from(quietTable).darkestSecret(Some(actor), 1,
      DarkestSecretFace.Festival)
      .siteTokens(CatalogNames.site("Broken Peaks"),
      secrets = 1).unchecked, Phase.Act)
    assert(!usableIds(festival).contains(power.id))
    assert(use(festival, power, darkestSecretOption).isLeft)

  test("it is usable in the Act phase only"):
    val wake = inPhase(Table.from(quietTable).darkestSecret(Some(actor),
      1).siteTokens(CatalogNames.site("Broken Peaks"), secrets = 1).unchecked,
      Phase.Wake)
    assert(!usableIds(wake).contains(power.id))

  test("it writes where the pawn went"):
    val done = use(staged(CatalogNames.site("Broken Peaks")), power,
      darkestSecretOption).toOption.get
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} placed at ${CatalogNames.site("Broken Peaks").value}.",
      covers = false)))
