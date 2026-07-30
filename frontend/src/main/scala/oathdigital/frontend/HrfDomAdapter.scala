package oathdigital.frontend

import org.scalajs.dom

/**
 * Minimal extraction of HRF html.scala's ElementAttachmentPoint lifecycle.
 *
 * HRF's useful idea here is a stable mount point that is cleared and
 * repopulated on each render. Its Elem/ImageResources materializer is
 * intentionally excluded because it pulls in the wider HRF framework.
 */
final class HrfDomAdapter(val parent: dom.Element) {
  def clear(): Unit =
    while (parent.lastChild != null) parent.removeChild(parent.lastChild)

  def replace(render: dom.Element => Unit): Unit = {
    clear()
    render(parent)
  }
}
