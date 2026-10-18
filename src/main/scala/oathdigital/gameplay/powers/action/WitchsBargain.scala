package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, PowerAnswers}
import oathdigital.model._

object WitchsBargainCard extends Denizen(DenizenId("77"), "Witch's Bargain", Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.witchs-bargain"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Give [secret] to a player whose pawn is at your " +
      "site to take [favor] [favor] from them, or give [favor] " +
      "[favor] for [secret], any number of times.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Witch's Bargain (card 77), ACTION: place 1 secret on this card, then
  * give a secret to a player whose pawn is at your site to take 2 favor
  * from them, or give 2 favor for a secret, any number of times.
  *
  * Two loops. The outer one asks for another player at the player's site;
  * the inner one asks what to do with them: "Give a secret, take 2 favor",
  * "Give 2 favor, take a secret", "Pick a different player" and "Stop". A
  * give must be paid in full, so an exchange is offered only while the
  * player can pay it, and "Pick a different player" only while a second
  * player is at the site. "Stop" ends both loops. "Pick a different player"
  * ends the inner one, and the outer one asks again. Nobody consents.
  *
  * The take is best effort: up to 2 favor, or one secret when they hold any.
  * A secret moves faceup first on either side ([[SecretMoves]]). Each
  * exchange is one required batch and writes its own line. With nobody at
  * the site, or no exchange made, one line says so.
  *
  * A loop's guard runs at its pass boundaries, after the pass's last
  * answer, so both guards read the latest answer to either question
  * (`PowerAnswers.last`).
  */
case object WitchsBargain extends PaidAction(WitchsBargainCard.power):
  val playerDecisionId: String = "power.witchs-bargain.player"
  val exchangeDecisionId: String = "power.witchs-bargain.exchange"
  /** The favor each exchange gives or takes. */
  val Favor: Int = 2
  val giveSecret: DecisionOptionRef.Button =
    DecisionOptionRef.Button("give-secret")
  val giveFavor: DecisionOptionRef.Button = DecisionOptionRef.Button("give-favor")
  val switch: DecisionOptionRef.Button = DecisionOptionRef.Button("switch")
  val stop: DecisionOptionRef.Button = DecisionOptionRef.Button("stop")
  private val notes = NoteSet(id)
  /** "Gave {1 secret} to {Blue} and took {n favor}." */
  val forSecret: NoteKey = exchangeKey("secret")
  /** "Gave {2 favor} to {Blue} and took {n secrets}." */
  val forFavor: NoteKey = exchangeKey("favor")
  /** "No exchange was made." */
  val nothing: NoteKey = notes.key(NoteKey.Used, Vector(
    NotePart.Text("No exchange was made.")))
  override def noteKeys: Vector[NoteKey] = notes.declared
  override def narratedDecisions: Set[String] =
    Set(playerDecisionId, exchangeDecisionId)

  private def exchangeKey(name: String): NoteKey = notes.key(name, Vector(
    NotePart.Text("Gave "), NotePart.Arg(0), NotePart.Text(" to "),
    NotePart.Arg(1), NotePart.Text(" and took "), NotePart.Arg(2),
    NotePart.Text(".")))

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Repeat((_, pending) => !latest(pending.answered).contains(stop),
      Sequence(Vector[Operation](
        Branch((live, _) => askPlayer(live, player)),
        Repeat((_, pending) => latest(pending.answered).exists(exchanging),
          Sequence(Vector[Operation](
            Branch((live, pending) => askExchange(live, player, pending)),
            BuildOps((live, pending) => exchange(live, player, pending),
              required = true),
            Note(id, exchangeNote(_, player, source)))))))),
    Note(id, nothingNote(_, source)))))

  /** The latest answer to either question. */
  private def latest(answered: Vector[Answered]): Option[DecisionOptionRef] =
    PowerAnswers.last(answered, playerDecisionId, exchangeDecisionId)

  /** Whether the inner loop goes on after `ref`: a player was just chosen,
    * or an exchange was just made. */
  private def exchanging(ref: DecisionOptionRef): Boolean = ref match
    case _: DecisionOptionRef.Player => true
    case other => other == giveSecret || other == giveFavor

  /** The player the latest player answer chose. */
  private def partner(answered: Vector[Answered]): Option[PlayerId] =
    PowerAnswers.last(answered, playerDecisionId).collect {
      case DecisionOptionRef.Player(chosen) => chosen }

  /** The other players whose pawn is at the player's site, in seat order. */
  private def others(ready: ReadyGame, actor: PlayerId): Vector[PlayerId] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.collect {
        case other if other.player != actor && other.pawnSite.contains(site) =>
          other.player })

  private def askPlayer(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    others(ready, actor) match
      case Vector() => Vector.empty
      case found => Vector(Decide(playerDecisionId, actor,
        DecisionQuery.ChooseOne(found.map(other =>
          DecisionOption.Player(DecisionOptionRef.Player(other))),
          heading = Some("Witch's Bargain: choose a player at your site to " +
            "bargain with"))))

  /** The exchanges the player can pay in full, a second player when one is
    * at the site, and Stop. Asked once a player is chosen. */
  private def askExchange(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    PlayerFacts.player(ready, actor).toOption
      .filter(_ => partner(pending.answered).nonEmpty).toVector.map { held =>
        val board = held.board
        Decide(exchangeDecisionId, actor, DecisionQuery.ChooseOne(
          Option.when(board.faceUpSecrets + board.faceDownSecrets > 0)(
            DecisionOption.Button(giveSecret, "Give a secret, take 2 favor"))
            .toVector ++
          Option.when(board.favor >= Favor)(
            DecisionOption.Button(giveFavor, "Give 2 favor, take a secret")) ++
          Option.when(others(ready, actor).size > 1)(
            DecisionOption.Button(switch, "Pick a different player")) :+
          DecisionOption.Button(stop, "Stop"),
          heading = Some("Witch's Bargain: make an exchange, or stop")))
      }

  /** The exchange the latest answer chose, with the chosen player. Nothing
    * after "Pick a different player" or "Stop". */
  private def exchange(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    latest(pending.answered) match
      case Some(choice) if choice == giveSecret || choice == giveFavor =>
        for
          other <- partner(pending.answered)
            .toRight(PowerAnswers.missing(playerDecisionId))
          mine <- PlayerFacts.player(ready, actor)
          theirs <- PlayerFacts.player(ready, other)
        yield
          if choice == giveSecret then secretForFavor(mine, theirs)
          else favorForSecret(mine, theirs)
      case _ => Right(Vector.empty)

  /** One secret given, faceup first, for up to 2 favor. */
  private def secretForFavor(mine: PlayerState, theirs: PlayerState)
      : Vector[CoreOperation] =
    SecretMoves.faceupFirst(mine, Give(Piece.Secrets(1), mine.player,
      Location.PlayArea(mine.player), Location.PlayArea(theirs.player))) ++
      Option.when(theirs.board.favor > 0)(Take(
        Piece.Favor(math.min(Favor, theirs.board.favor)), mine.player,
        Location.PlayArea(theirs.player), Location.PlayArea(mine.player)))

  /** 2 favor given for one secret, faceup first, when they hold any. */
  private def favorForSecret(mine: PlayerState, theirs: PlayerState)
      : Vector[CoreOperation] =
    val held = theirs.board.faceUpSecrets + theirs.board.faceDownSecrets
    Vector[CoreOperation](Give(Piece.Favor(Favor), mine.player,
      Location.PlayArea(mine.player), Location.PlayArea(theirs.player))) ++
      (if held == 0 then Vector.empty
       else SecretMoves.faceupFirst(theirs, Take(Piece.Secrets(1),
         mine.player, Location.PlayArea(theirs.player),
         Location.PlayArea(mine.player))))

  /** The exchange the step before restates. Nothing after "Pick a
    * different player" or "Stop". */
  private def exchangeNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[LogNote] = for
    card <- PowerSourceRef.of(source)
    choice <- latest(states.answered)
    other <- partner(states.answered)
    step <- states.previous
    note <- restated(card, choice, other, step, actor)
  yield note

  /** What was given, to whom, and what the player took in `step`. */
  private def restated(card: PowerSourceRef, choice: DecisionOptionRef,
      other: PlayerId, step: NoteSupport.Step, actor: PlayerId)
      : Option[LogNote] =
    if choice == giveSecret then Some(forSecret(card,
      NoteArg.Amount(1, NoteUnit.Secret), NoteArg.Player(other),
      NoteArg.Amount(NoteSupport.favor(step, actor), NoteUnit.Favor)))
    else if choice == giveFavor then Some(forFavor(card,
      NoteArg.Amount(Favor, NoteUnit.Favor), NoteArg.Player(other),
      NoteArg.Amount(NoteSupport.secrets(step, actor), NoteUnit.Secret)))
    else None

  /** "No exchange was made." when no exchange was. */
  private def nothingNote(states: NoteStates, source: DecisionOptionRef)
      : Option[LogNote] = PowerSourceRef.of(source).filterNot(_ =>
    states.answered.exists {
      case Answered(`exchangeDecisionId`, DecisionAnswer.ChooseOneAnswer(ref),
          _) => ref == giveSecret || ref == giveFavor
      case _ => false
    }).map(nothing(_))
