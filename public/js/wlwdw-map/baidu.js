/**
 * Baidu Maps provider. Coordinates are always BD-09 whatever the map type, so display
 * goes through wgs2bd and picking through bd2wgs.
 */
(function () {

	var map;
	var labelPos;
	var drawn = [];

	/* ---- coordinates ---- */

	function toNative(point) {
		return eviltransform.wgs2bd(point.lat, point.lng);
	}

	function fromNative(native) {
		return eviltransform.bd2wgs(native.lat, native.lng);
	}

	/* ---- drawing primitives ---- */

	// BMap.Point takes (lng, lat), the opposite of our points
	function toPoint(point) {
		var native = toNative(point);
		return new BMap.Point(native.lng, native.lat);
	}

	// sizes and anchors come from core.pin
	function icon(url) {
		var layout = WlwdwMap.core.pin.icon;
		return new BMap.Icon(url, new BMap.Size(layout.size, layout.size),
			{anchor: new BMap.Size(layout.anchor[0], layout.anchor[1])});
	}

	// Not map.clearOverlays(): that would also drop the hover label, which is created
	// once in init and has to stay.
	function keep(overlay) {
		map.addOverlay(overlay);
		drawn.push(overlay);
		return overlay;
	}

	function clearDrawn() {
		drawn.forEach(function (overlay) {
			map.removeOverlay(overlay);
		});
		drawn.length = 0;
	}

	// Overlays stack in insertion order, so the hover label needs re-raising after redraw
	function bumpHoverLabel() {
		map.removeOverlay(labelPos);
		map.addOverlay(labelPos);
	}

	/* ---- provider contract ---- */

	function init(containerId) {
		map = new BMap.Map(containerId, {mapType: BMAP_NORMAL_MAP});
		map.centerAndZoom(new BMap.Point(116.384, 39.925), 15);
		map.setDefaultCursor('default');
		map.addControl(new BMap.NavigationControl({type: BMAP_NAVIGATION_CONTROL_LARGE}));
		map.addControl(new BMap.MapTypeControl({
			mapTypes: [BMAP_NORMAL_MAP, BMAP_SATELLITE_MAP, BMAP_HYBRID_MAP]
		}));

		// Built once in init. No mouseover/mouseout toggling: with a polyline on the map
		// mouseover may not fire and the label would never come back.
		labelPos = new BMap.Label('坐标：', {offset: new BMap.Size(12, -35)});
		map.addOverlay(labelPos);

		map.addEventListener('mousemove', function (e) {
			labelPos.setPosition(e.point);
			labelPos.setContent(WlwdwMap.core.hoverText(fromNative(e.point)));
		});

		map.addEventListener('click', function (e) {
			WlwdwMap.core.recordPicked(fromNative(e.point));
		});
	}

	function redraw(anchors, pins) {
		clearDrawn();
		anchors.forEach(drawAnchor);
		pins.forEach(drawPin);
		bumpHoverLabel();
	}

	function drawTrailPoints(trails) {
		trails.forEach(drawTrailPoint);
	}

	function fitBounds(points) {
		map.setViewport(points.map(toPoint), {zoomFactor: -1});
	}

	/* ---- the three point kinds ---- */

	// Current position: icon + transparent-icon drag handle + unit name + leader line
	function drawPin(point) {
		var p = toPoint(point);
		var layout = WlwdwMap.core.pin;
		var infoWindow = new BMap.InfoWindow(WlwdwMap.core.pointInfo(point));

		var marker = new BMap.Marker(p, {icon: icon(Marker_Pointer)});
		marker.setZIndex(0);
		marker.addEventListener('click', function () {
			this.openInfoWindow(infoWindow);
		});

		// Transparent 32x18 icon. Do not add a marker offset: BMap.Icon already anchors
		// at the centre, and the old offset(16,9) double-compensated — it pushed the box
		// to the point's bottom-right corner.
		var handle = new BMap.Marker(p, {
			icon: new BMap.Icon(Marker_Blank,
				new BMap.Size(layout.handle.w, layout.handle.h),
				{anchor: new BMap.Size(layout.handle.anchor[0], layout.handle.anchor[1])})
		});
		handle.enableDragging();
		handle.setZIndex(1);
		handle.addEventListener('click', function () {
			handle.openInfoWindow(infoWindow);
		});

		// Not handle.setLabel() (Baidu-only): a standalone label using the shared
		// self-centring element. offset(0,0) puts the label origin on the point.
		var name = new BMap.Label(WlwdwMap.core.pinLabel(point.title, false),
			{position: p, offset: new BMap.Size(0, 0)});
		// Strip BMap's default padding, border and background: 2px skew plus a white blot
		name.setStyle({padding: '0', border: 'none', backgroundColor: 'transparent'});

		var line = new BMap.Polyline([p, p], {strokeColor: 'red', strokeWeight: 1, strokeOpacity: 0.8});
		handle.addEventListener('dragend', function (e) {
			line.setPositionAt(0, e.point);
			name.setPosition(e.point);
		});

		keep(marker);
		keep(handle);
		keep(name);
		keep(line);
	}

	// Trail point: icon + time label; the label hides on click
	function drawTrailPoint(point) {
		var p = toPoint(point);

		keep(new BMap.Marker(p, {icon: icon(Marker_Pin)}));

		var label = new BMap.Label(WlwdwMap.core.timeLabel(point.time), {
			position: p,
			offset: new BMap.Size(10, -5)
		});
		label.addEventListener('click', function () {
			this.hide();
		});
		keep(label);
	}

	// Reference point: icon + name label; the label hides on click
	function drawAnchor(point) {
		var p = toPoint(point);

		keep(new BMap.Marker(p, {icon: icon(Anchor_Path + point.icon)}));

		var label = new BMap.Label(point.title, {
			position: p,
			offset: new BMap.Size(10, -5)
		});
		label.addEventListener('click', function () {
			this.hide();
		});
		keep(label);
	}

	window.WlwdwMapProvider = {
		init: init,
		redraw: redraw,
		drawTrailPoints: drawTrailPoints,
		fitBounds: fitBounds
	};
})();
