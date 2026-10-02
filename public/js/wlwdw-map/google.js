/**
 * Google Maps provider。
 *
 * 坐标系策略是**动态**的：Google 在路线图 / 地形图下显示 GCJ-02，卫星图 / 混合图下
 * 显示 WGS-84。所以跟随 maptypeid_changed 切换换算方向，切换后重绘一次。
 * 这一点和百度不同（百度不随地图类型改变坐标系），也是"换算必须由 provider 负责"
 * 的原因 —— 核心层不该知道这种规则。
 */
(function () {

	var map;
	var labelPos;
	var coordsType = 'gcj';
	var drawn = [];

	/* ---- 坐标系 ---- */

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

	/* ---- 基础绘制 ---- */

	function toLatLng(point) {
		var native = toNative(point);
		return new google.maps.LatLng(native.lat, native.lng);
	}

	function icon(url) {
		return new google.maps.MarkerImage(url,
			new google.maps.Size(32, 32),
			new google.maps.Point(0, 0),   // origin
			new google.maps.Point(16, 32)); // anchor
	}

	// 记录本层画过的覆盖物。Google 没有"清空全部覆盖物"的接口，必须自己留账。
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

	/* ---- provider 契约实现 ---- */

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

		// 跟随鼠标的坐标标签，只在 init 建一次（见 core.js 契约里的说明）。
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

	/* ---- 三种点位的画法 ---- */

	// 单位当前点：图标 + 可拖拽的名称标签 + 一条跟随拖动的引线
	function drawPin(point) {
		var p = toLatLng(point);

		var marker = keep(new google.maps.Marker({
			position: p,
			icon: icon(Marker_Pointer),
			map: map,
			optimized: false,
			zIndex: 1
		}));

		var label = keep(new MarkerWithLabel({
			position: p,
			icon: ' ',
			draggable: true,
			raiseOnDrag: false,
			map: map,
			optimized: false,
			zIndex: 0,
			labelContent: point.title,
			labelClass: 'labels',
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

	// 历史点：图标 + 时间标签。Google 侧标签可拖动、点击隐藏（沿用原行为）。
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

	// 参考点：图标 + 名称标签。Google 侧标签可拖动且点击无响应（沿用原行为）。
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
