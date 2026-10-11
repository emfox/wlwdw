/**
 * wlwdw map shared layer, vendor-agnostic. A provider implements init / redraw /
 * drawTrailPoints / fitBounds, receives WGS-84 points and converts them itself; it
 * must register its mouse handlers in init only, never on redraw (a timer drives it).
 */
var WlwdwMap = (function () {

	/* ---- helpers ---- */

	// lat and lng both non-zero means a located point (existing rule, do not change)
	function placed(point) {
		return point && point.lat * point.lng != 0;
	}

	// located points of a {key: point} map
	function placedPoints(points) {
		return Object.keys(points).filter(function (key) {
			return placed(points[key]);
		}).map(function (key) {
			return points[key];
		});
	}

	function subtreePoints(node) {
		return zTreeObj.transformToArray(node);
	}

	function round6(value) {
		return Math.round(value * 1000000) / 1000000;
	}

	// Gauss-Kruger: one cached proj4 def per zone, uniquely named so they cannot collide.
	var zoneNames = {};
	function toGaussProj(lng, lat) {
		var zone = Math.floor(lng / 6) * 6 + 3;
		var name = zoneNames[zone];
		if (!name) {
			name = 'WGS84tm' + zone;
			proj4.defs(name, '+proj=tmerc +lat_0=0 +lon_0=' + zone
				+ ' +x_0=500000 +y_0=0 +ellps=WGS84 +datum=WGS84 +units=m +no_defs');
			zoneNames[zone] = name;
		}
		var gauss = proj4(name).forward({x: lng, y: lat});
		gauss.x = Math.round(gauss.x * 100) / 100;
		gauss.y = Math.round(gauss.y * 100) / 100;
		return gauss;
	}

	function provider() {
		var impl = window.WlwdwMapProvider;
		if (!impl) {
			throw new Error('wlwdw-map: no provider loaded');
		}
		return impl;
	}

	/* Point composition, one decision for all providers: icon 32x32 anchored at its
	 * bottom centre, handle 32x18 and name both centred on the point. Only this layer
	 * owns the numbers; providers translate "origin on the point" into their anchors. */
	var pin = {
		icon: {size: 32, anchor: [16, 32]},
		handle: {w: 32, h: 18, anchor: [16, 9]}
	};

	/* Unit-name label HTML, centred on the point from inside the element (only the text
	 * knows its own width). interactive = this label is itself the drag surface; when
	 * false it must let pointer events through or it swallows the handle's drags. */
	function pinLabel(text, interactive) {
		var style = 'display:inline-block;vertical-align:top;transform:translate(-50%,-50%)';
		if (!interactive) {
			style += ';pointer-events:none';
		}
		return '<span class="labels" style="' + style + '">' + text + '</span>';
	}

	/* ---- entry points the page templates call ---- */

	function initMap() {
		provider().init('map');
	}

	function reloadOverlay() {
		provider().redraw(
			placedPoints(anchorNodes),
			placedPoints(subtreePoints(zTreeObj.getNodes()))
		);
	}

	function setCurLocation(treeNode) {
		var points = placedPoints(subtreePoints(treeNode));
		if (points.length > 0) {
			provider().fitBounds(points);
		}
	}

	function addTrail(trail) {
		reloadOverlay();
		provider().drawTrailPoints(placedPoints(trail));
	}

	/* ---- for providers; points are already WGS-84 ---- */

	function hoverText(point) {
		var p = {lat: round6(point.lat), lng: round6(point.lng)};
		var gauss = toGaussProj(p.lng, p.lat);
		return '直角坐标：' + gauss.x + ',' + gauss.y + '<br />GPS坐标：' + p.lng + ',' + p.lat;
	}

	function recordPicked(point) {
		var p = {lat: round6(point.lat), lng: round6(point.lng)};
		var gauss = toGaussProj(p.lng, p.lat);
		$('#mouse_pos_gauss').val(gauss.x + ',' + gauss.y);
		$('#mouse_pos_wgs84').val(p.lng + ',' + p.lat);
	}

	/* ---- text shared with providers ---- */

	function pointInfo(point) {
		return '<p>单位：' + point.title + '</p><p>更新时间：' + point.updatetime.date + '</p>';
	}

	function timeLabel(time) {
		return new Date(time).toLocaleTimeString('en-US', {hour12: false});
	}

	return {
		initMap: initMap,
		reloadOverlay: reloadOverlay,
		setCurLocation: setCurLocation,
		addTrail: addTrail,
		core: {
			hoverText: hoverText,
			recordPicked: recordPicked,
			pointInfo: pointInfo,
			timeLabel: timeLabel,
			pin: pin,
			pinLabel: pinLabel
		}
	};
})();

// global names the templates have always used
var initMap = WlwdwMap.initMap;
var reloadOverlay = WlwdwMap.reloadOverlay;
var setCurLocation = WlwdwMap.setCurLocation;
var addTrail = WlwdwMap.addTrail;
