package oathdigital.gameplay

import java.nio.file.{Files, Paths}

import scala.jdk.CollectionConverters._

/** Every production file that builds a discard of a card in play attaches
  * `DiscardRestrictions` to it, so a rule about which cards can be discarded
  * (locked, active modifier, Hall of Ministers) cannot be bypassed by a new
  * power that forgets it.
  *
  * A file that builds one of these operations must be listed here with the
  * file that attaches the restrictions, or carry the restrictions itself. A new
  * discarding file fails this suite until it is added on purpose.
  */
class DiscardRestrictionsCoverageSuite extends munit.FunSuite {
  private val root = Paths.get("src/main/scala/oathdigital")
  private val discards =
    """Discard\.(Denizen|Vision|RuinedEdifice)\(""".r

  /** file suffix -> the file that attaches the restrictions to its operations. */
  private val delegated = Map(
    "gameplay/actions/CardPlay.scala" ->
      "gameplay/actions/cardplay/CardPlayProcedure.scala")

  private def read(relative: String): String =
    Files.readString(root.resolve(relative))

  private def sources: Vector[String] = Files.walk(root).iterator.asScala
    .filter(_.toString.endsWith(".scala")).map(p => root.relativize(p).toString)
    .toVector

  test("every file that discards a card in play attaches DiscardRestrictions") {
    val building = sources.filter(path => !path.startsWith("model/") &&
      !path.startsWith("serialization/") && discards.findFirstIn(read(path)).nonEmpty)
    assert(building.nonEmpty)
    val unguarded = building.filter { path =>
      val attached = delegated.getOrElse(path, path)
      !read(attached).contains("DiscardRestrictions")
    }
    assertEquals(unguarded, Vector.empty[String])
  }

  test("CardPlay's callers that build a play attach them") {
    assert(read("gameplay/actions/CardPlay.scala")
      .contains("new DiscardRestrictions"))
    assert(read("gameplay/actions/cardplay/CardPlayProcedure.scala")
      .contains("new DiscardRestrictions"))
  }
}
