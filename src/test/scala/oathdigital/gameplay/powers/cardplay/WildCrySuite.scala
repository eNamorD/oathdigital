package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{PlayerFacts, PowerFixture, SearchFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerStepRecorded
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Unwrap.accepted

class WildCrySuite extends munit.FunSuite:
  import PowerFixture._
  import SearchFixture._

  private val wildCry = CatalogNames.denizen("Wild Cry")
  private val modifiers = Vector(WildCry.id)
  // A Beast denizen p1 plays, and two Hearth fillers; their powers are not
  // used here.
  private val beastCard = CatalogNames.denizen("Errand Boy")
  private val others = Vector("Homesteaders", "Land Warden")
    .map(CatalogNames.denizen(_))
  // p1's site on the quiet table, and another site.
  private val home = CatalogNames.site("Ancient City")
  private val elsewhere = CatalogNames.site("Broken Peaks")

  /** Wild Cry at p1's site, Ancient City, the deck topped by `kept` and two
    * fillers, with 5 Supply. */
  private def withCry(kept: DenizenId): ReadyGame =
    Table.from(SearchFixture.staged(Vector(kept) ++ others, supply = 5))
      .denizen(wildCry, at = home).unchecked

  // 5 Supply less the world Search's 2.
  private val supplyAfterCost = 3
  private def warbands(ready: ReadyGame): Int = Look(ready).boardWarbands(actor)

  test("it is offered for a Search and a Play Facedown Adviser, not for " +
      "Travel or Muster"):
    val ready = withCry(beastCard)
    def offered(action: ActionRef) = accepted(
      rules.offerableWalkerPowers(ready, actor, action)).map(_.id)
    assert(offered(ActionRef.Search).contains(WildCry.id))
    assert(offered(ActionRef.PlayFacedownAdviser).contains(WildCry.id))
    assert(!offered(ActionRef.Travel).contains(WildCry.id))
    assert(!offered(ActionRef.Muster).contains(WildCry.id))

  test("it is not offered when the card is facedown or out of reach"):
    val facedown = Table.from(SearchFixture.staged(Vector(beastCard) ++ others,
      supply = 5)).adviser(actor, wildCry, facedown = true).unchecked
    assertEquals(start(facedown, modifiers).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "power denizen.wild-cry is not applicable to this search")))
    // Wild Cry stands at Broken Peaks, away from p1.
    val outOfReach = Table.from(
      SearchFixture.staged(Vector(beastCard) ++ others, supply = 5))
      .denizen(wildCry, at = elsewhere).unchecked
    assertEquals(start(outOfReach, modifiers).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        "power denizen.wild-cry is not applicable to this search")))

  test("a beast denizen played to a site gains 1 Supply and 2 warbands"):
    val ready = withCry(beastCard)
    val done = play(ready, modifiers, beastCard, "site")
    val after = SearchFixture.after(done)
    assertEquals(Look(after).supply(actor),
      supplyAfterCost + 1)
    assertEquals(warbands(after), warbands(ready) + 2)
    assert(done.events.collect { case step: WalkerStepRecorded => step }
      .exists(_.contributions.contains(WildCry.id)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events),
      after)

  test("a beast denizen played as a faceup adviser gains the same"):
    val ready = withCry(beastCard)
    val after = SearchFixture.after(play(ready, modifiers, beastCard,
      "adviser-faceup"))
    assertEquals(Look(after).supply(actor),
      supplyAfterCost + 1)
    assertEquals(warbands(after), warbands(ready) + 2)

  test("a facedown play, a discard, another suit and no selection gain nothing"):
    val ready = withCry(beastCard)
    Vector("adviser-facedown", "discard").foreach { button =>
      val after = SearchFixture.after(play(ready, modifiers, beastCard, button))
      assertEquals(Look(after).supply(actor),
        supplyAfterCost, button)
      assertEquals(warbands(after), warbands(ready), button)
    }
    val hearth = others.head
    val hearthFirst = Table.from(SearchFixture.staged(
      Vector(hearth, beastCard, others(1)), supply = 5))
      .denizen(wildCry, at = home).unchecked
    val other = SearchFixture.after(
      play(hearthFirst, modifiers, hearth, "site"))
    assertEquals(Look(other).supply(actor), supplyAfterCost)
    val unselected = SearchFixture.after(play(ready, Vector.empty, beastCard,
      "site"))
    assertEquals(Look(unselected).supply(actor),
      supplyAfterCost)
    assertEquals(warbands(unselected), warbands(ready))

  test("a card does not trigger on its own play"):
    val hook = CardPlayedFaceup(wildCry, RuleSourceRef.Adviser(actor, wildCry))
    val power = WildCry.forCatalog(catalog)
    val ctx = PowerCtx(Table.start.denizen(wildCry, at = home).ready, actor,
      power.source,
      PowerWindow.ActionCardPlayedFaceup, Vector.empty, hook)
    assert(!power.applicable(ctx))
    val beastHook = CardPlayedFaceup(beastCard,
      RuleSourceRef.Adviser(actor, beastCard))
    assert(power.applicable(ctx.copy(operation = beastHook)))
    val facedown = CardPlayedFacedown(beastCard, actor)
    assert(!power.applicable(ctx.copy(operation = facedown,
      window = PowerWindow.ActionCardPlayedFacedown)))

  test("a warband bank holding only 1 gives 1 warband and 1 Supply"):
    val kind = accepted(PlayerFacts.forceKind(quietTable, actor))
    val ready = leaveInBank(withCry(beastCard), kind, 1)
    val after = SearchFixture.after(play(ready, modifiers, beastCard, "site"))
    assertEquals(warbands(after), warbands(ready) + 1)
    assertEquals(Look(after).supply(actor),
      supplyAfterCost + 1)

  test("Wild Cry cannot be discarded while it is selected: it is not offered " +
      "as the replacement of a faceup adviser"):
    val extra = CatalogNames.denizen("Ward of Silence")
    val kept = beastCard
    // The actor holds a facedown Wrestlers, a facedown Ward of Silence and a
    // faceup Wild Cry: the area of three is full, and a faceup play asks for
    // a replacement.
    val holding = Table.from(withCry(kept))
      .adviser(actor, "Wrestlers", facedown = true)
      .adviser(actor, extra, facedown = true).adviser(actor, wildCry).unchecked
    val before = Look(holding).player(actor).advisers.map(_.id).toSet
    assertEquals(before.size, 3)
    val started = accepted(start(holding, modifiers))
    val chosen = accepted(keep(started, kept))
    val asked = accepted(place(chosen, kept, "adviser-faceup"))
    assert(replace(asked, kept, wildCry).isLeft, "Wild Cry was discardable")
    val done = accepted(replace(asked, kept, extra))
    val after = Look(SearchFixture.after(done)).player(actor).advisers.map(_.id)
      .toSet
    assert(after.contains(wildCry) && after.contains(kept))
    assert(!after.contains(extra))
