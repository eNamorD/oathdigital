package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{GameStateUpdates, OathLifecycle}
import oathdigital.model._
import oathdigital.setup._
import oathdigital.setup.OathContinue._
import oathdigital.setup.OathEvent._
import oathdigital.setup.OathState._
import oathdigital.setup.OathViolation._

sealed trait ChallengeCommand extends Product with Serializable
object ChallengeCommand {
  final case class Begin(player: PlayerId, decision: DecisionId, banner: Banner)
      extends ChallengeCommand
  final case class ChooseFavorBank(player: PlayerId, decision: DecisionId, suit: Suit)
      extends ChallengeCommand
  final case class ChooseSecretSite(player: PlayerId, decision: DecisionId, site: SiteId)
      extends ChallengeCommand
  final case class Complete(player: PlayerId, decision: DecisionId, amount: Int)
      extends ChallengeCommand
  final case class PlaceResource(player: PlayerId, banner: Banner, amount: Int)
      extends ChallengeCommand
}

/** Shared printed banner invariants. Raid uses the deterministic leftmost policy;
  * Challenge exposes genuine ties to its owner instead.
  */
object BannerRules {
  def holder(current: CurrentGameState, banner: Banner): Option[PlayerId] = banner match {
    case Banner.PeoplesFavor => current.banners.peoplesFavor.holder
    case Banner.DarkestSecret => current.banners.darkestSecret.holder
  }
  def resources(current: CurrentGameState, banner: Banner): Int = banner match {
    case Banner.PeoplesFavor => current.banners.peoplesFavor.favor
    case Banner.DarkestSecret => current.banners.darkestSecret.secrets
  }
  def playerResources(player: PlayerState, banner: Banner): Int = banner match {
    case Banner.PeoplesFavor => player.board.favor
    case Banner.DarkestSecret => player.board.faceUpSecrets // CR p.26: facedown do not count.
  }
  def leastFavorBanks(banks: Map[Suit, Int]): Vector[Suit] = {
    val minimum = Suit.all.map(s => banks.getOrElse(s, 0)).min
    Suit.all.filter(s => banks.getOrElse(s, 0) == minimum)
  }
  def addFavor(banks: Map[Suit, Int], order: Vector[Suit]): Map[Suit, Int] =
    order.foldLeft(banks)((b, s) => b.updated(s, b.getOrElse(s, 0) + 1))
  def leastSites(current: CurrentGameState, placements: Vector[SiteId]): Vector[SiteId] = {
    val counts = placements.groupBy(identity).view.mapValues(_.size).toMap
    val totals = current.map.inPlay.map { id =>
      val site = current.map.sites(id)
      id -> (site.tokens.favor + site.tokens.secrets + counts.getOrElse(id, 0))
    }
    val minimum = totals.map(_._2).min
    totals.collect { case (id, n) if n == minimum => id }
  }
  def automaticFavorPrefix(banks: Map[Suit, Int], remaining: Int): Vector[Suit] = {
    def loop(now: Map[Suit, Int], left: Int, out: Vector[Suit]): Vector[Suit] =
      if (left == 0) out else leastFavorBanks(now) match {
        case Vector(one) => loop(addFavor(now, Vector(one)), left - 1, out :+ one)
        case _ => out
      }
    loop(banks, remaining, Vector.empty)
  }
  def automaticSitePrefix(current: CurrentGameState, existing: Vector[SiteId],
      remaining: Int): Vector[SiteId] = {
    def loop(placed: Vector[SiteId], left: Int, out: Vector[SiteId]): Vector[SiteId] =
      if (left == 0) out else leastSites(current, placed) match {
        case Vector(one) => loop(placed :+ one, left - 1, out :+ one)
        case _ => out
      }
    loop(existing, remaining, Vector.empty)
  }
  def raidFavorReturn(banks: Map[Suit, Int], amount: Int): Vector[Suit] =
    (0 until amount).foldLeft(Vector.empty[Suit]) { (order, _) =>
      val current = addFavor(banks, order)
      order :+ leastFavorBanks(current).head
    }
}

