/**
 * Google Maps provider. Coordinates are dynamic: GCJ-02 for roadmap/terrain, WGS-84 for
 * satellite/hybrid, so the conversion direction follows maptypeid_changed and the map
 * is redrawn after a switch.
 */
(function () {

	var map;
	var labelPos;
	var coordsType = 'gcj';
	var drawn = [];

	/* ---- coordinates ---- */

	function toNative(point) {
		if (coordsType == 'gcj') {
			return eviltransform.wgs2gcj(point.lat, point.lng);
		}
		return {lat: point.lat, lng: point.lng};
	}

	function fromNative(latLng) {
		if (coordsType == 'gcj') {
			return eviltransform.gcj2wgs(latLng.lat(), latLng.lng());
		}
		return {lat: latLng.lat(), lng: latLng.lng()};
	}

	/* ---- drawing primitives ---- */

	function toLatLng(point) {
		var native = toNative(point);
		return new google.maps.LatLng(native.lat, native.lng);
	}

	// sizes and anchors come from core.pin
	function icon(url) {
		var layout = WlwdwMap.core.pin.icon;
		return new google.maps.MarkerImage(url,
			new google.maps.Size(layout.size, layout.size),
			new google.maps.Point(0, 0),   // origin
			new google.maps.Point(layout.anchor[0], layout.anchor[1])); // anchor
	}

	// No "clear all overlays" API here, so this layer keeps its own ledger
	function keep(overlay) {
		drawn.push(overlay);
		return overlay;
	}

	function clearDrawn() {
		drawn.forEach(function (overlay) {
			overlay.setMap(null);
		});
		drawn.length = 0;
	}

	/* ---- provider contract ---- */

	function init(containerId) {
		map = new google.maps.Map(document.getElementById(containerId), {
			center: new google.maps.LatLng(39.925, 116.384),
			zoom: 15,
			mapTypeId: google.maps.MapTypeId.ROADMAP
		});

		google.maps.event.addListener(map, 'maptypeid_changed', function () {
			var typeid = map.getMapTypeId();
			if (typeid == google.maps.MapTypeId.ROADMAP || typeid == google.maps.MapTypeId.TERRAIN) {
				if (coordsType != 'gcj') {
					coordsType = 'gcj';
					WlwdwMap.reloadOverlay();
				}
			}
			if (typeid == google.maps.MapTypeId.SATELLITE || typeid == google.maps.MapTypeId.HYBRID) {
				if (coordsType != 'wgs') {
					coordsType = 'wgs';
					WlwdwMap.reloadOverlay();
				}
			}
		});

		// Built once in init
		labelPos = new MarkerWithLabel({
			position: new google.maps.LatLng(0, 0),
			icon: ' ',
			optimized: false,
			zIndex: 2,
			map: map,
			labelContent: '坐标：',
			labelClass: 'labels',
			labelAnchor: new google.maps.Point(-35, 12)
		});

		google.maps.event.addListener(map, 'mousemove', function (e) {
			labelPos.setPosition(e.latLng);
			labelPos.set('labelContent', WlwdwMap.core.hoverText(fromNative(e.latLng)));
		});

		google.maps.event.addListener(map, 'click', function (e) {
			WlwdwMap.core.recordPicked(fromNative(e.latLng));
		});

		google.maps.event.addListener(map, 'mouseover', function () {
			labelPos.setMap(map);
		});

		google.maps.event.addListener(map, 'mouseout', function () {
			labelPos.setMap(null);
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
		var bound = new google.maps.LatLngBounds();
		points.forEach(function (point) {
			var native = toNative(point);
			bound.extend(new google.maps.LatLng(native.lat, native.lng));
		});
		if (!bound.isEmpty()) {
			map.fitBounds(bound);
		}
	}

	/* ---- the three point kinds ---- */

	// Current position: icon + draggable name label + leader line
	function drawPin(point) {
		var p = toLatLng(point);

		var marker = keep(new google.maps.Marker({
			position: p,
			icon: icon(Marker_Pointer),
			map: map,
			optimized: false,
			zIndex: 1
		}));

		// No separate handle here: the MarkerWithLabel label *is* the drag surface, so it
		// keeps pointer events and uses pinLabel(text, true). labelClass stays empty — the
		// shared span already carries the look, and the wrapper div would add a second frame.
		var label = keep(new MarkerWithLabel({
			position: p,
			icon: ' ',
			draggable: true,
			raiseOnDrag: false,
			map: map,
			optimized: false,
			zIndex: 0,
			labelContent: WlwdwMap.core.pinLabel(point.title, true),
			labelClass: '',
			labelAnchor: new google.maps.Point(0, 0)
		}));

		var line = keep(new google.maps.Polyline({
			path: [p, p],
			strokeColor: '#FF0000',
			strokeOpacity: 0.8,
			strokeWeight: 1,
			map: map
		}));

		var infoWindow = new google.maps.InfoWindow({content: WlwdwMap.core.pointInfo(point)});
		google.maps.event.addListener(marker, 'click', function () {
			infoWindow.open(map, label);
		});
		google.maps.event.addListener(label, 'click', function () {
			infoWindow.open(map, label);
		});
		google.maps.event.addListener(label, 'dragend', function (e) {
			line.getPath().setAt(0, e.latLng);
		});
	}

	// Trail point: icon + time label; the label is draggable and hides on click
	function drawTrailPoint(point) {
		var p = toLatLng(point);

		keep(new google.maps.Marker({
			position: p,
			icon: icon(Marker_Pin),
			map: map
		}));

		var label = keep(new MarkerWithLabel({
			position: p,
			icon: ' ',
			draggable: true,
			raiseOnDrag: false,
			map: map,
			labelContent: WlwdwMap.core.timeLabel(point.time),
			labelClass: 'labels',
			labelAnchor: new google.maps.Point(10, -5)
		}));

		google.maps.event.addListener(label, 'click', function () {
			this.setMap(null);
		});
	}

	// Reference point: icon + name label; the label is draggable, clicks do nothing
	function drawAnchor(point) {
		var p = toLatLng(point);

		keep(new google.maps.Marker({
			position: p,
			icon: icon(Anchor_Path + point.icon),
			map: map
		}));

		keep(new MarkerWithLabel({
			position: p,
			icon: ' ',
			draggable: true,
			raiseOnDrag: false,
			map: map,
			labelContent: point.title,
			labelClass: 'labels',
			labelAnchor: new google.maps.Point(10, -5)
		}));
	}

	window.WlwdwMapProvider = {
		init: init,
		redraw: redraw,
		drawTrailPoints: drawTrailPoints,
		fitBounds: fitBounds
	};
})();
