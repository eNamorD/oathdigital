package oathdigital.frontend

import org.scalajs.dom
import scala.scalajs.js

/** DOM reads and user events shared by the render suites. */
private[frontend] object Dom:
  /** Every element under `node` that matches `selector`, in document order. */
  def all(node: dom.Element, selector: String): Vector[dom.Element] =
    node.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  /** The first match under `node`, or None when nothing matches. */
  def firstMatch(node: dom.Element, selector: String): Option[dom.Element] =
    all(node, selector).headOption

  /** The only match under `root`; fails with the selector and how many
    * elements matched.
    */
  def one(root: dom.Element, selector: String): dom.Element =
    val found = all(root, selector)
    if found.size != 1 then
      munit.Assertions.fail(
        s"expected exactly one $selector, found ${found.size}")
    found.head

  /** A bubbling click on `node`, with Shift held when `shift` is set. */
  def click(node: dom.Element, shift: Boolean = false): Unit =
    node.dispatchEvent(new dom.MouseEvent("click", js.Dynamic.literal(
      bubbles = true, shiftKey = shift).asInstanceOf[dom.MouseEventInit]))

  /** A bubbling keydown of `key` on `node`. */
  def press(node: dom.Element, key: String): Unit =
    node.dispatchEvent(new dom.KeyboardEvent("keydown", js.Dynamic.literal(
      bubbles = true, key = key).asInstanceOf[dom.KeyboardEventInit]))
