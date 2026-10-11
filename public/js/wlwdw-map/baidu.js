/**
 * 百度地图 provider。
 *
 * 坐标系策略是**静态**的：百度 SDK 无论哪种地图类型都使用 BD-09，而后端给的是
 * WGS-84，所以显示恒做 wgs2bd、取点恒做 bd2wgs。这里没有 Google 那套随地图类型
 * 切换 coordinate system 的规则。
 */
(function () {

	var map;
	var labelPos;
	var drawn = [];

	/* ---- 坐标系 ---- */

	function toNative(point) {
		return eviltransform.wgs2bd(point.lat, point.lng);
	}

	function fromNative(native) {
		return eviltransform.bd2wgs(native.lat, native.lng);
	}

	/* ---- 基础绘制 ---- */

	// 注意 BMap.Point 的参数顺序是 (lng, lat)，与 WGS-84 数据点的 (lat, lng) 相反。
	function toPoint(point) {
		var native = toNative(point);
		return new BMap.Point(native.lng, native.lat);
	}

	// 尺寸与锚点取自 core.js 的 pin：三家 provider 共用同一份构图，谁也不另写一份。
	function icon(url) {
		var layout = WlwdwMap.core.pin.icon;
		return new BMap.Icon(url, new BMap.Size(layout.size, layout.size),
			{anchor: new BMap.Size(layout.anchor[0], layout.anchor[1])});
	}

	// 记录本层画过的覆盖物。不用 map.clearOverlays()，因为它会把跟随鼠标的
	// 坐标标签一起清掉，而那个标签是 init 时建一次、要长期存在的。
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

	// 覆盖物层序按加入顺序排，标签在 init 时最先加入，会被后画的点位压住，
	// 所以每次重绘后重新提一遍（原实现把标签放在重绘末尾创建，效果相同）。
	function bumpHoverLabel() {
		map.removeOverlay(labelPos);
		map.addOverlay(labelPos);
	}

	/* ---- provider 契约实现 ---- */

	function init(containerId) {
		map = new BMap.Map(containerId, {mapType: BMAP_NORMAL_MAP});
		map.centerAndZoom(new BMap.Point(116.384, 39.925), 15);
		map.setDefaultCursor('default');
		map.addControl(new BMap.NavigationControl({type: BMAP_NAVIGATION_CONTROL_LARGE}));
		map.addControl(new BMap.MapTypeControl({
			mapTypes: [BMAP_NORMAL_MAP, BMAP_SATELLITE_MAP, BMAP_HYBRID_MAP]
		}));

		// 跟随鼠标的坐标标签，只在 init 建一次（见 core.js 契约里的说明）。
		// 这里不做 mouseover / mouseout 显隐：地图上有折线时 mouseover 不触发，
		// 显隐会导致标签消失后不再出现。
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

	/* ---- 三种点位的画法 ---- */

	// 单位当前点：图标 + 一张透明图标做的拖拽手柄 + 挂在手柄上的单位名标签
	// + 一条跟随拖动的引线。构图（各覆盖物多大、锚在哪）见 core.js 的 pin。
	function drawPin(point) {
		var p = toPoint(point);
		var layout = WlwdwMap.core.pin;
		var infoWindow = new BMap.InfoWindow(WlwdwMap.core.pointInfo(point));

		var marker = new BMap.Marker(p, {icon: icon(Marker_Pointer)});
		marker.setZIndex(0);
		marker.addEventListener('click', function () {
			this.openInfoWindow(infoWindow);
		});

		// 手柄：32x18 的透明图标，锚点在正中心 —— 方框以坐标为中心。
		// ⚠️ 不要在这里再加 marker 的 offset。BMap.Icon 不给 anchor 时默认就是
		// 正中心，老代码那笔 offset(16,9) 是重复补偿：实测它把方框推到了坐标的
		// **右下角**（方框从坐标开始，而不是以坐标为中心），与天地图差半个手柄。
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

		// 单位名：不用 handle.setLabel()（百度专有，天地图没有），改成与天地图同一
		// 套做法 —— 独立标签 + core.pinLabel 里那个自己居中的元素。offset(0,0) 即
		// "标签原点就在坐标上"（实测），居中由元素自己算，所以拖拽时把它带走。
		var name = new BMap.Label(WlwdwMap.core.pinLabel(point.title, false),
			{position: p, offset: new BMap.Size(0, 0)});
		// BMap 给标签的默认样式是 1px 内边距 + 1px 边框 + 白底：前两个会把内容
		// 推偏 2px，白底则会在原点处留下一块方块（标签只负责定位，外观归 .labels）。
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

	// 历史点：图标 + 时间标签。百度侧标签不可拖动、点击隐藏（沿用原行为）。
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

	// 参考点：图标 + 名称标签。百度侧同上，标签不可拖动、点击隐藏。
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
