package oathdigital.gameplay.powers.banner

import oathdigital.gameplay.PlacementFixture
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.{PowerFixture, SearchFixture, TargetsFixture, WalkerPowerCatalog}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerOutcome
import oathdigital.model._

class PeoplesFavorMobSuite extends munit.FunSuite {
  import PowerFixture._
  import BannerFixture._
  import SearchFixture.{denizensOf, keep, place, rules, start}

  private val noDiscard = CardPlayProcedure.noReplacement.ref
  private val played = denizensOf(Suit.Beast).head
  private val drawn = Vector[WorldCardId](played) ++
    denizensOf(Suit.Arcane).take(2)
  private val capacity = catalog.sites.find(_.id == home(base)).get.capacity

  /** A Search of `drawn` at a pawn site holding `site`, the Banner of the
    * People's Favor held as `favor` arranges it (by the actor, on the Mob
    * face, unless a test says otherwise). `played` is the card that is kept.
    */
  private def staged(site: Vector[DenizenId],
      favor: ReadyGame => ReadyGame = holdingFavor(_)): ReadyGame =
    favor(site.foldLeft(SearchFixture.staged(drawn))(atHome))

  private def decisionId(kind: String) = s"cardplay.$kind.denizen.${played.value}"

  /** Searches, keeps the played card and chooses to play it to the site. */
  private def toSite(ready: ReadyGame): OathTransition = (for {
    started <- start(ready)
    kept <- keep(started, played)
    placed <- place(kept, played, "site")
  } yield placed.copy(events = started.events ++ kept.events ++ placed.events))
    .fold(error => throw new AssertionError(error.toString), identity)

  private def asksToDiscard(transition: OathTransition): Boolean =
    transition.continue == OathContinue.AwaitingSearchDecision(actor,
      DecisionId(decisionId("replace")))

  private def discardAnswer(from: OathTransition, chosen: DecisionOptionRef)
      : Either[OathViolation, OathTransition] = rules.resolveWalker(from.state,
    actor, decisionId("replace"), DecisionAnswer.ChooseOneAnswer(chosen))

  private def siteCards(ready: ReadyGame): Vector[CardId] =
    ready.game.current.map.sites(home(ready)).denizens.map(_.id)

  test("Mob is a registered persistent rule, so it is automatic") {
    val registered = WalkerPowerCatalog.default(catalog).powers
      .find(_.id == PeoplesFavorMob.id)
    assertEquals(registered.map(_.resolution), Some(PowerResolution.Automatic))
    assertEquals(PeoplesFavorMob.id.value, "banner.peoples-favor.mob")
  }

  test("the source index lists Mob on the Mob face and the Grand Council's " +
      "own power on the other") {
    def favor(ready: ReadyGame) = oathdigital.gameplay.RuleSourceIndex
      .enumerate(catalog, ready).find(_.source ==
        RuleSourceRef.Banner(Banner.PeoplesFavor.key)).get
    assertEquals(favor(base).powerIds, Vector(PeoplesFavorMob.id))
    assertEquals(favor(holdingFavor(base, PeoplesFavorFace.GrandCouncil))
      .handlerIds, Vector("banner.peoples-favor.grand-council"))
  }

  test("its holder is asked whether to discard a site card before a play " +
      "to a site with room, and may decline") {
    val hearth = denizensOf(Suit.Hearth)
    val (kept, other) = (hearth(0), hearth(1))
    val ready = staged(Vector(kept, other))
    assert(capacity > 2, s"the pawn site must have room, capacity $capacity")
    val asked = toSite(ready)
    assert(asksToDiscard(asked))
    val done = discardAnswer(asked, noDiscard).toOption.get
    val end = SearchFixture.after(done)
    assertEquals(siteCards(end), Vector[CardId](kept, other, played))
    assertEquals(PaidActionHarness.replayed(rules, ready,
      asked.events ++ done.events), end)
  }

  test("an empty site has nothing to discard, so the holder is asked nothing") {
    val done = toSite(staged(Vector.empty))
    assert(!asksToDiscard(done))
    assertEquals(siteCards(SearchFixture.after(done)),
      Vector[CardId](played))
  }

  test("choosing a card discards it by the standard discard: facedown to the " +
      "next region's pile, its favor to the bank, its secret to the holder") {
    val kept = denizensOf(Suit.Hearth).head
    val ready = withCardTokens(staged(Vector(kept)), home(base), kept,
      Tokens(1, 1))
    val asked = toSite(ready)
    val done = discardAnswer(asked, DecisionOptionRef.Denizen(kept)).toOption.get
    val end = SearchFixture.after(done)
    val region = CardPlay.nextRegion(
      ready.game.current.map.regionOf(home(base)).get)
    assertEquals(siteCards(end), Vector[CardId](played))
    assertEquals(end.game.current.commonCards.discard(region).last, kept)
    assertEquals(end.banks.favor.getOrElse(Suit.Hearth, 0),
      ready.banks.favor.getOrElse(Suit.Hearth, 0) + 1)
    assertEquals(player(end).board.faceDownSecrets,
      player(ready).board.faceDownSecrets + 1)
    assertEquals(PaidActionHarness.replayed(rules, ready,
      asked.events ++ done.events), end)
  }

  test("a full site with no matching homeland takes the play only with a " +
      "discard, and only for the holder") {
    val fillers = (denizensOf(Suit.Hearth) ++ denizensOf(Suit.Order))
      .take(capacity)
    assertEquals(fillers.size, capacity)
    val full = staged(fillers)
    assert(place(keep(start(full).toOption.get, played).toOption.get, played,
      "site").isRight)
    val asked = toSite(full)
    assert(asksToDiscard(asked))
    assert(discardAnswer(asked, noDiscard).isLeft, "a discard is required")
    val done = discardAnswer(asked, DecisionOptionRef.Denizen(fillers.head))
      .toOption.get
    assertEquals(siteCards(SearchFixture.after(done)).size, capacity)
    assert(siteCards(SearchFixture.after(done)).contains(played))
    val without = staged(fillers, identity)
    val attempt = for {
      started <- start(without)
      kept <- keep(started, played)
      placed <- place(kept, played, "site")
    } yield placed
    assert(attempt.isLeft, "a full site accepts no play without Mob")
  }

