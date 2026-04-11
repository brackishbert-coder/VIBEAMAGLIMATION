package com.imagedubstep.record;
//Action.java
public final class Action {
 public final String key;      // e.g. "tempo", "playButton", "leadSelect"
 public final String kind;     // "slider", "button", "toggle", "text", "choice"
 public final Object value;    // normalized value for replay
 public final long   tMillis;  // timestamp since start of recording

 public Action(String key, String kind, Object value, long tMillis) {
     this.key = key;
     this.kind = kind;
     this.value = value;
     this.tMillis = tMillis;
 }

 @Override public String toString() {
     return "Action{" + key + ", " + kind + ", " + value + ", t=" + tMillis + "}";
 }
}
