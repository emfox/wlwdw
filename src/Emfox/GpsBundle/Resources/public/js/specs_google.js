var activeNodes = new Array();
var coordsType = "gcj";

function googlemapClearOverlays(){
	Object.keys(activeNodes).forEach(function(key){
		activeNodes[key].setMap(null);
	});
	activeNodes.length = 0;
	if(labelPos)
		labelPos.setMap(null);
}

function initMap(){
	var mapOptions = {
			center: new google.maps.LatLng(39.925,116.384),
			zoom: 15,
			mapTypeId: google.maps.MapTypeId.ROADMAP
			};
	map = new google.maps.Map(document.getElementById("map"),
              mapOptions);
	google.maps.event.addListener(map, 'maptypeid_changed', function() {
		var typeid = map.getMapTypeId();
		if(typeid == google.maps.MapTypeId.ROADMAP || typeid == google.maps.MapTypeId.TERRAIN){
			if(coordsType != "gcj"){
				coordsType = "gcj";
				reloadOverlay();
			}
		}
		if(typeid == google.maps.MapTypeId.SATELLITE || typeid == google.maps.MapTypeId.HYBRID){
			if(coordsType != "wgs"){
				coordsType = "wgs";
				reloadOverlay();
			}
		}
	  });

}

function reloadOverlay(){
	googlemapClearOverlays();
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
	labelPos = new MarkerWithLabel({
		position: new google.maps.LatLng(0,0),
    	icon: " ",
    	optimized: false,
    	zIndex: 2,
    	map: map,
    	labelContent: "坐标：",
    	labelClass: "labels",
    	labelAnchor: new google.maps.Point(-35,12)});
	google.maps.event.addListener(map,"mousemove",function(e){
		labelPos.setPosition(e.latLng);
		var p = new Array();
		p.lat = e.latLng.lat();
		p.lng = e.latLng.lng();
		if(coordsType == "gcj"){
			p=gcj2wgs(p);
		}
		p.lng=Math.round(p.lng*1000000)/1000000;
		p.lat=Math.round(p.lat*1000000)/1000000;
		gauss = toGaussProj(p.lng,p.lat);
		var c = "直角坐标："+gauss.x+","+ gauss.y + "<br />GPS坐标：" + p.lng + "," + p.lat;
		labelPos.set("labelContent",c);
		});
	google.maps.event.addListener(map,"click",function(e){
		var p = new Array();
		p.lat = e.latLng.lat();
		p.lng = e.latLng.lng();
		if(coordsType == "gcj"){
			p=gcj2wgs(p);
		}
		p.lng=Math.round(p.lng*1000000)/1000000;
		p.lat=Math.round(p.lat*1000000)/1000000;
		gauss = toGaussProj(p.lng,p.lat);
		$('#mouse_pos_gauss').val(gauss.x+","+gauss.y);
		$('#mouse_pos_wgs84').val(p.lng+","+p.lat);
		});
	google.maps.event.addListener(map,"mouseover",function(e){
		labelPos.setMap(map);
		});
	google.maps.event.addListener(map,"mouseout",function(e){
		labelPos.setMap(null);
		});
}

function setCurLocation(treeNode){
    var nodes = zTreeObj.transformToArray(treeNode);
    var bound = new google.maps.LatLngBounds();
    var ggpoint;
    Object.keys(nodes).forEach(function(key) {
        if(nodes[key].lat*nodes[key].lng !=0){
    		if(coordsType == "gcj"){
    			ggpoint=wgs2gcj(nodes[key]);
    		}else{
    			ggpoint=nodes[key];
    		}
            bound.extend(new google.maps.LatLng(ggpoint.lat,ggpoint.lng));
        }
    });
    if (!bound.isEmpty())
    	map.fitBounds(bound);
}

