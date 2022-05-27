# Clover

A Clojure Socket REPL package for Visual Studio Code and Visual Studio Codium

## Features

For now, it is possible to connect on a Clojure (and Clojure-ish) socket REPL and evaluate forms. You can load the current file, there's autocomplete, and support for multiple ClojureScript REPLs (with the notable exception being that Figwheel is missing - it only supports Shadow-CLJS and "pure ClojureScript socket REPLs" like Lumo, Plank, `clj` with some additional code, etc).

For now, the following Clojure implementations were tested and are known to work:

* Clojure with lein/boot/clj
* ClojureScript with Shadow-CLJS (multi-target)
* ClojureScript with `clj` and the command `clj -J-Dclojure.server.browser="{:port 4444 :accept cljs.server.browser/repl}"`
* ClojureCLR
* Lumo
* Plank
* Joker
* Babashka

### Example:

![Evaluating code](doc/sample.gif)

## Usage:
Fire up a clojure REPL with Socket REPL support. With `shadow-cljs`, when you `watch` some build ID it'll give you a port for nREPL and Socket REPL. With `lein`, invoke it in a folder where you have `project.clj` and you can use `JVM_OPTS` environment variable like:

```bash
JVM_OPTS='-Dclojure.server.myrepl={:port,5555,:accept,clojure.core.server/repl}' lein repl
```

On Shadow-CLJS' newer versions, when you start a build with `shadow-cljs watch <some-id>`, it doesn't shows the Socket REPL port on the console, but it does create a file with the port number on `.shadow-cljs/socket-repl.port`. You can read that file to see the port number (Clover currently uses this file to mark the port as default).

With `clj`, you can run the following from any folder:

```bash
clj -J'-Dclojure.server.repl={:port,5555,:accept,clojure.core.server/repl}'
```

