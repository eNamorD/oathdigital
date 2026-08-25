package oathdigital.frontend

import org.scalajs.dom

object Main {
  def main(args: Array[String]): Unit = {
    val mount = Option(dom.document.getElementById("app")).getOrElse {
      throw new IllegalStateException("missing #app mount point")
    }
    ServerModeUi.start(mount)
  }
}