object Challenge {
  val SupplyCost = 1

  def handle(catalog: ExecutableCatalog, state: OathState, command: ChallengeCommand)
      : Either[OathViolation, OathTransition] = command match {
    case ChallengeCommand.Begin(player, decision, banner) => for {
      ready <- OathLifecycle.validateAct(state, player)
      facts <- ChallengeRules.validate(catalog, ready, player, banner)
      (priorHolder, priorResources) = facts
      initial = initialAutomatic(ready, banner, priorHolder, priorResources)
      event = BannerChallengeStarted(player, decision, banner, priorHolder,
        priorResources, SupplyCost, initial._1, initial._2)
      next <- transition(catalog, state, Vector(event), continuation(player, decision,
        banner, priorHolder, priorResources, initial._1.size, initial._2.size))
    } yield next

    case ChallengeCommand.ChooseFavorBank(player, decision, suit) => state match {
      case Ready(ready) => validatePending(ready, player, decision).flatMap { pending =>
        for {
          _ <- Either.cond(pending.banner == Banner.PeoplesFavor, (),
            ChallengeOutcomeMismatch("favor-bank choice is for the wrong banner"))
          banks = BannerRules.addFavor(ready.support.favorBanks, pending.favorReturned)
          legal = BannerRules.leastFavorBanks(banks)
          _ <- Either.cond(legal.size > 1 && legal.contains(suit), (),
            ChallengeOutcomeMismatch("favor bank is not a current tied least-stocked bank"))
          afterChoice = pending.favorReturned :+ suit
          remaining = pending.remainingRibbonResources - 1
          auto = BannerRules.automaticFavorPrefix(
            BannerRules.addFavor(ready.support.favorBanks, afterChoice), remaining)
          event = BannerRibbonChoiceMade(player, decision, pending.banner,
            Some(suit), None, auto, Vector.empty)
          next <- transition(catalog, state, Vector(event), continuation(player, decision,
            pending.banner, pending.priorHolder, pending.priorResources,
            pending.priorResources - remaining + auto.size, 0))
        } yield next
      }
      case _ => Left(GameNotStarted)
    }

    case ChallengeCommand.ChooseSecretSite(player, decision, site) => state match {
      case Ready(ready) => validatePending(ready, player, decision).flatMap { pending =>
        for {
          _ <- Either.cond(pending.banner == Banner.DarkestSecret, (),
            ChallengeOutcomeMismatch("site choice is for the wrong banner"))
          legal = BannerRules.leastSites(ready.game.current, pending.secretsPlaced)
          _ <- Either.cond(legal.size > 1 && legal.contains(site), (),
            ChallengeOutcomeMismatch("site is not a current tied least-stocked site"))
          placed = pending.secretsPlaced :+ site
          remaining = pending.remainingRibbonResources - 1
          auto = BannerRules.automaticSitePrefix(ready.game.current, placed, remaining)
          event = BannerRibbonChoiceMade(player, decision, pending.banner,
            None, Some(site), Vector.empty, auto)
          next <- transition(catalog, state, Vector(event), continuation(player, decision,
            pending.banner, pending.priorHolder, pending.priorResources, 0,
            placed.size + auto.size))
        } yield next
      }
      case _ => Left(GameNotStarted)
    }

    case ChallengeCommand.Complete(player, decision, amount) => state match {
      case Ready(ready) => validatePending(ready, player, decision).flatMap { p =>
        for {
          _ <- Either.cond(p.remainingRibbonResources == 0, (),
            ChallengeOutcomeMismatch("banner ribbon choices are incomplete"))
          actor = ready.game.current.players.find(_.player == player).get
          _ <- Either.cond(amount > p.priorResources, (),
            ChallengeOutcomeMismatch("replacement resources must exceed the prior banner resources"))
          _ <- Either.cond(amount <= BannerRules.playerResources(actor, p.banner), (),
            p.banner match {
              case Banner.PeoplesFavor => InsufficientFavor(amount, actor.board.favor)
              case Banner.DarkestSecret => InsufficientSecrets(amount, actor.board.faceUpSecrets)
            })
          favorOrder = p.favorReturned
          returned = if (p.banner == Banner.DarkestSecret && p.priorHolder.nonEmpty)
            p.priorResources - p.secretsPlaced.size else 0
          event = BannerChallengeCompleted(player, decision, p.banner,
            p.priorHolder, p.priorResources, amount, favorOrder,
            p.secretsPlaced, returned)
          next <- transition(catalog, state, Vector(event), ActActionSelection(player))
        } yield next
      }
      case _ => Left(GameNotStarted)
    }

    case ChallengeCommand.PlaceResource(player, banner, amount) => for {
      ready <- OathLifecycle.validateAct(state, player)
      _ <- ChallengeRules.validateBase(catalog, ready)
      _ <- Either.cond(BannerRules.holder(ready.game.current, banner).contains(player), (),
        ChallengeUnavailable("actor does not hold this banner"))
      p = ready.game.current.players.find(_.player == player).get
      _ <- Either.cond(amount > 0, (), ChallengeOutcomeMismatch("amount must be positive"))
      _ <- Either.cond(amount <= BannerRules.playerResources(p, banner), (),
        banner match {
          case Banner.PeoplesFavor => InsufficientFavor(amount, p.board.favor)
          case Banner.DarkestSecret => InsufficientSecrets(amount, p.board.faceUpSecrets)
        })
      next <- transition(catalog, state, Vector(BannerResourcePlaced(player, banner, amount)),
        ActActionSelection(player))
    } yield next
  }

