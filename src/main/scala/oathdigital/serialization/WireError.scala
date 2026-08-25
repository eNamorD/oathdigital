package oathdigital.serialization

import oathdigital.model.CatalogRef

sealed trait WireError extends Product with Serializable {
  def path: String
  def message: String
}

object WireError {
  final case class MalformedJson(path: String, message: String) extends WireError
  final case class MissingField(path: String, message: String) extends WireError
  final case class WrongType(path: String, message: String) extends WireError
  final case class UnsupportedFormatVersion(path: String, actual: Int, supported: Int)
      extends WireError {
    override val message: String =
      s"unsupported format version $actual; supported version is $supported"
  }
  final case class UnknownEventType(path: String, eventType: String)
      extends WireError {
    override val message: String = s"unknown event type '$eventType'"
  }
  final case class InvalidValue(path: String, message: String) extends WireError
  final case class CatalogMismatch(path: String, expected: CatalogRef, actual: CatalogRef)
      extends WireError {
    override val message: String =
      s"catalog ${actual.ruleset}@${actual.version} does not match " +
        s"${expected.ruleset}@${expected.version}"
  }
  final case class InvalidSequence(path: String, expected: Long, actual: Long)
      extends WireError {
    override val message: String =
      s"expected contiguous sequence position $expected but found $actual"
  }
}
