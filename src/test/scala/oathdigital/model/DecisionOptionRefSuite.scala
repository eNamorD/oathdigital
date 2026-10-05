package oathdigital.model

import oathdigital.testkit.CatalogNames

class DecisionOptionRefSuite extends munit.FunSuite:
  private val hall = DecisionOptionRef.Edifice(
    CatalogNames.edifice("Hall of Ministers"))

  test("an edifice reference spells itself as a kind and id pair and parses back"):
    assertEquals((hall.kind, hall.wireId), ("edifice", "E16"))
    assertEquals(DecisionOptionRef.fromWire(hall.kind, hall.wireId), Some(hall))

  test("a blank edifice id is not a reference"):
    assertEquals(DecisionOptionRef.fromWire("edifice", "  "), None)

  test("an edifice reference is presentable from the reference alone"):
    assertEquals(DecisionOption.forRef(hall), Some(DecisionOption.Edifice(hall)))

  private val slot = DecisionOptionRef.RelicSlot(PlayerId("blue"), 2)
  private val banner =
    DecisionOptionRef.Banner(oathdigital.model.Banner.DarkestSecret)

  test("a relic slot spells itself as a kind and owner-and-index id and parses back"):
    assertEquals((slot.kind, slot.wireId), ("relic-slot", "blue:2"))
    assertEquals(DecisionOptionRef.fromWire(slot.kind, slot.wireId), Some(slot))

  test("a relic slot owner may contain a colon and still round trips"):
    val odd = DecisionOptionRef.RelicSlot(PlayerId("a:b"), 0)
    assertEquals(DecisionOptionRef.fromWire(odd.kind, odd.wireId), Some(odd))

  test("a malformed relic slot is not a reference"):
    Vector("blue", "blue:", ":2", "blue:-1", "blue:x", "blue:1.5").foreach { id =>
      assertEquals(DecisionOptionRef.fromWire("relic-slot", id), None, id)
    }

  private val adviser = DecisionOptionRef.AdviserSlot(PlayerId("blue"), 1)

  test("an adviser slot spells itself as a kind and owner-and-index id and " +
      "parses back"):
    assertEquals((adviser.kind, adviser.wireId), ("adviser-slot", "blue:1"))
    assertEquals(DecisionOptionRef.fromWire(adviser.kind, adviser.wireId),
      Some(adviser))

  test("an adviser slot owner may contain a colon and still round trips"):
    val odd = DecisionOptionRef.AdviserSlot(PlayerId("a:b"), 0)
    assertEquals(DecisionOptionRef.fromWire(odd.kind, odd.wireId), Some(odd))

  test("a malformed adviser slot is not a reference"):
    Vector("blue", "blue:", ":2", "blue:-1", "blue:x", "blue:1.5").foreach { id =>
      assertEquals(DecisionOptionRef.fromWire("adviser-slot", id), None, id)
    }

  test("an adviser slot is presentable from the reference alone"):
    assertEquals(DecisionOption.forRef(adviser),
      Some(DecisionOption.AdviserSlot(adviser)))

  test("a banner spells itself as its key and an unknown key is not a reference"):
    assertEquals((banner.kind, banner.wireId), ("banner", "darkest-secret"))
    assertEquals(DecisionOptionRef.fromWire(banner.kind, banner.wireId),
      Some(banner))
    assertEquals(DecisionOptionRef.fromWire("banner", "the-crown"), None)

  test("a relic slot and a banner are presentable from the reference alone"):
    assertEquals(DecisionOption.forRef(slot),
      Some(DecisionOption.RelicSlot(slot)))
    assertEquals(DecisionOption.forRef(banner),
      Some(DecisionOption.Banner(banner)))

  private val siteSlot = DecisionOptionRef.SiteSlot(
    CatalogNames.site("Ancient City"), 2)
  private val adviserPlay =
    DecisionOptionRef.AdviserPlay(PlayerId("blue"), 1, faceup = false)
  private val revealed = DecisionOptionRef.RevealedVision(PlayerId("blue"))
  private val pile = DecisionOptionRef.DiscardPile(Region.Provinces)

  test("the card slot refs spell themselves as kind and id pairs and parse back"):
    assertEquals((siteSlot.kind, siteSlot.wireId),
      ("site-slot", s"${siteSlot.site.value}:2"))
    assertEquals((adviserPlay.kind, adviserPlay.wireId),
      ("adviser-play", "blue:1:facedown"))
    assertEquals((revealed.kind, revealed.wireId), ("revealed-vision", "blue"))
    assertEquals((pile.kind, pile.wireId), ("discard-pile", "provinces"))
    Vector[DecisionOptionRef](siteSlot, adviserPlay,
      adviserPlay.copy(faceup = true), revealed, pile).foreach(ref =>
      assertEquals(DecisionOptionRef.fromWire(ref.kind, ref.wireId), Some(ref),
        ref.toString))

  test("a card slot's site or owner may contain a colon and still round trips"):
    Vector[DecisionOptionRef](DecisionOptionRef.SiteSlot(SiteId("a:b"), 0),
      DecisionOptionRef.AdviserPlay(PlayerId("a:b"), 2, faceup = true),
      DecisionOptionRef.RevealedVision(PlayerId("a:b"))).foreach(ref =>
      assertEquals(DecisionOptionRef.fromWire(ref.kind, ref.wireId), Some(ref),
        ref.toString))

  test("a malformed card slot ref is not a reference"):
    Vector("x", "x:", ":2", "x:-1", "x:y").foreach(id =>
      assertEquals(DecisionOptionRef.fromWire("site-slot", id), None, id))
    Vector("blue:1", "blue:1:up", "blue:-1:faceup", ":1:faceup",
      "blue::faceup").foreach(id =>
      assertEquals(DecisionOptionRef.fromWire("adviser-play", id), None, id))
    assertEquals(DecisionOptionRef.fromWire("discard-pile", "elsewhere"), None)
    assertEquals(DecisionOptionRef.fromWire("revealed-vision", " "), None)

  test("the card slot refs are presentable from the reference alone"):
    assertEquals(DecisionOption.forRef(siteSlot),
      Some(DecisionOption.SiteSlot(siteSlot)))
    assertEquals(DecisionOption.forRef(adviserPlay),
      Some(DecisionOption.AdviserPlay(adviserPlay)))
    assertEquals(DecisionOption.forRef(revealed),
      Some(DecisionOption.RevealedVision(revealed)))
    assertEquals(DecisionOption.forRef(pile),
      Some(DecisionOption.DiscardPile(pile)))

  test("a Replace mark keeps the wrapped option's reference and availability"):
    val marked = DecisionOption.Replacing(DecisionOption.SiteSlot(siteSlot),
      CatalogNames.denizen("Wrestlers"))
    assertEquals(marked.ref, siteSlot)
    assert(marked.available)
    assert(!DecisionOption.Unavailable(marked, OptionReason.Written("No"))
      .available)
