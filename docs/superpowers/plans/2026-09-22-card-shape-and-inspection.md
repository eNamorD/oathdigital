# Card Shape and Inspection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every denizen and every relic a fixed box that never changes size between its face-down and face-up states, put a permanent summary on the face, and move full rules into a full-screen overlay opened by clicking any card.

**Architecture:** One renderer, `CardFace`, replaces `ServerUiSupport.cardDetailsPopover` at all nine call sites and emits a `<button>` of fixed `ex`-relative dimensions. Clicking it calls `CardInspection.open`, a tiny handler registry that `GameTableShell` wires to a `CardInspectionOverlay` mounted outside the panes — outside, because `PanelContent.replace` destroys and rebuilds pane DOM on every projection poll. Rules text is rendered by `RulesTextRenderer`, which turns the catalog's `[token]` markup into `<use>` references against an inline SVG sprite and applies Markdown emphasis. The hover popover, its `position: static` override and the `.peeked-relic` grid overlay are deleted rather than restyled.

**Tech Stack:** Scala 2.13 on Scala.js, munit under jsdom (`Test / jsEnv := JSDOMNodeJSEnv` in `build.sbt`), plain CSS in `frontend/styles.css`. Full gate: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`.

**Spec:** [docs/superpowers/specs/2026-09-22-card-shape-and-inspection-design.md](../specs/2026-09-22-card-shape-and-inspection-design.md)

## Global Constraints

- **Box ratios.** Denizen box is 1 : 1.4 and applies to `cardKind` `denizen`, `edifice` and `vision`. Relic box is 1 : 1, `cardKind` `relic`. There is no third ratio.
- **Starting size.** `--card-w: 13ex`, `--card-h: calc(var(--card-w) * 1.4)`. Declared once on `:root` and never overridden per pane. Per-context size comes from font size only.
- **Pinned map font.** `.map-content` sets `font-size: 1rem` explicitly. It must not inherit, because the map is additionally `transform: scale()`d and an inherited change would compound with that invisibly.
- **Map degradation threshold: 0.6.** Below it, map card faces show the name only. At and above it they show the full summary. A degraded face-up card must never adopt the face-down letter treatment.
- **Face-down letters.** `D` for denizen and edifice, `V` for vision, `R` for relic. Exactly the spec's three; there is no fourth case. This depends on commit `7ae54c3`, which stopped `GamePresentationProjector` over-redacting an unidentifiable adviser's `cardKind` to `"adviser"` — it now projects the real `"denizen"` or `"vision"`, which is public information because the two backs differ physically.
- **The no-reflow invariant.** Every state a card can enter occupies identical space at rest. Borders that appear on selection, focus or hover are declared `transparent` at rest, not added on the state. Emphasis that cannot be reserved uses `outline`, which is out of flow.
- **Token vocabulary — exactly these 17, verbatim from `docs/catalog/new-foundations-component-catalog.json`:** `favor`, `secret`, `attack-die`, `suit-beast`, `secret-burnt`, `favor-burnt`, `suit-nomad`, `defense-die`, `skull`, `suit-hearth`, `shield`, `suit-order`, `suit-discord`, `sword`, `hollow-sword`, `suit-arcane`, `round-die`. No others exist; do not invent any.
- **Palette — exact hex values, all new to the codebase:**

  | Custom property | Value |
  | --- | --- |
  | `--token-favor` | `#e4ba5a` |
  | `--token-favor-burnt` | `#a28a5a` |
  | `--token-secret` | `#66a8d8` |
  | `--token-secret-burnt` | `#60849c` |
  | `--suit-arcane` | `#6e377d` |
  | `--suit-beast` | `#8c371e` |
  | `--suit-discord` | `#c84623` |
  | `--suit-hearth` | `#d24b23` |
  | `--suit-nomad` | `#4b9678` |
  | `--suit-order` | `#14417d` |

- **Colour never identifies a suit.** Discord and Hearth are about one degree apart in hue and Beast is the same hue family. The emblem shape carries the identity; where a suit is named in text the name carries it and colour is decoration.
- **Never render a hidden card's identity.** A `CardDetails` with `hidden = true` carries `name = "Facedown <kind>"` and nothing else. Do not print that name. No test may assert a name, suit or rules string reaching the DOM from a hidden card.
- **Do not raise the model or effort level of any subagent above this session's** (project `CLAUDE.md`).
- **Commit messages end with** `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- **Other sessions commit to `main` in this repository.** Run `git log --oneline -1` and confirm HEAD is what you expect before any `--amend`, `reset` or `rebase`. Prefer a new commit.

## File Structure

| File | Responsibility |
| --- | --- |
| `frontend/src/main/scala/oathdigital/frontend/TokenSprite.scala` | The 17 `<symbol>` glyph definitions, their accessible names, and mounting the sprite once per document. |
| `frontend/src/main/scala/oathdigital/frontend/RulesTextRenderer.scala` | Catalog markup → DOM: `[token]` → glyph, `**bold**`/`_italic_` → elements, blank line → one block per power. |
| `frontend/src/main/scala/oathdigital/frontend/CardFace.scala` | The fixed-box card button: face-up summary, face-down letter, knowable pip, unimplemented marker. |
| `frontend/src/main/scala/oathdigital/frontend/CardInspection.scala` | A one-slot handler registry decoupling the nine card call sites from the overlay. |
| `frontend/src/main/scala/oathdigital/frontend/CardInspectionOverlay.scala` | The full-screen overlay: contents, Escape / Close / click dismissal, focus return. |
| `frontend/src/main/scala/oathdigital/frontend/DragClickGuard.scala` | Suppresses the click that follows a drag on a `.decision-option`. |
| `frontend/styles.css` | Palette custom properties, box sizing, face layout, overlay, map-compact degradation. Deletions: `.card-popover`, `.peeked-relic`, the `position: static` override. |

Tests mirror production one-to-one under `frontend/src/test/scala/oathdigital/frontend/`.

---

### Task 1: Token glyph sprite and the rules-text renderer

Pure DOM builders with no layout dependency, so they can be written and tested before anything is wired.

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/TokenSprite.scala`
- Create: `frontend/src/main/scala/oathdigital/frontend/RulesTextRenderer.scala`
- Modify: `frontend/styles.css` (add the palette block and glyph sizing at the top of the file, after the `:root` block ending at line 6)
- Test: `frontend/src/test/scala/oathdigital/frontend/RulesTextRendererSuite.scala`

**Interfaces:**
- Consumes: `ServerUiSupport.element`/`ServerUiSupport.text` (existing `private[frontend]` helpers).
- Produces, for Task 4:
  - `TokenSprite.ids: Vector[String]` — the 17 token names.
  - `TokenSprite.label(token: String): String` — the accessible name.
  - `TokenSprite.mount(root: dom.Element): Unit` — appends the hidden sprite `<svg>` once; a second call on the same root is a no-op.
  - `RulesTextRenderer.powers(rulesText: String): Vector[dom.Element]` — one `<p class="rules-power">` per power block.
  - `RulesTextRenderer.glyph(token: String): dom.Element` — one `<svg class="token-glyph token-<name>" role="img" aria-label="...">` containing a `<use>`.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/test/scala/oathdigital/frontend/RulesTextRendererSuite.scala`:

```scala
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.RulesTextRendererSuite"`

Expected: FAIL to compile — `not found: value TokenSprite`.

- [ ] **Step 3: Write `TokenSprite`**

Create `frontend/src/main/scala/oathdigital/frontend/TokenSprite.scala`. The paths are authored on a `0 0 24 24` grid and every one is filled `evenodd`, so an inner subpath is always a hole. They are deliberately geometric: shape, not colour, is what distinguishes a suit (see Global Constraints).

