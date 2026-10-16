package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.EffectiveSuit
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.actions.campaign.CampaignSetup
import oathdigital.model._

/** The standard discard of a denizen a battle plan used, once the plan is spent:
  * the card goes facedown to the discard pile of the region after the card's own
  * region, its favor returns to its suit's bank, and its secrets return to its
  * user facedown. The card is found where it stands now. A card at a site is in
  * the site's region. An adviser has no region of its own, so it takes the
  * region of its holder's pawn, as a card played from a hand does
  * (`CardPlay.nextRegion` is the one rule for the region after). Nothing happens
  * when the card is no longer in either place.
  *
  * `afterCampaign` is the discard of a plan that says "At end, discard" (Horse
  * Archers, Storm Caller), with its line. A power that replaces the discard
  * of a plan (Wild Mounts) finds the discard itself, however it was built.
  */
object PlanDiscard:
  def denizen(catalog: ExecutableCatalog, user: PlayerId, card: DenizenId)
      : Operation = BuildOps((ready, _) => operations(catalog, ready, user, card))

  /** The keys this object owns, which no one power owns. A power that
    * writes one includes this set. */
  val notes: NoteSet = NoteSet.unowned("PlanDiscard")
  /** "Discarded after the Campaign." */
  val discarded: NoteKey = notes.key("discarded",
    Vector(NotePart.Text("Discarded after the Campaign.")))

  /** The standard discard of `card` once the Campaign has resolved, whoever
    * won, and `power`'s line. A bandit defender has no user, so the attacker
    * is the acting player of its discard. */
  def afterCampaign(catalog: ExecutableCatalog, power: PowerId, use: PlanUse,
      card: DenizenId): Vector[Operation] = Vector(
    denizen(catalog, use.user.getOrElse(use.actor), card),
    discardedNote(power, card))

  /** `power`'s "Discarded after the Campaign." for `card`, written only when
    * the step before it took the card to a discard pile. */
  def discardedNote(power: PowerId, card: DenizenId): Note =
    Note(power, states => Option.when(states.previous.exists {
      case (before, after) =>
        !inDiscard(before, card) && inDiscard(after, card)
    })(discarded(PowerSourceRef.Card(card))))

  private def inDiscard(ready: ReadyGame, card: DenizenId): Boolean =
    ready.game.current.commonCards.regionalDiscards.values.exists(_.contains(card))

  private def operations(catalog: ExecutableCatalog, ready: ReadyGame,
      user: PlayerId, card: DenizenId)
      : Either[OathViolation, Vector[CoreOperation]] =
    val current = ready.game.current
    val player = current.players.find(_.player == user)
    val pawnRegion = CampaignSetup.originOf(ready, user)
      .flatMap(current.map.regionOf)
    val asAdviser = player.toVector.flatMap(_.advisers.collect {
      case held: DenizenState if held.id == card =>
        (PositionedLocation(Location.PlayArea(user)), held.tokens, pawnRegion)
    })
    val atSite = current.map.inPlay.flatMap(site => current.map.sites(site)
      .denizens.collect {
        case held: DenizenState if held.id == card =>
          (PositionedLocation(Location.Site(site)), held.tokens,
            current.map.regionOf(site))
      })
    (asAdviser ++ atSite).headOption match
      case None => Right(Vector.empty)
      case Some((from, tokens, region)) =>
        for
          own <- region.toRight(OathViolation.PawnSiteMissing(user))
          // A plan from the user's advisers is read before it leaves.
          suit <- EffectiveSuit.of(catalog, ready, card)
            .toRight(OathViolation.UnknownWorldCard(card))
        yield Vector[CoreOperation](Discard.Denizen(card, from,
          CardPlay.nextRegion(own), suit, tokens.favor, tokens.secrets, user,
          required = true))
