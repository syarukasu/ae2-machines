package com.syaru.ae2machines.client;

import org.joml.Vector3f;

/** Screen-space dragging at the current perspective camera distance. */
public final class PreviewCameraPan {
    private PreviewCameraPan(){}
    public static boolean gesture(int button,boolean shift){return button==1 || button==2 || button==0 && shift;}
    public static Vector3f displacement(double dx,double dy,float distance,int height,float elevation,float azimuth){
        if(!Double.isFinite(dx) || !Double.isFinite(dy) || !Float.isFinite(distance) || !Float.isFinite(elevation)
                || !Float.isFinite(azimuth) || distance<=0 || height<=0)throw new IllegalArgumentException("Invalid camera pan");
        double scale=2*distance*Math.tan(Math.toRadians(45)/2)/height;
        double e=Math.toRadians(elevation),a=Math.toRadians(azimuth);
        return new Vector3f((float)((-dx*Math.sin(a)-dy*Math.cos(a)*Math.sin(e))*scale),
                (float)(dy*Math.cos(e)*scale),(float)((dx*Math.cos(a)-dy*Math.sin(a)*Math.sin(e))*scale));
    }
}
