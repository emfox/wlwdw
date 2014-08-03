package org.rpwt.gps1s;

import com.baidu.mapapi.model.LatLng;

public class CoordsTrans {

	final double x_pi = 3.14159265358979324 * 3000.0 / 180.0;
    public LatLng gcj2bd(LatLng point){
        double x = point.longitude;
        double y = point.latitude;
        
        double z = Math.sqrt(x*x+y*y)+0.00002*Math.sin(y*x_pi);
        double theta = Math.atan2(y, x)+0.000003*Math.cos(x*x_pi);
        
        LatLng bd_point = new LatLng( (z*Math.sin(theta)+0.006),  (z*Math.cos(theta)+0.0065));
        return bd_point;
    }
    
    public LatLng bd2gcj(LatLng bd_point){
        double x = bd_point.longitude - 0.0065;
        double y = bd_point.latitude - 0.006;
        double z = Math.sqrt(x*x+y*y) - 0.00002*Math.sin(y*x_pi);
        double theta = Math.atan2(y, x) - 0.000003*Math.cos(x*x_pi);
        LatLng point = new LatLng((z*Math.sin(theta)), (z*Math.cos(theta)));
        return point;
    }
}
