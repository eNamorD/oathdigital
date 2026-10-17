package oathdigital.application.gamelog

import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ChoicePayload, WalkerCompleted,
  WalkerParked, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseManyAnswer, ChooseOneAnswer}
import oathdigital.protocol.projection.{LogKind, LogSpan}
import oathdigital.testkit.Unwrap.{accepted, present}
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
        .map(entry => (entry.sequence, entry.ordinal, entry.kind, entry.depth,
          entry.section)))
      shapes.tail.foreach(shape => assertEquals(shape, shapes.head,
        script.name))
    }

  test("no headline carries a section"):
    scripts.foreach { script =>
      val headlines = formatter.format(script.history.steps, None)
        .filter(_.depth == 0)
      assert(headlines.forall(_.section.isEmpty), script.name)
    }

  private val tagged = Set("search", "play-facedown-adviser", "recover",
    "forge", "travel", "muster", "trade", "challenge", "campaign")

  test("an action with a tag opens with it, once; no other action has one"):
    scripts.foreach { script =>
      val entries = formatter.format(script.history.steps, None)
      entries.flatMap(_.section).distinct.foreach { section =>
        val own = entries.filter(_.section.contains(section))
        val tags = own.filter(_.kind == LogKind.Section)
        if tagged(section.action) then
          // A run that opens with no tag yet (a Muster still choosing its
          // source) has none; a run that has one shows it first.
          assert(tags.size <= 1, s"${script.name}: $tags")
          tags.headOption.foreach(tag => assertEquals(own.head, tag,
            s"${script.name}: a line came before the ${section.action} tag"))
        else assertEquals(tags, Vector.empty, script.name)
      }
    }

  test("no line still reads as a start line"):
    scripts.foreach { script =>
      val all = texts(formatter.format(script.history.steps, None))
      assert(!all.exists(line => line.startsWith("Started ") ||
        line.startsWith("Playing Facedown") ||
        line.startsWith("Continued Recover")), s"${script.name}: $all")
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
        present(accepted(script.service.load(script.name)), script.name).state,
        script.name)
    }

  /** Every procedure production can complete, so a new one fails here until
    * a script completes it. `ProcedureRef.all` lists the finite references;
    * use-power is parameterized by its power, so it is checked by prefix. */
  test("every ProcedureRef key the formatter handles appears in some script"):
    val keys = scripts.flatMap(_.history.steps.map(_.event.event).collect {
      case completed: WalkerCompleted => completed.procedure.key
    }).toSet
    val expected = ProcedureRef.all.map(_.key).toSet
    assertEquals(expected -- keys, Set.empty[String])
    assert(keys.exists(_.startsWith("use-power:")), keys)

  /** Every decision a script asks, with the procedure whose run asked it and
    * the sequence of the step that answered it. A run begins at the first
    * walker event after the previous completion, as the formatter reads it.
    * A step voided by an undo still belongs to its procedure, so taking it
    * in only widens the check. Answers that name no option post no line
    * either way, so they are left out. */
  private def asked(script: Script): Vector[(ProcedureRef, String, Long)] =
    val journal = new LogJournal(script.history.steps)
    (0 until journal.size).foldLeft((Option.empty[ProcedureRef],
        Vector.empty[(ProcedureRef, String, Long)])) {
      case ((current, found), at) =>
        val procedure = current.orElse(journal.procedureAt(at))
        val more = (journal.event(at), procedure) match
          case (WalkerStepRecorded(_, ChoicePayload(id, answer, _), _, _),
              Some(ref)) if namesAnOption(answer) =>
            Vector((ref, id, journal.sequence(at)))
          case _ => Vector.empty
        val next = journal.event(at) match
          case _: WalkerCompleted => None
          case _ => procedure
        (next, found ++ more)
    }._2

  /** Whether an answer names an option, as `DetailLines.decision` reads it:
    * Done and Continue are not choices, and an empty choose-many is not one
    * either. */
  private def namesAnOption(answer: DecisionAnswer): Boolean = answer match
    case ChooseOneAnswer(DecisionQuery.Inspect.Done) => false
    case ChooseOneAnswer(Decide.Pass) => false
    case ChooseOneAnswer(_) => true
    case ChooseManyAnswer(selected) => selected.nonEmpty
    case _ => false

  /** Nothing a run asks goes untold. A decision either posts its own
    * "Chose ..." line, or something declares that another line tells its
    * answer: the procedure's own entry, one of the two cross-cutting
    * predicates, or a power's note. What this catches is a line that
    * disappears with nothing claiming to tell it, named by procedure and
    * decision id rather than as a diff in a golden log.
    *
    * The opposite mistake -- an entry that forgets a narration -- posts the
    * stray "Chose ..." line instead of hiding it, so it is accounted for
    * here and caught by the tag-order property above and by the golden
    * logs. This property does not stand in for them. */
  test("every decision a script asks is told by some line"):
    val byPowers = NoteWordings.default(catalog).narrated
    val unaccounted = scripts.flatMap { script =>
      val decided = formatter.format(script.history.steps, None)
        .filter(_.kind == LogKind.Decision).map(_.sequence).toSet
      asked(script).collect {
        case (procedure, id, sequence) if !decided(sequence) &&
            !formatter.procedures.of(procedure).narrates(id) &&
            !DetailLines.crossCutting(id) && !byPowers(id) =>
          s"${script.name}: ${procedure.key} asks $id, and no line tells it"
      }
    }.distinct
    assertEquals(unaccounted, Vector.empty[String])

  /** A declaration no script exercises is reported, not asserted: an exact
    * set would make every new script a two-file edit, which is the cost the
    * registry exists to cut. A decision whose answer names no option, such
    * as an amount or a partition, is not exercised here however often a
    * script asks it: no line is posted for it either way. */
  test("declared narrations no script exercises are reported"):
    val exercised = scripts.flatMap(asked).map((procedure, id, _) =>
      procedure -> id).toSet
    val idle = ProcedureRef.all.flatMap(procedure =>
      formatter.procedures.of(procedure).narrated.map(_.decision).sorted
        .filterNot(id => exercised((procedure, id)))
        .map(id => s"[narration] ${procedure.key} declares $id, and no " +
          "script answers it with an option that would post a line"))
    if idle.nonEmpty then println(idle.mkString("\n"))

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

  /** Every procedure says which of three things it is, so a procedure that
    * journals its own notes is not read as one that posts nothing
    * (procedures journal their own notes design, ruling 4). */
  test("every procedure states whether its line is a rule, its notes or absent"):
    val logs = formatter.procedures
    val silent = ProcedureRef.all.map(procedure =>
      procedure.key -> logs.of(procedure).lines).collect {
      case (key, ActionLineSource.Silent(reason)) => key -> reason
    }
    assert(silent.forall(_._2.nonEmpty), s"a silent procedure says why: $silent")
    // Rises by one with every procedure the migration moves.
    val noting = ProcedureRef.all.filter(
      logs.of(_).lines.isInstanceOf[ActionLineSource.Notes])
    assertEquals(noting.map(_.key),
      Vector("forge", "travel", "take-wealth",
        "muster", "challenge", "place-banner-resource", "negotiation",
        "peek-site-relics", "reveal-owned-relic", "move-warbands",
        "finish-rest", "oathkeeper", "setup"))
    // A procedure can be marked `Notes` while declaring none, and then its
    // line vanishes with nothing said: `NoteCheck` refuses a note whose key
    // is undeclared. Tie the two together here, since nothing else does.
    val keyless = noting.filter(procedure =>
      !ProcedureNotes.all.exists((id, keys) =>
        id == ProcedureNotes.id(procedure) && keys.nonEmpty))
    assertEquals(keyless.map(_.key), Vector.empty,
      "a procedure that journals its own notes declares at least one key")
    // And a narration that names a key names one of that procedure's own:
    // the compiler sees only that the symbol exists.
    val borrowed = ProcedureRef.all.flatMap(procedure =>
      logs.of(procedure).narrated.flatMap(_.by).collect {
        case key if !ProcedureNotes.all.exists((id, keys) =>
          id == ProcedureNotes.id(procedure) && keys.contains(key)) =>
          s"${procedure.key} narrates with ${key.name}, which it does not declare"
      })
    assertEquals(borrowed, Vector.empty[String])
