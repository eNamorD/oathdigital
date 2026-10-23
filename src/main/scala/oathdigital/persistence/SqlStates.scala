package oathdigital.persistence

import java.sql.SQLException

/** Reads of the SQLSTATE a JDBC driver attaches to a failure. */
private[persistence] object SqlStates:
  /** Whether `error` reports an integrity-constraint violation (SQLSTATE
    * class 23), which the repositories turn into an expected conflict rather
    * than a storage failure. */
  def constraintViolation(error: SQLException): Boolean =
    Option(error.getSQLState).exists(_.startsWith("23"))
