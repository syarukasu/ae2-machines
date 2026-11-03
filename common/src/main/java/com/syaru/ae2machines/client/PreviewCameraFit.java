package com.syaru.ae2machines.client;

/** Fits the eight bounds corners to LDLib's perspective camera, with room for block details. */
public final class PreviewCameraFit {
    private PreviewCameraFit(){}
    public static float distance(float sx,float sy,float sz,float elevation,float azimuth,float aspect){
        if(!(sx>0 && sy>0 && sz>0 && aspect>0) || !Float.isFinite(sx+sy+sz+aspect+elevation+azimuth))throw new IllegalArgumentException("Invalid preview bounds");
        double yaw=Math.toRadians(elevation),pitch=Math.toRadians(azimuth);
        double ex=Math.cos(pitch)*Math.cos(yaw),ey=Math.sin(yaw),ez=Math.sin(pitch)*Math.cos(yaw);
        double rx=Math.sin(pitch),rz=-Math.cos(pitch),ux=-Math.cos(pitch)*Math.sin(yaw),uy=Math.cos(yaw),uz=-Math.sin(pitch)*Math.sin(yaw);
        double tangent=Math.tan(Math.toRadians(45)/2)*.85,distance=2;
        for(int ix:new int[]{-1,1})for(int iy:new int[]{-1,1})for(int iz:new int[]{-1,1}){
            double x=ix*(sx/2+.05),y=iy*(sy/2+.05),z=iz*(sz/2+.05),depth=x*ex+y*ey+z*ez;
            distance=Math.max(distance,depth+Math.max(Math.abs(x*rx+z*rz)/(tangent*aspect),Math.abs(x*ux+y*uy+z*uz)/tangent));
        }
        return (float)distance;
    }
}
