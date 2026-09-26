package oathdigital.application.gamelog

import java.nio.file.{Files, Path, Paths}
import LogScripts._

/** The exact log of every script, for its actor and for one other seat
  * (spec, "Golden tests"). `GAMELOG_GOLDEN=write` rewrites the files
  * instead of comparing; a rewritten golden is reviewed line by line before
  * it is committed. */
class GameLogGoldenSuite extends munit.FunSuite:
  private val directory: Path = Paths.get("src/test/resources/gamelog")
  private val writing = sys.env.get("GAMELOG_GOLDEN").contains("write")

  named.foreach { case (name, build) =>
    test(s"$name reads exactly as its golden log for the actor and another seat"):
      val script = build()
      val other = script.players.find(_ != script.actor).get
      Vector("actor" -> script.actor, "other" -> other).foreach {
        case (seat, viewer) =>
          val actual = GoldenLog.render(format(script, Some(viewer)))
          val file = directory.resolve(s"$name.$seat.log")
          if writing then
            Files.createDirectories(directory)
            Files.writeString(file, actual)
          else
            assert(Files.exists(file), s"missing golden $file; run with " +
              "GAMELOG_GOLDEN=write and review it")
            assertNoDiff(actual, Files.readString(file), s"$name for $seat")
      }
  }
