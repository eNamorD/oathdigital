package oathdigital.presentation

class VisualResolverSuite extends munit.FunSuite {
  private val reference = ImageRef("theme/cards/vision")
  private val card = CardView(
    id = ViewId("card:vision"),
    label = AccessibleLabel("Vision"),
    image = Some(reference),
    fallback = FallbackVisual("V", "Vision card"),
    rulesText = "Draw one card."
  )

  private val placeholder = VisualInstruction.Placeholder(
    symbol = "V",
    text = "Vision card",
    accessibleLabel = AccessibleLabel("Vision")
  )

  test("a confirmed matching image preserves its accessible label") {
    assertEquals(
      VisualResolver.resolve(card, ImageLoadResult.Loaded(reference)),
      VisualInstruction.Image(reference, AccessibleLabel("Vision"))
    )
  }

  test("missing and failed images use the same deterministic placeholder") {
    assertEquals(VisualResolver.resolve(card, ImageLoadResult.NotRequested), placeholder)
    assertEquals(VisualResolver.resolve(card, ImageLoadResult.Failed(reference)), placeholder)
  }

  test("a stale loader result cannot substitute a different image") {
    val staleReference = ImageRef("theme/cards/different")
    assertEquals(
      VisualResolver.resolve(card, ImageLoadResult.Loaded(staleReference)),
      placeholder
    )
  }

  test("entities without an image reference always use their placeholder") {
    val site = SiteView(
      id = ViewId("site:mine"),
      label = AccessibleLabel("The Mine"),
      image = None,
      fallback = FallbackVisual("M", "The Mine")
    )

    assertEquals(
      VisualResolver.resolve(site, ImageLoadResult.Loaded(reference)),
      VisualInstruction.Placeholder("M", "The Mine", AccessibleLabel("The Mine"))
    )
  }

  test("board snapshots report IDs duplicated across entity types") {
    val sharedId = ViewId("entity:shared")
    val site = SiteView(
      sharedId,
      AccessibleLabel("Site"),
      None,
      FallbackVisual("S", "Site")
    )
    val action = ActionView(
      sharedId,
      AccessibleLabel("Act"),
      None,
      FallbackVisual("A", "Act"),
      enabled = true
    )
    val board = BoardView(
      ViewId("board:test"),
      AccessibleLabel("Test board"),
      Vector(site),
      Vector.empty,
      Vector.empty,
      Vector(action)
    )

    assertEquals(board.duplicateIds, Set(sharedId))
  }
}
