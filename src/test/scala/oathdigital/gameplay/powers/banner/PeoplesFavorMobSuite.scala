package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.PlacementFixture
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.{NoteText, PowerFixture, SearchFixture,
  WalkerPowerCatalog}
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerOutcome}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p3}
import oathdigital.testkit.Unwrap.{accepted, as}

class PeoplesFavorMobSuite extends munit.FunSuite:
  import PowerFixture._
  import SearchFixture.{keep, place, rules, start}

  /** The parked decision, as this suite rebuilds it: the same catalog and
    * walker power catalog `SearchFixture.rules` was built with.
    */
  private val parked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog))

  private val noDiscard = CardPlayProcedure.noReplacement.ref
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
    favor(siteCards.foldLeft(SearchFixture.staged(drawn))(atActorsSite))

  private def decisionId(kind: String) =
    PlacementFixture.cardPlayDecisionId(played, kind)

  /** Searches, keeps the played card and chooses to play it to the site. */
  private def toSite(ready: ReadyGame): OathTransition = (for
    started <- start(ready)
    kept <- keep(started, played)
    placed <- place(kept, played, "site")
  yield placed.copy(events = started.events ++ kept.events ++ placed.events))
    .fold(error => throw new AssertionError(error.toString), identity)

  private def asksToDiscard(transition: OathTransition): Boolean =
    parked.parkedDecision(transition.state).exists(facts =>
      facts.decision == decisionId("replace") && facts.awaiting == actor)

  private def discardAnswer(from: OathTransition, chosen: DecisionOptionRef)
      : Either[OathViolation, OathTransition] = rules.resolveWalker(from.state,
    actor, decisionId("replace"), DecisionAnswer.ChooseOneAnswer(chosen))

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

  test("its holder is asked whether to discard a site card before a play " +
      "to a site with room, and may decline"):
    val (kept, other) = (homesteaders, landWarden)
    val ready = searchAtAncientCityHolding(Vector(kept, other))
    assert(capacity > 2, s"the pawn site must have room, capacity $capacity")
    val asked = toSite(ready)
    assert(asksToDiscard(asked))
    val done = accepted(discardAnswer(asked, noDiscard))
    val end = SearchFixture.after(done)
    assertEquals(siteCards(end), Vector[CardId](kept, other, played))
    assertEquals(ActPowerHarness.replayed(rules, ready,
      asked.events ++ done.events), end)

  test("an empty site has nothing to discard, so the holder is asked nothing"):
    val done = toSite(searchAtAncientCityHolding(Vector.empty))
    assert(!asksToDiscard(done))
    assertEquals(siteCards(SearchFixture.after(done)),
      Vector[CardId](played))

  test("choosing a card discards it by the standard discard: facedown to the " +
      "next region's pile, its favor to the bank, its secret to the holder"):
    val kept = homesteaders
    val ready = Table.from(searchAtAncientCityHolding(Vector(kept)))
      .tokens(kept, favor = 1, secrets = 1).unchecked
    val asked = toSite(ready)
    val done = accepted(discardAnswer(asked, DecisionOptionRef.Denizen(kept)))
    val end = SearchFixture.after(done)
    assertEquals(siteCards(end), Vector[CardId](played))
    assertEquals(Look(end).discardAfter(ancientCity).last, kept)
    assertEquals(end.banks.favor.getOrElse(Suit.Hearth, 0),
      ready.banks.favor.getOrElse(Suit.Hearth, 0) + 1)
    assertEquals(Look(end).player(actor).board.faceDownSecrets,
      Look(ready).player(actor).board.faceDownSecrets + 1)
    assertEquals(ActPowerHarness.replayed(rules, ready,
      asked.events ++ done.events), end)

  test("a full site with no matching homeland takes the play only with a " +
      "discard, and only for the holder"):
    // Ancient City is no suit's homeland, so the Beast card matches none.
    assertEquals(catalog.site(ancientCity).flatMap(_.homeland), None)
    val fillers = Vector(homesteaders, landWarden, newsFromAfar)
    assertEquals(fillers.size, capacity)
    val full = searchAtAncientCityHolding(fillers)
    assert(place(accepted(keep(accepted(start(full)), played)), played, "site")
      .isRight)
    val asked = toSite(full)
    assert(asksToDiscard(asked))
    assert(discardAnswer(asked, noDiscard).isLeft, "a discard is required")
    val done = accepted(
      discardAnswer(asked, DecisionOptionRef.Denizen(fillers.head)))
    assertEquals(siteCards(SearchFixture.after(done)).size, capacity)
    assert(siteCards(SearchFixture.after(done)).contains(played))
    val without = searchAtAncientCityHolding(fillers, identity)
    val attempt = for
      started <- start(without)
      kept <- keep(started, played)
      placed <- place(kept, played, "site")
    yield placed
    assert(attempt.isLeft, "a full site accepts no play without Mob")

  test("nobody else is asked: not another holder, not the Grand Council face, " +
      "not the other banner, not an unheld banner"):
    val kept = homesteaders
    val unheld: Vector[(String, ReadyGame => ReadyGame)] = Vector(
      "held by p3" -> (r => Table.from(r).peoplesFavor(Some(p3), 1).unchecked),
      "Grand Council face" -> (r => Table.from(r)
      .peoplesFavor(Some(actor), 1, PeoplesFavorFace.GrandCouncil).unchecked),
      "unheld" -> (r => Table.from(r).peoplesFavor(None, 1).unchecked),
      "Wandering Flame" -> (r => Table.from(r).darkestSecret(Some(actor), 1)
      .unchecked))
    unheld.foreach { case (label, favor) =>
      val ready = searchAtAncientCityHolding(Vector(kept), favor)
      val done = toSite(ready)
      assert(!asksToDiscard(done), label)
      assertEquals(siteCards(SearchFixture.after(done)),
        Vector[CardId](kept, played), label)
    }

  test("an intact edifice is never offered for the discard: it is locked"):
    val kept = homesteaders
    val hall = CatalogNames.edifice("Hall of Ministers")
    // p1 holds the played card, at Ancient City, which p1 rules and which
    // holds the kept card and Hall of Ministers.
    val ready = Table.start.hand(p1, played).pawn(p1, ancientCity)
      .denizen(kept, at = ancientCity)
      .edifice(hall, EdificeSide.Intact, at = ancientCity)
      .warbandsAt(ancientCity, p1, 1).peoplesFavor(Some(p1), 1).ready
    val choice = CardPlay.legalChoices(catalog, ready, p1, played,
      CardPlay.Origin.TemporaryHand,
      oathdigital.gameplay.actions.PlacementRules.default.withSiteDiscardFirst)
      .find(_.placement.isInstanceOf[SearchPlacement.Site])
      .getOrElse(fail("no site placement offered"))
    assertEquals(choice.replacements, Vector[CardId](kept))

  test("a ruined edifice is offered: only an intact one is locked"):
    val kept = homesteaders
    val hall = CatalogNames.edifice("Hall of Ministers")
    // p1 holds the played card, at Ancient City, which p1 rules and which
    // holds the kept card and Hall of Ministers.
    val ready = Table.start.hand(p1, played).pawn(p1, ancientCity)
      .denizen(kept, at = ancientCity)
      .edifice(hall, EdificeSide.Ruined, at = ancientCity)
      .warbandsAt(ancientCity, p1, 1).peoplesFavor(Some(p1), 1).ready
    val choice = CardPlay.legalChoices(catalog, ready, p1, played,
      CardPlay.Origin.TemporaryHand,
      oathdigital.gameplay.actions.PlacementRules.default.withSiteDiscardFirst)
      .find(_.placement.isInstanceOf[SearchPlacement.Site])
      .getOrElse(fail("no site placement offered"))
    assertEquals(choice.replacements, Vector[CardId](kept, hall))

  test("it applies to a facedown adviser played to a site as well"):
    val kept = homesteaders
    val ready = asAdviser(searchAtAncientCityHolding(Vector(kept)), played,
      Orientation.FaceDown)
    val started = accepted(
      rules.startWalker(OathState.Ready(ready), ActionRef.PlayFacedownAdviser,
      actor, Vector.empty, Vector(DecisionOptionRef.Denizen(played))))
    val placed = accepted(place(started, played, "site"))
    assert(asksToDiscard(placed))

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
    val powers = WalkerPowerCatalog.default(catalog)
    val tree = PlacementFixture.build(ready, who, played)
    val parkedTree = PlacementFixture.park(ready, tree, powers)
    // Two advisers held under Silver Tongue: a faceup adviser needs a discard,
    // and the faceup Silver Tongue is a locked card, so only the other goes.
    val place = PlacementFixture.cardPlayDecisionId(played, "place")
    val faceup = as[WalkerOutcome.Parked](
      PlacementFixture.answer(ready, tree, parkedTree, powers, place,
      DecisionOptionRef.Button("adviser-faceup"), who))
    assertEquals(PlacementFixture.options(ready, tree, faceup.tree, powers).toSet,
      Set[DecisionOptionRef](DecisionOptionRef.Denizen(adviser)))
    // The site still offers the optional discard, under the same walk.
    val site = as[WalkerOutcome.Parked](
      PlacementFixture.answer(ready, tree, parkedTree, powers, place,
      DecisionOptionRef.Button("site"), who))
    assertEquals(PlacementFixture.options(ready, tree, site.tree, powers).head,
      noDiscard)
    assert(powers.powers.exists(_.id == SilverTongue.id))

  test("it leaves a selected card's power alone: Welcoming Party still pays " +
      "once, and the selected card itself cannot be the discard"):
    val party = CatalogNames.denizen("Welcoming Party")
    val kept = homesteaders
    val ready = atActorsSite(searchAtAncientCityHolding(Vector(kept)), party)
    val selected = Vector(oathdigital.gameplay.powers.cardplay.WelcomingParty.id)
    val started = accepted(start(ready, selected))
    val chosen = accepted(keep(started, played))
    val asked = accepted(place(chosen, played, "site"))
    assert(asksToDiscard(asked))
    assert(discardAnswer(asked, DecisionOptionRef.Denizen(party)).isLeft,
      "a card that prints a selected power cannot be discarded")
    val done = accepted(discardAnswer(asked, DecisionOptionRef.Denizen(kept)))
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

  test("the discard the Mob permits writes its line once it is answered"):
    val asked = toSite(
      searchAtAncientCityHolding(Vector(homesteaders, landWarden)))
    assertEquals(mobSaid(asked.events), Vector.empty)
    val done = accepted(discardAnswer(asked, noDiscard))
    assertEquals(mobSaid(done.events), Vector(NoteText.Said("discard-first",
      s"${actor.value} may discard a card at their site first.", covers = false)))

  test("a play asked no discard writes no Mob line"):
    assertEquals(
      mobSaid(toSite(searchAtAncientCityHolding(Vector.empty)).events),
      Vector.empty)
