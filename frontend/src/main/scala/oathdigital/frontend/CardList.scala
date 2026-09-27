package oathdigital.frontend

import org.scalajs.dom
import ServerUiSupport.{element, text}

/** Cards in the order given, as a scrolling grid of card faces, each its
  * face or its back as the server projected it (catalog batch 2, N5). The
  * Inspect panel draws one, and the card overlay draws one for a log line's
  * card list. A face is still a button that opens the card overlay.
  */
private[frontend] object CardList:
  def render(title: String, cards: Vector[CardDetails]): dom.Element =
    val list = element("section", "card-list")
    list.appendChild(text("h3", "card-list-title", title))
    val grid = element("ol", "card-list-grid")
    cards.foreach { card =>
      val item = element("li", "card-list-item")
      item.appendChild(CardFace.render(card))
      grid.appendChild(item)
    }
    list.appendChild(grid)
    list
