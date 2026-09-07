package oathdigital.server

import java.net.URI
import java.nio.file.Paths

class ServerConfigSuite extends munit.FunSuite {
  private val version = "0.1.0-alpha.1"

  test("defaults produce loopback development configuration") {
    val config = parse(Array.empty, Map.empty)

    assertEquals(config.host, "127.0.0.1")
    assertEquals(config.port, 8080)
    assertEquals(config.publicBaseUrl, None)
    assertEquals(config.databasePath,
      Paths.get("var/oathdigital").toAbsolutePath.normalize)
    assertEquals(config.catalogPath, Paths
      .get("docs/catalog/new-foundations-component-catalog.json")
      .toAbsolutePath.normalize)
    assertEquals(config.mode, ServerMode.Development)
    assertEquals(config.version, version)
  }

  test("environment configures every runtime value") {
    val config = parse(
      Array.empty,
      Map(
        "OATH_HOST" -> "0.0.0.0",
        "OATH_PORT" -> "8081",
        "OATH_PUBLIC_BASE_URL" -> "http://192.168.1.20:8081",
        "OATH_DATABASE_PATH" -> "var/test-db/../alpha-db",
        "OATH_CATALOG_PATH" -> "docs/catalog/../catalog.json",
        "OATH_MODE" -> "trusted-alpha"
      )
    )

    assertEquals(config.host, "0.0.0.0")
    assertEquals(config.port, 8081)
    assertEquals(config.publicBaseUrl,
      Some(new URI("http://192.168.1.20:8081")))
    assertEquals(config.databasePath,
      Paths.get("var/alpha-db").toAbsolutePath.normalize)
    assertEquals(config.catalogPath,
      Paths.get("docs/catalog.json").toAbsolutePath.normalize)
    assertEquals(config.mode, ServerMode.TrustedAlpha)
  }

  test("CLI values take precedence over environment values") {
    val result = ServerConfig.parse(
      Array("--host", "0.0.0.0", "--port", "9090"),
      Map(
        "OATH_HOST" -> "127.0.0.1",
        "OATH_PORT" -> "8081",
        "OATH_DATABASE_PATH" -> "var/test-db",
        "OATH_CATALOG_PATH" ->
          "docs/catalog/new-foundations-component-catalog.json",
        "OATH_MODE" -> "trusted-alpha",
        "OATH_PUBLIC_BASE_URL" -> "http://192.168.1.20:9090"
      ),
      version
    )
    val config = result.toOption.get

    assertEquals(config.host, "0.0.0.0")
    assertEquals(config.port, 9090)
    assertEquals(config.mode, ServerMode.TrustedAlpha)
    assertEquals(config.version, "0.1.0-alpha.1")
  }

  test("CLI overrides each environment-backed option") {
    val config = parse(
      Array(
        "--host", "localhost",
        "--port", "9090",
        "--public-base-url", "http://localhost:9090",
        "--database-path", "var/cli-db",
        "--catalog-path", "docs/cli-catalog.json",
        "--mode", "development"
      ),
      Map(
        "OATH_HOST" -> "0.0.0.0",
        "OATH_PORT" -> "8081",
        "OATH_PUBLIC_BASE_URL" -> "http://192.168.1.20:8081",
        "OATH_DATABASE_PATH" -> "var/env-db",
        "OATH_CATALOG_PATH" -> "docs/env-catalog.json",
        "OATH_MODE" -> "trusted-alpha"
      )
    )

    assertEquals(config.host, "localhost")
    assertEquals(config.port, 9090)
    assertEquals(config.publicBaseUrl,
      Some(new URI("http://localhost:9090")))
    assertEquals(config.databasePath,
      Paths.get("var/cli-db").toAbsolutePath.normalize)
    assertEquals(config.catalogPath,
      Paths.get("docs/cli-catalog.json").toAbsolutePath.normalize)
    assertEquals(config.mode, ServerMode.Development)
  }

  test("unknown options and missing values return usage for every error") {
    val errors = ServerConfig.parse(
      Array("--mystery", "value", "--port", "--mode"),
      Map.empty,
      version
    ).left.toOption.get

    assertEquals(errors.size, 3)
    assert(errors(0).startsWith("unknown option --mystery"))
    assert(errors(1).startsWith("missing value for --port"))
    assert(errors(2).startsWith("missing value for --mode"))
    errors.foreach(error => assert(error.contains(ServerConfig.usage)))
  }

  test("invalid values return all errors in stable option order") {
    val errors = ServerConfig.parse(
      Array(
        "--mode", "production",
        "--catalog-path", " ",
        "--database-path", " ",
        "--public-base-url", "ftp://example.com/path?query=yes#fragment",
        "--port", "70000",
        "--host", "bad host"
      ),
      Map.empty,
      version
    ).left.toOption.get

    assertEquals(errors.map(_.takeWhile(_ != ':')), Vector(
      "--host",
      "--port",
      "--public-base-url",
      "--database-path",
      "--catalog-path",
      "--mode"
    ))
  }