  private def initialAutomatic(ready: ReadyGame, banner: Banner,
      holder: Option[PlayerId], resources: Int): (Vector[Suit], Vector[SiteId]) = banner match {
    case Banner.PeoplesFavor =>
      BannerRules.automaticFavorPrefix(ready.support.favorBanks, resources) -> Vector.empty
    case Banner.DarkestSecret =>
      val count = if (holder.isEmpty) resources else resources / 2
      Vector.empty -> BannerRules.automaticSitePrefix(ready.game.current, Vector.empty, count)
  }

  private def ribbonCount(banner: Banner, holder: Option[PlayerId], prior: Int): Int = banner match {
    case Banner.PeoplesFavor => prior
    case Banner.DarkestSecret => if (holder.isEmpty) prior else prior / 2
  }

  private def continuation(player: PlayerId, decision: DecisionId, banner: Banner,
      holder: Option[PlayerId], prior: Int, favorDone: Int, sitesDone: Int): OathContinue =
    AwaitingBannerDecision(player, decision)

  private def validatePending(ready: ReadyGame, player: PlayerId, decision: DecisionId)
      : Either[OathViolation, PendingProcedure.Challenge] = {
    val current = ready.game.current
    if (current.turn.activePlayer != player) Left(WrongPlayer(current.turn.activePlayer, player))
    else if (current.turn.phase != Phase.Act) Left(WrongPhase(Phase.Act, current.turn.phase))
    else current.pending match {
      case Some(p: PendingProcedure.Challenge) if p.actor != player => Left(WrongPlayer(p.actor, player))
      case Some(p: PendingProcedure.Challenge) if p.decision != decision => Left(ChallengeDecisionMismatch(p.decision, decision))
      case Some(p: PendingProcedure.Challenge) => Right(p)
      case Some(other) => Left(PendingProcedureBlocksAction(other.decision))
      case None => Left(InvalidEventOrder("no Challenge is pending"))
    }
  }