```scala
package oathdigital.frontend

import org.scalajs.dom

/** The seventeen catalog icon tokens as one inline SVG sprite.
  *
  * Inline SVG rather than Unicode because colour-emoji glyphs ignore CSS
  * `color` entirely, and non-emoji Unicode has no character resembling a
  * cracked coin or a torn book -- which is exactly what the two burnt tokens
  * need. `currentColor` inside a `<symbol>` resolves against the referencing
  * `<use>`, so a class on the `<use>`'s owner colours the glyph.
  */
private[frontend] object TokenSprite {
  private val Svg = "http://www.w3.org/2000/svg"

  /** token name -> (accessible name, path data). Order is the sprite order. */
  private val glyphs: Vector[(String, String, String)] = Vector(
    ("favor", "favor",
      "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 4a6 6 0 1 1 0 12 6 6 0 0 1 0-12z"),
    ("favor-burnt", "burnt favor",
      "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 4a6 6 0 1 1 0 12 6 6 0 0 1 0-12z" +
        "M11.2 2.1l2 .2-1.4 4.6 2.6 2.4-2.9 3.3 2.1 9.3-1.9.2-1.8-9.7 2.8-3.2-2.5-2.3z"),
    ("secret", "secret",
      "M4 3h13a2 2 0 0 1 2 2v16H7a3 3 0 0 1-3-3zm3 3v12h10V6z"),
    ("secret-burnt", "burnt secret",
      "M4 3h13a2 2 0 0 1 2 2v16H7a3 3 0 0 1-3-3zm3 3v12h10V6z" +
        "M11.4 3l1.9.4-1.7 4.3 2.7 2.6-2.6 3 1.6 7.6-1.9.4-1.8-8.2 2.5-2.9-2.4-2.3z"),
    ("suit-arcane", "arcane suit",
      "M12 2 22 21H2zm0 6-1.2 3.6H7l3 2.3-1.2 3.6L12 15.2l3.2 2.3L14 13.9l3-2.3h-3.8z"),
    ("suit-beast", "beast suit",
      "M2 2l5 6h10l5-6-2 9-8 11-8-11zm7 9h2v2H9zm4 0h2v2h-2z"),
    ("suit-discord", "discord suit",
      "M3 2l4 6a8 8 0 0 1 10 0l4-6-2 8a8 8 0 1 1-14 0zM9 12h2v2H9zm4 0h2v2h-2z"),
    ("suit-hearth", "hearth suit",
      "M12 2 2 12h3v9h14v-9h3zm-2 12h4v7h-4z"),
    ("suit-nomad", "nomad suit",
      "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zM7 10h4v1.5H7zm6 0h4v1.5h-4z" +
        "m-5.2 4.3 1.6-.9A4 4 0 0 0 12 15.5a4 4 0 0 0 3.6-2.1l1.6.9A5.8 5.8 0 0 1 12 17.5" +
        "a5.8 5.8 0 0 1-4.2-3.2z"),
    ("suit-order", "order suit",
      "M3 3h18v18H3zm2 2v14h14V5zm3 3h2v2H8zm6 0h2v2h-2zm-6 6h2v2H8zm6 0h2v2h-2z" +
        "m-3-3h2v2h-2z"),
    ("attack-die", "attack die",
      "M4 4h16v16H4zm2 2v12h12V6zm2 2h2.5v2.5H8zm2.75 2.75h2.5v2.5h-2.5z" +
        "M13.5 13.5H16V16h-2.5z"),
    ("defense-die", "defense die",
      "M12 2 22 12 12 22 2 12zm0 3.5L5.5 12 12 18.5 18.5 12zM10 9h2v2h-2z" +
        "m2 4h2v2h-2z"),
    ("round-die", "round die",
      "M6 3h12a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3z" +
        "m0 2a1 1 0 0 0-1 1v12a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V6a1 1 0 0 0-1-1z" +
        "m4.5 5.5h3v3h-3z"),
    ("skull", "skull",
      "M12 2a8 8 0 0 0-8 8v4l2 2v4h3v-3h6v3h3v-4l2-2v-4a8 8 0 0 0-8-8z" +
        "M8.5 9.5a2 2 0 1 1 0 4 2 2 0 0 1 0-4zm7 0a2 2 0 1 1 0 4 2 2 0 0 1 0-4z"),
    ("shield", "shield",
      "M12 2 3 5v7c0 5.2 3.8 9.4 9 11 5.2-1.6 9-5.8 9-11V5z" +
        "m0 2.3 7 2.3V12c0 4-2.8 7.4-7 8.9-4.2-1.5-7-4.9-7-8.9V6.6z"),
    ("sword", "sword",
      "M12 1 14.5 5v9h-5V5zM6 14h12v2.5H6zm4.75 2.5h2.5V23h-2.5z"),
    ("hollow-sword", "hollow sword",
      "M12 1 14.5 5v9h-5V5zm0 3.4L11.5 6v6.5h1V6zM6 14h12v2.5H6z" +
        "m1.2 1.1v.3h9.6v-.3zm3.55 1.4h2.5V23h-2.5zm1 1v5h.5v-5z")
  )

  val ids: Vector[String] = glyphs.map(_._1)

  private val labels: Map[String, String] =
    glyphs.map { case (id, label, _) => id -> label }.toMap

  def label(token: String): String = labels.getOrElse(token, token)

  def isToken(value: String): Boolean = labels.contains(value)

  /** Appends the hidden sprite once. A second call on the same root is inert,
    * so callers need not track whether the document already has it.
    */
  def mount(root: dom.Element): Unit =
    if (root.querySelector("svg.token-sprite") == null) {
      val sprite = dom.document.createElementNS(Svg, "svg")
      sprite.setAttribute("class", "token-sprite")
      sprite.setAttribute("aria-hidden", "true")
      glyphs.foreach { case (id, _, path) =>
        val symbol = dom.document.createElementNS(Svg, "symbol")
        symbol.setAttribute("id", s"token-$id")
        symbol.setAttribute("viewBox", "0 0 24 24")
        val shape = dom.document.createElementNS(Svg, "path")
        shape.setAttribute("d", path)
        shape.setAttribute("fill", "currentColor")
        shape.setAttribute("fill-rule", "evenodd")
        symbol.appendChild(shape)
        sprite.appendChild(symbol)
      }
      root.appendChild(sprite)
    }
}
```

- [ ] **Step 4: Write `RulesTextRenderer`**

Create `frontend/src/main/scala/oathdigital/frontend/RulesTextRenderer.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom

/** Renders catalog `rulesText` markup instead of printing it.
  *
  * `CatalogModel.rulesText` joins a card's powers with a blank line before the
  * projection is built, so splitting on that boundary is what recovers the
  * per-power blocks. Six cards in the catalog have more than one power.
  */
private[frontend] object RulesTextRenderer {
  private val Svg = "http://www.w3.org/2000/svg"
  private val Token = """\[([a-z0-9-]+)\]""".r
  private val Emphasis = """\*\*(.+?)\*\*|(?<![A-Za-z0-9_])_(.+?)_(?![A-Za-z0-9_])""".r

  def glyph(token: String): dom.Element = {
    val node = dom.document.createElementNS(Svg, "svg")
    node.setAttribute("class", s"token-glyph token-$token")
    node.setAttribute("role", "img")
    node.setAttribute("aria-label", TokenSprite.label(token))
    val use = dom.document.createElementNS(Svg, "use")
    use.setAttribute("href", s"#token-$token")
    node.appendChild(use)
    node
  }

  def powers(rulesText: String): Vector[dom.Element] =
    rulesText.split("\n\\s*\n").toVector.map(_.trim).filter(_.nonEmpty)
      .map { block =>
        val node = ServerUiSupport.element("p", "rules-power")
        emphasise(block, node)
        node
      }

  /** `**bold**` and `_italic_` wrap runs that may themselves hold tokens. */
  private def emphasise(value: String, into: dom.Element): Unit = {
    var cursor = 0
    Emphasis.findAllMatchIn(value).foreach { found =>
      tokenise(value.substring(cursor, found.start), into)
      val inner = Option(found.group(1)).getOrElse(found.group(2))
      val wrapper = ServerUiSupport.element(
        if (found.group(1) != null) "strong" else "em", "")
      tokenise(inner, wrapper)
      into.appendChild(wrapper)
      cursor = found.end
    }
    tokenise(value.substring(cursor), into)
  }

  /** A bracketed word that is not one of the seventeen stays literal text --
    * silently dropping it would hide a catalog typo instead of showing it.
    */
  private def tokenise(value: String, into: dom.Element): Unit = {
    var cursor = 0
    Token.findAllMatchIn(value).foreach { found =>
      if (TokenSprite.isToken(found.group(1))) {
        append(value.substring(cursor, found.start), into)
        into.appendChild(glyph(found.group(1)))
        cursor = found.end
      }
    }
    append(value.substring(cursor), into)
  }

  private def append(value: String, into: dom.Element): Unit =
    if (value.nonEmpty) into.appendChild(dom.document.createTextNode(value))
}
```

- [ ] **Step 5: Add the palette and glyph sizing to the stylesheet**

In `frontend/styles.css`, replace the `:root` block (lines 1-6) with:

```css
:root {
  color-scheme: dark;
  font-family: Inter, ui-sans-serif, system-ui, sans-serif;
  background: #17140f;
  color: #f5ecd7;

  /* Sampled from the product owner's reference images, not chosen by eye.
     Discord, Hearth and Beast are one hue family: shape identifies a suit,
     never colour. */
  --token-favor: #e4ba5a;
  --token-favor-burnt: #a28a5a;
  --token-secret: #66a8d8;
  --token-secret-burnt: #60849c;
  --suit-arcane: #6e377d;
  --suit-beast: #8c371e;
  --suit-discord: #c84623;
  --suit-hearth: #d24b23;
  --suit-nomad: #4b9678;
  --suit-order: #14417d;
}

.token-sprite { position: absolute; width: 0; height: 0; overflow: hidden; }
.token-glyph { display: inline-block; width: 1.05em; height: 1.05em;
  vertical-align: -0.16em; }
.token-favor { color: var(--token-favor); }
.token-favor-burnt { color: var(--token-favor-burnt); }
.token-secret { color: var(--token-secret); }
.token-secret-burnt { color: var(--token-secret-burnt); }
.token-suit-arcane { color: var(--suit-arcane); }
.token-suit-beast { color: var(--suit-beast); }
.token-suit-discord { color: var(--suit-discord); }
.token-suit-hearth { color: var(--suit-hearth); }
.token-suit-nomad { color: var(--suit-nomad); }
.token-suit-order { color: var(--suit-order); }
.token-attack-die, .token-defense-die, .token-round-die,
.token-skull, .token-shield, .token-sword, .token-hollow-sword {
  color: #d8cdb4; }
.rules-power { margin: 0 0 0.7em; line-height: 1.5; }
.rules-power:last-child { margin-bottom: 0; }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.RulesTextRendererSuite"`

Expected: PASS, 6 tests.

- [ ] **Step 7: Run the full gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`

Expected: exit 0, no existing test broken (nothing calls the new code yet).

- [ ] **Step 8: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/TokenSprite.scala frontend/src/main/scala/oathdigital/frontend/RulesTextRenderer.scala frontend/src/test/scala/oathdigital/frontend/RulesTextRendererSuite.scala frontend/styles.css
git commit -m "$(cat <<'EOF'
feat(frontend): render catalog token markup as inline SVG glyphs

The seventeen [token] names and Markdown emphasis in catalog rulesText were
printed verbatim. TokenSprite defines one symbol per token and
RulesTextRenderer turns the markup into DOM, splitting multi-power text on
the blank line CatalogModel.rulesText joins powers with.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: The fixed-size card face

Builds the renderer and its CSS. Nothing calls it yet — Task 3 does the swap, so this task can be reviewed on its own output.

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/CardFace.scala`
- Modify: `frontend/styles.css` (add the card-box block immediately before the `.pile-display` rule, currently line 295)
- Test: `frontend/src/test/scala/oathdigital/frontend/CardFaceSuite.scala`

**Interfaces:**
- Consumes: `TokenSprite`, `RulesTextRenderer` (Task 1) only indirectly — the face carries no rules text. `ServerUiSupport.element`/`text`.
- Produces, for Tasks 3, 4 and 6:
  - `CardFace.render(card: CardDetails): dom.html.Button`
  - `CardFace.boxClass(cardKind: String): String` — `"card-face-relic"` or `"card-face-denizen"`.
  - `CardFace.backLetter(cardKind: String): String` — `"D"`, `"V"` or `"R"`.
  - `CardFace.faceDown(card: CardDetails): Boolean` — true when the projection says the card is not showing its face.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/test/scala/oathdigital/frontend/CardFaceSuite.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom

