package oathdigital.serialization

import oathdigital.model._

/** Operation spellings for recorded walker steps, split out of
  * `WalkerEventCodec` to give it headroom under the 800-line bound.
  *
  * A pure move: every spelling is byte-identical to the version before the
  * split, which `WalkerOperationWireSuite` pins.
  *
  * `encodeOperation`/`encodePiece` (I8) are total over `CoreOperation`/
  * `Piece`, exactly like [[encodeLocation]] in [[WalkerValueCodec]] (added
  * for the same reason: a bounded operation set is what let Catacombs'
  * recorded `Move` out of `Location.Deck` reach an append-time throw with no
  * test to catch it -- that lesson generalises to every recorded shape, not
  * just `Location`). Totality is now exceptionless: the walker's control
  * vocabulary lives under the unsealed `Operation`, not under
  * `CoreOperation`, so a control node is not a member of the encoded family
  * and cannot reach this method. A NEW `CoreOperation` case still fails to
  * compile here ("match may not be exhaustive") until it is given a real
  * arm.
  */
private[serialization] trait WalkerOperationCodec extends CampaignResultCodec
    with FollowUpWire with OathkeeperWire with PositionWire
    with WalkerValueCodec:
  this: GameEventJsonSupport =>
  import WireError._

  /** The `CoreOperation` cases each encode/decode helper below owns. The
    * compiler skips coverage instrumentation for a method body over 3000
    * tree nodes, so a single `encodeOperation`/`decodeOperation` match was
    * never measured; four smaller helpers are. Every group is a union of
    * concrete cases, so `encodeOperation` still fails to compile when a
    * `CoreOperation` case belongs to no group.
    */
  private type CardOperation =
    Peek | Flip | Bury | Discard | Draw | Play | Reveal | Swap | Shuffle
  private type PieceOperation =
    Move | Take | Kill | Replace | Sacrifice | Burn | Give | Exchange | PayCost
  private type ResourceOperation = SpendSupply | GainSupply | FlipSecrets | Gain
  private type TurnOperation = AdvanceVisionsDrawn.type | BeginTurn | EnterPhase |
    SetOathkeeper | SetOathkeeperGoal | RecordPowerUse | RecordCampaignResult |
    ModifyDicePool | Roll | ModifyRollOutcome | Reroll | SetDieFaces |
    QueueProcedure | Defer | TakeDeferred | ActAsIf

  protected final def encodeOperation(operation: CoreOperation): ujson.Value =
    operation match
      case operation: CardOperation => encodeCardOperation(operation)
      case operation: PieceOperation => encodePieceOperation(operation)
      case operation: ResourceOperation => encodeResourceOperation(operation)
      case operation: TurnOperation => encodeTurnOperation(operation)

  private def encodeCardOperation(operation: CardOperation): ujson.Value =
    operation match
      case Peek(viewer, card, at) => ujson.Obj("kind" -> "peek",
        "viewerPlayerId" -> viewer.value, "card" -> encodeCardRef(card),
        "at" -> encodeLocation(at))
      case Flip(card, at, orientation) => ujson.Obj("kind" -> "flip",
        "card" -> encodeCardRef(card), "at" -> encodeLocation(at),
        "orientation" -> orientationKey(orientation))
      case bury: Bury => ujson.Obj("kind" -> "bury",
        "card" -> encodeBuryableCard(bury.card),
        "from" -> encodePositionedLocation(bury.from))
      case Discard.Denizen(card, from, to, suit, favor, secrets, actingPlayer,
          _, actorless) =>
        withActorless(ujson.Obj("kind" -> "discard-denizen",
          "card" -> card.value,
          "from" -> encodePositionedLocation(from), "to" -> to.key,
          "suit" -> suit.key, "favor" -> favor, "secrets" -> secrets,
          "actingPlayerId" -> actingPlayer.value), actorless)
      case Discard.Vision(card, from, to, _) => ujson.Obj(
        "kind" -> "discard-vision", "card" -> card.value,
        "from" -> encodePositionedLocation(from), "to" -> to.key)
      case Discard.RuinedEdifice(card, from, suit, favor, secrets,
          actingPlayer, _, actorless) =>
        withActorless(ujson.Obj("kind" -> "discard-ruined-edifice",
          "card" -> card.value,
          "from" -> encodePositionedLocation(from), "suit" -> suit.key,
          "favor" -> favor, "secrets" -> secrets,
          "actingPlayerId" -> actingPlayer.value), actorless)
      case Discard.Relic(card, from, secrets, actingPlayer) => ujson.Obj(
        "kind" -> "discard-relic", "card" -> card.value,
        "from" -> encodePositionedLocation(from), "secrets" -> secrets,
        "actingPlayerId" -> actingPlayer.value)
      case Draw(player, cards, source, destination, position) => ujson.Obj(
        "kind" -> "draw", "playerId" -> player.value,
        "cards" -> ujson.Arr.from(cards.map(encodeCardRef)),
        "source" -> encodeLocation(source),
        "destination" -> encodeLocation(destination),
        "position" -> encodeStackPosition(position))
      case Play(card, from, destination, orientation, _) => ujson.Obj(
        "kind" -> "play", "card" -> encodeCardRef(card),
        "from" -> encodePositionedLocation(from),
        "destination" -> encodeLocation(destination),
        "orientation" -> orientationKey(orientation))
      case Reveal(card, at) => ujson.Obj("kind" -> "reveal",
        "card" -> encodeCardRef(card), "at" -> encodeLocation(at))
      case Swap(firstCard, firstLocation, secondCard, secondLocation, by) =>
        withMover(ujson.Obj("kind" -> "swap",
          "firstCard" -> encodeCardRef(firstCard),
          "firstLocation" -> encodePositionedLocation(firstLocation),
          "secondCard" -> encodeCardRef(secondCard),
          "secondLocation" -> encodePositionedLocation(secondLocation)), by)
      case Shuffle(pile, order) =>
        val shuffled = ujson.Obj("kind" -> "shuffle",
          "pile" -> encodeSearchSource(pile))
        order.foreach(cards =>
          shuffled("order") = ujson.Arr.from(cards.map(encodeWorldCardId)))
        shuffled

  /** `encoded`, with `"by"` when the move names its mover. Written only
    * when present, so a move without one encodes as before. */
  private def withMover(encoded: ujson.Obj, by: Option[PlayerId]): ujson.Obj =
    by.foreach(player => encoded("by") = player.value)
    encoded

  /** `encoded`, with `"actorless": true` when nobody performs the
    * discard. Written only then, so other discards encode as before. */
  private def withActorless(encoded: ujson.Obj, actorless: Boolean)
      : ujson.Obj =
    if actorless then encoded("actorless") = true
    encoded

  /** Whether a discard says nobody performs it. */
  private def decodeActorless(value: ujson.Value): Boolean =
    value.obj.get("actorless").exists(_.bool)

  /** The mover a move or swap names, if any. */
  private def decodeMover(value: ujson.Value): Option[PlayerId] =
    value.obj.get("by").map(by => PlayerId(by.str))

  private def encodePieceOperation(operation: PieceOperation): ujson.Value =
    operation match
      case Move(piece, from, to, orientation, by) => withMover(ujson.Obj(
        "kind" -> "move",
        "piece" -> encodePiece(piece),
        "from" -> encodePositionedLocation(from),
        "to" -> encodePositionedLocation(to),
        "resultingOrientation" -> orientation.fold[ujson.Value](ujson.Null)(
          value => ujson.Str(orientationKey(value)))), by)
      case Take(piece, player, from, to, sourcePosition, _, leaving,
          sourceSide) =>
        // Written only when present, so a Take without it encodes as before.
        val optional: Vector[(String, ujson.Value)] =
          if leaving.isEmpty then Vector.empty
          else Vector("leaving" -> ujson.Arr.from(leaving.map(encodeOperation)))
        ujson.Obj.from(Vector[(String, ujson.Value)](
          "kind" -> "take", "piece" -> encodePiece(piece),
          "playerId" -> player.value, "from" -> encodeLocation(from),
          "to" -> encodeLocation(to),
          "sourcePosition" -> encodeStackPosition(sourcePosition)) ++
          optional ++ sideField("sourceSide", sourceSide))
      case Kill(warbands, from) => ujson.Obj("kind" -> "kill",
        "warbands" -> encodePiece(warbands),
        "from" -> encodePositionedLocation(from))
      case Replace(removed, replacements, at, _) => ujson.Obj(
        "kind" -> "replace", "removed" -> encodePiece(removed),
        "replacements" -> encodePiece(replacements),
        "at" -> encodePositionedLocation(at))
      case Sacrifice(player, warbands, from) => ujson.Obj(
        "kind" -> "sacrifice", "playerId" -> player.value,
        "warbands" -> encodePiece(warbands),
        "from" -> encodePositionedLocation(from))
      case burn: Burn => ujson.Obj("kind" -> "burn",
        "resource" -> encodePiece(burn.resource),
        "from" -> encodePositionedLocation(burn.from),
        "by" -> burn.by.fold[ujson.Value](ujson.Null)(
          by => ujson.Str(by.value)))
      case give: Give => encodeGive(give, "give")
      case Exchange(give, receive) => ujson.Obj("kind" -> "exchange",
        "give" -> encodeGive(give, "exchange-give"),
        "receive" -> encodeGive(receive, "exchange-receive"))
      case PayCost(player, placedAt, cost, intoOccupied, matchingBank, _) =>
        val optional: Vector[(String, ujson.Value)] =
          (if intoOccupied then Vector("intoOccupied" -> (ujson.Bool(true): ujson.Value))
          else Vector.empty) ++
            matchingBank.toVector.map(suit =>
              "matchingBank" -> (ujson.Str(suit.key): ujson.Value))
        ujson.Obj.from(Vector[(String, ujson.Value)](
          "kind" -> "pay-cost", "playerId" -> player.value,
          "placedAt" -> encodeLocation(placedAt),
          "cost" -> encodeCost(cost)) ++ optional)

  private def encodeResourceOperation(operation: ResourceOperation): ujson.Value =
    operation match
      case SpendSupply(player, amount, _) => ujson.Obj(
        "kind" -> "spend-supply", "playerId" -> player.value,
        "amount" -> amount)
      case GainSupply(player, amount) => ujson.Obj(
        "kind" -> "gain-supply", "playerId" -> player.value,
        "amount" -> amount)
      case FlipSecrets(player, amount, from, to) => ujson.Obj(
        "kind" -> "flip-secrets", "playerId" -> player.value,
        "amount" -> amount, "from" -> encodeSecretSide(from),
        "to" -> encodeSecretSide(to))
      case Gain.Favor(player, suit, amount) => ujson.Obj(
        "kind" -> "gain-favor", "playerId" -> player.value,
        "suit" -> suit.key, "amount" -> amount)
      case Gain.Secrets(player, amount) => ujson.Obj(
        "kind" -> "gain-secrets", "playerId" -> player.value,
        "amount" -> amount)
      case Gain.Warbands(player, kind, amount) => ujson.Obj(
        "kind" -> "gain-warbands", "playerId" -> player.value,
        "force" -> encodeForceKind(kind), "amount" -> amount)

  private def encodeTurnOperation(operation: TurnOperation): ujson.Value =
    operation match
      case AdvanceVisionsDrawn => ujson.Obj("kind" -> "advance-visions-drawn")
      case BeginTurn(player, phase) => ujson.Obj("kind" -> "begin-turn",
        "playerId" -> player.value, "phase" -> phase.key)
      case EnterPhase(phase) => ujson.Obj("kind" -> "enter-phase",
        "phase" -> phase.key)
      case operation: SetOathkeeper => encodeSetOathkeeper(operation)
      case operation: SetOathkeeperGoal => encodeSetOathkeeperGoal(operation)
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
      case RecordCampaignResult(result) => ujson.Obj(
        "kind" -> "record-campaign-result",
        "result" -> encodeCampaignResult(result))
      case QueueProcedure(entry, atFront) => ujson.Obj(
        "kind" -> "queue-procedure",
        "entry" -> encodeQueued(entry),
        "atFront" -> ujson.Bool(atFront))
      case ModifyDicePool(pool, delta, _) => ujson.Obj(
        "kind" -> "modify-dice-pool", "pool" -> pool.value,
        "delta" -> delta)
      case Roll(pool, dice, _, _) => ujson.Obj("kind" -> "roll",
        "pool" -> pool.value, "die" -> encodeDiceKind(dice.die))
      case ModifyRollOutcome(pool, skulls, score) => ujson.Obj(
        "kind" -> "modify-roll-outcome", "pool" -> pool.value,
        "skulls" -> skulls.fold[ujson.Value](ujson.Null)(ujson.Num(_)),
        "score" -> score.fold[ujson.Value](ujson.Null)(ujson.Num(_)))
      case Reroll(pool, indices, faces) =>
        val rerolled = ujson.Obj("kind" -> "reroll", "pool" -> pool.value,
          "indices" -> ujson.Arr.from(indices.map(ujson.Num(_))))
        faces.foreach(rolled =>
          rerolled("faces") = ujson.Arr.from(rolled.map(encodeDieFace)))
        rerolled
      case SetDieFaces(pool, faces) => ujson.Obj(
        "kind" -> "set-die-faces", "pool" -> pool.value,
        "faces" -> ujson.Arr.from(faces.map(encodeDieFace)))
      // A held op is spelled as a recorded one: it is held canonically.
      case Defer(key, ops) => ujson.Obj("kind" -> "defer",
        "key" -> key.value, "ops" -> ujson.Arr.from(ops.map(encodeOperation)))
      case TakeDeferred(key) => ujson.Obj("kind" -> "take-deferred",
        "key" -> key.value)
      case ActAsIf(site) => ujson.Obj("kind" -> "act-as-if",
        "siteId" -> site.value)

  private def encodePowerTiming(timing: PowerTiming): String = timing match
    case PowerTiming.Wake => "wake"
    case PowerTiming.Act => "act"
    case PowerTiming.Rest => "rest"

  private def decodePowerTiming(value: String,
      path: String): Either[WireError, PowerTiming] = value match
    case "wake" => Right(PowerTiming.Wake)
    case "act" => Right(PowerTiming.Act)
    case "rest" => Right(PowerTiming.Rest)
    case other => Left(InvalidValue(path, s"unknown power timing '$other'"))

  private def decodePowerCard(kind: String, id: String,
      path: String): Either[WireError, CardId] = kind match
    case "denizen" => Right(DenizenId(id))
    case "relic" => Right(RelicId(id))
    case "edifice" => Right(EdificeId(id))
    case "vision" => Right(VisionId(id))
    case "legacy" => Right(LegacyId(id))
    case other => Left(InvalidValue(path, s"unknown power source card '$other'"))

  private type DecodedOperation = PartialFunction[String, Either[WireError, CoreOperation]]

  protected final def decodeOperation(value: ujson.Value,
      path: String): Either[WireError, CoreOperation] =
    decodeCardOperation(value, path)
      .orElse(decodePieceOperation(value, path))
      .orElse(decodeResourceOperation(value, path))
      .orElse(decodeTurnOperation(value, path))
      .applyOrElse(value("kind").str, (other: String) => Left(InvalidValue(
        s"$path.kind", s"unknown recorded walker operation '$other'")))

  private def decodeCardOperation(value: ujson.Value,
      path: String): DecodedOperation =
    case "peek" => for
      card <- decodeCardRef(value("card"), s"$path.card")
      at <- decodeLocation(value("at"), s"$path.at")
    yield Peek(PlayerId(value("viewerPlayerId").str), card, at)
    case "flip" => for
      card <- decodeCardRef(value("card"), s"$path.card")
      at <- decodeLocation(value("at"), s"$path.at")
      orientation <- orientationFromKey(value("orientation").str,
        s"$path.orientation")
    yield Flip(card, at, orientation)
    case "bury" => for
      card <- decodeBuryableCard(value("card"), s"$path.card")
      from <- decodePositionedLocation(value("from"), s"$path.from")
    yield Bury(card, from)
    case "discard-denizen" => for
      from <- decodePositionedLocation(value("from"), s"$path.from")
      to <- decodeRegion(value("to").str, s"$path.to")
      suit <- decodeSuit(value("suit").str, s"$path.suit")
      favor <- safeIntField(value.obj, "favor", path)
      secrets <- safeIntField(value.obj, "secrets", path)
    yield Discard.Denizen(DenizenId(value("card").str), from, to, suit,
      favor, secrets, PlayerId(value("actingPlayerId").str),
      actorless = decodeActorless(value))
    case "discard-vision" => for
      from <- decodePositionedLocation(value("from"), s"$path.from")
      to <- decodeRegion(value("to").str, s"$path.to")
    yield Discard.Vision(VisionId(value("card").str), from, to)
    case "discard-ruined-edifice" => for
      from <- decodePositionedLocation(value("from"), s"$path.from")
      suit <- decodeSuit(value("suit").str, s"$path.suit")
      favor <- safeIntField(value.obj, "favor", path)
      secrets <- safeIntField(value.obj, "secrets", path)
    yield Discard.RuinedEdifice(EdificeId(value("card").str), from, suit,
      favor, secrets, PlayerId(value("actingPlayerId").str),
      actorless = decodeActorless(value))
    case "discard-relic" => for
      from <- decodePositionedLocation(value("from"), s"$path.from")
      secrets <- safeIntField(value.obj, "secrets", path)
    yield Discard.Relic(RelicId(value("card").str), from, secrets,
      PlayerId(value("actingPlayerId").str))
    case "draw" => for
      cards <- traverse(value("cards").arr.zipWithIndex.toVector):
        case (id, index) => decodeCardRef(id, s"$path.cards[$index]")
      _ <- Either.cond(cards.nonEmpty, (), InvalidValue(s"$path.cards",
        "draw must contain at least one card"))
      source <- decodeLocation(value("source"), s"$path.source")
      destination <- decodeLocation(value("destination"), s"$path.destination")
      position <- value.obj.get("position").fold[Either[WireError,
          StackPosition]](Right(StackPosition.Top))(found =>
        decodeStackPosition(found.str, s"$path.position"))
      _ <- Either.cond(position != StackPosition.Unspecified, (),
        InvalidValue(s"$path.position",
          "a draw takes from the top or the bottom"))
    yield Draw(PlayerId(value("playerId").str), cards, source, destination,
      position)
    case "play" => for
      card <- decodeCardRef(value("card"), s"$path.card")
      from <- decodePositionedLocation(value("from"), s"$path.from")
      destination <- decodeLocation(value("destination"), s"$path.destination")
      _ <- destination match
        case _: Location.Site | _: Location.PlayArea => Right(())
        case other => Left(InvalidValue(s"$path.destination",
          s"play destination must be a site or a play area, got $other"))
      orientation <- orientationFromKey(value("orientation").str,
        s"$path.orientation")
    yield Play(card, from, destination, orientation)
    case "reveal" => for
      card <- decodeCardRef(value("card"), s"$path.card")
      at <- decodeLocation(value("at"), s"$path.at")
    yield Reveal(card, at)
    case "swap" => for
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
    yield Swap(firstCard, firstLocation, secondCard, secondLocation,
      decodeMover(value))
    case "shuffle" => for
      pile <- decodeSearchSource(value("pile"), s"$path.pile")
      order <- decodeShuffleOrder(value, path)
    yield Shuffle(pile, order)

  /** A recorded shuffle carries its order; a declared one has none. */
  private def decodeShuffleOrder(value: ujson.Value, path: String)
      : Either[WireError, Option[Vector[WorldCardId]]] =
    value.obj.get("order").fold[Either[WireError, Option[Vector[WorldCardId]]]](
      Right(None))(cards => traverse(cards.arr.zipWithIndex.toVector)({
        case (card, index) => decodeWorldCardId(card, s"$path.order[$index]")
      }).map(Some(_)))

  /** A recorded reroll carries its faces; a declared one has none. */
  private def decodeRerolledFaces(value: ujson.Value, path: String)
      : Either[WireError, Option[Vector[DieFace]]] =
    value.obj.get("faces").fold[Either[WireError, Option[Vector[DieFace]]]](
      Right(None))(faces => traverse(faces.arr.toVector)(face =>
        decodeDieFace(face.str, s"$path.faces")).map(Some(_)))

  private def decodePieceOperation(value: ujson.Value,
      path: String): DecodedOperation =
    case "move" => for
      piece <- decodePiece(value("piece"), s"$path.piece")
      from <- decodePositionedLocation(value("from"), s"$path.from")
      to <- decodePositionedLocation(value("to"), s"$path.to")
      orientation <- value("resultingOrientation") match
        case ujson.Null => Right(None)
        case other => orientationFromKey(other.str,
          s"$path.resultingOrientation").map(Some(_))
    yield Move(piece, from, to, orientation, decodeMover(value))
    case "take" => for
      piece <- decodePiece(value("piece"), s"$path.piece")
      from <- decodeLocation(value("from"), s"$path.from")
      to <- decodeLocation(value("to"), s"$path.to")
      sourcePosition <- decodeStackPosition(value("sourcePosition").str,
        s"$path.sourcePosition")
      leaving <- value.obj.get("leaving").fold[Either[WireError,
          Vector[CoreOperation]]](Right(Vector.empty))(raw =>
        traverse(raw.arr.zipWithIndex.toVector)({ case (operation, index) =>
          decodeOperation(operation, s"$path.leaving[$index]") }))
      sourceSide <- decodeSide(value, "sourceSide", path)
    yield Take(piece, PlayerId(value("playerId").str), from, to,
      sourcePosition, leaving = leaving, sourceSide = sourceSide)
    case "kill" => for
      piece <- decodePiece(value("warbands"), s"$path.warbands")
      warbands <- asWarbands(piece, s"$path.warbands")
      from <- decodePositionedLocation(value("from"), s"$path.from")
    yield Kill(warbands, from)
    case "replace" => for
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
    yield Replace(removed, replacements, at)
    case "sacrifice" => for
      piece <- decodePiece(value("warbands"), s"$path.warbands")
      warbands <- asWarbands(piece, s"$path.warbands")
      from <- decodePositionedLocation(value("from"), s"$path.from")
    yield Sacrifice(PlayerId(value("playerId").str), warbands, from)
    case "burn" => for
      resource <- decodePiece(value("resource"), s"$path.resource")
      from <- decodePositionedLocation(value("from"), s"$path.from")
      by = value("by").strOpt.map(PlayerId(_))
      burn <- resource match
        case Piece.Favor(amount) => Right(Burn.favor(amount, from, by))
        case Piece.Secrets(amount) => Right(Burn.secrets(amount, from, by))
        case other => Left(InvalidValue(s"$path.resource",
          s"unsupported burn resource $other"))
    yield burn
    case "give" => decodeGive(value, path)
    case "exchange" => for
      give <- decodeGive(value("give"), s"$path.give")
      receive <- decodeGive(value("receive"), s"$path.receive")
    yield Exchange(give, receive)
    case "pay-cost" => for
      placedAt <- decodeLocation(value("placedAt"), s"$path.placedAt")
      cost <- decodeCost(value("cost"), s"$path.cost")
      bank <- value.obj.get("matchingBank") match
        case None | Some(ujson.Null) => Right(None)
        case Some(raw) => decodeSuit(raw.str, s"$path.matchingBank").map(Some(_))
    yield PayCost(PlayerId(value("playerId").str), placedAt, cost,
      intoOccupied = value.obj.get("intoOccupied").exists(_.bool),
      matchingBank = bank)

  private def decodeResourceOperation(value: ujson.Value,
      path: String): DecodedOperation =
    case "spend-supply" => decodePositiveInt(value("amount"), s"$path.amount")
      .map(amount => SpendSupply(PlayerId(value("playerId").str), amount))
    case "gain-supply" => decodePositiveInt(value("amount"), s"$path.amount")
      .map(amount => GainSupply(PlayerId(value("playerId").str), amount))
    case "flip-secrets" => for
      amount <- safeIntField(value.obj, "amount", path)
      from <- decodeSecretSide(value("from").str, s"$path.from")
      to <- decodeSecretSide(value("to").str, s"$path.to")
    yield FlipSecrets(PlayerId(value("playerId").str), amount, from, to)
    case "gain-favor" => for
      amount <- safeIntField(value.obj, "amount", path)
      suit <- decodeSuit(value("suit").str, s"$path.suit")
    yield Gain.Favor(PlayerId(value("playerId").str), suit, amount)
    case "gain-secrets" => safeIntField(value.obj, "amount", path).map(
      amount => Gain.Secrets(PlayerId(value("playerId").str), amount))
    case "gain-warbands" => for
      force <- decodeForceKind(value("force"), s"$path.force")
      amount <- safeIntField(value.obj, "amount", path)
    yield Gain.Warbands(PlayerId(value("playerId").str), force, amount)

  private def decodeTurnOperation(value: ujson.Value,
      path: String): DecodedOperation =
    case "advance-visions-drawn" => Right(AdvanceVisionsDrawn)
    case "begin-turn" =>
      val key = value("phase").str
      Phase.fromKey(key).toRight(
        InvalidValue(s"$path.phase", s"unknown phase '$key'"))
        .map(BeginTurn(PlayerId(value("playerId").str), _))
    case "enter-phase" =>
      val key = value("phase").str
      Phase.fromKey(key).toRight(
        InvalidValue(s"$path.phase", s"unknown phase '$key'"))
        .map(EnterPhase.apply)
    case "set-oathkeeper" => Right(decodeSetOathkeeper(value))
    case "set-oathkeeper-goal" => decodeSetOathkeeperGoal(value, path)
    case "record-power-use" => for
      timing <- decodePowerTiming(value("timing").str, s"$path.timing")
      source <- (if value.obj.contains("siteId") then
          Right(PowerSourceRef.Site(SiteId(value("siteId").str)))
        else if value.obj.contains("bannerKey") then
          Banner.fromKey(value("bannerKey").str).map(PowerSourceRef.Banner(_))
            .toRight(InvalidValue(s"$path.bannerKey", "unknown banner"))
        else decodePowerCard(value("cardKind").str, value("cardId").str,
          s"$path.cardKind").map(PowerSourceRef.Card.apply)
        ): Either[WireError, PowerSourceRef]
    yield RecordPowerUse(PowerUseRef(timing, source,
      PowerId(value("powerId").str)))
    case "record-campaign-result" =>
      decodeCampaignResult(value("result"), s"$path.result")
        .map(RecordCampaignResult(_))
    case "queue-procedure" =>
      decodeQueued(value("entry"), s"$path.entry")
        .map(QueueProcedure(_, value("atFront").bool))
    case "modify-dice-pool" =>
      decodeSignedInt(value("delta"), s"$path.delta")
        .map(delta => ModifyDicePool(PoolKey(value("pool").str), delta))
    case "roll" => decodeDiceKind(value("die").str, s"$path.die").map(die =>
      Roll(PoolKey(value("pool").str), DiceSpec(die)))
    case "modify-roll-outcome" => for
      skulls <- decodeOptionalSignedInt(value("skulls"), s"$path.skulls")
      score <- decodeOptionalSignedInt(value("score"), s"$path.score")
    yield ModifyRollOutcome(PoolKey(value("pool").str), skulls, score)
    case "reroll" => for
      indices <- traverse(value("indices").arr.zipWithIndex.toVector)({
        case (index, at) => decodeSignedInt(index, s"$path.indices[$at]") })
      faces <- decodeRerolledFaces(value, path)
    yield Reroll(PoolKey(value("pool").str), indices, faces)
    case "set-die-faces" => traverse(value("faces").arr.toVector)(face =>
      decodeDieFace(face.str, s"$path.faces")).map(faces =>
      SetDieFaces(PoolKey(value("pool").str), faces))
    case "defer" =>
      traverse(value("ops").arr.zipWithIndex.toVector)({
        case (operation, index) =>
          decodeOperation(operation, s"$path.ops[$index]") })
        .map(Defer(DeferralKey(value("key").str), _))
    case "take-deferred" =>
      Right(TakeDeferred(DeferralKey(value("key").str)))
    case "act-as-if" => Right(ActAsIf(SiteId(value("siteId").str)))