  test("nobody else is asked: not another holder, not the Grand Council face, " +
      "not the other banner, not an unheld banner") {
    val kept = denizensOf(Suit.Hearth).head
    val unheld: Vector[ReadyGame => ReadyGame] = Vector(
      holdingFavor(_, holder = Some(p1)),
      holdingFavor(_, PeoplesFavorFace.GrandCouncil),
      holdingFavor(_, holder = None),
      holdingFlame(_))
    unheld.zipWithIndex.foreach { case (favor, index) =>
      val ready = staged(Vector(kept), favor)
      val done = toSite(ready)
      assert(!asksToDiscard(done), s"case $index")
      assertEquals(siteCards(SearchFixture.after(done)),
        Vector[CardId](kept, played), s"case $index")
    }
  }

  test("an intact edifice is never offered for the discard: it is locked") {
    val kept = denizensOf(Suit.Hearth).head
    val hall = EdificeId("E16")
    val (built, who, site) = PlacementFixture.staged(played,
      Vector(PlacementFixture.denizen(kept),
        EdificeState(hall, EdificeSide.Intact, Tokens.empty)))
    val ready = PlacementFixture.ruledByActor(built, site)
    val choice = CardPlay.legalChoices(catalog, holdingFavor(ready), who,
      played, CardPlay.Origin.TemporaryHand,
      oathdigital.gameplay.actions.PlacementRules.default.withSiteDiscardFirst)
      .find(_.placement.isInstanceOf[SearchPlacement.Site]).get
    assertEquals(choice.replacements, Vector[CardId](kept))
  }

  test("it applies to a facedown adviser played to a site as well") {
    val kept = denizensOf(Suit.Hearth).head
    val ready = asAdviser(staged(Vector(kept)), played, Orientation.FaceDown)
    val started = rules.startWalker(OathState.Ready(ready),
      ActionRef.PlayFacedownAdviser, actor, Vector.empty,
      Vector(DecisionOptionRef.Denizen(played))).toOption.get
    val placed = place(started, played, "site").toOption.get
    assert(asksToDiscard(placed))
  }

  test("it composes with Silver Tongue's limit of two advisers, both from " +
      "the production walker powers") {
    val tongue = DenizenId("92")
    val hearth = denizensOf(Suit.Hearth)
    val (kept, adviser) = (hearth(0), hearth(1))
    val (built, who, _) = PlacementFixture.staged(played,
      Vector(PlacementFixture.denizen(kept)))
    val bare = TargetsFixture.withoutAdvisers(built, who)
    val ready = holdingFavor(TargetsFixture.giveAdviser(
      TargetsFixture.giveAdviser(bare, who, tongue, Orientation.FaceUp), who,
      adviser, Orientation.FaceDown))
    val powers = WalkerPowerCatalog.default(catalog)
    val tree = PlacementFixture.build(ready, who, played)
    val parked = PlacementFixture.park(ready, tree, powers)
    // Two advisers held under Silver Tongue: a faceup adviser needs a discard,
    // and the faceup Silver Tongue is a locked card, so only the other goes.
    val faceup = PlacementFixture.answer(ready, tree, parked, powers,
      PlacementFixture.decisionId(played, "place"),
      DecisionOptionRef.Button("adviser-faceup"), who)
      .asInstanceOf[WalkerOutcome.Parked]
    assertEquals(PlacementFixture.options(ready, tree, faceup.tree, powers).toSet,
      Set[DecisionOptionRef](DecisionOptionRef.Denizen(adviser)))
    // The site still offers the optional discard, under the same walk.
    val site = PlacementFixture.answer(ready, tree, parked, powers,
      PlacementFixture.decisionId(played, "place"),
      DecisionOptionRef.Button("site"), who).asInstanceOf[WalkerOutcome.Parked]
    assertEquals(PlacementFixture.options(ready, tree, site.tree, powers).head,
      noDiscard)
    assert(powers.powers.exists(_.id == SilverTongue.id))
  }

  test("it leaves a selected card's power alone: Welcoming Party still pays " +
      "once, and the selected card itself cannot be the discard") {
    val party = DenizenId("50")
    val kept = denizensOf(Suit.Hearth).head
    val ready = atHome(staged(Vector(kept)), party)
    val selected = Vector(oathdigital.gameplay.powers.cardplay.WelcomingParty.id)
    val started = start(ready, selected).toOption.get
    val chosen = keep(started, played).toOption.get
    val asked = place(chosen, played, "site").toOption.get
    assert(asksToDiscard(asked))
    assert(discardAnswer(asked, DecisionOptionRef.Denizen(party)).isLeft,
      "a card that prints a selected power cannot be discarded")
    val done = discardAnswer(asked, DecisionOptionRef.Denizen(kept)).toOption.get
    val end = SearchFixture.after(done)
    val hearth = (s: ReadyGame) => s.banks.favor.getOrElse(Suit.Hearth, 0)
    // The play gains 1 favor from the played card's own bank, Welcoming Party
    // gains 1 from the Hearth bank.
    assertEquals(player(end).board.favor, player(ready).board.favor + 2)
    assertEquals(hearth(end), hearth(ready) - 1)
    assertEquals(siteCards(end), Vector[CardId](party, played))
  }
}
