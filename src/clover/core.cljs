(ns clover.core
  (:require [clover.aux :as aux :include-macros true]
            [clover.commands.formatter :as formatter]
            [clover.vs :as vs]
            [clover.state :as st]
            [clover.ui :as ui]
            [clover.commands.connection :as conn]
            [repl-tooling.editor-integration.evaluation :as e-eval]
            [repl-tooling.features.definition :as definition]
            [repl-tooling.editor-helpers :as helpers]
            [clover.joyride :as joy]
            ["vscode" :as vscode :refer [Location Uri Position Range]]
            ["path" :as path]))

(defn- var-definition [document position]
  (let [data (vs/get-document-data document position)
        [_ curr-var] (helpers/current-var (:contents data) (-> data :range first))
        [_ curr-ns] (helpers/ns-range-for (:contents data) (-> data :range first))
        aux (some-> @st/state :conn deref :clj/aux)
        repl (some-> (:conn @st/state)
                     (e-eval/repl-for
                      (.. vscode -window -activeTextEditor -document -fileName)
                      true))]
    (when repl
      (.. (definition/find-var-definition repl aux curr-ns curr-var)
          (then (fn [{:keys [file-name line]}]
                  (Location. (. Uri parse file-name) (Position. line 0))))))))

(def icons
  (let [vs-icons (-> vscode .-CompletionItemKind (js->clj :keywordize-keys true))]
    {:method (:Method vs-icons)
     :field (:Field vs-icons)
     :static-method (:Function vs-icons)
     :static-field (:Field vs-icons)
     :local (:Variable vs-icons)
     :class (:Class vs-icons)
     :namespace (:Module vs-icons)
     :keyword (:Property vs-icons)
     :protocol-function (:Function vs-icons)
     :function (:Function vs-icons)
     :record (:Struct vs-icons)
     :type (:TypeParameter vs-icons)
     :protocol (:Interface vs-icons)
     :var (:Constant vs-icons)
     :macro (:Keyword vs-icons)
     :resource (:Reference vs-icons)
     :special-form (:Constructor vs-icons)
     :value (:Value vs-icons)}))

(defn- range-to-replace []
  (let [{:keys [contents range]} (vs/get-editor-data)
        [[s-row s-col] [e-row e-col]] (first (helpers/current-var contents (first range)))]
    (Range. s-row s-col e-row e-col)))

(defn- autocomplete [ & args]
  (when-let [complete (some-> @st/state :conn deref :editor/features :autocomplete)]
    (.. (complete)
        (then (fn [candidates]
                (let [range (range-to-replace)]
                  (map (fn [{:keys [type candidate] :as a}]
                         {:label candidate
                          :kind (icons type (:value icons))
                          :filterText candidate
                          :range range})
                       candidates))))
        (then clj->js)
        (catch (constantly #js [])))))

(def ^:private document-selector (clj->js [{:scheme "file" :language "clojure"}
                                           {:scheme "jar" :language "clojure"}
                                           {:scheme "untitled" :language "clojure"}]))

(defn ^:export activate [^js ctx]
  (when ctx (reset! ui/curr-dir (.. ctx -extensionPath)))
  (.. vscode -languages
      (setLanguageConfiguration
       "clojure"
       (js->clj
        {:wordPattern #"[^\s,#()\[\]{};\"\\\@']+"})))

  (aux/add-disposable!
   (.. vscode -tasks (registerTaskProvider "Clover" conn/provider)))

  (aux/add-disposable! (.. vscode -languages
                           (registerOnTypeFormattingEditProvider document-selector
                                                                 formatter/formatter
                                                                 "\r"
                                                                 "\n")))

  (aux/add-disposable! (.. vscode -commands
                           (registerCommand "clover.connectSocketRepl"
                                            conn/connect!)))
  (aux/add-disposable! (.. vscode -languages
                           (registerDefinitionProvider
                            "clojure"
                            #js {:provideDefinition var-definition})))

  (aux/add-disposable! (.. vscode -languages
                           (registerCompletionItemProvider
                            "clojure"
                            #js {:provideCompletionItems autocomplete})))
  joy/exports)

(defn deactivate []
  (aux/clear-all!))

(defn before [done]
  (deactivate)
  (done))

(defn after []
  (activate nil)
  (vs/info "Reloaded Clover")
  (println "Reloaded"))
