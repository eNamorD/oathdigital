package oathdigital.gameplay

import oathdigital.gameplay.actions.{AdviserFace, AdviserPlayNote,
  CardPlayRules}
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
  private val tongue = CatalogNames.denizen("Silver Tongue")
  private val piper = CatalogNames.denizen("Pied Piper")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val wagon = CatalogNames.denizen("Family Wagon")
  private def faceup(held: CardId) = AdviserFace(held, Orientation.FaceUp)
  private def facedown(held: CardId) = AdviserFace(held, Orientation.FaceDown)
  
  test("one limit counts every adviser, faceup or facedown, and only lowers"):
    val rules = CardPlayRules.default.limitAdvisers(2)
    assert(rules.fits(Vector(faceup(card), facedown(tongue))))
    assert(!rules.fits(Vector(faceup(card), facedown(tongue),
      facedown(wrestlers))))
    assertEquals(CardPlayRules.default.limitAdvisers(5), CardPlayRules.default)

  test("a card's limit holds only while it is a faceup adviser after the " +
      "play"):
    // The rulebook checks a play against the state after it. Silver Tongue
    // facedown has no power, and a Silver Tongue the play replaces is gone.
    val rules = CardPlayRules.default.limitWhileFaceUp(tongue, 2)
    assertEquals(rules.limitOver(Vector(faceup(tongue), faceup(card))), 2)
    assertEquals(rules.limitOver(Vector(facedown(tongue), faceup(card))),
      CardPlayRules.DefaultAdviserLimit)
    assertEquals(rules.limitOver(Vector(faceup(card))),
      CardPlayRules.DefaultAdviserLimit)
    assertEquals(rules.limitWhileFaceUp(tongue, 3), rules)

  test("an adviser face that ignores the limit is not counted, in that " +
      "face only"):
    val rules = CardPlayRules.default.ignoreLimit(faceup(piper))
    val four = Vector(faceup(piper), faceup(card), facedown(tongue),
      faceup(wrestlers))
    assertEquals(rules.counted(four), 3)
    assert(rules.fits(four))
    assert(!rules.fits(four.updated(0, facedown(piper))))

  test("a card that lifts the limit removes it while it is a faceup " +
      "adviser after the play; cards' own limits still apply"):
    val rules = CardPlayRules.default.liftWhileFaceUp(wagon)
    val five = Vector(faceup(wagon), faceup(card), facedown(tongue),
      faceup(wrestlers), facedown(piper))
    assertEquals(rules.limitOver(five), CardPlayRules.Unbounded)
    assert(rules.fits(five))
    assertEquals(rules.limitOver(five.updated(0, facedown(wagon))),
      CardPlayRules.DefaultAdviserLimit)
    assertEquals(rules.limitWhileFaceUp(tongue, 2)
      .limitOver(five.updated(2, faceup(tongue))), 2)

  test("an adviser play's note is the first that applies after the play"):
    val first = OptionReason.Written("First")
    val second = OptionReason.Written("Second")
    val rules = CardPlayRules.default
      .noteAdviserPlay(AdviserPlayNote(first, (after, _) => after.size > 1))
      .noteAdviserPlay(AdviserPlayNote(second, (_, _) => true))
    assertEquals(rules.noteOn(Vector(faceup(tongue), faceup(card)),
      faceup(card)), Some(first))
    assertEquals(rules.noteOn(Vector(faceup(card)), faceup(card)),
      Some(second))
    assertEquals(CardPlayRules.default.noteOn(Vector(faceup(card)),
      faceup(card)), None)

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

  test("a rewrite of the play's body and an adjust compose in either " +
      "order"):
    val tree = as[CardPlayProcedure.PlacementTree](
      build(Table.start.hand(p1, card).ready, p1, card))
    val marker = Note(PowerId("test.marker"), _ => None)
    def marked(ops: Vector[Operation]): Vector[Operation] = ops.map {
      case body: RewritableComposite =>
        body.rewritten(child => Sequence(Vector(child, marker)))
      case other => other
    }
    def limited(ops: Vector[Operation]): Vector[Operation] =
      tree.adjust(ops)(_.limitAdvisers(2))
    Vector(limited(marked(tree.children)), marked(limited(tree.children)))
      .foreach { ops =>
        val body = as[CardPlayProcedure.PlacementBody](ops.head)
        assertEquals(body.rules.adviserLimit, 2)
        assert(body.children.nonEmpty && body.children.forall {
          case Sequence(Vector(_, last), _) => last eq marker
          case _ => false
        })
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
