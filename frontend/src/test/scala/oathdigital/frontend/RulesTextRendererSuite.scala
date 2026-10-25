package oathdigital.frontend

import oathdigital.frontend.Dom.all
import org.scalajs.dom

/** Catalog `rulesText` carries `[token]` markup and Markdown emphasis
  * (`PrintsPowers.rulesText` joins multiple powers with a blank line before
  * the projection ever sees them). The UI printed both verbatim; this suite
  * pins the rendered form. Runs under jsdom (`Test / jsEnv` in `build.sbt`).
  */
class RulesTextRendererSuite extends munit.FunSuite:
  test("every catalog token has a glyph, an accessible name and a colour class"):
    // 17 is the catalog's icon-token count, the "seventeen" of the
    // TokenSprite documentation; the warband the Supply track draws is the
    // eighteenth.
    assertEquals(TokenSprite.ids.size, 18)
    TokenSprite.ids.foreach { token =>
      val glyph = RulesTextRenderer.glyph(token)
      assertEquals(glyph.getAttribute("role"), "img")
      assertEquals(glyph.getAttribute("aria-label"), TokenSprite.label(token))
      assertEquals(glyph.getAttribute("class"), s"token-glyph token-$token")
      assertEquals(all(glyph, "use").size, 1)
      assertEquals(all(glyph, "use").head.getAttribute("href"), s"#token-$token")
    }

  test("tokens become glyphs and emphasis becomes elements"):
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

  test("a blank line starts a new power block"):
    val blocks = RulesTextRenderer.powers("First power.\n\nSecond power.")
    assertEquals(blocks.map(_.textContent),
      Vector("First power.", "Second power."))
    assertEquals(blocks.map(_.getAttribute("class")),
      Vector("rules-power", "rules-power"))

  test("an unknown bracketed word is left as literal text, not dropped"):
    val block = RulesTextRenderer.powers("Gain [wombat] now.").head
    assertEquals(all(block, ".token-glyph"), Vector.empty)
    assertEquals(block.textContent, "Gain [wombat] now.")

  test("the sprite mounts exactly once per root"):
    val root = dom.document.createElement("div")
    TokenSprite.mount(root)
    TokenSprite.mount(root)
    assertEquals(all(root, "svg.token-sprite").size, 1)
    assertEquals(all(root, "symbol").size, TokenSprite.ids.size)

  test("Conspiracy renders as two paragraphs with both banner glyphs"):
    val text = oathdigital.protocol.projection.VisionCardPresentation
      .byId("vision:conspiracy").rulesText
    val blocks = RulesTextRenderer.powers(text)
    assertEquals(blocks.size, 2)
    val glyphs = all(blocks(1), ".token-glyph")
    assertEquals(glyphs.size, 2)
    assertEquals(glyphs.map(_.getAttribute("aria-label")),
      Vector("favor", "secret"))
