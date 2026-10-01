package oathdigital.gameplay.powers.search

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.{NoteText, SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.powers.banner.{BannerFixture, PeoplesFavorMob}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

class CropRotationSuite extends munit.FunSuite:
  import SearchFixture.{keep, place, rules, start}

  private val parked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog))

  private val crop = CatalogNames.denizen("Crop Rotation")
  private val modifiers = Vector(CropRotation.id)
  private val played = SearchFixture.denizensOf(Suit.Beast).head
  private val fillers = SearchFixture.denizensOf(Suit.Order) ++
    SearchFixture.denizensOf(Suit.Arcane)
  private val home = Table.homeOf(p1)
  private val capacity = catalog.sites.find(_.id == home).get.capacity
  private val noDiscard = CardPlayProcedure.noReplacement.ref
  private val replaceId = s"cardplay.replace.denizen.${played.value}"

  /** p1 in Act at Ancient City with 5 Supply, the world deck topped by
    * `played`, and Ancient City holding `site`. p1 holds Crop Rotation as a
    * faceup adviser, or it stands at the site when `atSite`. */
  private def staged(site: Vector[DenizenId], atSite: Boolean = false)
      : ReadyGame =
    val table = Table.start.worldDeckTop(played).supply(p1, 5)
    val held = if atSite then table.denizen(crop, at = home)
      else table.adviser(p1, crop)
    site.foldLeft(held)((t, card) => t.denizen(card, at = home)).ready

  /** Searches with `selected`, keeps `played` and plays it to the site. */
  private def toSite(ready: ReadyGame, selected: Vector[PowerId] = modifiers)
      : Either[OathViolation, OathTransition] = for
    started <- start(ready, selected)
    kept <- keep(started, played)
    placed <- place(kept, played, "site")
  yield placed.copy(events = started.events ++ kept.events ++ placed.events)

  private def asksToDiscard(transition: OathTransition): Boolean =
    parked.parkedDecision(transition.state).exists(facts =>
      facts.decision == replaceId && facts.awaiting == p1)

  private def discard(from: OathTransition, chosen: DecisionOptionRef)
      : Either[OathViolation, OathTransition] = rules.resolveWalker(from.state,
    p1, replaceId, DecisionAnswer.ChooseOneAnswer(chosen))

  private def siteCards(ready: ReadyGame): Vector[CardId] =
    ready.game.current.map.sites(home).denizens.map(_.id)

  private def cropSaid(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(CropRotation.id, Vector(
      oathdigital.gameplay.actions.PlacementRules.discardFirst), events)

  test("selected, a play to a site with room asks first, and may decline"):
    val ready = staged(Vector(fillers(0)))
    val asked = toSite(ready).toOption.get
    assert(asksToDiscard(asked))
    val done = discard(asked, noDiscard).toOption.get
    val end = SearchFixture.after(done)
    assertEquals(siteCards(end), Vector[CardId](fillers(0), played))
    assertEquals(PaidActionHarness.replayed(rules, ready,
      asked.events ++ done.events), end)
    assert(PaidActionHarness.wireRoundTrips(asked.events ++ done.events))

  test("choosing a card discards it before the play"):
    val asked = toSite(staged(Vector(fillers(0)))).toOption.get
    val done = discard(asked, DecisionOptionRef.Denizen(fillers(0))).toOption.get
    assertEquals(siteCards(SearchFixture.after(done)), Vector[CardId](played))

  test("without the selection nothing is asked"):
    val done = toSite(staged(Vector(fillers(0))), Vector.empty).toOption.get
    assert(!asksToDiscard(done))
    assertEquals(siteCards(SearchFixture.after(done)),
      Vector[CardId](fillers(0), played))

  test("a full site takes the play only with a discard"):
    val full = staged(fillers.take(capacity))
    assert(toSite(full, Vector.empty).isLeft,
      "a full site accepts no play without a permission")
    val asked = toSite(full).toOption.get
    assert(asksToDiscard(asked))
    assert(discard(asked, noDiscard).isLeft, "a discard is required")
    val done = discard(asked, DecisionOptionRef.Denizen(fillers(0))).toOption.get
    assert(siteCards(SearchFixture.after(done)).contains(played))

  test("Crop Rotation itself, selected at the site, cannot be the discard"):
    val asked = toSite(staged(Vector(fillers(0)), atSite = true)).toOption.get
    assert(asksToDiscard(asked))
    assert(discard(asked, DecisionOptionRef.Denizen(crop)).isLeft,
      "a card that prints a selected power cannot be discarded")
    assert(discard(asked, DecisionOptionRef.Denizen(fillers(0))).isRight)

  test("it applies to a facedown adviser played to a site"):
    val ready = Table.start.adviser(p1, crop)
      .adviser(p1, played, facedown = true)
      .denizen(fillers(0), at = home).ready
    val started = rules.startWalker(Ready(ready), ActionRef.PlayFacedownAdviser,
      p1, modifiers, Vector(DecisionOptionRef.Denizen(played))).toOption.get
    assert(asksToDiscard(place(started, played, "site").toOption.get))

  test("its line is written once the discard is answered"):
    val asked = toSite(staged(Vector(fillers(0)))).toOption.get
    assertEquals(cropSaid(asked.events), Vector.empty)
    val done = discard(asked, noDiscard).toOption.get
    assertEquals(cropSaid(done.events), Vector(NoteText.Said("discard-first",
      s"${p1.value} may discard a card at their site first.", covers = false)))

  test("with the Mob as well, only one line is written"):
    val ready = BannerFixture.holdingFavor(staged(Vector(fillers(0))))
    val asked = toSite(ready).toOption.get
    val done = discard(asked, noDiscard).toOption.get
    val mobSaid = NoteText.said(PeoplesFavorMob.id, PeoplesFavorMob.noteKeys,
      done.events)
    assertEquals(cropSaid(done.events).size + mobSaid.size, 1)
