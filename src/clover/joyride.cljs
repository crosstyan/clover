(ns clover.joyride
  (:require [clover.vs :as vs]
            [repl-tooling.editor-helpers :as helpers]
            [clover.state :refer [state]]
            [promesa.core :as p]
            [clover.commands.connection :as conn]))

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

(defn- norm-range [range]
  (if (and (array? range)
           (-> range first array?)
           (-> range second array?))
    range
    (:range (vs/get-editor-data))))

(defn- run-eval-command [key code range]
  (let [range (norm-range range)]
    (when-let [command (some-> @state :conn deref :editor/features key)]
      (-> {:text code :range range}
          js->clj
          command
          (p/then clj->js)))))

(defn evaluate-interactive [code range]
  (let [range (norm-range range)]
    (when-let [command (some-> @state :conn deref
                               :editor/features :evaluate-and-render)]
      (-> {:text code :range range :pass {:aux true :interactive true}}
          js->clj
          command
          (p/then clj->js)))))

(defn disconnect []
  (when-let [command (some-> @state :conn deref :editor/commands :disconnect)]
    ((:command command))))

(defn- run-command [command-name & args]
  (let [n (keyword command-name)]
    (when-let [command (some-> @state :conn deref :editor/commands n)]
      (apply (:command command) args))))

(defn- get-commands []
  (clj->js (or (some->> @state :conn deref :editor/commands keys) [])))

(def ^:export exports
  (clj->js
   {:get_top_block #(get-code "top-block")
    :get_block #(get-code "block")
    :get_var #(get-code "var")
    :get_selection #(get-code "selection")
    :get_namespace #(get-code "ns")
    :evaluate_and_present (partial run-eval-command :evaluate-and-render)
    :evaluate (partial run-eval-command :eval)
    :evaluate_interactive evaluate-interactive
    :connect_socket conn/connect!
    :disconnect disconnect
    :get_commands get-commands
    :run_command run-command}))
