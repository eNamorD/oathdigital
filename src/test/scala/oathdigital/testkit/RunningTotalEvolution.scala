package oathdigital.testkit

import oathdigital.engine.EventEvolution

/** Fake of EventEvolution: the state is the running total of the events
  * applied, and a negative event is corrupt, so a replay test needs no game
  * to see the engine fold events and name the one that breaks. */
object RunningTotalEvolution extends EventEvolution[Int, Int, String]:
  val initialState = 0
  def evolve(state: Int, event: Int): Either[String, Int] =
    if event < 0 then Left(s"negative $event") else Right(state + event)
