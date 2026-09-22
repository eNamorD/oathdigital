package oathdigital.gameplay.setup

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._
import oathdigital.model.OathViolation._

/**
 * Builds the initial `ReadyGame` a `GameStarted` event evolves into
 * (2026-09-21 Chronicle design, slice 2, "Setup from a Chronicle").
 * Replaces the application-layer `ChronicleFirstGamePlan` bridge into the
 * old `FirstGameSetupPlan` command and the setup half of the deleted
 * `FirstGameSetupRules`.
 *
 * Validation here is only what Setup itself needs: known and unique ids,
 * enough cards to deal, and one edifice per Homeland in play. The old
 * plan's suit-count and Vision-packet audit is the generator's own job now
 * (`FirstGameChronicleGenerator.validate`), checked once at generation
 * time rather than again at every setup.
 */
object GameStartRules {
  def evolve(catalog: ExecutableCatalog, chronicle: Chronicle,
      orders: SetupOrders): Either[OathViolation, ReadyGame] =
    for {
      plan <- buildPlan(catalog, chronicle, orders)
      material = new FirstGameSetupMaterializer(catalog)
        .materialize(plan, Vector.empty, Vector.empty)
      lineages = plan.participants.map(participant =>
        participant.lineageId -> LineageState(
          participant.lineageId, None, Role.Exile, Vector.empty, Vector.empty)
      ).toMap
      foundations = FoundationNumber.all.map(number =>
        number -> FoundationState(FoundationFace.Normal, Set.empty)).toMap
      atlas = AtlasState(chronicle.atlasBox.drop(8).map(stored =>
        AtlasEntry.StoredSite(stored.site, Vector.empty, Vector.empty,
          stored.items.collectFirst { case id: EdificeId => id })))
      game = OathGame(catalog.ref,
        CampaignState(atlas, foundations, lineages, chronicle.reliquary,
          chronicle.dispossessed, Map.empty, OathkeeperGoal.Supremacy,
          EraState(20, lineages.keys.map(_ -> 0).toMap)),
        CurrentGameState(material.players, material.map, material.commonCards,
          material.banners, OathkeeperState(None, TitleSide.Oathkeeper),
          TurnState(orders.firstPlayer, Phase.Setup, Set.empty),
          material.tracks, None, material.temporaryHands))
      problems = DomainValidation.validate(game)
      _ <- Either.cond(problems.isEmpty, (), InvalidAggregate(problems))
    } yield ReadyGame.start(game,
      plan.participants.map(p => p.playerId -> p.color).toMap,
      orders.firstPlayer, material.favorBanks)

  private def buildPlan(catalog: ExecutableCatalog, chronicle: Chronicle,
      orders: SetupOrders): Either[OathViolation, FirstGameSetupPlan] =
    for {
      _ <- Either.cond(chronicle.world.isEmpty, (),
        UnsupportedChronicle("a non-empty world requires the Empire"))
      _ <- Either.cond(chronicle.atlasBox.size >= 8, (),
        UnsupportedChronicle(
          s"at least 8 atlas sites are required, got ${chronicle.atlasBox.size}"))
      _ <- Either.cond(
        chronicle.atlasBox.forall(_.items.forall(_.isInstanceOf[EdificeId])), (),
        UnsupportedChronicle(
          "stored denizens or relics on an atlas site are not supported yet"))
      _ <- validateIds(catalog, chronicle)
      _ <- Either.cond(
        chronicle.worldDeck.size >= 6 + orders.participants.size * 3, (),
        UnsupportedChronicle("not enough world-deck cards to deal"))
      inPlay = chronicle.atlasBox.take(8)
      homelands <- homelandEdifices(catalog, inPlay)
      _ <- Either.cond(
        chronicle.relicDeck.size >= inPlay.map(s => relicSlots(catalog, s.site)).sum,
        (), UnsupportedChronicle("not enough relics to fill site slots"))
      _ <- validateParticipants(orders)
    } yield FirstGameSetupPlan(orders.participants, orders.firstPlayer,
      inPlay.map(_.site), chronicle.worldDeck, orders.worldDeckOrder,
      chronicle.relicDeck, homelands)

  private def duplicate[A](values: Vector[A]): Option[A] = {
    val seen = scala.collection.mutable.HashSet.empty[A]
    values.find(value => !seen.add(value))
  }

