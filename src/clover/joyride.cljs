(ns clover.joyride
  (:require [clover.vs :as vs]
            [repl-tooling.editor-helpers :as helpers]
            [clover.state :refer [state]]
            [clover.commands.connection :as conn]))

(let [{:keys [range contents]} (vs/get-editor-data)]
  (helpers/text-in-range contents range))

(defn get-code [kind]
  (when-let [{:keys [range contents]} (vs/get-editor-data)]
    (let [[[row col]] range
          [range text] (case kind
                         "top-block" (helpers/top-block-for contents [row col])
                         "block" (helpers/block-for contents [row col])
                         "var" (helpers/current-var contents [row col])
                         "selection" [range (helpers/text-in-range contents range)]
                         "ns" (helpers/ns-range-for contents [row col]))]
      (clj->js {:text text
                :range range}))))

(defn evaluate-and-present [code range]
  (when-let [command (some-> @state :conn deref
                             :editor/features :evaluate-and-render)]
    (command (js->clj {:text code :range range}))))

(defn evaluate-interactive [code range]
  (when-let [command (some-> @state :conn deref
                             :editor/features :evaluate-and-render)]
    (command (js->clj {:text code
                       :range range
                       :pass {:aux true :interactive true}}))))

; #_
; (when-let [command (some-> @state :conn deref
;                            :editor/commands
;                            keys
;                            sort)])
;
(defn disconnect []
  (when-let [command (some-> @state :conn deref :editor/commands :disconnect)]
    ((:command command))))

(def exports
  #js {:get_top_block #(get-code "top-block")
       :get_block #(get-code "block")
       :get_var #(get-code "var")
       :get_selection #(get-code "selection")
       :get_namespace #(get-code "ns")
       :evaluate_and_present evaluate-and-present
       :evaluate_interactive evaluate-interactive
       :connect_socket conn/connect!
       :disconnect disconnect})
