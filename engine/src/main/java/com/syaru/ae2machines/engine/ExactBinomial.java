package com.syaru.ae2machines.engine;

import java.math.*;
import java.util.*;
import java.util.random.RandomGenerator;

/** Discrete log-concave rejection with exact rational proposals and certified decisions. */
public final class ExactBinomial {
    private static final BigInteger ONE=BigInteger.ONE,TWO=BigInteger.TWO;
    private ExactBinomial(){}

    /** Probability of a native uniform 53-bit nextDouble being <= the supplied value. */
    public static BigInteger inclusiveDoubleNumerator(double probability){
        if(!Double.isFinite(probability) || probability<0 || probability>1)throw new IllegalArgumentException("Invalid probability");
        if(probability==1)return ONE.shiftLeft(53);
        return new BigDecimal(probability).multiply(new BigDecimal(ONE.shiftLeft(53))).toBigInteger().add(ONE);
    }
    public static BigInteger sample(BigInteger trials,BigInteger numerator,BigInteger denominator,RandomGenerator random){
        Objects.requireNonNull(random);if(trials.signum()<0 || denominator.signum()<=0 || numerator.signum()<0 || numerator.compareTo(denominator)>0)throw new IllegalArgumentException("Invalid binomial parameters");
        if(trials.signum()==0 || numerator.signum()==0)return BigInteger.ZERO;
        if(numerator.equals(denominator))return trials;
        if(trials.compareTo(BigInteger.valueOf(128))<0){var sum=BigInteger.ZERO;for(int i=0;i<trials.intValueExact();i++)if(uniform(denominator,random).compareTo(numerator)<0)sum=sum.add(ONE);return sum;}
        return new Draw(trials,numerator,denominator,random).sample();
    }
    static BigInteger uniform(BigInteger bound,RandomGenerator random){
        if(bound.signum()<=0)throw new IllegalArgumentException("Nonpositive random bound");
        int bits=bound.subtract(ONE).bitLength();if(bits==0)return BigInteger.ZERO;
        var bytes=new byte[(bits+7)/8];BigInteger value;
        do{random.nextBytes(bytes);bytes[0]&=(byte)(255>>>(8*bytes.length-bits));value=new BigInteger(1,bytes);}while(value.compareTo(bound)>=0);
        return value;
    }
    private record Fraction(BigInteger a,BigInteger b){
        Fraction{if(a.signum()<0 || b.signum()<=0 || a.compareTo(b)>=0)throw new IllegalArgumentException("Invalid geometric ratio");var g=a.gcd(b);a=a.divide(g);b=b.divide(g);}
    }
    private static final class Draw {
        final BigInteger n,p,q,m,low,high,centre,leftWeight,rightWeight,total;
        final Fraction left,right;final RandomGenerator random;final int initialPrecision;
        Draw(BigInteger n,BigInteger p,BigInteger d,RandomGenerator random){
            this.n=n;this.p=p;q=d.subtract(p);this.random=random;m=n.add(ONE).multiply(p).divide(d).min(n);
            var width=n.multiply(p).multiply(q).divide(d.multiply(d)).sqrt().add(ONE);
            low=m.subtract(width).max(BigInteger.ZERO);high=m.add(width).min(n);centre=high.subtract(low).add(ONE);
            left=new Fraction(low.multiply(q),n.subtract(low).add(ONE).multiply(p));
            right=new Fraction(n.subtract(high).multiply(p),high.add(ONE).multiply(q));
            var ld=left.b.subtract(left.a);var rd=right.b.subtract(right.a);
            leftWeight=left.a.multiply(rd);rightWeight=right.a.multiply(ld);total=centre.multiply(ld).multiply(rd).add(leftWeight).add(rightWeight);
            initialPrecision=Math.addExact((n.toString().length()+1)/2,64);
        }
        BigInteger sample(){
            var math=new Bounds(initialPrecision);
            while(true){
                var choice=uniform(total,random);BigInteger k,distance;Fraction ratio;
                if(choice.compareTo(leftWeight)<0){ratio=left;distance=geometric(ratio,math);k=low.subtract(distance);}
                else if(choice.compareTo(leftWeight.add(rightWeight))<0){ratio=right;distance=geometric(ratio,math);k=high.add(distance);}
                else{ratio=null;distance=BigInteger.ZERO;k=low.add(uniform(centre,random));}
                if(k.signum()<0 || k.compareTo(n)>0)continue;
                if(k.equals(m) && ratio==null)return k;
                var u=new Uniform(random);int refinement=0;
                while(true){
                    var log=math.massRatio(n,m,k,p,q,refinement);
                    if(ratio!=null)log=log.subtract(math.log(ratio.a,ratio.b).multiply(distance));
                    // The envelope proves a probability <= 1; clipping the upper bound is exact.
                    if(log.lo.signum()>0)throw new IllegalStateException("Binomial envelope failed");
                    log=new Interval(log.lo,log.hi.min(BigDecimal.ZERO),math.precision);
                    var lu=u.log(math);
                    if(lu.hi.compareTo(log.lo)<0)return k;
                    if(lu.lo.compareTo(log.hi)>0)break;
                    u.extend();refinement=Math.incrementExact(refinement);math=new Bounds(Math.addExact(math.precision,32));
                }
            }
        }
        BigInteger geometric(Fraction r,Bounds initial){
            var u=new Uniform(random);var math=initial;
            int required=Math.addExact(r.b.bitLength()-r.b.subtract(r.a).bitLength(),64);
            while(u.bits<required)u.extend();
            while(true){
                var quotient=u.log(math).divide(math.log(r.a,r.b));
                var a=quotient.lo.setScale(0,RoundingMode.FLOOR).toBigIntegerExact();var b=quotient.hi.setScale(0,RoundingMode.FLOOR).toBigIntegerExact();
                if(a.equals(b))return a.add(ONE);
                u.extend();math=new Bounds(Math.addExact(math.precision,32));
            }
        }
    }
    private static final class Uniform {
        BigInteger prefix=BigInteger.ZERO;int bits;final RandomGenerator random;
        Uniform(RandomGenerator random){this.random=random;extend();}
        void extend(){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Binomial sampling interrupted");var bytes=new byte[8];random.nextBytes(bytes);prefix=prefix.shiftLeft(64).or(new BigInteger(1,bytes));bits=Math.addExact(bits,64);}
        Interval log(Bounds math){while(prefix.signum()==0)extend();return new Interval(math.log(prefix,ONE.shiftLeft(bits)).lo,math.log(prefix.add(ONE),ONE.shiftLeft(bits)).hi,math.precision);}
    }
    static final class Bounds {
        final int precision;final MathContext down,up;final Map<String,Interval> logs=new HashMap<>();Interval logTwo;
        Bounds(int precision){this.precision=precision;down=new MathContext(precision,RoundingMode.FLOOR);up=new MathContext(precision,RoundingMode.CEILING);}
        Interval value(BigInteger n){return new Interval(new BigDecimal(n),new BigDecimal(n),precision);}
        Interval ratio(BigInteger a,BigInteger b){return value(a).divide(value(b));}
        Interval log(BigInteger a,BigInteger b){
            if(a.signum()<=0 || b.signum()<=0)throw new IllegalArgumentException("Nonpositive logarithm");
            if(a.equals(b))return value(BigInteger.ZERO);
            String key=a+"/"+b;var old=logs.get(key);if(old!=null)return old;
            Interval result;
            if(a.compareTo(b)<0)result=log(b,a).negate();
            else{
                int shift=a.bitLength()-b.bitLength();if(a.compareTo(b.shiftLeft(shift))<0)shift--;
                var normalized=series(a,b.shiftLeft(shift));
                if(shift!=0 && logTwo==null)logTwo=series(TWO,ONE);
                result=shift==0?normalized:normalized.add(logTwo.multiply(BigInteger.valueOf(shift)));
            }
            logs.put(key,result);return result;
        }
        private Interval series(BigInteger a,BigInteger b){
            int roots=a.subtract(b).shiftLeft(4).compareTo(b)>0?(precision>128?8:4):0;
            Interval z;
            if(roots==0)z=ratio(a.subtract(b),a.add(b));
            else{var x=ratio(a,b);for(int i=0;i<roots;i++)x=x.sqrt();z=x.subtract(value(ONE)).divide(x.add(value(ONE)));}
            var square=z.multiply(z);var power=z;var sum=value(BigInteger.ZERO);int j=1;
            var epsilon=BigDecimal.ONE.scaleByPowerOfTen(-precision-4);
            while(true){
                if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Logarithm refinement interrupted");
                sum=sum.add(power.divide(value(BigInteger.valueOf(j))));j=Math.addExact(j,2);power=power.multiply(square);
                var tail=power.multiply(TWO).divide(value(BigInteger.valueOf(j)).multiply(value(ONE).subtract(square)));
                if(tail.hi.compareTo(epsilon)<0)return new Interval(sum.lo.multiply(BigDecimal.valueOf(2),down),sum.hi.multiply(BigDecimal.valueOf(2),up).add(tail.hi,up),precision).multiply(ONE.shiftLeft(roots));
            }
        }
        Interval correction(BigInteger n){return new Interval(ratio(ONE,n.multiply(BigInteger.valueOf(12)).add(ONE)).lo,ratio(ONE,n.multiply(BigInteger.valueOf(12))).hi,precision);}
        Interval massRatio(BigInteger n,BigInteger m,BigInteger k,BigInteger p,BigInteger q,int refinement){
            var other=n.subtract(m);var rest=n.subtract(k);var delta=k.subtract(m);
            if(refinement==0 && m.min(other).min(k).min(rest).compareTo(BigInteger.valueOf(32))>=0){
                // Cancel the large factorial terms algebraically before rounding. All logs are
                // near one for a central proposal, even when n has thousands of digits.
                return log(other.multiply(p),m.multiply(q)).multiply(delta)
                        .subtract(log(k,m).multiply(ratio(k.shiftLeft(1).add(ONE),TWO)))
                        .subtract(log(rest,other).multiply(ratio(rest.shiftLeft(1).add(ONE),TWO)))
                        .add(correction(m)).add(correction(other)).subtract(correction(k)).subtract(correction(rest));
            }
            return factorial(m,refinement).add(factorial(other,refinement)).subtract(factorial(k,refinement)).subtract(factorial(rest,refinement)).add(log(p,q).multiply(delta));
        }
        Interval factorial(BigInteger n,int refinement){
            var base=BigInteger.valueOf(32).shiftLeft(refinement);var at=n.max(base);
            var log=log(at,ONE).multiply(ratio(at.shiftLeft(1).add(ONE),TWO)).subtract(value(at));
            log=log.add(correction(at));
            for(var j=n.add(ONE);j.compareTo(at)<=0;j=j.add(ONE))log=log.subtract(log(j,ONE));
            return log;
        }
    }
    static final class Interval {
        final BigDecimal lo,hi;final int precision;final MathContext down,up;
        Interval(BigDecimal lo,BigDecimal hi,int precision){if(lo.compareTo(hi)>0)throw new IllegalStateException("Reversed arithmetic interval");this.lo=lo;this.hi=hi;this.precision=precision;down=new MathContext(precision,RoundingMode.FLOOR);up=new MathContext(precision,RoundingMode.CEILING);}
        Interval add(Interval v){return new Interval(lo.add(v.lo,down),hi.add(v.hi,up),precision);}
        Interval subtract(Interval v){return new Interval(lo.subtract(v.hi,down),hi.subtract(v.lo,up),precision);}
        Interval negate(){return new Interval(hi.negate(),lo.negate(),precision);}
        Interval multiply(BigInteger n){return multiply(new Interval(new BigDecimal(n),new BigDecimal(n),precision));}
        Interval multiply(Interval v){return combine(v,false);}
        Interval divide(Interval v){if(v.lo.signum()<=0 && v.hi.signum()>=0)throw new ArithmeticException("Interval divides by zero");return combine(v,true);}
        Interval sqrt(){
            if(lo.signum()<0)throw new ArithmeticException("Negative square root interval");int scale=Math.addExact(precision,8);
            var lower=lo.movePointRight(Math.multiplyExact(scale,2)).setScale(0,RoundingMode.FLOOR).toBigIntegerExact().sqrt();
            var upper=hi.movePointRight(Math.multiplyExact(scale,2)).setScale(0,RoundingMode.CEILING).toBigIntegerExact().sqrt().add(ONE);
            return new Interval(new BigDecimal(lower,scale),new BigDecimal(upper,scale),precision);
        }
        private Interval combine(Interval v,boolean divide){
            BigDecimal a=null,b=null;for(var x:List.of(lo,hi))for(var y:List.of(v.lo,v.hi)){
                var lower=divide?x.divide(y,down):x.multiply(y,down);var upper=divide?x.divide(y,up):x.multiply(y,up);
                a=a==null?lower:a.min(lower);b=b==null?upper:b.max(upper);
            }return new Interval(a,b,precision);
        }
    }
}
