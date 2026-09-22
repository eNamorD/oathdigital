package oathdigital.frontend

import org.scalajs.dom

/** Catalog `rulesText` carries `[token]` markup and Markdown emphasis
  * (`CatalogModel.rulesText` joins multiple powers with a blank line before
  * the projection ever sees them). The UI printed both verbatim; this suite
  * pins the rendered form. Runs under jsdom (`Test / jsEnv` in `build.sbt`).
  */
class RulesTextRendererSuite extends munit.FunSuite {
  private def all(node: dom.Element, selector: String): Vector[dom.Element] =
    node.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  test("every catalog token has a glyph, an accessible name and a colour class") {
    assertEquals(TokenSprite.ids.size, 17)
    TokenSprite.ids.foreach { token =>
      val glyph = RulesTextRenderer.glyph(token)
      assertEquals(glyph.getAttribute("role"), "img")
      assertEquals(glyph.getAttribute("aria-label"), TokenSprite.label(token))
      assertEquals(glyph.getAttribute("class"), s"token-glyph token-$token")
      assertEquals(all(glyph, "use").size, 1)
      assertEquals(all(glyph, "use").head.getAttribute("href"), s"#token-$token")
    }
  }

  test("the three warm suits reference three different symbols") {
    val warm = Vector("suit-discord", "suit-hearth", "suit-beast")
      .map(t => all(RulesTextRenderer.glyph(t), "use").head.getAttribute("href"))
    assertEquals(warm.distinct.size, 3)
  }

  test("tokens become glyphs and emphasis becomes elements") {
    val blocks = RulesTextRenderer.powers(
      "[secret] **ACTION:** Gain [favor] _(once)_.")
    assertEquals(blocks.size, 1)
    val block = blocks.head
    assertEquals(all(block, ".token-glyph").map(_.getAttribute("aria-label")),
      Vector("secret", "favor"))
    assertEquals(all(block, "strong").map(_.textContent), Vector("ACTION:"))
    assertEquals(all(block, "em").map(_.textContent), Vector("(once)"))
    assert(!block.textContent.contains("["), block.textContent)
    assert(!block.textContent.contains("**"), block.textContent)
    assert(!block.textContent.contains("_"), block.textContent)
  }

  test("a blank line starts a new power block") {
    val blocks = RulesTextRenderer.powers("First power.\n\nSecond power.")
    assertEquals(blocks.map(_.textContent),
      Vector("First power.", "Second power."))
    assertEquals(blocks.map(_.getAttribute("class")),
      Vector("rules-power", "rules-power"))
  }

  test("an unknown bracketed word is left as literal text, not dropped") {
    val block = RulesTextRenderer.powers("Gain [wombat] now.").head
    assertEquals(all(block, ".token-glyph"), Vector.empty)
    assertEquals(block.textContent, "Gain [wombat] now.")
  }

  test("the sprite mounts exactly once per root") {
    val root = dom.document.createElement("div")
    TokenSprite.mount(root)
    TokenSprite.mount(root)
    assertEquals(all(root, "svg.token-sprite").size, 1)
    assertEquals(all(root, "symbol").size, 17)
  }
}
