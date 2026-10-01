package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{PlayerFacts, PowerAnswers}
import oathdigital.model._

/** Bandit Prince (card 226, adviser-only, locked), WHEN PLAYED: you may
  * replace all bandits at any sites you choose with your warbands.
  *
  * The bandit-ruled sites are read live and offered in map order. The actor
  * chooses any number of them, or none. At each chosen site a `Replace`
  * swaps all its bandits for the actor's warbands from their bank, in one
  * step. An answer whose sites hold more bandits than the bank holds is
  * refused, so a site is never half replaced. The walker's search hides a
  * site whose bandits alone exceed the bank; with none left the question is
  * not asked. One line per site tells the replacement, in answer order, and
  * covers that site's Moved line.
  */
final case class BanditPrince private (cardId: DenizenId)
    extends WhenPlayedPower:
  import BanditPrince._
  def id: PowerId = BanditPrince.id

  override def noteKeys: Vector[NoteKey] = Vector(replaced, none)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(Branch((live, _) =>
      val sites = banditSites(live)
      if sites.isEmpty then Vector(Note(id, _ =>
        Some(none(PowerSourceRef.Card(cardId)))))
      else if affordable(live, actor, sites).isEmpty then Vector.empty
      else Vector(
        Decide(decisionId, actor, DecisionQuery.ChooseMany(0, sites.size,
          affordable(live, actor, sites).map(site =>
            DecisionOption.Site(DecisionOptionRef.Site(site))),
          heading = Some("Bandit Prince: choose the sites whose bandits your " +
            "warbands replace"))),
        BuildOps((ready, pending) => replace(ready, actor, pending)),
        Branch((_, pending) => chosen(pending).map(site =>
          Note(id, replacedNote(_, actor, site), covers = true))))))

  /** The actor's warbands the replacement put at `site`. */
  private def replacedNote(states: NoteStates, actor: PlayerId,
      site: SiteId): Option[PowerNote] = for
    (before, after) <- states.previous
    kind <- PlayerFacts.forceKind(after, actor).toOption
    count = held(after, site, kind)
    if count > 0 && bandits(before, site) > 0
  yield replaced(PowerSourceRef.Card(cardId), NoteArg.Number(count),
    NoteArg.Site(site), NoteArg.Player(actor))

object BanditPrince:
  val id: PowerId = PowerId("denizen.bandit-prince")
  val decisionId: String = "cardplay.bandit-prince.sites"
  /** "Replaced {n} bandit at {site} with {Red}'s warbands." */
  val replaced: NoteKey = NoteKey("replaced", Vector(
    NotePart.Text("Replaced "), NotePart.Arg(0),
    NotePart.Plural(0, " bandit at ", " bandits at "), NotePart.Arg(1),
    NotePart.Text(" with "), NotePart.Arg(2), NotePart.Text("'s warbands.")))
  /** "No site was ruled by bandits." */
  val none: NoteKey = NoteKey("none", Vector(
    NotePart.Text("No site was ruled by bandits.")))

  def forCatalog(catalog: ExecutableCatalog): Option[BanditPrince] =
    WhenPlayedPower.cardOf(catalog, id).map(new BanditPrince(_))

  /** The sites in play that bandits hold, in map order. */
  private def banditSites(ready: ReadyGame): Vector[SiteId] =
    ready.game.current.map.inPlay.filter(bandits(ready, _) > 0)

  private def bandits(ready: ReadyGame, site: SiteId): Int =
    held(ready, site, ForceKind.Bandit)

  private def held(ready: ReadyGame, site: SiteId, kind: ForceKind): Int =
    ready.game.current.map.sites.get(site).map(_.forces) match
      case Some(SiteForces.Occupied(`kind`, count)) => count
      case _ => 0

  /** The sites whose bandits alone fit the actor's warband bank. The walker
    * would hide the others, but only after trying every selection holding
    * them, which grows with the number of sites. */
  private def affordable(ready: ReadyGame, actor: PlayerId,
      sites: Vector[SiteId]): Vector[SiteId] =
    PlayerFacts.forceKind(ready, actor).toOption.fold(Vector.empty) { kind =>
      val banked = PlayerFacts.banked(ready, kind)
      sites.filter(bandits(ready, _) <= banked) }

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
