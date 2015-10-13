package com.wlwdw.gps1s;

import com.baidu.mapapi.model.LatLng;

public class CoordsTrans {

	public static boolean outOfChina(LatLng point) {
		if ((point.longitude < 72.004) || (point.longitude > 137.8347)) {
			return true;
		}
		if ((point.latitude < 0.8293) || (point.latitude > 55.8271)) {
			return true;
		}
		return false;
	}

	private static double transformLat(double x, double y) {
		double ret = -100.0 + 2.0*x + 3.0*y + 0.2*y*y + 0.1*x*y + 0.2*Math.sqrt(Math.abs(x));
		ret += (20.0*Math.sin(6.0*x*Math.PI) + 20.0*Math.sin(2.0*x*Math.PI)) * 2.0 / 3.0;
		ret += (20.0*Math.sin(y*Math.PI) + 40.0*Math.sin(y/3.0*Math.PI)) * 2.0 / 3.0;
		ret += (160.0*Math.sin(y/12.0*Math.PI) + 320*Math.sin(y*Math.PI/30.0)) * 2.0 / 3.0;
		return ret;
	}

	private static double transformLon(double x, double y) {
		double ret = 300.0 + x + 2.0*y + 0.1*x*x + 0.1*x*y + 0.1*Math.sqrt(Math.abs(x));
		ret += (20.0*Math.sin(6.0*x*Math.PI) + 20.0*Math.sin(2.0*x*Math.PI)) * 2.0 / 3.0;
		ret += (20.0*Math.sin(x*Math.PI) + 40.0*Math.sin(x/3.0*Math.PI)) * 2.0 / 3.0;
		ret += (150.0*Math.sin(x/12.0*Math.PI) + 300.0*Math.sin(x/30.0*Math.PI)) * 2.0 / 3.0;
		return ret;
	}

	private static LatLng delta(LatLng point) {
		double lng = point.longitude;
		double lat = point.latitude;
		double a = 6378245.0;
		double ee = 0.00669342162296594323;
		double dLat = transformLat(lng-105.0, lat-35.0);
		double dLng = transformLon(lng-105.0, lat-35.0);
		double radLat = lat / 180.0 * Math.PI;
		double magic = Math.sin(radLat);
		magic = 1 - ee*magic*magic;
		double sqrtMagic = Math.sqrt(magic);
		dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * Math.PI);
		dLng = (dLng * 180.0) / (a / sqrtMagic * Math.cos(radLat) * Math.PI);
		return new LatLng(dLat,dLng);

	}

	public static LatLng wgs2gcj(LatLng point) {
		double wgsLat = point.latitude;
		double wgsLng = point.longitude;
		if (outOfChina(point)) {
			return point;
		}
		LatLng d = delta(point);
		return new LatLng(wgsLat + d.latitude, wgsLng + d.longitude);
	}

	public static LatLng gcj2wgs(LatLng point) {
		double gcjLat = point.latitude;
		double gcjLng = point.longitude;
		if (outOfChina(point)) {
			return point;
		}
		LatLng d = delta(point);
		return new LatLng(gcjLat - d.latitude, gcjLng - d.longitude);
	}
	
	final static double x_pi = 3.14159265358979324 * 3000.0 / 180.0;
    public static LatLng gcj2bd(LatLng point){
        double x = point.longitude;
        double y = point.latitude;
        
        double z = Math.sqrt(x*x+y*y)+0.00002*Math.sin(y*x_pi);
        double theta = Math.atan2(y, x)+0.000003*Math.cos(x*x_pi);
        
        LatLng bd_point = new LatLng( (z*Math.sin(theta)+0.006),  (z*Math.cos(theta)+0.0065));
        return bd_point;
    }
    
    public static LatLng bd2gcj(LatLng bd_point){
        double x = bd_point.longitude - 0.0065;
        double y = bd_point.latitude - 0.006;
        double z = Math.sqrt(x*x+y*y) - 0.00002*Math.sin(y*x_pi);
        double theta = Math.atan2(y, x) - 0.000003*Math.cos(x*x_pi);
        LatLng point = new LatLng((z*Math.sin(theta)), (z*Math.cos(theta)));
        return point;
    }
    
    public static LatLng wgs2bd(LatLng point){
    	return gcj2bd(wgs2gcj(point));
    }
    public static LatLng bd2wgs(LatLng point){
    	return gcj2wgs(bd2gcj(point));
    }
    
    /**
     * 由经纬度反算成高斯投影坐标
     * 
     * @param longitude
     * @param latitude
     * @return
     */
    public static double[] ToGaussProj(double longitude, double latitude) {
     int ProjNo = 0;
     int ZoneWide; // //带宽
     double[] output = new double[2];
     double longitude1, latitude1, longitude0, X0, Y0, xval, yval;
     double a, f, e2, ee, NN, T, C, A, M, iPI;
     iPI = 0.0174532925199433; // //3.1415926535898/180.0;
     ZoneWide = 6; // //6度带宽
     
     a = 6378137.0; f = 1.0 /298.257222; //2000国家坐标系，基本等同于 wgs84 标准  298.257224
     // a = 6378245.0; f = 1.0 / 298.3; // 54年北京坐标系参数
     // //a=6378140.0; f=1/298.257; //80年西安坐标系参数
     ProjNo = (int) (longitude / ZoneWide);
     longitude0 = ProjNo * ZoneWide + ZoneWide / 2;
     longitude0 = longitude0 * iPI;
     longitude1 = longitude * iPI; // 经度转换为弧度
     latitude1 = latitude * iPI; // 纬度转换为弧度
     e2 = 2 * f - f * f;
     ee = e2 / (1.0 - e2);
     NN = a
       / Math.sqrt(1.0 - e2 * Math.sin(latitude1)
         * Math.sin(latitude1));
     T = Math.tan(latitude1) * Math.tan(latitude1);
     C = ee * Math.cos(latitude1) * Math.cos(latitude1);
     A = (longitude1 - longitude0) * Math.cos(latitude1);
     M = a
       * ((1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256)
         * latitude1
         - (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * e2 * e2 * e2
           / 1024) * Math.sin(2 * latitude1)
         + (15 * e2 * e2 / 256 + 45 * e2 * e2 * e2 / 1024)
         * Math.sin(4 * latitude1) - (35 * e2 * e2 * e2 / 3072)
         * Math.sin(6 * latitude1));
     // 因为是以赤道为Y轴的，与我们南北为Y轴是相反的，所以xy与高斯投影的标准xy正好相反;
     xval = NN
       * (A + (1 - T + C) * A * A * A / 6 + (5 - 18 * T + T * T + 14
         * C - 58 * ee)
         * A * A * A * A * A / 120);
     yval = M
       + NN
       * Math.tan(latitude1)
       * (A * A / 2 + (5 - T + 9 * C + 4 * C * C) * A * A * A * A / 24 + (61
         - 58 * T + T * T + 270 * C - 330 * ee)
         * A * A * A * A * A * A / 720);
     X0 = 1000000L * (ProjNo + 1) + 500000L;
     Y0 = 0;
     xval = xval + X0;
     yval = yval + Y0;
     output[0] = xval;
     output[1] = yval;
     return output;
    }
}
