package oathdigital.gameplay.actions.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.EffectiveSuit
import oathdigital.gameplay.actions.{AdviserFace, CardPlay, CardPlayRules,
  DiscardPermission, SiteRemoval, VisionRules}
import oathdigital.gameplay.operations.{OperationConsent,
  OperationRestrictions}
import oathdigital.model._

/** One way to play a card: the option the placement decision offers, and
  * where the planner puts the card when it is chosen. A Replace option's
  * placement names the card it removes. `buried` is the card the play
  * buries ("Bury and replace" or "Bury and play"). `mayBury` marks a
  * Replace whose card may be buried instead of discarded: the play asks
  * which.
  */
final case class CardSlot(option: DecisionOption, placement: SearchPlacement,
    buried: Option[CardId] = None, mayBury: Boolean = false):
  def ref: DecisionOptionRef = option.ref

  /** The site card this play removes to make room, for a Replace site
    * slot. */
  def replacedAtSite: Option[CardId] = placement match
    case SearchPlacement.Site(_, replaced) => replaced
    case _ => None

  /** The site the card is played to, for a site slot. */
  def site: Option[SiteId] = placement match
    case SearchPlacement.Site(at, _) => Some(at)
    case _ => None

/** The follow-up of a Replace site slot: the replaced card, shown chosen; the
  * other site cards the permissions left may still cover; and how many more
  * may go. */
final case class MoreDiscards(locked: DecisionOption,
    options: Vector[DecisionOption], most: Int)

/** The bury follow-up of a Replace site slot whose card was discarded: the
  * site's other row cards and its relics, at most one to bury. */
final case class MoreBuries(options: Vector[DecisionOption])

/** Where a card may be played, as card slots (card play slots design, "Slot
  * builder").
  *
  * The options, in order: the discard pile; the site slots, the pawn
  * site's first and then each opened site's in map order (`playSites`),
  * each site's first empty slot, then each site card marked Replace when
  * a discard permission holds there (or as a bury when only a bury
  * permission may take it), then, while the site has room and a bury
  * permission holds, each relic there as a bury; the faceup plays; the
  * facedown plays. An adviser play is checked against the advisers as
  * they would be after it (`CardPlayRules.fits`): it offers a new slot when
  * that fits, and only that; or else each held adviser marked Replace whose
  * replacement fits, an adviser that does not count shown unavailable with
  * its reason; and nothing when no replacement fits. An adviser play after
  * which a note of `CardPlayRules` applies is shown `Warned` with its
  * reason, and stays on offer. A facedown adviser offers only its own slot,
  * faceup. A Vision played faceup goes to the revealed Vision spot, and a
  * Conspiracy played faceup is the "Play" button.
  *
  * Each option is planned with `CardPlay.operations`, and the printed
  * restrictions in force judge the plan (`judge`): an option only a waivable
  * protection refuses is askable of its ruler, an option another protection
  * refuses is shown unavailable with its reason, and an option the planner
  * or another restriction refuses is left out. Powers' own restrictions (Vow
  * of Obedience) are the walker look-ahead's to apply.
  *
  * Last, the rules' placement limits (`CardPlayRules.placementLimits`)
  * show each play they refuse unavailable with their reason. When they
  * leave no play open, the discard pile stays open.
  */
