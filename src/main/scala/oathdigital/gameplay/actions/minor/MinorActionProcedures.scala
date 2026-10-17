package oathdigital.gameplay.actions.minor

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.gameplay.powers.economy.VowOfWandering
import oathdigital.model._
import oathdigital.model.OathViolation.{InvalidEventOrder,
  MinorActionUnavailable, PawnSiteMissing}

/** The three minor actions that were legacy commands (walker-driven game
  * loop design, "The minor actions"): Peek Site Relics, Reveal Owned Relic
  * and Move Warbands. Their rules are the legacy handler's, moved into
  * walker trees.
  *
  * A start checks the first-game rules gate, as the handler did, and
  * takes no start selection: what the player picks is
  * asked inside the walk. Every operation is required, so a restriction
  * that refuses one refuses the action with its own reason. A resume
  * rebuilds the same tree without the start's gates.
  */
object MinorActionProcedures:
  type Build = (ExecutableCatalog, ReadyGame, PlayerId,
    Vector[DecisionOptionRef]) => Either[OathViolation, Operation]

  val relicDecisionId: String = "minor.reveal-relic"
  val directionDecisionId: String = "minor.warbands-direction"
  val amountDecisionId: String = "minor.warbands-amount"
  val toSite: DecisionOptionRef.Button = DecisionOptionRef.Button("to-site")
  val toBoard: DecisionOptionRef.Button = DecisionOptionRef.Button("to-board")

  /** One set per procedure, because each is held to its own declared keys.
    * `NoteSet` is read through a `def`, so these are plain `val`s. */
  private val peekNotes = NoteSet(ProcedureNotes.id(ActionRef.PeekSiteRelics))
  /** "Peeked at Sticky Fire at Broken Peaks". A site holds at most two
    * relics, so the list stays the inline phrase and never becomes a
    * `Cards` span. */
  val peeked: NoteKey = peekNotes.key("peeked", Vector(
    NotePart.Text("Peeked at "), NotePart.Arg(0), NotePart.Text(" at "),
    NotePart.Arg(1)))
  def peekKeys: Vector[NoteKey] = peekNotes.declared

  val buildPeek: Build = (_, ready, actor, args) =>
    for
      _ <- started(ready, actor, args, ActionRef.PeekSiteRelics)
      site <- pawnSite(ready, actor)
      _ <- Either.cond(site.relics.nonEmpty, (),
        MinorActionUnavailable("the pawn's site has no relics"))
    yield peekTree(actor)

  val rebuildPeek: Build = (_, _, actor, _) => Right(peekTree(actor))

  val buildReveal: Build = (_, ready, actor, args) =>
    for
      _ <- started(ready, actor, args, ActionRef.RevealOwnedRelic)
      _ <- Either.cond(facedownRelics(ready, actor).nonEmpty, (),
        MinorActionUnavailable("no facedown relic to reveal"))
    yield revealTree(ready, actor)

  val rebuildReveal: Build = (_, ready, actor, _) =>
    Right(revealTree(ready, actor))

  val buildMove: Build = (_, ready, actor, args) =>
    for
      _ <- started(ready, actor, args, ActionRef.MoveWarbands)
      _ <- pawnSite(ready, actor)
      blocked = Vector(toSite, toBoard).flatMap(way =>
        most(ready, actor, way).left.toOption)
      _ <- Either.cond(blocked.size < 2, (),
        MinorActionUnavailable(blocked.mkString("; ")))
    yield moveTree(ready, actor)

  val rebuildMove: Build = (_, ready, actor, _) =>
    Right(moveTree(ready, actor))

  /** Every relic at the pawn's site, peeked at. */
  private def peekTree(actor: PlayerId): Operation =
    Sequence(Vector(
      BuildOps((live, _) =>
        Right(sitedPawn(live, actor).toVector.flatMap { (siteId, site) =>
          site.relics.map(relic => Peek(actor, relic.id, Location.Site(siteId)))
        }), required = true),
      // After the peeks, so `previous` is the step that peeked; the relics
      // are read at the state before it, which is where the deleted rule
      // read them off the recorded `Peek` operations.
      Note(ProcedureNotes.id(ActionRef.PeekSiteRelics), peekedNote(actor))))

  /** The relics at the pawn's site before the peek. A site with none writes
    * no line, as the rule this replaced posted none without a recorded
    * `Peek`. */
  private def peekedNote(actor: PlayerId)(states: NoteStates)
      : Option[PowerNote] = states.previous.flatMap((before, _) =>
    sitedPawn(before, actor).filter(_._2.relics.nonEmpty).map:
      (siteId, site) => peeked.sourceless(
        NoteArg.Cards(site.relics.map(_.id: CardId)), NoteArg.Site(siteId)))

  /** The relic to reveal, then its reveal. */
  private def revealTree(ready: ReadyGame, actor: PlayerId): Operation =
    Sequence(Vector(
      Decide(relicDecisionId, actor, DecisionQuery.ChooseOne(
        facedownRelics(ready, actor).map(id =>
          DecisionOption.Relic(DecisionOptionRef.Relic(id))),
        heading = Some("Choose a relic to reveal")),
        presentation = DecisionPresentation.Panel),
      BuildOps((_, pending) => Right(pending.answered.collectFirst {
        case Answered(`relicDecisionId`, DecisionAnswer.ChooseOneAnswer(
            DecisionOptionRef.Relic(id)), _) =>
          Reveal(id, Location.PlayArea(actor))
      }.toVector), required = true)))

  /** The way, then how many, then the move. */
  private def moveTree(ready: ReadyGame, actor: PlayerId): Operation =
    Sequence(Vector(
      Decide(directionDecisionId, actor, DecisionQuery.ChooseOne(
        Vector(way(ready, actor, toSite, "To your site"),
          way(ready, actor, toBoard, "To your board")),
        heading = Some("Move warbands")),
        presentation = DecisionPresentation.Panel),
      Branch((live, pending) => chosenWay(pending.answered).toVector
        .flatMap(picked => most(live, actor, picked).toOption.toVector
          .map(max => Decide(amountDecisionId, actor,
            DecisionQuery.ChooseAmount(1, max, Some(
              if picked == toSite then "Move how many warbands to your site?"
              else "Move how many warbands to your board?"), "Move"))))),
      BuildOps((live, pending) => Right((for
        picked <- chosenWay(pending.answered)
        amount <- pending.answered.collectFirst {
          case Answered(`amountDecisionId`,
              DecisionAnswer.ChooseAmountAnswer(value), _) => value
        }
        operation <- move(live, actor, picked, amount)
      yield operation).toVector), required = true)))

  private def way(ready: ReadyGame, actor: PlayerId,
      ref: DecisionOptionRef.Button, label: String): DecisionOption =
    val option = DecisionOption.Button(ref, label)
    most(ready, actor, ref).fold(reason => DecisionOption.Unavailable(option,
      OptionReason.Written(reason)), _ => option)

  private def chosenWay(answered: Vector[Answered])
      : Option[DecisionOptionRef.Button] = answered.collectFirst {
    case Answered(`directionDecisionId`,
        DecisionAnswer.ChooseOneAnswer(picked), _)
        if picked == toSite || picked == toBoard => picked
  }.collect { case button: DecisionOptionRef.Button => button }

  /** The most warbands that may move `way`, or why none may. To the site,
    * the actor must rule it, hold warbands on the board and not be bound by
    * Vow of Wandering; to the board, one of their warbands must stay. */
  private def most(ready: ReadyGame, actor: PlayerId,
      way: DecisionOptionRef.Button): Either[String, Int] =
    val current = ready.game.current
    current.players.find(_.player == actor).zip(sitedPawn(ready, actor)) match
      case None => Left("Your pawn is not at a site")
      case Some((player, (_, site))) =>
        if way == toSite then
          if !SiteRule.ruledBy(site.forces, current.players, actor)
              .getOrElse(false) then Left("You do not rule your site")
          else if player.board.warbands == 0 then
            Left("No warbands on your board")
          else if VowOfWandering.holds(ready, actor) then
            Left(VowOfWandering.Reason)
          else Right(player.board.warbands)
        else site.forces match
          case SiteForces.Occupied(ForceKind.Exile(lineage), count)
              if lineage == player.lineage =>
            if count > 1 then Right(count - 1)
            else Left("One warband must stay at your site")
          case _ => Left("None of your warbands are at your site")

  private def move(ready: ReadyGame, actor: PlayerId,
      way: DecisionOptionRef.Button, amount: Int): Option[CoreOperation] =
    for
      player <- ready.game.current.players.find(_.player == actor)
      siteId <- player.pawnSite
    yield
      val board = PositionedLocation(Location.PlayArea(actor))
      val site = PositionedLocation(Location.Site(siteId))
      Move(Piece.Warbands(ForceKind.Exile(player.lineage), amount),
        if way == toSite then board else site,
        if way == toSite then site else board)

  private def facedownRelics(ready: ReadyGame, actor: PlayerId)
      : Vector[RelicId] =
    ready.game.current.players.find(_.player == actor).toVector
      .flatMap(_.relics.filter(_.orientation == Orientation.FaceDown)
        .map(_.id))

  /** The actor's pawn site and its state, when the pawn stands on one. */
  private def sitedPawn(ready: ReadyGame, actor: PlayerId)
      : Option[(SiteId, SiteState)] =
    ready.game.current.players.find(_.player == actor)
      .flatMap(_.pawnSite).flatMap(id =>
        ready.game.current.map.sites.get(id).map(id -> _))

  private def pawnSite(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, SiteState] =
    ready.game.current.players.find(_.player == actor)
      .flatMap(_.pawnSite).toRight(PawnSiteMissing(actor))
      .flatMap(_ => sitedPawn(ready, actor).map(_._2).toRight(
        MinorActionUnavailable("pawn site is not in play")))

  /** The legacy handler's gates: no start selection, and the first-game
    * rules. */
  private def started(ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef], action: ActionRef)
      : Either[OathViolation, Unit] =
    for
      _ <- Either.cond(args.isEmpty, (), InvalidEventOrder(
        s"walker procedure ${action.key} takes no start selection, got " +
          args.map(_.kind).mkString(", ")))
      _ <- Either.cond(supported(ready), (), MinorActionUnavailable(
        "minor actions are limited to fixed unaltered all-Exile " +
          "first-game rules"))
    yield ()

  private def supported(ready: ReadyGame): Boolean =
    ready.setup.foundationProfile ==
      FirstGameFoundationProfile.FixedUnaltered &&
      ready.game.campaign.lineages.values.forall(_.role == Role.Exile) &&
      ready.game.campaign.foundations.values.forall(foundation =>
        foundation.face == FoundationFace.Normal &&
          foundation.alterationSources.isEmpty)
