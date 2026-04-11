
package com.imagedubstep.core;

import java.util.ArrayList;
import java.util.List;

public class AudioParam {
    public static class Event {
        public enum Type { SET_VALUE_AT_TIME, LINEAR_RAMP_TO_VALUE_AT_TIME, EXPONENTIAL_RAMP_TO_VALUE_AT_TIME, SET_TARGET_AT_TIME }
        public final Type type;
        public final double value;
        public final double time;
        public final double timeConstant;
        public Event(Type type, double value, double time, double timeConstant){
            this.type = type; this.value = value; this.time = time; this.timeConstant = timeConstant;
        }
    }
    private volatile double defaultValue;
    private final List<Event> events = new ArrayList<>();

    public AudioParam(double defaultValue){ this.defaultValue = defaultValue; }
    public void setValueAtTime(double value, double time){ add(new Event(Event.Type.SET_VALUE_AT_TIME, value, time, 0)); }
    public void linearRampToValueAtTime(double value, double time){ add(new Event(Event.Type.LINEAR_RAMP_TO_VALUE_AT_TIME, value, time, 0)); }
    public void exponentialRampToValueAtTime(double value, double time){ add(new Event(Event.Type.EXPONENTIAL_RAMP_TO_VALUE_AT_TIME, value, time, 0)); }
    public void setTargetAtTime(double target, double time, double tc){ add(new Event(Event.Type.SET_TARGET_AT_TIME, target, time, tc)); }
    public void cancelScheduledValues(double start){ synchronized (events){ events.removeIf(e -> e.time >= start); } }
    public void setImmediate(double v){ defaultValue = v; }

    private void add(Event e){ synchronized (events){ int i=0; while(i<events.size() && events.get(i).time<=e.time) i++; events.add(i,e);} }

    public double valueAt(double t, double sr){
        synchronized (events){
            if(events.isEmpty()) return defaultValue;
            Event prev=null, next=null;
            for(Event ev: events){
                if(ev.time <= t) prev = ev;
                if(ev.time > t){ next = ev; break; }
            }
            double v0 = defaultValue, t0 = 0;
            if(prev != null){
                switch(prev.type){
                    case SET_VALUE_AT_TIME:
                    case LINEAR_RAMP_TO_VALUE_AT_TIME:
                    case EXPONENTIAL_RAMP_TO_VALUE_AT_TIME:
                        v0 = prev.value; t0 = prev.time; break;
                    case SET_TARGET_AT_TIME:
                        double dt = Math.max(0, t - prev.time);
                        double tc = prev.timeConstant <= 0 ? 1e-4 : prev.timeConstant;
                        return defaultValue + (prev.value - defaultValue) * (1 - Math.exp(-dt/tc));
                }
            }
            if(next == null) return v0;
            double dur = Math.max(1.0/sr, next.time - t0);
            double a = Math.max(0, Math.min(1, (t - t0)/dur));
            if(next.type == Event.Type.LINEAR_RAMP_TO_VALUE_AT_TIME) return v0 + (next.value - v0) * a;
            if(next.type == Event.Type.EXPONENTIAL_RAMP_TO_VALUE_AT_TIME){
                double start = Math.max(1e-6, v0), end = Math.max(1e-6, next.value);
                return start * Math.pow(end/start, a);
            }
            return v0;
        }
    }
}