  test("ports must be decimal integers from 1 through 65535") {
    Vector("", "zero", "0", "-1", "65536").foreach { value =>
      val error = ServerConfig.parse(
        Array("--port", value),
        Map.empty,
        version
      ).left.toOption.get
      assert(error.exists(_.startsWith("--port:")), clues(value, error))
    }
    Vector("1", "65535").foreach { value =>
      assertEquals(parse(Array("--port", value)).port, value.toInt)
    }
  }

  test("bind host must be a non-blank host without whitespace") {
    Vector("", " ", "bad host", "host/path").foreach { host =>
      val errors = ServerConfig.parse(
        Array("--host", host, "--mode", "trusted-alpha",
          "--public-base-url", "http://localhost:8080"),
        Map.empty,
        version
      ).left.toOption.get
      assert(errors.exists(_.startsWith("--host:")), clues(host, errors))
    }
  }

  test("paths are absolute and normalized and malformed paths are errors") {
    val config = parse(Array(
      "--database-path", "var/data/../database",
      "--catalog-path", "docs/catalog/../catalog.json"
    ))

    assert(config.databasePath.isAbsolute)
    assert(config.catalogPath.isAbsolute)
    assertEquals(config.databasePath,
      Paths.get("var/database").toAbsolutePath.normalize)
    assertEquals(config.catalogPath,
      Paths.get("docs/catalog.json").toAbsolutePath.normalize)

    val invalidPath = "bad\u0000path"
    val errors = ServerConfig.parse(
      Array("--database-path", invalidPath, "--catalog-path", invalidPath),
      Map.empty,
      version
    ).left.toOption.get
    assertEquals(errors.map(_.takeWhile(_ != ':')), Vector(
      "--database-path", "--catalog-path"
    ))
  }

  test("runtime mode accepts only development and trusted-alpha") {
    assertEquals(parse(Array("--mode", "development")).mode,
      ServerMode.Development)
    assertEquals(parse(Array(
      "--mode", "trusted-alpha",
      "--public-base-url", "http://localhost:8080"
    )).mode, ServerMode.TrustedAlpha)

    val errors = ServerConfig.parse(
      Array("--mode", "production"), Map.empty, version
    ).left.toOption.get
    assert(errors.exists(_.startsWith("--mode:")))
  }

  test("development mode rejects non-loopback bind hosts") {
    Vector("0.0.0.0", "192.168.1.10", "example.com").foreach { host =>
      val errors = ServerConfig.parse(
        Array("--host", host), Map.empty, version
      ).left.toOption.get
      assert(errors.exists(error =>
        error.startsWith("--host:") && error.contains("loopback")))
    }
  }

  test("trusted-alpha requires a public base URL for non-loopback binding") {
    val errors = ServerConfig.parse(
      Array("--mode", "trusted-alpha", "--host", "0.0.0.0"),
      Map.empty,
      version
    ).left.toOption.get

    assert(errors.exists(error =>
      error.startsWith("--public-base-url:") && error.contains("required")))
  }

  test("trusted-alpha permits loopback without a public base URL") {
    Vector("127.0.0.1", "localhost", "::1").foreach { host =>
      val config = parse(Array(
        "--mode", "trusted-alpha", "--host", host
      ))
      assertEquals(config.host, host)
      assertEquals(config.publicBaseUrl, None)
    }
  }

  test("public base URL is an absolute HTTP or HTTPS origin") {
    Vector(
      "https://play.example.com",
      "http://192.168.1.20:9090",
      "http://localhost:8080"
    ).foreach { value =>
      val config = parse(Array(
        "--mode", "trusted-alpha",
        "--host", "0.0.0.0",
        "--public-base-url", value
      ))
      assertEquals(config.publicBaseUrl, Some(new URI(value)))
    }

    Vector(
      "play.example.com",
      "ftp://play.example.com",
      "https://user:pass@play.example.com",
      "https://play.example.com/game",
      "https://play.example.com?query=yes",
      "https://play.example.com#fragment"
    ).foreach { value =>
      val errors = ServerConfig.parse(
        Array("--public-base-url", value), Map.empty, version
      ).left.toOption.get
      assert(errors.exists(_.startsWith("--public-base-url:")),
        clues(value, errors))
    }
  }

  private def parse(
      arguments: Array[String],
      environment: Map[String, String] = Map.empty
  ): ServerConfig = ServerConfig
    .parse(arguments, environment, version)
    .fold(errors => fail(errors.mkString("; ")), identity)
}