/** `CardFace` is the one place a projected `CardDetails` becomes DOM, so
  * every rule about card shape, redaction and knowability is pinned here.
  * Runs under jsdom (`Test / jsEnv` in `build.sbt`).
  */
class CardFaceSuite extends munit.FunSuite {
  private def all(node: dom.Element, selector: String): Vector[dom.Element] =
    node.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  private def one(node: dom.Element, selector: String): Option[dom.Element] =
    all(node, selector).headOption

  private val faceUp = CardDetails("d1", "denizen", "Old Oak",
    suit = Some("order"), restrictions = Some("site-only"),
    rulesText = Some("**ACTION:** Gain [favor]."),
    orientation = Some("face-up"), favor = 2, secrets = 1)

  private val hidden = CardDetails("hidden", "vision", "Facedown vision",
    orientation = Some("face-down"), hidden = true)

  private val knowable = CardDetails("r7", "relic", "Ancient Crown",
    rulesText = Some("Rule"), orientation = Some("face-down"),
    relicValue = Some(3), defense = Some(1))

  test("a denizen and a relic each get their type's box, whichever way they face") {
    assert(CardFace.render(faceUp).classList.contains("card-face-denizen"))
    assert(CardFace.render(faceUp.copy(orientation = Some("face-down"),
      hidden = true)).classList.contains("card-face-denizen"))
    assert(CardFace.render(knowable).classList.contains("card-face-relic"))
    assert(CardFace.render(knowable.copy(orientation = Some("face-up")))
      .classList.contains("card-face-relic"))
    assertEquals(CardFace.boxClass("edifice"), "card-face-denizen")
    assertEquals(CardFace.boxClass("vision"), "card-face-denizen")
  }

  test("a face-up card shows its summary with no field names") {
    val node = CardFace.render(faceUp)
    assertEquals(one(node, ".card-name").map(_.textContent), Some("Old Oak"))
    assertEquals(one(node, ".card-suit").map(_.textContent), Some("order"))
    assertEquals(one(node, ".card-suit").map(_.getAttribute("class")),
      Some("card-suit suit-order"))
    assertEquals(one(node, ".card-restriction").map(_.textContent),
      Some("site-only"))
    assertEquals(all(node, ".card-tokens .token-glyph")
      .map(_.getAttribute("aria-label")), Vector("favor", "secret"))
    assertEquals(all(node, ".card-token-count").map(_.textContent),
      Vector("2", "1"))
    assert(!node.textContent.contains("Suit:"), node.textContent)
    assert(!node.textContent.contains("Favor:"), node.textContent)
  }

  test("rules text never reaches the face") {
    assert(!CardFace.render(faceUp).textContent.contains("ACTION"))
    assert(!CardFace.render(knowable).textContent.contains("Rule"))
  }

  test("a relic face carries its value and defense, a denizen face does not") {
    val relic = CardFace.render(knowable.copy(orientation = Some("face-up")))
    assertEquals(all(relic, ".card-stat").map(_.textContent), Vector("3", "1"))
    assertEquals(all(CardFace.render(faceUp), ".card-stat"), Vector.empty)
  }

  test("an unidentifiable card exposes no name, suit or rules and shows its letter") {
    val node = CardFace.render(hidden)
    assert(node.classList.contains("card-face-down"))
    assert(!node.classList.contains("card-face-knowable"))
    assertEquals(one(node, ".card-back-letter").map(_.textContent), Some("V"))
    assertEquals(node.getAttribute("aria-label"), "Facedown vision")
    assertEquals(all(node, ".card-name"), Vector.empty)
    assertEquals(all(node, ".card-suit"), Vector.empty)
    assertEquals(node.textContent, "V")
  }

  test("each hidden kind gets its own letter") {
    assertEquals(CardFace.backLetter("denizen"), "D")
    assertEquals(CardFace.backLetter("edifice"), "D")
    assertEquals(CardFace.backLetter("vision"), "V")
    assertEquals(CardFace.backLetter("relic"), "R")
  }

  /** A denizen back and a vision back differ physically, so which one a
    * face-down adviser is remains public even when its identity is not.
    * `GamePresentationProjector` projects the real kind (commit `7ae54c3`);
    * the face must therefore never collapse the two into one letter.
    */
  test("an unidentifiable adviser still says whether it is a denizen or a vision") {
    val denizenBack = CardFace.render(CardDetails("hidden", "denizen",
      "Facedown denizen", orientation = Some("face-down"), hidden = true))
    val visionBack = CardFace.render(CardDetails("hidden", "vision",
      "Facedown vision", orientation = Some("face-down"), hidden = true))
    assertEquals(one(denizenBack, ".card-back-letter").map(_.textContent), Some("D"))
    assertEquals(one(visionBack, ".card-back-letter").map(_.textContent), Some("V"))
  }

  test("a knowable back carries the pip and a hover-only summary") {
    val node = CardFace.render(knowable)
    assert(node.classList.contains("card-face-down"))
    assert(node.classList.contains("card-face-knowable"))
    assertEquals(one(node, ".card-back-letter").map(_.textContent), Some("R"))
    assertEquals(all(node, ".card-knowable-pip").size, 1)
    assertEquals(one(node, ".card-hover-summary .card-name").map(_.textContent),
      Some("Ancient Crown"))
  }

  test("knowability is read per render, so a later projection can add the pip") {
    val before = CardFace.render(CardDetails("hidden", "relic",
      "Facedown relic", orientation = Some("face-down"), hidden = true))
    val after = CardFace.render(knowable)
    assert(!before.classList.contains("card-face-knowable"))
    assert(after.classList.contains("card-face-knowable"))
  }

  test("the unimplemented marker survives the redesign") {
    val node = CardFace.render(faceUp.copy(implemented = false))
    assert(node.classList.contains("card-face-unimplemented"))
    assertEquals(node.getAttribute("aria-label"), "Old Oak (unimplemented)")
    assertEquals(all(node, ".card-unimplemented-badge").size, 1)
  }

