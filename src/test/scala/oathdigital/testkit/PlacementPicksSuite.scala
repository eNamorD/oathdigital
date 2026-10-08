package oathdigital.testkit

import oathdigital.gameplay.FacedownAdviserPlay
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** `PlacementPicks`: choosing a card play's placement answer by how it plays
  * the card, and reading what a placement option replaces. */
class PlacementPicksSuite extends munit.FunSuite:
  private val home = CatalogNames.site("Ancient City")
  private val rainBoots = CatalogNames.denizen("Rain Boots")

  private val pileRef = DecisionOptionRef.DiscardPile(Region.Provinces)
  private val slotRef = DecisionOptionRef.SiteSlot(home, 0)
  private val faceupRef = DecisionOptionRef.AdviserPlay(p1, 0, faceup = true)
  private val facedownRef = DecisionOptionRef.AdviserPlay(p1, 0, faceup = false)
  private val stopRef = DecisionOptionRef.Button("stop")

  private val pile = DecisionOption.DiscardPile(pileRef)
  private val slot = DecisionOption.SiteSlot(slotRef)
  private val faceup = DecisionOption.AdviserPlay(faceupRef)
  private val facedown = DecisionOption.AdviserPlay(facedownRef)
  /** Plays the card no way: it is not a placement. */
  private val stop = DecisionOption.Button(stopRef, "Stop")

  private def unavailable(option: DecisionOption): DecisionOption =
    DecisionOption.Unavailable(option, OptionReason.Written("not now"))

  test("offered names how each option that may be chosen plays the card, " +
      "in order, and skips an option that plays it no way"):
    assertEquals(PlacementPicks.offered(Vector(pile, stop, slot, faceup,
      facedown)), Vector(PlayedAs.Discarded, PlayedAs.AtSite, PlayedAs.Faceup,
        PlayedAs.Facedown))

  test("offered skips an option that cannot be chosen"):
    assertEquals(PlacementPicks.offered(Vector(pile, unavailable(slot))),
      Vector(PlayedAs.Discarded))

  test("find prefers an option that may be chosen to a refused one that " +
      "plays the card the same way"):
    val options = Vector(unavailable(slot),
      DecisionOption.SiteSlot(DecisionOptionRef.SiteSlot(home, 1)))
    assertEquals(PlacementPicks.find(options, PlayedAs.AtSite),
      Some(DecisionOptionRef.SiteSlot(home, 1)))

  test("find falls back to a refused option when none may be chosen, so a " +
      "test of a refusal still reaches the engine"):
    assertEquals(PlacementPicks.find(Vector(unavailable(slot)),
      PlayedAs.AtSite), Some(slotRef))

  test("find has nothing to return when no option plays the card that way"):
    assertEquals(PlacementPicks.find(Vector(pile, stop), PlayedAs.Faceup), None)

  test("pick returns the ref, and otherwise fails naming what was offered"):
    assertEquals(PlacementPicks.pick(Vector(pile, faceup), PlayedAs.Faceup),
      faceupRef)
    val failure = intercept[munit.FailException](
      PlacementPicks.pick(Vector(pile, stop), PlayedAs.Faceup))
    assert(failure.getMessage.contains(s"offered ${Vector(pileRef, stopRef)}"),
      failure.getMessage)

  test("pick over a decision picks among its choose-one options"):
    val decide = Decide("place", p1, DecisionQuery.ChooseOne(
      Vector(pile, slot)))
    assertEquals(PlacementPicks.pick(decide, PlayedAs.AtSite), slotRef)
    val notChooseOne = Decide("place", p1, DecisionQuery.ChooseMany(0, 1,
      Vector(pile)))
    intercept[munit.FailException](PlacementPicks.pick(notChooseOne,
      PlayedAs.Discarded))

  test("replaces reads the replaced card through availability, price and " +
      "badge marks, and is empty for a slot that replaces nothing"):
    val replacing = DecisionOption.Replacing(slot, rainBoots)
    assertEquals(PlacementPicks.replaces(replacing), Some(rainBoots))
    assertEquals(PlacementPicks.replaces(unavailable(replacing)),
      Some(rainBoots))
    assertEquals(PlacementPicks.replaces(slot), None)

  test("findReplacing finds the option that plays the card that way in the " +
      "place of one card, whether or not it may be chosen"):
    val binding = CatalogNames.denizen("Ancient Binding")
    val options = Vector(
      DecisionOption.Replacing(DecisionOption.SiteSlot(
        DecisionOptionRef.SiteSlot(home, 0)), rainBoots),
      unavailable(DecisionOption.Replacing(DecisionOption.SiteSlot(
        DecisionOptionRef.SiteSlot(home, 1)), binding)))
    assertEquals(PlacementPicks.findReplacing(options, PlayedAs.AtSite,
      binding), Some(DecisionOptionRef.SiteSlot(home, 1)))
    assertEquals(PlacementPicks.findReplacing(options, PlayedAs.Faceup,
      binding), None)

  test("siteSlotRefs and siteSlotOptions keep only the site slots, in order"):
    assertEquals(PlacementPicks.siteSlotRefs(Vector(pileRef, slotRef,
      faceupRef)), Vector[DecisionOptionRef](slotRef))
    assertEquals(PlacementPicks.siteSlotOptions(Vector(pile, slot, faceup)),
      Vector(slot))

  test("optionNaming names a denizen and a Vision by their own refs"):
    assertEquals(PlacementPicks.optionNaming(rainBoots),
      DecisionOptionRef.Denizen(rainBoots))
    assertEquals(PlacementPicks.optionNaming(VisionId("faith")),
      DecisionOptionRef.Vision(VisionId("faith")))

  test("parkedOptions reads the choose-one a play is parked on, and " +
      "parkedAnswer answers it by how it plays the card"):
    // Parks the real facedown-adviser play on its placement: the options a
    // parked state shows are the subject.
    val rules = new OathRules(catalog)
    val table = Table.start.pawn(p1, "Ancient City")
      .adviser(p1, rainBoots, facedown = true)
    val parked = accepted(FacedownAdviserPlay.parkedOnPlacement(rules,
      table.state, p1, rainBoots))
    assertEquals(PlacementPicks.parkedOptions(parked.state, WalkerPowers.empty)
      .map(_.ref), Vector[DecisionOptionRef](pileRef, slotRef,
        DecisionOptionRef.AdviserPlay(p1, 0, faceup = true)))
    assertEquals(PlacementPicks.parkedAnswer(parked.state, PlayedAs.AtSite,
      WalkerPowers.empty), DecisionAnswer.ChooseOneAnswer(slotRef))

  test("parkedOptions is empty when nothing is parked on a choose-one"):
    assertEquals(PlacementPicks.parkedOptions(Table.start.state,
      WalkerPowers.empty), Vector.empty)
