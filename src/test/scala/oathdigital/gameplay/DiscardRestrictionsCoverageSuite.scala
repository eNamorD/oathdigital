package oathdigital.gameplay

import java.nio.file.{Files, Paths}

import scala.jdk.CollectionConverters._

/** Every production file that builds a discard of a card in play attaches
  * `DiscardRestrictions` to it, so a rule about which cards can be discarded
  * (locked, active modifier, Hall of Ministers) cannot be bypassed by a new
  * power that forgets it. A new discarding file fails this suite until it
  * carries the restrictions.
  */
class DiscardRestrictionsCoverageSuite extends munit.FunSuite:
  private val root = Paths.get("src/main/scala/oathdigital")
  private val discards =
    """Discard\.(Denizen|Vision|RuinedEdifice)\(""".r

  private def read(relative: String): String =
    Files.readString(root.resolve(relative))

  private def sources: Vector[String] = Files.walk(root).iterator.asScala
    .filter(_.toString.endsWith(".scala")).map(p => root.relativize(p).toString)
    .toVector

  test("every file that discards a card in play attaches DiscardRestrictions"):
    val building = sources.filter(path => !path.startsWith("model/") &&
      !path.startsWith("serialization/") && discards.findFirstIn(read(path)).nonEmpty)
    assert(building.nonEmpty)
    assertEquals(building.filterNot(read(_).contains("DiscardRestrictions")),
      Vector.empty[String])