  def evolve(catalog: ExecutableCatalog, state: OathState, event: OathEvent)
      : Either[OathViolation, OathState] = event match {
    case e: BannerChallengeStarted => for {
      ready <- OathLifecycle.validateAct(state, e.playerId)
      facts <- ChallengeRules.validate(catalog, ready, e.playerId, e.banner)
      _ <- Either.cond(facts == (e.priorHolder -> e.priorResources), (), ChallengeOutcomeMismatch("recorded prior banner facts changed"))
      _ <- Either.cond(e.supplySpent == SupplyCost, (), ChallengeOutcomeMismatch("Challenge must spend 1 Supply"))
      expected = initialAutomatic(ready, e.banner, e.priorHolder, e.priorResources)
      _ <- Either.cond(expected == (e.automaticFavorReturns -> e.automaticSecretSites), (), ChallengeOutcomeMismatch("recorded automatic ribbon distribution is invalid"))
      total = ribbonCount(e.banner, e.priorHolder, e.priorResources)
    } yield Ready(GameStateUpdates.updateCurrent(ready) { c =>
      c.copy(players = c.players.map(p => if (p.player != e.playerId) p else
        p.copy(board = p.board.copy(supply = SupplyTrack(p.board.supply.supply - 1)))),
        pending = Some(PendingProcedure.Challenge(e.decision, e.playerId, e.banner,
          e.priorHolder, e.priorResources, total - expected._1.size - expected._2.size,
          e.automaticFavorReturns,
          e.automaticSecretSites)))
    })

    case e: BannerRibbonChoiceMade => state match {
      case Ready(ready) => validatePending(ready, e.playerId, e.decision).flatMap { p =>
        if (p.banner == Banner.PeoplesFavor) for {
          suit <- e.favorBank.toRight(ChallengeOutcomeMismatch("missing favor-bank choice"))
          _ <- Either.cond(e.secretSite.isEmpty, (), ChallengeOutcomeMismatch("mixed ribbon choice kinds"))
          banks = BannerRules.addFavor(ready.support.favorBanks, p.favorReturned)
          _ <- Either.cond(BannerRules.leastFavorBanks(banks).size > 1 && BannerRules.leastFavorBanks(banks).contains(suit), (), ChallengeOutcomeMismatch("recorded favor tie choice is invalid"))
          chosen = p.favorReturned :+ suit
          expected = BannerRules.automaticFavorPrefix(BannerRules.addFavor(ready.support.favorBanks, chosen), p.remainingRibbonResources - 1)
          _ <- Either.cond(expected == e.automaticFavorReturns, (), ChallengeOutcomeMismatch("recorded favor automatic suffix is invalid"))
        } yield Ready(GameStateUpdates.updateCurrent(ready)(_.copy(pending = Some(p.copy(
          remainingRibbonResources = p.remainingRibbonResources - 1 - expected.size,
          favorReturned = chosen ++ expected)))))
        else for {
          site <- e.secretSite.toRight(ChallengeOutcomeMismatch("missing secret-site choice"))
          _ <- Either.cond(e.favorBank.isEmpty, (), ChallengeOutcomeMismatch("mixed ribbon choice kinds"))
          legal = BannerRules.leastSites(ready.game.current, p.secretsPlaced)
          _ <- Either.cond(legal.size > 1 && legal.contains(site), (), ChallengeOutcomeMismatch("recorded site tie choice is invalid"))
          chosen = p.secretsPlaced :+ site
          expected = BannerRules.automaticSitePrefix(ready.game.current, chosen, p.remainingRibbonResources - 1)
          _ <- Either.cond(expected == e.automaticSecretSites, (), ChallengeOutcomeMismatch("recorded site automatic suffix is invalid"))
        } yield Ready(GameStateUpdates.updateCurrent(ready)(_.copy(pending = Some(p.copy(
          remainingRibbonResources = p.remainingRibbonResources - 1 - expected.size,
          secretsPlaced = chosen ++ expected)))))
      }
      case _ => Left(GameNotStarted)
    }

    case e: BannerChallengeCompleted => state match {
      case Ready(ready) => validatePending(ready, e.playerId, e.decision).flatMap { p =>
        val current = ready.game.current
        val actor = current.players.find(_.player == e.playerId).get
        val expectedReturn = if (p.banner == Banner.DarkestSecret && p.priorHolder.nonEmpty)
          p.priorResources - p.secretsPlaced.size else 0
        for {
          _ <- Either.cond(p.remainingRibbonResources == 0 && e.banner == p.banner &&
            e.priorHolder == p.priorHolder && e.priorResources == p.priorResources &&
            e.placedResources > p.priorResources && e.placedResources <= BannerRules.playerResources(actor, p.banner), (), ChallengeOutcomeMismatch("recorded completion facts are stale or invalid"))
          _ <- Either.cond(e.favorReturnOrder == p.favorReturned && e.secretSiteOrder == p.secretsPlaced && e.secretsReturnedToHolder == expectedReturn, (), ChallengeOutcomeMismatch("recorded ribbon result differs from pending choices"))
        } yield {
          val placedBySite = p.secretsPlaced.groupBy(identity).view.mapValues(_.size).toMap
          val players = current.players.map { x =>
            val paid = if (x.player == e.playerId) p.banner match {
              case Banner.PeoplesFavor => x.board.copy(favor = x.board.favor - e.placedResources)
              case Banner.DarkestSecret => x.board.copy(faceUpSecrets = x.board.faceUpSecrets - e.placedResources)
            } else x.board
            val returned = if (p.banner == Banner.DarkestSecret && p.priorHolder.contains(x.player))
              paid.copy(faceUpSecrets = paid.faceUpSecrets + expectedReturn) else paid
            x.copy(board = returned)
          }
          val sites = placedBySite.foldLeft(current.map.sites) { case (all, (id, n)) =>
            val site = all(id); all.updated(id, site.copy(tokens = Tokens(site.tokens.favor, site.tokens.secrets + n)))
          }
          val banners = p.banner match {
            case Banner.PeoplesFavor => current.banners.copy(peoplesFavor = current.banners.peoplesFavor.copy(holder = Some(e.playerId), favor = e.placedResources))
            case Banner.DarkestSecret => current.banners.copy(darkestSecret = current.banners.darkestSecret.copy(holder = Some(e.playerId), secrets = e.placedResources))
          }
          val updated = GameStateUpdates.updateCurrent(ready)(_.copy(players = players,
            map = current.map.copy(sites = sites), banners = banners, pending = None))
          Ready(updated.copy(support = updated.support.copy(favorBanks =
            BannerRules.addFavor(updated.support.favorBanks, e.favorReturnOrder))))
        }
      }
      case _ => Left(GameNotStarted)
    }

    case e: BannerResourcePlaced => for {
      ready <- OathLifecycle.validateAct(state, e.playerId)
      _ <- ChallengeRules.validateBase(catalog, ready)
      _ <- Either.cond(BannerRules.holder(ready.game.current, e.banner).contains(e.playerId), (), ChallengeOutcomeMismatch("recorded banner holder is invalid"))
      p = ready.game.current.players.find(_.player == e.playerId).get
      _ <- Either.cond(e.amount > 0 && e.amount <= BannerRules.playerResources(p, e.banner), (), ChallengeOutcomeMismatch("recorded banner amount is invalid"))
    } yield Ready(GameStateUpdates.updateCurrent(ready) { c =>
      val players = c.players.map(x => if (x.player != e.playerId) x else e.banner match {
        case Banner.PeoplesFavor => x.copy(board = x.board.copy(favor = x.board.favor - e.amount))
        case Banner.DarkestSecret => x.copy(board = x.board.copy(faceUpSecrets = x.board.faceUpSecrets - e.amount))
      })
      val banners = e.banner match {
        case Banner.PeoplesFavor => c.banners.copy(peoplesFavor = c.banners.peoplesFavor.copy(favor = c.banners.peoplesFavor.favor + e.amount))
        case Banner.DarkestSecret => c.banners.copy(darkestSecret = c.banners.darkestSecret.copy(secrets = c.banners.darkestSecret.secrets + e.amount))
      }
      c.copy(players = players, banners = banners)
    })
    case _ => Left(InvalidEventOrder("Challenge received a non-banner event"))
  }

