/**
 * wlwdw 地图共享层 —— 与地图厂商无关的部分。
 *
 * 这里放"画什么"：数据遍历、点位筛选、坐标文案、高斯投影，以及模板调用的四个入口。
 * "怎么画"全部交给 provider。
 *
 * 加载顺序：本文件 → 一个 provider（挂到 window.WlwdwMapProvider）→ 页面脚本。
 *
 * ── provider 契约 ──────────────────────────────────────────────────────────
 * 只需实现四个方法。传入的坐标一律是后端给的 WGS-84 原始数据点，换算由 provider
 * 自己负责（因为各家的显示坐标系不同，见各 provider 头部说明）。
 *
 *   init(containerId)
 *       创建地图，并在此注册一次跟随鼠标的坐标标签与 mousemove / click：
 *         mousemove → 换算回 WGS-84 → 取 WlwdwMap.core.hoverText() 更新标签
 *         click     → 换算回 WGS-84 → 调 WlwdwMap.core.recordPicked()
 *       只在这里注册。本方法只在页面初始化时被调用一次，重绘时不得再注册 ——
 *       历史实现把标签和监听都放在重绘里，而重绘由定时刷新驱动，导致地图上的
 *       事件监听无限累积（鼠标每动一下都要跑 N 份相同的换算）。
 *
 *   redraw(anchors, pins)
 *       清空本层覆盖物，重画参考点与单位当前点。清空与重画都归 provider 掌管，
 *       因为覆盖物层序（谁能压住谁）各厂商规则不同。
 *
 *   drawTrailPoints(trails)
 *       在现有内容之上追加历史点。
 *
 *   fitBounds(wgsPoints)
 *       把视口套到这些点上。
 *
 * provider 可复用的文案与构图：WlwdwMap.core.pointInfo(point)、
 * WlwdwMap.core.timeLabel(time)、WlwdwMap.core.pin（单位当前点的尺寸与锚点）、
 * WlwdwMap.core.pinLabel(title, interactive)（单位名标签的 HTML —— 它以自身
 * 中心对齐坐标，provider 只要把它放在坐标上）。图标路径来自模板注入的全局变量
 * Marker_Pointer / Marker_Blank / Marker_Pin / Anchor_Path。
 *
 * 注意：历史点与参考点的标签可拖动性、点击响应，两个 provider 目前并不一致，
 * 属既有差异，已按各自原行为保留。要统一就改各自的 provider。
 * ──────────────────────────────────────────────────────────────────────────
 */
var WlwdwMap = (function () {

	/* ---- 内部工具 ---- */

	// lat、lng 同时非 0 才算有效的已定位点（沿用既有判定，勿改）
	function placed(point) {
		return point && point.lat * point.lng != 0;
	}

	// 从 {key: point} 里取出所有已定位的点
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

	// 高斯投影。zone 取值有限，按 zone 缓存 proj4 定义 —— 否则鼠标每移动一次
	// 都要重新解析注册一遍投影参数；同时每个 zone 用独立的名字，避免互相覆盖。
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
			throw new Error('wlwdw-map: 没有加载 provider');
		}
		return impl;
	}

	/* ---- 点位构图（三个 provider 共用同一份）----
	 *
	 * 坐标点上要叠三样东西，而"谁跟谁对齐"是个决定，不是各家的自由：
	 *
	 *   图标   32x32，锚底边中点   —— 锥尖指着坐标
	 *   手柄   32x18，锚正中心     —— 拖拽面，方框**以坐标为中心**
	 *   名称   以自身中心对齐坐标  —— 于是文字正好落在手柄方框里（重合）
	 *
	 * 为什么居中而不是左上角对齐：手柄是"把标记挪到别处"的抓取面，抓取面的中心
	 * 才是它代表的那个位置；而锥尖（一个点）本来就是居中，两种约定混用会让同一个
	 * 坐标在图上对应两个地方。尺寸与锚点只写在这里，谁都不许再各写一份。
	 *
	 * provider 负责的只有"翻译"：百度 Marker 的 anchor、天地图 Icon 的 iconAnchor
	 * 与 DivIcon 的取负 margin、google 的 MarkerImage anchor 与 labelAnchor ——
	 * 换算规则各家不同，所以留在 provider（见本文件顶部的契约说明）。
	 */
	var pin = {
		icon: {size: 32, anchor: [16, 32]},
		handle: {w: 32, h: 18, anchor: [16, 9]}
	};

	/*
	 * 单位名标签的 HTML —— **以自身中心对齐坐标**。
	 *
	 * 居中由元素自己算：文字宽度取决于单位名，锚点算不出来；把 translate 写进元素，
	 * provider 就只需要"把这个元素的左上角放在坐标上"，而这是三家 SDK 的共同行为
	 * （百度 Label 的 offset(0,0)、天地图 DivIcon 的 iconAnchor(0,0)、google
	 * MarkerWithLabel 的 labelAnchor(0,0)）。这就是通用方法 —— 不靠百度那句
	 * marker.setLabel()，天地图没有那个 API。
	 *
	 * interactive：这个标签自己是不是拖拽面。
	 *   百度与天地图的拖拽面是另一个覆盖物（BMap.Label 没有 enableDragging，只有
	 *   带透明图标的 Marker；天地图的 DivIcon 同样只是一个内容容器），所以标签必须
	 *   让指针事件穿过去，否则文字会盖住手柄、把拖拽整个吃掉 —— 改之前两边都没重合，
	 *   这个冲突看不出来，重合之后它就必然出现。
	 *   Google 的 MarkerWithLabel 没有独立手柄、标签就是拖拽面，必须保留事件。
	 *
	 * 样式全部写在元素上（不依赖页面里的 CSS，三处宿主只有 .labels 一份共同的）：
	 *   display:inline-block  宽度由内容撑开 —— 天地图那个包裹 div 的宽度是 0，块级
	 *                     元素会跟着算成 0；inline-block 在 0 宽里也能用文字宽度撑开
	 *                     （文字不换行，最小内容宽度就是整串文字）；
	 *   vertical-align:top    inline-block 会被行框的基线推下去几个 px（天地图的
	 *                     包裹 div 行高 18px、字号 10px，实测推下 3px），顶对齐即可；
	 *   transform            居中 —— 只有元素自己知道文字多宽，锚点算不出来。
	 * 不用 width:fit-content：老 Safari 上要加前缀，inline-block 到处都一样。
	 */
	function pinLabel(text, interactive) {
		var style = 'display:inline-block;vertical-align:top;transform:translate(-50%,-50%)';
		if (!interactive) {
			style += ';pointer-events:none';
		}
		return '<span class="labels" style="' + style + '">' + text + '</span>';
	}

	/* ---- 模板调用的四个入口 ---- */

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

	/* ---- 供 provider 调用，传入的坐标须已换算回 WGS-84 ---- */

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

	/* ---- 供 provider 复用的文案 ---- */

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

// 模板沿用以来的全局函数名
var initMap = WlwdwMap.initMap;
var reloadOverlay = WlwdwMap.reloadOverlay;
var setCurLocation = WlwdwMap.setCurLocation;
var addTrail = WlwdwMap.addTrail;
