package oathdigital.gameplay

import oathdigital.gameplay.walker.{PowerNoted, ProcedureWalker, WalkerOutcome,
  WalkerPowers, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

/** The `Note` node (power log lines design, section 1). */
class PowerNoteWalkerSuite extends munit.FunSuite:
  private val actor: PlayerId = playerId
  private val power = PowerId("test.noting")
  private val source = PowerSourceRef.Site(SiteId("test-site"))
  private val pool = PoolKey("test-pool")
  private def said(word: String) = PowerNote(source, word, Vector.empty)
  private def note(word: String): Note = Note(power, _ => Some(said(word)))
  private def noted(word: String) = PowerNoted(power, said(word), covers = false)
  private def button(key: String) =
    DecisionOption.Button(DecisionOptionRef.Button(key), key)
  private val ask = Decide("ask", actor,
    DecisionQuery.ChooseOne(Vector(button("yes"))))
  private val yes = Answered("ask",
    DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("yes")), actor)
  private def notes(events: Vector[OathEvent]): Vector[OathEvent] =
    events.filter(_.isInstanceOf[PowerNoted])

  test("a note is journaled where the walk reaches it, between the steps"):
    val tree = Sequence(Vector[Operation](note("first"),
      ModifyDicePool(pool, 1), note("second")))
    ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty) match
      case Right(WalkerOutcome.Finished(_, events)) =>
        assertEquals(events.map {
          case noted: PowerNoted => noted.note.key
          case _: WalkerStepRecorded => "step"
          case other => other.toString
        }, Vector("first", "step", "second"))
      case other => fail(s"expected a finished walk, got $other")

  test("a note reads the states around the step before it"):
    var seen = Option.empty[NoteStates]
    val reading = Note(power, states => { seen = Some(states); Some(said("read")) })
    val tree = Sequence(Vector[Operation](ModifyDicePool(pool, 1), reading))
    assert(ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty).isRight)
    val states = seen.get
    val (before, after) = states.previous.get
    assertEquals(after, states.now)
    assertEquals(before.game.current.rollPools.get(pool), None)
    assert(after.game.current.rollPools.contains(pool))

  test("a note before any step has no previous step"):
    var seen = Option.empty[NoteStates]
    val reading = Note(power, states => { seen = Some(states); None })
    assert(ProcedureWalker.advance(ready, Sequence(Vector[Operation](reading)),
      None, WalkerPowers.empty).isRight)
    assertEquals(seen.map(_.previous), Some(None))

  test("a note after a leaf that changed nothing reads no change, not an older step"):
    var seen = Option.empty[NoteStates]
    val reading = Note(power, states => { seen = Some(states); None })
    val tree = Sequence(Vector[Operation](ModifyDicePool(pool, 1),
      BuildOps((_, _) => Right(Vector.empty)), reading))
    assert(ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty).isRight)
    val (before, after) = seen.get.previous.get
    assertEquals(before, after)
    assert(after.game.current.rollPools.contains(pool))

  test("a note whose build says nothing journals nothing"):
    val tree = Sequence(Vector[Operation](Note(power, _ => None),
      ModifyDicePool(pool, 1)))
    val Right(WalkerOutcome.Finished(_, events)) =
      ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty): @unchecked
    assertEquals(notes(events), Vector.empty)

  test("a note in a branch that does not select it is not journaled"):
    val tree = Branch((state, _) =>
      if state.game.current.rollPools.contains(pool) then Vector(note("chosen"))
      else Vector(ModifyDicePool(pool, 1)))
    val Right(WalkerOutcome.Finished(_, events)) =
      ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty): @unchecked
    assertEquals(notes(events), Vector.empty)

  test("a resumed command journals only the notes after the park"):
    val tree = Sequence(Vector[Operation](note("before"), ask, note("after")))
    val Right(WalkerOutcome.Parked(pending, first)) =
      ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty): @unchecked
    assertEquals(notes(first), Vector(noted("before")))
    val Right(WalkerOutcome.Finished(_, second)) = ProcedureWalker.resolve(ready,
      tree, pending, yes, WalkerPowers.empty): @unchecked
    assertEquals(notes(second), Vector(noted("after")))

  test("each new pass of a Repeat journals its note again"):
    val tree = Repeat((_, pending) => pending.answered.size < 2,
      Sequence(Vector[Operation](note("pass"), ask)))
    val Right(WalkerOutcome.Parked(first, e1)) =
      ProcedureWalker.advance(ready, tree, None, WalkerPowers.empty): @unchecked
    assertEquals(notes(e1), Vector(noted("pass")))
    val Right(WalkerOutcome.Parked(second, e2)) = ProcedureWalker.resolve(ready,
      tree, first, yes, WalkerPowers.empty): @unchecked
    assertEquals(notes(e2), Vector(noted("pass")))
    val Right(WalkerOutcome.Finished(_, e3)) = ProcedureWalker.resolve(ready,
      tree, second, yes, WalkerPowers.empty): @unchecked
    assertEquals(notes(e3), Vector.empty)

  test("a Repeat whose pass only notes stops after one pass"):
    ProcedureWalker.advance(ready, Repeat((_, _) => true, note("only")), None,
        WalkerPowers.empty) match
      case Right(WalkerOutcome.Finished(_, events)) =>
        assertEquals(events, Vector[OathEvent](noted("only")))
      case other => fail(s"expected one pass, got $other")

  test("replay applies a note as nothing"):
    val state = OathState.Ready(ready)
    assertEquals(ProcedureWalker.applyRecorded(state, noted("x")), Right(state))

  test("a phase power's selected card, relic, edifice, site or banner is its " +
      "note source"):
    assertEquals(PowerSourceRef.of(DecisionOptionRef.Denizen(DenizenId("93"))),
      Some(PowerSourceRef.Card(DenizenId("93"))))
    assertEquals(PowerSourceRef.of(DecisionOptionRef.Site(SiteId("s"))),
      Some(PowerSourceRef.Site(SiteId("s"))))
    assertEquals(PowerSourceRef.of(DecisionOptionRef.Banner(Banner.DarkestSecret)),
      Some(PowerSourceRef.Banner(Banner.DarkestSecret)))
    assertEquals(PowerSourceRef.of(DecisionOptionRef.Button("b")), None)
