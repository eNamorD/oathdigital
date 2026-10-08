package oathdigital.gameplay

import oathdigital.gameplay.actions.CardPlayRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.as

/** `CardPlayRules`: the adviser limits and site discard permissions a card
  * play is planned under, and how two contributors to them compose.
  * `limitTwo` is PlacementFixture's stub power that lowers the adviser
  * limit to two, and `testPermission` its plain site discard permission.
  */
class CardPlayRulesSuite extends munit.FunSuite:
  import PlacementFixture.{build, limitTwo,
    options, park, secondTestPermission, testPermission}

  /** A plain Beast card p1 plays from hand. */
  private val card = CatalogNames.denizen("Errand Boy")

  test("one limit counts every adviser, faceup or facedown, and only lowers"):
    val rules = CardPlayRules.default.limitAdvisers(2)
    assertEquals(rules.limitAfter(faceup = true), 2)
    assertEquals(rules.limitAfter(faceup = false), 2)
    assertEquals(CardPlayRules.default.limitAdvisers(5), CardPlayRules.default)

  test("the played card's own limit counts only when it is played faceup"):
    // The rulebook checks a play that changes the adviser limit against the
    // new limit. Silver Tongue played facedown has no power, so the old limit
    // holds for that play.
    val rules = CardPlayRules.default.limitPlayedCard(2)
    assertEquals(rules.limitAfter(faceup = true), 2)
    assertEquals(rules.limitAfter(faceup = false),
      CardPlayRules.DefaultAdviserLimit)

  test("site discard permissions stack in the order they are given"):
    val stacked = CardPlayRules.default.permitSiteDiscard(testPermission)
      .permitSiteDiscard(secondTestPermission)
    assertEquals(stacked.siteDiscards.map(_.source),
      Vector(testPermission.source, secondTestPermission.source))

  test("without a contributor the tree is the play under the default rules"):
    val ready = Table.start.pawn(p1, "Ancient City").hand(p1, card).ready
    val tree = build(ready, p1, card)
    // Ancient City is empty and p1 holds no adviser, so the default play
    // offers the discard pile, the first site slot and the first adviser slot
    // each way.
    assertEquals(options(ready, tree, park(ready, tree, WalkerPowers.empty),
      WalkerPowers.empty), Vector[DecisionOptionRef](
        DecisionOptionRef.DiscardPile(Region.Provinces),
        DecisionOptionRef.SiteSlot(CatalogNames.site("Ancient City"), 0),
        DecisionOptionRef.AdviserPlay(p1, 0, faceup = true),
        DecisionOptionRef.AdviserPlay(p1, 0, faceup = false)))

  test("two contributors compose in either order"):
    val tree = as[CardPlayProcedure.PlacementTree](
      build(Table.start.hand(p1, card).ready, p1, card))
    def rulesOf(ops: Vector[Operation]): CardPlayRules =
      as[CardPlayProcedure.PlacementBody](ops.head).rules
    val limitFirst = tree.adjust(tree.adjust(tree.children)(
      _.limitAdvisers(2)))(_.permitSiteDiscard(testPermission))
    val permitFirst = tree.adjust(tree.adjust(tree.children)(
      _.permitSiteDiscard(testPermission)))(_.limitAdvisers(2))
    Vector(limitFirst, permitFirst).foreach { ops =>
      assertEquals(rulesOf(ops).adviserLimit, 2)
      assertEquals(rulesOf(ops).siteDiscards.map(_.source),
        Vector(testPermission.source))
      // Both contributors fold into the one placement body.
      assertEquals(ops.size, 1)
    }

  test("a contributed limit reaches the placement: two advisers fill an " +
      "area limited to two"):
    val first = CatalogNames.denizen("Wrestlers")
    val second = CatalogNames.denizen("Battle Honors")
    val ready = Table.start.hand(p1, card)
      .adviser(p1, first, facedown = true).adviser(p1, second, facedown = true)
      .ready
    val tree = build(ready, p1, card)
    def faceup(powers: WalkerPowers): Vector[DecisionOptionRef] =
      options(ready, tree, park(ready, tree, powers), powers).filter(ref =>
        CardPlayProcedure.playedAs(ref).contains(PlayedAs.Faceup))
    // Under the default limit of three there is room.
    assertEquals(faceup(WalkerPowers.empty), Vector[DecisionOptionRef](
      DecisionOptionRef.AdviserPlay(p1, 2, faceup = true)))
    // Under a limit of two the area is full: each adviser is a Replace slot.
    assertEquals(faceup(WalkerPowers(Vector(limitTwo))),
      Vector[DecisionOptionRef](
        DecisionOptionRef.AdviserPlay(p1, 0, faceup = true),
        DecisionOptionRef.AdviserPlay(p1, 1, faceup = true)))
