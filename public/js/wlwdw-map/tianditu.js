/**
 * Tianditu provider. Coordinates are identity (the base map is CGCS2000, ~15 cm off
 * WGS-84). Use only main-bundle classes at init time: components.js is injected
 * asynchronously, so T.Label and the TMAP_* constants may not exist yet.
 */
(function () {

	var map;
	var hover;
	var drawn = [];

	/* ---- coordinates ---- */

	function toNative(point) {
		return {lat: point.lat, lng: point.lng};
	}

	function fromNative(lnglat) {
		return {lat: lnglat.getLat(), lng: lnglat.getLng()};
	}

	/* ---- drawing primitives ---- */

	// T.LngLat takes (lng, lat), the opposite of our points
	function toLngLat(point) {
		var native = toNative(point);
		return new T.LngLat(native.lng, native.lat);
	}

	// sizes and anchors come from core.pin
	function icon(url, w, h, anchorX, anchorY) {
		return new T.Icon({
			iconUrl: url,
			iconSize: new T.Point(w, h),
			iconAnchor: new T.Point(anchorX, anchorY)
		});
	}

	// the 32x32 bottom-centre icon used by trail points and anchors
	function pointIcon(url) {
		var layout = WlwdwMap.core.pin.icon;
		return icon(url, layout.size, layout.size, layout.anchor[0], layout.anchor[1]);
	}

	// DivIcon negates iconAnchor into marginLeft / -marginTop, so "right dx, up dy"
	// needs (-dx, dy). iconSize 0 is deliberate: `.labels` sizes itself.
	function textLabel(text, dx, dy) {
		return new T.DivIcon({
			html: '<span class="labels">' + text + '</span>',
			iconSize: new T.Point(0, 0),
			iconAnchor: new T.Point(-dx, dy)
		});
	}

	// The wrapper div carries a 1px border and a block child starts at its padding box,
	// hence iconAnchor(1, 1) — measured, not guessed.
	function pinName(text) {
		return new T.DivIcon({
			html: WlwdwMap.core.pinLabel(text, false),
			iconSize: new T.Point(0, 0),
			iconAnchor: new T.Point(1, 1)
		});
	}

	// removeOverLay on an already-removed overlay is a no-op, so stale entries are safe
	function keep(overlay) {
		map.addOverLay(overlay);
		drawn.push(overlay);
		return overlay;
	}

	function clearDrawn() {
		drawn.forEach(function (overlay) {
			map.removeOverLay(overlay);
		});
		drawn.length = 0;
	}

	/* ---- provider contract ---- */

	function init(containerId) {
		map = new T.Map(containerId);
		map.centerAndZoom(new T.LngLat(116.384, 39.925), 15);
		map.enableScrollWheelZoom();

		map.addControl(new T.Control.Zoom());
		map.addControl(new T.Control.Scale());

		// Built once in init. No mouseout hiding: with a polyline on the map mouseover
		// does not always fire, and the label would then never come back.
		hover = document.createElement('div');
		hover.className = 'labels';
		hover.style.position = 'absolute';
		hover.style.pointerEvents = 'none';
		hover.style.left = '12px';
		hover.style.top = '0px';
		hover.innerHTML = '坐标：';
		document.getElementById(containerId).appendChild(hover);

		map.addEventListener('mousemove', function (e) {
			// containerPoint is already relative to the map container
			hover.style.left = (e.containerPoint.x + 12) + 'px';
			hover.style.top = (e.containerPoint.y - 35) + 'px';
			hover.innerHTML = WlwdwMap.core.hoverText(fromNative(e.lnglat));
		});

		map.addEventListener('click', function (e) {
			WlwdwMap.core.recordPicked(fromNative(e.lnglat));
		});
	}

	function redraw(anchors, pins) {
		clearDrawn();
		anchors.forEach(drawAnchor);
		pins.forEach(drawPin);
	}

	function drawTrailPoints(trails) {
		trails.forEach(drawTrailPoint);
	}

	function fitBounds(points) {
		map.setViewport(points.map(toLngLat));
	}

	/* ---- the three point kinds ---- */

	// Current position: icon + transparent-icon drag handle + unit name + leader line
	function drawPin(point) {
		var p = toLngLat(point);
		var layout = WlwdwMap.core.pin;
		var infoWindow = new T.InfoWindow(WlwdwMap.core.pointInfo(point));

		var marker = keep(new T.Marker(p, {icon: pointIcon(Marker_Pointer)}));
		marker.addEventListener('click', function () {
			marker.openInfoWindow(infoWindow);
		});

		// The name is a separate marker (no Marker.setLabel here) and centres itself.
		// Order matters: name first, then handle — the name's wrapper div is a 2x2 dot
		// sitting exactly on the handle's centre and is not covered by pointer-events.
		var title = keep(new T.Marker(p, {icon: pinName(point.title)}));

		// Transparent icon anchored at its centre, so the box is centred on the point
		var handle = keep(new T.Marker(p, {
			icon: icon(Marker_Blank, layout.handle.w, layout.handle.h,
				layout.handle.anchor[0], layout.handle.anchor[1]),
			draggable: true
		}));
		handle.addEventListener('click', function () {
			handle.openInfoWindow(infoWindow);
		});

		var line = keep(new T.Polyline([p, p], {color: 'red', weight: 1, opacity: 0.8}));
		handle.addEventListener('dragend', function (e) {
			var moved = e.target.getLngLat();
			line.setLngLats([moved, p]);
			title.setLngLat(moved);
		});
	}

	// Trail point: icon + time label; clicking the label removes it
	function drawTrailPoint(point) {
		var p = toLngLat(point);

		keep(new T.Marker(p, {icon: pointIcon(Marker_Pin)}));

		var time = keep(new T.Marker(p, {icon: textLabel(WlwdwMap.core.timeLabel(point.time), 10, 5)}));
		time.addEventListener('click', function () {
			map.removeOverLay(time);
		});
	}

	// Reference point: icon + name label; clicking the label removes it
	function drawAnchor(point) {
		var p = toLngLat(point);

		keep(new T.Marker(p, {icon: pointIcon(Anchor_Path + point.icon)}));

		var name = keep(new T.Marker(p, {icon: textLabel(point.title, 10, 5)}));
		name.addEventListener('click', function () {
			map.removeOverLay(name);
		});
	}

	window.WlwdwMapProvider = {
		init: init,
		redraw: redraw,
		drawTrailPoints: drawTrailPoints,
		fitBounds: fitBounds
	};
})();