object CardSlots:
  /** The answer that plays a faceup Conspiracy, which takes no slot. */
  val play: DecisionOptionRef.Button = DecisionOptionRef.Button("play")

  /** Why a printed protection refuses a play, by its restriction's code. */
  private val reasons: Map[String, String] = Map(
    "locked" -> "Locked: cannot be discarded",
    "active-modifier" -> "In use as a modifier this action",
    "discard-immune" -> "Protected by the Hall of Ministers",
    "bloodline-locked" -> "Ancient Bloodline: Locked.")

  def slots(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      card: WorldCardId, origin: CardPlay.Origin, rules: CardPlayRules,
      picked: Vector[CardId] = Vector.empty): Vector[CardSlot] =
    ready.game.current.players.find(_.player == actor).toVector.flatMap {
      player =>
        def judged(candidates: Vector[(DecisionOption, SearchPlacement)])
            : Vector[CardSlot] = candidates.flatMap { (option, placement) =>
          judge(catalog, ready, actor, option, CardPlay.operations(catalog,
            ready, actor, card, placement, origin), picked)
            .map(CardSlot(_, placement))
        }
        limited(judged(discardPile(ready, actor).toVector) ++
          playSites(ready, player, rules).flatMap(site =>
            siteSlots(catalog, ready, player, card, origin, site, rules,
              picked)) ++
          judged(faceup(player, card, origin, rules) ++
            facedown(player, card, origin, rules)), rules)
    }

  /** `slots` under the placement limits of `rules`: a play a limit
    * refuses is shown unavailable with that limit's reason. When the
    * limits leave no play open, the discard pile stays as it was, since
    * a card that cannot be played is discarded. */
  private def limited(slots: Vector[CardSlot], rules: CardPlayRules)
      : Vector[CardSlot] =
    val marked = slots.map(slot => rules.refusal(slot.placement)
      .filter(_ => slot.option.available).fold(slot)(reason =>
        slot.copy(option = DecisionOption.Unavailable(slot.option,
          reason))))
    if marked.exists(_.option.available) then marked
    else marked.zip(slots).map((after, before) =>
      if before.placement == SearchPlacement.Discard then before
      else after)

  /** The sites `player` may play to under `rules`: the pawn site, then
    * each other site in play that a leave opens, in map order. */
  def playSites(ready: ReadyGame, player: PlayerState,
      rules: CardPlayRules): Vector[SiteId] =
    val pawn = player.pawnSite.toVector
    pawn ++ ready.game.current.map.inPlay.filter(site =>
      !pawn.contains(site) && rules.opens(ready, site))

  /** The removal permissions of `actor`'s play of `card` to `site`: the
    * Homeland rule's first, when `site` is the Homeland of the card's
    * effective suit, then the powers' that hold there. */
  def permissions(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, site: SiteId,
      rules: CardPlayRules): Vector[DiscardPermission] =
    val homeland = for
      suit <- EffectiveSuit.of(catalog, ready, card)
      if CardPlay.homelandSuit(catalog, site).contains(suit)
    yield CardPlayRules.homeland(site)
    homeland.toVector ++ rules.siteDiscards.filter(_.appliesAt(ready, site))

  /** The follow-up of `slot`, a Replace site slot: the other cards of its
    * site's row, an edifice included, and how many more the discard
    * permissions left allow. The replaced card spent one, unless it was
    * buried (`buriedReplaced`). `None` when nothing more may be
    * discarded. */
  def moreDiscards(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, origin: CardPlay.Origin,
      rules: CardPlayRules, slot: CardSlot, buriedReplaced: Boolean = false,
      picked: Vector[CardId] = Vector.empty): Option[MoreDiscards] =
    for
      site <- slot.site
      replacedId <- slot.replacedAtSite
      held = rowCards(ready, site)
      replaced <- held.find(_.id == replacedId)
      discards = permissions(catalog, ready, actor, card, site, rules)
        .count(_.removal == SiteRemoval.Discard)
      left = if buriedReplaced then discards else discards - 1
      if left >= 1
      options = held.filter(_.id != replacedId).flatMap(other =>
        judge(catalog, ready, actor, optionOf(other), CardPlay.operations(
          catalog, ready, actor, card, slot.placement, origin,
          Vector(other.id), buried = Option.when(buriedReplaced)(replacedId)),
          picked))
      most = math.min(left, options.count(_.available))
      if most >= 1
    yield MoreDiscards(optionOf(replaced), options, most)

  /** The bury follow-up of `slot`, a Replace site slot whose card was
    * discarded with `discarded` after it: the site's remaining row cards
    * and every relic there, when a bury permission holds at the site;
    * `None` when nothing may be buried. */
  def moreBuries(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, origin: CardPlay.Origin,
      rules: CardPlayRules, slot: CardSlot, discarded: Vector[CardId],
      picked: Vector[CardId] = Vector.empty): Option[MoreBuries] =
    for
      site <- slot.site
      replacedId <- slot.replacedAtSite
      if permissions(catalog, ready, actor, card, site, rules)
        .exists(_.removal == SiteRemoval.Bury)
      state <- ready.game.current.map.sites.get(site)
      gone = replacedId +: discarded
      rows = state.denizens.filterNot(c => gone.contains(c.id))
        .map(c => (c.id: CardId) -> optionOf(c))
      relics = state.relics.zipWithIndex.map((relic, index) =>
        (relic.id: CardId) -> (DecisionOption.SiteRelicSlot(
          DecisionOptionRef.SiteRelicSlot(site, index)): DecisionOption))
      options = (rows ++ relics).flatMap((id, option) => judge(catalog,
        ready, actor, option, CardPlay.operations(catalog, ready, actor,
          card, slot.placement, origin, discarded, buried = Some(id)),
        picked))
      if options.exists(_.available)
    yield MoreBuries(options)

  /** The line of the permission each removal at `site` spent: discards
    * spend the discard permissions and a bury a bury permission, each in
    * the order `permissions` gives them, and every one covers any site
    * card. Refused when more cards are removed than there are permissions
    * for. */
  def credits(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      card: WorldCardId, site: SiteId, rules: CardPlayRules,
      discarded: Vector[CardId], buried: Option[CardId] = None)
      : Either[OathViolation, Vector[Note]] =
    val permitted = permissions(catalog, ready, actor, card, site, rules)
    val discards = permitted.filter(_.removal == SiteRemoval.Discard)
    val buries = permitted.filter(_.removal == SiteRemoval.Bury)
    for
      _ <- Either.cond(discarded.size <= discards.size, (),
        OathViolation.InvalidSearchPlacement("more site cards are " +
          "discarded than the permissions in play allow"))
      _ <- Either.cond(buried.size <= buries.size, (),
        OathViolation.InvalidSearchPlacement("more site cards are buried " +
          "than the permissions in play allow"))
    yield (discards.take(discarded.size) ++ buries.take(buried.size))
      .map(_.note(site))

  /** `option` as the restrictions in force judge the plan it stands for,
    * played by `actor`. It is kept as it is when nothing refuses. When only
    * waivable refusals stop it, it is kept askable of their granters
    * (consent design, slice 3). Otherwise it is shown unavailable with a
    * protection's reason, or left out. */
  def judge(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      option: DecisionOption,
      planned: Either[OathViolation, Vector[CoreOperation]],
      picked: Vector[CardId] = Vector.empty): Option[DecisionOption] =
    if !option.available then Some(option)
    else planned.toOption.flatMap { operations =>
      val restrictions = OperationRestrictions.forCatalog(catalog)
        .active(Vector.empty, Vector.empty, picked)
      val refusals = OperationConsent.refusals(ready, operations,
        restrictions)
      // A refusal no consent can lift names the blocker, whatever order
      // the restrictions run in.
      val shown = refusals.find(_.waiver.isEmpty).orElse(refusals.headOption)
      if refusals.isEmpty then Some(option)
      else OperationConsent.ask(ready, actor, refusals)
        .map(DecisionOption.Unavailable(option, _))
        .orElse(shown.flatMap(refusal => reasons.get(refusal.code).map(
          reason =>
            DecisionOption.Unavailable(option, OptionReason.Written(reason)))))
    }

  private def discardPile(ready: ReadyGame, actor: PlayerId)
      : Option[(DecisionOption, SearchPlacement)] =
    CardPlay.discardRegion(ready, actor).map(region =>
      DecisionOption.DiscardPile(DecisionOptionRef.DiscardPile(region)) ->
        SearchPlacement.Discard)

  /** The slots of `siteId`, judged: its first empty slot; each row card
    * as a Replace when a discard permission holds there, or as a bury when
    * only a bury permission may take it; and, while the site has room,
    * each relic there as a bury when a bury permission holds. A Replace
    * both may take asks which after it is chosen (`mayBury`). A bury is
    * never refused by a lock, so a locked card a discard may not take is
    * still offered as a bury. */
  private def siteSlots(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, card: WorldCardId, origin: CardPlay.Origin,
      siteId: SiteId, rules: CardPlayRules, picked: Vector[CardId])
      : Vector[CardSlot] =
    val actor = player.player
    (for
      site <- ready.game.current.map.sites.get(siteId)
      capacity <- catalog.site(siteId).map(_.capacity)
    yield
      def slot(index: Int): DecisionOption =
        DecisionOption.SiteSlot(DecisionOptionRef.SiteSlot(siteId, index))
      def judged(option: DecisionOption, placement: SearchPlacement,
          buried: Option[CardId] = None): Option[DecisionOption] =
        judge(catalog, ready, actor, option, CardPlay.operations(catalog,
          ready, actor, card, placement, origin, buried = buried), picked)
      val permitted = permissions(catalog, ready, actor, card, siteId, rules)
      val discards = permitted.exists(_.removal == SiteRemoval.Discard)
      val buries = permitted.exists(_.removal == SiteRemoval.Bury)
      val room = site.denizens.size < capacity
      val open = SearchPlacement.Site(siteId, None)
      val empty = Option.when(room)(slot(site.denizens.size)).flatMap(
        option => judged(option, open).map(CardSlot(_, open)))
      // A Replace a discard covers is a Replace; one only a bury may take
      // is a bury; one both may take asks which, after it is chosen.
      def replace(held: CardId, index: Int): Vector[CardSlot] =
        val placement = SearchPlacement.Site(siteId, Some(held))
        val discarded = Option.when(discards)(judged(
          DecisionOption.Replacing(slot(index), held), placement)).flatten
        val buried = Option.when(buries)(judged(
          DecisionOption.Burying(slot(index), held), placement,
          Some(held))).flatten
        (discarded, buried) match
          case (Some(d), Some(b)) if d.available && b.available =>
            Vector(CardSlot(d, placement, mayBury = true))
          case (Some(d), Some(b)) if !b.available && d.available =>
            Vector(CardSlot(d, placement))
          case (_, Some(b)) =>
            Vector(CardSlot(b, placement, buried = Some(held)))
          case (Some(d), None) => Vector(CardSlot(d, placement))
          case (None, None) => Vector.empty
      val replacing = site.denizens.zipWithIndex.flatMap((held, index) =>
        replace(held.id, index))
      val relics = if !room || !buries then Vector.empty
        else site.relics.zipWithIndex.flatMap((relic, index) =>
          judged(DecisionOption.Burying(DecisionOption.SiteRelicSlot(
            DecisionOptionRef.SiteRelicSlot(siteId, index)), relic.id),
            open, Some(relic.id)).map(CardSlot(_, open,
              buried = Some(relic.id))))
      empty.toVector ++ replacing ++ relics).getOrElse(Vector.empty)

  private def faceup(player: PlayerState, card: WorldCardId,
      origin: CardPlay.Origin, rules: CardPlayRules)
      : Vector[(DecisionOption, SearchPlacement)] =
    val placement = SearchPlacement.Adviser(Orientation.FaceUp, None)
    card match
      case VisionRules.Conspiracy =>
        Vector(DecisionOption.Button(play, "Play") -> placement)
      case _: VisionId =>
        val spot = DecisionOption.RevealedVision(
          DecisionOptionRef.RevealedVision(player.player))
        Vector(player.revealedVision.fold[DecisionOption](spot)(held =>
          DecisionOption.Replacing(spot, held.id)) -> placement)
      case _ => adviserSlots(player, card, origin, Orientation.FaceUp, rules)

  private def facedown(player: PlayerState, card: WorldCardId,
      origin: CardPlay.Origin, rules: CardPlayRules)
      : Vector[(DecisionOption, SearchPlacement)] = origin match
    case CardPlay.Origin.FacedownAdviser => Vector.empty
    case CardPlay.Origin.TemporaryHand =>
      adviserSlots(player, card, origin, Orientation.FaceDown, rules)

  private def adviserSlots(player: PlayerState, card: WorldCardId,
      origin: CardPlay.Origin, orientation: Orientation, rules: CardPlayRules)
      : Vector[(DecisionOption, SearchPlacement)] =
    val held = player.advisers
    val faces = held.map(AdviserFace.of)
    val played = AdviserFace(card, orientation)
    def slot(index: Int): DecisionOption = DecisionOption.AdviserPlay(
      DecisionOptionRef.AdviserPlay(player.player, index,
        orientation == Orientation.FaceUp))
    def noted(option: DecisionOption, after: Vector[AdviserFace])
        : DecisionOption =
      rules.noteOn(after, played).fold(option)(
        DecisionOption.Warned(option, _))
    origin match
      case CardPlay.Origin.FacedownAdviser =>
        // The card turns faceup where it lies.
        val own = held.indexWhere(_.id == card)
        if own < 0 then Vector.empty
        else
          val after = faces.updated(own, played)
          if !rules.fits(after) then Vector.empty
          else Vector(noted(slot(own), after) ->
            SearchPlacement.Adviser(orientation, None))
      case CardPlay.Origin.TemporaryHand =>
        val added = faces :+ played
        if rules.fits(added) then Vector(noted(slot(held.size), added) ->
          SearchPlacement.Adviser(orientation, None))
        else replacements(faces, played, rules).map((index, fits) =>
          val option = DecisionOption.Replacing(slot(index), held(index).id)
          (if fits then noted(option, faces.updated(index, played))
          else DecisionOption.Unavailable(option,
            OptionReason.Written(CardPlayRules.notCounted))) ->
            SearchPlacement.Adviser(orientation, Some(held(index).id)))

  /** The held advisers a play with no free slot may replace, by index, and
    * whether the area after the replacement fits. One that does not fit is
    * kept only when it does not count, to be shown unavailable: replacing it
    * frees no room. Nothing when no replacement fits. */
  private def replacements(faces: Vector[AdviserFace], played: AdviserFace,
      rules: CardPlayRules): Vector[(Int, Boolean)] =
    val fitting = faces.indices.toVector.map(index =>
      index -> rules.fits(faces.updated(index, played)))
    if !fitting.exists(_._2) then Vector.empty
    else fitting.filter((index, fits) =>
      fits || rules.uncounted(faces(index)))

  private def rowCards(ready: ReadyGame, site: SiteId)
      : Vector[SiteDenizenState] =
    ready.game.current.map.sites.get(site)
      .fold(Vector.empty[SiteDenizenState])(_.denizens)

  private def optionOf(card: SiteDenizenState): DecisionOption = card match
    case denizen: DenizenState =>
      DecisionOption.Denizen(DecisionOptionRef.Denizen(denizen.id))
    case edifice: EdificeState =>
      DecisionOption.Edifice(DecisionOptionRef.Edifice(edifice.id))
