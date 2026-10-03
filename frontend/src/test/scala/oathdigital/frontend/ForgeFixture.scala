package oathdigital.frontend

/** The Forge pay partition the walker-decision suites draw: Forge parks on
  * decision "forge-9" asking the player to place three denizens into "pay
  * favor" (at least 2) and "pay secret" (at least 1). The sections carry
  * their own labels and minima, the denizen options their own references,
  * and generic code assembles the answer, so nothing here states Forge's
  * printed cost.
  */
private[frontend] object ForgeFixture:
  val query: DecisionQueryState.Partition = DecisionQueryState.Partition(
    Vector(DecisionSectionState("pay-favor", "Pay Favor", 2),
      DecisionSectionState("pay-secret", "Pay Secret", 1)),
    Vector("1", "2", "3").map(id =>
      DecisionOptionState("denizen", s"denizen:$id", s"Denizen $id")))

  /** The parked walker decision whose question is `query`. */
  val parked: WalkerDecisionState = WalkerDecisionState("forge", "forge-9",
    "decide", query = Some(query))

  /** The position red answers it at: game "game", sequence 9. */
  val context: BoardSelectionContext = BoardSelectionContext("game", "red", 9)
