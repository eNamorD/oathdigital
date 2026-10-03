package oathdigital.engine

import oathdigital.testkit.Unwrap.{accepted, rejected}

/** What every `EventEvolution` promises: a fixed initial state, and a fold
  * that is deterministic and refuses an event the state cannot take. The
  * replay engine's running-total fake and the game's `OathRules` both run
  * it, so the fake cannot drift from the evolution the service really
  * replays. */
abstract class EventEvolutionContract[S, E, V] extends munit.FunSuite:
  /** The evolution under test. */
  protected def evolution: EventEvolution[S, E, V]
  /** A sequence of events that is valid from the initial state. */
  protected def events: Vector[E]
  /** An event the initial state refuses. */
  protected def corrupt: E

  private def evolved: Either[V, S] =
    events.foldLeft[Either[V, S]](Right(evolution.initialState))(
      (state, event) => state.flatMap(evolution.evolve(_, event)))

  test("the initial state reads the same twice"):
    assertEquals(evolution.initialState, evolution.initialState)

  test("evolving the same events twice from the initial state agrees"):
    assertEquals(accepted(evolved), accepted(evolved))

  test("the initial state refuses a corrupt event"):
    rejected(evolution.evolve(evolution.initialState, corrupt))
