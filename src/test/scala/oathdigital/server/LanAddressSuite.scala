package oathdigital.server

import java.net.InetAddress

class LanAddressSuite extends munit.FunSuite {
  private def ip(a: Int, b: Int, c: Int, d: Int): InetAddress =
    InetAddress.getByAddress(Array(a, b, c, d).map(_.toByte))

  private def interface(name: String, address: InetAddress, up: Boolean = true, loopback: Boolean = false) =
    NetworkAddress(name, up, loopback, address)

  test("private IPv4 ranges are recognized at their boundaries") {
    assert(LanAddress.isPrivateIpv4(ip(10, 0, 0, 1)))
    assert(LanAddress.isPrivateIpv4(ip(172, 16, 0, 1)))
    assert(LanAddress.isPrivateIpv4(ip(172, 31, 255, 254)))
    assert(LanAddress.isPrivateIpv4(ip(192, 168, 1, 20)))
    assert(!LanAddress.isPrivateIpv4(ip(172, 15, 0, 1)))
    assert(!LanAddress.isPrivateIpv4(ip(172, 32, 0, 1)))
    assert(!LanAddress.isPrivateIpv4(ip(8, 8, 8, 8)))
    assert(!LanAddress.isPrivateIpv4(ip(127, 0, 0, 1)))
    assert(!LanAddress.isPrivateIpv4(InetAddress.getByName("fd00::1")))
  }

  test("a private default-route probe wins over earlier Docker or VPN interfaces") {
    val interfaces = Seq(
      interface("docker0", ip(172, 17, 0, 1)),
      interface("utun3", ip(10, 8, 0, 2)),
      interface("en0", ip(192, 168, 1, 20))
    )
    assertEquals(LanAddress.choose(Some(ip(192, 168, 1, 20)), interfaces), Some("192.168.1.20"))
  }

  test("a public probe result is rejected in favor of the first private interface") {
    val interfaces = Seq(interface("en0", ip(192, 168, 1, 20)))
    assertEquals(LanAddress.choose(Some(ip(203, 0, 113, 7)), interfaces), Some("192.168.1.20"))
  }

  test("the fallback skips down and loopback interfaces") {
    val interfaces = Seq(
      interface("lo0", ip(10, 0, 0, 9), loopback = true),
      interface("en1", ip(10, 0, 0, 5), up = false),
      interface("en0", ip(10, 0, 0, 7))
    )
    assertEquals(LanAddress.choose(None, interfaces), Some("10.0.0.7"))
  }

  test("no private address means no LAN address") {
    assertEquals(LanAddress.choose(None, Seq(interface("en0", ip(203, 0, 113, 7)))), None)
    assertEquals(LanAddress.choose(None, Seq.empty), None)
  }
}
