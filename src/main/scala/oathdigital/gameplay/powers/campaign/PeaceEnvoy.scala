package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.NoteSupport
import oathdigital.gameplay.actions.campaign.{CampaignAnswers, CampaignIds,
  CampaignSetup}
import oathdigital.gameplay.powerresolver.{OptionRestriction, PowerCtx}
import oathdigital.model._

object PeaceEnvoyCard extends Denizen(DenizenId("125"), "Peace Envoy", Suit.Order):
  val power = PrintedPower(PowerId("denizen.peace-envoy"),
    persistent = false, cost = Cost(favor = 1),
    text = "You **cannot** use other battle plans. If your enemy's pawn " +
      "is at your site, give them one [favor] per [defense-die] in " +
      "the pool. You're victorious now. **Ignore** killing " +
      "warbands.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Peace Envoy (card 125), a battle plan for either side: "[favor] You
  * cannot use other battle plans. If your enemy's pawn is at your site,
  * give them one [favor] per [defense-die] in the pool. You're victorious
  * now. Ignore killing warbands."
  *
  * One favor is placed onto the card. The gift is part of the plan's
  * application: one favor per die the defense pool holds then goes from the
  * user's board to the enemy's. The plan window's dry run therefore prices
  * it, and a user who cannot give it all sees the plan unavailable with the
  * shortfall. The shortfall counts the favor already placed.
  *
  * It is withheld, with "Your enemy's pawn must be at your site", unless
  * both pawns stand at one site. Bandits have no pawn, so it is never used
  * by or against them. It is used with no other plan (`SolePlan`). When the
  * attacker chose it, every defender plan is withheld with "Peace Envoy:
  * The battle is decided.", so the defender is not asked.
  *
  * Once used, it decides the battle (`VictoriousNow`): nothing is rolled
  * or sacrificed, and its user's side wins, with "Won the battle without
  * a roll.", or, for a defender, "{Blue} won the battle without a roll.".
  * The losses kill no warband. After a Conquest the attacker wins with it,
  * the defender's warbands at the targets all return to their board.
  *
  * The gift writes "Gave {2 favor} to {Blue}.", or, for a defender, "{Blue}
  * gave {2 favor} to {Red}.".
  */
case object PeaceEnvoy extends BattlePlan:
  val cardId: DenizenId = PeaceEnvoyCard.id
  val id: PowerId = PeaceEnvoyCard.power.id
  /** The favor placed onto the card. */
  val Favor: Int = 1
  /** Why the plan is shown but not offered. */
  val Apart: String = "Your enemy's pawn must be at your site"
  private val notes = NoteSet(id)
  /** "Cannot be used with other battle plans." */
  val notWithOthers: NoteKey = notes.key("not-with-others",
    Vector(NotePart.Text("Cannot be used with other battle plans.")))
  /** "No other battle plans." */
  val noOthers: NoteKey = notes.key("no-others",
    Vector(NotePart.Text("No other battle plans.")))
  /** "The battle is decided.", on the defender's plans. */
  val decided: NoteKey = notes.key("decided",
    Vector(NotePart.Text("The battle is decided.")))
  /** "Gave {2 favor} to {Blue}." */
  val gave: NoteKey = notes.key("gave", Vector(NotePart.Text("Gave "),
    NotePart.Arg(0), NotePart.Text(" to "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "{Blue} gave {2 favor} to {Red}.", for a defender. */
  val gaveOffTurn: NoteKey = notes.key("gave-off-turn", Vector(NotePart.Arg(0),
    NotePart.Text(" gave "), NotePart.Arg(1), NotePart.Text(" to "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "Won the battle without a roll." */
  val won: NoteKey = notes.key("won",
    Vector(NotePart.Text("Won the battle without a roll.")))
  /** "{Blue} won the battle without a roll.", for a defender. */
  val wonOffTurn: NoteKey = notes.key("won-off-turn", Vector(NotePart.Arg(0),
    NotePart.Text(" won the battle without a roll.")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = notes.declared

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map { source =>
      val offer = CampaignPlanOffer(source,
        "Peace Envoy: give one favor per defense die and win",
        Vector(CampaignPlanCost.Favor(PeaceEnvoy.Favor)), Vector.empty,
        alone = true)
      parties(context) match
        case Some((user, enemy)) => offer.copy(effects = Vector(
          CampaignPlanEffect.Run(Vector(
            gift(user, enemy, favor(context.ready, user)),
            giftNote(context.side, user, enemy),
            // Nothing is rolled and the losses kill nothing, so the
            // attacker's dead are none, at the origin too (slice 13).
            ModifyDicePool(CampaignIds.attackerSpared, 1)))))
        case None => offer.copy(withheld =
          Some(OptionReason.Written(PeaceEnvoy.Apart)))
    }

  override def restrictions: Map[PowerWindow, Vector[OptionRestriction]] =
    val sole = SolePlan.restrictions(cardRef, sides,
      PowerSourceRef.Card(cardId), PeaceEnvoy.notWithOthers,
      PeaceEnvoy.noOthers)
    val window = PowerWindow.CampaignDefenderBattlePlans
    sole.updated(window, sole.getOrElse(window, Vector.empty) :+ settled)

  override def wrapping: Map[PowerWindow, BattlePlan.Wrap] =
    VictoriousNow.wrapping(victory).updated(PowerWindow.CampaignLosses,
      (ctx: PowerCtx, use: PlanUse, losses: Vector[Operation]) =>
        spared(ctx, use, losses))

  /** The victory's line. A defender is named. */
  private def victory(use: PlanUse): Note = Note(id, _ => {
    val source = PowerSourceRef.Card(cardId)
    use.side match
      case CampaignPlanSide.Attacker => Some(PeaceEnvoy.won(source))
      case CampaignPlanSide.Defender => use.user.map(user =>
        PeaceEnvoy.wonOffTurn(source, NoteArg.Player(user)))
  })

  /** The losses, killing no warband. No kill from a board is left. After a
    * Conquest the attacker won, the warbands at the targets still leave
    * them, so the victor can place warbands, but all of them return to the
    * defender's board, not half. Read when the losses window is folded,
    * before the losses run. */
  private def spared(ctx: PowerCtx, use: PlanUse,
      losses: Vector[Operation]): Vector[Operation] =
    val atTargets = use.result.fold(0)(result => result.targetSites
      .flatMap(use.ready.game.current.map.sites.get).map(_.forces).collect {
        case SiteForces.Occupied(_, count) => count }.sum)
    LossRewrite(ctx, losses) {
      case Kill(Piece.Warbands(_, _),
          PositionedLocation(Location.PlayArea(_), _, _)) => Vector.empty
      case move @ Move(Piece.Warbands(kind, _),
          PositionedLocation(Location.WarbandBank(_), _, _),
          PositionedLocation(Location.PlayArea(_), _, _), None, _) =>
        Vector(move.copy(piece = Piece.Warbands(kind, atTargets)))
      case other => Vector(other)
    }

  /** Every defender plan, once the attacker chose this one. */
  private def settled: OptionRestriction = OptionRestriction(
    (ctx, _) => Option.when(attackerChose(ctx))(
      OathViolation.CampaignUnavailable("Peace Envoy decided the battle")),
    (ctx, _) => Option.when(attackerChose(ctx))(
      PeaceEnvoy.decided(PowerSourceRef.Card(cardId))))

  private def attackerChose(ctx: PowerCtx): Boolean = CampaignAnswers.picks(
    PendingTree(Vector.empty, ctx.answered), CampaignIds.attackerPlan)
    .contains(cardRef)

  /** The user and the enemy, when both are players whose pawns stand at
    * one site. */
  private def parties(context: PlanContext): Option[(PlayerId, PlayerId)] =
    val enemy = context.enemy match
      case CampaignDefender.Player(player) => Some(player)
      case CampaignDefender.Bandits => None
    for
      user <- context.user
      other <- enemy
      site <- standingSite(context.ready, user)
      if standingSite(context.ready, other).contains(site)
    yield (user, other)

  /** Where `player` stands for the Campaign: a queued origin for the
    * attacker, else the pawn site. */
  private def standingSite(ready: ReadyGame, player: PlayerId)
      : Option[SiteId] = CampaignSetup.originOf(ready, player)

  private def favor(ready: ReadyGame, player: PlayerId): Int =
    ready.game.current.players.find(_.player == player).fold(0)(_.board.favor)

  /** One favor per die in the defense pool, from `user` to `enemy`. It
    * runs after the payments, so its shortfall is reported against
    * `before`, the user's favor when the plan was offered: everything the
    * payments took (the favor placed onto the card, and any surcharge, as
    * Insect Swarm's) is added back. */
  private def gift(user: PlayerId, enemy: PlayerId, before: Int): Operation =
    BuildOps((ready, _) => {
      val dice = ready.game.current.rollPools.get(CampaignIds.defensePool)
        .fold(0)(_.count)
      val held = favor(ready, user)
      if dice == 0 then Right(Vector.empty)
      else if held < dice then Left(OathViolation.InsufficientFavor(
        before - held + dice, before))
      else Right(Vector[CoreOperation](Give(Piece.Favor(dice), user,
        Location.PlayArea(user), Location.PlayArea(enemy), required = true)))
    })

  /** The gift's line, from what the enemy gained. A defender is named. */
  private def giftNote(side: CampaignPlanSide, user: PlayerId,
      enemy: PlayerId): Note = Note(id, states => states.previous
    .map(NoteSupport.favor(_, enemy)).filter(_ > 0).map { count =>
      val source = PowerSourceRef.Card(cardId)
      val amount = NoteArg.Amount(count, NoteUnit.Favor)
      side match
        case CampaignPlanSide.Attacker =>
          PeaceEnvoy.gave(source, amount, NoteArg.Player(enemy))
        case CampaignPlanSide.Defender => PeaceEnvoy.gaveOffTurn(source,
          NoteArg.Player(user), amount, NoteArg.Player(enemy))
    })
