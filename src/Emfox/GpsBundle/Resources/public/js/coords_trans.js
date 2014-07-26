function outOfChina(lat, lng) {
	if ((lng < 72.004) || (lng > 137.8347)) {
		return true;
	}
	if ((lat < 0.8293) || (lat > 55.8271)) {
		return true;
	}
	return false;
}

function transformLat(x, y) {
	var ret = -100.0 + 2.0*x + 3.0*y + 0.2*y*y + 0.1*x*y + 0.2*Math.sqrt(Math.abs(x));
	ret += (20.0*Math.sin(6.0*x*Math.PI) + 20.0*Math.sin(2.0*x*Math.PI)) * 2.0 / 3.0;
	ret += (20.0*Math.sin(y*Math.PI) + 40.0*Math.sin(y/3.0*Math.PI)) * 2.0 / 3.0;
	ret += (160.0*Math.sin(y/12.0*Math.PI) + 320*Math.sin(y*Math.PI/30.0)) * 2.0 / 3.0;
	return ret;
}

function transformLon(x, y) {
	var ret = 300.0 + x + 2.0*y + 0.1*x*x + 0.1*x*y + 0.1*Math.sqrt(Math.abs(x));
	ret += (20.0*Math.sin(6.0*x*Math.PI) + 20.0*Math.sin(2.0*x*Math.PI)) * 2.0 / 3.0;
	ret += (20.0*Math.sin(x*Math.PI) + 40.0*Math.sin(x/3.0*Math.PI)) * 2.0 / 3.0;
	ret += (150.0*Math.sin(x/12.0*Math.PI) + 300.0*Math.sin(x/30.0*Math.PI)) * 2.0 / 3.0;
	return ret;
}

function delta(lat, lng) {
	var a = 6378245.0;
	var ee = 0.00669342162296594323;
	var dLat = transformLat(lng-105.0, lat-35.0);
	var dLng = transformLon(lng-105.0, lat-35.0);
	var radLat = lat / 180.0 * Math.PI;
	var magic = Math.sin(radLat);
	magic = 1 - ee*magic*magic;
	var sqrtMagic = Math.sqrt(magic);
	dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * Math.PI);
	dLng = (dLng * 180.0) / (a / sqrtMagic * Math.cos(radLat) * Math.PI);
	return {"lat": dLat, "lng": dLng};
}

function wgs2gcj(point) {
	wgsLat = point.lat;
	wgsLng = point.lng;
	if (outOfChina(wgsLat, wgsLng)) {
		return {"lat": wgsLat, "lng": wgsLng};
	}
	var d = delta(wgsLat, wgsLng);
	return {"lat": wgsLat + d.lat, "lng": wgsLng + d.lng};
}

function gcj2wgs(point) {
	gcjLat = point.lat;
	gcjLng = point.lng;
	if (outOfChina(gcjLat, gcjLng)) {
		return {"lat": gcjLat, "lng": gcjLng};
	}
	var d = delta(gcjLat, gcjLng);
	return {"lat": gcjLat - d.lat, "lng": gcjLng - d.lng};
}

function gcj2wgs_exact(gcjLat, gcjLng) {
	var initDelta = 0.01;
	var threshold = 0.000001;
	var dLat = initDelta, dLng = initDelta;
	var mLat = gcjLat-dLat, mLng = gcjLng-dLng;
	var pLat = gcjLat+dLat, pLng = gcjLng+dLng;
	var wgsLat, wgsLng;
	for (var i = 0; i < 30; i++) {
		wgsLat = (mLat+pLat)/2;
		wgsLng = (mLng+pLng)/2;
		var tmp = wgs2gcj(wgsLat, wgsLng)
		dLat = tmp.lat-gcjLat;
		dLng = tmp.lng-gcjLng;
		if ((Math.abs(dLat) < threshold) && (Math.abs(dLng) < threshold)) {
			return {"lat": wgsLat, "lng": wgsLng};
		}
		if (dLat > 0) {
			pLat = wgsLat;
		} else {
			mLat = wgsLat;
		}
		if (dLng > 0) {
			pLng = wgsLng;
		} else {
			mLng = wgsLng;
		}
	}
	return {"lat": wgsLat, "lng": wgsLng};
}

function distance(latA, lngA, latB, lngB) {
	var earthR = 6371000;
	var x = Math.cos(latA*Math.PI/180) * Math.cos(latB*Math.PI/180) * Math.cos((lngA-lngB)*Math.PI/180);
	var y = Math.sin(latA*Math.PI/180) * Math.sin(latB*Math.PI/180);
	var s = x + y;
	if (s > 1) {
		s = 1;
	}
	if (s < -1) {
		s = -1;
	}
	var alpha = Math.acos(s);
	var distance = alpha * earthR;
	return distance;
}


/// <summary>
/// 中国正常坐标系GCJ02协议的坐标，转到 百度地图对应的 BD09 协议坐标
///  point 为传入的对象，例如{lat:xxxxx,lng:xxxxx}
/// </summary>
function gcj2bd(point) {
	var x_pi = Math.PI * 3000.0 / 180.0;
    var x = point.lng, y = point.lat;
    var z = Math.sqrt(x * x + y * y) + 0.00002 * Math.sin(y * x_pi);
    var theta = Math.atan2(y, x) + 0.000003 * Math.cos(x * x_pi);
    bdLng = z * Math.cos(theta) + 0.0065;
    bdLat = z * Math.sin(theta) + 0.006;
    return {"lat": bdLat, "lng": bdLng};
}
/// <summary>
/// 百度地图对应的 BD09 协议坐标，转到 中国正常坐标系GCJ02协议的坐标
/// </summary>
function bd2gcj(point) {
	var x_pi = Math.PI * 3000.0 / 180.0;
    var x = point.lng - 0.0065, y = point.lat - 0.006;
    var z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * x_pi);
    var theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * x_pi);
    gcjLng = z * Math.cos(theta);
    gcjLat = z * Math.sin(theta);
    return {"lat": gcjLat, "lng": gcjLng};
}

function wgs2bd(point){
	return gcj2bd(wgs2gcj(point));
}
function bd2wgs(point){
	return gcj2wgs(bd2gcj(point));
}