Or have it in `:aliases` in `deps.edn`. (For an example with port 50505 see https://github.com/seancorfield/dot-clojure/blob/master/deps.edn, then you can run `clj -A:socket`.)

Then, you connect Clover with the port using the command _Connect Clojure Socket REPL_. This package works with lumo too, but you'll need to run _Connect ClojureScript Socket REPL_.

When connected, it'll try to load `compliment` (for autocomplete, falling back to a "simpler" autocomplete if not present). Then you can evaluate code on it.

### Custom Commands
Exactly as in Chlorine, [there's support for Custom Commands](https://github.com/mauricioszabo/atom-chlorine/blob/master/docs/extending.md). Please follow the link for more explanation, but the idea is that, after connecting to a REPL, you run the command `Clover: Open config file"` and then register your custom commands there. Please notice that "custom tags" is **not supported**, and probably never will unless VSCode makes its API better - the only custom tags supported are `:div/clj` and `:div/ansi` (the first accepts a Clojure data structure and uses the Clover renderer to render it, the second accepts a string with ANSI codes and color then accordingly).

Because of limitations of VSCode, you will **not see** custom commands on the command palette - they are registered as Tasks. So you'll run the command "Tasks: Run Task" and then choose "Clover". There, the custom commands will be presented. Probably VSCode will ask you if you want to "Scan output of task". It's safe to say "Never scan the output for any Clover task".

The reason that Clover uses tasks is because **you can set keybindings to tasks** - to register a keybinding to a task, you need to run the command "Preferences: Open Keyboard Shortcuts (JSON)". Be aware that **you need to edit keybindings via  JSON**. There, you'll define a keybinding for the command `workbench.action.tasks.runTask` and the args will be **exactly the name** that appears on the task menu - **case sensitive**.

So, supposed you did add a custom command on your config (one that just prints "Hello, World" to the console:

```clojure
(defn hello-world []
  (println "Hello, World!"))
```

Then, you'll see that the task registered will be named "Clover: Hello world". You can register, for example, `ctrl+h` with the following JSON fragment:

```json
    {
        "key": "ctrl+h",
        "command": "workbench.action.tasks.runTask",
        "args": "Clover: Hello world"
    }
```

### Refresh Namespaces

Clover does not have the command to use `clojure.tools.namespace` to refresh, but it's easy to add your own version with custom commands:

```clojure
(def ^:private refresh-needs-clear? (atom true))
(defn- refresh-full-command []
  (if @refresh-needs-clear?
    '(do
       (clojure.tools.namespace.repl/clear)
       (clojure.tools.namespace.repl/refresh-all))
    '(do
       (clojure.tools.namespace.repl/refresh))))

(defn refresh-namespaces []
  (p/let [_ (p/catch (editor/eval {:text "(clojure.core/require '[clojure.tools.namespace.repl])"})
                   (constantly nil))
          cmd (refresh-full-command)
          cmd (list 'let ['res cmd]
                    '(if (= res :ok)
                       {:html [:div "Refresh Successful"]}
                       {:html [:div.error [:div/clj res]]}))
          res (editor/eval-interactive {:text (pr-str cmd)
                                        :range [[0 0] [0 0]]
                                        :namespace "user"})]
    (if (->> res :result pr-str (re-find #":div/clj"))
      (reset! refresh-needs-clear? true)
      (reset! refresh-needs-clear? false))))
```

## Keybindings
To avoid conflicts, this plug-in does not register any keybindings. You can define your own on `keybindings.json`. One possible suggestion is:

```json
    {
        "key": "ctrl+enter",
        "command": "clover.evaluate-top-block",
        "when": "!editorHasSelection"
    },
    {
        "key": "shift+enter",
        "command": "clover.evaluate-block",
        "when": ""
    },
    {
        "key": "ctrl+enter",
        "command": "clover.evaluate-selection",
        "when": "editorHasSelection"
    },
    {
        "key": "ctrl+shift+c",
        "command": "clover.connectSocketRepl",
        "when": ""
    },
    {
        "key": "ctrl+shift+l",
        "command": "clover.clear-console",
        "when": ""
    }
```

### Integration with Joyride

Currently Clover exposes some APIs that can be used with [Joyride](https://github.com/BetterThanTomorrow/joyride). To use it, first get the plug-in exports with the following code:

```clojure
(let [clover (vscode/extensions.getExtension "mauricioszabo.clover")
      commands (.-exports clover)]
  ...your code here...
  )
```

The following commands are exposed:

* **get_top_block()** - Gets the topmost block in the current editor considering the cursor position
* **get_block()** - Gets the current block in the current editor considering the cursor position
* **get_var()** - Gets the var under the current position
* **get_selection()** - Gets selected text in the current position
* **get_namespace()** - Gets the current namespace as a string, considering the current cursor position. For example, if your code have two namespace definitions, one like `(ns name1...)` at line 20, and `(ns name2...)` at line 30, if your cursor is at line 21, it'll return `name1`, and if your cursor is at line 40, it'll return `name2`.
* **evaluate(code, range)** - Evaluate the current code and returns the result inside a promise. If the evaluation succeeds, result will be returned inside `result`. If it fails, it'll be inside `error`.  Please notice that to run this function a REPL needs to be connected.
* **evaluate_and_present(code, range)** - Evaluate the current code and shows in the Clover console window. Please notice that to run this function a REPL needs to be connected.
* **evaluate_interactive(code, range)** - Evaluate the current code and shows in the Clover console window using the interactive renderer. For more information, see [Customize rendering of results](https://gitlab.com/clj-editors/repl-tooling/-/blob/master/doc/extending.md#customize-rendering-of-results). Please notice that to run this function a REPL needs to be connected.
* **connect_socket(host, port)** - Connects to a socket REPL. If you call without arguments, it'll open a popup window asking for host and port number. If you call with a string and an integer, it'll try to connect to that specific host and port.
* **disconnect()** - Disconnects the REPL if it's connected.
* **get_commands()** - Gets all commands that Clover supports. Please notice that to run this function a REPL needs to be connected.
* **run_command(command, ...args)** - Runs the command, passing arguments if the function allows to (it probably will not). Please notice that to run this function a REPL needs to be connected.

All `get_*` commands return an object containing `.text` and `.range`, containing the currrent text that was detected and the range that the text represents (for example, if you get a block, it'll return the full contents of the block as `.text` and `.range` will be the beginning of the block, and the end of it). The `evaluate_*` commands expect the same object that `.get_*` returns. `get_*` **can return** `null` if a text editor is not selected, or if the current cursor position does not point to the object you're trying to get (for example, if the cursor is on a blank space and you're trying to get the current var, or if the cursor is outside a block and you're trying to get the block).

Finally, `run_command` is kinda internal use only - you can use it to run Clover commands **and** custom commands that you define in the config file (as shown on *Custom Commands* above) so you can use it to script things with a REPL connected exactly the same as you would inside a Clover config file.

> **WARNING ABOUT `range`:** You **must pass a Javascript** array of arrays. So you either pass `#js [# js [0 1] #js [1 1] ]` or use `clj->js` over the vector in Joyride. Clover _can't detect_, and actually there is no way to detect, if a specific object is a Clojure vector of vectors because of the way ClojureScript to JS compiler works (it renames and minifies variable names, and it's also not deterministic, so the variable names are not the same between compilations, even if we don't change the app in any way). Clover _will detect_ that a range is not valid and it'll add the current editor's range in place, so you can leave it empty if the range is not important.
>
> Basically, the `range` serves only a single purpose: to know which namespace the current code will run. On Chlorine, it also defines where it'll render the inline results, but Clover does not have these for now.

**Example** - connect to a socket REPL at port 5555 and run a command, disconnecting after it:

```clojure
(ns activate
  (:require [joyride.core :as joyride]
            ["vscode" :as vscode]
            [promesa.core :as p]))

(when (= (joyride/invoked-script) joyride/*file*)
  (p/let [clover (vscode/extensions.getExtension "mauricioszabo.clover")
          commands (.-exports clover)
          _ (.connect_socket commands "localhost" 5555)
          res (.evaluate commands "(+ 1 2)" (clj->js [[0 0] [0 0]]))]
    (.append (joyride/output-channel) (str "Result is: " (.-result res) "\n"))))
```

## Related Projects:
* [Chlorine](https://github.com/mauricioszabo/atom-chlorine)
* [REPL Tooling](https://github.com/mauricioszabo/repl-tooling)
