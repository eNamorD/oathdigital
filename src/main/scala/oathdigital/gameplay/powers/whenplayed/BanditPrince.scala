package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{PlayerFacts, PowerAnswers}
import oathdigital.gameplay.powers.economy.VowOfWandering
import oathdigital.model._

object BanditPrinceCard extends Denizen(DenizenId("226"), "Bandit Prince", Suit.Discord) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.bandit-prince"),
    persistent = false, cost = Cost.free,
    text = "**WHEN PLAYED,** you may replace all bandits at any sites " +
      "you choose with your warbands.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Bandit Prince (card 226, adviser-only, locked), WHEN PLAYED: you may
  * replace all bandits at any sites you choose with your warbands.
  *
  * The bandit-ruled sites are read live and offered in map order. The actor
  * chooses any number of them, or none. At each chosen site a `Replace`
  * swaps all its bandits for the actor's warbands from their bank, in one
  * step. An answer whose sites hold more bandits than the bank holds is
  * refused, so a site is never half replaced. A site whose bandits alone
  * exceed the bank is shown unavailable with what it needs, and a tally of
  * the warbands needed, limited by the bank, guides the choice; the answer's
  * sum check stays. With no site left that fits, the question is not asked.
  * A holder of Vow of Wandering is not asked either, and the Vow's line says
  * why. One line per site tells the replacement, in answer order, and
  * covers that site's Moved line.
  */
case object BanditPrince extends WhenPlayedPower:
  val cardId: DenizenId = BanditPrinceCard.id
  val id: PowerId = BanditPrinceCard.power.id
  val decisionId: String = "cardplay.bandit-prince.sites"
  private val notes = NoteSet(id)
  /** "Replaced {n} bandit at {site} with {Red}'s warbands." */
  val replaced: NoteKey = notes.key("replaced", Vector(
    NotePart.Text("Replaced "), NotePart.Arg(0),
    NotePart.Plural(0, " bandit at ", " bandits at "), NotePart.Arg(1),
    NotePart.Text(" with "), NotePart.Arg(2), NotePart.Text("'s warbands.")))
  /** "No site was ruled by bandits." */
  val none: NoteKey = notes.key("none", Vector(
    NotePart.Text("No site was ruled by bandits.")))
  notes.borrow(VowOfWandering.forbidden)

  /** The sites in play that bandits hold, in map order. */
  private def banditSites(ready: ReadyGame): Vector[SiteId] =
    ready.game.current.map.inPlay.filter(bandits(ready, _) > 0)

  private def bandits(ready: ReadyGame, site: SiteId): Int =
    held(ready, site, ForceKind.Bandit)

  private def held(ready: ReadyGame, site: SiteId, kind: ForceKind): Int =
    ready.game.current.map.sites.get(site).map(_.forces) match
      case Some(SiteForces.Occupied(`kind`, count)) => count
      case _ => 0

  /** The warbands in the actor's bank: what the replacement can draw on. */
  private def bank(ready: ReadyGame, actor: PlayerId): Int =
    PlayerFacts.forceKind(ready, actor).toOption
      .fold(0)(PlayerFacts.banked(ready, _))

  /** The sites answered, in answer order. A question the search did not ask
    * reads as none. */
  private def chosen(pending: PendingTree): Vector[SiteId] =
    PowerAnswers.many(pending, decisionId).getOrElse(Vector.empty).collect {
      case DecisionOptionRef.Site(site) => site }

  /** A `Replace` of all the bandits at each chosen site, refused when a
    * site is not a bandit site or the bank cannot cover them all. */
  private def replace(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    val sites = chosen(pending)
    val offered = banditSites(ready)
    val counts = sites.map(site => site -> bandits(ready, site))
    val total = counts.map(_._2).sum
    for
      _ <- sites.find(!offered.contains(_)).toLeft(()).left.map(site =>
        OathViolation.InvalidEventOrder(
          s"${site.value} is not a site the bandits rule"))
      kind <- PlayerFacts.forceKind(ready, actor)
      banked = PlayerFacts.banked(ready, kind)
      _ <- Either.cond(total <= banked, (), OathViolation.InvalidEventOrder(
        s"the chosen sites hold $total bandits, more than the $banked " +
          "warbands in the bank"))
    yield counts.map { case (site, count) => Replace(
      Piece.Warbands(ForceKind.Bandit, count), Piece.Warbands(kind, count),
      PositionedLocation(Location.Site(site))) }

  override def noteKeys: Vector[NoteKey] = notes.declared
  override def narratedDecisions: Set[String] = Set(decisionId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    Vector(Branch((live, _) =>
      val sites = banditSites(live)
      val banked = bank(live, ctx.actor)
      val fits = sites.filter(bandits(live, _) <= banked)
      def ref(site: SiteId) = DecisionOptionRef.Site(site)
      if sites.isEmpty then Vector(Note(id, _ =>
        Some(none(PowerSourceRef.Card(cardId)))))
      else if VowOfWandering.holds(live, ctx.actor) then Vector(Note(id, _ =>
        Some(VowOfWandering.forbidden(PowerSourceRef.Card(cardId)))))
      else if fits.isEmpty then Vector.empty
      else Vector(
        Decide(decisionId, ctx.actor, DecisionQuery.ChooseMany(0, fits.size,
          sites.map(site =>
            if fits.contains(site) then DecisionOption.Site(ref(site))
            else DecisionOption.Unavailable(DecisionOption.Site(ref(site)),
              OptionReason.Written(needs(bandits(live, site), banked)))),
          heading = Some("Bandit Prince: choose the sites whose bandits " +
            "your warbands replace"),
          tallies = Vector(DecisionTally("Warbands needed", "warbands",
            fits.map(site => ref(site) -> bandits(live, site)).toMap,
            Some(banked))),
          summary = Some(DecisionSummary("Replace bandits at", "site",
            "sites")),
          confirm = Some(CountedConfirm("Replace bandits at", "site", "sites",
            Some("Replace none"))))),
        BuildOps((ready, pending) => replace(ready, ctx.actor, pending)),
        Branch((_, pending) => chosen(pending).map(site =>
          Note(id, replacedNote(_, ctx.actor, site), covers = true))))))

  /** Why a site's bandits alone are more than the bank holds. */
  private def needs(count: Int, banked: Int): String =
    s"Needs $count ${if count == 1 then "warband" else "warbands"}; " +
      s"your bank holds $banked"

  /** The actor's warbands the replacement put at `site`. */
  private def replacedNote(states: NoteStates, actor: PlayerId,
      site: SiteId): Option[LogNote] = for
    (before, after) <- states.previous
    kind <- PlayerFacts.forceKind(after, actor).toOption
    count = held(after, site, kind)
    if count > 0 && bandits(before, site) > 0
  yield replaced(PowerSourceRef.Card(cardId), NoteArg.Number(count),
    NoteArg.Site(site), NoteArg.Player(actor))