function addMarker_p(point){ //添加所有单位的当前点
	if(coordsType == "gcj"){
		ggpoint=wgs2gcj(point);
	}else{
		ggpoint = point;
	}
    var p = new google.maps.LatLng(ggpoint.lat,ggpoint.lng);
    var myIcon = new google.maps.MarkerImage(Marker_Pointer,
    	    new google.maps.Size(32, 32),
    	    new google.maps.Point(0,0), //Origin
    	    new google.maps.Point(16, 32)); //Anchor
    var marker = new google.maps.Marker({
    	position: p,
    	icon: myIcon,
    	map: map,
    	optimized: false,
    	zIndex: 1});
    activeNodes.push(marker);
    var label = new MarkerWithLabel({
    	position: p,
    	icon: " ",
    	draggable: true,
    	raiseOnDrag: false,
    	map: map,
    	optimized: false,
    	zIndex: 0,
    	labelContent: point.title,
    	labelClass: "labels",
    	labelAnchor: new google.maps.Point(0,0)});
    activeNodes.push(label);
    var line = new google.maps.Polyline({
    	path: [p,p],
    	strokeColor: "#FF0000",
        strokeOpacity: 0.8,
        strokeWeight: 1,
    	map: map
    });
    activeNodes.push(line);

    content = "<p>单位："+point.title+"</p><p>更新时间："+ point.updatetime.date+"</p>";
 
    var infoWindow = new google.maps.InfoWindow({content:content});    
    google.maps.event.addListener(marker, "click", function(){            
    	infoWindow.open(map,label);  
    });
    google.maps.event.addListener(label, "click", function(){            
    	infoWindow.open(map,label);  
    });
    google.maps.event.addListener(label, "dragend", function(e){            
    	line.getPath().setAt(0,e.latLng);  
    });

}

function addMarker_t(point){ //添加某一单位的历史点
	if(coordsType == "gcj"){
		ggpoint=wgs2gcj(point);
	}else{
		ggpoint = point;
	}
    var p = new google.maps.LatLng(ggpoint.lat,ggpoint.lng);
    var myIcon = new google.maps.MarkerImage(Marker_Pin,
    	    new google.maps.Size(32, 32),
    	    new google.maps.Point(0,0), //Origin
    	    new google.maps.Point(16, 32)); //Anchor
    var marker = new google.maps.Marker({
    	position: p,
    	icon: myIcon,
    	map: map});
    activeNodes.push(marker);
    var label = new MarkerWithLabel({
    	position: p,
    	icon: " ",
    	draggable: true,
    	raiseOnDrag: false,
    	map: map,
    	labelContent: new Date(point.time).toLocaleTimeString("en-US", {hour12: false}),
    	labelClass: "labels",
    	labelAnchor: new google.maps.Point(10,-5)});
    activeNodes.push(label);
    google.maps.event.addListener(label, "click", function(){            
        this.setMap(null);  
    });
}

function addMarker_a(point){ //添加固定参考点
	if(coordsType == "gcj"){
		ggpoint=wgs2gcj(point);
	}else{
		ggpoint = point;
	}
    var p = new google.maps.LatLng(ggpoint.lat,ggpoint.lng);
    var myIcon = new google.maps.MarkerImage(Anchor_Path + point.icon,
    	    new google.maps.Size(32, 32),
    	    new google.maps.Point(0,0), //Origin
    	    new google.maps.Point(16, 32)); //Anchor
    var marker = new google.maps.Marker({
    	position: p,
    	icon: myIcon,
    	map: map});
    activeNodes.push(marker);
    var label = new MarkerWithLabel({
    	position: p,
    	icon: " ",
    	draggable: true,
    	raiseOnDrag: false,
    	map: map,
    	labelContent: point.title,
    	labelClass: "labels",
    	labelAnchor: new google.maps.Point(10,-5)});
    activeNodes.push(label);
}

function addTrail(trail){
    reloadOverlay();
    Object.keys(trail).forEach(function(key) {
        if(trail[key].lat*trail[key].lng !=0)
        	addMarker_t(trail[key]);
    });
}
