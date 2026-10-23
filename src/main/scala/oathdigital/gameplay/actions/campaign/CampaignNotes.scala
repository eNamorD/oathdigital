package oathdigital.gameplay.actions.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.{BannerRules, ProcedureNotes}
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.gameplay.powers.campaign.AttackScore
import oathdigital.model._

/** Every Game Log line a Campaign writes about itself, and what each one
  * reads (procedures journal their own notes design, "Campaign"). The
  * Campaign's own files place the leaves; the words and the readings are
  * all here.
  *
  * **Every count is read from the step the note restates, never computed
  * again from the board.** Four powers rewrite the Campaign's own loss
  * operations before they run — Billowing Fog, Traveling Doctor and Peace
  * Envoy through `SparedForce.spare`, and Keep replaces the Raid's
  * consequences outright — so the Campaign's own arithmetic would state
  * numbers that did not happen. `NoteStates.previous` holds the before and
  * after of the leaf immediately above the note, which is what did.
  *
  * The loss sentence the rule joined from up to five clauses is three
  * sentences here, because the clauses are applied in three different
  * nodes and one note cannot span them. The only subject the rule omitted
  * — the loser is the Campaign's own actor — carries exactly one clause,
  * so it is a flat key.
  */
object CampaignNotes:
  private val notes = NoteSet(ProcedureNotes.id(ActionRef.Campaign))
  def noteKeys: Vector[NoteKey] = notes.declared

  /** A note leaf of the Campaign's own id, for the files that place one. */
  def leaf(build: NoteStates => Option[LogNote]): Note =
    Note(ProcedureNotes.id(ActionRef.Campaign), build)

  /** "Targets: Broken Peaks and the Mine", or a Raid's pawn, relics and
    * banners. */
  val targets: NoteKey = notes.key("targets", Vector(
    NotePart.Text("Targets: "), NotePart.Arg(0)))
  /** "Attack Pool: 3, Defense Pool: 2". */
  val pools: NoteKey = notes.key("pools", Vector(
    NotePart.Text("Attack Pool: "), NotePart.Arg(0),
    NotePart.Text(", Defense Pool: "), NotePart.Arg(1)))
  /** "Activated Careful Plans": the Campaign's own actor. */
  val activatedPlan: NoteKey = notes.key("activated-plan", Vector(
    NotePart.Text("Activated "), NotePart.Arg(0)))
  /** "{Blue} activated Careful Plans": a defender, or an off-turn user. */
  val playerActivatedPlan: NoteKey = notes.key("player-activated-plan",
    Vector(NotePart.Arg(0), NotePart.Text(" activated "), NotePart.Arg(1)))
  val banditsActivatedPlan: NoteKey = notes.key("bandits-activated-plan",
    Vector(NotePart.Text("The bandits activated "), NotePart.Arg(0)))
  /** "{Blue} activated the Oathkeeper title": the title's defense plan,
    * which no card prints. The rule read the label its offer authored,
    * which a typed argument cannot carry (wording ledger, slice 4). */
  val activatedOathkeeperTitle: NoteKey = notes.key(
    "activated-oathkeeper-title", Vector(NotePart.Arg(0),
      NotePart.Text(" activated the Oathkeeper title")))
  val activatedUsurperTitle: NoteKey = notes.key("activated-usurper-title",
    Vector(NotePart.Arg(0),
      NotePart.Text(" activated the Usurper title")))
  /** "Sacrificed 3 warbands for an attack of 6". */
  val sacrificed: NoteKey = notes.key("sacrificed", Vector(
    NotePart.Text("Sacrificed "), NotePart.Arg(0), NotePart.Text(" "),
    NotePart.Plural(0, "warband", "warbands"),
    NotePart.Text(" for an attack of "), NotePart.Arg(1)))
  /** "Attack: 5", when the skulls killed nothing. */
  val attack: NoteKey = notes.key("attack", Vector(
    NotePart.Text("Attack: "), NotePart.Arg(0)))
  /** "Attack: 5 with 1 skull", when each skull killed one warband. */
  val attackWithSkulls: NoteKey = notes.key("attack-with-skulls", Vector(
    NotePart.Text("Attack: "), NotePart.Arg(0), NotePart.Text(" with "),
    NotePart.Arg(1), NotePart.Text(" "),
    NotePart.Plural(1, "skull", "skulls")))
  /** "Attack: 6 with 2 skulls, killing 4 warbands", when the skulls killed
    * another number than they were (Weeping Banner, or the force cap). */
  val attackWithSkullKills: NoteKey = notes.key("attack-with-skull-kills",
    Vector(NotePart.Text("Attack: "), NotePart.Arg(0),
      NotePart.Text(" with "), NotePart.Arg(1), NotePart.Text(" "),
      NotePart.Plural(1, "skull", "skulls"), NotePart.Text(", killing "),
      NotePart.Arg(2), NotePart.Text(" "),
      NotePart.Plural(2, "warband", "warbands")))
  /** "Defense: 3". */
  val defense: NoteKey = notes.key("defense", Vector(
    NotePart.Text("Defense: "), NotePart.Arg(0)))
  /** "{Blue} wins!" */
  val playerWins: NoteKey = notes.key("player-wins", Vector(
    NotePart.Arg(0), NotePart.Text(" wins!")))
  val banditsWin: NoteKey = notes.key("bandits-win", Vector(
    NotePart.Text("The bandits win!")))
  /** "Took Wine of Welcome and Darkest Secret from {Blue}". */
  val took: NoteKey = notes.key("took", Vector(NotePart.Text("Took "),
    NotePart.Arg(0), NotePart.Text(" from "), NotePart.Arg(1)))
  /** "Placed 2 warbands on the Mine and 1 warband on Standing Stones". */
  val placed: NoteKey = notes.key("placed", Vector(
    NotePart.Text("Placed "), NotePart.Arg(0)))
  /** "Lost 2 warbands": the loser is the Campaign's own actor. */
  val lost: NoteKey = notes.key("lost", Vector(NotePart.Text("Lost "),
    NotePart.Arg(0), NotePart.Text(" "),
    NotePart.Plural(0, "warband", "warbands")))
  /** "{Blue} lost 2 warbands". */
  val playerLost: NoteKey = notes.key("player-lost", Vector(
    NotePart.Arg(0), NotePart.Text(" lost "), NotePart.Arg(1),
    NotePart.Text(" "), NotePart.Plural(1, "warband", "warbands")))
  val banditsLost: NoteKey = notes.key("bandits-lost", Vector(
    NotePart.Text("The bandits lost "), NotePart.Arg(0),
    NotePart.Text(" "), NotePart.Plural(0, "warband", "warbands")))
  /** "{Blue} burned 1 favor, discarded Tinker and set aside a Relic": what
    * a defeated Raid defender loses beyond their warbands, in the order
    * the rule's joined sentence named them. */
  val defenderLost: NoteKey = notes.key("defender-lost", Vector(
    NotePart.Arg(0), NotePart.Text(" "), NotePart.Arg(1)))
  /** "{Blue} was sent to the Mine". */
  val sentTo: NoteKey = notes.key("sent-to", Vector(NotePart.Arg(0),
    NotePart.Text(" was sent to "), NotePart.Arg(1)))
  val skipped: NoteKey = notes.key("skipped", Vector(
    NotePart.Text("Skipped the Campaign.")))
  val nothingToAttack: NoteKey = notes.key("nothing-to-attack", Vector(
    NotePart.Text("Nothing left to attack: the Campaign ended.")))

  /** The Campaign's setup, from the answers so far. */
  private def setupOf(states: NoteStates, actor: PlayerId)
      : Option[CampaignSetup] =
    CampaignSetup.setup(states.now, actor,
      PendingTree(Vector.empty, states.answered))

  /** What the Campaign targets, each as the Game Log names it. */
  private def targeted(setup: CampaignSetup): Vector[NoteThing] =
    setup.kind match
      case CampaignKind.Conquest =>
        setup.targetSites.map(NoteThing.Site(_))
      case CampaignKind.Raid => setup.raidTargets.map {
        case CampaignRaidTarget.Pawn(player) => NoteThing.Pawn(player)
        case CampaignRaidTarget.Relic(_, relic) => NoteThing.Card(relic)
        case CampaignRaidTarget.Banner(_, banner) =>
          NoteThing.Banner(banner)
      }

  def targetsNote(actor: PlayerId)(states: NoteStates): Option[LogNote] =
    setupOf(states, actor).map(setup =>
      targets.sourceless(NoteArg.Things(targeted(setup))))

  def poolsNote(catalog: ExecutableCatalog, actor: PlayerId)(
      states: NoteStates): Option[LogNote] =
    setupOf(states, actor).map(setup => pools.sourceless(
      NoteArg.Number(setup.force),
      NoteArg.Number(CampaignBattle.printedDefense(catalog, setup))))

  /** A plan a player chose, named by its card, or by the title it is
    * printed on no card for. `user` is who chose it. */
  def activationNote(source: CampaignPlanSource, user: PlayerId,
      actor: PlayerId)(states: NoteStates): Option[LogNote] =
    CampaignPlans.cardOf(source) match
      case Some(card) => Some(
        if user == actor then activatedPlan.sourceless(NoteArg.Card(card))
        else playerActivatedPlan.sourceless(NoteArg.Player(user),
          NoteArg.Card(card)))
      case None => Some(states.now.game.current.title.side match
        case TitleSide.Oathkeeper =>
          activatedOathkeeperTitle.sourceless(NoteArg.Player(user))
        case TitleSide.Usurper =>
          activatedUsurperTitle.sourceless(NoteArg.Player(user)))

  /** A plan a bandit defender applied, which is always a card's: the title
    * is a player defender's. */
  def banditActivationNote(source: CampaignPlanSource)(
      states: NoteStates): Option[LogNote] =
    CampaignPlans.cardOf(source).map(card =>
      banditsActivatedPlan.sourceless(NoteArg.Card(card)))

  /** The attack each sacrificed warband bought, as the new total: the
    * sacrifice is added to the recorded attack when the result is built,
    * so the pool still holds the attack before it. */
  def sacrificedNote(states: NoteStates): Option[LogNote] =
    val count = CampaignAnswers.sacrificed(
      PendingTree(Vector.empty, states.answered))
    Option.when(count > 0)(sacrificed.sourceless(NoteArg.Number(count),
      NoteArg.Number(scoreOf(states.now, CampaignIds.attackPool) +
        count * CampaignBattle.sacrificeWorth(states.now))))

  /** The attack as it stands once every plan has scored it, with the skull
    * faces counted as the scoring counts them and, when the skulls killed
    * another number of warbands, that number (batch 8, item 13b). */
  def attackNote(actor: PlayerId)(states: NoteStates): Option[LogNote] =
    states.now.game.current.rollOutcomes.get(CampaignIds.attackPool).map {
      outcome =>
        val kills = outcome.skulls
        val faces = AttackScore.skullFaces(
          CampaignBattle.attackFacesOf(states.now),
          AttackScore.rules(states.now,
            PendingTree(Vector.empty, states.answered), actor))
        if kills == 0 then attack.sourceless(NoteArg.Number(outcome.score))
        else if kills == faces then attackWithSkulls.sourceless(
          NoteArg.Number(outcome.score), NoteArg.Number(faces))
        else attackWithSkullKills.sourceless(
          NoteArg.Number(outcome.score), NoteArg.Number(faces),
          NoteArg.Number(kills))
    }

  /** The defense, which is written even for a pool that never rolled: it
    * is the dice plus the defender's force. */
  def defenseNote(states: NoteStates): Option[LogNote] = Some(
    defense.sourceless(NoteArg.Number(
      scoreOf(states.now, CampaignIds.defensePool))))

  def winnerNote(states: NoteStates): Option[LogNote] =
    resultOf(states).map(result =>
      if result.attackerWins then
        playerWins.sourceless(NoteArg.Player(result.attacker))
      else result.defender match
        case CampaignDefender.Player(player) =>
          playerWins.sourceless(NoteArg.Player(player))
        case CampaignDefender.Bandits => banditsWin.sourceless())

  /** What the battle's losses killed, read as the warbands the loser holds
    * on their board and across the map before and after the leaf. A
    * Conquest defender's returned half comes back into that total, so the
    * count is the net loss the rule's joined clause named. */
  def lossNote(states: NoteStates): Option[LogNote] = for
    result <- resultOf(states)
    step <- states.previous
    owner = loserOf(result)
    kind <- forceKindOf(step._1, owner)
    count = forceHeld(step._1, owner, kind) - forceHeld(step._2, owner, kind)
    if count > 0
  yield owner match
    case Some(player) if player == result.attacker =>
      lost.sourceless(NoteArg.Number(count))
    case Some(player) => playerLost.sourceless(NoteArg.Player(player),
      NoteArg.Number(count))
    case None => banditsLost.sourceless(NoteArg.Number(count))

  /** What a won Raid took, read as what the attacker gained in the step:
    * the relics first, then the banners, as the takes ran. A take a power
    * refused is not named, because nothing was gained. */
  def spoilsNote(states: NoteStates): Option[LogNote] = for
    result <- resultOf(states)
    defender <- defenderOf(result)
    step <- states.previous
    taken = NoteSupport.relicsGained(step, result.attacker)
      .map(relic => NoteThing.Card(relic): NoteThing) ++
      bannersGained(step, result.attacker).map(NoteThing.Banner(_))
    if taken.nonEmpty
  yield took.sourceless(NoteArg.Things(taken), NoteArg.Player(defender))

  /** Where a Conquest victory's survivors went, read per target site. */
  def placementNote(states: NoteStates): Option[LogNote] = for
    result <- resultOf(states)
    step <- states.previous
    kind <- forceKindOf(step._1, Some(result.attacker))
    entries = result.targetSites.flatMap { site =>
      val added = atSite(step._2, site, kind) - atSite(step._1, site, kind)
      Option.when(added > 0)(added -> site)
    }
    if entries.nonEmpty
  yield placed.sourceless(NoteArg.Placements(entries))

  /** What a defeated Raid defender lost beyond their warbands, read from
    * the consequences step: the favor burnt, the cards that left their
    * play area for a discard pile, and the relics set aside. A facedown
    * Conspiracy goes to the box, not a pile, so no clause names it. */
  def consequenceNote(states: NoteStates): Option[LogNote] = for
    result <- resultOf(states)
    defender <- defenderOf(result)
    step <- states.previous
    clauses = consequences(step, defender)
    if clauses.nonEmpty
  yield defenderLost.sourceless(NoteArg.Player(defender),
    NoteArg.Clauses(clauses))

  private def consequences(step: NoteSupport.Step, defender: PlayerId)
      : Vector[NoteClause] =
    val burned = -NoteSupport.favor(step, defender)
    val left = held(step._1, defender).filterNot(
      held(step._2, defender).contains)
    val discarded = left.collect { case card: WorldCardId => card }
      .filter(card => Region.all.exists(region =>
        step._2.game.current.commonCards.discard(region).contains(card)))
    val aside = step._2.game.current.setAsideRelics.filterNot(
      step._1.game.current.setAsideRelics.contains)
    Vector(
      Option.when(burned > 0)(NoteClause.BurnedFavor(burned)),
      Option.when(discarded.nonEmpty)(NoteClause.Discarded(
        discarded.map(card => card: CardId))),
      Option.when(aside.nonEmpty)(NoteClause.SetAside(
        aside.map(relic => relic: CardId)))).flatten

  /** Where a defeated Raid defender's pawn was sent, read from the move
    * itself: a power that ends the Raid instead (Keep) replaces this
    * step's operations and its own line says where the pawn stands. */
  def relocationNote(states: NoteStates): Option[LogNote] = for
    result <- resultOf(states)
    defender <- defenderOf(result)
    step <- states.previous
    to <- step._2.game.current.pawnSite(defender)
    if !step._1.game.current.pawnSite(defender).contains(to)
  yield sentTo.sourceless(NoteArg.Player(defender), NoteArg.Site(to))

  /** Why a Campaign ended before its force was committed. */
  def endedNote(states: NoteStates): Option[LogNote] = Some(
    if CampaignAnswers.skipped(PendingTree(Vector.empty, states.answered))
    then skipped.sourceless() else nothingToAttack.sourceless())

  private def resultOf(states: NoteStates): Option[CampaignResult] =
    states.now.game.current.lastCampaignResult

  private def defenderOf(result: CampaignResult): Option[PlayerId] =
    Some(result.defender).collect {
      case CampaignDefender.Player(player) => player }

  /** Who lost the battle: the defender when the attacker won, else the
    * attacker. `None` is the bandits. */
  private def loserOf(result: CampaignResult): Option[PlayerId] =
    if result.attackerWins then defenderOf(result)
    else Some(result.attacker)

  private def forceKindOf(ready: ReadyGame, owner: Option[PlayerId])
      : Option[ForceKind] = owner match
    case None => Some(ForceKind.Bandit)
    case Some(player) => ready.game.current.players
      .find(_.player == player).map(state =>
        PlayerForceKind.of(ready, state).getOrElse(
          ForceKind.Exile(state.lineage)))

  /** Every warband of `kind` the owner holds: their board, and every site.
    * The warband bank is not counted, so a warband returned from the
    * supply comes back out of the loss. The sites are read through an
    * iterator: mapping `sites.keys`, which is a `Set`, would collapse two
    * sites holding the same number into one. */
  private def forceHeld(ready: ReadyGame, owner: Option[PlayerId],
      kind: ForceKind): Int =
    val onBoard = owner.flatMap(player => ready.game.current.players
      .find(_.player == player)).fold(0)(_.board.warbands)
    onBoard + ready.game.current.map.sites.valuesIterator.map(_.forces)
      .collect { case SiteForces.Occupied(`kind`, count) => count }.sum

  private def atSite(ready: ReadyGame, site: SiteId, kind: ForceKind): Int =
    ready.game.current.map.sites.get(site).map(_.forces).collect {
      case SiteForces.Occupied(`kind`, count) => count
    }.getOrElse(0)

  private def scoreOf(ready: ReadyGame, pool: PoolKey): Int =
    ready.game.current.rollOutcomes.get(pool).fold(0)(_.score)

  private def held(ready: ReadyGame, player: PlayerId): Vector[CardId] =
    ready.game.current.players.find(_.player == player).toVector.flatMap(
      state => state.advisers.map(_.id: CardId) ++
        state.relics.map(_.id: CardId))


  private def bannersGained(step: NoteSupport.Step, player: PlayerId)
      : Vector[Banner] = Banner.all.filter(banner =>
    BannerRules.holder(step._2.game.current, banner).contains(player) &&
      !BannerRules.holder(step._1.game.current, banner).contains(player))
