package oathdigital.gameplay.powers.action

import oathdigital.catalog.PrintedPower
import oathdigital.gameplay.powers.banner.WanderingFlameMove
import oathdigital.model._

/** A card's paid action reads its id and cost from the power its card
  * prints; a banner's, which no card prints, names both. */
class PaidActionSuite extends munit.FunSuite:
  private val printed = PrintedPower(PowerId("denizen.example"),
    persistent = false, cost = Cost(favor = 2, secretBurnt = 1),
    text = "**ACTION:** Do the example.")

  private object Example extends PaidAction(printed):
    def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
        : Either[OathViolation, Operation] = Right(Sequence(Vector.empty))

  test("a card's paid action takes its id and cost from the printed power"):
    assertEquals(Example.id, PowerId("denizen.example"))
    assertEquals(Example.cost, Cost(favor = 2, secretBurnt = 1))

  test("a card's paid action charges what its card prints"):
    assertEquals(Storyteller.id, StorytellerCard.power.id)
    assertEquals(Storyteller.cost, StorytellerCard.power.cost)

  test("a banner's paid action names its own id and cost"):
    assertEquals(WanderingFlameMove.id,
      PowerId("banner.darkest-secret.wandering-flame.move"))
    assertEquals(WanderingFlameMove.cost, Cost.free)
