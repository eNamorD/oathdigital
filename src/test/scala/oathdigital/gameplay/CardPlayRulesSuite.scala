package oathdigital.gameplay

import oathdigital.gameplay.actions.{CardPlayRules, DiscardPermission}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.AdviserLimit
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
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
  import PlacementFixture.{answer, build, cardPlayDecisionId, limitTwo,
    options, park, testPermission}

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
    val second = DiscardPermission(PowerId("test.second-discard"),
      Note(PowerId("test.second-discard"), _ => None))
    val stacked = CardPlayRules.default.permitSiteDiscard(testPermission)
      .permitSiteDiscard(second)
    assertEquals(stacked.siteDiscards.map(_.source),
      Vector(testPermission.source, second.source))

  test("without a contributor the tree is the play under the default rules"):
    val tree = build(Table.start.hand(p1, card).ready, p1, card)
    // The placement decision and the Branch that acts on its answer.
    assertEquals(tree.children.size, 2)
    assert(tree.children.head.isInstanceOf[Decide])

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

  test("a contributed limit reaches the placement: two advisers fill an area " +
      "limited to two"):
    val first = CatalogNames.denizen("Wrestlers")
    val second = CatalogNames.denizen("Battle Honors")
    val ready = Table.start.hand(p1, card)
      .adviser(p1, first, facedown = true).adviser(p1, second, facedown = true)
      .ready
    val actor = p1
    val tree = build(ready, actor, card)
    // Under the default limit of three there is room, so no discard is asked.
    val open = WalkerPowers.empty
    val opened = answer(ready, tree, park(ready, tree, open), open,
      cardPlayDecisionId(card, "place"),
      DecisionOptionRef.Button("adviser-faceup"), actor)
    assert(opened.isInstanceOf[WalkerOutcome.Finished])
    // Under a limit of two the area is full and a discard is asked first.
    val limited = WalkerPowers(Vector(limitTwo))
    val asked = as[WalkerOutcome.Parked](answer(ready, tree,
      park(ready, tree, limited), limited, cardPlayDecisionId(card, "place"),
      DecisionOptionRef.Button("adviser-faceup"), actor))
    assertEquals(options(ready, tree, asked.tree, limited).toSet,
      Set[DecisionOptionRef](DecisionOptionRef.Denizen(first),
        DecisionOptionRef.Denizen(second)))

  test("Silver Tongue and the adviser-limit read agree on the limit"):
    val tongue = SilverTongue.forCatalog(catalog)
    val held = Table.start.adviser(p1, tongue.cardId).ready
    val bare = Table.start.ready
    assertEquals(tongue.limitFor(held, p1), Some(SilverTongue.HolderLimit))
    assertEquals(AdviserLimit.of(catalog, held, p1), SilverTongue.HolderLimit)
    assertEquals(AdviserLimit.of(catalog, bare, p1), AdviserLimit.Default)
    assertEquals(tongue.limitFor(bare, p1), None)
