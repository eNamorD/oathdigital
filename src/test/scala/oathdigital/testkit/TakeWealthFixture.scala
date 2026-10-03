package oathdigital.testkit

import oathdigital.gameplay.OathRules
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Take Wealth's Wake board and command, shared by `TakeWealthProcedureSuite`
  * and `OathkeeperProcedureSuite`, so "a Wake board with a takeable
  * resource" has one definition.
  */
object TakeWealthFixture:
  val favorArg: Vector[DecisionOptionRef] = Vector(
    DecisionOptionRef.Button("favor"))
  val secretArg: Vector[DecisionOptionRef] = Vector(
    DecisionOptionRef.Button("secret"))

  /** `from`, with the active player's pawn site holding `favor` and
    * `secrets`. With `sharedEnemy`, every other player's pawn stands there
    * too, and an enemy pawn at the site blocks the take. */
  def wakeReady(from: ReadyGame, favor: Int, secrets: Int,
      sharedEnemy: Boolean = false)(using munit.Location): ReadyGame =
    val actor = Look(from).active
    val site = Look(from).pawn(actor)
    val stocked = Table.from(from).siteTokens(site, favor = favor,
      secrets = secrets)
    val others = from.game.current.players.map(_.player).filterNot(_ == actor)
    (if sharedEnemy then others.foldLeft(stocked)(_.pawn(_, site))
     else stocked).unchecked

  /** The active player takes `args` from their pawn site. */
  def take(rules: OathRules, ready: ReadyGame,
      args: Vector[DecisionOptionRef] = favorArg)
      : Either[OathViolation, OathTransition] =
    rules.startWalker(Ready(ready), ActionRef.TakeWealth,
      ready.game.current.turn.activePlayer, Vector.empty, args)
