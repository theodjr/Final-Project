package com.projecttango.experiments.quickstartjava;

/**
 * Created by Theo_ on 5/16/2016.
 */



final class State {
   static private double R=.05;
   static private double L=.28;

   public static double vr(double v,double omega){
       return (2*v+omega*L)/(2*R);
   }
    public static double vl(double v, double omega){
        return (2*v-omega*L)/(2*R);
    }


}