  private def transition(catalog: ExecutableCatalog, state: OathState,
      events: Vector[OathEvent], continue: OathContinue) =
    events.foldLeft[Either[OathViolation, OathState]](Right(state))(
      (s, e) => s.flatMap(evolve(catalog, _, e))).map(OathTransition(_, events, continue))
}

object ChallengeRules {
  private val AuditedHandlerFingerprint =
    "70b57be7a3a4751e81d5235e033fdb62d1773f1e90fa2354d1c275e3e9d12f97"
  private def fingerprint(catalog: ExecutableCatalog): String = {
    val ids = catalog.denizens.flatMap(_.handlers) ++ catalog.relics.flatMap(_.handlers) ++
      catalog.legacies.flatMap(_.handlers) ++ catalog.sites.flatMap(_.handlers) ++
      catalog.edifices.flatMap(e => e.intact.handlers ++ e.ruined.handlers)
    java.security.MessageDigest.getInstance("SHA-256").digest(
      ids.distinct.sorted.mkString("\n").getBytes("UTF-8"))
      .map(b => f"${b & 0xff}%02x").mkString
  }
  def validateBase(catalog: ExecutableCatalog, ready: ReadyGame): Either[OathViolation, Unit] = {
    val game = ready.game
    val reason =
      if (game.campaign.foundations.values.exists(f => f.face != FoundationFace.Normal || f.alterationSources.nonEmpty)) Some("altered banner Foundations are unsupported")
      else if (game.current.banners.peoplesFavor.active != PeoplesFavorFace.Mob || game.current.banners.darkestSecret.active != DarkestSecretFace.WanderingFlame) Some("unsupported active banner face")
      else if (game.campaign.lineages.values.exists(_.role != Role.Exile)) Some("Challenge is bounded to the first-game Exile state")
      else if (game.campaign.lineages.values.exists(_.legacies.exists(_.active))) Some("active legacy Challenge modifiers are unsupported")
      else if (fingerprint(catalog) != AuditedHandlerFingerprint) Some("unknown banner/Challenge handler catalog")
      else None
    reason.map(UnsupportedBannerState).toLeft(())
  }
  def validate(catalog: ExecutableCatalog, ready: ReadyGame, playerId: PlayerId,
      banner: Banner): Either[OathViolation, (Option[PlayerId], Int)] = for {
    _ <- validateBase(catalog, ready)
    player <- ready.game.current.players.find(_.player == playerId)
      .toRight(ChallengeUnavailable("actor is not in the game"))
    _ <- Either.cond(player.board.supply.supply >= Challenge.SupplyCost, (),
      InsufficientSupply(Challenge.SupplyCost, player.board.supply.supply))
    holder = BannerRules.holder(ready.game.current, banner)
    amount = BannerRules.resources(ready.game.current, banner)
    _ <- Either.cond(!holder.contains(playerId), (), ChallengeUnavailable("a player cannot challenge a banner they hold"))
    _ <- holder.filterNot(_ == playerId).map { enemy =>
      val enemySite = ready.game.current.players.find(_.player == enemy).flatMap(_.pawnSite)
      Either.cond(player.pawnSite.nonEmpty && player.pawnSite == enemySite, (),
        ChallengeUnavailable("enemy-held banner requires pawn co-location"))
    }.getOrElse(Right(()))
    _ <- Either.cond(BannerRules.playerResources(player, banner) > amount, (),
      ChallengeUnavailable("actor lacks strictly more relevant faceup resources than the banner"))
  } yield holder -> amount
  def legal(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId): Vector[Banner] =
    Banner.all.filter(validate(catalog, ready, player, _).isRight)
}
