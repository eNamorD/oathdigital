package oathdigital.serialization

import oathdigital.model._

/** Operation spellings for recorded walker steps, split out of
  * `WalkerEventCodec` to give it headroom under the 800-line bound.
  *
  * A pure move: every spelling is byte-identical to the version before the
  * split, which `GameEventWireSuite` pins without being edited.
  *
  * `encodeOperation`/`encodePiece` (I8) are total over `CoreOperation`/
  * `Piece`, exactly like [[encodeLocation]] below (added for the same
  * reason: a bounded operation set is what let Catacombs' recorded `Move`
  * out of `Location.Deck` reach an append-time throw with no test to catch
  * it -- that lesson generalises to every recorded shape, not just
  * `Location`). The one genuine exception is `encodeOperation`'s five
  * walker tree-control arms (`Decide`/`BuildOps`/`Repeat`/`Branch`/
  * `Sequence`): three close over a Scala function value with no data
  * representation at all, and none of the five can ever legally reach this
  * method, because `ProcedureWalker` only ever records an
  * ALREADY-APPLIED delta batch (a leaf's own effect, or a `BuildOps`
  * closure's *returned* `Vector[CoreOperation]`) -- never one of these
  * control nodes themselves. Those five arms throw
  * [[UnencodableOperation]] (a typed [[WireError]] carrier caught by
  * `GameEventWire.encodePayloadSafe`) instead of falling into a silent
  * wildcard `case other => throw`, so a NEW `CoreOperation` case fails to
  * compile here ("match may not be exhaustive") until it is given a real
  * arm.
  */