  test("the card id rides the element for focus restoration") {
    assertEquals(CardFace.render(faceUp).getAttribute("data-card-id"), "d1")
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.CardFaceSuite"`

Expected: FAIL to compile — `not found: value CardFace`.

- [ ] **Step 3: Write `CardFace`**

Create `frontend/src/main/scala/oathdigital/frontend/CardFace.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport._

/** The one place a projected `CardDetails` becomes DOM.
  *
  * Every card occupies its type's box -- denizen 1:1.4, relic 1:1 -- in both
  * states, so revealing or covering a card can never reflow the pane around
  * it. Full rules live in the inspection overlay, not here: a summary fits a
  * small card and rules do not (measured p90 is 134 glyphs for a denizen).
  */
private[frontend] object CardFace {

  /** Edifices share the denizen box because they occupy denizen slots, and a
    * differently shaped edifice would break the slot grid the moment one is
    * built. Visions share it rather than earn a ratio used by one case.
    */
  def boxClass(cardKind: String): String =
    if (cardKind == "relic") "card-face-relic" else "card-face-denizen"

  /** Safe on a card the viewer cannot identify: a denizen back and a vision
    * back differ physically, so which one this is stays public even when the
    * card's identity does not. `GamePresentationProjector` projects the real
    * kind for an unidentifiable adviser (commit `7ae54c3`).
    */
  def backLetter(cardKind: String): String = cardKind match {
    case "vision" => "V"
    case "relic" => "R"
    case _ => "D"
  }

  def faceDown(card: CardDetails): Boolean =
    card.hidden || card.orientation.contains("face-down")

  def render(card: CardDetails): dom.html.Button = {
    val node = dom.document.createElement("button").asInstanceOf[dom.html.Button]
    node.`type` = "button"
    node.className = Vector("card-face", boxClass(card.cardKind),
      if (faceDown(card)) "card-face-down" else "",
      if (faceDown(card) && !card.hidden) "card-face-knowable" else "",
      if (card.implemented) "" else "card-face-unimplemented")
      .filter(_.nonEmpty).mkString(" ")
    node.setAttribute("data-card-id", card.cardId)
    node.setAttribute("aria-label",
      if (card.hidden) card.name
      else if (card.implemented) card.name
      else s"${card.name} (unimplemented)")

    if (card.hidden) {
      node.appendChild(text("span", "card-back-letter", backLetter(card.cardKind)))
    } else if (faceDown(card)) {
      // Knowable: the back is what shows at rest; the summary is revealed by
      // CSS on hover only, inside the same box, so nothing moves.
      node.appendChild(text("span", "card-back-letter", backLetter(card.cardKind)))
      val pip = element("span", "card-knowable-pip")
      pip.setAttribute("aria-hidden", "true")
      node.appendChild(pip)
      val peek = element("span", "card-hover-summary")
      summary(card).foreach(peek.appendChild)
      node.appendChild(peek)
    } else {
      summary(card).foreach(node.appendChild)
    }
    node
  }

  /** Priority order, dropped from the bottom as the box shrinks: name, live
    * tokens, suit, relic value and defense, restriction, unimplemented.
    * Live tokens outrank suit because they are the only fields that change
    * during play, and a board that looks stale is worse than one with an
    * unlabelled suit.
    */
  private def summary(card: CardDetails): Vector[dom.Element] = {
    val name = text("span", "card-name", card.name)
    val tokens = element("span", "card-tokens")
    if (card.favor > 0) counted("favor", card.favor).foreach(tokens.appendChild)
    if (card.secrets > 0) counted("secret", card.secrets).foreach(tokens.appendChild)
    val stats = element("span", "card-stats")
    card.relicValue.foreach(value =>
      stats.appendChild(labelled("card-stat relic-value", value.toString,
        s"relic value $value")))
    card.defense.foreach(value =>
      stats.appendChild(labelled("card-stat relic-defense", value.toString,
        s"defense $value")))
    Vector(Some(name),
      Option.when(tokens.childNodes.length > 0)(tokens),
      card.suit.map(value => text("span", s"card-suit suit-$value", value)),
      Option.when(stats.childNodes.length > 0)(stats),
      card.restrictions.map(value => text("span", "card-restriction", value)),
      Option.when(!card.implemented)(
        text("span", "card-unimplemented-badge", "Unimplemented"))).flatten
  }

  private def counted(token: String, count: Int): Vector[dom.Element] =
    Vector(RulesTextRenderer.glyph(token),
      text("span", "card-token-count", count.toString))

  private def labelled(className: String, value: String,
      accessible: String): dom.Element = {
    val node = text("span", className, value)
    node.setAttribute("aria-label", accessible)
    node
  }
}
```

- [ ] **Step 4: Add the card-box styles**

In `frontend/styles.css`, insert immediately before the `.pile-display` rule (currently line 295):

```css
/* One box per card type, in ex so a pane's font size scales every card in it
   together. Never overridden per pane: per-context size is font size alone. */
:root { --card-w: 13ex; --card-h: calc(var(--card-w) * 1.4);
  --card-gap: 0.55ex; }
.card-face { position: relative; display: flex; flex-direction: column;
  gap: 0.15em; box-sizing: border-box; width: var(--card-w);
  height: var(--card-h); flex: 0 0 auto; margin: 0; padding: 0.4em;
  overflow: hidden; text-align: left;
  /* Reserved at rest so selection, focus and hover never reflow. */
  border: 1px solid transparent; border-radius: 0.35em;
  background: #17140f; color: #f5ecd7; font: inherit; line-height: 1.15; }
.card-face-denizen { border-color: #675a43; }
.card-face-relic { height: var(--card-w); border-color: #7a6a4c; }
.card-face-unimplemented { border-style: dashed; border-color: #a15c4a; }
.card-face:hover, .card-face:focus-visible { outline: 2px solid #fff0c9;
  outline-offset: -3px; }
.card-name { font-weight: 750; font-size: 0.95em; overflow-wrap: anywhere; }
.card-suit { font-size: 0.78em; text-transform: capitalize; color: #cdbfa2; }
.card-suit.suit-arcane { color: var(--suit-arcane); }
.card-suit.suit-beast { color: var(--suit-beast); }
.card-suit.suit-discord { color: var(--suit-discord); }
.card-suit.suit-hearth { color: var(--suit-hearth); }
.card-suit.suit-nomad { color: var(--suit-nomad); }
.card-suit.suit-order { color: var(--suit-order); }
.card-tokens, .card-stats { display: flex; align-items: center; gap: 0.15em;
  font-size: 0.8em; }
.card-token-count { margin-right: 0.35em; font-weight: 700; }
.card-stat { padding: 0 0.3em; border: 1px solid #675a43; border-radius: 0.25em;
  font-weight: 700; }
.card-restriction { margin-top: auto; font-size: 0.72em; color: #a99f8c;
  text-transform: capitalize; }
.card-unimplemented-badge { align-self: flex-start; padding: 0 0.35em;
  border-radius: 0.25em; background: #a15c4a; color: #f5ecd7;
  font-size: 0.62em; text-transform: uppercase; letter-spacing: 0.03em; }
.card-face-down { align-items: center; justify-content: center;
  border-style: solid; background: #2a2333; }
.card-back-letter { font-size: 1.9em; font-weight: 800; color: #cbb7dd; }
.card-knowable-pip { position: absolute; top: 0.3em; right: 0.3em;
  width: 0.55em; height: 0.55em; border-radius: 50%;
  background: var(--token-secret); }
/* Hover reveal is CSS only and holds no client state: PanelContent.replace
   rebuilds the pane on every poll, so a persistent flag would have to become
   reconciled state. Touch users tap straight through to the overlay. */
.card-hover-summary { display: none; position: absolute; inset: 0;
  flex-direction: column; gap: 0.15em; padding: 0.4em; overflow: hidden;
  background: #17140f; text-align: left; }
@media (hover: hover) {
  .card-face-knowable:hover .card-hover-summary,
  .card-face-knowable:focus-visible .card-hover-summary { display: flex; }
  .card-face-knowable:hover .card-back-letter,
  .card-face-knowable:focus-visible .card-back-letter { visibility: hidden; }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.CardFaceSuite"`

Expected: PASS, 10 tests.

- [ ] **Step 6: Run the full gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`

Expected: exit 0.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/CardFace.scala frontend/src/test/scala/oathdigital/frontend/CardFaceSuite.scala frontend/styles.css
git commit -m "$(cat <<'EOF'
feat(frontend): add the fixed-size card face

One box per card type -- denizen 1:1.4, relic 1:1, both in ex -- carrying a
permanent summary face-up and a centred letter face-down, so a card cannot
change footprint when its state does. A back the viewer is entitled to
identify carries a pip and a hover-only reveal inside the same box.

Not yet wired; the call sites move in the next commit.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Retire the hover popover

The reflow actually disappears here. The nine `cardDetailsPopover` call sites move to `CardFace.render`, and the three CSS causes — the in-flow popover, the mismatched face-down squares, the `.peeked-relic` grid overlay — are deleted.

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala` (delete `cardDetailsPopover` at 121-149 and `peekedRelic` at 151-163; rewrite the denizen and relic loops in `siteDetails` at 78-111)
- Modify: `frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala:39,47,58`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala:57`
- Modify: `frontend/src/main/scala/oathdigital/frontend/FacedownAdviserRenderer.scala:23`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala:314-316`
- Modify: `frontend/src/main/scala/oathdigital/frontend/DistributePanelRenderer.scala:45-47`
- Modify: `frontend/src/main/scala/oathdigital/frontend/SiteCardPresentation.scala` (drop the now-unused `PeekedRelicPresentation` reveal fields)
- Modify: `frontend/styles.css` (delete `.card-detail`, `.card-detail-unimplemented`, `.card-popover`, its `:hover`/`:focus` rules, `.card-property`, `.empty-denizen-slot`/`.facedown-relic`, the whole `.peeked-relic` block at 343-351, and the `.game-table .card-popover` override at 430-432)
- Modify: `frontend/src/test/scala/oathdigital/frontend/ServerUiSupportCardMarkerSuite.scala` (delete; `CardFaceSuite` covers it)
- Modify: `frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala:875-888` (the peeked-relic assertions)

**Interfaces:**
- Consumes: `CardFace.render` (Task 2).
- Produces: `ServerUiSupport.facedownCard(cardKind: String): dom.Element` — the shared face-down box used for a site relic the viewer cannot identify, and `ServerUiSupport.emptySlot(): dom.Element` for an unfilled denizen slot. Both occupy the same box as a real card.

- [ ] **Step 1: Write the failing test**

Append to `frontend/src/test/scala/oathdigital/frontend/CardFaceSuite.scala`, inside the class:

```scala
  test("an empty denizen slot and an unknown relic occupy their type's box") {
    assert(ServerUiSupport.emptySlot().classList.contains("card-face-denizen"))
    assert(ServerUiSupport.emptySlot().classList.contains("card-slot-empty"))
    assertEquals(ServerUiSupport.emptySlot().getAttribute("aria-label"),
      "Empty denizen slot")
    val relic = ServerUiSupport.facedownCard("relic")
    assert(relic.classList.contains("card-face-relic"))
    assert(relic.classList.contains("card-face-down"))
    assertEquals(relic.textContent, "R")
    assertEquals(relic.getAttribute("aria-label"), "Facedown relic")
  }
```

And create the boundary test the spec calls for. Add to the same class:

```scala
  test("face-up and face-down cards of one type carry the same box class") {
    Vector("denizen", "vision", "edifice", "relic").foreach { kind =>
      val up = CardFace.render(CardDetails("c", kind, "Name",
        orientation = Some("face-up")))
      val down = CardFace.render(CardDetails("hidden", kind,
        s"Facedown $kind", orientation = Some("face-down"), hidden = true))
      assertEquals(CardFace.boxClass(kind), CardFace.boxClass(kind))
      assert(up.classList.contains(CardFace.boxClass(kind)), kind)
      assert(down.classList.contains(CardFace.boxClass(kind)), kind)
    }
  }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.CardFaceSuite"`

Expected: FAIL to compile — `value emptySlot is not a member of object ServerUiSupport`.

- [ ] **Step 3: Replace the popover helpers in `ServerUiSupport`**

In `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala`, delete `cardDetailsPopover` (lines 121-149) and `peekedRelic` (lines 151-163) entirely and put in their place:

```scala
  /** A site relic the viewer cannot identify. Same box as a real relic, so
    * learning what it is -- which a negotiation can do mid-game -- swaps the
    * contents of a box that does not move.
    */
  private[frontend] def facedownCard(cardKind: String): dom.Element = {
    val node = element("span", s"card-face ${CardFace.boxClass(cardKind)} card-face-down")
    node.setAttribute("role", "img")
    node.setAttribute("aria-label", s"Facedown $cardKind")
    node.appendChild(text("span", "card-back-letter", CardFace.backLetter(cardKind)))
    node
  }

  private[frontend] def emptySlot(): dom.Element = {
    val node = element("span", "card-face card-face-denizen card-slot-empty")
    node.setAttribute("role", "img")
    node.setAttribute("aria-label", "Empty denizen slot")
    node
  }
```

- [ ] **Step 4: Rewrite the denizen and relic loops in `siteDetails`**

Replace lines 78-111 of the same file with:

```scala
    val denizens = element("div", "site-denizens")
    denizens.appendChild(text("strong", "", "Denizens: "))
    site.denizens.foreach { denizen =>
      val shell = element("span", "site-card-target")
      val card = denizen.details.fold[dom.Element](
        facedownCard("denizen"))(CardFace.render)
      card.setAttribute("data-denizen-id", denizen.denizenId)
      shell.appendChild(card)
      denizens.appendChild(shell)
    }
    (site.denizens.size until site.denizenCapacity)
      .foreach(_ => denizens.appendChild(emptySlot()))
    details.appendChild(denizens)

    val relics = element("div", "site-relics")
    relics.appendChild(text("strong", "", "Relics: "))
    if (site.relics.facedownCount == 0)
      relics.appendChild(dom.document.createTextNode("None"))
    // A peeked relic is a real card sitting face-down: CardFace gives it the
    // knowable pip and the hover reveal, in the same box as an unknown one.
    presentation.peekedRelics.foreach(value =>
      relics.appendChild(CardFace.render(value.card)))
    (0 until presentation.unknownRelicCount)
      .foreach(_ => relics.appendChild(facedownCard("relic")))
    details.appendChild(relics)
    details
  }
```

Note this drops the `VisualDomRenderer.render(...)` fallback for a face-down site denizen. That call produced a placeholder initial for a card with no details; `facedownCard` is the same information in the shared box. `presentation.denizenVisuals` is now unused by `siteDetails`, but leave the field on `SiteCardPresentation` — the site heading still uses `siteVisual`, and removing one field of a presentation case class is outside this slice.

- [ ] **Step 5: Move the remaining six call sites**

Each is a one-word substitution of `CardFace.render` for `cardDetailsPopover`:

- `WorldBoardRenderer.scala:39` → `shell.appendChild(CardFace.render(card))`
- `WorldBoardRenderer.scala:47` → `shell.appendChild(CardFace.render(card))`
- `WorldBoardRenderer.scala:58` → `section.appendChild(CardFace.render(card))`
- `ActionDecisionRenderer.scala:57` → `value.privateAdviserPreview.foreach(card => preview.appendChild(CardFace.render(card)))`
- `FacedownAdviserRenderer.scala:23` → `panel.appendChild(CardFace.render(adviser.card))`
- `WalkerPanelSupport.scala:314-316` → replace the `node.appendChild(option.card.fold(...))` expression with:

```scala
    node.appendChild(option.card.fold[dom.Element](
      text("span", "option-summary", option.label))(CardFace.render))
```

- `DistributePanelRenderer.scala:45-47` → replace the `node.appendChild(slot.option.card.fold(...))` expression with:

```scala
    node.appendChild(slot.option.card.fold[dom.Element](
      text("span", "distribute-label", slot.option.label))(CardFace.render))
```

`DistributePanelRenderer` and `WalkerPanelSupport` reference `ServerUiSupport.cardDetailsPopover` by its qualified name; `CardFace` is in the same package, so `CardFace.render` needs no import in either.

- [ ] **Step 6: Drop the obsolete reveal metadata from `SiteCardPresentation`**

In `frontend/src/main/scala/oathdigital/frontend/SiteCardPresentation.scala`, replace the `PeekedRelicPresentation` case class (line 6-8) with:

```scala
private[frontend] final case class PeekedRelicPresentation(card: CardDetails)
```

`concealedAtRest` and `revealInteractions` described the deleted `.peeked-relic` grid overlay — a hover/focus/press-and-hold dance across two stacked elements. The shared face-down box replaces all three interactions with one hover rule and one click, so the fields no longer describe anything the UI does.

- [ ] **Step 7: Delete the popover CSS**

In `frontend/styles.css`, delete these rules outright:

- Lines 284-291: `.card-detail`, `.card-detail-unimplemented`, `.card-popover`, the two `:hover`/`:focus` reveal rules, `.card-property`.
- Line 303-304: `.empty-denizen-slot, .facedown-relic` (the `2rem` squares).
- Lines 343-351: the entire `.peeked-relic` block.
- Lines 430-432: the comment and the `.game-table .card-popover { position: static; ... }` override — the primary reflow cause. It exists only because `.game-pane` is `overflow: hidden`; the overlay renders at document level, so the constraint is gone.

Keep `.card-unimplemented-badge` (lines 292-294) — `CardFace` still emits it, but its `font-size` and `margin` are now set by the card-box block from Task 2, so delete only the duplicate declaration at 292-294 and leave Task 2's version.

Add, to the card-box block from Task 2:

```css
.card-slot-empty { border-style: dashed; border-color: #88775d;
  background: transparent; }
.decision-cards, .board-cards, .site-denizens, .site-relics {
  gap: var(--card-gap); }
.site-denizens, .site-relics { display: flex; flex-wrap: wrap;
  align-items: flex-start; gap: var(--card-gap); }
```

- [ ] **Step 8: Delete the superseded suite and fix the peeked-relic assertions**

```bash
git rm frontend/src/test/scala/oathdigital/frontend/ServerUiSupportCardMarkerSuite.scala
```

Its two cases moved into `CardFaceSuite` ("the unimplemented marker survives the redesign").

In `frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala`, replace the two obsolete assertions at lines 884-886 with nothing — delete these lines:

```scala
    assert(owner.peekedRelics.forall(_.concealedAtRest))
    assertEquals(owner.peekedRelics.head.revealInteractions,
      Vector("hover", "focus", "press-and-hold"))
```

The surrounding assertions (`unknownRelicCount`, `peekedRelics.map(_.card.name)`, `other.peekedRelics`) still hold and are what the test is actually for: a peeked relic is scoped to the viewer who peeked.

- [ ] **Step 9: Run the frontend suite**

Run: `./sbtw "frontend/test"`

Expected: PASS. If `PartitionPanelRenderSuite` or `DistributePanelRenderSuite` fail on a text assertion, it is because the option label now lives inside `.card-name` rather than in a bare span — update the selector, not the production code.

- [ ] **Step 10: Run the full gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`

Expected: exit 0.

- [ ] **Step 11: Commit**

```bash
git add -A frontend/
git commit -m "$(cat <<'EOF'
refactor(frontend): retire the hover popover for the fixed card face

All nine cardDetailsPopover call sites now render CardFace, and the three CSS
causes of the hover reflow are deleted: the `.game-table .card-popover`
`position: static` override that grew its button in flow, the 2rem facedown
squares that did not match a card, and the `.peeked-relic` grid overlay whose
cell resized when the reveal appeared.

The static override existed only because `.game-pane` is `overflow: hidden`.
The inspection overlay renders at document level, so that is no longer a
constraint.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: The inspection overlay

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/CardInspection.scala`
- Create: `frontend/src/main/scala/oathdigital/frontend/CardInspectionOverlay.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/CardFace.scala` (attach the click)
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala` (own the overlay and the sprite)
- Modify: `frontend/styles.css` (append the overlay block at the end of the file)
- Test: `frontend/src/test/scala/oathdigital/frontend/CardInspectionOverlaySuite.scala`

**Interfaces:**
- Consumes: `CardFace.render`, `CardFace.boxClass`, `CardFace.faceDown` (Task 2); `RulesTextRenderer.powers`, `TokenSprite.mount` (Task 1).
- Produces:
  - `CardInspection.onOpen(handler: (CardDetails, dom.html.Element) => Unit): Unit`
  - `CardInspection.clear(): Unit`
  - `CardInspection.open(card: CardDetails, origin: dom.html.Element): Unit` — inert when no handler is installed.
  - `new CardInspectionOverlay(root: dom.Element)` with `show(card: CardDetails, origin: dom.html.Element): Unit`, `hide(): Unit`, `isOpen: Boolean`, `dispose(): Unit`.

**Why a registry rather than threading a callback.** The nine call sites reach `CardFace.render` through six different renderer objects, three of which (`ActionDecisionRenderer`, `FacedownAdviserRenderer`, `WalkerPanelSupport`) already take a `ServerUiView` and three of which (`ServerUiSupport.siteDetails`, `DistributePanelRenderer.row`, `WorldBoardRenderer.playerBoards`) do not. Threading a callback through all six for one handler is more change than the feature is worth; one installed-once slot, cleared by `dispose`, matches how the codebase already treats `GameTableShell` as the owner of document-level chrome.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/test/scala/oathdigital/frontend/CardInspectionOverlaySuite.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom
import scala.scalajs.js

/** The overlay is the only place a card's full rules appear after the hover
  * popover was retired. It renders at document level, above `.game-pane`,
  * which is what lets that pane keep `overflow: hidden`.
  * Runs under jsdom (`Test / jsEnv` in `build.sbt`).
  */
class CardInspectionOverlaySuite extends munit.FunSuite {
  private def all(node: dom.Element, selector: String): Vector[dom.Element] =
    node.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  private def one(node: dom.Element, selector: String): Option[dom.Element] =
    all(node, selector).headOption

  private def press(node: dom.Element, key: String): Unit =
    node.dispatchEvent(new dom.KeyboardEvent("keydown",
      new dom.KeyboardEventInit { this.key = key; bubbles = true }))

  private def click(node: dom.Element): Unit =
    node.dispatchEvent(new dom.MouseEvent("click",
      new dom.MouseEventInit { bubbles = true }))

  private val card = CardDetails("d1", "denizen", "Old Oak",
    suit = Some("order"), restrictions = Some("site-only"),
    rulesText = Some("**ACTION:** Gain [favor].\n\n[secret] Persistent."),
    orientation = Some("face-up"), relicValue = None, favor = 1)

  private val hidden = CardDetails("hidden", "relic", "Facedown relic",
    orientation = Some("face-down"), hidden = true)

  private def fixture(): (dom.Element, CardInspectionOverlay) = {
    val root = dom.document.createElement("div")
    dom.document.body.appendChild(root)
    (root, new CardInspectionOverlay(root))
  }

  private def origin(): dom.html.Element = {
    val node = dom.document.createElement("button").asInstanceOf[dom.html.Element]
    node.setAttribute("tabindex", "0")
    dom.document.body.appendChild(node)
    node
  }

  test("the overlay is hidden until a card opens it, and shows that card in full") {
    val (root, overlay) = fixture()
    assert(root.querySelector(".card-overlay").hasAttribute("hidden"))
    overlay.show(card, origin())
    assert(overlay.isOpen)
    val node = root.querySelector(".card-overlay")
    assert(!node.hasAttribute("hidden"))
    assertEquals(node.getAttribute("role"), "dialog")
    assertEquals(one(node, ".card-name").map(_.textContent), Some("Old Oak"))
    // Field names come back in the overlay, unlike on the face.
    assert(node.textContent.contains("Suit"), node.textContent)
    assert(node.textContent.contains("Restrictions"), node.textContent)
    overlay.dispose(); root.remove()
  }

  test("multi-power rules render one block per power with glyphs") {
    val (root, overlay) = fixture()
    overlay.show(card, origin())
    val node = root.querySelector(".card-overlay")
    assertEquals(all(node, ".rules-power").size, 2)
    assertEquals(all(node, ".card-overlay-rules .token-glyph")
      .map(_.getAttribute("aria-label")), Vector("favor", "secret"))
    assertEquals(all(node, ".card-overlay-rules strong").map(_.textContent),
      Vector("ACTION:"))
    overlay.dispose(); root.remove()
  }

  test("a card the viewer cannot identify renders as face-down, not as a card") {
    val (root, overlay) = fixture()
    overlay.show(hidden, origin())
    val node = root.querySelector(".card-overlay")
    assertEquals(all(node, ".card-overlay-rules"), Vector.empty)
    assertEquals(one(node, ".card-back-letter").map(_.textContent), Some("R"))
    assert(!node.textContent.contains("Suit"), node.textContent)
    overlay.dispose(); root.remove()
  }

  test("Escape, the Close button and a click on the overlay each dismiss it") {
    Vector[(dom.Element, CardInspectionOverlay) => Unit](
      (root, _) => press(root.querySelector(".card-overlay"), "Escape"),
      (root, _) => click(root.querySelector(".card-overlay-close")),
      (root, _) => click(root.querySelector(".card-overlay"))
    ).foreach { dismiss =>
      val (root, overlay) = fixture()
      overlay.show(card, origin())
      assert(overlay.isOpen)
      dismiss(root, overlay)
      assert(!overlay.isOpen)
      assert(root.querySelector(".card-overlay").hasAttribute("hidden"))
      overlay.dispose(); root.remove()
    }
  }

  test("a click on the card itself does not dismiss the overlay") {
    val (root, overlay) = fixture()
    overlay.show(card, origin())
    click(root.querySelector(".card-overlay-body"))
    assert(overlay.isOpen)
    overlay.dispose(); root.remove()
  }

  test("focus returns to the card that opened the overlay") {
    val (root, overlay) = fixture()
    val opener = origin()
    overlay.show(card, opener)
    assertEquals(dom.document.activeElement,
      root.querySelector(".card-overlay-close"))
    overlay.hide()
    assertEquals(dom.document.activeElement, opener)
    overlay.dispose(); root.remove(); opener.remove()
  }

  test("focus return survives the opener being rebuilt out from under it") {
    val (root, overlay) = fixture()
    val opener = origin()
    overlay.show(card, opener)
    opener.remove()
    overlay.hide()
    assert(!overlay.isOpen)
    overlay.dispose(); root.remove()
  }

  test("a projection update while the overlay is open leaves it open and unchanged") {
    val (root, overlay) = fixture()
    overlay.show(card, origin())
    val before = root.querySelector(".card-overlay-body").innerHTML
    // What a poll does to the panes: PanelContent.replace tears down and
    // rebuilds pane DOM. The overlay is not inside a pane.
    val pane = dom.document.createElement("div")
    root.appendChild(pane)
    PanelContent.replace(pane, dom.document.createElement("p").asInstanceOf[dom.Element],
      dom.document.createElement("h2").asInstanceOf[dom.html.Element])
    assert(overlay.isOpen)
    assertEquals(root.querySelector(".card-overlay-body").innerHTML, before)
    overlay.dispose(); root.remove()
  }

  test("clicking a card face asks the installed handler to open it") {
    var opened = Vector.empty[String]
    CardInspection.onOpen((c, _) => opened = opened :+ c.cardId)
    val face = CardFace.render(card)
    dom.document.body.appendChild(face)
    click(face)
    assertEquals(opened, Vector("d1"))
    CardInspection.clear()
    click(face)
    assertEquals(opened, Vector("d1"))
    face.remove()
  }

  test("a face-down card is clickable too") {
    var opened = Vector.empty[String]
    CardInspection.onOpen((c, _) => opened = opened :+ c.cardKind)
    val face = CardFace.render(hidden)
    dom.document.body.appendChild(face)
    click(face)
    assertEquals(opened, Vector("relic"))
    CardInspection.clear()
    face.remove()
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.CardInspectionOverlaySuite"`

Expected: FAIL to compile — `not found: type CardInspectionOverlay`.

- [ ] **Step 3: Write `CardInspection`**

Create `frontend/src/main/scala/oathdigital/frontend/CardInspection.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom

/** One slot connecting every rendered card to whatever is showing overlays.
  *
  * Cards reach `CardFace.render` through six renderer objects, only three of
  * which carry a `ServerUiView`. Threading a callback through the other three
  * for a single handler is more change than the feature is worth, and
  * `GameTableShell` already owns the other document-level chrome.
  */
private[frontend] object CardInspection {
  private var handler = Option.empty[(CardDetails, dom.html.Element) => Unit]

  def onOpen(value: (CardDetails, dom.html.Element) => Unit): Unit =
    handler = Some(value)

  def clear(): Unit = handler = None

  def open(card: CardDetails, origin: dom.html.Element): Unit =
    handler.foreach(_(card, origin))
}
```

- [ ] **Step 4: Attach the click in `CardFace.render`**

In `frontend/src/main/scala/oathdigital/frontend/CardFace.scala`, immediately before the closing `node` of `render`, add:

```scala
    // Unclaimed everywhere: `BoardTargetRefProjection` has only a `Site` case,
    // and decision panels select by drag, by the move-option buttons, or by
    // the plus/minus steppers -- never by clicking a card. So a click can mean
    // inspect, in every context, with nothing to disambiguate against.
    node.onclick = _ => CardInspection.open(card, node)
```

- [ ] **Step 5: Write `CardInspectionOverlay`**

Create `frontend/src/main/scala/oathdigital/frontend/CardInspectionOverlay.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport._

/** A full-window card inspector, rendered at document level above the panes.
  *
  * Follows the development panel's dialog pattern in `GameTableShell`: hidden
  * attribute, Escape handler, Close button, focus return to the opening
  * control. It ignores projection changes while open -- what a card does does
  * not change when the card moves, and closing on every poll tick would yank
  * the card out from under a reader mid-sentence, which is the interruption
  * this whole design exists to remove.
  */
private[frontend] final class CardInspectionOverlay(root: dom.Element) {
  private val node = element("aside", "card-overlay").asInstanceOf[dom.html.Element]
  node.setAttribute("hidden", "")
  node.setAttribute("role", "dialog")
  node.setAttribute("aria-modal", "true")
  node.setAttribute("aria-label", "Card details")
  private val body = element("div", "card-overlay-body")
  private val close = button("Close", "card-overlay-close")
  node.appendChild(body)
  node.appendChild(close)
  root.appendChild(node)
  TokenSprite.mount(root)

  private var opener = Option.empty[dom.html.Element]

  def isOpen: Boolean = !node.hasAttribute("hidden")

  def show(card: CardDetails, origin: dom.html.Element): Unit = {
    while (body.firstChild != null) body.removeChild(body.firstChild)
    body.appendChild(CardFace.render(card))
    if (!card.hidden) body.appendChild(details(card))
    opener = Some(origin)
    node.removeAttribute("hidden")
    close.focus()
  }

  def hide(): Unit = {
    node.setAttribute("hidden", "")
    // The opener can have been rebuilt away by a poll while the overlay was
    // open; focusing a detached node silently does nothing, so guard instead.
    opener.filter(dom.document.contains).foreach(_.focus())
    opener = None
  }

  private def details(card: CardDetails): dom.Element = {
    val panel = element("div", "card-overlay-details")
    val properties = element("dl", "card-overlay-properties")
    Vector(card.suit.map("Suit" -> _),
      card.restrictions.map("Restrictions" -> _),
      card.orientation.map("Orientation" -> _),
      card.side.map("Side" -> _),
      Option.when(card.favor > 0)("Favor" -> card.favor.toString),
      Option.when(card.secrets > 0)("Secrets" -> card.secrets.toString),
      card.relicValue.map(v => "Relic value" -> v.toString),
      card.defense.map(v => "Defense" -> v.toString)).flatten.foreach {
      case (label, value) =>
        val item = element("div", "card-overlay-property")
        item.appendChild(text("dt", "", label))
        item.appendChild(text("dd", "", value))
        properties.appendChild(item)
    }
    if (properties.childNodes.length > 0) panel.appendChild(properties)
    card.rulesText.foreach { value =>
      val rules = element("div", "card-overlay-rules")
      RulesTextRenderer.powers(value).foreach(rules.appendChild)
      panel.appendChild(rules)
    }
    if (!card.implemented)
      panel.appendChild(text("p", "card-unimplemented-badge", "Unimplemented"))
    panel
  }

  private val dismiss: dom.MouseEvent => Unit = event =>
    // A click anywhere on the overlay dismisses it, except inside the card
    // itself -- selecting rules text should not close what you are reading.
    if (event.target == node || event.target == close) hide()

  private val escape: dom.KeyboardEvent => Unit = event =>
    if (event.key == "Escape" && isOpen) { event.preventDefault(); hide() }

  node.addEventListener("click", dismiss)
  node.addEventListener("keydown", escape)

  def dispose(): Unit = {
    node.removeEventListener("click", dismiss)
    node.removeEventListener("keydown", escape)
    node.remove()
  }
}
```

- [ ] **Step 6: Wire the overlay into `GameTableShell`**

In `frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala`, after the `dev.addEventListener("keydown", escape)` line (line 81), add:

```scala
  private val inspector = new CardInspectionOverlay(mount)
  CardInspection.onOpen((card, origin) => inspector.show(card, origin))
```

and in `dispose()` (line 98-103), before `table.remove()`, add:

```scala
    CardInspection.clear()
    inspector.dispose()
```

- [ ] **Step 7: Add the overlay styles**

Append to `frontend/styles.css`:

```css
/* Document level, above .game-pane -- which is what lets that pane keep
   overflow: hidden without clipping anything a card needs to show. */
.card-overlay { position: fixed; z-index: 300; inset: 0; display: flex;
  align-items: center; justify-content: center; gap: 2ex; flex-wrap: wrap;
  padding: 4vmin; background: #0b0a08e8; font-size: clamp(1rem, 2.6vmin, 1.9rem); }
.card-overlay[hidden] { display: none; }
.card-overlay-body { display: flex; align-items: flex-start; gap: 2ex;
  flex-wrap: wrap; max-width: 100%; max-height: 100%; overflow: auto;
  padding: 1.5ex; border: 1px solid #7c694b; border-radius: 0.5em;
  background: #1b1811; }
/* The card reuses its type's ratio at overlay scale, the way haunt-roll-fail's
   courtCard is card's exact ratio at 1.34x. No third ratio. */
.card-overlay-body > .card-face { font-size: 1.9em; }
.card-overlay-details { flex: 1 1 22em; min-width: 0; max-width: 34em;
  font-size: 0.82em; }
.card-overlay-properties { display: grid;
  grid-template-columns: auto minmax(0, 1fr); gap: 0.2em 0.9em; margin: 0 0 1em; }
.card-overlay-property { display: contents; }
.card-overlay-property dt { color: #a99f8c; }
.card-overlay-property dd { margin: 0; text-transform: capitalize; }
.card-overlay-close { position: absolute; top: 2vmin; right: 2vmin;
  min-width: 32px; min-height: 30px; padding: 4px 9px; color: #e8d9bb;
  background: #302a20; border: 1px solid #7c694b; border-radius: 4px;
  font-size: 0.78rem; cursor: pointer; }
.card-overlay-close:focus-visible { outline: 2px solid #fff0c9; }
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.CardInspectionOverlaySuite"`

Expected: PASS, 10 tests.

- [ ] **Step 9: Run the full gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`

Expected: exit 0. `ServerModeUiSuite` constructs `GameTableShell` and therefore now constructs an overlay; if a case fails because `mount.childNodes` grew by one, assert on `.game-table` rather than on a child count.

- [ ] **Step 10: Commit**

```bash
git add -A frontend/
git commit -m "$(cat <<'EOF'
feat(frontend): open a full-screen overlay on any card click

Clicking a card anywhere -- map, player board, decision panel, shared bank --
shows it in full: the card at overlay scale, every field with its name
restored, and rules text with token markup rendered and emphasis applied. A
card the viewer cannot identify renders as face-down rather than as a card.

The overlay renders at document level and ignores projection changes while
open. Closing it on every poll tick would yank the card out from under a
reader mid-sentence, which is the interruption this design exists to remove.

Card clicks were unclaimed everywhere: BoardTargetRefProjection has only a
Site case, and decision panels select by drag, by move-option buttons, or by
steppers. Nothing had to be given up to make click mean inspect.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Decision panels — drop the dead focus stop, keep drag working

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/DragClickGuard.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala:308-310`
- Test: `frontend/src/test/scala/oathdigital/frontend/PartitionPanelRenderSuite.scala`

**Interfaces:**
- Consumes: nothing new.
- Produces: `DragClickGuard.attach(node: dom.html.Element): Unit` — swallows, at capture phase, the click that follows a drag gesture on `node`.

- [ ] **Step 1: Write the failing test**

Append to `frontend/src/test/scala/oathdigital/frontend/PartitionPanelRenderSuite.scala`, inside the class. Match the suite's existing helper names (`all`, and however it builds a panel) — if `all(node, selector)` is not already defined there, copy the one-liner from `CardFaceSuite`.

```scala
  test("a decision option is not a focus stop; the move buttons are the keyboard path") {
    val panel = /* the suite's existing panel fixture */ partitionPanel()
    val options = all(panel, ".decision-option")
    assert(options.nonEmpty)
    options.foreach(option =>
      assertEquals(option.getAttribute("tabindex"), null,
        "a decision-option has no keydown handler, so a tab stop here is dead"))
    assert(all(panel, ".move-option").nonEmpty)
    all(panel, ".move-option").foreach(move =>
      assert(move.getAttribute("aria-label").startsWith("Move ")))
  }

  test("a drag on an option swallows the click that follows; a plain press does not") {
    val node = dom.document.createElement("div").asInstanceOf[dom.html.Element]
    dom.document.body.appendChild(node)
    var clicks = 0
    node.addEventListener("click", (_: dom.Event) => clicks += 1)
    DragClickGuard.attach(node)

    def at(kind: String, x: Double, y: Double): Unit =
      node.dispatchEvent(new dom.MouseEvent(kind,
        new dom.MouseEventInit { bubbles = true; clientX = x; clientY = y }))

    at("mousedown", 10, 10); at("mousemove", 12, 11); at("click", 12, 11)
    assertEquals(clicks, 1, "a 2px wobble is a press, not a drag")

    at("mousedown", 10, 10); at("mousemove", 40, 40); at("click", 40, 40)
    assertEquals(clicks, 1, "a 30px drag must not also open the overlay")

    at("mousedown", 10, 10); at("click", 10, 10)
    assertEquals(clicks, 2, "the guard resets between gestures")

    node.remove()
  }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.PartitionPanelRenderSuite"`

Expected: FAIL to compile — `not found: value DragClickGuard`.

- [ ] **Step 3: Write `DragClickGuard`**

Create `frontend/src/main/scala/oathdigital/frontend/DragClickGuard.scala`:

```scala
package oathdigital.frontend

import org.scalajs.dom

/** Keeps press-and-drag and press-and-release apart on one element.
  *
  * `partitionOption` builds a `draggable` article with a clickable card
  * inside it, so without a threshold the drop would also open the inspection
  * overlay. `MapViewport` solves the same problem the same way at capture
  * phase, with the same six-pixel threshold; the decision panel is outside the
  * map, so it needs its own.
  */
private[frontend] object DragClickGuard {
  private val Threshold = 6.0

  def attach(node: dom.html.Element): Unit = {
    var start = Option.empty[(Double, Double)]
    var suppress = false
    node.addEventListener("mousedown", (event: dom.MouseEvent) => {
      start = Some((event.clientX, event.clientY))
      suppress = false
    })
    node.addEventListener("mousemove", (event: dom.MouseEvent) =>
      start.foreach { case (x, y) =>
        if (math.hypot(event.clientX - x, event.clientY - y) > Threshold)
          suppress = true
      })
    node.addEventListener("dragstart", (_: dom.Event) => suppress = true)
    node.addEventListener("click", (event: dom.Event) => {
      if (suppress) { event.preventDefault(); event.stopImmediatePropagation() }
      suppress = false
      start = None
    }, true)
  }
}
```

- [ ] **Step 4: Remove the dead tab stop and attach the guard**

In `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala`, in `partitionOption`, delete line 309:

```scala
    node.setAttribute("tabindex", "0")
```

and after `node.setAttribute("aria-label", option.label)` (line 312) add:

```scala
    // The article has no keydown handler and never did: the keyboard path for
    // moving an option is the move-option buttons below. The tab stop was a
    // duplicate announcement, and in front of a modal trigger it is a dead
    // stop the user has to pass through to reach the card.
    DragClickGuard.attach(node.asInstanceOf[dom.html.Element])
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.PartitionPanelRenderSuite"`

Expected: PASS.

- [ ] **Step 6: Run the full gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`

Expected: exit 0.

- [ ] **Step 7: Commit**

```bash
git add -A frontend/
git commit -m "$(cat <<'EOF'
fix(frontend): drop the dead tab stop on a decision option

.decision-option carried tabindex="0" with no keydown handler; the keyboard
path for moving an option has always been the move-option buttons inside it.
The stop produced a duplicate tab target and a doubled screen-reader
announcement, and with the card now opening a modal it sat dead in front of a
modal trigger.

DragClickGuard keeps press-and-drag apart from press-and-release on the
draggable article, with the same six-pixel threshold MapViewport uses.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Map degradation below 0.6 scale

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala:33-36`
- Modify: `frontend/styles.css` (the `.map-content` rule, currently line 399, plus a compact block)
- Test: `frontend/src/test/scala/oathdigital/frontend/MapViewStateSuite.scala`

**Interfaces:**
- Consumes: `MapViewport`'s existing `onScale: Double => Unit` callback, which `paint()` already fires on every change.
- Produces: `GameTableShell.compactAtScale(scale: Double): Boolean` — `private[frontend]`, so the threshold is testable without a live viewport.

- [ ] **Step 1: Write the failing test**

Append to `frontend/src/test/scala/oathdigital/frontend/MapViewStateSuite.scala`, inside the class:

```scala
  test("map cards drop to name only below 0.6 scale and not at or above it") {
    // Measured working scales: fit-to-screen lands at 0.4524 and 0.5655,
    // comfortable reading at 0.71. Below 0.6 the token and suit lines are
    // noise while a name is still legible and is what a player scans for.
    assert(GameTableShell.compactAtScale(0.4524))
    assert(GameTableShell.compactAtScale(0.5655))
    assert(GameTableShell.compactAtScale(0.5999))
    assert(!GameTableShell.compactAtScale(0.6))
    assert(!GameTableShell.compactAtScale(0.71))
    assert(!GameTableShell.compactAtScale(1.0))
  }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.MapViewStateSuite"`

Expected: FAIL to compile — `value compactAtScale is not a member of object GameTableShell`.

- [ ] **Step 3: Add the threshold and set the class**

In `frontend/src/main/scala/oathdigital/frontend/GameTableShell.scala`, add a companion object at the bottom of the file:

```scala
private[frontend] object GameTableShell {
  /** Below this the summary lines are noise at map scale; the name is not. */
  private val CompactBelow = 0.6
  def compactAtScale(scale: Double): Boolean = scale < CompactBelow
}
```

and replace the `mapView` construction (lines 35-36) with:

```scala
  private val mapView = new MapViewport(world.content, mapContent, scale => {
    zoomLabel.textContent = s"${math.round(scale * 100)}%"
    // Pure class toggle, no re-render: the face keeps every element and CSS
    // decides what is visible, so a degraded face-up card can never adopt the
    // face-down letter treatment.
    mapContent.classList.toggle("map-compact", GameTableShell.compactAtScale(scale))
  })
```

`classList.toggle(token, force)` is in the scalajs-dom `DOMTokenList` facade; if the two-argument form is missing in this version, write it as:

```scala
    if (GameTableShell.compactAtScale(scale)) mapContent.classList.add("map-compact")
    else mapContent.classList.remove("map-compact")
```

- [ ] **Step 4: Pin the map font and add the compact rules**

In `frontend/styles.css`, replace the `.map-content` rule (line 399) with:

```css
/* Pinned, never inherited: the map is additionally transform-scaled, so an
   inherited font-size change would compound with that invisibly. */
.map-content { position: absolute; width: 1500px; padding: 20px;
  transform-origin: top left; font-size: 1rem; }
.map-content.map-compact .card-face .card-suit,
.map-content.map-compact .card-face .card-tokens,
.map-content.map-compact .card-face .card-stats,
.map-content.map-compact .card-face .card-restriction,
.map-content.map-compact .card-face .card-unimplemented-badge {
  display: none; }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.MapViewStateSuite"`

Expected: PASS.

- [ ] **Step 6: Verify the 13ex starting width against both constraints**

This is the one number the spec says must be checked rather than assumed. Run a dev server and measure; do not reason about it from the stylesheet.

```bash
./sbtw "frontend/fastLinkJS"
```

Then serve the frontend, open a game, and in the browser console:

```javascript
// Constraint 1: three denizen boxes plus gaps fit a 460px site row at the
// map's pinned font size.
const row = document.querySelector('.map-content .site-denizens');
console.log('site row', row.clientWidth,
  [...row.children].map(n => n.getBoundingClientRect().width));

// Constraint 2: two denizen boxes plus gaps fit a 225px player board column.
const col = document.querySelector('.player-board .advisers');
console.log('board column', col.clientWidth,
  [...col.children].map(n => n.getBoundingClientRect().width));
```

Expected: three map boxes plus two `--card-gap`s at or under the row's `clientWidth`, and two board boxes plus one gap at or under the column's. If either overflows, reduce `--card-w` in one-`ex` steps until both hold, and record the value you landed on in the browser verification record in Task 7. Card rows wrap and `.pane-content` already scrolls, so an overflow degrades rather than breaks — but the spec asks for the measurement, so take it.

- [ ] **Step 7: Run the full gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`

Expected: exit 0.

- [ ] **Step 8: Commit**

```bash
git add -A frontend/
git commit -m "$(cat <<'EOF'
feat(frontend): show map cards as name-only below 0.6 scale

MapViewport already reports scale on every change; the shell turns that into
one class on .map-content and CSS decides what is visible, so no re-render is
involved and a degraded face-up card can never adopt the face-down letter.

The map's font size is now pinned rather than inherited. The map is also
transform-scaled, so an inherited change would compound with that invisibly.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Browser verification and documentation

The remaining spec requirements are visual and cannot be asserted in jsdom: that no card changes footprint on hover, focus or selection at any viewport, and that the three warm suits are told apart by shape at the smallest card size.

**Files:**
- Create: `docs/testing/card-shape-verification.md`
- Modify: `docs/superpowers/specs/2026-09-22-card-shape-and-inspection-design.md` (status line and the two follow-ups this slice closes)

**Interfaces:** none — documentation only.

- [ ] **Step 1: Start an isolated development server with its own database**

Follow the pattern of the existing panel UI record. Use a throwaway database path so the run cannot touch a real game:

```bash
OATH_DATABASE_PATH=/tmp/card-shape-verification ./sbtw "run --port 8123"
```

- [ ] **Step 2: Check the no-reflow invariant at four viewports**

At 1440×900, 1024×768, 768×1024 and 390×844, for a face-up denizen, a face-down denizen, a knowable face-down relic and an unknown face-down relic:

```javascript
// Run once per viewport. Any non-zero delta is a reflow and a defect.
const probe = document.querySelector('.card-face');
const before = probe.getBoundingClientRect();
probe.focus();
const after = probe.getBoundingClientRect();
console.log(after.width - before.width, after.height - before.height);
```

Hover each card by pointer and confirm by eye that neither the card nor the row around it moves. Record the four viewports, the four card states, and the measured deltas.

- [ ] **Step 3: Check the three warm suits at the smallest card size**

Zoom the map to below 0.6 and back to 1.0. At the smallest size at which a suit glyph is drawn, confirm by eye that `[suit-discord]`, `[suit-hearth]` and `[suit-beast]` are told apart. Their colours are about one degree apart in hue and cannot do this work; only the emblem shapes can. If any pair is not distinguishable, that is a defect in the glyph paths — record which pair and what you changed.

- [ ] **Step 4: Check the palette renders**

Open any card with rules text. Confirm `[favor]` is gold, `[secret]` is blue, the burnt variants read as darker and duller versions of the same colour, and each suit subtitle carries its suit's colour. The hex values are in the `:root` block; the unit tests assert only the class names, so this step is the only check that the values actually reach the glyph.

- [ ] **Step 5: Put all seventeen glyphs in front of the product owner**

**This step blocks.** Steps 3 and 4 only check the failure modes that could be named in advance — the warm-suit collisions and the palette. The glyph paths were authored from descriptions in the spec, not traced from component art, so the ones most likely to be wrong are the ones nobody thought to test.

Render every glyph at both sizes it is used at. In the browser console on any page that has the sprite mounted:

```javascript
// One row per token, at card-face size and at overlay size, on the game's
// own background so the palette is judged in context.
const sheet = document.createElement('div');
sheet.style.cssText = 'position:fixed;inset:0;z-index:999;overflow:auto;' +
  'background:#1b1811;color:#f5ecd7;padding:2rem;font:14px Inter,sans-serif';
sheet.innerHTML = [...document.querySelectorAll('.token-sprite symbol')]
  .map(s => s.id.replace('token-', ''))
  .map(t => `<div style="display:flex;align-items:center;gap:1.5rem;margin:.5rem 0">
    <code style="width:11rem">${t}</code>
    <span style="font-size:.8rem"><svg class="token-glyph token-${t}"
      role="img" aria-label="${t}"><use href="#token-${t}"/></svg></span>
    <span style="font-size:2.4rem"><svg class="token-glyph token-${t}"
      role="img" aria-label="${t}"><use href="#token-${t}"/></svg></span>
  </div>`).join('');
document.body.appendChild(sheet);
```

Screenshot the sheet and hand it over. Wait for the response before writing the record — do not decide on the product owner's behalf that a glyph reads well enough. Apply whatever changes come back, re-run this step, and record which glyphs were revised and why.

- [ ] **Step 6: Write the record**

Create `docs/testing/card-shape-verification.md` with: the commit verified, the server command and database path, the four viewports, the measured footprint deltas for the four card states, the suit-distinguishability result for the Discord/Hearth and Discord/Beast pairs, the palette check, the glyph review outcome and every path revised because of it, the `--card-w` value you landed on, and the two measurements from Task 6 Step 6. Record failures as failures with what you changed, not as a clean pass.

- [ ] **Step 7: Close out the spec**

In `docs/superpowers/specs/2026-09-22-card-shape-and-inspection-design.md`, change the status line at the top to name this plan and the verification record. Leave the four follow-ups at the end open — suit and restriction icons on the card face, player area and site restructuring, the map implementation, and the polling change-check are all still deferred and each needs its own design.

- [ ] **Step 8: Run the full gate one last time**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`

Expected: exit 0.

- [ ] **Step 9: Commit**

```bash
git add docs/testing/card-shape-verification.md docs/superpowers/specs/2026-09-22-card-shape-and-inspection-design.md frontend/src/main/scala/oathdigital/frontend/TokenSprite.scala
git commit -m "$(cat <<'EOF'
docs: record the card shape and inspection browser verification

Covers what jsdom cannot assert: that no card changes footprint on hover,
focus or selection at four viewports, that the three warm suits are told apart
by emblem shape at the smallest card size, since their colours are about one
degree apart in hue, and the product owner's review of all seventeen glyphs.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review

**Spec coverage.** Every section maps to a task:

| Spec section | Task |
| --- | --- |
| Fixed, type-uniform card boxes in `ex` | 2 (renderer and CSS), 6 Step 6 (the `13ex` measurement) |
| Permanent summary on the face, field names removed | 2 |
| Face-down letter treatment and the knowable pip | 2 |
| Hover reveal under `@media (hover: hover)`, no client state | 2 |
| Full-screen inspection overlay opened by click from anywhere | 4 |
| Overlay contents, dismissal, focus return, staleness, placement | 4 |
| `[token]` markup as inline SVG, suit palette | 1 |
| Removing `tabindex="0"` from `.decision-option` | 5 |
| Drag versus click in decision panels | 5 |
| Map degradation below 0.6 | 6 |
| The no-reflow invariant, deleting the `position: static` override and `.peeked-relic` | 3 (deletions), 7 (measurement) |
| Testing list, all nine items | 1, 2, 3, 4, 5, 6 unit tests; the two visual items in 7 |
| Follow-ups | Left open in 7 Step 7 |

**Deviations, each stated where it occurs rather than folded in silently:**

1. **The palette-colour and suit-distinguishability tests are browser checks, not unit tests.** The spec lists both under Testing. The hex values live in CSS custom properties, which a jsdom test cannot resolve, and "mutually distinguishable" is a visual judgement. The unit test asserts what is mechanical — three suits reference three different symbols, each glyph carries its colour class — and Task 7 Steps 3 to 5 carry the rest.
2. **The glyph paths are authored here, not sourced.** Seventeen hand-written paths on a 24-unit grid. They are real and complete, not placeholders, but they are a first cut; Task 7 Step 5 puts all seventeen in front of the product owner, and changing several there is expected, not a failure.

**One assumption this plan now rests on.** Commit `7ae54c3` fixed
`GamePresentationProjector` to project the real `"denizen"` or `"vision"` for
an adviser the viewer cannot identify, rather than collapsing both to
`"adviser"`. Card backs are public information — the two backs differ
physically — so this is not a leak, and the identical `cardKind` was already
shipped unredacted for the world deck's face-down top card
(`GameProjection.scala:83`). Without that fix the face would have needed a
fourth letter for a kind the server refused to name. With it, the spec's
`D`/`V`/`R` is complete, and `CardFaceSuite` pins the denizen-versus-vision
distinction so a future re-redaction cannot quietly undo it.

**Type consistency checked.** `CardFace.render` returns `dom.html.Button` everywhere it is named; `boxClass`/`backLetter` take `cardKind: String` and are called with `card.cardKind` and with literals in both the renderer and `ServerUiSupport.facedownCard`. `CardInspection.open(card, origin)` matches `onOpen`'s `(CardDetails, dom.html.Element) => Unit` and the shell's lambda. `RulesTextRenderer.powers` returns `Vector[dom.Element]` and is consumed as such in the overlay. `TokenSprite.label`/`isToken`/`ids`/`mount` are each used with the signature declared in Task 1.

**One thing to watch during execution.** `TestBrowser.click(name)` invokes `.onclick` directly rather than dispatching an event, so it bypasses the capture-phase listeners in `DragClickGuard` and the overlay. The new suites dispatch real events instead; if you extend `ServerModeUiSuite` to cover card clicks, dispatch rather than using that helper.
