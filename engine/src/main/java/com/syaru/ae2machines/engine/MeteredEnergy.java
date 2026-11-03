package com.syaru.ae2machines.engine;

import java.math.BigInteger;

/** A real buffer. S and Q never multiply its charge, capacity or input rate. */
public final class MeteredEnergy {
    private final String unit;
    private final BigInteger capacity;
    private final BigInteger maxInput;
    private final BigInteger voltage;
    private final boolean limitPerTick;
    private BigInteger stored;
    private BigInteger receivedThisTick = BigInteger.ZERO;
    private long inputTick = Long.MIN_VALUE;
    public MeteredEnergy(String unit, BigInteger capacity, BigInteger maxInput, BigInteger voltage, BigInteger stored) {
        this(unit,capacity,maxInput,voltage,stored,true);
    }
    public MeteredEnergy(String unit, BigInteger capacity, BigInteger maxInput, BigInteger voltage, BigInteger stored,boolean limitPerTick) {
        if (!java.util.Set.of("FE","EU","MEK_J_1E4").contains(unit)) throw new IllegalArgumentException("Unit");
        this.unit = unit;
        this.limitPerTick=limitPerTick;
        this.capacity = ExactAmounts.nonnegative(capacity);
        this.maxInput = ExactAmounts.nonnegative(maxInput);
        this.voltage = ExactAmounts.nonnegative(voltage);
        this.stored = ExactAmounts.nonnegative(stored);
        if (stored.compareTo(capacity) > 0) throw new IllegalArgumentException("Overfilled buffer");
    }
    public BigInteger insert(BigInteger offer, long tick, boolean simulate) {
        ExactAmounts.nonnegative(offer);
        BigInteger used = limitPerTick && inputTick == tick ? receivedThisTick : BigInteger.ZERO;
        BigInteger accepted = offer.min(capacity.subtract(stored)).min(maxInput.subtract(used));
        if (!simulate) {
            if (inputTick != tick) { inputTick = tick; receivedThisTick = BigInteger.ZERO; }
            receivedThisTick = receivedThisTick.add(accepted);
            stored = stored.add(accepted);
        }
        return accepted;
    }
    public boolean supports(Recipe<?> recipe) {
        return unit.equals(recipe.energyUnit()) && voltage.compareTo(recipe.minimumVoltage()) >= 0;
    }
    public void consume(BigInteger n) {
        ExactAmounts.nonnegative(n);
        if (n.compareTo(stored) > 0) throw new IllegalStateException("Energy overdraft");
        stored = stored.subtract(n);
    }
    public BigInteger stored() { return stored; }
    public BigInteger capacity() { return capacity; }
    public BigInteger maxInput() { return maxInput; }
    public BigInteger voltage() { return voltage; }
    public String unit() { return unit; }
    public boolean limitPerTick() { return limitPerTick; }
}