private[serialization] trait WalkerOperationCodec extends CampaignResultCodec {
    this: GameEventJsonSupport =>
  import WireError._

  protected final def encodeOperation(operation: CoreOperation): ujson.Value =
    operation match {
      case AdvanceVisionsDrawn => ujson.Obj("kind" -> "advance-visions-drawn")
      case SpendSupply(player, amount, _) => ujson.Obj(
        "kind" -> "spend-supply", "playerId" -> player.value,
        "amount" -> amount)
      case GainSupply(player, amount) => ujson.Obj(
        "kind" -> "gain-supply", "playerId" -> player.value,
        "amount" -> amount)
      case ModifyDicePool(pool, delta, _) => ujson.Obj(
        "kind" -> "modify-dice-pool", "pool" -> pool.value,
        "delta" -> delta)
      // A use limit is journalled as the ref it records, not as the power
      // that asked for it: replay adds the same ref to the same turn without
      // gathering anything.
      case RecordPowerUse(PowerUseRef(timing, source, id)) =>
        ujson.Obj.from(Vector[(String, ujson.Value)](
          "kind" -> "record-power-use",
          "timing" -> encodePowerTiming(timing)) ++ (source match {
          case PowerSourceRef.Site(site) => Vector("siteId" -> ujson.Str(site.value))
          case PowerSourceRef.Card(card) => Vector(
            "cardKind" -> ujson.Str(card.kind), "cardId" -> ujson.Str(card.value))
          case PowerSourceRef.Banner(banner) =>
            Vector("bannerKey" -> ujson.Str(banner.key))
        }) :+ ("powerId" -> ujson.Str(id.value)))
      case EnterPhase(phase) => ujson.Obj("kind" -> "enter-phase",
        "phase" -> phase.key)
      case RecordCampaignResult(result) => ujson.Obj(
        "kind" -> "record-campaign-result",
        "result" -> encodeCampaignResult(result))
      case SetOathkeeper(holder) => ujson.Obj("kind" -> "set-oathkeeper",
        "holderPlayerId" -> holder.fold[ujson.Value](ujson.Null)(p =>
          ujson.Str(p.value)))
      case BeginTurn(player, phase) => ujson.Obj("kind" -> "begin-turn",
        "playerId" -> player.value, "phase" -> phase.key)
      case Move(piece, from, to, orientation) => ujson.Obj(
        "kind" -> "move",
        "piece" -> encodePiece(piece),
        "from" -> encodePositionedLocation(from),
        "to" -> encodePositionedLocation(to),
        "resultingOrientation" -> orientation.fold[ujson.Value](ujson.Null)(
          value => ujson.Str(encodeOrientation(value))))
      case PayCost(player, placedAt, cost, intoOccupied, matchingBank, _) =>
        val optional: Vector[(String, ujson.Value)] =
          (if (intoOccupied) Vector("intoOccupied" -> (ujson.Bool(true): ujson.Value))
          else Vector.empty) ++
            matchingBank.toVector.map(suit =>
              "matchingBank" -> (ujson.Str(suit.key): ujson.Value))
        ujson.Obj.from(Vector[(String, ujson.Value)](
          "kind" -> "pay-cost", "playerId" -> player.value,
          "placedAt" -> encodeLocation(placedAt),
          "cost" -> encodeCost(cost)) ++ optional)
      case Peek(viewer, card, at) => ujson.Obj("kind" -> "peek",
        "viewerPlayerId" -> viewer.value, "card" -> encodeCardRef(card),
        "at" -> encodeLocation(at))
      case Flip(card, at, orientation) => ujson.Obj("kind" -> "flip",
        "card" -> encodeCardRef(card), "at" -> encodeLocation(at),
        "orientation" -> encodeOrientation(orientation))
      case FlipSecrets(player, amount, from, to) => ujson.Obj(
        "kind" -> "flip-secrets", "playerId" -> player.value,
        "amount" -> amount, "from" -> encodeSecretSide(from),
        "to" -> encodeSecretSide(to))
      case burn: Burn => ujson.Obj("kind" -> "burn",
        "resource" -> encodePiece(burn.resource),
        "from" -> encodePositionedLocation(burn.from))
      case bury: Bury => ujson.Obj("kind" -> "bury",
        "card" -> encodeBuryableCard(bury.card),
        "from" -> encodePositionedLocation(bury.from))
      case Discard.Denizen(card, from, to, suit, favor, secrets, actingPlayer, _) =>
        ujson.Obj("kind" -> "discard-denizen", "card" -> card.value,
          "from" -> encodePositionedLocation(from), "to" -> to.key,
          "suit" -> suit.key, "favor" -> favor, "secrets" -> secrets,
          "actingPlayerId" -> actingPlayer.value)
      case Discard.Vision(card, from, to, _) => ujson.Obj(
        "kind" -> "discard-vision", "card" -> card.value,
        "from" -> encodePositionedLocation(from), "to" -> to.key)
      case Discard.RuinedEdifice(card, from, suit, favor, secrets, actingPlayer, _) =>
        ujson.Obj("kind" -> "discard-ruined-edifice", "card" -> card.value,
          "from" -> encodePositionedLocation(from), "suit" -> suit.key,
          "favor" -> favor, "secrets" -> secrets,
          "actingPlayerId" -> actingPlayer.value)
      case Discard.Relic(card, from, secrets, actingPlayer) => ujson.Obj(
        "kind" -> "discard-relic", "card" -> card.value,
        "from" -> encodePositionedLocation(from), "secrets" -> secrets,
        "actingPlayerId" -> actingPlayer.value)
      case Draw(player, cards, source, destination) => ujson.Obj(
        "kind" -> "draw", "playerId" -> player.value,
        "cards" -> ujson.Arr.from(cards.map(encodeCardRef)),
        "source" -> encodeLocation(source),
        "destination" -> encodeLocation(destination))
      case Exchange(give, receive) => ujson.Obj("kind" -> "exchange",
        "give" -> encodeGive(give, "exchange-give"),
        "receive" -> encodeGive(receive, "exchange-receive"))
      case Gain.Favor(player, suit, amount) => ujson.Obj(
        "kind" -> "gain-favor", "playerId" -> player.value,
        "suit" -> suit.key, "amount" -> amount)
      case Gain.Secrets(player, amount) => ujson.Obj(
        "kind" -> "gain-secrets", "playerId" -> player.value,
        "amount" -> amount)
      case Gain.Warbands(player, kind, amount) => ujson.Obj(
        "kind" -> "gain-warbands", "playerId" -> player.value,
        "force" -> encodeForceKind(kind), "amount" -> amount)
      case give: Give => encodeGive(give, "give")
      case Kill(warbands, from) => ujson.Obj("kind" -> "kill",
        "warbands" -> encodePiece(warbands),
        "from" -> encodePositionedLocation(from))
      case Play(card, from, destination, orientation, _) => ujson.Obj(
        "kind" -> "play", "card" -> encodeCardRef(card),
        "from" -> encodePositionedLocation(from),
        "destination" -> encodeLocation(destination),
        "orientation" -> encodeOrientation(orientation))
      case Replace(removed, replacements, at, _) => ujson.Obj(
        "kind" -> "replace", "removed" -> encodePiece(removed),
        "replacements" -> encodePiece(replacements),
        "at" -> encodePositionedLocation(at))
      case Reveal(card, at) => ujson.Obj("kind" -> "reveal",
        "card" -> encodeCardRef(card), "at" -> encodeLocation(at))
      case Sacrifice(player, warbands, from) => ujson.Obj(
        "kind" -> "sacrifice", "playerId" -> player.value,
        "warbands" -> encodePiece(warbands),
        "from" -> encodePositionedLocation(from))
      case Swap(firstCard, firstLocation, secondCard, secondLocation) =>
        ujson.Obj("kind" -> "swap", "firstCard" -> encodeCardRef(firstCard),
          "firstLocation" -> encodePositionedLocation(firstLocation),
          "secondCard" -> encodeCardRef(secondCard),
          "secondLocation" -> encodePositionedLocation(secondLocation))
      case Take(piece, player, from, to, sourcePosition) => ujson.Obj(
        "kind" -> "take", "piece" -> encodePiece(piece),
        "playerId" -> player.value, "from" -> encodeLocation(from),
        "to" -> encodeLocation(to),
        "sourcePosition" -> encodeStackPosition(sourcePosition))
      case Roll(pool, dice, _, _) => ujson.Obj("kind" -> "roll",
        "pool" -> pool.value, "die" -> encodeDiceKind(dice.die))
      case ModifyRollOutcome(pool, skulls, score) => ujson.Obj(
        "kind" -> "modify-roll-outcome", "pool" -> pool.value,
        "skulls" -> skulls.fold[ujson.Value](ujson.Null)(ujson.Num(_)),
        "score" -> score.fold[ujson.Value](ujson.Null)(ujson.Num(_)))
      case ClearDicePool(pool) => ujson.Obj("kind" -> "clear-dice-pool",
        "pool" -> pool.value)
      // The five arms below are the walker's own tree-control vocabulary
      // (see this trait's doc): `decide`/`build`/`repeat`/`branch` each
      // close over a Scala function value with no data representation, and
      // `sequence`'s children are typed as the deliberately non-sealed
      // `Operation` (not `CoreOperation`), so even a closure-free Sequence
      // cannot be soundly decomposed here. None of the five can legally
      // reach this method: `ProcedureWalker` only ever records an
      // ALREADY-APPLIED delta batch, never one of its own control nodes.
      // Each throws a specific `UnencodableOperation` (a typed `WireError`
      // carrier `GameEventWire.encodePayloadSafe` unwraps) instead of a
      // silent wildcard, so a genuine new `CoreOperation` case still fails
      // to compile here until it is given a real arm.
      case decide: Decide => throw UnencodableOperation(InvalidValue(
        "$.payload.ops", s"a Decide node (decision '${decide.decisionId}') " +
          "parks the walker and can never be a recorded, already-applied " +
          "operation"))
      case _: BuildOps => throw UnencodableOperation(InvalidValue(
        "$.payload.ops", "a BuildOps node closes over a build function " +
          "and can never be a recorded, already-applied operation -- only " +
          "the Vector[CoreOperation] it RETURNS is ever recorded"))
      case _: Repeat => throw UnencodableOperation(InvalidValue(
        "$.payload.ops", "a Repeat node closes over a guard function and " +
          "is a tree-control composite, never a recorded delta"))
      case _: Branch => throw UnencodableOperation(InvalidValue(
        "$.payload.ops", "a Branch node closes over a select function and " +
          "is a tree-control composite, never a recorded delta"))
      case _: Sequence => throw UnencodableOperation(InvalidValue(
        "$.payload.ops", "a Sequence node is a tree-control composite over " +
          "the non-sealed Operation type, never a recorded delta"))
      case _: CardPlayedFaceup | _: CardPlayedFacedown =>
        throw UnencodableOperation(InvalidValue(
          "$.payload.ops", "a CardPlayed hook is a tree-control composite, " +
            "never a recorded delta"))
    }

  private def encodePowerTiming(timing: PowerTiming): String = timing match {
    case PowerTiming.Wake => "wake"
    case PowerTiming.Act => "act"
    case PowerTiming.Rest => "rest"
  }

  private def decodePowerTiming(value: String,
      path: String): Either[WireError, PowerTiming] = value match {
    case "wake" => Right(PowerTiming.Wake)
    case "act" => Right(PowerTiming.Act)
    case "rest" => Right(PowerTiming.Rest)
    case other => Left(InvalidValue(path, s"unknown power timing '$other'"))
  }

  private def decodePowerCard(kind: String, id: String,
      path: String): Either[WireError, CardId] = kind match {
    case "denizen" => Right(DenizenId(id))
    case "relic" => Right(RelicId(id))
    case "edifice" => Right(EdificeId(id))
    case "vision" => Right(VisionId(id))
    case "legacy" => Right(LegacyId(id))
    case other => Left(InvalidValue(path, s"unknown power source card '$other'"))
  }

  protected final def decodeOperation(value: ujson.Value,
      path: String): Either[WireError, CoreOperation] =
    value("kind").str match {
      case "advance-visions-drawn" => Right(AdvanceVisionsDrawn)
      case "spend-supply" => decodePositiveInt(value("amount"), s"$path.amount")
        .map(amount => SpendSupply(PlayerId(value("playerId").str), amount))
      case "gain-supply" => decodePositiveInt(value("amount"), s"$path.amount")
        .map(amount => GainSupply(PlayerId(value("playerId").str), amount))
      case "modify-dice-pool" =>
        decodeSignedInt(value("delta"), s"$path.delta")
          .map(delta => ModifyDicePool(PoolKey(value("pool").str), delta))
      case "record-power-use" => for {
        timing <- decodePowerTiming(value("timing").str, s"$path.timing")
        source <- (if (value.obj.contains("siteId"))
            Right(PowerSourceRef.Site(SiteId(value("siteId").str)))
          else if (value.obj.contains("bannerKey"))
            Banner.fromKey(value("bannerKey").str).map(PowerSourceRef.Banner(_))
              .toRight(InvalidValue(s"$path.bannerKey", "unknown banner"))
          else decodePowerCard(value("cardKind").str, value("cardId").str,
            s"$path.cardKind").map(PowerSourceRef.Card)
          ): Either[WireError, PowerSourceRef]
      } yield RecordPowerUse(PowerUseRef(timing, source,
        PowerId(value("powerId").str)))
      case "enter-phase" =>
        val key = value("phase").str
        Phase.fromKey(key).toRight(
          InvalidValue(s"$path.phase", s"unknown phase '$key'"))
          .map(EnterPhase.apply)
      case "record-campaign-result" =>
        decodeCampaignResult(value("result"), s"$path.result")
          .map(RecordCampaignResult(_))
      case "set-oathkeeper" => Right(SetOathkeeper(value("holderPlayerId") match {
        case ujson.Null => None
        case other => Some(PlayerId(other.str))
      }))
      case "begin-turn" =>
        val key = value("phase").str
        Phase.fromKey(key).toRight(
          InvalidValue(s"$path.phase", s"unknown phase '$key'"))
          .map(BeginTurn(PlayerId(value("playerId").str), _))
      case "move" => for {
        piece <- decodePiece(value("piece"), s"$path.piece")
        from <- decodePositionedLocation(value("from"), s"$path.from")
        to <- decodePositionedLocation(value("to"), s"$path.to")
        orientation <- value("resultingOrientation") match {
          case ujson.Null => Right(None)
          case other => decodeOrientation(other.str,
            s"$path.resultingOrientation").map(Some(_))
        }
      } yield Move(piece, from, to, orientation)
      case "pay-cost" => for {
        placedAt <- decodeLocation(value("placedAt"), s"$path.placedAt")
        cost <- decodeCost(value("cost"), s"$path.cost")
        bank <- value.obj.get("matchingBank") match {
          case None | Some(ujson.Null) => Right(None)
          case Some(raw) => decodeSuit(raw.str, s"$path.matchingBank").map(Some(_))
        }
      } yield PayCost(PlayerId(value("playerId").str), placedAt, cost,
        intoOccupied = value.obj.get("intoOccupied").exists(_.bool),
        matchingBank = bank)
      case "peek" => for {
        card <- decodeCardRef(value("card"), s"$path.card")
        at <- decodeLocation(value("at"), s"$path.at")
      } yield Peek(PlayerId(value("viewerPlayerId").str), card, at)
      case "flip" => for {
        card <- decodeCardRef(value("card"), s"$path.card")
        at <- decodeLocation(value("at"), s"$path.at")
        orientation <- decodeOrientation(value("orientation").str,
          s"$path.orientation")
      } yield Flip(card, at, orientation)
      case "flip-secrets" => for {
        amount <- safeIntField(value.obj, "amount", path)
        from <- decodeSecretSide(value("from").str, s"$path.from")
        to <- decodeSecretSide(value("to").str, s"$path.to")
      } yield FlipSecrets(PlayerId(value("playerId").str), amount, from, to)
      case "burn" => for {
        resource <- decodePiece(value("resource"), s"$path.resource")
        from <- decodePositionedLocation(value("from"), s"$path.from")
        burn <- resource match {
          case Piece.Favor(amount) => Right(Burn.favor(amount, from))
          case Piece.Secrets(amount) => Right(Burn.secrets(amount, from))
          case other => Left(InvalidValue(s"$path.resource",
            s"unsupported burn resource $other"))
        }
      } yield burn
      case "bury" => for {
        card <- decodeBuryableCard(value("card"), s"$path.card")
        from <- decodePositionedLocation(value("from"), s"$path.from")
      } yield Bury(card, from)
      case "discard-denizen" => for {
        from <- decodePositionedLocation(value("from"), s"$path.from")
        to <- decodeRegion(value("to").str, s"$path.to")
        suit <- decodeSuit(value("suit").str, s"$path.suit")
        favor <- safeIntField(value.obj, "favor", path)
        secrets <- safeIntField(value.obj, "secrets", path)
      } yield Discard.Denizen(DenizenId(value("card").str), from, to, suit,
        favor, secrets, PlayerId(value("actingPlayerId").str))
      case "discard-vision" => for {
        from <- decodePositionedLocation(value("from"), s"$path.from")
        to <- decodeRegion(value("to").str, s"$path.to")
      } yield Discard.Vision(VisionId(value("card").str), from, to)
      case "discard-ruined-edifice" => for {
        from <- decodePositionedLocation(value("from"), s"$path.from")
        suit <- decodeSuit(value("suit").str, s"$path.suit")
        favor <- safeIntField(value.obj, "favor", path)
        secrets <- safeIntField(value.obj, "secrets", path)
      } yield Discard.RuinedEdifice(EdificeId(value("card").str), from, suit,
        favor, secrets, PlayerId(value("actingPlayerId").str))
      case "discard-relic" => for {
        from <- decodePositionedLocation(value("from"), s"$path.from")
        secrets <- safeIntField(value.obj, "secrets", path)
      } yield Discard.Relic(RelicId(value("card").str), from, secrets,
        PlayerId(value("actingPlayerId").str))
      case "draw" => for {
        cards <- traverse(value("cards").arr.zipWithIndex.toVector) {
          case (id, index) => decodeCardRef(id, s"$path.cards[$index]")
        }
        _ <- Either.cond(cards.nonEmpty, (), InvalidValue(s"$path.cards",
          "draw must contain at least one card"))
        source <- decodeLocation(value("source"), s"$path.source")
        destination <- decodeLocation(value("destination"), s"$path.destination")
      } yield Draw(PlayerId(value("playerId").str), cards, source, destination)
      case "exchange" => for {
        give <- decodeGive(value("give"), s"$path.give")
        receive <- decodeGive(value("receive"), s"$path.receive")
      } yield Exchange(give, receive)
      case "gain-favor" => for {
        amount <- safeIntField(value.obj, "amount", path)
        suit <- decodeSuit(value("suit").str, s"$path.suit")
      } yield Gain.Favor(PlayerId(value("playerId").str), suit, amount)
      case "gain-secrets" => safeIntField(value.obj, "amount", path).map(
        amount => Gain.Secrets(PlayerId(value("playerId").str), amount))
      case "gain-warbands" => for {
        force <- decodeForceKind(value("force"), s"$path.force")
        amount <- safeIntField(value.obj, "amount", path)
      } yield Gain.Warbands(PlayerId(value("playerId").str), force, amount)
      case "give" => decodeGive(value, path)
      case "kill" => for {
        piece <- decodePiece(value("warbands"), s"$path.warbands")
        warbands <- asWarbands(piece, s"$path.warbands")
        from <- decodePositionedLocation(value("from"), s"$path.from")
      } yield Kill(warbands, from)
      case "play" => for {
        card <- decodeCardRef(value("card"), s"$path.card")
        from <- decodePositionedLocation(value("from"), s"$path.from")
        destination <- decodeLocation(value("destination"), s"$path.destination")
        _ <- destination match {
          case _: Location.Site | _: Location.PlayArea => Right(())
          case other => Left(InvalidValue(s"$path.destination",
            s"play destination must be a site or a play area, got $other"))
        }
        orientation <- decodeOrientation(value("orientation").str,
          s"$path.orientation")
      } yield Play(card, from, destination, orientation)
      case "replace" => for {
        removedPiece <- decodePiece(value("removed"), s"$path.removed")
        removed <- asWarbands(removedPiece, s"$path.removed")
        replacementsPiece <- decodePiece(value("replacements"),
          s"$path.replacements")
        replacements <- asWarbands(replacementsPiece, s"$path.replacements")
        at <- decodePositionedLocation(value("at"), s"$path.at")
        _ <- Either.cond(removed.amount == replacements.amount, (),
          InvalidValue(path, "replace must exchange equal numbers of warbands"))
        _ <- Either.cond(removed.kind != replacements.kind, (),
          InvalidValue(path, "replacement warbands must have a new color"))
      } yield Replace(removed, replacements, at)
      case "reveal" => for {
        card <- decodeCardRef(value("card"), s"$path.card")
        at <- decodeLocation(value("at"), s"$path.at")
      } yield Reveal(card, at)
      case "sacrifice" => for {
        piece <- decodePiece(value("warbands"), s"$path.warbands")
        warbands <- asWarbands(piece, s"$path.warbands")
        from <- decodePositionedLocation(value("from"), s"$path.from")
      } yield Sacrifice(PlayerId(value("playerId").str), warbands, from)
      case "swap" => for {
        firstCard <- decodeCardRef(value("firstCard"), s"$path.firstCard")
        firstLocation <- decodePositionedLocation(value("firstLocation"),
          s"$path.firstLocation")
        secondCard <- decodeCardRef(value("secondCard"), s"$path.secondCard")
        secondLocation <- decodePositionedLocation(value("secondLocation"),
          s"$path.secondLocation")
        _ <- Either.cond(firstCard != secondCard, (),
          InvalidValue(path, "swap requires two different cards"))
        _ <- Either.cond(firstLocation != secondLocation, (),
          InvalidValue(path, "swap requires two different locations"))
      } yield Swap(firstCard, firstLocation, secondCard, secondLocation)
      case "take" => for {
        piece <- decodePiece(value("piece"), s"$path.piece")
        from <- decodeLocation(value("from"), s"$path.from")
        to <- decodeLocation(value("to"), s"$path.to")
        sourcePosition <- decodeStackPosition(value("sourcePosition").str,
          s"$path.sourcePosition")
      } yield Take(piece, PlayerId(value("playerId").str), from, to,
        sourcePosition)
      case "roll" => decodeDiceKind(value("die").str, s"$path.die").map(die =>
        Roll(PoolKey(value("pool").str), DiceSpec(die)))
      case "modify-roll-outcome" => for {
        skulls <- decodeOptionalSignedInt(value("skulls"), s"$path.skulls")
        score <- decodeOptionalSignedInt(value("score"), s"$path.score")
      } yield ModifyRollOutcome(PoolKey(value("pool").str), skulls, score)
      case "clear-dice-pool" => Right(ClearDicePool(PoolKey(value("pool").str)))
      case other => Left(InvalidValue(s"$path.kind",
        s"unknown recorded walker operation '$other'"))
    }

  private def encodeGive(give: Give, kind: String): ujson.Value = ujson.Obj(
    "kind" -> kind, "piece" -> encodePiece(give.piece),
    "giverPlayerId" -> give.giver.value, "from" -> encodeLocation(give.from),
    "to" -> encodeLocation(give.to))

  private def decodeGive(value: ujson.Value,
      path: String): Either[WireError, Give] = for {
    piece <- decodePiece(value("piece"), s"$path.piece")
    from <- decodeLocation(value("from"), s"$path.from")
    to <- decodeLocation(value("to"), s"$path.to")
  } yield Give(piece, PlayerId(value("giverPlayerId").str), from, to)

  private def asWarbands(piece: Piece,
      path: String): Either[WireError, Piece.Warbands] = piece match {
    case warbands: Piece.Warbands => Right(warbands)
    case other => Left(InvalidValue(path, s"expected warbands, found $other"))
  }

  private def encodeBuryableCard(card: BuryableCard): ujson.Value = card match {
    case BuryableCard.Denizen(id) => ujson.Obj("kind" -> "denizen",
      "id" -> id.value)
    case BuryableCard.Relic(id) => ujson.Obj("kind" -> "relic",
      "id" -> id.value)
    case BuryableCard.Edifice(id) => ujson.Obj("kind" -> "edifice",
      "id" -> id.value)
    case BuryableCard.Vision(id) => ujson.Obj("kind" -> "vision",
      "id" -> id.value)
  }

  private def decodeBuryableCard(value: ujson.Value,
      path: String): Either[WireError, BuryableCard] = value("kind").str match {
    case "denizen" => Right(BuryableCard.Denizen(DenizenId(value("id").str)))
    case "relic" => Right(BuryableCard.Relic(RelicId(value("id").str)))
    case "edifice" => Right(BuryableCard.Edifice(EdificeId(value("id").str)))
    case "vision" => Right(BuryableCard.Vision(VisionId(value("id").str)))
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown buryable card '$other'"))
  }

  private def encodeSecretSide(value: SecretSide): String = value match {
    case SecretSide.FaceUp => "face-up"
    case SecretSide.FaceDown => "face-down"
  }

  private def decodeSecretSide(value: String,
      path: String): Either[WireError, SecretSide] = value match {
    case "face-up" => Right(SecretSide.FaceUp)
    case "face-down" => Right(SecretSide.FaceDown)
    case other => Left(InvalidValue(path, s"unknown secret side '$other'"))
  }

  private def encodeDiceKind(kind: DiceKind): String = kind match {
    case DiceKind.Defense => "defense"
    case DiceKind.Attack => "attack"
  }

  private def decodeDiceKind(value: String,
      path: String): Either[WireError, DiceKind] = value match {
    case "defense" => Right(DiceKind.Defense)
    case "attack" => Right(DiceKind.Attack)
    case other => Left(InvalidValue(path, s"unknown dice kind '$other'"))
  }

  private def decodeOptionalSignedInt(value: ujson.Value,
      path: String): Either[WireError, Option[Int]] = value match {
    case ujson.Null => Right(None)
    case other => decodeSignedInt(other, path).map(Some(_))
  }

  /** Total over `Piece` (I8), matching `encodeLocation`'s pattern: every
    * case is real data with a direct encoding, so there is no throwing arm
    * here at all.
    */
  private def encodePiece(piece: Piece): ujson.Value = piece match {
    case Piece.Card(id) => ujson.Obj("kind" -> "card",
      "card" -> encodeCardRef(id))
    case Piece.Banner(banner) => ujson.Obj("kind" -> "banner",
      "banner" -> banner.key)
    case Piece.Pawn(player) => ujson.Obj("kind" -> "pawn",
      "playerId" -> player.value)
    case Piece.Favor(amount) => ujson.Obj("kind" -> "favor",
      "amount" -> amount)
    case Piece.Secrets(amount) => ujson.Obj("kind" -> "secrets",
      "amount" -> amount)
    case Piece.Warbands(force, amount) => ujson.Obj("kind" -> "warbands",
      "force" -> encodeForceKind(force), "amount" -> amount)
  }

  private def decodePiece(value: ujson.Value,
      path: String): Either[WireError, Piece] = value("kind").str match {
    case "card" => decodeCardRef(value("card"), s"$path.card").map(Piece.Card)
    case "banner" => decodeBanner(value("banner").str, s"$path.banner")
      .map(Piece.Banner)
    case "pawn" => Right(Piece.Pawn(PlayerId(value("playerId").str)))
    case "favor" => safeIntField(value.obj, "amount", path).flatMap(amount =>
      if (amount > 0) Right(Piece.Favor(amount))
      else Left(InvalidValue(s"$path.amount", "must be positive")))
    case "secrets" => safeIntField(value.obj, "amount", path).flatMap(amount =>
      if (amount > 0) Right(Piece.Secrets(amount))
      else Left(InvalidValue(s"$path.amount", "must be positive")))
    case "warbands" => for {
      force <- decodeForceKind(value("force"), s"$path.force")
      amount <- safeIntField(value.obj, "amount", path)
      warbands <- if (amount > 0) Right(Piece.Warbands(force, amount))
        else Left(InvalidValue(s"$path.amount", "must be positive"))
    } yield warbands
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown recorded walker piece '$other'"))
  }

  private def encodeStackPosition(value: StackPosition): String = value match {
    case StackPosition.Unspecified => "unspecified"
    case StackPosition.Top => "top"
    case StackPosition.Bottom => "bottom"
  }

  private def decodeStackPosition(value: String,
      path: String): Either[WireError, StackPosition] = value match {
    case "unspecified" => Right(StackPosition.Unspecified)
    case "top" => Right(StackPosition.Top)
    case "bottom" => Right(StackPosition.Bottom)
    case other => Left(InvalidValue(path, s"unknown stack position '$other'"))
  }

  private def encodePositionedLocation(value: PositionedLocation): ujson.Value =
    ujson.Obj("location" -> encodeLocation(value.location),
      "position" -> encodeStackPosition(value.position))

  private def decodePositionedLocation(value: ujson.Value,
      path: String): Either[WireError, PositionedLocation] = for {
    location <- decodeLocation(value("location"), s"$path.location")
    position <- decodeStackPosition(value("position").str, s"$path.position")
  } yield PositionedLocation(location, position)

  /** Total over `Location`, unlike the other encoders here, which stay bounded
    * to the leaf shapes this slice records. A location is a closed sealed
    * hierarchy whose every case is cheap to name, and the bounded form was a
    * production defect rather than a safety margin: Catacombs' relic move out
    * of `Location.Deck` reached this method only through `StartWalker`, the
    * one path no test drove, and turned into an append-time codec failure.
    * Being total means a new `Location` case only warns here ("match may
    * not be exhaustive"), since this project builds without
    * `-Xfatal-warnings`; what actually catches a missed case is the
    * enumeration test "every Location variant round-trips through the
    * walker codec" in `GameEventWireSuite`.
    */
  private def encodeLocation(value: Location): ujson.Value = value match {
    case Location.Site(site) => ujson.Obj("kind" -> "site",
      "siteId" -> site.value)
    case Location.PlayArea(player) => ujson.Obj("kind" -> "play-area",
      "playerId" -> player.value)
    case Location.Hand(player) => ujson.Obj("kind" -> "hand",
      "playerId" -> player.value)
    case Location.OnCard(card) => ujson.Obj("kind" -> "on-card",
      "card" -> encodeCardRef(card))
    case Location.OnBanner(banner) => ujson.Obj("kind" -> "on-banner",
      "banner" -> banner.key)
    case Location.FavorBank(suit) => ujson.Obj("kind" -> "favor-bank",
      "suit" -> suit.key)
    case Location.WarbandBank(force) => ujson.Obj("kind" -> "warband-bank",
      "force" -> encodeForceKind(force))
    case Location.Deck(deck) => ujson.Obj("kind" -> "deck",
      "deck" -> encodeCardDeck(deck))
    case Location.RegionalDiscard(region) => ujson.Obj(
      "kind" -> "regional-discard", "region" -> region.key)
    case Location.SharedBank => ujson.Obj("kind" -> "shared-bank")
    case Location.SetAsideRelics => ujson.Obj("kind" -> "set-aside-relics")
    case Location.Reliquary => ujson.Obj("kind" -> "reliquary")
    case Location.Atlas => ujson.Obj("kind" -> "atlas")
    case Location.Dispossessed => ujson.Obj("kind" -> "dispossessed")
  }

  private def decodeLocation(value: ujson.Value,
      path: String): Either[WireError, Location] = value("kind").str match {
    case "site" => Right(Location.Site(SiteId(value("siteId").str)))
    case "play-area" =>
      Right(Location.PlayArea(PlayerId(value("playerId").str)))
    case "hand" => Right(Location.Hand(PlayerId(value("playerId").str)))
    case "on-card" => decodeCardRef(value("card"), s"$path.card")
      .map(Location.OnCard)
    case "on-banner" => Banner.fromKey(value("banner").str)
      .toRight(InvalidValue(s"$path.banner",
        s"unknown banner '${value("banner").str}'")).map(Location.OnBanner)
    case "favor-bank" => decodeSuit(value("suit").str, s"$path.suit")
      .map(Location.FavorBank)
    case "warband-bank" => decodeForceKind(value("force"), s"$path.force")
      .map(Location.WarbandBank)
    case "deck" => decodeCardDeck(value("deck").str, s"$path.deck")
      .map(Location.Deck)
    case "regional-discard" =>
      decodeRegion(value("region").str, s"$path.region")
        .map(Location.RegionalDiscard)
    case "shared-bank" => Right(Location.SharedBank)
    case "set-aside-relics" => Right(Location.SetAsideRelics)
    case "reliquary" => Right(Location.Reliquary)
    case "atlas" => Right(Location.Atlas)
    case "dispossessed" => Right(Location.Dispossessed)
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown recorded walker location '$other'"))
  }

  private def encodeCardDeck(value: CardDeck): String = value.key

  private def decodeCardDeck(value: String,
      path: String): Either[WireError, CardDeck] =
    CardDeck.fromKey(value).toRight(
      InvalidValue(path, s"unknown card deck '$value'"))

  private def encodeCost(cost: Cost): ujson.Value = ujson.Obj(
    "favor" -> cost.favor, "secret" -> cost.secret,
    "favorBurnt" -> cost.favorBurnt, "secretBurnt" -> cost.secretBurnt)

  /** `safeIntField` already rejects a negative, so `Cost`'s own non-negative
    * `require` can never be reached from decoded JSON. */
  private def decodeCost(value: ujson.Value,
      path: String): Either[WireError, Cost] = for {
    favor <- safeIntField(value.obj, "favor", path)
    secret <- safeIntField(value.obj, "secret", path)
    favorBurnt <- safeIntField(value.obj, "favorBurnt", path)
    secretBurnt <- safeIntField(value.obj, "secretBurnt", path)
  } yield Cost(favor, secret, favorBurnt, secretBurnt)

  private def encodeOrientation(value: Orientation): String = value match {
    case Orientation.FaceUp => "face-up"
    case Orientation.FaceDown => "face-down"
  }

  private def decodeOrientation(value: String,
      path: String): Either[WireError, Orientation] = value match {
    case "face-up" => Right(Orientation.FaceUp)
    case "face-down" => Right(Orientation.FaceDown)
    case other => Left(InvalidValue(path, s"unknown orientation '$other'"))
  }

  protected final def decodeSignedInt(value: ujson.Value,
      path: String): Either[WireError, Int] = value match {
    case ujson.Num(number) if number.isFinite && number == math.rint(number) &&
        number >= Int.MinValue && number <= Int.MaxValue => Right(number.toInt)
    case _ => Left(InvalidValue(path, "expected a signed 32-bit integer"))
  }

  private def decodePositiveInt(value: ujson.Value,
      path: String): Either[WireError, Int] =
    decodeSignedInt(value, path).flatMap { amount =>
      if (amount > 0) Right(amount)
      else Left(InvalidValue(path, "expected a positive integer"))
    }
}
