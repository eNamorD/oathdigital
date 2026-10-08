package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay._
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PhasePowerHarness,
  Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.rejected

class DazzleSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = Dazzle.forCatalog(catalog)
  private val dazzle = power.cardId
  private val homesteaders = CatalogNames.denizen("Homesteaders")
  private val wrestlers = CatalogNames.denizen("Wrestlers")

  /** p1 holds Dazzle faceup. */
  private val holding = Table.start.adviser(p1, dazzle)

  /** Homesteaders (Hearth, with 1 favor on it) and Wrestlers (Order) stand
    * at p1's site, and the Hearth bank holds 3.
    */
  private val twoTargets: ReadyGame = holding
    .denizen(homesteaders, at = homeSite).tokens(homesteaders, favor = 1)
    .denizen(wrestlers, at = homeSite)
    .bankFavor(Suit.Hearth, 3).ready

  private def discarded(events: Vector[OathEvent]): Vector[DenizenId] =
    PhasePowerHarness.ops(events).collect { case d: Discard.Denizen => d.card }

  test("Dazzle discards Hearth and Order site cards from the actor region"):
    val done = Outcomes.finished(play(twoTargets, power, dazzle))
    val left = Look(done.treeless).cardsAt(homeSite)
    assert(!left.contains(homesteaders) && !left.contains(wrestlers), left)
    assertEquals(discarded(done.events), Vector(homesteaders, wrestlers))
    assertJournalRebuilds(twoTargets, done.events, done.treeless)
    val asAdviser = RuleSourceRef.Adviser(p1, dazzle)
    val ignored = PowerRuntime.ignoredAtSource(catalog, twoTargets, p1,
      ActionKind.WhenPlayed, asAdviser)
    assertEquals(ignored, Right(Vector.empty))

  test("Dazzle returns a discarded card's favor to its bank"):
    val done = Outcomes.finished(play(twoTargets, power, dazzle))
    // Homesteaders' 1 favor goes back to the Hearth bank: 3 + 1.
    assertEquals(done.treeless.banks.favor(Suit.Hearth), 3 + 1)

  test("Dazzle writes the cards it discarded"):
    val done = Outcomes.finished(play(twoTargets, power, dazzle))
    assertEquals(said(power, done.events), Vector(NoteText.Said("discarded",
      s"Discarded ${homesteaders.value}, ${wrestlers.value}.", covers = true)))

  test("a second Dazzle finds nothing left to discard, and says so"):
    val once = Outcomes.finished(play(twoTargets, power, dazzle)).treeless
    val again = Outcomes.finished(play(once, power, dazzle))
    assertEquals(said(power, again.events),
      Vector(NoteText.Said("none", "Nothing was discarded.", covers = true)))

  test("Dazzle skips a rule-immune target and still discards another"):
    // p2 rules the other Cradle site with 1 warband, and the Hall of
    // Ministers there stops its ruler's enemies discarding there.
    val ready = holding
      .warbandsAt(homeSite, p1, 1).denizen(homesteaders, at = homeSite)
      .warbandsAt(nearSite, p2, 1).denizen(wrestlers, at = nearSite)
      .edifice("Hall of Ministers", EdificeSide.Intact, at = nearSite).ready
    val done = Outcomes.finished(play(ready, power, dazzle))
    assert(!Look(done.treeless).cardsAt(homeSite).contains(homesteaders))
    assert(Look(done.treeless).cardsAt(nearSite).contains(wrestlers))
    assertEquals(discarded(done.events), Vector(homesteaders))

  test("Dazzle rejects a site denizen absent from the catalog"):
    // No Table step places a card the catalog lacks.
    val unknown = DenizenId("denizen:missing-from-catalog")
    val ready = Table.start.ready.updateCurrent(c => c.copy(map = c.map.copy(
      sites = c.map.sites.updated(homeSite, c.map.sites(homeSite).copy(
        denizens = c.map.sites(homeSite).denizens :+
          DenizenState(unknown, Orientation.FaceUp, Tokens.empty))))))
    assertEquals(rejected(play(ready, power, dazzle)),
      OathViolation.UnknownWorldCard(unknown))

  test("Dazzle discards a ruined Hearth edifice, not an intact one or " +
      "another suit's"):
    val emptyGrounds = CatalogNames.edifice("Empty Grounds")
    val spiralCastle = CatalogNames.edifice("Spiral Castle")
    val ruinedSchool = CatalogNames.edifice("Ruined School")
    val ready = holding
      .edifice("Empty Grounds", EdificeSide.Ruined, at = homeSite)
      .edifice("Spiral Castle", EdificeSide.Intact, at = homeSite)
      .edifice("Ruined School", EdificeSide.Ruined, at = homeSite).ready
    val after = Outcomes.finished(play(ready, power, dazzle)).treeless
    val remaining = Look(after).cardsAt(homeSite)
    assert(!remaining.contains(emptyGrounds),
      "a ruined Hearth edifice is discarded")
    assert(remaining.contains(spiralCastle), "an intact edifice is locked")
    assert(remaining.contains(ruinedSchool), "a Beast edifice is not discarded")
    assertEquals(after.game.current.commonCards.edificeDeck.lastOption,
      Some(emptyGrounds))

  test("Dazzle leaves other suits and other regions alone"):
    val errandBoy = CatalogNames.denizen("Errand Boy")
    val landWarden = CatalogNames.denizen("Land Warden")
    val ready = holding
      .denizen(errandBoy, at = homeSite)
      .denizen(homesteaders, at = homeSite)
      .denizen(landWarden, at = awaySite).ready
    val after = Look(Outcomes.finished(play(ready, power, dazzle)).treeless)
    assert(after.cardsAt(homeSite).contains(errandBoy),
      "a Beast card in the region is not discarded")
    assert(!after.cardsAt(homeSite).contains(homesteaders),
      "a Hearth card in the region is discarded")
    assert(after.cardsAt(awaySite).contains(landWarden),
      "a Hearth card in another region is not discarded")
