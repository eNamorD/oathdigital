package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{CardStaging, PlayerFacts, PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerStepRecorded
import oathdigital.model._
import oathdigital.testkit.CatalogNames

class WildCrySuite extends munit.FunSuite:
  import PowerFixture._
  import SearchFixture._

  private val wildCry = DenizenId("189")
  private val modifiers = Vector(WildCry.id)
  private val beast = denizensOf(Suit.Beast)
  private val others = denizensOf(Suit.Hearth).take(2)

  /** Wild Cry at the pawn site, the deck topped by `kept` and two fillers. */
  private def withCry(kept: DenizenId): ReadyGame =
    atHome(SearchFixture.staged(Vector(kept) ++ others), wildCry)

  private val supplyAfterCost = 3
  private def warbands(ready: ReadyGame): Int = player(ready).board.warbands

  test("it is offered for a Search when the card is usable, and for no other action"):
    val ready = withCry(beast.head)
    def offered(action: ActionRef) = rules.offerableWalkerPowers(ready, actor,
      action).toOption.get.map(_.id)
    assert(offered(ActionRef.Search).contains(WildCry.id))
    assert(offered(ActionRef.PlayFacedownAdviser).contains(WildCry.id))
    assert(!offered(ActionRef.Travel).contains(WildCry.id))
    assert(!offered(ActionRef.Muster).contains(WildCry.id))

  test("it is not offered when the card is facedown or out of reach"):
    val facedown = asAdviser(SearchFixture.staged(Vector(beast.head) ++ others),
      wildCry, Orientation.FaceDown)
    assertEquals(start(facedown, modifiers).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "power denizen.wild-cry is not applicable to this search")))
    val elsewhere = SearchFixture.staged(Vector(beast.head) ++ others)
    assertEquals(start(elsewhere, modifiers).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "power denizen.wild-cry is not applicable to this search")))

  test("a beast denizen played to a site gains 1 Supply and 2 warbands"):
    val ready = withCry(beast.head)
    val done = play(ready, modifiers, beast.head, "site")
    val after = SearchFixture.after(done)
    assertEquals(player(after).board.supply.supply, supplyAfterCost + 1)
    assertEquals(warbands(after), warbands(ready) + 2)
    assert(done.events.collect { case step: WalkerStepRecorded => step }
      .exists(_.contributions.contains(WildCry.id)))
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events),
      after)

  test("a beast denizen played as a faceup adviser gains the same"):
    val ready = withCry(beast.head)
    val after = SearchFixture.after(play(ready, modifiers, beast.head,
      "adviser-faceup"))
    assertEquals(player(after).board.supply.supply, supplyAfterCost + 1)
    assertEquals(warbands(after), warbands(ready) + 2)

  test("a facedown play, a discard, another suit and no selection gain nothing"):
    val ready = withCry(beast.head)
    Vector("adviser-facedown", "discard").foreach { button =>
      val after = SearchFixture.after(play(ready, modifiers, beast.head, button))
      assertEquals(player(after).board.supply.supply, supplyAfterCost, button)
      assertEquals(warbands(after), warbands(ready), button)
    }
    val hearth = others.head
    val other = SearchFixture.after(play(atHome(SearchFixture.staged(
      Vector(hearth, beast.head, others(1))), wildCry), modifiers, hearth, "site"))
    assertEquals(player(other).board.supply.supply, supplyAfterCost)
    val unselected = SearchFixture.after(play(ready, Vector.empty, beast.head,
      "site"))
    assertEquals(player(unselected).board.supply.supply, supplyAfterCost)
    assertEquals(warbands(unselected), warbands(ready))

  test("a card does not trigger on its own play"):
    val hook = CardPlayedFaceup(wildCry, RuleSourceRef.Adviser(actor, wildCry))
    val power = WildCry.forCatalog(catalog)
    val ctx = PowerCtx(atHome(base, wildCry), actor, power.source,
      PowerWindow.ActionCardPlayedFaceup, Vector.empty, hook)
    assert(!power.applicable(ctx))
    val beastHook = CardPlayedFaceup(beast.head,
      RuleSourceRef.Adviser(actor, beast.head))
    assert(power.applicable(ctx.copy(operation = beastHook)))
    val facedown = CardPlayedFacedown(beast.head, actor)
    assert(!power.applicable(ctx.copy(operation = facedown,
      window = PowerWindow.ActionCardPlayedFacedown)))

  test("an empty warband bank gives what it holds"):
    val kind = PlayerFacts.forceKind(base, actor).toOption.get
    val ready = leaveInBank(withCry(beast.head), kind, 1)
    val after = SearchFixture.after(play(ready, modifiers, beast.head, "site"))
    assertEquals(warbands(after), warbands(ready) + 1)
    assertEquals(player(after).board.supply.supply, supplyAfterCost + 1)

  test("Wild Cry cannot be discarded while it is selected: it is not offered " +
      "as the replacement of a faceup adviser"):
    val extra = denizensOf(Suit.Arcane).head
    val kept = beast.head
    // The actor holds a facedown Wrestlers. With one more and Wild Cry, the
    // area of three is full and a faceup play asks for a replacement.
    val holding = asAdviser(CardStaging.without(asAdviser(asAdviser(
      withCry(kept), CatalogNames.denizen("Wrestlers"), Orientation.FaceDown),
      extra, Orientation.FaceDown), wildCry), wildCry)
    val before = player(holding).advisers.map(_.id).toSet
    assertEquals(before.size, 3)
    val started = start(holding, modifiers).toOption.get
    val chosen = keep(started, kept).toOption.get
    val asked = place(chosen, kept, "adviser-faceup").toOption.get
    assert(replace(asked, kept, wildCry).isLeft, "Wild Cry was discardable")
    val done = replace(asked, kept, extra).toOption.get
    val after = player(SearchFixture.after(done)).advisers.map(_.id).toSet
    assert(after.contains(wildCry) && after.contains(kept))
    assert(!after.contains(extra))
