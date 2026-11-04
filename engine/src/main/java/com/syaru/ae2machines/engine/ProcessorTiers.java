package com.syaru.ae2machines.engine;

/** Hardware limits are independent of world initialization and exact runtime quantities. */
public final class ProcessorTiers {
    private ProcessorTiers(){}
    private static void check(int tier){if(tier<1 || tier>9)throw new IllegalArgumentException("Invalid processor tier");}
    public static int parallelPower(int tier){check(tier);return tier<=5?2*tier+2:tier+7;}
    public static int speedPower(int tier){check(tier);return tier-1;}
}
