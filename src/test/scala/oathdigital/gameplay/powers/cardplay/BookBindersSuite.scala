package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powerresolver.{PowerCtx, Transform}
import oathdigital.gameplay.powers.{CardStaging, PowerFixture, SearchFixture,
  TargetsFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._

class BookBindersSuite extends munit.FunSuite:
  import PowerFixture._

  private val binders = DenizenId("140")
  private val holder = TargetsFixture.others(base).head
  private val parked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog))

  /** A Search drawing Faith, `owner` holding Book Binders, and exactly these
    * banks holding favor.
    */
  private def arranged(banks: Map[Suit, Int], owner: PlayerId = holder,
      orientation: Orientation = Orientation.FaceUp,
      vision: VisionId = VisionRules.Faith): ReadyGame =
    val ready = TargetsFixture.giveAdviser(CardStaging.without(
      SearchFixture.staged(Vector(vision)), binders), owner, binders,
      orientation)
    ready.copy(banks = ready.banks.copy(favor =
      Suit.all.map(suit => suit -> banks.getOrElse(suit, 0)).toMap))

  private def faceup(ready: ReadyGame): OathTransition =
    SearchFixture.play(ready, Vector.empty, VisionRules.Faith, "adviser-faceup")

  private def favor(ready: ReadyGame, id: PlayerId): Int =
    player(ready, id).board.favor

  test("Book Binders is a registered persistent rule, so it is automatic"):
    val power = BookBinders.forCatalog(catalog).get
    assertEquals(power.resolution, PowerResolution.Automatic)
    assert(WalkerPowerCatalog.default(catalog).powers.exists(
      _.id == BookBinders.id))

  test("another player's faceup Vision gives the holder a choice of bank, " +
      "then two favor"):
    val ready = arranged(Map(Suit.Arcane -> 3, Suit.Order -> 3))
    val placed = faceup(ready)
    val choice = BookBinders.decisionId(ready, holder, VisionRules.Faith)
    parked.assertParked(placed.state, ActionRef.Search, choice, holder)
    val taken = SearchFixture.rules.resolveWalker(placed.state, holder, choice,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(Suit.Order)))
      .toOption.get
    val after = SearchFixture.after(taken)
    assertEquals(favor(after, holder), favor(ready, holder) + 2)
    assertEquals(after.banks.favor(Suit.Order), 1)
    assertEquals(after.banks.favor(Suit.Arcane), 3)

  test("a single stocked bank is taken without asking"):
    val ready = arranged(Map(Suit.Hearth -> 5))
    val after = SearchFixture.after(faceup(ready))
    assertEquals(favor(after, holder), favor(ready, holder) + 2)
    assertEquals(after.banks.favor(Suit.Hearth), 3)

  test("a bank holding one favor gives one"):
    val ready = arranged(Map(Suit.Hearth -> 1))
    val after = SearchFixture.after(faceup(ready))
    assertEquals(favor(after, holder), favor(ready, holder) + 1)

  test("with every bank empty nothing happens"):
    val ready = arranged(Map.empty)
    val after = SearchFixture.after(faceup(ready))
    assertEquals(favor(after, holder), favor(ready, holder))

  test("the holder's own faceup Vision gives nothing"):
    val ready = arranged(Map(Suit.Hearth -> 5), owner = actor)
    val after = SearchFixture.after(faceup(ready))
    assertEquals(after.banks.favor(Suit.Hearth), 5)

  test("a facedown Book Binders is not active"):
    val ready = arranged(Map(Suit.Hearth -> 5),
      orientation = Orientation.FaceDown)
    val after = SearchFixture.after(faceup(ready))
    assertEquals(favor(after, holder), favor(ready, holder))

  test("a facedown play gives nothing"):
    val ready = arranged(Map(Suit.Hearth -> 5))
    val after = SearchFixture.after(SearchFixture.play(ready, Vector.empty,
      VisionRules.Faith, "adviser-facedown"))
    assertEquals(after.banks.favor(Suit.Hearth), 5)

  test("the Conspiracy triggers it"):
    val ready = arranged(Map(Suit.Arcane -> 3, Suit.Order -> 3),
      vision = VisionRules.Conspiracy)
    val power = BookBinders.forCatalog(catalog).get
    val hook = CardPlayedFaceup(VisionRules.Conspiracy,
      RuleSourceRef.Adviser(actor, VisionRules.Conspiracy))
    val ctx = PowerCtx(ready, actor, power.source,
      PowerWindow.ActionCardPlayedFaceup, Vector.empty, hook)
    val Transform(fn) =
      power.contributions(PowerWindow.ActionCardPlayedFaceup).head: @unchecked
    val folded = fn(ctx, Vector(hook))
    assertEquals(folded.head, hook)
    assertEquals(folded.collect { case decide: Decide => decide.owner },
      Vector(holder))
