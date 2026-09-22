package oathdigital.server

import java.net.{DatagramSocket, Inet4Address, InetAddress, NetworkInterface}

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

final case class NetworkAddress(
    interfaceName: String,
    up: Boolean,
    loopback: Boolean,
    address: InetAddress
)

/** Picks the address other computers on the local network can reach. */
object LanAddress {
  // TEST-NET-1: connecting a UDP socket sends nothing, but the OS selects the
  // source address of the route it would use, which skips Docker bridges and
  // most VPN adapters.
  private val ProbeDestination = "192.0.2.1"

  def isPrivateIpv4(address: InetAddress): Boolean = address match {
    case ipv4: Inet4Address =>
      val octets = ipv4.getAddress.map(_ & 0xff)
      octets(0) == 10 ||
      (octets(0) == 172 && octets(1) >= 16 && octets(1) <= 31) ||
      (octets(0) == 192 && octets(1) == 168)
    case _ => false
  }

  def choose(
      probe: Option[InetAddress],
      addresses: Seq[NetworkAddress]
  ): Option[String] =
    probe.filter(isPrivateIpv4)
      .orElse(addresses.collectFirst {
        case NetworkAddress(_, true, false, address)
            if isPrivateIpv4(address) => address
      })
      .map(_.getHostAddress)

  def detect(): Option[String] =
    choose(probeDefaultRoute(), systemAddresses())

  private def probeDefaultRoute(): Option[InetAddress] =
    try {
      val socket = new DatagramSocket()
      try {
        socket.connect(InetAddress.getByName(ProbeDestination), 9)
        Option(socket.getLocalAddress).filterNot(_.isAnyLocalAddress)
      } finally socket.close()
    } catch {
      case NonFatal(_) => None
    }

  private def systemAddresses(): Seq[NetworkAddress] =
    try
      NetworkInterface.getNetworkInterfaces.asScala.toVector.flatMap { network =>
        val up = network.isUp
        val loopback = network.isLoopback
        network.getInetAddresses.asScala.toVector
          .map(NetworkAddress(network.getName, up, loopback, _))
      }
    catch {
      case NonFatal(_) => Vector.empty
    }
}
