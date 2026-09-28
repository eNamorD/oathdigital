package oathdigital.gameplay

import oathdigital.gameplay.actions.{CardPlay, PlacementRules}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

/** `PlacementRules.siteDiscardFirst`: a play to a site may first discard one
  * card of the site's card list, at any capacity. No real power sets it yet
  * (the People's Favor banner face does), so a test double does.
  */
class SiteDiscardFirstSuite extends munit.FunSuite:
  import PlacementFixture._

  private val powers = WalkerPowers(Vector(discardFirst))
  private val noReplacement = CardPlayProcedure.noReplacement.ref
  private val home = Table.homeOf(p1)
  /** The Beast card p1 plays. The other cards are plain and of other suits. */
  private val card = CatalogNames.denizen("Errand Boy")
  private val kept = CatalogNames.denizen("Rain Boots")
  private val other = CatalogNames.denizen("Ancient Binding")
  private val hall = CatalogNames.edifice("Hall of Ministers")

  /** p1 stands at Ancient City (no Homeland, room for three), which holds
    * `cards`, with Errand Boy in hand to play. */
  private def playing(cards: DenizenId*): Table =
    cards.foldLeft(Table.start.hand(p1, card))((table, held) =>
      table.denizen(held, at = home))

  private def choices(ready: ReadyGame, rules: PlacementRules)
      : Option[CardPlay.Choice] =
    CardPlay.legalChoices(catalog, ready, p1, card,
      CardPlay.Origin.TemporaryHand, rules)
      .find(_.placement.isInstanceOf[SearchPlacement.Site])

  /** Chooses "play at site" and returns the walk parked on the discard. */
  private def toDiscardDecision(ready: ReadyGame): (Operation, PendingTree) =
    val tree = build(ready, p1, card)
    val placed = answer(ready, tree, park(ready, tree, powers), powers,
      decisionId(card, "place"), DecisionOptionRef.Button("site"), p1)
      .asInstanceOf[WalkerOutcome.Parked]
    (tree, placed.tree)

  test("a site with room offers a discard only under the permission"):
    val ready = playing(kept, other).ready
    val plainChoice = choices(ready, PlacementRules.default).get
    assertEquals(plainChoice.replacements, Vector.empty)
    assert(!plainChoice.replacementOptional)
    val allowed = choices(ready, PlacementRules.default.withSiteDiscardFirst).get
    assertEquals(allowed.replacements.toSet, Set[CardId](kept, other))
    assert(allowed.replacementOptional)

  test("declining the optional discard plays the card and discards nothing"):
    val ready = playing(kept).ready
    val (tree, asked) = toDiscardDecision(ready)
    assertEquals(options(ready, tree, asked, powers),
      Vector(noReplacement, DecisionOptionRef.Denizen(kept)))
    val done = answer(ready, tree, asked, powers, decisionId(card, "replace"),
      noReplacement, p1).asInstanceOf[WalkerOutcome.Finished].treeless
    assertEquals(Look(done).denizens(home), Vector[CardId](kept, card))
    assertEquals(done.game.current.temporaryHands(p1), Vector.empty)

  test("choosing a site card discards it with the standard returns"):
    val ready = playing(kept).tokens(kept, favor = 1, secrets = 1).ready
    val (tree, asked) = toDiscardDecision(ready)
    val done = answer(ready, tree, asked, powers, decisionId(card, "replace"),
      DecisionOptionRef.Denizen(kept), p1)
      .asInstanceOf[WalkerOutcome.Finished].treeless
    val region = CardPlay.nextRegion(done.game.current.map.regionOf(home).get)
    assertEquals(Look(done).denizens(home), Vector[CardId](card))
    assertEquals(done.game.current.commonCards.discard(region).last, kept)
    // The favor on the discarded card returns to its suit bank (Rain Boots is
    // Nomad), its secret to the player facedown.
    assertEquals(done.banks.favor(Suit.Nomad), ready.banks.favor(Suit.Nomad) + 1)
    assertEquals(Look(done).faceDownSecrets(p1),
      Look(ready).faceDownSecrets(p1) + 1)

  test("a full site with no matching homeland accepts the play only with a " +
      "discard, and only under the permission"):
    val fillers = Vector(kept, other, CatalogNames.denizen("Wrestlers"))
    val ready = playing(fillers*).ready                // Ancient City is full
    assertEquals(choices(ready, PlacementRules.default), None)
    val site = choices(ready, PlacementRules.default.withSiteDiscardFirst).get
    assertEquals(site.replacements.toSet, fillers.toSet[CardId])
    assert(!site.replacementOptional)
    val (tree, asked) = toDiscardDecision(ready)
    assert(!options(ready, tree, asked, powers).contains(noReplacement))
    val done = answer(ready, tree, asked, powers, decisionId(card, "replace"),
      DecisionOptionRef.Denizen(fillers.head), p1)
      .asInstanceOf[WalkerOutcome.Finished].treeless
    assertEquals(Look(done).denizens(home).size, 3)
    assert(Look(done).denizens(home).contains(card))

  test("an intact edifice is never offered for the discard: it is locked"):
    val ready = playing(kept).edifice(hall, EdificeSide.Intact, at = home)
      .warbandsAt(home, p1, 1).ready
    val site = choices(ready, PlacementRules.default.withSiteDiscardFirst).get
    assertEquals(site.replacements, Vector[CardId](kept))

  test("a ruined edifice may be discarded, and it goes back to the edifice deck"):
    val ruined = DecisionOptionRef.Button("replace:edifice:E16")
    val ready = playing(kept).edifice(hall, EdificeSide.Ruined, at = home)
      .warbandsAt(home, p1, 1).ready
    val (tree, asked) = toDiscardDecision(ready)
    assertEquals(options(ready, tree, asked, powers).toSet,
      Set[DecisionOptionRef](noReplacement, DecisionOptionRef.Denizen(kept),
        ruined))
    val done = answer(ready, tree, asked, powers, decisionId(card, "replace"),
      ruined, p1).asInstanceOf[WalkerOutcome.Finished].treeless
    assertEquals(Look(done).denizens(home), Vector[CardId](kept, card))
    assertEquals(done.game.current.commonCards.edificeDeck.last, hall)

  test("the permission composes with an adviser limit through the walker"):
    val (first, second) = ("Wrestlers", "Battle Honors")
    val ready = playing(kept)
      .adviser(p1, first, facedown = true).adviser(p1, second, facedown = true)
      .ready
    val both = WalkerPowers(Vector(discardFirst, limitTwo))
    val tree = build(ready, p1, card)
    val parked = park(ready, tree, both)
    // A limit of two with two advisers held: a faceup adviser needs a discard.
    val faceup = answer(ready, tree, parked, both, decisionId(card, "place"),
      DecisionOptionRef.Button("adviser-faceup"), p1)
      .asInstanceOf[WalkerOutcome.Parked]
    assertEquals(options(ready, tree, faceup.tree, both).toSet,
      Set[DecisionOptionRef](DecisionOptionRef.Denizen(CatalogNames.denizen(first)),
        DecisionOptionRef.Denizen(CatalogNames.denizen(second))))
    // And the site still offers the optional discard.
    val site = answer(ready, tree, parked, both, decisionId(card, "place"),
      DecisionOptionRef.Button("site"), p1).asInstanceOf[WalkerOutcome.Parked]
    assertEquals(options(ready, tree, site.tree, both).head, noReplacement)

  test("without the permission a site with room asks no discard"):
    val ready = playing(kept).ready
    val none = WalkerPowers.empty
    val tree = build(ready, p1, card)
    val done = answer(ready, tree, park(ready, tree, none), none,
      decisionId(card, "place"), DecisionOptionRef.Button("site"), p1)
      .asInstanceOf[WalkerOutcome.Finished].treeless
    assertEquals(Look(done).denizens(home), Vector[CardId](kept, card))
