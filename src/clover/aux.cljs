(ns clover.aux
  (:require ["vscode" :as vscode]))

(def ^:private cmds (atom []))
(def ^:private transient-cmds (atom []))

(defn add-disposable* [fun]
  (swap! cmds conj (fun)))
(defn add-transient-disposable* [fun]
  (swap! transient-cmds conj (fun)))

(defn clear-all! []
  (doseq [cmd @cmds]
    (.dispose ^js cmd))
  (reset! cmds []))

(defn clear-transients! []
  (doseq [cmd @transient-cmds]
    (.dispose ^js cmd))
  (reset! transient-cmds []))
