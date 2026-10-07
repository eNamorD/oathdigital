package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.{FacedownAdviserPlay, PlacementFixture}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{NoteText, PowerFixture, SearchFixture,
  WalkerPowerCatalog}
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PlacementPicks, Table}
import oathdigital.testkit.Table.p3
import oathdigital.testkit.Unwrap.accepted

class PeoplesFavorMobSuite extends munit.FunSuite:
  import PowerFixture._
  import SearchFixture.{keep, place, placeReplacing, rules, start}

  private val walkerPowers = WalkerPowerCatalog.default(catalog)
  private val ancientCity = CatalogNames.site("Ancient City")
  /** The Beast card each Search keeps and plays, above two Arcane cards. */
  private val played = CatalogNames.denizen("Errand Boy")
  private val drawn = Vector[WorldCardId](played,
    CatalogNames.denizen("Ward of Silence"),
    CatalogNames.denizen("Arcane Brokers"))
  // Hearth denizens whose powers these tests do not use.
  private val homesteaders = CatalogNames.denizen("Homesteaders")
  private val landWarden = CatalogNames.denizen("Land Warden")
  private val newsFromAfar = CatalogNames.denizen("News from Afar")
  private val capacity = 3 // Ancient City's printed capacity

  /** p1's Act at Ancient City, which holds `siteCards`, searching a world
    * deck topped by Errand Boy, Ward of Silence and Arcane Brokers with 5
    * Supply. The Banner of the People's Favor is as `favor` arranges it: held
    * by p1, on the Mob face, unless a test says otherwise.
    */
  private def searchAtAncientCityHolding(siteCards: Vector[DenizenId],
    favor: ReadyGame => ReadyGame = (r => Table.from(r)
        .peoplesFavor(Some(actor), 1).unchecked)): ReadyGame =
    favor(
      siteCards.foldLeft(SearchFixture.staged(drawn, supply = 5))(atActorsSite))

  /** Searches with `selected` and keeps the played card, parked on its
    * placement. The events are the whole Search's so far. */
  private def toPlacement(ready: ReadyGame,
      selected: Vector[PowerId] = Vector.empty): OathTransition = (for
    started <- start(ready, selected)
    kept <- keep(started, played)
  yield kept.copy(events = started.events ++ kept.events))
    .fold(error => throw new AssertionError(error.toString), identity)

  /** The parked placement's site slots, each as the card it replaces. */
  private def siteSlots(at: OathTransition): Vector[Option[CardId]] =
    PlacementPicks.parkedOptions(at.state, walkerPowers)
      .filter(_.ref.isInstanceOf[DecisionOptionRef.SiteSlot])
      .map(PlacementPicks.replaces)

  private def siteCards(ready: ReadyGame): Vector[CardId] =
    Look(ready).cardsAt(ancientCity)

  test("the source index lists Mob on the Mob face and the Grand Council's " +
      "own power on the other"):
    def favor(ready: ReadyGame) = oathdigital.gameplay.RuleSourceIndex
      .enumerate(catalog, ready).find(_.source ==
        RuleSourceRef.Banner(Banner.PeoplesFavor.key))
          .getOrElse(fail("no People's Favor source"))
    assertEquals(favor(quietTable).powerIds, Vector(PeoplesFavorMob.id))
    assertEquals(favor(Table.from(quietTable).peoplesFavor(Some(actor), 1,
      PeoplesFavorFace.GrandCouncil).unchecked)
      .handlerIds, Vector("banner.peoples-favor.grand-council"))

  test("its holder is offered each site card as Replace beside the empty " +
      "slot, and the empty slot discards nothing"):
    val (kept, other) = (homesteaders, landWarden)
    val ready = searchAtAncientCityHolding(Vector(kept, other))
    assert(capacity > 2, s"the pawn site must have room, capacity $capacity")
    val asked = toPlacement(ready)
    assertEquals(siteSlots(asked), Vector(None, Some(kept), Some(other)))
    val done = accepted(place(asked, played, PlayedAs.AtSite))
    val end = SearchFixture.after(done)
    assertEquals(siteCards(end), Vector[CardId](kept, other, played))
    assertEquals(ActPowerHarness.replayed(rules, ready,
      asked.events ++ done.events), end)

  test("an empty site has nothing to replace"):
    val asked = toPlacement(searchAtAncientCityHolding(Vector.empty))
    assertEquals(siteSlots(asked), Vector(None))
    assertEquals(siteCards(SearchFixture.after(
      accepted(place(asked, played, PlayedAs.AtSite)))), Vector[CardId](played))

  test("a Replace slot discards by the standard discard: facedown to the " +
      "next region's pile, its favor to the bank, its secret to the holder"):
    val kept = homesteaders
    val ready = Table.from(searchAtAncientCityHolding(Vector(kept)))
      .tokens(kept, favor = 1, secrets = 1).unchecked
    val asked = toPlacement(ready)
    val done = accepted(placeReplacing(asked, played, PlayedAs.AtSite, kept))
    val end = SearchFixture.after(done)
    assertEquals(siteCards(end), Vector[CardId](played))
    assertEquals(Look(end).discardAfter(ancientCity).last, kept)
    assertEquals(end.banks.favor.getOrElse(Suit.Hearth, 0),
      ready.banks.favor.getOrElse(Suit.Hearth, 0) + 1)
    assertEquals(Look(end).player(actor).board.faceDownSecrets,
      Look(ready).player(actor).board.faceDownSecrets + 1)
    assertEquals(ActPowerHarness.replayed(rules, ready,
      asked.events ++ done.events), end)

  test("a full site with no matching homeland takes the play only through a " +
      "Replace slot, and only for the holder"):
    // Ancient City is no suit's homeland, so the Beast card matches none.
    assertEquals(catalog.site(ancientCity).flatMap(_.homeland), None)
    val fillers = Vector(homesteaders, landWarden, newsFromAfar)
    assertEquals(fillers.size, capacity)
    val asked = toPlacement(searchAtAncientCityHolding(fillers))
    assertEquals(siteSlots(asked), fillers.map(Some(_)))
    val done = accepted(placeReplacing(asked, played, PlayedAs.AtSite,
      fillers.head))
    assertEquals(siteCards(SearchFixture.after(done)).size, capacity)
    assert(siteCards(SearchFixture.after(done)).contains(played))
    val without = toPlacement(searchAtAncientCityHolding(fillers, identity))
    assert(place(without, played, PlayedAs.AtSite).isLeft,
      "a full site accepts no play without Mob")

  test("nobody else may replace: not another holder, not the Grand Council " +
      "face, not the other banner, not an unheld banner"):
    val kept = homesteaders
    val unheld: Vector[(String, ReadyGame => ReadyGame)] = Vector(
      "held by p3" -> (r => Table.from(r).peoplesFavor(Some(p3), 1).unchecked),
      "Grand Council face" -> (r => Table.from(r)
      .peoplesFavor(Some(actor), 1, PeoplesFavorFace.GrandCouncil).unchecked),
      "unheld" -> (r => Table.from(r).peoplesFavor(None, 1).unchecked),
      "Wandering Flame" -> (r => Table.from(r).darkestSecret(Some(actor), 1)
      .unchecked))
    unheld.foreach { case (label, favor) =>
      val asked = toPlacement(searchAtAncientCityHolding(Vector(kept), favor))
      assertEquals(siteSlots(asked), Vector(None), label)
      assertEquals(siteCards(SearchFixture.after(
        accepted(place(asked, played, PlayedAs.AtSite)))),
        Vector[CardId](kept, played), label)
    }

  test("it applies to a facedown adviser played to a site as well"):
    val kept = homesteaders
    val ready = asAdviser(searchAtAncientCityHolding(Vector(kept)), played,
      Orientation.FaceDown)
    val started = accepted(FacedownAdviserPlay.parkedOnPlacement(rules,
      OathState.Ready(ready), actor, played))
    assert(siteSlots(started).contains(Some(kept)))

  test("it composes with Silver Tongue's limit of two advisers, both from " +
      "the production walker powers"):
    val tongue = CatalogNames.denizen("Silver Tongue")
    val (kept, adviser) = (homesteaders, landWarden)
    // p1 plays `played` from hand at Ancient City, which holds `kept`, while
    // holding the People's Favor on Mob, a faceup Silver Tongue and a
    // facedown `adviser`.
    val who = actor
    val ready = Table.start.hand(who, played).pawn(who, ancientCity)
      .denizen(kept, at = ancientCity).adviser(who, tongue)
      .adviser(who, adviser, facedown = true).peoplesFavor(Some(who), 1).ready
    val tree = PlacementFixture.build(ready, who, played)
    val shown = PlacementFixture.placementOptions(ready, tree,
      PlacementFixture.park(ready, tree, walkerPowers), walkerPowers)
    // Two advisers held under Silver Tongue: a faceup play replaces one, and
    // the faceup Silver Tongue is a locked card, so only the other may go.
    assertEquals(shown.filter(option => option.available &&
      CardPlayProcedure.playedAs(option.ref).contains(PlayedAs.Faceup))
      .map(PlacementPicks.replaces), Vector(Some(adviser)))
    // The site still offers the Mob's Replace slot beside the empty one.
    assertEquals(shown.filter(_.ref.isInstanceOf[DecisionOptionRef.SiteSlot])
      .map(PlacementPicks.replaces), Vector(None, Some(kept)))
    assert(walkerPowers.powers.exists(_.id == SilverTongue.id))

  test("it leaves a selected card's power alone: Welcoming Party still pays " +
      "once, and the selected card itself cannot be replaced"):
    val party = CatalogNames.denizen("Welcoming Party")
    val kept = homesteaders
    val ready = atActorsSite(searchAtAncientCityHolding(Vector(kept)), party)
    val selected = Vector(oathdigital.gameplay.powers.cardplay.WelcomingParty.id)
    val asked = toPlacement(ready, selected)
    assert(placeReplacing(asked, played, PlayedAs.AtSite, party).isLeft,
      "a card that prints a selected power cannot be discarded")
    val done = accepted(placeReplacing(asked, played, PlayedAs.AtSite, kept))
    val end = SearchFixture.after(done)
    val hearth = (s: ReadyGame) => s.banks.favor.getOrElse(Suit.Hearth, 0)
    // The play gains 1 favor from the played card's own bank, Welcoming Party
    // gains 1 from the Hearth bank.
    assertEquals(Look(end).player(actor).board.favor, Look(ready).player(actor)
      .board.favor + 2)
    assertEquals(hearth(end), hearth(ready) - 1)
    assertEquals(siteCards(end), Vector[CardId](party, played))

  // ---- Lines ----

  private def mobSaid(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(PeoplesFavorMob.id, PeoplesFavorMob.noteKeys, events)

  test("the discard the Mob permits writes its line when it is made"):
    val asked = toPlacement(
      searchAtAncientCityHolding(Vector(homesteaders, landWarden)))
    assertEquals(mobSaid(asked.events), Vector.empty)
    val done = accepted(placeReplacing(asked, played, PlayedAs.AtSite,
      homesteaders))
    assertEquals(mobSaid(done.events), Vector(NoteText.Said("discard-first",
      s"${actor.value} may discard a card at their site first.", covers = false)))

  test("a play to the empty slot writes no Mob line"):
    val asked = toPlacement(searchAtAncientCityHolding(Vector(homesteaders)))
    assertEquals(mobSaid(accepted(place(asked, played, PlayedAs.AtSite))
      .events), Vector.empty)
