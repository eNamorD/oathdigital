package oathdigital.frontend

import oathdigital.protocol.DecisionAnswerWire
import oathdigital.testkit.Unwrap.present

import munit.FunSuite

/** The walker decision drafts and answers: roll summaries, the Forge
  * partition draft and the commands they send.
  */
class WalkerDecisionDraftSuite extends FunSuite:

  test("the roll outcome summary reads the accumulated dice, the score and " +
      "what it is measured against"):
    assertEquals(WalkerPanelSupport.rollOutcomeSummary(
      WalkerRollOutcomeState("recover", Vector("blank", "blank"), 0, Some(4))),
      "Rolled blank, blank -- 0 shields so far (need 4).")
    assertEquals(WalkerPanelSupport.rollOutcomeSummary(
      WalkerRollOutcomeState("recover", Vector("two-shields", "doubler"), 4,
        Some(4))),
      "Rolled two-shields, doubler -- 4 shields so far (need 4).")
    assertEquals(WalkerPanelSupport.rollOutcomeSummary(
      WalkerRollOutcomeState("campaign.attack",
        Vector("two-swords-skull", "one-sword"), 3, None,
        Vector("1 skull loss"))),
      "Rolled two-swords-skull, one-sword -- Attack 3, 1 skull loss.")

  private def forgeItem(index: Int): String =
    WalkerPartitionDraft.itemId(ForgeFixture.query.options(index))

  test("a Forge draft is dropped whenever the question changes"):
    val context = ForgeFixture.context
    val parked = ForgeFixture.parked
    val initial = present(WalkerPartitionDraft.reconcile(None, context,
      Some(parked)), "a partition draft")
    val moved = initial.move(forgeItem(2), "pay-favor")
      .move(forgeItem(0), "pay-secret")
    assertEquals(WalkerPartitionDraft.reconcile(Some(moved), context,
      Some(parked)), Some(moved))
    assertEquals(present(WalkerPartitionDraft.reconcile(Some(moved),
      context.copy(sequence = 10), Some(parked)), "a partition draft")
      .partition, initial.partition)
    assertEquals(present(WalkerPartitionDraft.reconcile(Some(moved), context,
      Some(parked.copy(decisionId = "forge-new"))), "a partition draft")
      .partition, initial.partition)
    // A power that changes the option set asks a different question, so the
    // draft assembled against the old one is dropped.
    assertEquals(present(WalkerPartitionDraft.reconcile(Some(moved), context,
      Some(parked.copy(query = Some(ForgeFixture.query.copy(
        options = ForgeFixture.query.options.drop(1)))))),
      "a partition draft").optionsIn("pay-favor").map(_.label),
      Vector("Denizen 2", "Denizen 3"))
    assertEquals(WalkerPartitionDraft.reconcile(Some(moved), context, None),
      None)

  test("a parked walker decision that is not a partition drives no draft"):
    val context = ForgeFixture.context
    // A choose-one park, and a park whose query was suppressed because an
    // option could not be presented: neither is an answerable partition.
    assertEquals(WalkerPartitionDraft.reconcile(None, context, Some(
      WalkerDecisionState("recover", "recover.choice", "decide",
        query = Some(DecisionQueryState.ChooseOne(Vector(
          DecisionOptionState("button", "stop", "Stop"))))))), None)
    assertEquals(WalkerPartitionDraft.reconcile(None, context,
      Some(ForgeFixture.parked.copy(query = None))), None)

  private val adviser = CardDetails("D1", "denizen", "The Adviser")
  private val otherAdviser = CardDetails("D2", "denizen", "The Other Adviser")

  private def minor(advisers: Vector[MinorAdviser]) = MinorActionsState(
    advisers, canPeekSiteRelics = false, Vector.empty, Some("site:a"), 0, 0)

  /** The minor actions of a player holding both advisers facedown. */
  private def twoAdvisersFacedown = minor(Vector(
    MinorAdviser(adviser), MinorAdviser(otherAdviser)))

  test("Play facedown adviser is offered while any adviser lies facedown"):
    assertEquals(ServerUiSupport.facedownAdviserLaunchCount(
      minor(Vector.empty)), 0)
    assertEquals(ServerUiSupport.facedownAdviserLaunchCount(
      minor(Vector(MinorAdviser(adviser)))), 1)
    assertEquals(ServerUiSupport.facedownAdviserLaunchCount(
      twoAdvisersFacedown), 1)

  test("a choose-one decision outside Recover is answered from its projected options"):
    val query = DecisionQueryState.ChooseOne(Vector(
      DecisionOptionState("player", "blue", "blue"),
      DecisionOptionState("player", "yellow", "yellow")),
      heading = Some("Choose the Oathkeeper"))
    val decision = WalkerDecisionState("oathkeeper", "oathkeeper.recipient",
      "decide", query = Some(query))
    assertEquals(WalkerPanelSupport.resolveChooseOneCommand(decision,
      query.options(1)),
      oathdigital.protocol.GameIntent.ResolveWalker("oathkeeper.recipient",
        DecisionAnswerWire.ChooseOneWire("player", "yellow")))

  test("selection actions map only authorized single target shapes to commands"):
    val placeholderCandidates = Vector("a", "b", "c", "d").map(id =>
      BoardTargetCandidate(BoardTargetRef.Site(id), id, Vector.empty))
    def action(kind: String) = BoardTargetAction(kind, "Choose", 1, 1,
      false, placeholderCandidates)
    assertEquals(ServerUiSupport.commandForSelection(action("travel"),
      Vector(BoardTargetRef.Site("site:b")), "red"),
      Some(oathdigital.protocol.GameIntent.StartWalker("travel", Vector.empty,
        Vector(oathdigital.protocol.WalkerStartArgWire("site", "site:b")))))
    assertEquals(ServerUiSupport.commandForSelection(action("travel"), Vector(
      BoardTargetRef.Site("site:b"), BoardTargetRef.Site("site:c")), "red"), None)
    // Campaign is no longer a board-target selection: it starts from its own
    // control and asks its questions as walker decisions.
    assertEquals(ServerUiSupport.commandForSelection(action("campaign-conquest"),
      Vector(BoardTargetRef.Site("site:b")), "red"), None)

  test("a pool rolls the die its action rolls"):
    assertEquals(WalkerPanelSupport.poolDie("campaign.attack"), Some("attack"))
    assertEquals(WalkerPanelSupport.poolDie("campaign.defense"),
      Some("defense"))
    assertEquals(WalkerPanelSupport.poolDie("recover"), Some("defense"))
    assertEquals(WalkerPanelSupport.poolDie("gambling-hall"), None)
