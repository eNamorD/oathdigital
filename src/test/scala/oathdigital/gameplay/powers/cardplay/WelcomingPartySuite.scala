package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{PowerFixture, SearchFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}

class WelcomingPartySuite extends munit.FunSuite:
  import PowerFixture._
  import SearchFixture._

  private val party = CatalogNames.denizen("Welcoming Party")
  private val modifiers = Vector(WelcomingParty.id)
  // Three Arcane denizens whose powers these tests do not use; p1 plays the
  // first, Ward of Silence.
  private val plain = Vector("Ward of Silence", "Arcane Brokers",
    "Arcane Armor").map(CatalogNames.denizen(_))
  // p1's site on the quiet table.
  private val home = CatalogNames.site("Ancient City")

  /** p1 at Ancient City beside Welcoming Party, the deck topped by `top`. */
  private def partyAtHome(top: Vector[WorldCardId]): Table =
    Table.from(SearchFixture.staged(top, supply = 5)).pawn(actor, home)
      .denizen(party, at = home)
  private def withParty(top: Vector[WorldCardId]): ReadyGame =
    partyAtHome(top).unchecked

  private def favor(ready: ReadyGame): Int = Look(ready).favor(actor)
  private def hearthBank(ready: ReadyGame): Int =
    ready.banks.favor.getOrElse(Suit.Hearth, 0)

  test("a denizen played faceup straight from the draw, to a site, gains 1 " +
      "favor from the Hearth bank"):
    val ready = withParty(plain)
    val done = play(ready, modifiers, plain.head, "site")
    val after = SearchFixture.after(done)
    // The play itself also gains 1 favor from the card's own suit bank (Arcane),
    // so Hearth is the only bank the power touches.
    assertEquals(hearthBank(after), hearthBank(ready) - 1)
    assertEquals(favor(after), favor(ready) + 2)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), after)

  test("a card played as a faceup adviser gains it too, of any suit"):
    val ready = withParty(plain)
    val after = SearchFixture.after(play(ready, modifiers, plain.head,
      "adviser-faceup"))
    assertEquals(hearthBank(after), hearthBank(ready) - 1)
    // An adviser play takes no favor from the card's own suit bank, only 1
    // from the Hearth bank.
    assertEquals(favor(after), favor(ready) + 1)

  test("a card drawn by a Search and placed facedown is not played faceup, so " +
      "it gains nothing"):
    val ready = withParty(plain)
    val done = play(ready, modifiers, plain.head, "adviser-facedown")
    val after = SearchFixture.after(done)
    assertEquals(hearthBank(after), hearthBank(ready))
    assertEquals(favor(after), favor(ready))

  test("a card that was already a facedown adviser is not first drawn, so it " +
      "gains nothing, played faceup or to a site"):
    val card = plain.head
    val ready = asAdviser(withParty(plain.drop(1)), card, Orientation.FaceDown)
    Vector("adviser-faceup", "site").foreach { button =>
      val after = SearchFixture.after(playFacedown(ready, modifiers, card, button))
      assertEquals(hearthBank(after), hearthBank(ready), button)
    }

  test("a discard and an unselected power gain nothing"):
    val ready = withParty(plain)
    val discarded = SearchFixture.after(play(ready, modifiers, plain.head,
      "discard"))
    assertEquals(hearthBank(discarded), hearthBank(ready))
    val unselected = SearchFixture.after(play(ready, Vector.empty, plain.head,
      "adviser-faceup"))
    assertEquals(hearthBank(unselected), hearthBank(ready))

  test("it applies only to another denizen played faceup from a Search"):
    val power = WelcomingParty.forCatalog(catalog)
    val vision = CardPlayedFaceup(VisionRules.Faith,
      RuleSourceRef.Adviser(actor, VisionRules.Faith))
    val ctx = PowerCtx(Table.start.denizen(party, at = home).ready, actor,
      power.source,
      PowerWindow.ActionCardPlayedFaceup, Vector.empty, vision,
      Some(ActionRef.Search))
    assert(!power.applicable(ctx))
    assert(!power.applicable(ctx.copy(operation = CardPlayedFaceup(party,
      RuleSourceRef.SiteCard(home, party)))))
    val played = CardPlayedFaceup(plain.head,
      RuleSourceRef.SiteCard(home, plain.head))
    assert(power.applicable(ctx.copy(operation = played)))
    assert(!power.applicable(ctx.copy(operation = played,
      procedure = Some(ActionRef.PlayFacedownAdviser))))
    assert(!power.applicable(ctx.copy(operation = played, procedure = None)))
    assert(!power.applicable(ctx.copy(operation =
      CardPlayedFacedown(plain.head, actor),
      window = PowerWindow.ActionCardPlayedFacedown)))

  test("an empty Hearth bank gives nothing"):
    val ready = partyAtHome(plain).bankFavor(Suit.Hearth, 0).unchecked
    val after = SearchFixture.after(play(ready, modifiers, plain.head,
      "adviser-faceup"))
    assertEquals(favor(after), favor(ready))
