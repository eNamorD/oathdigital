package oathdigital.presentation

/**
 * Compile-checked usage example. A UI adapter can map these instructions to
 * HTML, canvas, native widgets, or plain text without changing the game engine.
 */
object PresentationExample {
  val site: SiteView = SiteView(
    id = ViewId("site:cradle"),
    label = AccessibleLabel("The Cradle"),
    image = Some(ImageRef("theme/site/cradle")),
    fallback = FallbackVisual("⌂", "The Cradle")
  )

  val pawn: PieceView = PieceView(
    id = ViewId("piece:blue-exile"),
    label = AccessibleLabel("Blue Exile at The Cradle"),
    image = None,
    fallback = FallbackVisual("●", "Blue Exile"),
    siteId = Some(site.id)
  )

  val board: BoardView = BoardView(
    id = ViewId("board:example"),
    label = AccessibleLabel("Example game board"),
    sites = Vector(site),
    cards = Vector.empty,
    pieces = Vector(pawn),
    actions = Vector.empty
  )

  // Both missing and failed images resolve to the same text/symbol fallback.
  val absentImage: VisualInstruction =
    VisualResolver.resolve(pawn, ImageLoadResult.NotRequested)
  val failedImage: VisualInstruction =
    VisualResolver.resolve(site, ImageLoadResult.Failed(ImageRef("theme/site/cradle")))
}
