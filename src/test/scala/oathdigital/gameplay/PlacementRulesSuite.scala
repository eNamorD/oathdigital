package oathdigital.gameplay

import oathdigital.gameplay.actions.PlacementRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.AdviserLimit
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.as

/** `PlacementRules`: the adviser limits a card play is planned under, and how
  * two contributors to them compose. `limitTwo` is PlacementFixture's stub
  * power that lowers both adviser limits to two.
  */
class PlacementRulesSuite extends munit.FunSuite:
  import PlacementFixture.{answer, build, cardPlayDecisionId, limitTwo,
    options, park}

  /** A plain Beast card p1 plays from hand. */
  private val card = CatalogNames.denizen("Errand Boy")

  test("a limit only lowers, and a faceup limit leaves the facedown one"):
    assertEquals(PlacementRules.default.limitAdvisers(5), PlacementRules.default)
    assertEquals(PlacementRules.default.limitAdvisers(2),
      PlacementRules(faceupAdviserLimit = 2, facedownAdviserLimit = 2,
        siteDiscardFirst = false))
    assertEquals(PlacementRules.default.limitFaceupAdvisers(2),
      PlacementRules(faceupAdviserLimit = 2, facedownAdviserLimit = 3,
        siteDiscardFirst = false))
    assertEquals(PlacementRules.default.adviserLimit(Orientation.FaceUp), 3)
    assertEquals(PlacementRules.default.limitFaceupAdvisers(2)
      .adviserLimit(Orientation.FaceDown), 3)

  test("without a contributor the tree is the play under the default rules"):
    val tree = build(Table.start.hand(p1, card).ready, p1, card)
    // The placement decision and the placement body.
    assertEquals(tree.children.size, 2)
    assert(tree.children.head.isInstanceOf[Decide])

  test("two contributors compose in either order"):
    val tree = as[CardPlayProcedure.PlacementTree](
      build(Table.start.hand(p1, card).ready, p1, card))
    def rulesOf(ops: Vector[Operation]): PlacementRules =
      as[CardPlayProcedure.PlacementBody](ops.head).rules
    val limitFirst = tree.adjust(tree.adjust(tree.children)(_.limitAdvisers(2)))(
      _.withSiteDiscardFirst)
    val discardFirstThenLimit = tree.adjust(tree.adjust(tree.children)(
      _.withSiteDiscardFirst))(_.limitAdvisers(2))
    val composed = PlacementRules(faceupAdviserLimit = 2,
      facedownAdviserLimit = 2, siteDiscardFirst = true)
    assertEquals(rulesOf(limitFirst), composed)
    assertEquals(rulesOf(discardFirstThenLimit), composed)
    // Both contributors fold into the one placement body.
    assertEquals(limitFirst.size, 1)

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
