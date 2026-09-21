package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{PowerFixture, SearchFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class WelcomingPartySuite extends munit.FunSuite {
  import PowerFixture._
  import SearchFixture._

  private val party = DenizenId("50")
  private val modifiers = Vector(WelcomingParty.id)
  private val plain = denizensOf(Suit.Arcane).take(3)

  private def withParty(top: Vector[WorldCardId]): ReadyGame =
    atHome(SearchFixture.staged(top), party)

  private def favor(ready: ReadyGame): Int = player(ready).board.favor
  private def hearthBank(ready: ReadyGame): Int =
    ready.banks.favor.getOrElse(Suit.Hearth, 0)

  test("Welcoming Party is a registered selected Search modifier") {
    val power = WelcomingParty.forCatalog(catalog).get
    assertEquals(power.cardId, party)
    assertEquals(power.resolution, PowerResolution.PlayerSelected)
    assert(rules.offerableWalkerPowers(withParty(plain), actor, ActionRef.Search)
      .toOption.get.map(_.id).contains(WelcomingParty.id))
  }

  test("a denizen played faceup straight from the draw, to a site, gains 1 " +
      "favor from the Hearth bank") {
    val ready = withParty(plain)
    val done = play(ready, modifiers, plain.head, "site")
    val after = SearchFixture.after(done)
    // The play itself also gains 1 favor from the card's own suit bank (Arcane),
    // so Hearth is the only bank the power touches.
    assertEquals(hearthBank(after), hearthBank(ready) - 1)
    assertEquals(favor(after), favor(ready) + 2)
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), after)
  }

  test("a card played as a faceup adviser gains it too, of any suit") {
    val ready = withParty(plain)
    val after = SearchFixture.after(play(ready, modifiers, plain.head,
      "adviser-faceup"))
    assertEquals(hearthBank(after), hearthBank(ready) - 1)
    assertEquals(favor(after), favor(ready) + 1)
  }

  test("a card drawn by a Search and placed facedown is not played faceup, so " +
      "it gains nothing") {
    val ready = withParty(plain)
    val done = play(ready, modifiers, plain.head, "adviser-facedown")
    val after = SearchFixture.after(done)
    assertEquals(hearthBank(after), hearthBank(ready))
    assertEquals(favor(after), favor(ready))
  }

  test("a card that was already a facedown adviser is not first drawn, so it " +
      "gains nothing, played faceup or to a site") {
    val card = plain.head
    val ready = asAdviser(withParty(plain.drop(1)), card, Orientation.FaceDown)
    Vector("adviser-faceup", "site").foreach { button =>
      val after = SearchFixture.after(playFacedown(ready, modifiers, card, button))
      assertEquals(hearthBank(after), hearthBank(ready), button)
    }
  }

  test("a discard and an unselected power gain nothing") {
    val ready = withParty(plain)
    val discarded = SearchFixture.after(play(ready, modifiers, plain.head,
      "discard"))
    assertEquals(hearthBank(discarded), hearthBank(ready))
    val unselected = SearchFixture.after(play(ready, Vector.empty, plain.head,
      "adviser-faceup"))
    assertEquals(hearthBank(unselected), hearthBank(ready))
  }

  test("a Vision is not a denizen, and a card does not trigger on its own play") {
    val power = WelcomingParty.forCatalog(catalog).get
    val vision = CardPlayedFaceup(VisionRules.Faith,
      RuleSourceRef.Adviser(actor, VisionRules.Faith))
    val ctx = PowerCtx(atHome(base, party), actor, power.source,
      PowerWindow.ActionCardPlayedFaceup, Vector.empty, vision,
      Some(ActionRef.Search))
    assert(!power.applicable(ctx))
    assert(!power.applicable(ctx.copy(operation = CardPlayedFaceup(party,
      RuleSourceRef.SiteCard(home(base), party)))))
    val played = CardPlayedFaceup(plain.head,
      RuleSourceRef.SiteCard(home(base), plain.head))
    assert(power.applicable(ctx.copy(operation = played)))
    assert(!power.applicable(ctx.copy(operation = played,
      procedure = Some(ActionRef.PlayFacedownAdviser))))
    assert(!power.applicable(ctx.copy(operation = played, procedure = None)))
    assert(!power.applicable(ctx.copy(operation =
      CardPlayedFacedown(plain.head, actor),
      window = PowerWindow.ActionCardPlayedFacedown)))
  }

  test("an empty Hearth bank gives nothing") {
    val ready = withParty(plain).copy(banks = base.banks.copy(favor =
      base.banks.favor.updated(Suit.Hearth, 0)))
    val after = SearchFixture.after(play(ready, modifiers, plain.head,
      "adviser-faceup"))
    assertEquals(favor(after), favor(ready))
  }
}
