package oathdigital.frontend

import oathdigital.frontend.ServerModeFixtures.{forgeParked, forgeQuery}
import oathdigital.protocol.DecisionAnswerWire

import munit.FunSuite

/** The walker decision drafts and answers: roll summaries, the Forge
  * partition draft, the facedown adviser draft and the commands they send.
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
    WalkerPartitionDraft.itemId(forgeQuery.options(index))

  test("a Forge draft is dropped whenever the question changes"):
    val context = BoardSelectionContext("game", "red", 9)
    val initial = WalkerPartitionDraft.reconcile(None, context,
      Some(forgeParked)).get
    val moved = initial.move(forgeItem(2), "pay-favor")
      .move(forgeItem(0), "pay-secret")
    assertEquals(WalkerPartitionDraft.reconcile(Some(moved), context,
      Some(forgeParked)), Some(moved))
    assertEquals(WalkerPartitionDraft.reconcile(Some(moved),
      context.copy(sequence = 10), Some(forgeParked)).get.partition,
      initial.partition)
    assertEquals(WalkerPartitionDraft.reconcile(Some(moved), context,
      Some(forgeParked.copy(decisionId = "forge-new"))).get.partition,
      initial.partition)
    // A power that changes the option set asks a different question, so the
    // draft assembled against the old one is dropped.
    assertEquals(WalkerPartitionDraft.reconcile(Some(moved), context,
      Some(forgeParked.copy(query = Some(forgeQuery.copy(
        options = forgeQuery.options.drop(1)))))).get
        .optionsIn("pay-favor").map(_.label),
      Vector("Denizen 2", "Denizen 3"))
    assertEquals(WalkerPartitionDraft.reconcile(Some(moved), context, None),
      None)

  test("a parked walker decision that is not a partition drives no draft"):
    val context = BoardSelectionContext("game", "red", 9)
    // A choose-one park, and a park whose query was suppressed because an
    // option could not be presented: neither is an answerable partition.
    assertEquals(WalkerPartitionDraft.reconcile(None, context, Some(
      WalkerDecisionState("recover", "recover.choice", "decide",
        query = Some(DecisionQueryState.ChooseOne(Vector(
          DecisionOptionState("button", "stop", "Stop"))))))), None)
    assertEquals(WalkerPartitionDraft.reconcile(None, context,
      Some(forgeParked.copy(query = None))), None)

  test("facedown adviser draft supports zero one and multiple choices and only faceup outcomes"):
    val context = BoardSelectionContext("game", "red", 7)
    val card = CardDetails("D1", "denizen", "The Adviser")
    val other = CardDetails("D2", "denizen", "The Other Adviser")
    val replacement = CardDetails("D3", "denizen", "Old Denizen")
    val placements = Vector(MinorAdviserPlacement("discard"),
      MinorAdviserPlacement("play-adviser"),
      MinorAdviserPlacement("play-site", Some(replacement)))
    def minor(advisers: Vector[MinorAdviser]) = MinorActionsState(advisers,
      canPeekSiteRelics = false, Vector.empty, Some("site:a"), 0, 0)
    assertEquals(FacedownAdviserDraft.initial(context, minor(Vector.empty)), None)
    assertEquals(ServerUiSupport.facedownAdviserLaunchCount(minor(Vector.empty)), 0)
    val one = FacedownAdviserDraft.initial(context,
      minor(Vector(MinorAdviser(card, placements)))).get
    assertEquals(one.selectedCardId, Some("D1"))
    assertEquals(ServerUiSupport.facedownAdviserLaunchCount(
      minor(Vector(MinorAdviser(card, placements)))), 1)
    assertEquals(ServerUiSupport.facedownAdviserLaunchCount(
      minor(Vector(MinorAdviser(card, Vector.empty)))), 1)
    assert(FacedownAdviserDraft.initial(context,
      minor(Vector(MinorAdviser(card, Vector.empty)))).nonEmpty)
    assertEquals(one.command, Some(oathdigital.protocol.GameIntent.StartWalker(
      "play-facedown-adviser", Vector.empty,
      Vector(oathdigital.protocol.WalkerStartArgWire("denizen", "D1")))))
    val many = FacedownAdviserDraft.initial(context, minor(Vector(
      MinorAdviser(card, placements), MinorAdviser(other, placements)))).get
    assertEquals(ServerUiSupport.facedownAdviserLaunchCount(minor(Vector(
      MinorAdviser(card, placements), MinorAdviser(other, placements)))), 1)
    assertEquals(many.selected, None)
    assertEquals(many.choose("D2").selected.map(_.card.cardId), Some("D2"))
    assertEquals(FacedownAdviserDraft.reconcile(Some(many.choose("D2")),
      context, Some(minor(Vector(MinorAdviser(card, placements),
        MinorAdviser(other, placements))))).flatMap(_.selected).map(_.card.cardId),
      Some("D2"))
    assertEquals(FacedownAdviserDraft.reconcile(Some(many.choose("D2")),
      context.copy(sequence = 8), Some(minor(Vector(
        MinorAdviser(card, placements), MinorAdviser(other, placements))))), None)

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
