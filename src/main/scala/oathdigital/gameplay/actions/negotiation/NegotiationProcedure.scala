package oathdigital.gameplay.actions.negotiation

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.model._
import oathdigital.model.DecisionAnswer.DeclineDeal

/** Negotiation on the walker: choose who to negotiate with, then loop one
  * deal decision that every participant may answer until the deal closes,
  * then settle it if it was agreed. A 0-Supply minor action taking no start
  * selection.
  *
  * {{{
  * Sequence(
  *   Decide(negotiators)                  -- omitted when there is one candidate
  *   Repeat(!closed) { Branch -> Decide(deal, co-owned) }
  *   Branch(agreed -> BuildOps(settle)))
  * }}}
  *
  * Nothing about the deal is stored: the guard, the query and the settlement
  * all read `PendingTree.answered` (see [[NegotiationDeal]]).
  */
object NegotiationProcedure:
  private val notes = NoteSet(ProcedureNotes.id(ActionRef.Negotiation))
  /** "Negotiated with p2", or "Negotiated with p2 and p3". */
  val negotiated: NoteKey = notes.key("negotiated", Vector(
    NotePart.Text("Negotiated with "), NotePart.Arg(0)))
  val ended: NoteKey = notes.key("ended", Vector(
    NotePart.Text("Negotiation ended by "), NotePart.Arg(0)))
  /** "p1 gave 1 favor to p2". */
  val gaveFavor: NoteKey = notes.key("gave-favor", Vector(
    NotePart.Arg(0), NotePart.Text(" gave "), NotePart.Arg(1),
    NotePart.Text(" favor to "), NotePart.Arg(2)))
  val gaveCard: NoteKey = notes.key("gave-card", Vector(
    NotePart.Arg(0), NotePart.Text(" gave "), NotePart.Arg(1),
    NotePart.Text(" to "), NotePart.Arg(2)))
  /** "p2 showed p1 Birdsong". */
  val showed: NoteKey = notes.key("showed", Vector(
    NotePart.Arg(0), NotePart.Text(" showed "), NotePart.Arg(1),
    NotePart.Text(" "), NotePart.Arg(2)))
  /** "p1 was shown Sticky Fire at Broken Peaks". */
  val wasShown: NoteKey = notes.key("was-shown", Vector(
    NotePart.Arg(0), NotePart.Text(" was shown "), NotePart.Arg(1),
    NotePart.Text(" at "), NotePart.Arg(2)))
  def noteKeys: Vector[NoteKey] = notes.declared

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for
    _ <- noStartArgs(args)
    _ <- Either.cond(NegotiationDeal.eligible(state, actor).nonEmpty, (),
      OathViolation.NegotiationUnavailable(
        "no other player has a pawn at your site"))
  yield tree(state, actor)

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    noStartArgs(args).map(_ => tree(state, actor))

  private def noStartArgs(args: Vector[DecisionOptionRef])
      : Either[OathViolation, Unit] = Either.cond(args.isEmpty, (),
    OathViolation.InvalidEventOrder(
      s"walker procedure ${ActionRef.Negotiation.key} takes no start " +
        s"selection, got ${args.map(_.kind).mkString(", ")}"))

  private def tree(state: ReadyGame, actor: PlayerId): Operation =
    val candidates = NegotiationDeal.eligible(state, actor)
    val choose: Vector[Operation] =
      if candidates.size < 2 then Vector.empty
      else Vector(Decide(NegotiationDeal.negotiatorsDecisionId, actor,
        DecisionQuery.ChooseMany(1, candidates.size, candidates.map(id =>
          DecisionOption.Player(DecisionOptionRef.Player(id))),
          heading = Some("Choose who to negotiate with"),
          confirm = Some(CountedConfirm("Negotiate with", "player",
            "players"))),
        window = Some(PowerWindow.NegotiationEligibility)))
    Sequence(choose ++ Vector[Operation](
      Repeat((ready, pending) =>
        !NegotiationDeal.deal(ready, actor, pending).closed,
        Branch((ready, pending) => {
          val deal = NegotiationDeal.deal(ready, actor, pending)
          Vector(Decide(NegotiationDeal.dealDecisionId, actor,
            NegotiationDeal.snapshot(ready, deal),
            coOwners = deal.participants.filter(_ != actor)))
        })),
      Branch((ready, pending) => {
        val deal = NegotiationDeal.deal(ready, actor, pending)
        // The opening line, as the rule posted it: the decline names who
        // ended the deal, and anything else names who was negotiated with,
        // whether or not the deal had substance to settle.
        val opening: Vector[Operation] = Vector(
          Note(ProcedureNotes.id(ActionRef.Negotiation),
            if deal.declined then endedNote
            else _ => Some(negotiated.sourceless(NoteArg.Players(
              NegotiationDeal.chosen(ready, actor, pending))))))
        // The settlement's lines, in the order `settle` applies them:
        // disclosures first, then transfers. A refused settlement fails the
        // command, so nothing is journaled and no note is reached.
        //
        // They state the agreement rather than the journal: the deleted
        // rule read the executed operations, these are derived from the
        // same `DealState` the settling node is. No power contributes at
        // `NegotiationSettlement`; a first one that removed an operation
        // would leave its line standing, so it must not.
        val settlement: Vector[Operation] =
          if !deal.agreed then Vector.empty
          else BuildOps((state, _) => NegotiationDeal.settle(state, deal),
            window = Some(PowerWindow.NegotiationSettlement)) +:
            (disclosed(deal) ++ transferred(deal)).map(note =>
              Note(ProcedureNotes.id(ActionRef.Negotiation), _ => Some(note)))
        opening ++ settlement
      })))

  private def endedNote(states: NoteStates): Option[PowerNote] =
    states.answered.reverse.collectFirst {
      case Answered(NegotiationDeal.dealDecisionId, DeclineDeal, by) =>
        ended.sourceless(NoteArg.Player(by))
    }

  /** One line per disclosure, in `NegotiationDeal.knowledge`'s order. */
  private def disclosed(deal: DealState): Vector[PowerNote] =
    deal.participants.flatMap(deal.terms(_).disclosures).map:
      case NegotiationDisclosure(recipient,
          NegotiationDisclosureRef.Adviser(owner, card)) =>
        showed.sourceless(NoteArg.Player(owner), NoteArg.Player(recipient),
          NoteArg.Card(card))
      case NegotiationDisclosure(recipient,
          NegotiationDisclosureRef.HeldRelic(owner, relic)) =>
        showed.sourceless(NoteArg.Player(owner), NoteArg.Player(recipient),
          NoteArg.Card(relic))
      case NegotiationDisclosure(recipient,
          NegotiationDisclosureRef.SiteRelic(site, relic)) =>
        wasShown.sourceless(NoteArg.Player(recipient), NoteArg.Card(relic),
          NoteArg.Site(site))

  /** One line per transfer, in `NegotiationDeal.transfers`' order: each
    * author's favor before their relics. */
  private def transferred(deal: DealState): Vector[PowerNote] =
    deal.participants.flatMap(author =>
      deal.terms(author).transfers.flatMap(transfer =>
        Option.when(transfer.favor > 0)(gaveFavor.sourceless(
          NoteArg.Player(author), NoteArg.Number(transfer.favor),
          NoteArg.Player(transfer.recipient))).toVector ++
          transfer.relics.map(relic => gaveCard.sourceless(
            NoteArg.Player(author), NoteArg.Card(relic),
            NoteArg.Player(transfer.recipient)))))
