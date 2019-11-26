function initMap(){
	map = new BMap.Map("map",{mapType: BMAP_NORMAL_MAP});
	map.centerAndZoom(new BMap.Point(116.384, 39.925), 15);
	map.setDefaultCursor("default");
	map.addControl(new BMap.NavigationControl({type: BMAP_NAVIGATION_CONTROL_LARGE}));
	map.addControl(new BMap.MapTypeControl({mapTypes: [BMAP_NORMAL_MAP,BMAP_SATELLITE_MAP,BMAP_HYBRID_MAP]}));
}

function reloadOverlay(){
    map.clearOverlays();
    Object.keys(anchorNodes).forEach(function(key) {
        if(anchorNodes[key].lat*anchorNodes[key].lng !=0){
            addMarker_a(anchorNodes[key]);
        }

    });
    var nodes = zTreeObj.transformToArray(zTreeObj.getNodes());
    Object.keys(nodes).forEach(function(key) {
        if(nodes[key].lat*nodes[key].lng !=0)
        	addMarker_p(nodes[key]);
    });
    locLabel();
}

function locLabel(){
    opts = {
  		  offset   : new BMap.Size(12, -35)    //设置文本偏移量
  		}
	labelPos = new BMap.Label("坐标：", opts);
  map.addOverlay(labelPos);
  map.addEventListener("mousemove",function(e){
      labelPos.setPosition(e.point);
      var p=bd2wgs(e.point);
      p.lng=Math.round(p.lng*1000000)/1000000;
      p.lat=Math.round(p.lat*1000000)/1000000;
      gauss = toGaussProj(p.lng,p.lat);
      var c = "直角坐标："+gauss.x+","+ gauss.y + "<br />GPS坐标：" + p.lng + "," + p.lat;
      labelPos.setContent(c);
  });
  map.addEventListener("click",function(e){
      var p=bd2wgs(e.point);
      p.lng=Math.round(p.lng*1000000)/1000000;
      p.lat=Math.round(p.lat*1000000)/1000000;
      gauss = toGaussProj(p.lng,p.lat);
      $('#mouse_pos_gauss').val(gauss.x+","+gauss.y);
      $('#mouse_pos_wgs84').val(p.lng+","+p.lat);
  });
// temp disable hide labelPos, cause the bug of mouseover not triggered if map has polylines.
//  map.addEventListener("mouseover",function(e){
//		labelPos.show();
//  });
//  map.addEventListener("mouseout",function(e){
//		labelPos.hide();
//  });
}

function setCurLocation(treeNode){
    var nodes = zTreeObj.transformToArray(treeNode);
    var bdpoints = new Array();
    Object.keys(nodes).forEach(function(key) {
        if(nodes[key].lat*nodes[key].lng !=0){
            bdpoint = wgs2bd(nodes[key]);
            p = new BMap.Point(bdpoint.lng,bdpoint.lat);
            bdpoints.push(p);
        }
    });
    if (bdpoints.length>0)
    	map.setViewport(bdpoints,{zoomFactor:-1});
}

function addMarker_p(point){ //添加所有单位的当前点
    bdpoint=wgs2bd(point);
    var p = new BMap.Point(bdpoint.lng,bdpoint.lat);
    content = "<p>单位："+point.title+"</p><p>更新时间："+ point.updatetime.date+"</p>";
    var myIcon = new BMap.Icon(Marker_Pointer,
    	    new BMap.Size(32, 32), {anchor: new BMap.Size(16, 32)});
    var marker = new BMap.Marker(p, {icon: myIcon});  
    var infoWindow = new BMap.InfoWindow(content);   
    map.addOverlay(marker);
    marker.addEventListener("click", function(){            
        this.openInfoWindow(infoWindow);
    });
    marker.setZIndex(0);
    opts = {
  		  offset   : new BMap.Size(16, 9),
  		  icon: new BMap.Icon(Marker_Blank,
  				  new BMap.Size(32, 18),
  				  {imageSize:BMap.Size(32, 18)})
  		}
	var label = new BMap.Marker(p, opts);
    map.addOverlay(label);
    label.enableDragging();
    label.setLabel(new BMap.Label(point.title));
    label.setZIndex(1);

    label.addEventListener("click", function(){
    	label.openInfoWindow(infoWindow);
    });
    var line = new BMap.Polyline([p,p], {strokeColor:"red", strokeWeight:1, strokeOpacity:0.8});
    map.addOverlay(line);
    label.addEventListener("dragend", function(e){
    	line.setPositionAt(0,e.point);
    });
}

function addMarker_t(point){ //添加某一单位的历史点
    bdpoint=wgs2bd(point);
    var p = new BMap.Point(bdpoint.lng,bdpoint.lat);
    var myIcon = new BMap.Icon(Marker_Pin,
    	    new BMap.Size(32, 32), {anchor: new BMap.Size(16, 32)});
    var marker = new BMap.Marker(p, {icon: myIcon});    
    map.addOverlay(marker);  
    opts = {
    		  position : p,    // 指定文本标注所在的地理位置
    		  offset   : new BMap.Size(10, -5)    //设置文本偏移量
    		}
  	var label = new BMap.Label(new Date(point.time).toLocaleTimeString("en-US", {hour12: false}), opts);
  	map.addOverlay(label);
      label.addEventListener("click", function(){            
          this.hide();  
      });
}

function addMarker_a(point){ //添加固定参考点
    bdpoint=wgs2bd(point);
    var p = new BMap.Point(bdpoint.lng,bdpoint.lat);
    var myIcon = new BMap.Icon(Anchor_Path + point.icon,
    	    new BMap.Size(32, 32), {anchor: new BMap.Size(16, 32)});
    var marker = new BMap.Marker(p, {icon: myIcon});    
    map.addOverlay(marker);  
    opts = {
    		  position : p,    // 指定文本标注所在的地理位置
    		  offset   : new BMap.Size(10, -5)    //设置文本偏移量
    		}
  	var label = new BMap.Label(point.title, opts);
  	map.addOverlay(label);
      label.addEventListener("click", function(){            
          this.hide();  
      });
}

function addTrail(trail){
    reloadOverlay();
    Object.keys(trail).forEach(function(key) {
        if(trail[key].lat*trail[key].lng !=0)
        	addMarker_t(trail[key]);
    });
}
