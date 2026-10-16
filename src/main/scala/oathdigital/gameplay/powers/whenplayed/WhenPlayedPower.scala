package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** A When Played power of one denizen. When that card is played, `effect`'s
  * operations run after whatever other powers contributed at the
  * card-played window, inside an `AutomaticUse` the walker judges when it
  * reaches it: a power-use restriction may refuse the card's player the
  * effect (catalog batch 8, slice 10).
  *
  * `effect` is called on every fold, and the walker folds again on every
  * command, so it must be a pure function of the state in `ctx`. Anything
  * that depends on state an earlier operation changes belongs inside a
  * `BuildOps`, `Branch` or `Repeat` it returns, which the walker evaluates
  * at walk time.
  */
trait WhenPlayedPower extends ContributingPower:
  def cardId: DenizenId
  def effect(ctx: PowerCtx): Vector[Operation]

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  /** Fires on its card's faceup play, unless the card went to the world
    * deck before the hook: Family Wagon buried it on arrival, so it was
    * never gained (catalog batch 5 rulings, slice 4). A move of the card
    * elsewhere (Twin Brother, Great Herd) keeps it firing, so a resumed walk
    * folds the hook the same way. */
  final override def applicable(ctx: PowerCtx): Boolean = ctx.operation match
    case CardPlayedFaceup(card, _) => card == cardId &&
      !ctx.state.game.current.commonCards.worldDeck.contains(cardId)
    case _ => false

  final def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, children) =>
      children :+ AutomaticUse(ctx.activePlayer, id, cardId,
        (_, _) => effect(ctx)))))
