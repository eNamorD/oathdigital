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

/** `PlacementRules`: the adviser limits a card play is planned under, and how
  * two contributors to them compose. The powers here are test doubles.
  */
class PlacementRulesSuite extends munit.FunSuite:
  import PlacementFixture._

  /** A plain Beast card p1 plays from hand. */
  private val card = CatalogNames.denizen("Errand Boy")

  test("a limit only lowers, and a faceup limit leaves the facedown one"):
    assertEquals(PlacementRules.default.limitAdvisers(5), PlacementRules.default)
    assertEquals(PlacementRules.default.limitAdvisers(2),
      PlacementRules(2, 2, false))
    assertEquals(PlacementRules.default.limitFaceupAdvisers(2),
      PlacementRules(2, 3, false))
    assertEquals(PlacementRules.default.adviserLimit(Orientation.FaceUp), 3)
    assertEquals(PlacementRules.default.limitFaceupAdvisers(2)
      .adviserLimit(Orientation.FaceDown), 3)

  test("without a contributor the tree is the play under the default rules"):
    val tree = build(Table.start.hand(p1, card).ready, p1, card)
    assertEquals(tree.children.size, 2)
    assert(tree.children.head.isInstanceOf[Decide])

  test("two contributors compose in either order"):
    val tree = build(Table.start.hand(p1, card).ready, p1, card)
      .asInstanceOf[CardPlayProcedure.PlacementTree]
    def rulesOf(ops: Vector[Operation]): PlacementRules =
      ops.head.asInstanceOf[CardPlayProcedure.PlacementBody].rules
    val limitFirst = tree.adjust(tree.adjust(tree.children)(_.limitAdvisers(2)))(
      _.withSiteDiscardFirst)
    val discardFirstThenLimit = tree.adjust(tree.adjust(tree.children)(
      _.withSiteDiscardFirst))(_.limitAdvisers(2))
    assertEquals(rulesOf(limitFirst), PlacementRules(2, 2, true))
    assertEquals(rulesOf(discardFirstThenLimit), PlacementRules(2, 2, true))
    assertEquals(limitFirst.size, 1)

  test("a contributed limit reaches the placement: two advisers fill an area " +
      "limited to two"):
    val Vector(first, second) =
      Vector("Wrestlers", "Battle Honors").map(CatalogNames.denizen(_))
    val ready = Table.start.hand(p1, card)
      .adviser(p1, first, facedown = true).adviser(p1, second, facedown = true)
      .ready
    val actor = p1
    val tree = build(ready, actor, card)
    // Under the default limit of three there is room, so no discard is asked.
    val open = WalkerPowers.empty
    val opened = answer(ready, tree, park(ready, tree, open), open,
      decisionId(card, "place"), DecisionOptionRef.Button("adviser-faceup"),
      actor)
    assert(opened.isInstanceOf[WalkerOutcome.Finished])
    // Under a limit of two the area is full and a discard is asked first.
    val limited = WalkerPowers(Vector(limitTwo))
    val asked = answer(ready, tree, park(ready, tree, limited), limited,
      decisionId(card, "place"), DecisionOptionRef.Button("adviser-faceup"),
      actor).asInstanceOf[WalkerOutcome.Parked]
    assertEquals(options(ready, tree, asked.tree, limited).toSet,
      Set[DecisionOptionRef](DecisionOptionRef.Denizen(first),
        DecisionOptionRef.Denizen(second)))

  test("Silver Tongue and the adviser-limit read agree on the limit"):
    val tongue = SilverTongue.forCatalog(catalog).get
    val held = Table.start.adviser(p1, tongue.cardId).ready
    val bare = Table.start.ready
    assertEquals(tongue.limitFor(held, p1), Some(SilverTongue.HolderLimit))
    assertEquals(AdviserLimit.of(catalog, held, p1), SilverTongue.HolderLimit)
    assertEquals(AdviserLimit.of(catalog, bare, p1), AdviserLimit.Default)
    assertEquals(tongue.limitFor(bare, p1), None)
