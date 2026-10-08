package oathdigital.gameplay

import oathdigital.engine.EventEvolutionContract
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.model.{OathEvent, OathState, OathViolation}

/** `OathRules`, which the service's replay engine delegates to, keeps
  * the `EventEvolution` contract. The events are the first game's setup
  * through Wake. Their last, `WalkerCompleted` for Setup, has no game to
  * act on before `GameStarted`, so `NoGame` refuses it (`GameNotStarted`). */
class OathRulesEvolutionSuite
    extends EventEvolutionContract[OathState, OathEvent, OathViolation]:
  protected val evolution = new OathRules(FirstGameSetupFixture.catalog)
  // The real setup's events on purpose: the contract replays a real journal.
  protected def events = FirstGameSetupFixture.setupThroughWake().events
  protected def corrupt = events.last
