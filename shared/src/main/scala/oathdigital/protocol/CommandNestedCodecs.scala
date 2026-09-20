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
  }

  def decodeDecision(value: ujson.Value, path: String)
      : Either[ProtocolDecodeFailure, DecisionResolution] = obj(value, path).flatMap { root =>
    string(root, "kind", path).flatMap {
      case "starting-adviser" => exact(root, Set("kind", "adviserId"), path)
        .flatMap(_ => string(root, "adviserId", path)).map(DecisionResolution.StartingAdviser)
      case kind => Left(InvalidValue(s"$path.kind", s"unknown decision resolution '$kind'"))
    }}

  /** Mirrors the journal's generic decision-answer tags (`"choose-one"` /
    * `"partition"`) and its kind/id option spelling, so a client's answer and
    * the engine's recording of it read alike. An unknown tag fails with the
    * same typed `InvalidValue` every other discriminator here does, never an
    * exception.
    */
  /** The same `optionKind`/`optionId` pair a decision answer travels as, so a
    * start selection and an answer naming the same site are spelled the same
    * way on the wire.
    */
  def encodeStartArgWire(value: WalkerStartArgWire): ujson.Obj = ujson.Obj(
    "optionKind" -> value.optionKind, "optionId" -> value.optionId)

  def decodeStartArgsWire(value: ujson.Value, path: String)
      : Either[ProtocolDecodeFailure, Vector[WalkerStartArgWire]] =
    array(value, path).flatMap(values => traverse(values.zipWithIndex) {
      case (entry, index) =>
        val entryPath = s"$path[$index]"
        obj(entry, entryPath).flatMap { root => for {
          _ <- exact(root, Set("optionKind", "optionId"), entryPath)
          kind <- string(root, "optionKind", entryPath)
          id <- string(root, "optionId", entryPath)
        } yield WalkerStartArgWire(kind, id) }
    })

  def encodeDecisionAnswerWire(value: DecisionAnswerWire): ujson.Obj = value match {
    case DecisionAnswerWire.ChooseOneWire(kind, id) =>
      ujson.Obj("kind" -> "choose-one", "optionKind" -> kind, "optionId" -> id)
    case DecisionAnswerWire.PartitionWire(placements) =>
      ujson.Obj("kind" -> "partition",
        "placements" -> ujson.Arr.from(placements.map(row => ujson.Obj(
          "optionKind" -> row.optionKind, "optionId" -> row.optionId,
          "sectionKey" -> row.sectionKey))))
    case DecisionAnswerWire.DistributeWire(amounts) => ujson.Obj(
      "kind" -> "distribute",
      "amounts" -> ujson.Arr.from(amounts.map(row => ujson.Obj(
        "optionKind" -> row.optionKind, "optionId" -> row.optionId,
        "amount" -> row.amount))))
    case DecisionAnswerWire.ChooseManyWire(options) => ujson.Obj(
      "kind" -> "choose-many",
      "options" -> ujson.Arr.from(options.map(row => ujson.Obj(
        "optionKind" -> row.optionKind, "optionId" -> row.optionId))))
    case DecisionAnswerWire.ChooseAmountWire(amount) =>
      ujson.Obj("kind" -> "choose-amount", "amount" -> amount)
    case DecisionAnswerWire.ProposeTermsWire(terms) =>
      ujson.Obj("kind" -> "propose-terms", "terms" -> encodeNegotiation(terms))
    case DecisionAnswerWire.AcceptDealWire => ujson.Obj("kind" -> "accept-deal")
    case DecisionAnswerWire.DeclineDealWire => ujson.Obj("kind" -> "decline-deal")
  }

  def decodeDecisionAnswerWire(value: ujson.Value, path: String)
      : Either[ProtocolDecodeFailure, DecisionAnswerWire] = obj(value, path).flatMap { root =>
    string(root, "kind", path).flatMap {
      case "choose-one" => for {
        _ <- exact(root, Set("kind", "optionKind", "optionId"), path)
        optionKind <- string(root, "optionKind", path)
        optionId <- string(root, "optionId", path)
      } yield DecisionAnswerWire.ChooseOneWire(optionKind, optionId)
      case "partition" => for {
        _ <- exact(root, Set("kind", "placements"), path)
        raw <- field(root, "placements", path).flatMap(array(_, s"$path.placements"))
        rows <- traverse(raw.zipWithIndex) { case (v, i) =>
          decodeDecisionPlacement(v, s"$path.placements[$i]") }
        _ <- noDuplicates(rows.map(row => s"${row.optionKind}/${row.optionId}"),
          s"$path.placements")
      } yield DecisionAnswerWire.PartitionWire(rows)
      case "distribute" => for {
        _ <- exact(root, Set("kind", "amounts"), path)
        raw <- field(root, "amounts", path).flatMap(array(_, s"$path.amounts"))
        rows <- traverse(raw.zipWithIndex) { case (v, i) =>
          decodeDistributeAmount(v, s"$path.amounts[$i]") }
        _ <- noDuplicates(rows.map(row => s"${row.optionKind}/${row.optionId}"),
          s"$path.amounts")
      } yield DecisionAnswerWire.DistributeWire(rows)
      case "choose-many" => for {
        _ <- exact(root, Set("kind", "options"), path)
        raw <- field(root, "options", path).flatMap(array(_, s"$path.options"))
        rows <- traverse(raw.zipWithIndex) { case (v, i) =>
          decodeDecisionOption(v, s"$path.options[$i]") }
        _ <- noDuplicates(rows.map(row => s"${row.optionKind}/${row.optionId}"),
          s"$path.options")
      } yield DecisionAnswerWire.ChooseManyWire(rows)
      case "choose-amount" => for {
        _ <- exact(root, Set("kind", "amount"), path)
        amount <- field(root, "amount", path).flatMap(integer(_, s"$path.amount"))
      } yield DecisionAnswerWire.ChooseAmountWire(amount)
      case "propose-terms" => for {
        _ <- exact(root, Set("kind", "terms"), path)
        raw <- field(root, "terms", path)
        terms <- decodeNegotiation(raw, s"$path.terms")
      } yield DecisionAnswerWire.ProposeTermsWire(terms)
      case "accept-deal" => exact(root, Set("kind"), path)
        .map(_ => DecisionAnswerWire.AcceptDealWire)
      case "decline-deal" => exact(root, Set("kind"), path)
        .map(_ => DecisionAnswerWire.DeclineDealWire)
      case kind => Left(InvalidValue(s"$path.kind", s"unknown decision answer '$kind'"))
    }}

  private def decodeDecisionPlacement(value: ujson.Value, path: String)
      : Either[ProtocolDecodeFailure, DecisionPlacementWire] = obj(value, path).flatMap { row => for {
    _ <- exact(row, Set("optionKind", "optionId", "sectionKey"), path)
    optionKind <- string(row, "optionKind", path)
    optionId <- string(row, "optionId", path)
    sectionKey <- string(row, "sectionKey", path)
  } yield DecisionPlacementWire(optionKind, optionId, sectionKey) }

  private def decodeDecisionOption(value: ujson.Value, path: String)
      : Either[ProtocolDecodeFailure, DecisionOptionWire] =
    obj(value, path).flatMap { row => for {
      _ <- exact(row, Set("optionKind", "optionId"), path)
      optionKind <- string(row, "optionKind", path)
      optionId <- string(row, "optionId", path)
    } yield DecisionOptionWire(optionKind, optionId) }

  private def decodeDistributeAmount(value: ujson.Value, path: String)
      : Either[ProtocolDecodeFailure, DistributeAmountWire] =
    obj(value, path).flatMap { row => for {
      _ <- exact(row, Set("optionKind", "optionId", "amount"), path)
      optionKind <- string(row, "optionKind", path)
      optionId <- string(row, "optionId", path)
      amount <- field(row, "amount", path).flatMap(integer(_, s"$path.amount"))
    } yield DistributeAmountWire(optionKind, optionId, amount) }

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
}
