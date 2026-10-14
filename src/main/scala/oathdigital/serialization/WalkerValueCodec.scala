package oathdigital.serialization

import oathdigital.model._

/** Wire spellings for the values a recorded operation is made of -- pieces,
  * locations, positions, costs, gives, dice kinds, die faces, orientations
  * and bounded integers -- split out of `WalkerOperationCodec` to keep it
  * under the 800-line bound. A pure move: every spelling is byte-identical
  * to the version before the split, which `WalkerOperationWireSuite` pins.
  *
  * Mixed into `WalkerOperationCodec`, which keeps the operation arms. The
  * helpers here are `protected final` rather than `private` because the
  * operation arms and the other sibling traits call them; see the naming
  * note on `orientationKey`/`orientationFromKey` below before adding a
  * method whose name also exists in `GameEventJsonSupport`.
  */
private[serialization] trait WalkerValueCodec extends PositionWire:
  this: GameEventJsonSupport =>
  import WireError._

  protected final def encodeGive(give: Give, kind: String): ujson.Value = ujson.Obj(
    "kind" -> kind, "piece" -> encodePiece(give.piece),
    "giverPlayerId" -> give.giver.value, "from" -> encodeLocation(give.from),
    "to" -> encodeLocation(give.to))

  protected final def decodeGive(value: ujson.Value,
      path: String): Either[WireError, Give] = for
    piece <- decodePiece(value("piece"), s"$path.piece")
    from <- decodeLocation(value("from"), s"$path.from")
    to <- decodeLocation(value("to"), s"$path.to")
  yield Give(piece, PlayerId(value("giverPlayerId").str), from, to)

  protected final def asWarbands(piece: Piece,
      path: String): Either[WireError, Piece.Warbands] = piece match
    case warbands: Piece.Warbands => Right(warbands)
    case other => Left(InvalidValue(path, s"expected warbands, found $other"))

  protected final def encodeBuryableCard(card: BuryableCard): ujson.Value = card match
    case BuryableCard.Denizen(id) => ujson.Obj("kind" -> "denizen",
      "id" -> id.value)
    case BuryableCard.Relic(id) => ujson.Obj("kind" -> "relic",
      "id" -> id.value)
    case BuryableCard.Edifice(id) => ujson.Obj("kind" -> "edifice",
      "id" -> id.value)
    case BuryableCard.Vision(id) => ujson.Obj("kind" -> "vision",
      "id" -> id.value)

  protected final def decodeBuryableCard(value: ujson.Value,
      path: String): Either[WireError, BuryableCard] = value("kind").str match
    case "denizen" => Right(BuryableCard.Denizen(DenizenId(value("id").str)))
    case "relic" => Right(BuryableCard.Relic(RelicId(value("id").str)))
    case "edifice" => Right(BuryableCard.Edifice(EdificeId(value("id").str)))
    case "vision" => Right(BuryableCard.Vision(VisionId(value("id").str)))
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown buryable card '$other'"))

  protected final def encodeDiceKind(kind: DiceKind): String = kind match
    case DiceKind.Defense => "defense"
    case DiceKind.Attack => "attack"

  protected final def decodeDiceKind(value: String,
      path: String): Either[WireError, DiceKind] = value match
    case "defense" => Right(DiceKind.Defense)
    case "attack" => Right(DiceKind.Attack)
    case other => Left(InvalidValue(path, s"unknown dice kind '$other'"))

  protected final def decodeOptionalSignedInt(value: ujson.Value,
      path: String): Either[WireError, Option[Int]] = value match
    case ujson.Null => Right(None)
    case other => decodeSignedInt(other, path).map(Some(_))

  /** Total over `Piece` (I8), matching `encodeLocation`'s pattern: every
    * case is real data with a direct encoding, so there is no throwing arm
    * here at all.
    */
  protected final def encodePiece(piece: Piece): ujson.Value = piece match
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

  protected final def decodePiece(value: ujson.Value,
      path: String): Either[WireError, Piece] = value("kind").str match
    case "card" => decodeCardRef(value("card"), s"$path.card").map(Piece.Card.apply)
    case "banner" => decodeBanner(value("banner").str, s"$path.banner")
      .map(Piece.Banner.apply)
    case "pawn" => Right(Piece.Pawn(PlayerId(value("playerId").str)))
    case "favor" => safeIntField(value.obj, "amount", path).flatMap(amount =>
      if amount > 0 then Right(Piece.Favor(amount))
      else Left(InvalidValue(s"$path.amount", "must be positive")))
    case "secrets" => safeIntField(value.obj, "amount", path).flatMap(amount =>
      if amount > 0 then Right(Piece.Secrets(amount))
      else Left(InvalidValue(s"$path.amount", "must be positive")))
    case "warbands" =>
      for
        force <- decodeForceKind(value("force"), s"$path.force")
        amount <- safeIntField(value.obj, "amount", path)
        warbands <- if amount > 0 then Right(Piece.Warbands(force, amount))
          else Left(InvalidValue(s"$path.amount", "must be positive"))
      yield warbands
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown recorded walker piece '$other'"))

  protected final def encodeStackPosition(value: StackPosition): String = value match
    case StackPosition.Unspecified => "unspecified"
    case StackPosition.Top => "top"
    case StackPosition.Bottom => "bottom"

  protected final def decodeStackPosition(value: String,
      path: String): Either[WireError, StackPosition] = value match
    case "unspecified" => Right(StackPosition.Unspecified)
    case "top" => Right(StackPosition.Top)
    case "bottom" => Right(StackPosition.Bottom)
    case other => Left(InvalidValue(path, s"unknown stack position '$other'"))

  protected final def encodePositionedLocation(value: PositionedLocation): ujson.Value =
    ujson.Obj.from(Vector[(String, ujson.Value)](
      "location" -> encodeLocation(value.location),
      "position" -> encodeStackPosition(value.position)) ++
      sideField("side", value.side))

  protected final def decodePositionedLocation(value: ujson.Value,
      path: String): Either[WireError, PositionedLocation] = for
    location <- decodeLocation(value("location"), s"$path.location")
    position <- decodeStackPosition(value("position").str, s"$path.position")
    side <- decodeSide(value, "side", path)
  yield PositionedLocation(location, position, side)

  /** Total over `Location`, unlike the other encoders here, which stay bounded
    * to the leaf shapes this slice records. A location is a closed sealed
    * hierarchy whose every case is cheap to name, and the bounded form was a
    * production defect rather than a safety margin: Catacombs' relic move out
    * of `Location.Deck` reached this method only through a walker start, the
    * one path no test drove, and turned into an append-time codec failure.
    * Being total means a new `Location` case only warns here ("match may
    * not be exhaustive"), since this project builds without
    * `-Xfatal-warnings`; what actually catches a missed case is the
    * enumeration test "every Location variant round-trips through the
    * walker codec" in `WalkerOperationWireSuite`.
    */
  protected final def encodeLocation(value: Location): ujson.Value = value match
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

  protected final def decodeLocation(value: ujson.Value,
      path: String): Either[WireError, Location] = value("kind").str match
    case "site" => Right(Location.Site(SiteId(value("siteId").str)))
    case "play-area" =>
      Right(Location.PlayArea(PlayerId(value("playerId").str)))
    case "hand" => Right(Location.Hand(PlayerId(value("playerId").str)))
    case "on-card" => decodeCardRef(value("card"), s"$path.card")
      .map(Location.OnCard.apply)
    case "on-banner" => Banner.fromKey(value("banner").str)
      .toRight(InvalidValue(s"$path.banner",
        s"unknown banner '${value("banner").str}'")).map(Location.OnBanner.apply)
    case "favor-bank" => decodeSuit(value("suit").str, s"$path.suit")
      .map(Location.FavorBank.apply)
    case "warband-bank" => decodeForceKind(value("force"), s"$path.force")
      .map(Location.WarbandBank.apply)
    case "deck" => decodeCardDeck(value("deck").str, s"$path.deck")
      .map(Location.Deck.apply)
    case "regional-discard" =>
      decodeRegion(value("region").str, s"$path.region")
        .map(Location.RegionalDiscard.apply)
    case "shared-bank" => Right(Location.SharedBank)
    case "set-aside-relics" => Right(Location.SetAsideRelics)
    case "reliquary" => Right(Location.Reliquary)
    case "atlas" => Right(Location.Atlas)
    case "dispossessed" => Right(Location.Dispossessed)
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown recorded walker location '$other'"))

  protected final def encodeCardDeck(value: CardDeck): String = value.key

  protected final def decodeCardDeck(value: String,
      path: String): Either[WireError, CardDeck] =
    CardDeck.fromKey(value).toRight(
      InvalidValue(path, s"unknown card deck '$value'"))

  protected final def encodeCost(cost: Cost): ujson.Value = ujson.Obj(
    "favor" -> cost.favor, "secret" -> cost.secret,
    "favorBurnt" -> cost.favorBurnt, "secretBurnt" -> cost.secretBurnt)

  /** `safeIntField` already rejects a negative, so `Cost`'s own non-negative
    * `require` can never be reached from decoded JSON. */
  protected final def decodeCost(value: ujson.Value,
      path: String): Either[WireError, Cost] = for
    favor <- safeIntField(value.obj, "favor", path)
    secret <- safeIntField(value.obj, "secret", path)
    favorBurnt <- safeIntField(value.obj, "favorBurnt", path)
    secretBurnt <- safeIntField(value.obj, "secretBurnt", path)
  yield Cost(favor, secret, favorBurnt, secretBurnt)

  // Neither of these may be named encode/decodeOrientation: under Scala 3 a
  // same-named private method here was dispatched in place of
  // GameEventJsonSupport's, which broke encoding of Flip and Play
  // operations. These take a String key where GameEventJsonSupport's pair
  // takes a ujson.Value, so the two were an overload that resolved only by
  // argument type; the split widened these to protected and merged the
  // overload sets, so they carry distinct names instead.
  protected final def orientationKey(value: Orientation): String = value match
    case Orientation.FaceUp => "face-up"
    case Orientation.FaceDown => "face-down"

  protected final def orientationFromKey(value: String,
      path: String): Either[WireError, Orientation] = value match
    case "face-up" => Right(Orientation.FaceUp)
    case "face-down" => Right(Orientation.FaceDown)
    case other => Left(InvalidValue(path, s"unknown orientation '$other'"))

  protected final def encodeDieFace(face: DieFace): ujson.Value = face match
    case face: DefenseDieFace => ujson.Str(encodeDefenseFace(face))
    case face: AttackDieFace => ujson.Str(encodeAttackFace(face))
    case other => throw new IllegalArgumentException(
      s"unsupported walker die face $other")

  protected final def decodeDieFace(value: String, path: String)
      : Either[WireError, DieFace] = value match
    case "hollow-sword" | "one-sword" | "two-swords-skull" =>
      decodeAttackFace(value, path)
    case _ => decodeDefenseFace(value, path)

  protected final def decodeSignedInt(value: ujson.Value,
      path: String): Either[WireError, Int] = value match
    case ujson.Num(number) if number.isFinite && number == math.rint(number) &&
        number >= Int.MinValue && number <= Int.MaxValue => Right(number.toInt)
    case _ => Left(InvalidValue(path, "expected a signed 32-bit integer"))

  protected final def decodePositiveInt(value: ujson.Value,
      path: String): Either[WireError, Int] =
    decodeSignedInt(value, path).flatMap { amount =>
      if amount > 0 then Right(amount)
      else Left(InvalidValue(path, "expected a positive integer"))
    }
