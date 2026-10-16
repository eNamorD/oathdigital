package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class ResettleSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val resettle = CatalogNames.denizen("Resettle")
  private val source = DecisionOptionRef.Denizen(resettle)
  private val power = Resettle.forCatalog(catalog)
  /** p1's pawn site, with room for three cards. */
  private val home = CatalogNames.site("Ancient City")
  /** Two sites with room for one card each. */
  private val peaks = CatalogNames.site("Broken Peaks")
  private val giant = CatalogNames.site("Buried Giant")
  /** Plain Nomad denizens. */
  private val elders = CatalogNames.denizen("Elders")
  private val wildMounts = CatalogNames.denizen("Wild Mounts")
  private val mountainGiant = CatalogNames.denizen("Mountain Giant")
  /** A plain Hearth denizen. */
  private val landWarden = CatalogNames.denizen("Land Warden")
  /** Nomad and adviser-only. */
  private val pilgrimage = CatalogNames.denizen("Pilgrimage")
  /** Nomad, locked and adviser-only. */
  private val faithfulFriend = CatalogNames.denizen("Faithful Friend")
  /** A plain Beast denizen, filling Broken Peaks. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")

  /** p1's Act at Ancient City beside Resettle, site-only. p2 holds Wild
    * Mounts faceup with 1 favor on it. Errand Boy fills Broken Peaks; Buried
    * Giant is empty. */
  private def staged: Table = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).denizen(resettle, at = home)
    .adviser(p2, wildMounts).tokens(wildMounts, favor = 1)
    .denizen(errandBoy, at = peaks)

  /** The walk Resettle started and the walk after `card` was chosen, parked
    * on the site question. */
  private def chooseAdviser(ready: ReadyGame, card: DenizenId): UsedAndAnswered =
    useAndAnswer(ready, power, source,
      Resettle.adviserDecisionId, DecisionOptionRef.Denizen(card))

  private def chooseSite(chosen: UsedAndAnswered, site: SiteId): OathTransition =
    accepted(answer(chosen.answered, p1, Resettle.siteDecisionId,
      pick(DecisionOptionRef.Site(site))))

  test("it offers every player's faceup Nomad advisers, the actor's own " +
      "included, and not other suits, facedown, adviser-only or locked ones"):
    val ready = staged.adviser(p1, elders).adviser(p2, landWarden)
      .adviser(p2, mountainGiant, facedown = true).adviser(p3, pilgrimage)
      .adviser(p3, faithfulFriend).ready
    val t = accepted(use(ready, power, source))
    assert(awaits(t, Resettle.adviserDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector("denizen" -> elders.value,
      "denizen" -> wildMounts.value)))

  test("then it asks for a site in play with room, its own site included"):
    val chosen = chooseAdviser(staged.ready, wildMounts)
    assert(awaits(chosen.answered, Resettle.siteDecisionId),
      parked.parkedDecision(chosen.answered.state).toString)
    val options = offered(chosen.answered, p1).getOrElse(Vector.empty)
    assert(options.contains("site" -> home.value), options)
    assert(options.contains("site" -> giant.value), options)
    assert(!options.contains("site" -> peaks.value), options)

  test("the adviser moves to the chosen site with its tokens, and the line " +
      "names its owner"):
    val ready = staged.ready
    val chosen = chooseAdviser(ready, wildMounts)
    val done = chooseSite(chosen, giant)
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = Look(after(done))
    assertEquals(end.cardsAt(giant), Vector[CardId](wildMounts))
    assertEquals(end.tokensOn(wildMounts), Tokens(1, 0))
    assertEquals(end.advisers(p2), Vector.empty[CardId])
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"Moved ${p2.value}'s ${wildMounts.value} to ${giant.value}.",
      covers = false)))
    val events = chosen.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assertWireRoundTrips(events)

  test("the actor's own adviser can go to Resettle's own site"):
    val done = chooseSite(chooseAdviser(staged.adviser(p1, elders).ready,
      elders), home)
    assertEquals(Look(after(done)).cardsAt(home),
      Vector[CardId](resettle, elders))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Moved ${p1.value}'s ${elders.value} to ${home.value}.",
      covers = false)))

  test("with no faceup Nomad adviser it shows Continue and the line says " +
      "so"):
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(resettle, at = home).adviser(p2, landWarden).ready
    val asked = accepted(use(ready, power, source))
    assert(awaits(asked, Resettle.adviserDecisionId))
    val done = accepted(continuedPast(asked, Resettle.adviserDecisionId))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](landWarden))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", "No Nomad adviser could be moved.", covers = false)))
