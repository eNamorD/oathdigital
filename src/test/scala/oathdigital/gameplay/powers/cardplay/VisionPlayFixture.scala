package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.powers.{CardStaging, PowerFixture, SearchFixture,
  TargetsFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.ParkedNode

/** The two ways to play a Vision, each stopped at its placement decision, for
  * the suites of the cards that forbid or reward a faceup Vision.
  */
object VisionPlayFixture:
  import PowerFixture._

  private def orFail(result: Either[OathViolation, OathTransition])
      : OathTransition =
    result.fold(error => throw new AssertionError(error.toString), identity)

  /** A Search that drew `vision`, parked on its placement. `ready` must come
    * from `SearchFixture.staged(Vector(vision))`. The events are the whole
    * Search's so far: with one card drawn the placement is reached in the
    * command that starts it, and its notes are journaled there.
    */
  def searched(ready: ReadyGame, vision: VisionId): OathTransition =
    orFail(SearchFixture.start(ready).flatMap(started =>
      SearchFixture.keep(started, vision).map(kept =>
        kept.copy(events = started.events ++ kept.events))))

  /** The Play-Facedown-Adviser action on `vision`, which the actor is given as
    * a facedown adviser, parked on its placement. `ready` must be in the Act
    * phase.
    */
  def fromAdvisers(ready: ReadyGame, vision: VisionId): OathTransition =
    val holding = TargetsFixture.giveVision(CardStaging.without(ready, vision),
      actor, vision, Orientation.FaceDown)
    orFail(SearchFixture.rules.startWalker(Ready(holding),
      ActionRef.PlayFacedownAdviser, actor, Vector.empty,
      Vector(DecisionOptionRef.Vision(vision))))

  /** The buttons of the placement decision the walk is parked on. */
  def offered(from: OathTransition): Vector[String] =
    ParkedNode.of(from.state, catalog, WalkerPowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide.query match
        case DecisionQuery.ChooseOne(options, _) => options.map(_.ref).collect {
          case DecisionOptionRef.Button(key) => key }
        case other => throw new AssertionError(s"not a choose-one: $other")
      case other => throw new AssertionError(s"not parked on a decision: $other")
