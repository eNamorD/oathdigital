package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

object SaltTheEarthCard extends Denizen(DenizenId("89"), "Salt the Earth", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.salt-the-earth"),
    persistent = false, cost = Cost.free,
    text = "If you're victorious in a conquest, bury all cards at one " +
      "site you targeted.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Salt the Earth (card 89), an attacker's battle plan: "If you're
  * victorious in a conquest, bury all cards at one site you targeted."
  *
  * Free, and offered only in a Conquest. Once the Campaign has resolved and
  * the attacker won, every card at one targeted site is buried: its denizens
  * and edifice, and its relics, facedown ones included. Each burial makes the
  * returns a discard makes (`Bury.standard`): favor to the card's suit bank,
  * secrets to the attacker. A relic has no suit and returns no favor.
  *
  * The attacker chooses the site only when the Campaign targeted two or more.
  * The question offers every target: the targets are durable, so the
  * question never changes once cards are gone. A site that held no card
  * writes no line, so the decision is not narrated.
  *
  * Its line names the cards and the site, "Buried Tinker, the Great Clock and
  * a relic from Ancient City.", and covers the generic "Buried" lines.
  */
final case class SaltTheEarth private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = SaltTheEarthCard.id
  def id: PowerId = SaltTheEarth.id

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Attacker)
  override def noteKeys: Vector[NoteKey] = SaltTheEarth.noteKeys

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId)
      .filter(_ => context.setup.kind == CampaignKind.Conquest)
      .map(source => CampaignPlanOffer(source,
        "Salt the Earth: bury every card at a targeted site if victorious",
        Vector.empty, Vector.empty))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use => (for
      result <- use.result
      if use.won.contains(true) && result.kind == CampaignKind.Conquest
    yield salt(use.actor, result.targetSites)).getOrElse(Vector.empty)))

  private def salt(actor: PlayerId, targets: Vector[SiteId])
      : Vector[Operation] =
    Option.when(targets.size >= 2)(Decide(SaltTheEarth.decisionId, actor,
      DecisionQuery.ChooseOne(targets.map(site =>
        DecisionOption.Site(DecisionOptionRef.Site(site))),
        heading = Some("Salt the Earth: bury every card at one site")))
    ).toVector ++ Vector[Operation](
      BuildOps((ready, pending) => saltedSite(targets, pending)
        .flatMap(burials(ready, actor, _))),
      Note(id, buriedNote(targets), covers = true))

  /** The site to salt: the only target, or the one chosen. */
  private def saltedSite(targets: Vector[SiteId], pending: PendingTree)
      : Either[OathViolation, SiteId] = targets match
    case Vector(only) => Right(only)
    case _ => PowerAnswers.one(pending, SaltTheEarth.decisionId).collect {
      case DecisionOptionRef.Site(chosen) if targets.contains(chosen) => chosen
    }.toRight(PowerAnswers.missing(SaltTheEarth.decisionId))

  /** Every card at `site`, buried with the standard returns. */
  private def burials(ready: ReadyGame, actor: PlayerId, site: SiteId)
      : Either[OathViolation, Vector[CoreOperation]] =
    val from = PositionedLocation(Location.Site(site))
    val cards: Vector[(BuryableCard, Tokens)] =
      ready.game.current.map.sites.get(site).toVector.flatMap(state =>
        state.denizens.map {
          case held: DenizenState => BuryableCard.Denizen(held.id) -> held.tokens
          case held: EdificeState => BuryableCard.Edifice(held.id) -> held.tokens
        } ++ state.relics.map(held =>
          BuryableCard.Relic(held.id) -> held.tokens))
    cards.foldLeft[Either[OathViolation, Vector[CoreOperation]]](
        Right(Vector.empty)) { case (done, (card, tokens)) =>
      val suit = catalog.suitOf(card.id)
      for
        earlier <- done
        _ <- Either.cond(tokens.favor == 0 || suit.isDefined, (),
          OathViolation.InvalidEventOrder(
            s"no suit is known for ${card.id.value}"))
      yield earlier ++ Bury.standard(card, from, suit, tokens.favor,
        tokens.secrets, actor)
    }

  /** The cards that left the salted site in the step before. */
  private def buriedNote(targets: Vector[SiteId])(states: NoteStates)
      : Option[LogNote] = for
    (before, after) <- states.previous
    site <- targets.find(site => SaltTheEarth.cardsAt(before, site) !=
      SaltTheEarth.cardsAt(after, site))
    gone = SaltTheEarth.cardsAt(before, site)
      .filterNot(SaltTheEarth.cardsAt(after, site).contains)
    if gone.nonEmpty
  yield SaltTheEarth.buried(PowerSourceRef.Card(cardId), NoteArg.Cards(gone),
    NoteArg.Site(site))

object SaltTheEarth:
  val id: PowerId = SaltTheEarthCard.power.id
  /** Under the Campaign's prefix, so a parked question is a Campaign
    * decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix + "salt-the-earth"
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Buried {cards} from {site}." */
  val buried: NoteKey = notes.key("buried", Vector(NotePart.Text("Buried "),
    NotePart.Arg(0), NotePart.Text(" from "), NotePart.Arg(1),
    NotePart.Text(".")))

  /** The cards at `site`: its denizens and edifice, then its relics. */
  private def cardsAt(ready: ReadyGame, site: SiteId): Vector[CardId] =
    ready.game.current.map.sites.get(site).toVector.flatMap(state =>
      state.denizens.map[CardId](_.id) ++ state.relics.map[CardId](_.id))

  def forCatalog(catalog: ExecutableCatalog): SaltTheEarth =
    new SaltTheEarth(catalog)
