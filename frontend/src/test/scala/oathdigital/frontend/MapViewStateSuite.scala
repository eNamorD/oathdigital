package oathdigital.frontend

import munit.FunSuite

class MapViewStateSuite extends FunSuite {
  private val bounds = MapBounds(500, 300, 1000, 600)

  test("initial fit includes the whole map without enlarging small content") {
    assertEquals(MapViewState().resize(bounds).scale, 0.5)
    assertEquals(MapViewState().resize(MapBounds(900, 800, 300, 200)).scale, 1.0)
  }

  test("zoom preserves the world point at the viewport center") {
    val manual = MapViewState().resize(bounds).zoomBy(2, bounds)
    assertEquals(manual.scale, 1.0)
    assertEquals(manual.left, 250.0)
    assertEquals(manual.top, 150.0)
    assert(!manual.fit)
  }

  test("panning and resize keep the map recoverable") {
    val manual = MapViewState().resize(bounds).zoomBy(2, bounds)
    val panned = manual.panTo(9999, -10, bounds)
    assertEquals(panned.left, 500.0)
    assertEquals(panned.top, 0.0)
    assertEquals(panned.resize(bounds.copy(viewWidth = 1200)).left, 0.0)
  }

  test("zoom preserves the center when fitted content has horizontal or vertical margins") {
    val square = MapBounds(500, 300, 1000, 1000)
    val zoomed = MapViewState().resize(square).zoomBy(2, square)
    assertEquals(zoomed.scale, 0.6)
    assertEquals(zoomed.left, 50.0)
    assertEquals(zoomed.top, 150.0)
    val wide = MapBounds(300, 500, 1000, 1000)
    val portrait = MapViewState().resize(wide).zoomBy(2, wide)
    assertEquals(portrait.left, 150.0)
    assertEquals(portrait.top, 50.0)
  }

  test("refresh retains manual navigation while fit mode follows viewport size") {
    val manual = MapViewState().resize(bounds).zoomBy(2, bounds).panTo(120, 80, bounds)
    assertEquals(manual.resize(bounds), manual)
    assertEquals(manual.resize(bounds.copy(viewWidth = 600)).scale, 1.0)
    assertEquals(MapViewState().resize(bounds.copy(viewWidth = 250)).scale, 0.25)
  }

  test("zoom limits and temporarily zero-size containers produce finite geometry") {
    val huge = MapViewState().resize(bounds).zoomBy(100, bounds)
    assertEquals(huge.scale, 3.0)
    assertEquals(huge.zoomBy(0.001, bounds).scale, 0.5)
    val hidden = MapViewState().resize(MapBounds(0, 0, 0, 0))
    assert(hidden.scale > 0 && !hidden.scale.isInfinity && !hidden.scale.isNaN)
  }
}
