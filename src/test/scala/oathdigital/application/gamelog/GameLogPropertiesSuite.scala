package oathdigital.application.gamelog

import oathdigital.gameplay.walker.{WalkerCompleted, WalkerParked}
import oathdigital.model._
import LogScripts._

/** What holds for every script (spec, "Testing"). The scripts are built once
  * per run of this suite. */
class GameLogPropertiesSuite extends munit.FunSuite:
  private lazy val scripts = LogScripts.all

  private def viewers(script: Script): Vector[Option[PlayerId]] =
    None +: script.players.map(Some(_))

  test("prefix stability: every segment boundary formats to the same entries"):
    scripts.foreach { script =>
      val steps = script.history.steps
      val boundaries = steps.indices
        .filter(index => closes(steps(index).event.event))
        .map(_ + 1) :+ steps.size
      viewers(script).foreach { viewer =>
        val whole = formatter.format(steps, viewer)
        boundaries.foreach { end =>
          assertEquals(formatter.format(steps.take(end), viewer),
            whole.takeWhile(_.sequence < end),
            s"${script.name} for $viewer at $end")
        }
      }
    }

  test("every viewer receives the same entries with the same keys"):
    scripts.foreach { script =>
      val shapes = viewers(script).map(viewer => formatter
        .format(script.history.steps, viewer)
        .map(entry => (entry.sequence, entry.ordinal, entry.kind, entry.depth)))
      shapes.tail.foreach(shape => assertEquals(shape, shapes.head,
        script.name))
    }

  /** A line judges a card at the operation that moved it, which may lie
    * earlier in the same walker run than the event posting the line (a
    * Campaign's losses post at completion). The states a card may be
    * identified in are therefore every state from the run's first event
    * through the posting event. */
  private def runStates(steps: Vector[oathdigital.engine.ReplayStep[OathState,
      OathEvent]], at: Int): Vector[OathState] =
    val first = (at - 1 to 0 by -1).find(index =>
      steps(index).event.event.isInstanceOf[WalkerCompleted]).fold(0)(_ + 1)
    (first to at).toVector.flatMap(index =>
      Vector(steps(index).before, steps(index).after))

  test("no card span names a card its viewer cannot identify"):
    scripts.foreach { script =>
      val steps = script.history.steps
      script.players.foreach { player =>
        formatter.format(steps, Some(player)).foreach { entry =>
          val step = steps(entry.sequence.toInt)
          entry.spans.collect { case card: LogSpan.Card => card }
            .foreach { card =>
              val id = cardId(card.id, step)
              val known = runStates(steps, entry.sequence.toInt).exists {
                case OathState.Ready(ready) => id.exists(value =>
                  presentation.identifiesAt(ready, Some(player), value))
                case _ => false
              }
              assert(known, s"${script.name}: $player may not read " +
                s"${card.name} at ${entry.sequence}")
            }
        }
      }
    }

  test("scan ends on the state load reconstructs"):
    scripts.foreach { script =>
      assertEquals(script.history.steps.last.after,
        script.service.load(script.name).toOption.flatten.get.state,
        script.name)
    }

  test("every ProcedureRef key the formatter handles appears in some script"):
    val keys = scripts.flatMap(_.history.steps.map(_.event.event).collect {
      case completed: WalkerCompleted => completed.procedure.key
    }).toSet
    val expected = Set("travel", "search", "play-facedown-adviser", "muster",
      "trade", "take-wealth", "recover", "forge", "challenge",
      "place-banner-resource", "campaign", "negotiation", "end-wake",
      "begin-rest", "finish-rest", "oathkeeper", "setup")
    assertEquals(expected -- keys, Set.empty[String])
    assert(keys.exists(_.startsWith("use-power:")), keys)

  /** A segment ends at a park or a completion: one command's append. */
  private def closes(event: OathEvent): Boolean = event match
    case _: WalkerParked | _: WalkerCompleted => true
    case _ => false

  /** The card a span's id names, found among the cards either side of its
    * step. */
  private def cardId(value: String,
      step: oathdigital.engine.ReplayStep[OathState, OathEvent])
      : Option[CardId] =
    Vector(step.before, step.after).collectFirst {
      case OathState.Ready(ready) => CardIndex.from(ready.game).toOption
        .flatMap(_.ids.find(_.value == value))
    }.flatten
