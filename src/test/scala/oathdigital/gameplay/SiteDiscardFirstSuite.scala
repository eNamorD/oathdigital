package oathdigital.gameplay

import oathdigital.gameplay.actions.{CardPlay, PlacementRules}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{as, present}

/** `PlacementRules.siteDiscardFirst`: a play to a site may first discard one
  * card of the site's card list, at any capacity. `discardFirst` is
  * PlacementFixture's stub of ContributingPower that sets it, and `limitTwo`
  * its stub that lowers both adviser limits to two.
  */
class SiteDiscardFirstSuite extends munit.FunSuite:
  import PlacementFixture.{answer, build, cardPlayDecisionId, discardFirst,
    limitTwo, options, park}

  private val powers = WalkerPowers(Vector(discardFirst))
  private val noReplacement = CardPlayProcedure.noReplacement.ref
  private val home = CatalogNames.site("Ancient City")
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

  private def siteChoice(ready: ReadyGame, rules: PlacementRules)
      : CardPlay.Choice =
    present(choices(ready, rules), "the site placement")

  /** The card play's tree, and the walk parked on the discard it asks. */
  private final case class AskedToDiscard(tree: Operation, pending: PendingTree)

  /** Chooses "play at site" and returns the walk parked on the discard. */
  private def toDiscardDecision(ready: ReadyGame): AskedToDiscard =
    val tree = build(ready, p1, card)
    val placed = as[WalkerOutcome.Parked](answer(ready, tree,
      park(ready, tree, powers), powers, cardPlayDecisionId(card, "place"),
      DecisionOptionRef.Button("site"), p1))
    AskedToDiscard(tree, placed.tree)

  /** Answers the discard decision with `ref`, returning the finished game. */
  private def discard(ready: ReadyGame, asked: AskedToDiscard,
      ref: DecisionOptionRef): ReadyGame =
    as[WalkerOutcome.Finished](answer(ready, asked.tree, asked.pending, powers,
      cardPlayDecisionId(card, "replace"), ref, p1)).treeless

  test("a site with room offers a discard only under the permission"):
    val ready = playing(kept, other).ready
    val plainChoice = siteChoice(ready, PlacementRules.default)
    assertEquals(plainChoice.replacements, Vector.empty)
    assert(!plainChoice.replacementOptional)
    val allowed = siteChoice(ready, PlacementRules.default.withSiteDiscardFirst)
    assertEquals(allowed.replacements.toSet, Set[CardId](kept, other))
    assert(allowed.replacementOptional)

  test("declining the optional discard plays the card and discards nothing"):
    val ready = playing(kept).ready
    val asked = toDiscardDecision(ready)
    assertEquals(options(ready, asked.tree, asked.pending, powers),
      Vector(noReplacement, DecisionOptionRef.Denizen(kept)))
    val done = discard(ready, asked, noReplacement)
    assertEquals(Look(done).cardsAt(home), Vector[CardId](kept, card))
    assertEquals(done.game.current.temporaryHands(p1), Vector.empty)

  test("choosing a site card discards it with the standard returns"):
    val ready = playing(kept).tokens(kept, favor = 1, secrets = 1).ready
    val asked = toDiscardDecision(ready)
    val done = discard(ready, asked, DecisionOptionRef.Denizen(kept))
    assertEquals(Look(done).cardsAt(home), Vector[CardId](card))
    // Ancient City is in the Cradle, so the discard goes to the Provinces.
    assertEquals(done.game.current.commonCards.discard(Region.Provinces).last,
      kept)
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
    val site = siteChoice(ready, PlacementRules.default.withSiteDiscardFirst)
    assertEquals(site.replacements.toSet, fillers.toSet[CardId])
    assert(!site.replacementOptional)
    val asked = toDiscardDecision(ready)
    assert(!options(ready, asked.tree, asked.pending, powers)
      .contains(noReplacement))
    val done = discard(ready, asked, DecisionOptionRef.Denizen(fillers.head))
    assertEquals(Look(done).cardsAt(home).size, 3)
    assert(Look(done).cardsAt(home).contains(card))

  test("an intact edifice is never offered for the discard: it is locked"):
    val ready = playing(kept).edifice(hall, EdificeSide.Intact, at = home)
      .warbandsAt(home, p1, 1).ready
    val site = siteChoice(ready, PlacementRules.default.withSiteDiscardFirst)
    assertEquals(site.replacements, Vector[CardId](kept))

  test("a ruined edifice may be discarded, and it goes back to the edifice deck"):
    // Offered as the edifice itself, so the panel names it.
    val ruined = DecisionOptionRef.Edifice(hall)
    val ready = playing(kept).edifice(hall, EdificeSide.Ruined, at = home)
      .warbandsAt(home, p1, 1).ready
    val asked = toDiscardDecision(ready)
    assertEquals(options(ready, asked.tree, asked.pending, powers).toSet,
      Set[DecisionOptionRef](noReplacement, DecisionOptionRef.Denizen(kept),
        ruined))
    val done = discard(ready, asked, ruined)
    assertEquals(Look(done).cardsAt(home), Vector[CardId](kept, card))
    assertEquals(done.game.current.commonCards.edificeDeck.last, hall)

  test("the permission composes with an adviser limit through the walker"):
    val first = "Wrestlers"
    val second = "Battle Honors"
    val ready = playing(kept)
      .adviser(p1, first, facedown = true).adviser(p1, second, facedown = true)
      .ready
    val both = WalkerPowers(Vector(discardFirst, limitTwo))
    val tree = build(ready, p1, card)
    val parked = park(ready, tree, both)
    // A limit of two with two advisers held: a faceup adviser needs a discard.
    val faceup = as[WalkerOutcome.Parked](answer(ready, tree, parked, both,
      cardPlayDecisionId(card, "place"),
      DecisionOptionRef.Button("adviser-faceup"), p1))
    assertEquals(options(ready, tree, faceup.tree, both).toSet,
      Set[DecisionOptionRef](DecisionOptionRef.Denizen(CatalogNames.denizen(first)),
        DecisionOptionRef.Denizen(CatalogNames.denizen(second))))
    // And the site still offers the optional discard.
    val site = as[WalkerOutcome.Parked](answer(ready, tree, parked, both,
      cardPlayDecisionId(card, "place"), DecisionOptionRef.Button("site"), p1))
    assertEquals(options(ready, tree, site.tree, both).head, noReplacement)

  test("without the permission a site with room asks no discard"):
    val ready = playing(kept).ready
    val none = WalkerPowers.empty
    val tree = build(ready, p1, card)
    val done = as[WalkerOutcome.Finished](answer(ready, tree,
      park(ready, tree, none), none, cardPlayDecisionId(card, "place"),
      DecisionOptionRef.Button("site"), p1)).treeless
    assertEquals(Look(done).cardsAt(home), Vector[CardId](kept, card))
