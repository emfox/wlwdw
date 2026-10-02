/**
 * 天地图 provider。
 *
 * 坐标系策略是**恒等**的：天地图底图用 CGCS2000，与 WGS-84 的差异约 15cm，在定位
 * 记录的精度上可以忽略，所以显示与取点都不做换算。这是第三种策略 —— 百度恒 BD-09、
 * Google 随地图类型在 GCJ-02/WGS-84 间切换、这里恒等 —— 也再次说明换算必须由
 * provider 负责：核心层不该知道这种规则。
 *
 * 三个落地细节和另外两个 provider 不一样，都是被这个 SDK 的加载方式逼出来的：
 *
 *  1. 只用主包（api?v=4.0）里**同步**可用的类：T.Map / T.Marker / T.Icon /
 *     T.DivIcon / T.Polyline / T.InfoWindow / T.Control.Zoom / T.Control.Scale。
 *     SDK 用 createElement + appendChild 异步注入 components.js，所以 T.Label 和
 *     地图类型切换用的 TMAP_* 常量在 init() 跑的时候**可能还没定义**；new T.Label
 *     会让整个 ready 处理器抛异常、地图完全不显示。因此文字标注一律用
 *     Marker + DivIcon（DivIcon 把 html 写进 div 的 innerHTML），也就没有加地图
 *     类型控件 —— 卫星图切换要等 components.js，值得单独处理。
 *
 *  2. 跟随鼠标的坐标标签用**普通 DOM**，不用覆盖物。它每 mousemove 都要改文字，而
 *     Marker 没有公开的"改图标内容"入口（setIcon 会重建图标并重新注册
 *     mouseover/mouseout，等于把监听泄漏又引进来）。事件对象自带 containerPoint，
 *     直接把一个 div 摆进地图容器最省事，也不必在平移缩放时重投影。
 *
 *  3. 单位名标签跟着拖拽走，靠 Marker.setLngLat() 只重算位置、不重建 DOM；内容不变
 *     就不碰图标。
 *
 * 与 baidu.js 的一处简化：天地图的覆盖物按 Leaflet 的 pane 分层（popupPane 在
 * markerPane 之上），不存在百度那种"覆盖物按加入顺序排、悬停标签会被压住"的问题，
 * 所以 baidu.js 里重绘后重新提一遍标签的补救在这里是多余的。
 */
(function () {

	var map;
	var hover;
	var drawn = [];

	/* ---- 坐标系 ---- */

	// 恒等：天地图底图是 CGCS2000（≈WGS-84），无需纠偏。
	function toNative(point) {
		return {lat: point.lat, lng: point.lng};
	}

	function fromNative(lnglat) {
		return {lat: lnglat.getLat(), lng: lnglat.getLng()};
	}

	/* ---- 基础绘制 ---- */

	// 注意 T.LngLat 的参数顺序是 (lng, lat)，与 WGS-84 数据点的 (lat, lng) 相反。
	function toLngLat(point) {
		var native = toNative(point);
		return new T.LngLat(native.lng, native.lat);
	}

	function icon(url, w, h, anchorX, anchorY) {
		return new T.Icon({
			iconUrl: url,
			iconSize: new T.Point(w, h),
			iconAnchor: new T.Point(anchorX, anchorY)
		});
	}

	// DivIcon 把 iconAnchor 实现成 marginLeft / -marginTop 的**取负**（见主包里 Icon.er），
	// 所以想"向右 dx、向上 dy"得传 (-dx, dy)。统一按人话传，换算只此一处。
	// iconSize 传 0 是故意的：div 不设尺寸，宽度交给 .labels 的内容撑开。
	function textLabel(text, dx, dy) {
		return new T.DivIcon({
			html: '<span class="labels">' + text + '</span>',
			iconSize: new T.Point(0, 0),
			iconAnchor: new T.Point(-dx, dy)
		});
	}

	// 记录本层画过的覆盖物。removeOverLay 对已经移除的图层是空操作，所以标签被点掉
	// 之后依然留在 drawn 里也不会出错。
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

	/* ---- provider 契约实现 ---- */

	function init(containerId) {
		map = new T.Map(containerId);
		map.centerAndZoom(new T.LngLat(116.384, 39.925), 15);
		map.enableScrollWheelZoom();

		map.addControl(new T.Control.Zoom());
		map.addControl(new T.Control.Scale());

		// 跟随鼠标的坐标标签，只在 init 建一次（见 core.js 契约里的说明）。
		// 不做 mouseout 隐藏：地图上有折线时 mouseover 不一定触发，隐藏之后就再也不
		// 出现了（baidu.js 踩过，这里沿用它的取舍）。这里多挂一个 mouseout 也不会
		// 泄漏，但它带来的行为更差，索性不挂。
		hover = document.createElement('div');
		hover.className = 'labels';
		hover.style.position = 'absolute';
		hover.style.pointerEvents = 'none';
		hover.style.left = '12px';
		hover.style.top = '0px';
		hover.innerHTML = '坐标：';
		document.getElementById(containerId).appendChild(hover);

		map.addEventListener('mousemove', function (e) {
			// containerPoint 是相对地图容器左上角的像素坐标，直接当 left/top 用。
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

	/* ---- 三种点位的画法 ---- */

	// 单位当前点：图标 + 一张透明图标做的拖拽手柄（挂单位名标签）+ 一条跟随拖动的引线
	function drawPin(point) {
		var p = toLngLat(point);
		var infoWindow = new T.InfoWindow(WlwdwMap.core.pointInfo(point));

		var marker = keep(new T.Marker(p, {icon: icon(Marker_Pointer, 32, 32, 16, 32)}));
		marker.addEventListener('click', function () {
			marker.openInfoWindow(infoWindow);
		});

		// 手柄用透明图标、锚点居中 —— 对应 baidu.js 里 32x18 的图标加 (16,9) 偏移。
		var handle = keep(new T.Marker(p, {
			icon: icon(Marker_Blank, 32, 18, 16, 9),
			draggable: true
		}));
		handle.addEventListener('click', function () {
			handle.openInfoWindow(infoWindow);
		});

		// 天地图没有 Marker.setLabel，单位名是独立的标注点，拖拽时要自己跟过去。
		var title = keep(new T.Marker(p, {icon: textLabel(point.title, 0, 0)}));

		var line = keep(new T.Polyline([p, p], {color: 'red', weight: 1, opacity: 0.8}));
		handle.addEventListener('dragend', function (e) {
			var moved = e.target.getLngLat();
			line.setLngLats([moved, p]);
			title.setLngLat(moved);
		});
	}

	// 历史点：图标 + 时间标签。标签点一下移除（沿用 baidu.js 的行为）。
	function drawTrailPoint(point) {
		var p = toLngLat(point);

		keep(new T.Marker(p, {icon: icon(Marker_Pin, 32, 32, 16, 32)}));

		var time = keep(new T.Marker(p, {icon: textLabel(WlwdwMap.core.timeLabel(point.time), 10, 5)}));
		time.addEventListener('click', function () {
			map.removeOverLay(time);
		});
	}

	// 参考点：图标 + 名称标签。同上。
	function drawAnchor(point) {
		var p = toLngLat(point);

		keep(new T.Marker(p, {icon: icon(Anchor_Path + point.icon, 32, 32, 16, 32)}));

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