  private def validateIds(catalog: ExecutableCatalog, chronicle: Chronicle)
      : Either[OathViolation, Unit] = {
    val siteIds = catalog.sites.map(_.id).toSet
    val edificeIds = catalog.edifices.map(e => EdificeId(e.id.value)).toSet
    val denizenIds = catalog.denizens.map(d => DenizenId(d.id.value)).toSet
    val relicIds = catalog.relics.map(r => RelicId(r.id.value)).toSet
    val atlasSites = chronicle.atlasBox.map(_.site)
    val atlasEdifices = chronicle.atlasBox.flatMap(_.items)
      .collect { case id: EdificeId => id }

    if (atlasSites.exists(id => !siteIds.contains(id)))
      Left(UnsupportedChronicle(
        s"unknown atlas site ${atlasSites.find(id => !siteIds.contains(id)).get.value}"))
    else if (duplicate(atlasSites).nonEmpty)
      Left(UnsupportedChronicle(
        s"duplicate atlas site ${duplicate(atlasSites).get.value}"))
    else if (atlasEdifices.exists(id => !edificeIds.contains(id)))
      Left(UnsupportedChronicle(s"unknown edifice " +
        s"${atlasEdifices.find(id => !edificeIds.contains(id)).get.value}"))
    else if (chronicle.worldDeck.exists(id => !denizenIds.contains(id)))
      Left(UnsupportedChronicle(s"unknown denizen " +
        s"${chronicle.worldDeck.find(id => !denizenIds.contains(id)).get.value}"))
    else if (duplicate(chronicle.worldDeck).nonEmpty)
      Left(UnsupportedChronicle(
        s"duplicate world-deck denizen ${duplicate(chronicle.worldDeck).get.value}"))
    else if (chronicle.relicDeck.exists(id => !relicIds.contains(id)))
      Left(UnsupportedChronicle(s"unknown relic " +
        s"${chronicle.relicDeck.find(id => !relicIds.contains(id)).get.value}"))
    else if (duplicate(chronicle.relicDeck).nonEmpty)
      Left(UnsupportedChronicle(
        s"duplicate relic ${duplicate(chronicle.relicDeck).get.value}"))
    else Right(())
  }

  private def validateParticipants(orders: SetupOrders)
      : Either[OathViolation, Unit] = {
    val playerIds = orders.participants.map(_.playerId)
    val lineageIds = orders.participants.map(_.lineageId)
    val colors = orders.participants.map(_.color)
    if (orders.participants.isEmpty) Left(ParticipantsEmpty)
    else if (duplicate(playerIds).nonEmpty)
      Left(DuplicatePlayer(duplicate(playerIds).get))
    else if (duplicate(lineageIds).nonEmpty)
      Left(DuplicateLineage(duplicate(lineageIds).get))
    else if (duplicate(colors).nonEmpty)
      Left(DuplicateColor(duplicate(colors).get))
    else if (!playerIds.contains(orders.firstPlayer))
      Left(UnknownFirstPlayer(orders.firstPlayer))
    else Right(())
  }

  private def homelandEdifices(catalog: ExecutableCatalog,
      inPlay: Vector[StoredSite])
      : Either[OathViolation, Vector[(SiteId, EdificeId)]] = {
    val sitesById = catalog.sites.map(s => s.id -> s).toMap
    val edificesById = catalog.edifices.map(e => EdificeId(e.id.value) -> e).toMap
    inPlay.foldLeft[Either[OathViolation, Vector[(SiteId, EdificeId)]]](
        Right(Vector.empty)) { (acc, stored) =>
      acc.flatMap { built =>
        homelandSuit(sitesById(stored.site).handlers) match {
          case None => Right(built)
          case Some(suit) =>
            stored.items.collectFirst { case id: EdificeId => id } match {
              case Some(edificeId) if edificesById.get(edificeId)
                    .exists(_.suit == suit) =>
                Right(built :+ (stored.site -> edificeId))
              case Some(edificeId) => Left(UnsupportedChronicle(
                s"edifice ${edificeId.value} at ${stored.site.value} does " +
                  "not match its Homeland suit"))
              case None => Left(UnsupportedChronicle(
                s"Homeland ${stored.site.value} has no stored edifice"))
            }
        }
      }
    }
  }

  private def homelandSuit(handlers: Vector[String]): Option[Suit] =
    handlers.collectFirst {
      case handler if handler.contains(".homeland-") =>
        handler.substring(handler.indexOf(".homeland-") + 10)
    }.flatMap(Suit.fromKey)

  private def relicSlots(catalog: ExecutableCatalog, site: SiteId): Int =
    catalog.sites.find(_.id == site).get.relicSlots
}
