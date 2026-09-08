package oathdigital.protocol

private[protocol] object CommandNestedCodecs {
  import CommandJsonSupport._
  import ProtocolDecodeFailure._

  def encodeNegotiation(value: NegotiationTerms): ujson.Obj = ujson.Obj(
    "transfers" -> ujson.Arr.from(value.transfers.map { row => ujson.Obj(
      "recipientPlayerId" -> row.recipientPlayerId, "favor" -> row.favor,
      "relicIds" -> ujson.Arr.from(row.relicIds.map(ujson.Str(_)))) }),
    "disclosures" -> ujson.Arr.from(value.disclosures.map { row => ujson.Obj(
      "recipientPlayerId" -> row.recipientPlayerId,
      "information" -> encodeInformation(row.information)) }))

  def decodeNegotiation(value: ujson.Value, path: String)
      : Either[ProtocolDecodeFailure, NegotiationTerms] = obj(value, path).flatMap { root => for {
    _ <- exact(root, Set("transfers", "disclosures"), path)
    transferValues <- field(root, "transfers", path).flatMap(array(_, s"$path.transfers"))
    transfers <- traverse(transferValues.zipWithIndex) { case (v, i) => decodeTransfer(v, s"$path.transfers[$i]") }
    disclosureValues <- field(root, "disclosures", path).flatMap(array(_, s"$path.disclosures"))
    disclosures <- traverse(disclosureValues.zipWithIndex) { case (v, i) => decodeDisclosure(v, s"$path.disclosures[$i]") }
    _ <- noDuplicates(transfers.map(_.recipientPlayerId), s"$path.transfers")
    disclosureKeys = disclosures.map(v => s"${v.recipientPlayerId}/${v.information}")
    _ <- noDuplicates(disclosureKeys, s"$path.disclosures")
  } yield NegotiationTerms(transfers, disclosures) }

  def encodeDecision(value: DecisionResolution): ujson.Obj = value match {
    case DecisionResolution.StartingAdviser(id) => ujson.Obj("kind" -> "starting-adviser", "adviserId" -> id)
    case DecisionResolution.Search(kept, discarded, placement) => ujson.Obj(
      "kind" -> "search", "kept" -> world(kept),
      "discardedInOrder" -> ujson.Arr.from(discarded.map(world)),
      "placement" -> encodePlacement(placement))
    case DecisionResolution.TakeFacedownRelic(id) => ujson.Obj("kind" -> "take-facedown-relic", "relicId" -> id)
  }

  def decodeDecision(value: ujson.Value, path: String)
      : Either[ProtocolDecodeFailure, DecisionResolution] = obj(value, path).flatMap { root =>
    string(root, "kind", path).flatMap {
      case "starting-adviser" => exact(root, Set("kind", "adviserId"), path)
        .flatMap(_ => string(root, "adviserId", path)).map(DecisionResolution.StartingAdviser)
      case "search" => for {
        _ <- exact(root, Set("kind", "kept", "discardedInOrder", "placement"), path)
        kept <- field(root, "kept", path).flatMap(decodeWorld(_, s"$path.kept"))
        raw <- field(root, "discardedInOrder", path).flatMap(array(_, s"$path.discardedInOrder"))
        discarded <- traverse(raw.zipWithIndex) { case (v, i) => decodeWorld(v, s"$path.discardedInOrder[$i]") }
        _ <- noDuplicates((kept +: discarded).map(v => s"${v.kind}/${v.id}"), s"$path.discardedInOrder")
        placement <- field(root, "placement", path).flatMap(decodePlacement(_, s"$path.placement"))
      } yield DecisionResolution.Search(kept, discarded, placement)
      case "take-facedown-relic" => exact(root, Set("kind", "relicId"), path)
        .flatMap(_ => string(root, "relicId", path)).map(DecisionResolution.TakeFacedownRelic)
      case kind => Left(InvalidValue(s"$path.kind", s"unknown decision resolution '$kind'"))
    }}

  /** Mirrors `WalkerEventCodec`'s "kind" discriminator for the same two
    * Recover payloads (`"recover-choice"` / `"recover-relic"`); an unknown
    * kind fails with the same typed `InvalidValue`, never an exception.
    */
  def encodeDecisionPayloadWire(value: DecisionPayloadWire): ujson.Obj = value match {
    case DecisionPayloadWire.RecoverChoiceWire(choice) =>
      ujson.Obj("kind" -> "recover-choice", "choice" -> choice)
    case DecisionPayloadWire.RecoverRelicWire(relicId) =>
      ujson.Obj("kind" -> "recover-relic", "relicId" -> relicId)
  }

  def decodeDecisionPayloadWire(value: ujson.Value, path: String)
      : Either[ProtocolDecodeFailure, DecisionPayloadWire] = obj(value, path).flatMap { root =>
    string(root, "kind", path).flatMap {
      case "recover-choice" => exact(root, Set("kind", "choice"), path)
        .flatMap(_ => string(root, "choice", path)).map(DecisionPayloadWire.RecoverChoiceWire)
      case "recover-relic" => exact(root, Set("kind", "relicId"), path)
        .flatMap(_ => string(root, "relicId", path)).map(DecisionPayloadWire.RecoverRelicWire)
      case kind => Left(InvalidValue(s"$path.kind", s"unknown decision payload '$kind'"))
    }}

  private def encodeInformation(value: NegotiationInformation): ujson.Obj = value match {
    case NegotiationInformation.Adviser(owner, card) => ujson.Obj("kind" -> "adviser", "ownerPlayerId" -> owner, "card" -> world(card))
    case NegotiationInformation.HeldRelic(owner, relic) => ujson.Obj("kind" -> "held-relic", "ownerPlayerId" -> owner, "relicId" -> relic)
    case NegotiationInformation.SiteRelic(site, relic) => ujson.Obj("kind" -> "site-relic", "siteId" -> site, "relicId" -> relic)
  }
  private def decodeTransfer(value: ujson.Value, path: String) = obj(value, path).flatMap { row => for {
    _ <- exact(row, Set("recipientPlayerId", "favor", "relicIds"), path)
    recipient <- string(row, "recipientPlayerId", path)
    favor <- field(row, "favor", path).flatMap(integer(_, s"$path.favor"))
    relics <- field(row, "relicIds", path).flatMap(strings(_, s"$path.relicIds"))
    _ <- noDuplicates(relics, s"$path.relicIds")
  } yield NegotiationTransfer(recipient, favor, relics) }
  private def decodeDisclosure(value: ujson.Value, path: String) = obj(value, path).flatMap { row => for {
    _ <- exact(row, Set("recipientPlayerId", "information"), path)
    recipient <- string(row, "recipientPlayerId", path)
    information <- field(row, "information", path).flatMap(decodeInformation(_, s"$path.information"))
  } yield NegotiationDisclosure(recipient, information) }
  private def decodeInformation(value: ujson.Value, path: String): Either[ProtocolDecodeFailure, NegotiationInformation] = obj(value, path).flatMap { row => string(row, "kind", path).flatMap {
    case "adviser" => for { _ <- exact(row, Set("kind", "ownerPlayerId", "card"), path); owner <- string(row, "ownerPlayerId", path); card <- field(row, "card", path).flatMap(decodeWorld(_, s"$path.card")) } yield NegotiationInformation.Adviser(owner, card)
    case "held-relic" => for { _ <- exact(row, Set("kind", "ownerPlayerId", "relicId"), path); owner <- string(row, "ownerPlayerId", path); relic <- string(row, "relicId", path) } yield NegotiationInformation.HeldRelic(owner, relic)
    case "site-relic" => for { _ <- exact(row, Set("kind", "siteId", "relicId"), path); site <- string(row, "siteId", path); relic <- string(row, "relicId", path) } yield NegotiationInformation.SiteRelic(site, relic)
    case kind => Left(InvalidValue(s"$path.kind", s"unknown disclosure kind '$kind'"))
  }}
  private def world(value: WorldCard) = ujson.Obj("kind" -> value.kind, "id" -> value.id)
  private def decodeWorld(value: ujson.Value, path: String) = obj(value, path).flatMap { row => for {
    _ <- exact(row, Set("kind", "id"), path); kind <- string(row, "kind", path); id <- string(row, "id", path)
  } yield WorldCard(kind, id) }
  private def encodePlacement(value: Placement) = ujson.Obj("kind" -> value.kind,
    "replace" -> value.replace.map(v => ujson.Obj("kind" -> v.kind, "id" -> v.id)).getOrElse(ujson.Null))
  private def decodePlacement(value: ujson.Value, path: String) = obj(value, path).flatMap { row => for {
    _ <- exact(row, Set("kind", "replace"), path); kind <- string(row, "kind", path)
    replacement <- field(row, "replace", path).flatMap {
      case ujson.Null => Right(None)
      case v => obj(v, s"$path.replace").flatMap { card => for {
        _ <- exact(card, Set("kind", "id"), s"$path.replace"); k <- string(card, "kind", s"$path.replace"); id <- string(card, "id", s"$path.replace")
      } yield Some(CardRef(k, id)) }
    }
  } yield Placement(kind, replacement) }
}
